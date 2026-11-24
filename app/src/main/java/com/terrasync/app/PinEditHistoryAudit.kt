package com.terrasync.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class PinEditEntry(
    val pin: PinItem,
    val historyLineIndex: Int, // -1 if synthesized from editedAt/editedBy
    val dateGroup: String,     // e.g. "24 Nov"
    val clockTime: String,     // e.g. "18:42"
    val fullTimeLabel: String, // e.g. "24 Nov 18:42"
    val minuteOfDay: Int?,     // 0..1439 or null if unparseable
    val sortEpochMs: Long,
    val operatorRaw: String,
    val operatorShort: String,
    val actionSummary: String,
    val actionStatus: String,  // "done", "issue", "pending", "text"
    val isOutsideWorkHours: Boolean
)

/**
 * Parses per-pin edit history logs across a KML/cloud folder into a unified
 * chronological submission timeline with working-hours compliance checks.
 */
object PinEditHistoryAudit {

    private val TIME_PREFIX_REGEX = Regex("""^(\d{1,2}\s+[A-Za-z]{3})\s+(\d{2}):(\d{2})$""")
    private val MONTH_MAP = mapOf(
        "jan" to Calendar.JANUARY,
        "feb" to Calendar.FEBRUARY,
        "mar" to Calendar.MARCH,
        "apr" to Calendar.APRIL,
        "may" to Calendar.MAY,
        "jun" to Calendar.JUNE,
        "jul" to Calendar.JULY,
        "aug" to Calendar.AUGUST,
        "sep" to Calendar.SEPTEMBER,
        "oct" to Calendar.OCTOBER,
        "nov" to Calendar.NOVEMBER,
        "dec" to Calendar.DECEMBER
    )

    fun formatMinutes(totalMin: Int): String {
        val clamped = totalMin.coerceIn(0, 23 * 60 + 59)
        return "%02d:%02d".format(Locale.US, clamped / 60, clamped % 60)
    }

    /**
     * Returns true if [minuteOfDay] is outside the configured [startMin]..[endMin] window.
     * Supports both standard daytime shifts (08:00–17:00) and overnight shifts (22:00–06:00).
     */
    fun isOutsideWorkingHours(minuteOfDay: Int?, startMin: Int, endMin: Int): Boolean {
        if (minuteOfDay == null) return false
        if (startMin == endMin) return false
        val inside = if (startMin < endMin) {
            minuteOfDay in startMin..endMin
        } else {
            minuteOfDay >= startMin || minuteOfDay <= endMin
        }
        return !inside
    }

    /** Checks a single raw history line for after-hours status (used on Pin Card badges). */
    fun isHistoryLineOutsideWorkHours(line: String, startMin: Int, endMin: Int): Boolean {
        val timePart = line.substringBefore("•").trim()
        val m = TIME_PREFIX_REGEX.matchEntire(timePart) ?: return false
        val hh = m.groupValues[2].toIntOrNull() ?: return false
        val mm = m.groupValues[3].toIntOrNull() ?: return false
        return isOutsideWorkingHours(hh * 60 + mm, startMin, endMin)
    }

    fun collectFolderHistory(
        layerPins: List<PinItem>,
        workStartMin: Int,
        workEndMin: Int
    ): List<PinEditEntry> {
        val entries = ArrayList<PinEditEntry>(layerPins.size)
        val nowCal = Calendar.getInstance()
        val currentYear = nowCal.get(Calendar.YEAR)
        val nowMs = nowCal.timeInMillis

        for (pin in layerPins) {
            val lines = pin.editHistory.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                lines.forEachIndexed { idx, rawLine ->
                    entries.add(
                        parseHistoryLine(
                            pin = pin,
                            lineIdx = idx,
                            rawLine = rawLine,
                            workStartMin = workStartMin,
                            workEndMin = workEndMin,
                            currentYear = currentYear,
                            nowMs = nowMs
                        )
                    )
                }
            } else if (pin.isEdited || pin.editedAt > 0L || !pin.status.equals("pending", ignoreCase = true)) {
                val ts = when {
                    pin.editedAt > 0L -> pin.editedAt
                    pin.updatedAt > 0L -> pin.updatedAt
                    else -> 0L
                }
                val dateGroup: String
                val clockTime: String
                val fullTime: String
                val minOfDay: Int?
                if (ts > 0L) {
                    val cal = Calendar.getInstance().apply { timeInMillis = ts }
                    dateGroup = SimpleDateFormat("dd MMM", Locale.US).format(Date(ts))
                    clockTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
                    fullTime = "$dateGroup $clockTime"
                    minOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                } else {
                    dateGroup = "Unknown Date"
                    clockTime = "--:--"
                    fullTime = "Unknown time"
                    minOfDay = null
                }
                val opRaw = pin.editedBy.ifBlank { "Unknown operator" }
                val action = buildString {
                    append(statusLabel(pin.status))
                    if (pin.issueReason.isNotBlank()) append(": ${pin.issueReason}")
                }
                entries.add(
                    PinEditEntry(
                        pin = pin,
                        historyLineIndex = -1,
                        dateGroup = dateGroup,
                        clockTime = clockTime,
                        fullTimeLabel = fullTime,
                        minuteOfDay = minOfDay,
                        sortEpochMs = ts,
                        operatorRaw = opRaw,
                        operatorShort = extractShortOperator(opRaw),
                        actionSummary = action,
                        actionStatus = pin.status.lowercase(Locale.US).ifBlank { "pending" },
                        isOutsideWorkHours = isOutsideWorkingHours(minOfDay, workStartMin, workEndMin)
                    )
                )
            }
        }

        return entries.sortedWith(
            compareByDescending<PinEditEntry> { it.sortEpochMs }
                .thenBy { it.historyLineIndex }
        )
    }

    private fun parseHistoryLine(
        pin: PinItem,
        lineIdx: Int,
        rawLine: String,
        workStartMin: Int,
        workEndMin: Int,
        currentYear: Int,
        nowMs: Long
    ): PinEditEntry {
        val bulletIdx = rawLine.indexOf('•')
        val timePart = if (bulletIdx > 0) rawLine.substring(0, bulletIdx).trim() else ""
        val rest = if (bulletIdx > 0) rawLine.substring(bulletIdx + 1).trim() else rawLine

        val arrowIdx = rest.indexOf('→')
        val operatorRaw: String
        val actionSummary: String
        if (arrowIdx >= 0) {
            operatorRaw = rest.substring(0, arrowIdx).trim().ifBlank { pin.editedBy.ifBlank { "Unknown" } }
            actionSummary = rest.substring(arrowIdx + 1).trim().ifBlank { "Updated" }
        } else if (rest.endsWith("edited text", ignoreCase = true)) {
            operatorRaw = rest.removeSuffix("edited text").trim().ifBlank { pin.editedBy.ifBlank { "Unknown" } }
            actionSummary = "Edited text"
        } else {
            operatorRaw = pin.editedBy.ifBlank { "Unknown" }
            actionSummary = rest.ifBlank { "Updated" }
        }

        val actionStatus = when {
            actionSummary.startsWith("Done", ignoreCase = true) -> "done"
            actionSummary.startsWith("Issue", ignoreCase = true) -> "issue"
            actionSummary.startsWith("Pending", ignoreCase = true) -> "pending"
            actionSummary.contains("edited text", ignoreCase = true) -> "text"
            else -> pin.status.lowercase(Locale.US).ifBlank { "pending" }
        }

        val match = TIME_PREFIX_REGEX.matchEntire(timePart)
        var dateGroup = "Unknown Date"
        var clockTime = "--:--"
        var minOfDay: Int? = null
        var sortMs = if (lineIdx == 0 && pin.editedAt > 0L) pin.editedAt else 0L

        if (match != null) {
            dateGroup = match.groupValues[1].trim()
            val hh = match.groupValues[2].toIntOrNull() ?: 0
            val mm = match.groupValues[3].toIntOrNull() ?: 0
            clockTime = "%02d:%02d".format(Locale.US, hh, mm)
            minOfDay = hh * 60 + mm

            val reconstructed = reconstructEpoch(dateGroup, hh, mm, currentYear, nowMs)
            // Keep exact editedAt for the newest history line if it matches the same minute
            if (lineIdx == 0 && pin.editedAt > 0L) {
                val cal = Calendar.getInstance().apply { timeInMillis = pin.editedAt }
                val sameClock = cal.get(Calendar.HOUR_OF_DAY) == hh && cal.get(Calendar.MINUTE) == mm
                sortMs = if (sameClock) pin.editedAt else reconstructed
            } else {
                // Subtract lineIdx ms so multiple edits within the same minute preserve exact order
                sortMs = reconstructed - lineIdx
            }
        } else if (lineIdx == 0 && pin.editedAt > 0L) {
            val cal = Calendar.getInstance().apply { timeInMillis = pin.editedAt }
            dateGroup = SimpleDateFormat("dd MMM", Locale.US).format(Date(pin.editedAt))
            clockTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(pin.editedAt))
            minOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            sortMs = pin.editedAt
        }

        val fullLabel = if (clockTime != "--:--") "$dateGroup $clockTime" else timePart.ifBlank { "Unknown time" }

        return PinEditEntry(
            pin = pin,
            historyLineIndex = lineIdx,
            dateGroup = dateGroup,
            clockTime = clockTime,
            fullTimeLabel = fullLabel,
            minuteOfDay = minOfDay,
            sortEpochMs = sortMs,
            operatorRaw = operatorRaw,
            operatorShort = extractShortOperator(operatorRaw),
            actionSummary = actionSummary,
            actionStatus = actionStatus,
            isOutsideWorkHours = isOutsideWorkingHours(minOfDay, workStartMin, workEndMin)
        )
    }

    private fun reconstructEpoch(dateGroup: String, hh: Int, mm: Int, currentYear: Int, nowMs: Long): Long {
        val parts = dateGroup.split(Regex("\\s+"))
        val day = parts.getOrNull(0)?.toIntOrNull() ?: 1
        val mon = MONTH_MAP[parts.getOrNull(1)?.lowercase(Locale.US)] ?: Calendar.JANUARY
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, currentYear)
            set(Calendar.MONTH, mon)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hh)
            set(Calendar.MINUTE, mm)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Handle year boundary (e.g., Dec logs viewed in Jan)
        if (cal.timeInMillis > nowMs + 7L * 24L * 3600L * 1000L) {
            cal.add(Calendar.YEAR, -1)
        }
        return cal.timeInMillis
    }

    private fun extractShortOperator(raw: String): String {
        val clean = raw.trim()
        if (clean.isBlank()) return "Unknown"
        val parenIdx = clean.indexOf('(')
        if (parenIdx > 0) {
            return clean.substring(0, parenIdx).trim().ifBlank { clean }
        }
        return clean
    }

    fun buildAuditShareText(
        folderName: String,
        entries: List<PinEditEntry>,
        workStartMin: Int,
        workEndMin: Int
    ): String {
        val window = "${formatMinutes(workStartMin)}–${formatMinutes(workEndMin)}"
        val afterHours = entries.filter { it.isOutsideWorkHours }
        val inHours = entries.size - afterHours.size
        return buildString {
            appendLine("*$folderName — Edit History & Hours Audit*")
            appendLine("Working Hours: $window")
            appendLine("Total Submissions: ${entries.size} (In Hours: $inHours • After Hours: ${afterHours.size})")
            if (afterHours.isNotEmpty()) {
                appendLine()
                appendLine("*⏰ Outside Working Hours (${afterHours.size})*")
                afterHours.take(40).forEach { e ->
                    appendLine("• [${e.fullTimeLabel}] ${e.operatorShort} — ${e.pin.name} → ${e.actionSummary}")
                }
                if (afterHours.size > 40) {
                    appendLine("• _+${afterHours.size - 40} more after-hours edits_")
                }
            }
            val byOperator = entries.groupBy { it.operatorShort }
                .entries
                .sortedByDescending { it.value.size }
            if (byOperator.isNotEmpty()) {
                appendLine()
                appendLine("*Per Operator*")
                byOperator.forEach { (op, list) ->
                    val late = list.count { it.isOutsideWorkHours }
                    val lateNote = if (late > 0) " ($late after hours)" else ""
                    appendLine("• $op: ${list.size} edits$lateNote")
                }
            }
        }.trim()
    }
}

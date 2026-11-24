package com.terrasync.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DailyFieldReport(
    val whatsappText: String
)

object DailyReportGenerator {

    private val KNOWN_REASONS = listOf(
        "Meter Linked",
        "Inaccessible",
        "No Bill",
        "Need Electricity",
        "Customer Refused / No Access",
        "Wrong Location / Coordinates",
        "Closed / Nobody Home",
        "Safety Hazard / Network Fault",
        "Wrong Link",
        "Disconnected",
        "Replaced",
        "Damaged"
    )

    fun buildReport(
        folderName: String,
        pins: List<PinItem>,
        isCloudLive: Boolean = true
    ): DailyFieldReport = DailyFieldReport(generate(folderName, pins, isCloudLive))

    fun generate(
        folderName: String,
        pins: List<PinItem>,
        isCloudLive: Boolean = false
    ): String {
        val now = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.US).format(Date())
        val total = pins.size
        if (total == 0) {
            return buildString {
                appendLine("*$folderName — Daily Field Report*")
                appendLine("_${now}_")
                appendLine()
                appendLine("No pins in this folder.")
            }.trim()
        }

        var doneCount = 0
        var issueCount = 0
        var pendingTotal = 0
        var pendingTouched = 0

        val doneReasons = linkedMapOf<String, Int>()
        val issueReasons = linkedMapOf<String, Int>()
        val fieldNotes = mutableListOf<String>()

        data class TeamStats(
            val displayName: String,
            var done: Int = 0,
            var issue: Int = 0,
            var pendingTouched: Int = 0
        ) {
            val worked: Int get() = done + issue + pendingTouched
        }

        val teamMap = linkedMapOf<String, TeamStats>()

        for (pin in pins) {
            val status = normalizeStatus(pin.status)
            val rawReason = pin.issueReason.trim()
            val rawNote = pin.description.trim()
            val teamKeyAndLabel = resolveTeam(pin)

            when (status) {
                "DONE" -> {
                    doneCount++
                    val reason = classifyReason(rawReason, rawNote, defaultLabel = "Completed")
                    doneReasons[reason] = (doneReasons[reason] ?: 0) + 1
                    teamKeyAndLabel?.let { (key, label) ->
                        teamMap.getOrPut(key) { TeamStats(label) }.done++
                    }
                }
                "ISSUE" -> {
                    issueCount++
                    val reason = classifyReason(rawReason, rawNote, defaultLabel = "Other Issue")
                    issueReasons[reason] = (issueReasons[reason] ?: 0) + 1
                    teamKeyAndLabel?.let { (key, label) ->
                        teamMap.getOrPut(key) { TeamStats(label) }.issue++
                    }
                }
                else -> {
                    pendingTotal++
                    val isTouched = pin.isEdited || teamKeyAndLabel != null || rawReason.isNotBlank()
                    if (isTouched) {
                        pendingTouched++
                        teamKeyAndLabel?.let { (key, label) ->
                            teamMap.getOrPut(key) { TeamStats(label) }.pendingTouched++
                        }
                    }
                }
            }

            if (status == "ISSUE" && rawReason.isNotBlank() && KNOWN_REASONS.none { it.equals(rawReason, true) }) {
                val prefix = teamKeyAndLabel?.second?.let { "[$it] " } ?: ""
                val pinLabel = pin.name.ifBlank { "Pin" }
                fieldNotes.add("$prefix$pinLabel: $rawReason")
            }
        }

        val pendingUntouched = (pendingTotal - pendingTouched).coerceAtLeast(0)
        val pctDone = ((doneCount * 100.0) / total).toInt()
        val syncBadge = if (isCloudLive) "Live Cloud" else "Local"

        return buildString {
            appendLine("*$folderName — Daily Field Report*")
            appendLine("_${now} • $syncBadge")
            appendLine()
            appendLine("*Summary*")
            appendLine("• Total Pins: *$total*")
            appendLine("• Completion: *$pctDone%* ($doneCount / $total)")
            appendLine("• ✓ Done: *$doneCount*")
            appendLine("• ⚠ Issue: *$issueCount*")
            appendLine("• ⏳ Pending: *$pendingTotal* ($pendingUntouched untouched, $pendingTouched touched)")

            if (doneCount > 0) {
                appendLine()
                appendLine("*✓ Done Breakdown ($doneCount)*")
                doneReasons.entries
                    .sortedByDescending { it.value }
                    .forEach { (reason, count) -> appendLine("• $reason: *$count*") }
            }

            if (issueCount > 0) {
                appendLine()
                appendLine("*⚠ Issue Breakdown ($issueCount)*")
                issueReasons.entries
                    .sortedByDescending { it.value }
                    .forEach { (reason, count) -> appendLine("• $reason: *$count*") }
            }

            if (teamMap.isNotEmpty()) {
                appendLine()
                appendLine("*Per-Team Activity*")
                teamMap.values
                    .sortedWith(compareByDescending<TeamStats> { it.worked }.thenBy { it.displayName })
                    .forEach { t ->
                        val parts = mutableListOf<String>()
                        if (t.done > 0) parts.add("${t.done} Done")
                        if (t.issue > 0) parts.add("${t.issue} Issue")
                        if (t.pendingTouched > 0) parts.add("${t.pendingTouched} Pending")
                        appendLine("• *${t.displayName}*: ${t.worked} worked (${parts.joinToString(" • ")})")
                    }
            }

            if (fieldNotes.isNotEmpty()) {
                appendLine()
                appendLine("*Field Notes / Rework (${fieldNotes.size})*")
                fieldNotes.take(15).forEach { note -> appendLine("• $note") }
                if (fieldNotes.size > 15) {
                    appendLine("• _+${fieldNotes.size - 15} more notes_")
                }
            }
        }.trim()
    }

    private fun normalizeStatus(raw: String): String {
        val s = raw.trim().uppercase(Locale.US)
        return when {
            s.contains("DONE") || s.contains("COMPLETE") || s == "OK" -> "DONE"
            s.contains("ISSUE") || s.contains("FAIL") || s.contains("PROBLEM") || s.contains("ERROR") -> "ISSUE"
            else -> "PENDING"
        }
    }

    private fun classifyReason(rawReason: String, rawNote: String, defaultLabel: String): String {
        if (rawReason.isNotEmpty()) {
            val matched = KNOWN_REASONS.firstOrNull { it.equals(rawReason, ignoreCase = true) }
            if (matched != null) return matched
            val cleaned = rawReason.substringBefore(":").trim()
            if (cleaned.length <= 40) return cleaned
        }
        val combined = "$rawReason $rawNote".lowercase(Locale.US)
        return when {
            "meter link" in combined -> "Meter Linked"
            "inaccess" in combined -> "Inaccessible"
            "no bill" in combined || "nobill" in combined -> "No Bill"
            "need electric" in combined || "no electric" in combined -> "Need Electricity"
            else -> defaultLabel
        }
    }

    private fun resolveTeam(pin: PinItem): Pair<String, String>? {
        val raw = pin.editedBy.trim()
        if (raw.isEmpty()) return null
        val display = raw.substringBefore(" (").trim().ifBlank { raw }
        val teamMatch = Regex("^(?i)team[\\s_-]*(\\d+)$").matchEntire(display)
        val label = if (teamMatch != null) "Team ${teamMatch.groupValues[1]}" else display
        val key = label.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        return key to label
    }
}

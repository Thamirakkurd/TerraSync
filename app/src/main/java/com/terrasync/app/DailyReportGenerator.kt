package com.terrasync.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TeamStats(
    val teamName: String,
    val totalSubmitted: Int,
    val doneCount: Int,
    val issueCount: Int,
    val pendingCount: Int
)

data class DailyReportSummary(
    val uniqueTotal: Int,
    val rawTotal: Int,
    val duplicatesRemoved: Int,
    val uniqueDone: Int,
    val uniqueIssue: Int,
    val uniquePending: Int,
    val teamStats: List<TeamStats>,
    val whatsappText: String
)

object DailyReportGenerator {

    private fun statusPriority(status: String): Int = when (status.lowercase()) {
        "done" -> 3
        "issue" -> 2
        else -> 1
    }

    private fun dedupKey(pin: PinItem): String {
        val normName = pin.name.trim().lowercase().replace(Regex("\\s+"), " ")
        val latKey = String.format(Locale.US, "%.5f", pin.lat)
        val lngKey = String.format(Locale.US, "%.5f", pin.lng)
        // If pin has a meaningful non-generic name, combine name + ~1m GPS grid;
        // also catch identical coordinates with identical name
        return if (normName.isNotBlank() && !normName.startsWith("pin ")) {
            "n:$normName"
        } else {
            "g:$latKey,$lngKey"
        }
    }

    fun deduplicatePins(pins: List<PinItem>): List<PinItem> {
        // Deduplicate by ID first, then by normalized pin identity (keeping Done > Issue > Pending, then newest edit)
        val byKey = LinkedHashMap<String, PinItem>()
        for (p in pins) {
            val key = dedupKey(p)
            val existing = byKey[key]
            if (existing == null) {
                byKey[key] = p
            } else {
                val pPrio = statusPriority(p.status)
                val ePrio = statusPriority(existing.status)
                val takeNew = when {
                    pPrio != ePrio -> pPrio > ePrio
                    p.editedAt != existing.editedAt -> p.editedAt > existing.editedAt
                    else -> p.updatedAt > existing.updatedAt
                }
                if (takeNew) byKey[key] = p
            }
        }
        return byKey.values.toList()
    }

    private fun extractTeamLabel(pin: PinItem): String {
        var raw = pin.editedBy.trim()
        if (raw.isBlank() && pin.editHistory.isNotBlank()) {
            val firstStatus = pin.editHistory.lines().firstOrNull { it.contains("→") }
                ?: pin.editHistory.lines().firstOrNull { it.isNotBlank() }
            if (!firstStatus.isNullOrBlank()) {
                val left = firstStatus.substringBefore("→").trim()
                raw = left.substringAfter("•", left).substringBefore(" edited text").trim()
            }
        }
        if (raw.isBlank()) return "Unassigned / Unknown"
        // Prefer custom team name before "(Device • #ID)" when present
        val beforeParen = raw.substringBefore("(").trim()
        return if (raw.contains("(") && beforeParen.isNotBlank()) beforeParen else raw
    }

    fun buildReport(folderName: String, pins: List<PinItem>): DailyReportSummary {
        val unique = deduplicatePins(pins)
        val duplicatesRemoved = (pins.size - unique.size).coerceAtLeast(0)

        var doneTotal = 0
        var issueTotal = 0
        var pendingTotal = 0

        data class Acc(var done: Int = 0, var issue: Int = 0, var pending: Int = 0)
        val byTeam = LinkedHashMap<String, Acc>()

        for (p in unique) {
            val st = p.status.lowercase()
            when (st) {
                "done" -> doneTotal++
                "issue" -> issueTotal++
                else -> pendingTotal++
            }
            val touched = p.isEdited || p.editedBy.isNotBlank() || st == "done" || st == "issue"
            if (touched) {
                val team = extractTeamLabel(p)
                val acc = byTeam.getOrPut(team) { Acc() }
                when (st) {
                    "done" -> acc.done++
                    "issue" -> acc.issue++
                    else -> acc.pending++
                }
            }
        }

        val teamList = byTeam.entries.map { (name, a) ->
            TeamStats(
                teamName = name,
                totalSubmitted = a.done + a.issue + a.pending,
                doneCount = a.done,
                issueCount = a.issue,
                pendingCount = a.pending
            )
        }.sortedWith(compareByDescending<TeamStats> { it.doneCount }.thenByDescending { it.totalSubmitted })

        val dateStr = SimpleDateFormat("dd MMM yyyy • HH:mm", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("*TerraSync Daily Report*\n")
        sb.append("Folder: *$folderName*\n")
        sb.append("Date: $dateStr\n\n")
        sb.append("*Overall Summary (Unique Pins)*\n")
        sb.append("• Total Pins: *${unique.size}*")
        if (duplicatesRemoved > 0) sb.append(" (_${duplicatesRemoved} duplicate${if (duplicatesRemoved == 1) "" else "s"} excluded_)")
        sb.append("\n")
        sb.append("• Done: *$doneTotal*\n")
        sb.append("• Issue: *$issueTotal*\n")
        sb.append("• Pending: *$pendingTotal*\n")

        if (teamList.isNotEmpty()) {
            sb.append("\n*Per-Team Breakdown (No Duplicates)*\n")
            teamList.forEachIndexed { idx, t ->
                sb.append("${idx + 1}. *${t.teamName}*: *${t.doneCount} Done*")
                if (t.issueCount > 0) sb.append(", ${t.issueCount} Issue")
                if (t.pendingCount > 0) sb.append(", ${t.pendingCount} Pending")
                sb.append(" (Total: ${t.totalSubmitted})\n")
            }
        } else {
            sb.append("\n_No team submissions recorded yet._\n")
        }

        return DailyReportSummary(
            uniqueTotal = unique.size,
            rawTotal = pins.size,
            duplicatesRemoved = duplicatesRemoved,
            uniqueDone = doneTotal,
            uniqueIssue = issueTotal,
            uniquePending = pendingTotal,
            teamStats = teamList,
            whatsappText = sb.toString().trim()
        )
    }
}

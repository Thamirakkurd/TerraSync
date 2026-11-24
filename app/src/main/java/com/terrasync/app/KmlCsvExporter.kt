package com.terrasync.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KmlCsvExporter {

    private fun safeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40).ifBlank { "TerraSync_Layer" }
    }

    private fun xmlEscape(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun csvEscape(s: String): String {
        val clean = s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " | ")
        return "\"$clean\""
    }

    private fun styleUrlForStatus(status: String): String = when (status.lowercase()) {
        "done" -> "#placemark-green"
        "issue" -> "#placemark-red"
        else -> "#placemark-orange"
    }

    fun exportAndShareKml(
        ctx: Context,
        layer: LayerItem,
        pins: List<PinItem>,
        tracks: List<TrackItem>,
        filterSuffix: String = "",
        extraShareText: String? = null
    ) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val suffixClean = filterSuffix.trim()
        val exportTitle = if (suffixClean.isNotBlank()) "${layer.name} ($suffixClean)" else layer.name
        val baseName = if (suffixClean.isNotBlank()) {
            "${safeFileName(layer.name)}_${safeFileName(suffixClean)}"
        } else {
            safeFileName(layer.name)
        }
        val file = File(dir, "$baseName.kml")
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n")
        sb.append("  <name>${xmlEscape(exportTitle)}</name>\n")
        sb.append("  <Style id=\"placemark-green\">\n")
        sb.append("    <IconStyle><color>ff5ec522</color><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/grn-circle.png</href></Icon></IconStyle>\n")
        sb.append("  </Style>\n")
        sb.append("  <Style id=\"placemark-red\">\n")
        sb.append("    <IconStyle><color>ff4444ef</color><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/red-circle.png</href></Icon></IconStyle>\n")
        sb.append("  </Style>\n")
        sb.append("  <Style id=\"placemark-orange\">\n")
        sb.append("    <IconStyle><color>ff0b9ef5</color><scale>1.1</scale><Icon><href>http://maps.google.com/mapfiles/kml/paddle/ylw-circle.png</href></Icon></IconStyle>\n")
        sb.append("  </Style>\n")

        pins.forEach { p ->
            val normStatus = p.status.trim().lowercase().ifBlank { "pending" }
            val hasMeaningfulEdit = normStatus != "pending" ||
                p.issueReason.isNotBlank() ||
                p.isEdited ||
                p.editedBy.isNotBlank() ||
                p.editHistory.isNotBlank()

            val descParts = mutableListOf<String>()
            descParts += "Status: ${statusLabel(normStatus)}"
            if (p.issueReason.isNotBlank()) {
                val noteLabel = if (normStatus == "done") "Done Note" else "Issue"
                descParts += "$noteLabel: ${p.issueReason}"
            }
            if (p.description.isNotBlank()) descParts += p.description
            if (p.isEdited && p.editedBy.isNotBlank()) descParts += "Edited by: ${p.editedBy}"

            sb.append("  <Placemark>\n")
            sb.append("    <name>${xmlEscape(p.name)}</name>\n")
            sb.append("    <styleUrl>${styleUrlForStatus(normStatus)}</styleUrl>\n")
            sb.append("    <description>${xmlEscape(descParts.joinToString("\n"))}</description>\n")
            sb.append("    <ExtendedData>\n")
            sb.append("      <Data name=\"ts_status\"><value>${xmlEscape(normStatus)}</value></Data>\n")
            if (p.issueReason.isNotBlank()) {
                sb.append("      <Data name=\"ts_issue_reason\"><value>${xmlEscape(p.issueReason)}</value></Data>\n")
            }
            sb.append("      <Data name=\"ts_raw_description\"><value>${xmlEscape(p.description)}</value></Data>\n")
            if (hasMeaningfulEdit) {
                sb.append("      <Data name=\"ts_is_edited\"><value>true</value></Data>\n")
            }
            if (p.editedBy.isNotBlank()) {
                sb.append("      <Data name=\"ts_edited_by\"><value>${xmlEscape(p.editedBy)}</value></Data>\n")
            }
            if (p.editedAt > 0L) {
                sb.append("      <Data name=\"ts_edited_at\"><value>${p.editedAt}</value></Data>\n")
            }
            if (p.editHistory.isNotBlank()) {
                sb.append("      <Data name=\"ts_edit_history\"><value>${xmlEscape(p.editHistory)}</value></Data>\n")
            }
            sb.append("    </ExtendedData>\n")
            sb.append("    <Point><coordinates>${p.lng},${p.lat},0</coordinates></Point>\n")
            sb.append("  </Placemark>\n")
        }

        tracks.forEach { t ->
            val coordText = t.coords.split(";").joinToString(" ") { "$it,0" }
            sb.append("  <Placemark>\n")
            sb.append("    <name>${xmlEscape(t.name)}</name>\n")
            sb.append("    <LineString><coordinates>$coordText</coordinates></LineString>\n")
            sb.append("  </Placemark>\n")
        }

        sb.append("</Document>\n</kml>\n")
        file.writeText(sb.toString(), Charsets.UTF_8)
        shareFile(ctx, file, "application/vnd.google-earth.kml+xml", "Export KML: $exportTitle", extraShareText)
    }

    fun exportAndShareCsv(ctx: Context, layer: LayerItem, pins: List<PinItem>) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${safeFileName(layer.name)}.csv")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder()
        sb.append("Name,Status,StatusNote,Description,Latitude,Longitude,EditedBy,EditedAt\n")
        pins.forEach { p ->
            val timeStr = if (p.editedAt > 0L) fmt.format(Date(p.editedAt)) else ""
            sb.append(
                listOf(
                    csvEscape(p.name),
                    csvEscape(statusLabel(p.status)),
                    csvEscape(p.issueReason),
                    csvEscape(p.description),
                    "${p.lat}",
                    "${p.lng}",
                    csvEscape(p.editedBy),
                    csvEscape(timeStr)
                ).joinToString(",")
            ).append("\n")
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        shareFile(ctx, file, "text/csv", "Export CSV: ${layer.name}", null)
    }

    private fun shareFile(ctx: Context, file: File, mime: String, title: String, extraText: String? = null) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            if (!extraText.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TEXT, extraText)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, title))
    }
}

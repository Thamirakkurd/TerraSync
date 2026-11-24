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

    fun exportAndShareKml(ctx: Context, layer: LayerItem, pins: List<PinItem>, tracks: List<TrackItem>) {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${safeFileName(layer.name)}.kml")
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n")
        sb.append("  <name>${xmlEscape(layer.name)}</name>\n")

        pins.forEach { p ->
            val descParts = mutableListOf<String>()
            descParts += "Status: ${statusLabel(p.status)}"
            if (p.issueReason.isNotBlank()) {
                val noteLabel = if (p.status.equals("done", true)) "Done Note" else "Issue"
                descParts += "$noteLabel: ${p.issueReason}"
            }
            if (p.description.isNotBlank()) descParts += p.description
            if (p.isEdited && p.editedBy.isNotBlank()) descParts += "Edited by: ${p.editedBy}"
            sb.append("  <Placemark>\n")
            sb.append("    <name>${xmlEscape(p.name)}</name>\n")
            sb.append("    <description>${xmlEscape(descParts.joinToString("\n"))}</description>\n")
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
        shareFile(ctx, file, "application/vnd.google-earth.kml+xml", "Export KML: ${layer.name}")
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
        shareFile(ctx, file, "text/csv", "Export CSV: ${layer.name}")
    }

    private fun shareFile(ctx: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, title))
    }
}

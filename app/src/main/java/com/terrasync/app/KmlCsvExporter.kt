package com.terrasync.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object KmlCsvExporter {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val UNSAFE_FILE_REGEX = Regex("[^a-zA-Z0-9._-]")

    private fun safeFileName(name: String): String {
        return name.replace(UNSAFE_FILE_REGEX, "_").take(40).ifBlank { "TerraSync_Layer" }
    }

    private fun cleanupOldExports(dir: File) {
        val cutoff = System.currentTimeMillis() - 24L * 3600L * 1000L
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.lastModified() < cutoff) runCatching { f.delete() }
        }
    }

    private fun xmlEscape(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch == '\t' || ch == '\n' || ch == '\r' || ch.code >= 0x20 -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun csvEscape(s: String): String {
        val clean = s.replace("\"", "\"\"").replace("\r", " ").replace("\n", " | ")
        return "\"$clean\""
    }

    private fun styleUrlForStatus(status: String): String = when (PinStatus.fromRaw(status)) {
        PinStatus.DONE -> "#placemark-green"
        PinStatus.ISSUE -> "#placemark-red"
        PinStatus.PENDING -> "#placemark-orange"
    }

    fun exportAndShareKml(
        ctx: Context,
        layer: LayerItem,
        pins: List<PinItem>,
        tracks: List<TrackItem>,
        filterSuffix: String = "",
        extraShareText: String? = null
    ) {
        val appCtx = ctx.applicationContext
        val pinsCopy = pins.toList()
        val tracksCopy = tracks.toList()
        if (pinsCopy.size >= 500) {
            android.widget.Toast.makeText(ctx, "Preparing KML export...", android.widget.Toast.LENGTH_SHORT).show()
        }
        RoutingEngine.ioExecutor.execute {
            val result = runCatching {
                val dir = File(appCtx.cacheDir, "exports").apply { mkdirs() }
                cleanupOldExports(dir)
                val suffixClean = filterSuffix.trim()
                val exportTitle = if (suffixClean.isNotBlank()) "${layer.name} ($suffixClean)" else layer.name
                val baseName = if (suffixClean.isNotBlank()) {
                    "${safeFileName(layer.name)}_${safeFileName(suffixClean)}"
                } else {
                    safeFileName(layer.name)
                }
                val file = File(dir, "$baseName.kml")
                val sb = StringBuilder((pinsCopy.size * 420 + tracksCopy.size * 256 + 1024).coerceAtMost(8 * 1024 * 1024))
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

                pinsCopy.forEach { p ->
                    val normStatus = PinStatus.fromRaw(p.status).key
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

                tracksCopy.forEach { t ->
                    val validPts = t.coords.split(";").map { it.trim() }.filter { it.contains(",") }
                    if (validPts.size >= 2) {
                        val coordText = validPts.joinToString(" ") { "$it,0" }
                        sb.append("  <Placemark>\n")
                        sb.append("    <name>${xmlEscape(t.name)}</name>\n")
                        sb.append("    <LineString><coordinates>$coordText</coordinates></LineString>\n")
                        sb.append("  </Placemark>\n")
                    }
                }

                sb.append("</Document>\n</kml>\n")
                file.writeText(sb.toString(), Charsets.UTF_8)
                file to "Export KML: $exportTitle"
            }
            mainHandler.post {
                val act = ctx as? android.app.Activity
                if (act != null && (act.isFinishing || act.isDestroyed)) return@post
                result.onSuccess { (file, title) ->
                    shareFile(ctx, file, "application/vnd.google-earth.kml+xml", title, extraShareText)
                }.onFailure {
                    android.widget.Toast.makeText(appCtx, "Failed to export KML file", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun exportAndShareCsv(ctx: Context, layer: LayerItem, pins: List<PinItem>) {
        val appCtx = ctx.applicationContext
        val pinsCopy = pins.toList()
        if (pinsCopy.size >= 500) {
            android.widget.Toast.makeText(ctx, "Preparing CSV export...", android.widget.Toast.LENGTH_SHORT).show()
        }
        RoutingEngine.ioExecutor.execute {
            val result = runCatching {
                val dir = File(appCtx.cacheDir, "exports").apply { mkdirs() }
                cleanupOldExports(dir)
                val file = File(dir, "${safeFileName(layer.name)}.csv")
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                val sb = StringBuilder((pinsCopy.size * 140 + 256).coerceAtMost(4 * 1024 * 1024))
                // UTF-8 BOM ensures Excel opens Kurdish / Arabic pin names cleanly
                sb.append('\uFEFF')
                sb.append("Name,Status,StatusNote,Description,Latitude,Longitude,EditedBy,EditedAt\n")
                pinsCopy.forEach { p ->
                    val timeStr = if (p.editedAt > 0L) fmt.format(Date(p.editedAt)) else ""
                    sb.append(csvEscape(p.name)).append(',')
                        .append(csvEscape(statusLabel(p.status))).append(',')
                        .append(csvEscape(p.issueReason)).append(',')
                        .append(csvEscape(p.description)).append(',')
                        .append(p.lat).append(',')
                        .append(p.lng).append(',')
                        .append(csvEscape(p.editedBy)).append(',')
                        .append(csvEscape(timeStr)).append('\n')
                }
                file.writeText(sb.toString(), Charsets.UTF_8)
                file
            }
            mainHandler.post {
                val act = ctx as? android.app.Activity
                if (act != null && (act.isFinishing || act.isDestroyed)) return@post
                result.onSuccess { file ->
                    shareFile(ctx, file, "text/csv", "Export CSV: ${layer.name}", null)
                }.onFailure {
                    android.widget.Toast.makeText(appCtx, "Failed to export CSV file", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
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
        val chooser = Intent.createChooser(intent, title).apply {
            if (ctx !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            ctx.startActivity(chooser)
        }.onFailure {
            android.widget.Toast.makeText(ctx, "No app available to share file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

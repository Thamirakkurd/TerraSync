package com.terrasync.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ParsedKml(
    val pins: List<PinItem>,
    val tracks: List<TrackItem>,
    val documentName: String = ""
)

object KmlParser {

    private const val MAX_KML_BYTES = 25 * 1024 * 1024
    private val HTML_BR_REGEX = Regex("(?i)<br\\s*/?>")
    private val HTML_BLOCK_END_REGEX = Regex("(?i)</(p|div|li|tr)>")
    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val BARE_AMP_REGEX = Regex("&(?!amp;|lt;|gt;|quot;|apos;|#\\d+;|#x[0-9a-fA-F]+;)")
    private val CTRL_CHAR_REGEX = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]")

    private fun readBoundedBytes(input: InputStream, maxBytes: Int = MAX_KML_BYTES): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (n == 0) continue
            total += n
            if (total > maxBytes) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    fun parse(inputStream: InputStream, layerId: String): ParsedKml {
        val raw = readBoundedBytes(inputStream) ?: return ParsedKml(emptyList(), emptyList(), "")
        val kmlBytes = if (raw.size >= 2 && raw[0] == 0x50.toByte() && raw[1] == 0x4B.toByte()) {
            extractKmlFromKmz(raw) ?: return ParsedKml(emptyList(), emptyList(), "")
        } else {
            raw
        }
        return runCatching { parseKmlXml(kmlBytes, layerId) }.getOrElse {
            // Resilient recovery for real-world KMLs containing unescaped '&' or stray control chars
            val sanitized = String(kmlBytes, Charsets.UTF_8)
                .replace(CTRL_CHAR_REGEX, "")
                .replace(BARE_AMP_REGEX, "&amp;")
                .toByteArray(Charsets.UTF_8)
            runCatching { parseKmlXml(sanitized, layerId) }
                .getOrElse { ParsedKml(emptyList(), emptyList(), "") }
        }
    }

    private fun extractKmlFromKmz(bytes: ByteArray): ByteArray? {
        var fallbackKml: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".kml")) {
                    val base = entry.name.substringAfterLast('/').lowercase()
                    val data = readBoundedBytes(zis)
                    if (data != null) {
                        if (base == "doc.kml") return data
                        if (fallbackKml == null) fallbackKml = data
                    }
                }
                entry = zis.nextEntry
            }
        }
        return fallbackKml
    }

    private fun normalizeStatus(raw: String): String? {
        val clean = raw.trim().lowercase()
        if (clean.isBlank()) return null
        return when {
            clean == "done" || clean.startsWith("done:") || clean.startsWith("done ") ||
                clean == "completed" || clean == "complete" || clean == "finished" ||
                clean == "meter linked" || clean == "inaccessible" || clean == "no bill" -> "done"
            clean == "issue" || clean.startsWith("issue:") || clean.startsWith("issue ") ||
                clean == "problem" || clean == "error" ||
                clean == "need electricity" ||
                clean == "customer refused / no access" ||
                clean == "wrong location / coordinates" ||
                clean == "closed / nobody home" ||
                clean == "safety hazard / network fault" -> "issue"
            clean == "pending" || clean.startsWith("pending:") || clean.startsWith("pending ") ||
                clean == "todo" || clean == "open" -> "pending"
            else -> null
        }
    }

    private fun extractImplicitReasonFromStatus(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        val low = trimmed.lowercase()
        return when {
            low.startsWith("done:") || low.startsWith("issue:") -> trimmed.substringAfter(":").trim()
            low in setOf(
                "meter linked",
                "inaccessible",
                "no bill",
                "need electricity",
                "customer refused / no access",
                "wrong location / coordinates",
                "closed / nobody home",
                "safety hazard / network fault"
            ) -> trimmed
            else -> ""
        }
    }

    private fun statusFromStyleUrl(styleUrl: String): String? {
        val low = styleUrl.trim().lowercase()
        if (low.isBlank()) return null
        return when {
            low.contains("green") || low.contains("grn") || low.contains("done") ||
                low.contains("0f9d58") || low.contains("22c55e") -> "done"
            low.contains("red") || low.contains("issue") ||
                low.contains("db4436") || low.contains("ef4444") -> "issue"
            low.contains("orange") || low.contains("yellow") || low.contains("ylw") ||
                low.contains("amber") || low.contains("pending") -> "pending"
            else -> null
        }
    }

    private fun stripHtml(raw: String): String {
        if (!raw.contains('<') && !raw.contains('&')) return raw
        return raw
            .replace(HTML_BR_REGEX, "\n")
            .replace(HTML_BLOCK_END_REGEX, "\n")
            .replace(HTML_TAG_REGEX, "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
    }

    private fun getAttrIgnoreCase(parser: XmlPullParser, name: String): String {
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i)?.substringAfterLast(':') ?: continue
            if (attrName.equals(name, ignoreCase = true)) {
                return parser.getAttributeValue(i)?.trim().orEmpty()
            }
        }
        return ""
    }

    private fun parseKmlXml(bytes: ByteArray, layerId: String): ParsedKml {
        val pins = mutableListOf<PinItem>()
        val tracks = mutableListOf<TrackItem>()
        var docName = ""
        val now = System.currentTimeMillis()

        val cleanBytes = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(cleanBytes), null)

        var inPlacemark = false
        var inStyle = false
        var inPoint = false
        var lineDepth = 0
        var inDescription = false
        var inExtendedData = false
        var currentDataKey = ""
        var activeTag = ""
        val textBuf = StringBuilder()
        val descBuf = StringBuilder()

        var pmName = ""
        var pmDesc = ""
        var pmStyleUrl = ""
        val pmExtra = mutableListOf<String>()
        val pmMeta = mutableMapOf<String, String>()
        val pmGeneric = mutableMapOf<String, String>()
        val pmPoints = mutableListOf<Pair<Double, Double>>()
        val pmLines = mutableListOf<List<Pair<Double, Double>>>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfterLast(':').lowercase()
                    if (inDescription) {
                        if (tag == "br" || tag == "p" || tag == "div" || tag == "li" || tag == "tr") {
                            descBuf.append("\n")
                        }
                    } else {
                        activeTag = tag
                        textBuf.setLength(0)
                        when (tag) {
                            "style", "stylemap" -> inStyle = true
                            "placemark" -> {
                                inPlacemark = true
                                inPoint = false
                                lineDepth = 0
                                inDescription = false
                                inExtendedData = false
                                currentDataKey = ""
                                pmName = ""
                                pmDesc = ""
                                pmStyleUrl = ""
                                descBuf.setLength(0)
                                pmExtra.clear()
                                pmMeta.clear()
                                pmGeneric.clear()
                                pmPoints.clear()
                                pmLines.clear()
                            }
                            "description" -> if (inPlacemark && !inExtendedData) {
                                inDescription = true
                                descBuf.setLength(0)
                            }
                            "point" -> inPoint = true
                            "linestring", "polygon", "linearring" -> lineDepth++
                            "extendeddata" -> inExtendedData = true
                            "data", "simpledata" -> if (inPlacemark) {
                                currentDataKey = getAttrIgnoreCase(parser, "name")
                            }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inDescription) {
                        descBuf.append(parser.text ?: "")
                    } else if ((!inPlacemark && !inStyle && activeTag == "name") ||
                        (inPlacemark && (activeTag == "name" || activeTag == "styleurl" ||
                            activeTag == "coordinates" || activeTag == "value" || activeTag == "simpledata"))
                    ) {
                        textBuf.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfterLast(':').lowercase()
                    if (inDescription) {
                        if (tag == "description") {
                            inDescription = false
                            if (pmDesc.isBlank()) pmDesc = descBuf.toString().trim()
                            activeTag = ""
                        } else if (tag == "p" || tag == "div" || tag == "li" || tag == "tr") {
                            descBuf.append("\n")
                        }
                    } else if (tag == "style" || tag == "stylemap") {
                        inStyle = false
                    } else if (!inPlacemark && !inStyle && tag == "name" && docName.isBlank()) {
                        docName = textBuf.toString().trim()
                    } else if (inPlacemark) {
                        when (tag) {
                            "name" -> if (!inExtendedData && pmName.isBlank()) pmName = textBuf.toString().trim()
                            "styleurl" -> if (!inExtendedData && pmStyleUrl.isBlank()) pmStyleUrl = textBuf.toString().trim()
                            "value", "simpledata" -> {
                                val v = textBuf.toString().trim()
                                if (currentDataKey.isNotBlank()) {
                                    val keyLow = currentDataKey.lowercase().replace(" ", "_")
                                    if (keyLow == "ts_raw_description") {
                                        pmMeta[keyLow] = v
                                    } else if (v.isNotBlank()) {
                                        when {
                                            keyLow.startsWith("ts_") -> pmMeta[keyLow] = v
                                            keyLow in setOf("status", "pin_status", "issue", "issue_reason", "done_note", "status_note", "edited_by", "edited_at", "edit_history") -> {
                                                pmGeneric[keyLow] = v
                                            }
                                            else -> pmExtra.add("$currentDataKey: $v")
                                        }
                                    }
                                }
                                if (tag == "simpledata") currentDataKey = ""
                            }
                            "data" -> currentDataKey = ""
                            "extendeddata" -> inExtendedData = false
                            "coordinates" -> {
                                val pts = parseCoordinates(textBuf.toString())
                                if (inPoint && pts.isNotEmpty()) {
                                    pmPoints.add(pts.first())
                                } else if (lineDepth > 0 && pts.size >= 2) {
                                    pmLines.add(pts)
                                }
                            }
                            "point" -> inPoint = false
                            "linestring", "polygon", "linearring" -> lineDepth = (lineDepth - 1).coerceAtLeast(0)
                            "placemark" -> {
                                val rawStatusField = pmMeta["ts_status"]
                                    ?: pmGeneric["status"]
                                    ?: pmGeneric["pin_status"]
                                var restoredStatus = rawStatusField?.let { normalizeStatus(it) }

                                var restoredReason = pmMeta["ts_issue_reason"]
                                    ?: pmGeneric["done_note"]
                                    ?: pmGeneric["issue_reason"]
                                    ?: pmGeneric["status_note"]
                                    ?: pmGeneric["issue"]
                                    ?: extractImplicitReasonFromStatus(rawStatusField)

                                if (restoredStatus == null) {
                                    restoredStatus = when {
                                        !pmGeneric["done_note"].isNullOrBlank() -> "done"
                                        !pmGeneric["issue_reason"].isNullOrBlank() || !pmGeneric["issue"].isNullOrBlank() -> "issue"
                                        else -> null
                                    }
                                }

                                var restoredEditedBy = pmMeta["ts_edited_by"] ?: pmGeneric["edited_by"] ?: ""
                                val restoredEditedAt = pmMeta["ts_edited_at"]?.toLongOrNull()
                                    ?: pmGeneric["edited_at"]?.toLongOrNull()
                                    ?: 0L
                                val restoredHistory = pmMeta["ts_edit_history"] ?: pmGeneric["edit_history"] ?: ""
                                var restoredIsEdited = pmMeta["ts_is_edited"]?.equals("true", true) == true
                                val rawDescFromMeta = pmMeta["ts_raw_description"]

                                val cleanedDescLines = mutableListOf<String>()
                                if (rawDescFromMeta != null) {
                                    if (rawDescFromMeta.isNotBlank()) cleanedDescLines.add(rawDescFromMeta)
                                } else if (pmDesc.isNotBlank()) {
                                    stripHtml(pmDesc).lines().forEach { line ->
                                        val trimmed = line.trim()
                                        val low = trimmed.lowercase()
                                        when {
                                            low.startsWith("status:") -> {
                                                if (restoredStatus == null) {
                                                    restoredStatus = normalizeStatus(trimmed.substringAfter(":"))
                                                }
                                            }
                                            low.startsWith("done note:") -> {
                                                val noteVal = trimmed.substringAfter(":").trim()
                                                if (restoredReason.isBlank() && noteVal.isNotBlank()) {
                                                    restoredReason = noteVal
                                                }
                                                if (restoredStatus == null) {
                                                    restoredStatus = "done"
                                                }
                                            }
                                            low.startsWith("issue reason:") || low.startsWith("issue:") -> {
                                                val issueVal = trimmed.substringAfter(":").trim()
                                                if (restoredReason.isBlank() && issueVal.isNotBlank()) {
                                                    restoredReason = issueVal
                                                }
                                                if (restoredStatus == null) {
                                                    restoredStatus = "issue"
                                                }
                                            }
                                            low.startsWith("edited by:") -> {
                                                if (restoredEditedBy.isBlank()) {
                                                    restoredEditedBy = trimmed.substringAfter(":").trim()
                                                    restoredIsEdited = true
                                                }
                                            }
                                            else -> {
                                                if (trimmed.isNotBlank()) cleanedDescLines.add(line)
                                            }
                                        }
                                    }
                                }

                                if (restoredStatus == null) {
                                    restoredStatus = statusFromStyleUrl(pmStyleUrl)
                                }

                                val finalStatus = restoredStatus ?: "pending"
                                if (finalStatus != "pending" || restoredReason.isNotBlank() ||
                                    restoredEditedBy.isNotBlank() || restoredHistory.isNotBlank()
                                ) {
                                    restoredIsEdited = true
                                }

                                val fullDesc = buildString {
                                    val baseDesc = cleanedDescLines.joinToString("\n").trim()
                                    if (baseDesc.isNotBlank()) append(baseDesc)
                                    if (pmExtra.isNotEmpty()) {
                                        if (isNotEmpty()) append("\n")
                                        append(pmExtra.joinToString("\n"))
                                    }
                                }

                                pmPoints.forEach { (lng, lat) ->
                                    pins.add(
                                        PinItem(
                                            id = UUID.randomUUID().toString(),
                                            layerId = layerId,
                                            name = pmName.ifBlank { "Pin ${pins.size + 1}" },
                                            description = fullDesc,
                                            lat = lat,
                                            lng = lng,
                                            status = finalStatus,
                                            issueReason = restoredReason,
                                            updatedAt = now,
                                            isEdited = restoredIsEdited,
                                            editedBy = restoredEditedBy,
                                            editedAt = restoredEditedAt,
                                            editHistory = restoredHistory
                                        )
                                    )
                                }
                                pmLines.forEach { pts ->
                                    val encoded = pts.joinToString(";") { "${it.first},${it.second}" }
                                    tracks.add(
                                        TrackItem(
                                            id = UUID.randomUUID().toString(),
                                            layerId = layerId,
                                            name = pmName.ifBlank { "Track ${tracks.size + 1}" },
                                            coords = encoded,
                                            updatedAt = now
                                        )
                                    )
                                }
                                inPlacemark = false
                                inPoint = false
                                lineDepth = 0
                                inDescription = false
                                inExtendedData = false
                                currentDataKey = ""
                            }
                        }
                    }
                    if (!inDescription) activeTag = ""
                }
            }
            event = parser.next()
        }
        return ParsedKml(pins, tracks, docName)
    }

    private fun parseCoordinates(raw: String): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        val len = raw.length
        var i = 0
        while (i < len) {
            while (i < len && raw[i].isWhitespace()) i++
            if (i >= len) break
            var end = i
            while (end < len && !raw[end].isWhitespace()) end++
            val c1 = raw.indexOf(',', i)
            if (c1 in (i + 1) until end) {
                val c2 = raw.indexOf(',', c1 + 1)
                val latEnd = if (c2 in (c1 + 1) until end) c2 else end
                val lng = raw.substring(i, c1).toDoubleOrNull()
                val lat = raw.substring(c1 + 1, latEnd).toDoubleOrNull()
                if (lng != null && lat != null && lat.isFinite() && lng.isFinite() &&
                    lat in -90.0..90.0 && lng in -180.0..180.0
                ) {
                    out.add(lng to lat)
                }
            }
            i = end + 1
        }
        return out
    }
}

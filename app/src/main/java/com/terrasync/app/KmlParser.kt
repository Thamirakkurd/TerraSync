package com.terrasync.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class ParsedKml(
    val pins: List<PinItem>,
    val tracks: List<TrackItem>
)

object KmlParser {

    fun parse(inputStream: InputStream, layerId: String): ParsedKml {
        val raw = inputStream.readBytes()
        val kmlBytes = if (raw.size >= 2 && raw[0] == 0x50.toByte() && raw[1] == 0x4B.toByte()) {
            extractKmlFromKmz(raw) ?: return ParsedKml(emptyList(), emptyList())
        } else {
            raw
        }
        return parseKmlXml(kmlBytes, layerId)
    }

    private fun extractKmlFromKmz(bytes: ByteArray): ByteArray? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.lowercase().endsWith(".kml")) {
                    return zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun normalizeStatus(raw: String): String {
        return when (raw.trim().lowercase()) {
            "done", "completed", "complete" -> "done"
            "issue", "problem", "error", "inaccessible", "no bill", "need electricity" -> "issue"
            else -> "pending"
        }
    }

    private fun parseKmlXml(bytes: ByteArray, layerId: String): ParsedKml {
        val pins = mutableListOf<PinItem>()
        val tracks = mutableListOf<TrackItem>()
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
        var inPoint = false
        var inLine = false
        var inExtendedData = false
        var currentDataKey = ""
        var activeTag = ""
        val textBuf = StringBuilder()

        var pmName = ""
        var pmDesc = ""
        val pmExtra = mutableListOf<String>()
        val pmMeta = mutableMapOf<String, String>()
        val pmPoints = mutableListOf<Pair<Double, Double>>()
        val pmLines = mutableListOf<List<Pair<Double, Double>>>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.substringAfterLast(':').lowercase()
                    activeTag = tag
                    textBuf.setLength(0)
                    when (tag) {
                        "placemark" -> {
                            inPlacemark = true
                            inPoint = false
                            inLine = false
                            inExtendedData = false
                            currentDataKey = ""
                            pmName = ""
                            pmDesc = ""
                            pmExtra.clear()
                            pmMeta.clear()
                            pmPoints.clear()
                            pmLines.clear()
                        }
                        "point" -> inPoint = true
                        "linestring", "polygon", "linearring" -> inLine = true
                        "extendeddata" -> inExtendedData = true
                        "data", "simpledata" -> if (inPlacemark) {
                            currentDataKey = parser.getAttributeValue(null, "name")?.trim().orEmpty()
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                    if (inPlacemark && (activeTag == "name" || activeTag == "description" || activeTag == "coordinates" || activeTag == "value" || activeTag == "simpledata")) {
                        textBuf.append(parser.text ?: "")
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name.substringAfterLast(':').lowercase()
                    if (inPlacemark) {
                        when (tag) {
                            "name" -> if (!inExtendedData && pmName.isBlank()) pmName = textBuf.toString().trim()
                            "description" -> if (!inExtendedData && pmDesc.isBlank()) pmDesc = textBuf.toString().trim()
                            "value", "simpledata" -> {
                                val v = textBuf.toString().trim()
                                if (currentDataKey.isNotBlank() && v.isNotBlank()) {
                                    val keyLow = currentDataKey.lowercase()
                                    if (keyLow.startsWith("ts_")) {
                                        pmMeta[keyLow] = v
                                    } else {
                                        pmExtra.add("$currentDataKey: $v")
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
                                } else if (inLine && pts.size >= 2) {
                                    pmLines.add(pts)
                                }
                            }
                            "point" -> inPoint = false
                            "linestring", "polygon", "linearring" -> inLine = false
                            "placemark" -> {
                                // Restore status/notes from ExtendedData first, or fallback to legacy TerraSync description lines
                                var restoredStatus = pmMeta["ts_status"]?.let { normalizeStatus(it) }
                                var restoredReason = pmMeta["ts_issue_reason"] ?: ""
                                var restoredEditedBy = pmMeta["ts_edited_by"] ?: ""
                                val restoredEditedAt = pmMeta["ts_edited_at"]?.toLongOrNull() ?: 0L
                                val restoredHistory = pmMeta["ts_edit_history"] ?: ""
                                var restoredIsEdited = pmMeta["ts_is_edited"]?.equals("true", true) == true
                                val rawDescFromMeta = pmMeta["ts_raw_description"]

                                val cleanedDescLines = mutableListOf<String>()
                                if (rawDescFromMeta != null) {
                                    if (rawDescFromMeta.isNotBlank()) cleanedDescLines.add(rawDescFromMeta)
                                } else if (pmDesc.isNotBlank()) {
                                    pmDesc.lines().forEach { line ->
                                        val trimmed = line.trim()
                                        val low = trimmed.lowercase()
                                        when {
                                            low.startsWith("status:") -> {
                                                if (restoredStatus == null) {
                                                    restoredStatus = normalizeStatus(trimmed.substringAfter(":"))
                                                }
                                            }
                                            low.startsWith("done note:") || low.startsWith("issue:") -> {
                                                if (restoredReason.isBlank()) {
                                                    restoredReason = trimmed.substringAfter(":").trim()
                                                }
                                            }
                                            low.startsWith("edited by:") -> {
                                                if (restoredEditedBy.isBlank()) {
                                                    restoredEditedBy = trimmed.substringAfter(":").trim()
                                                    restoredIsEdited = true
                                                }
                                            }
                                            else -> cleanedDescLines.add(line)
                                        }
                                    }
                                }

                                val finalStatus = restoredStatus ?: "pending"
                                if (finalStatus != "pending" || restoredEditedBy.isNotBlank() || restoredHistory.isNotBlank()) {
                                    restoredIsEdited = restoredIsEdited || restoredEditedBy.isNotBlank() || restoredHistory.isNotBlank()
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
                                inLine = false
                                inExtendedData = false
                                currentDataKey = ""
                            }
                        }
                    }
                    activeTag = ""
                }
            }
            event = parser.next()
        }
        return ParsedKml(pins, tracks)
    }

    private fun parseCoordinates(raw: String): List<Pair<Double, Double>> {
        val out = mutableListOf<Pair<Double, Double>>()
        for (token in raw.trim().split(Regex("\\s+"))) {
            val parts = token.split(",")
            if (parts.size >= 2) {
                val lng = parts[0].toDoubleOrNull()
                val lat = parts[1].toDoubleOrNull()
                if (lng != null && lat != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                    out.add(lng to lat)
                }
            }
        }
        return out
    }
}

package com.terrasync.app

import java.util.Locale

data class KmlPointMeta(
    val status: String,
    val reason: String? = null,
    val editedBy: String? = null,
    val note: String? = null
)

object KmlStatusCodec {

    fun encodeExtendedDataXml(pin: PinItem): String {
        val status = normalizeStatus(pin.status)
        val reason = xmlEscape(pin.issueReason)
        val editedBy = xmlEscape(pin.editedBy)
        val note = xmlEscape(pin.description)

        return buildString {
            appendLine("<ExtendedData>")
            appendLine("  <Data name=\"ts_status\"><value>$status</value></Data>")
            if (reason.isNotEmpty()) appendLine("  <Data name=\"ts_issue_reason\"><value>$reason</value></Data>")
            if (editedBy.isNotEmpty()) appendLine("  <Data name=\"ts_edited_by\"><value>$editedBy</value></Data>")
            if (note.isNotEmpty()) appendLine("  <Data name=\"ts_raw_description\"><value>$note</value></Data>")
            appendLine("</ExtendedData>")
        }.trim()
    }

    fun decodeFromXmlBlock(xmlBlock: String): KmlPointMeta {
        val statusVal = extractTagValue(xmlBlock, "ts_status")
            ?: extractTagValue(xmlBlock, "terrasync_status")
            ?: extractTagValue(xmlBlock, "status")
        val reasonVal = extractTagValue(xmlBlock, "ts_issue_reason")
            ?: extractTagValue(xmlBlock, "terrasync_reason")
            ?: extractTagValue(xmlBlock, "reason")
        val editedByVal = extractTagValue(xmlBlock, "ts_edited_by")
            ?: extractTagValue(xmlBlock, "terrasync_team")
        val noteVal = extractTagValue(xmlBlock, "ts_raw_description")
            ?: extractTagValue(xmlBlock, "terrasync_note")

        return KmlPointMeta(
            status = normalizeStatus(statusVal),
            reason = reasonVal?.takeIf { it.isNotBlank() },
            editedBy = editedByVal?.takeIf { it.isNotBlank() },
            note = noteVal?.takeIf { it.isNotBlank() }
        )
    }

    fun normalizeStatus(raw: String?): String {
        val s = raw?.trim()?.lowercase(Locale.US).orEmpty()
        return when {
            s.contains("done") || s.contains("complete") || s == "ok" -> "done"
            s.contains("issue") || s.contains("fail") || s.contains("problem") -> "issue"
            else -> "pending"
        }
    }

    private fun extractTagValue(xml: String, key: String): String? {
        val dataRegex = Regex(
            "<Data\\s+name=\"" + Regex.escape(key) + "\"\\s*>\\s*<value>(.*?)</value>\\s*</Data>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return dataRegex.find(xml)?.groupValues?.getOrNull(1)?.let { xmlUnescape(it.trim()) }
    }

    private fun xmlEscape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun xmlUnescape(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
}

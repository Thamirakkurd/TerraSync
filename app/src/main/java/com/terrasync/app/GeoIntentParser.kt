package com.terrasync.app

import android.net.Uri
import okhttp3.Request
import java.util.Locale

data class ParsedLocationIntent(
    val lat: Double?,
    val lng: Double?,
    val zoom: Double? = null,
    val label: String = "",
    val searchQuery: String = "",
    val sourceHint: String = "Shared Location",
    val needsRedirectResolution: Boolean = false,
    val originalUrl: String = ""
) {
    val hasValidCoordinates: Boolean
        get() = lat != null && lng != null &&
            lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0
}

/**
 * Parses incoming Android ACTION_VIEW location URIs from WhatsApp, Telegram, Google Maps,
 * Organic Maps (omaps.app / ge0.me / geo.me), and OpenStreetMap.
 */
object GeoIntentParser {

    private const val GE0_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val COORD_WITH_OPTIONAL_LABEL = Regex(
        """^(?:loc:)?\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)(?:\s*,\s*-?\d+(?:\.\d+)?)?(?:\s*\((.+)\))?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val AT_COORDS_REGEX = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)(?:,(\d+(?:\.\d+)?)z)?""")
    private val BANG_3D4D_REGEX = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val OSM_HASH_REGEX = Regex("""map=(\d+(?:\.\d+)?)/(-?\d+(?:\.\d+)?)/(-?\d+(?:\.\d+)?)""")
    private val GOOGLE_PLACE_SEG_REGEX = Regex("""/maps/place/([^/]+)""")
    private val GOOGLE_SEARCH_SEG_REGEX = Regex("""/maps/search/([^/]+)""")
    private val EMBEDDED_PAIR_REGEX = Regex("""(-?\d{1,2}\.\d{3,}),\s*(-?\d{1,3}\.\d{3,})""")

    fun isSupportedLocationUri(uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
        if (scheme == "geo") return true
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase(Locale.US) ?: return false
        val path = uri.path?.lowercase(Locale.US).orEmpty()
        return host == "maps.google.com" ||
            host == "maps.app.goo.gl" ||
            host == "geo.me" ||
            host == "ge0.me" ||
            host == "omaps.app" ||
            host == "openstreetmap.org" ||
            host == "www.openstreetmap.org" ||
            ((host == "www.google.com" || host == "google.com" || host == "goo.gl") && path.startsWith("/maps"))
    }

    fun parse(uri: Uri): ParsedLocationIntent? {
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        return when (scheme) {
            "geo" -> parseGeoScheme(uri)
            "http", "https" -> parseHttpMapUrl(uri)
            else -> null
        }
    }

    private fun parseGeoScheme(uri: Uri): ParsedLocationIntent? {
        val ssp = uri.schemeSpecificPart.orEmpty()
        val qIdx = ssp.indexOf('?')
        val coordsPart = (if (qIdx >= 0) ssp.substring(0, qIdx) else ssp).trim()
        val queryPart = if (qIdx >= 0) ssp.substring(qIdx + 1) else ""
        val params = parseQueryString(queryPart)

        val baseMatch = COORD_WITH_OPTIONAL_LABEL.matchEntire(coordsPart)
        val baseLat = baseMatch?.groupValues?.get(1)?.toDoubleOrNull()
        val baseLng = baseMatch?.groupValues?.get(2)?.toDoubleOrNull()
        val baseLabel = baseMatch?.groupValues?.get(3)?.trim().orEmpty()
        val baseValid = isValidCoord(baseLat, baseLng)
        val baseNonZero = baseValid && (baseLat != 0.0 || baseLng != 0.0)

        val zoom = params["z"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 2.0..21.0 }
        val rawQ = params["q"]?.trim().orEmpty()

        var qLat: Double? = null
        var qLng: Double? = null
        var qLabel = ""
        var searchQuery = ""

        if (rawQ.isNotEmpty()) {
            val qMatch = COORD_WITH_OPTIONAL_LABEL.matchEntire(rawQ)
            if (qMatch != null) {
                val lat = qMatch.groupValues[1].toDoubleOrNull()
                val lng = qMatch.groupValues[2].toDoubleOrNull()
                if (isValidCoord(lat, lng)) {
                    qLat = lat
                    qLng = lng
                    qLabel = qMatch.groupValues[3].trim()
                }
            } else {
                // Check for embedded coordinates inside q (e.g. "36.8669, 42.9900 (Zakho)" or "Label @ 36.86,42.99")
                val emb = EMBEDDED_PAIR_REGEX.find(rawQ)
                if (emb != null) {
                    val lat = emb.groupValues[1].toDoubleOrNull()
                    val lng = emb.groupValues[2].toDoubleOrNull()
                    if (isValidCoord(lat, lng)) {
                        qLat = lat
                        qLng = lng
                        val parenLbl = Regex("""\(([^)]+)\)""").find(rawQ)?.groupValues?.get(1)?.trim().orEmpty()
                        qLabel = parenLbl.ifBlank {
                            rawQ.removeRange(emb.range).replace(Regex("""^[(\s@,-]+|[)\s,-]+$"""), "").trim()
                        }
                    } else {
                        searchQuery = rawQ
                    }
                } else {
                    if (baseNonZero) qLabel = rawQ else searchQuery = rawQ
                }
            }
        }

        val finalLat: Double?
        val finalLng: Double?
        if (isValidCoord(qLat, qLng) && (qLat != 0.0 || qLng != 0.0 || !baseNonZero)) {
            finalLat = qLat
            finalLng = qLng
        } else if (baseNonZero || (baseValid && searchQuery.isEmpty())) {
            finalLat = baseLat
            finalLng = baseLng
        } else {
            finalLat = null
            finalLng = null
        }

        val finalLabel = cleanLabel(qLabel.ifBlank { baseLabel })
        if (finalLat == null && searchQuery.isBlank()) return null

        return ParsedLocationIntent(
            lat = finalLat,
            lng = finalLng,
            zoom = zoom,
            label = finalLabel,
            searchQuery = searchQuery,
            sourceHint = "Shared Location",
            originalUrl = uri.toString()
        )
    }

    private fun parseHttpMapUrl(uri: Uri): ParsedLocationIntent? {
        val host = uri.host?.lowercase(Locale.US).orEmpty()
        val path = uri.path.orEmpty()
        val full = uri.toString()

        // 1. Short redirect links (Google Maps share links from WhatsApp/Telegram)
        if (host == "maps.app.goo.gl" || (host == "goo.gl" && path.lowercase(Locale.US).startsWith("/maps"))) {
            return ParsedLocationIntent(
                lat = null,
                lng = null,
                sourceHint = "Google Maps",
                needsRedirectResolution = true,
                originalUrl = full
            )
        }

        // 2. Organic Maps / ge0 short links (omaps.app, ge0.me, geo.me)
        if (host == "omaps.app" || host == "ge0.me" || host == "geo.me") {
            val ge0Result = parseGe0Url(uri, host)
            if (ge0Result != null) return ge0Result
            // Fallback to redirect resolution if geo.me uses a redirect
            return ParsedLocationIntent(
                lat = null,
                lng = null,
                sourceHint = "Shared Map Link",
                needsRedirectResolution = true,
                originalUrl = full
            )
        }

        // 3. OpenStreetMap URLs
        if (host == "openstreetmap.org" || host == "www.openstreetmap.org") {
            val mlat = uri.getQueryParameter("mlat")?.toDoubleOrNull()
            val mlon = uri.getQueryParameter("mlon")?.toDoubleOrNull()
            val qLat = uri.getQueryParameter("lat")?.toDoubleOrNull()
            val qLon = (uri.getQueryParameter("lon") ?: uri.getQueryParameter("lng"))?.toDoubleOrNull()
            val qZoom = uri.getQueryParameter("zoom")?.toDoubleOrNull()

            var hashZoom: Double? = null
            var hashLat: Double? = null
            var hashLng: Double? = null
            val frag = uri.fragment.orEmpty()
            OSM_HASH_REGEX.find(frag)?.let { m ->
                hashZoom = m.groupValues[1].toDoubleOrNull()
                hashLat = m.groupValues[2].toDoubleOrNull()
                hashLng = m.groupValues[3].toDoubleOrNull()
            }

            val finalLat = when {
                isValidCoord(mlat, mlon) -> mlat
                isValidCoord(qLat, qLon) -> qLat
                isValidCoord(hashLat, hashLng) -> hashLat
                else -> null
            }
            val finalLng = when {
                isValidCoord(mlat, mlon) -> mlon
                isValidCoord(qLat, qLon) -> qLon
                isValidCoord(hashLat, hashLng) -> hashLng
                else -> null
            }
            val queryText = uri.getQueryParameter("query")?.trim().orEmpty()
            if (finalLat != null && finalLng != null) {
                return ParsedLocationIntent(
                    lat = finalLat,
                    lng = finalLng,
                    zoom = (qZoom ?: hashZoom)?.takeIf { it.isFinite() && it in 2.0..21.0 },
                    label = cleanLabel(queryText),
                    sourceHint = "OpenStreetMap",
                    originalUrl = full
                )
            }
            if (queryText.isNotBlank()) {
                return ParsedLocationIntent(
                    lat = null,
                    lng = null,
                    searchQuery = queryText,
                    sourceHint = "OpenStreetMap",
                    originalUrl = full
                )
            }
            return null
        }

        // 4. Google Maps URLs (maps.google.com, google.com/maps, www.google.com/maps)
        if (host == "maps.google.com" || host == "www.google.com" || host == "google.com") {
            return parseGoogleMapsUrl(uri, full)
        }

        return null
    }

    private fun parseGoogleMapsUrl(uri: Uri, full: String): ParsedLocationIntent? {
        val path = uri.path.orEmpty()
        val placeLabelFromPath = GOOGLE_PLACE_SEG_REGEX.find(path)?.groupValues?.get(1)?.let { seg ->
            decodeUrlSegment(seg)
        }.orEmpty()
        val searchFromPath = GOOGLE_SEARCH_SEG_REGEX.find(path)?.groupValues?.get(1)?.let { seg ->
            decodeUrlSegment(seg)
        }.orEmpty()

        val atMatch = AT_COORDS_REGEX.find(full)
        val atLat = atMatch?.groupValues?.get(1)?.toDoubleOrNull()
        val atLng = atMatch?.groupValues?.get(2)?.toDoubleOrNull()
        val atZoom = atMatch?.groupValues?.get(3)?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 2.0..21.0 }
        val paramZoom = uri.getQueryParameter("z")?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 2.0..21.0 }
        val zoom = paramZoom ?: atZoom

        // Exact pin coordinates (!3d...!4d...) take precedence over viewport center (@lat,lng)
        val bangMatch = BANG_3D4D_REGEX.find(full)
        if (bangMatch != null) {
            val lat = bangMatch.groupValues[1].toDoubleOrNull()
            val lng = bangMatch.groupValues[2].toDoubleOrNull()
            if (isValidCoord(lat, lng)) {
                return ParsedLocationIntent(
                    lat = lat,
                    lng = lng,
                    zoom = zoom,
                    label = cleanLabel(placeLabelFromPath),
                    sourceHint = "Google Maps",
                    originalUrl = full
                )
            }
        }

        // Check query params: q, query, ll, sll, center, daddr, destination
        val paramKeys = listOf("q", "query", "destination", "daddr", "ll", "sll", "center")
        var fallbackQuery = ""
        for (key in paramKeys) {
            val v = uri.getQueryParameter(key)?.trim().orEmpty()
            if (v.isEmpty()) continue
            val m = COORD_WITH_OPTIONAL_LABEL.matchEntire(v)
            if (m != null) {
                val lat = m.groupValues[1].toDoubleOrNull()
                val lng = m.groupValues[2].toDoubleOrNull()
                if (isValidCoord(lat, lng)) {
                    val lbl = m.groupValues[3].trim().ifBlank { placeLabelFromPath }
                    return ParsedLocationIntent(
                        lat = lat,
                        lng = lng,
                        zoom = zoom,
                        label = cleanLabel(lbl),
                        sourceHint = "Google Maps",
                        originalUrl = full
                    )
                }
            }
            val emb = EMBEDDED_PAIR_REGEX.find(v)
            if (emb != null) {
                val lat = emb.groupValues[1].toDoubleOrNull()
                val lng = emb.groupValues[2].toDoubleOrNull()
                if (isValidCoord(lat, lng)) {
                    val parenLbl = Regex("""\(([^)]+)\)""").find(v)?.groupValues?.get(1)?.trim().orEmpty()
                    val lbl = parenLbl.ifBlank { placeLabelFromPath }
                    return ParsedLocationIntent(
                        lat = lat,
                        lng = lng,
                        zoom = zoom,
                        label = cleanLabel(lbl),
                        sourceHint = "Google Maps",
                        originalUrl = full
                    )
                }
            }
            if (fallbackQuery.isEmpty() && key in setOf("q", "query", "destination", "daddr")) {
                fallbackQuery = v
            }
        }

        // Check @lat,lng,zoom in path
        if (isValidCoord(atLat, atLng)) {
            return ParsedLocationIntent(
                lat = atLat,
                lng = atLng,
                zoom = zoom,
                label = cleanLabel(placeLabelFromPath.ifBlank { fallbackQuery }),
                sourceHint = "Google Maps",
                originalUrl = full
            )
        }

        // Check /maps/search/<coords> or /maps/place/<coords>
        val candidateText = fallbackQuery.ifBlank { searchFromPath }.ifBlank { placeLabelFromPath }
        if (candidateText.isNotBlank()) {
            val m = COORD_WITH_OPTIONAL_LABEL.matchEntire(candidateText)
            if (m != null) {
                val lat = m.groupValues[1].toDoubleOrNull()
                val lng = m.groupValues[2].toDoubleOrNull()
                if (isValidCoord(lat, lng)) {
                    return ParsedLocationIntent(
                        lat = lat,
                        lng = lng,
                        zoom = zoom,
                        label = cleanLabel(m.groupValues[3]),
                        sourceHint = "Google Maps",
                        originalUrl = full
                    )
                }
            }
            return ParsedLocationIntent(
                lat = null,
                lng = null,
                zoom = zoom,
                searchQuery = candidateText,
                sourceHint = "Google Maps",
                originalUrl = full
            )
        }

        return null
    }

    private fun parseGe0Url(uri: Uri, host: String): ParsedLocationIntent? {
        // First check explicit lat/lng or q parameters if present
        val qLat = uri.getQueryParameter("lat")?.toDoubleOrNull()
        val qLng = (uri.getQueryParameter("lng") ?: uri.getQueryParameter("lon"))?.toDoubleOrNull()
        if (isValidCoord(qLat, qLng)) {
            val lbl = uri.getQueryParameter("name") ?: uri.getQueryParameter("q") ?: ""
            return ParsedLocationIntent(
                lat = qLat,
                lng = qLng,
                zoom = uri.getQueryParameter("z")?.toDoubleOrNull(),
                label = cleanLabel(lbl),
                sourceHint = if (host == "omaps.app") "Organic Maps" else "Shared Map Link",
                originalUrl = uri.toString()
            )
        }

        val segs = uri.pathSegments.orEmpty()
        val code = segs.firstOrNull()?.trim().orEmpty()
        if (code.length !in 4..11) return null
        val decoded = decodeGe0Code(code) ?: return null
        val rawName = segs.getOrNull(1)?.let { decodeUrlSegment(it).replace('_', ' ') }.orEmpty()
        return ParsedLocationIntent(
            lat = decoded.first,
            lng = decoded.second,
            zoom = decoded.third,
            label = cleanLabel(rawName),
            sourceHint = if (host == "omaps.app") "Organic Maps" else "Shared Map Link",
            originalUrl = uri.toString()
        )
    }

    /** Decodes a Maps.me / Organic Maps ge0 Base64URL short coordinate code into (lat, lng, zoom). */
    fun decodeGe0Code(code: String): Triple<Double, Double, Double>? {
        if (code.length < 4) return null
        val zoomIdx = GE0_ALPHABET.indexOf(code[0])
        if (zoomIdx < 0) return null
        val zoom = (zoomIdx / 4.0) + 4.0

        val coordChars = code.substring(1).take(10)
        var latBits = 0L
        var lonBits = 0L
        for (ch in coordChars) {
            val a = GE0_ALPHABET.indexOf(ch)
            if (a < 0) return null
            val lat3 = (((a shr 5) and 1) shl 2) or (((a shr 3) and 1) shl 1) or ((a shr 1) and 1)
            val lon3 = (((a shr 4) and 1) shl 2) or (((a shr 2) and 1) shl 1) or (a and 1)
            latBits = (latBits shl 3) or lat3.toLong()
            lonBits = (lonBits shl 3) or lon3.toLong()
        }
        val maxVal = (1L shl (coordChars.length * 3)).toDouble()
        val lat = ((latBits.toDouble() + 0.5) / maxVal) * 180.0 - 90.0
        val lng = ((lonBits.toDouble() + 0.5) / maxVal) * 360.0 - 180.0
        if (!isValidCoord(lat, lng)) return null
        return Triple(lat, lng, zoom.coerceIn(4.0, 20.0))
    }

    /**
     * Resolves short redirect map links (e.g. maps.app.goo.gl / goo.gl/maps) off the main thread
     * and parses the final destination URL or embedded coordinates.
     */
    fun resolveRedirectAndParse(shortUrl: String): ParsedLocationIntent? {
        return runCatching {
            val client = RoutingEngine.sharedOkHttpClient.newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
            val req = Request.Builder().url(shortUrl).get().build()
            client.newCall(req).execute().use { resp ->
                val finalUrl = resp.request.url.toString()
                if (finalUrl != shortUrl) {
                    val parsedFinal = parse(Uri.parse(finalUrl))
                    if (parsedFinal != null && !parsedFinal.needsRedirectResolution &&
                        (parsedFinal.hasValidCoordinates || parsedFinal.searchQuery.isNotBlank())
                    ) {
                        return@use parsedFinal
                    }
                }
                // Fallback: scan limited response body for embedded Google Maps coords/URL
                val snippet = resp.body?.charStream()?.use { reader ->
                    val buf = CharArray(16384)
                    val n = reader.read(buf)
                    if (n > 0) String(buf, 0, n) else ""
                }.orEmpty()
                if (snippet.isNotEmpty()) {
                    BANG_3D4D_REGEX.find(snippet)?.let { m ->
                        val lat = m.groupValues[1].toDoubleOrNull()
                        val lng = m.groupValues[2].toDoubleOrNull()
                        if (isValidCoord(lat, lng)) {
                            return@use ParsedLocationIntent(lat, lng, 16.0, "", "", "Google Maps", false, finalUrl)
                        }
                    }
                    AT_COORDS_REGEX.find(snippet)?.let { m ->
                        val lat = m.groupValues[1].toDoubleOrNull()
                        val lng = m.groupValues[2].toDoubleOrNull()
                        val z = m.groupValues[3].toDoubleOrNull()
                        if (isValidCoord(lat, lng)) {
                            return@use ParsedLocationIntent(lat, lng, z, "", "", "Google Maps", false, finalUrl)
                        }
                    }
                }
                null
            }
        }.getOrNull()
    }

    private fun parseQueryString(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        query.split('&').forEach { part ->
            if (part.isBlank()) return@forEach
            val eq = part.indexOf('=')
            if (eq > 0) {
                val k = Uri.decode(part.substring(0, eq)).lowercase(Locale.US)
                val v = Uri.decode(part.substring(eq + 1).replace('+', ' '))
                out[k] = v
            }
        }
        return out
    }

    private fun decodeUrlSegment(raw: String): String {
        return Uri.decode(raw.replace('+', ' ')).trim()
    }

    private fun cleanLabel(raw: String): String {
        val clean = raw.replace(Regex("[\\r\\n\\s]+"), " ").trim()
        if (clean.isEmpty()) return ""
        // Don't treat bare coordinates as a human place name
        if (COORD_WITH_OPTIONAL_LABEL.matches(clean)) return ""
        return clean.take(120)
    }

    private fun isValidCoord(lat: Double?, lng: Double?): Boolean {
        return lat != null && lng != null &&
            lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0
    }
}

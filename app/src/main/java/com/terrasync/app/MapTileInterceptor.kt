package com.terrasync.app

import android.content.Context
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * OkHttp interceptor for MapLibre HTTP traffic (tiles, glyphs, sprites, TileJSON):
 *  1. Serves bundled Kurdish-patched 64256-64511.pbf SDF glyph range (ێ/ڵ/ڕ at U+FB5A..U+FB63)
 *     locally with zero network latency (works online & offline).
 *  2. Serves vector tiles from downloaded local PMTiles archives ([LocalPmTilesStore]) first
 *     (<0.5 ms, works 100% offline in Airplane Mode for both Day & Night styles).
 *  3. Patches Kurdish Sorani labels inside MVT vector tiles via [MvtKurdishPatcher] so both
 *     online and offline basemap labels join properly in true RTL order.
 *  4. Persistently caches glyphs, sprites, and TileJSON on disk so Airplane Mode renders cleanly.
 */
class MapTileInterceptor(
    private val contextProvider: () -> Context?
) : Interceptor {

    companion object {
        private const val OPENFREEMAP_HOST = "tiles.openfreemap.org"
        private const val KURDISH_GLYPH_RANGE_SUFFIX = "/64256-64511.pbf"
        private const val KURDISH_GLYPH_ASSET_PATH = "fonts/64256-64511.pbf"
        private const val PUNCT_GLYPH_RANGE_SUFFIX = "/8192-8447.pbf"
        private const val PUNCT_GLYPH_ASSET_PATH = "fonts/8192-8447.pbf"

        private val TILE_ZXY_REGEX = Regex(""".*/(\d{1,2})/(\d+)/(\d+)\.pbf$""")
        private val PROTOBUF_MEDIA = "application/x-protobuf".toMediaType()
        private val JSON_MEDIA = "application/json".toMediaType()
        private val PNG_MEDIA = "image/png".toMediaType()
        private val OCTET_MEDIA = "application/octet-stream".toMediaType()

        private const val SYNTHETIC_OFFLINE_TILEJSON = """{
  "tilejson": "3.0.0",
  "name": "TerraSync Offline",
  "scheme": "xyz",
  "minzoom": 0,
  "maxzoom": 14,
  "bounds": [-180, -85.05112877980659, 180, 85.05112877980659],
  "tiles": ["https://tiles.openfreemap.org/planet/local/{z}/{x}/{y}.pbf"]
}"""

        @Volatile private var cachedKurdishGlyphPbf: ByteArray? = null
        @Volatile private var cachedPunctGlyphPbf: ByteArray? = null

        internal fun webCacheDir(ctx: Context): File =
            File(LocalPmTilesStore.dir(ctx), "web_cache").apply { mkdirs() }

        internal fun cacheKeyForUrl(url: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(32)
            for (i in 0 until 16) {
                sb.append(String.format("%02x", digest[i]))
            }
            return sb.toString()
        }

        internal fun loadBundledKurdishGlyphPbf(ctx: Context?): ByteArray? {
            cachedKurdishGlyphPbf?.let { return it }
            val c = ctx ?: return null
            return runCatching {
                c.assets.open(KURDISH_GLYPH_ASSET_PATH).use { it.readBytes() }
            }.getOrNull()?.takeIf { it.size > 256 }?.also {
                cachedKurdishGlyphPbf = it
            }
        }

        internal fun loadBundledPunctGlyphPbf(ctx: Context?): ByteArray? {
            cachedPunctGlyphPbf?.let { return it }
            val c = ctx ?: return null
            return runCatching {
                c.assets.open(PUNCT_GLYPH_ASSET_PATH).use { it.readBytes() }
            }.getOrNull()?.takeIf { it.size > 256 }?.also {
                cachedPunctGlyphPbf = it
            }
        }

        private fun isGzipBytes(bytes: ByteArray): Boolean {
            return bytes.size >= 2 &&
                (bytes[0].toInt() and 0xFF) == 0x1F &&
                (bytes[1].toInt() and 0xFF) == 0x8B
        }

        private fun gunzipIfNeeded(bytes: ByteArray): ByteArray {
            if (!isGzipBytes(bytes)) return bytes
            return runCatching {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
            }.getOrDefault(bytes)
        }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val url = req.url
        if (!url.host.equals(OPENFREEMAP_HOST, ignoreCase = true)) {
            return chain.proceed(req)
        }

        val ctx = contextProvider()
        val path = url.encodedPath

        // 1. Patched Kurdish SDF glyph ranges (64256-64511.pbf for ێ/ڵ/ڕ & 8192-8447.pbf for invisible ZWJ/ZWNJ)
        if (path.contains("/fonts/") && path.endsWith(KURDISH_GLYPH_RANGE_SUFFIX)) {
            val bundled = loadBundledKurdishGlyphPbf(ctx)
            if (bundled != null) {
                return buildOkResponse(req, bundled, PROTOBUF_MEDIA, "public, max-age=86400")
            }
        }
        if (path.contains("/fonts/") && path.endsWith(PUNCT_GLYPH_RANGE_SUFFIX)) {
            val bundled = loadBundledPunctGlyphPbf(ctx)
            if (bundled != null) {
                return buildOkResponse(req, bundled, PROTOBUF_MEDIA, "public, max-age=86400")
            }
        }

        // 2. Vector tiles ({z}/{x}/{y}.pbf)
        val tileMatch = TILE_ZXY_REGEX.matchEntire(path)
        if (tileMatch != null) {
            val z = tileMatch.groupValues[1].toIntOrNull()
            val x = tileMatch.groupValues[2].toIntOrNull()
            val y = tileMatch.groupValues[3].toIntOrNull()
            if (z != null && x != null && y != null) {
                return handleVectorTile(chain, req, ctx, z, x, y)
            }
        }

        // 3. Other glyphs & sprites -> persistent disk cache (instant Airplane Mode rendering)
        if (path.contains("/fonts/") || path.contains("/sprites/")) {
            return handleCachedStaticAsset(chain, req, ctx, path)
        }

        // 4. TileJSON (/planet, /backup_planet) -> cache + offline synthetic fallback
        if (path == "/planet" || path == "/backup_planet" || path.endsWith("/planet.json")) {
            return handleTileJson(chain, req, ctx)
        }

        return chain.proceed(req)
    }

    private fun handleVectorTile(
        chain: Interceptor.Chain,
        req: Request,
        ctx: Context?,
        z: Int,
        x: Int,
        y: Int
    ): Response {
        // For zoom >= 6, serve directly from downloaded local PMTiles first (0.3ms, zero data)
        if (z >= 6) {
            when (val local = LocalPmTilesStore.readTile(ctx, z, x, y)) {
                is LocalPmTilesStore.TileLookup.Found -> {
                    val raw = if (local.isGzipped) gunzipIfNeeded(local.bytes) else local.bytes
                    val patched = MvtKurdishPatcher.patchTile(raw)
                    return buildOkResponse(req, patched, PROTOBUF_MEDIA, "public, max-age=3600")
                }
                LocalPmTilesStore.TileLookup.EmptyInBounds -> {
                    return buildEmptyResponse(req)
                }
                LocalPmTilesStore.TileLookup.Miss -> {
                    // Fall through to online network
                }
            }
        }

        // Online fetch + Kurdish MVT patching, with offline PMTiles fallback (e.g. z0..5 in Airplane Mode)
        return try {
            val resp = chain.proceed(req)
            if (resp.code == 200) {
                val mediaType = resp.body?.contentType() ?: PROTOBUF_MEDIA
                val bodyBytes = resp.body?.bytes()
                if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                    val raw = gunzipIfNeeded(bodyBytes)
                    val patched = MvtKurdishPatcher.patchTile(raw)
                    resp.newBuilder()
                        .removeHeader("Content-Encoding")
                        .removeHeader("Content-Length")
                        .body(patched.toResponseBody(mediaType))
                        .build()
                } else {
                    resp
                }
            } else {
                resp
            }
        } catch (ioe: IOException) {
            if (z < 6) {
                when (val local = LocalPmTilesStore.readTile(ctx, z, x, y)) {
                    is LocalPmTilesStore.TileLookup.Found -> {
                        val raw = if (local.isGzipped) gunzipIfNeeded(local.bytes) else local.bytes
                        val patched = MvtKurdishPatcher.patchTile(raw)
                        return buildOkResponse(req, patched, PROTOBUF_MEDIA, "public, max-age=3600")
                    }
                    LocalPmTilesStore.TileLookup.EmptyInBounds -> return buildEmptyResponse(req)
                    LocalPmTilesStore.TileLookup.Miss -> {}
                }
            }
            throw ioe
        }
    }

    private fun handleCachedStaticAsset(
        chain: Interceptor.Chain,
        req: Request,
        ctx: Context?,
        path: String
    ): Response {
        val mediaType = when {
            path.endsWith(".json") -> JSON_MEDIA
            path.endsWith(".png") -> PNG_MEDIA
            path.endsWith(".pbf") -> PROTOBUF_MEDIA
            else -> OCTET_MEDIA
        }
        val cacheFile = ctx?.let { File(webCacheDir(it), cacheKeyForUrl(req.url.toString())) }
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0L) {
            val bytes = runCatching { cacheFile.readBytes() }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                return buildOkResponse(req, bytes, mediaType, "public, max-age=604800")
            }
        }

        val resp = chain.proceed(req)
        if (resp.isSuccessful) {
            val respMedia = resp.body?.contentType() ?: mediaType
            val bodyBytes = resp.body?.bytes()
            if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                if (cacheFile != null) {
                    runCatching {
                        val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                        tmp.writeBytes(bodyBytes)
                        if (!tmp.renameTo(cacheFile)) {
                            tmp.copyTo(cacheFile, overwrite = true)
                            tmp.delete()
                        }
                    }
                }
                return resp.newBuilder()
                    .removeHeader("Content-Encoding")
                    .removeHeader("Content-Length")
                    .body(bodyBytes.toResponseBody(respMedia))
                    .build()
            }
        }
        return resp
    }

    private fun handleTileJson(chain: Interceptor.Chain, req: Request, ctx: Context?): Response {
        val cacheFile = ctx?.let { File(webCacheDir(it), "tilejson_${cacheKeyForUrl(req.url.toString())}.json") }
        try {
            val resp = chain.proceed(req)
            if (resp.isSuccessful) {
                val bodyBytes = resp.body?.bytes()
                if (bodyBytes != null && bodyBytes.isNotEmpty()) {
                    if (cacheFile != null) {
                        runCatching {
                            val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                            tmp.writeBytes(bodyBytes)
                            if (!tmp.renameTo(cacheFile)) {
                                tmp.copyTo(cacheFile, overwrite = true)
                                tmp.delete()
                            }
                        }
                    }
                    return resp.newBuilder()
                        .removeHeader("Content-Encoding")
                        .removeHeader("Content-Length")
                        .body(bodyBytes.toResponseBody(JSON_MEDIA))
                        .build()
                }
            }
            return resp
        } catch (ioe: IOException) {
            if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 32L) {
                val cached = runCatching { cacheFile.readBytes() }.getOrNull()
                if (cached != null && cached.isNotEmpty()) {
                    return buildOkResponse(req, cached, JSON_MEDIA, "public, max-age=3600")
                }
            }
            if (LocalPmTilesStore.hasAnyInstalledPack(ctx)) {
                return buildOkResponse(
                    req,
                    SYNTHETIC_OFFLINE_TILEJSON.toByteArray(Charsets.UTF_8),
                    JSON_MEDIA,
                    "public, max-age=3600"
                )
            }
            throw ioe
        }
    }

    private fun buildOkResponse(
        req: Request,
        bytes: ByteArray,
        mediaType: okhttp3.MediaType,
        cacheControl: String
    ): Response {
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Cache-Control", cacheControl)
            .body(bytes.toResponseBody(mediaType))
            .build()
    }

    private fun buildEmptyResponse(req: Request): Response {
        return Response.Builder()
            .request(req)
            .protocol(Protocol.HTTP_1_1)
            .code(204)
            .message("No Content")
            .header("Cache-Control", "public, max-age=3600")
            .body(ByteArray(0).toResponseBody(PROTOBUF_MEDIA))
            .build()
    }
}

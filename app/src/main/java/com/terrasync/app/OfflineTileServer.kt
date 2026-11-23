package com.terrasync.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

data class OfflineRegion(
    val id: String,
    val title: String,
    val subtitle: String,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
    val minZoom: Int,
    val maxZoom: Int
)

class OfflineTileServer(context: Context) {

    companion object {
        val REGIONS = listOf(
            OfflineRegion(
                id = "kurdistan_north",
                title = "Northern Iraq (Kurdistan Region)",
                subtitle = "Duhok, Zakho, Akre, Erbil, Sulaymaniyah, Halabja, Kirkuk, Nineveh",
                minLat = 34.45,
                maxLat = 37.38,
                minLng = 42.35,
                maxLng = 46.35,
                minZoom = 7,
                maxZoom = 13
            ),
            OfflineRegion(
                id = "iraq_south",
                title = "Central & Southern Iraq",
                subtitle = "Baghdad, Anbar, Diyala, Karbala, Najaf, Wasit, Dhi Qar, Basra",
                minLat = 29.05,
                maxLat = 34.45,
                minLng = 38.75,
                maxLng = 48.65,
                minZoom = 7,
                maxZoom = 12
            )
        )
    }

    private class TileDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, "terrasync_osm_tiles.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS tiles (z INTEGER, x INTEGER, y INTEGER, region TEXT, data BLOB, PRIMARY KEY (z, x, y))")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tiles_region ON tiles(region)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    private val helper = TileDbHelper(context.applicationContext)
    private val serverPool = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    var port: Int = 0
        private set

    private val cancelDownload = AtomicBoolean(false)
    @Volatile
    var activeDownloadRegionId: String? = null
        private set

    fun start() {
        if (running.getAndSet(true)) return
        val ss = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
        serverSocket = ss
        port = ss.localPort
        serverPool.execute {
            while (running.get()) {
                try {
                    val client = ss.accept()
                    serverPool.execute { handleClient(client) }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        cancelDownload.set(true)
        runCatching { serverSocket?.close() }
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 10000
            val input = BufferedInputStream(s.getInputStream())
            val out = s.getOutputStream()
            val reqLine = readLine(input) ?: return
            while (true) {
                val h = readLine(input)
                if (h.isNullOrEmpty()) break
            }
            val parts = reqLine.split(" ")
            if (parts.size < 2) return
            val path = parts[1].substringBefore('?')
            val match = Regex("^/tiles/(\\d+)/(\\d+)/(\\d+)\\.png$").find(path)
            if (match == null) {
                writeResponse(out, 404, "text/plain", ByteArray(0))
                return
            }
            val z = match.groupValues[1].toInt()
            val x = match.groupValues[2].toInt()
            val y = match.groupValues[3].toInt()

            var bytes = getTileFromDb(z, x, y)
            if (bytes == null) {
                bytes = fetchTileFromOsm(z, x, y)
                if (bytes != null && bytes.isNotEmpty()) {
                    saveTileToDb(z, x, y, "cache", bytes)
                }
            }
            if (bytes != null && bytes.isNotEmpty()) {
                writeResponse(out, 200, "image/png", bytes)
            } else {
                writeResponse(out, 502, "text/plain", ByteArray(0))
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun writeResponse(out: OutputStream, code: Int, contentType: String, body: ByteArray) {
        val statusText = if (code == 200) "OK" else "Error"
        val header = "HTTP/1.1 $code $statusText\r\nContent-Type: $contentType\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.US_ASCII))
        if (body.isNotEmpty()) out.write(body)
        out.flush()
    }

    private fun getTileFromDb(z: Int, x: Int, y: Int): ByteArray? {
        val db = helper.readableDatabase
        db.rawQuery("SELECT data FROM tiles WHERE z=? AND x=? AND y=? LIMIT 1", arrayOf("$z", "$x", "$y")).use { c ->
            if (c.moveToFirst()) return c.getBlob(0)
        }
        return null
    }

    private fun saveTileToDb(z: Int, x: Int, y: Int, region: String, data: ByteArray) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("z", z)
            put("x", x)
            put("y", y)
            put("region", region)
            put("data", data)
        }
        db.insertWithOnConflict("tiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun fetchTileFromOsm(z: Int, x: Int, y: Int): ByteArray? {
        return runCatching {
            val url = URL("https://tile.openstreetmap.org/$z/$x/$y.png")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "TerraSync/2.2.0 (Android Field Mapping; https://github.com/Thamirakkurd/TerraSync)")
            }
            if (conn.responseCode == 200) {
                conn.inputStream.use { it.readBytes() }
            } else null
        }.getOrNull()
    }

    fun getRegionStats(regionId: String): Pair<Int, Long> {
        val db = helper.readableDatabase
        db.rawQuery("SELECT COUNT(*), COALESCE(SUM(LENGTH(data)), 0) FROM tiles WHERE region=?", arrayOf(regionId)).use { c ->
            if (c.moveToFirst()) return c.getInt(0) to c.getLong(1)
        }
        return 0 to 0L
    }

    fun deleteRegion(regionId: String) {
        if (activeDownloadRegionId == regionId) cancelDownload.set(true)
        helper.writableDatabase.delete("tiles", "region=?", arrayOf(regionId))
    }

    fun cancelActiveDownload() {
        cancelDownload.set(true)
    }

    fun downloadRegion(
        region: OfflineRegion,
        onProgress: (done: Int, total: Int, finished: Boolean, error: String?) -> Unit
    ) {
        if (activeDownloadRegionId != null) return
        activeDownloadRegionId = region.id
        cancelDownload.set(false)

        Thread {
            val coords = enumerateTiles(region)
            val total = coords.size
            var done = 0
            try {
                for ((z, x, y) in coords) {
                    if (cancelDownload.get()) {
                        activeDownloadRegionId = null
                        onProgress(done, total, true, "Download cancelled")
                        return@Thread
                    }
                    if (getTileFromDb(z, x, y) == null) {
                        val bytes = fetchTileFromOsm(z, x, y)
                        if (bytes != null && bytes.isNotEmpty()) {
                            saveTileToDb(z, x, y, region.id, bytes)
                        }
                        Thread.sleep(45L)
                    }
                    done++
                    if (done % 15 == 0 || done == total) {
                        onProgress(done, total, done == total, null)
                    }
                }
            } catch (e: Exception) {
                onProgress(done, total, true, e.message ?: "Download interrupted")
            } finally {
                activeDownloadRegionId = null
            }
        }.start()
    }

    private fun enumerateTiles(r: OfflineRegion): List<Triple<Int, Int, Int>> {
        val list = mutableListOf<Triple<Int, Int, Int>>()
        for (z in r.minZoom..r.maxZoom) {
            val xMin = lngToTileX(r.minLng, z)
            val xMax = lngToTileX(r.maxLng, z)
            val yMin = latToTileY(r.maxLat, z)
            val yMax = latToTileY(r.minLat, z)
            for (x in xMin..xMax) {
                for (y in yMin..yMax) {
                    list.add(Triple(z, x, y))
                }
            }
        }
        return list
    }

    private fun lngToTileX(lng: Double, z: Int): Int {
        val n = 1 shl z
        return floor((lng + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    private fun latToTileY(lat: Double, z: Int): Int {
        val n = 1 shl z
        val rad = lat * PI / 180.0
        val y = (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * n
        return floor(y).toInt().coerceIn(0, n - 1)
    }
}

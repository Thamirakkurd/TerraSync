package com.terrasync.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPOutputStream

class OfflineMapPipelineTest {

    private fun makePmTilesHeader(internalComp: Int, tileComp: Int, minZ: Int = 6, maxZ: Int = 13): ByteArray {
        val raw = ByteArray(127)
        "PMTiles".toByteArray(Charsets.US_ASCII).copyInto(raw, 0)
        raw[7] = 3
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        bb.putLong(8, 127L)   // rootDirOffset
        bb.putLong(16, 64L)   // rootDirLength
        bb.putLong(56, 256L)  // tileDataOffset
        raw[97] = internalComp.toByte()
        raw[98] = tileComp.toByte()
        raw[99] = 1           // MVT
        raw[100] = minZ.toByte()
        raw[101] = maxZ.toByte()
        bb.putInt(102, (42.35 * 1e7).toInt())
        bb.putInt(106, (34.45 * 1e7).toInt())
        bb.putInt(110, (46.35 * 1e7).toInt())
        bb.putInt(114, (37.38 * 1e7).toInt())
        return raw
    }

    @Test
    fun parseHeaderAcceptsGzipAndNoneAndRejectsBrotliOrZstd() {
        val gzipHeader = LocalPmTilesStore.parseHeaderBytes(makePmTilesHeader(2, 2, 6, 13))
        assertNotNull(gzipHeader)
        assertEquals(6, gzipHeader!!.minZoom)
        assertEquals(13, gzipHeader.maxZoom)

        assertNotNull(LocalPmTilesStore.parseHeaderBytes(makePmTilesHeader(1, 1)))
        assertNull(LocalPmTilesStore.parseHeaderBytes(makePmTilesHeader(2, 3))) // Brotli
        assertNull(LocalPmTilesStore.parseHeaderBytes(makePmTilesHeader(2, 4))) // Zstd
        assertNull(LocalPmTilesStore.parseHeaderBytes(makePmTilesHeader(3, 2))) // Brotli dir
    }

    @Test
    fun offlineTileJsonClampsMaxZoomForOverzoom() {
        val json = JSONObject(String(MapTileInterceptor.buildOfflineTileJson(13), Charsets.UTF_8))
        assertEquals(0, json.getInt("minzoom"))
        assertEquals(13, json.getInt("maxzoom"))
        assertTrue(json.getJSONArray("tiles").getString(0).endsWith("/{z}/{x}/{y}.pbf"))

        val clamped = JSONObject(String(MapTileInterceptor.buildOfflineTileJson(18), Charsets.UTF_8))
        assertEquals(14, clamped.getInt("maxzoom"))
    }

    @Test
    fun gunzipIfNeededDecompressesGzippedTileJsonCleanly() {
        val plain = """{"tilejson":"3.0.0","maxzoom":13}""".toByteArray(Charsets.UTF_8)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(plain) }
        val gz = baos.toByteArray()

        assertTrue(MapTileInterceptor.isGzipBytes(gz))
        val out = MapTileInterceptor.gunzipIfNeeded(gz)
        assertFalse(MapTileInterceptor.isGzipBytes(out))
        assertEquals(String(plain, Charsets.UTF_8), String(out, Charsets.UTF_8))
    }

    @Test
    fun partialProgressReadsParallelCheckpointAndClearsCleanly() {
        val dir = java.io.File.createTempFile("ts_dl_test", "").apply { delete(); mkdirs() }
        try {
            val part = java.io.File(dir, "pack_iraq_south.pmtiles.part")
            java.io.RandomAccessFile(part, "rw").use { it.setLength(10_000L) }
            val meta = ParallelFileDownloader.metaFileFor(part)
            meta.writeText("""{"mode":"parallel","totalBytes":10000,"numParts":4,"partDone":[1000,1500,0,500]}""", Charsets.UTF_8)

            val prog = ParallelFileDownloader.readPartialProgress(part)
            assertNotNull(prog)
            assertEquals(3000L, prog!!.downloadedBytes)
            assertEquals(10_000L, prog.totalBytes)
            assertEquals(29, prog.percent)

            ParallelFileDownloader.clearPartialFiles(part)
            assertFalse(part.exists())
            assertFalse(meta.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}

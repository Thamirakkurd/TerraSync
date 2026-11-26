package com.terrasync.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OfflineRoutingAndStoreRobolectricTest {

    @Test
    fun testOfflineRoutingSqliteStagingToTsrAndRouteOffline() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val chunkFile = File.createTempFile("qa_robo_overpass_", ".json", ctx.cacheDir)
        val packId = "qa_robo_test"
        try {
            chunkFile.writeText(
                """
                {
                  "version": 0.6,
                  "elements": [
                    {"type":"node","id":1,"lat":36.8500,"lon":42.9500},
                    {"type":"node","id":2,"lat":36.8530,"lon":42.9500},
                    {"type":"node","id":3,"lat":36.8560,"lon":42.9540},
                    {"type":"node","id":4,"lat":36.8560,"lon":42.9580},
                    {"type":"node","id":5,"lat":36.8520,"lon":42.9580},
                    {"type":"way","id":101,"nodes":[1,2,3,4],"tags":{"highway":"residential","name":"Zakho Main St"}},
                    {"type":"way","id":102,"nodes":[2,5,4],"tags":{"highway":"secondary","name":"Asia Bypass"}}
                  ]
                }
                """.trimIndent(),
                Charsets.UTF_8
            )

            val region = OfflineRoutingEngine.compileLocalOverpassChunksToTsr(
                ctx = ctx,
                regionName = "QA Robolectrics",
                packId = packId,
                minLat = 36.80,
                minLng = 42.90,
                maxLat = 36.90,
                maxLng = 43.00,
                chunkFiles = listOf(chunkFile)
            )
            assertNotNull("Expected compiled .tsr region from SQLite staging", region)
            assertTrue("Expected >= 5 nodes, got ${region!!.nodeCount}", region.nodeCount >= 5)
            assertTrue("Expected >= 8 bidirectional edges, got ${region.edgeCount}", region.edgeCount >= 8)

            val listed = OfflineRoutingEngine.listRegions(ctx)
            assertTrue("Expected region in listRegions", listed.any { it.fileName == OfflineRoutingEngine.packFileName(packId) })

            val (route, err) = OfflineRoutingEngine.routeOffline(
                ctx = ctx,
                startLat = 36.8501,
                startLng = 42.9501,
                endLat = 36.8559,
                endLng = 42.9579,
                mode = TravelMode.CAR
            )
            assertNotNull("Expected valid offline route (err=$err)", route)
            assertTrue("Expected multi-point route geometry", route!!.geometry.size >= 3)
            assertTrue("Expected route distance > 300m, got ${route.totalDistanceMeters}", route.totalDistanceMeters > 300.0)
            assertTrue("Expected turn-by-turn steps", route.steps.isNotEmpty())
            assertTrue("Expected Offline engine label, got ${route.engineLabel}", route.engineLabel.contains("Offline", ignoreCase = true))
        } finally {
            OfflineRoutingEngine.deletePackRegion(ctx, packId)
            chunkFile.delete()
        }
    }

    @Test
    fun testLocalFolderStoreStreamingRoundTrip() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val storeFile = File(ctx.filesDir, "local_folders_v1.json")
        storeFile.delete()
        LocalFolderStore.clearCacheForTest()

        val pins = (1..250).map { i ->
            val st = when (i % 3) {
                0 -> "done"
                1 -> "issue"
                else -> "pending"
            }
            PinItem(
                id = "pin_$i",
                layerId = "local_robo_1",
                name = "Feeder #$i - گەرەکا سەرهلدان",
                description = "Box $i \"quoted\" notes",
                lat = 36.85 + i * 0.0001,
                lng = 42.94 + i * 0.0001,
                status = st,
                issueReason = if (st == "issue") "Need Electricity" else if (st == "done") "Meter Linked" else "",
                updatedAt = 1_764_150_000_000L + i,
                isEdited = st != "pending",
                editedBy = if (st == "pending") "" else "Team 1 (#A1B2)",
                editedAt = if (st == "pending") 0L else 1_764_150_000_000L + i,
                editHistory = if (st == "pending") "" else "26 Nov 10:15 • Team 1 → Done: Meter Linked"
            )
        }
        val tracks = (1..5).map { k ->
            TrackItem(
                id = "trk_$k",
                layerId = "local_robo_1",
                name = "Line $k",
                coords = "42.94,36.85;42.95,36.86",
                updatedAt = 1_764_150_000_000L + k
            )
        }

        LocalFolderStore.saveNewLayer(
            ctx = ctx,
            layerId = "local_robo_1",
            layerName = "Robolectric Folder",
            createdBy = "Team 1 (#A1B2)",
            creatorDeviceId = "A1B2",
            parsed = ParsedKml(pins = pins, tracks = tracks, documentName = "Robolectric Folder")
        )
        LocalFolderStore.flushPendingWritesForTest(ctx)
        LocalFolderStore.clearCacheForTest()
        assertTrue("Expected persisted local_folders_v1.json on disk", storeFile.exists())

        // Simulate interrupted atomic write leftovers (.tmp & .tmp2) and verify loadAll cleans both
        val staleTmp = File(ctx.filesDir, "local_folders_v1.json.tmp").apply { writeText("{}") }
        val staleTmp2 = File(ctx.filesDir, "local_folders_v1.json.tmp2").apply { writeText("{}") }
        val reloaded = LocalFolderStore.loadAll(ctx)
        assertTrue("Expected stale .tmp deleted", !staleTmp.exists())
        assertTrue("Expected stale .tmp2 deleted", !staleTmp2.exists())
        assertEquals(1, reloaded.layers.size)
        assertEquals("Robolectric Folder", reloaded.layers[0].name)
        assertEquals(250, reloaded.pins.size)
        assertEquals(5, reloaded.tracks.size)
        assertEquals("Feeder #1 - گەرەکا سەرهلدان", reloaded.pins[0].name)
        assertEquals("Box 1 \"quoted\" notes", reloaded.pins[0].description)
        assertEquals("issue", reloaded.pins[0].status)
        assertEquals("Feeder #250 - گەرەکا سەرهلدان", reloaded.pins[249].name)
        assertEquals("42.94,36.85;42.95,36.86", reloaded.tracks[0].coords)

        // Verify 5-second undo snapshot restore reverts status, note, and editHistory cleanly
        val original = reloaded.pins[1]
        val dirty = LocalFolderStore.updatePinStatus(ctx, original, "issue", "QA (#A1B2)", "No Bill")
        assertEquals("issue", dirty.status)
        assertTrue(dirty.editHistory.isNotBlank())
        val undone = LocalFolderStore.restorePinStatusSnapshot(ctx, original)
        LocalFolderStore.flushPendingWritesForTest(ctx)
        LocalFolderStore.clearCacheForTest()
        val afterUndo = LocalFolderStore.loadAll(ctx).pins.first { it.id == original.id }
        assertEquals(original.status, undone.status)
        assertEquals(original.status, afterUndo.status)
        assertEquals(original.issueReason, afterUndo.issueReason)
        assertEquals(original.isEdited, afterUndo.isEdited)
        assertEquals(original.editHistory, afterUndo.editHistory)

        storeFile.delete()
        LocalFolderStore.clearCacheForTest()
    }

    @Test
    fun testOfflineRoutingSqliteStagingMultiMbGraphStress() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val chunkFile = File.createTempFile("qa_robo_multimb_", ".json", ctx.cacheDir)
        val packId = "qa_robo_multimb"
        val rows = 180
        val cols = 180 // 32,400 nodes, 360 ways -> ~2.27 MB JSON, ~128,880 directed edges
        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(chunkFile), StandardCharsets.UTF_8), 64 * 1024).use { w ->
                w.write("{\"version\":0.6,\"elements\":[")
                var first = true
                for (r in 0 until rows) {
                    val lat = 36.80 + r * 0.0008
                    for (c in 0 until cols) {
                        val id = r * cols + c + 1
                        val lon = 42.90 + c * 0.0009
                        if (!first) w.write(",")
                        first = false
                        w.write("{\"type\":\"node\",\"id\":")
                        w.write(id.toString())
                        w.write(",\"lat\":")
                        w.write(lat.toString())
                        w.write(",\"lon\":")
                        w.write(lon.toString())
                        w.write("}")
                    }
                }
                var wayId = 1_000_000
                // Horizontal roads
                for (r in 0 until rows) {
                    wayId++
                    w.write(",{\"type\":\"way\",\"id\":")
                    w.write(wayId.toString())
                    w.write(",\"nodes\":[")
                    for (c in 0 until cols) {
                        if (c > 0) w.write(",")
                        w.write((r * cols + c + 1).toString())
                    }
                    w.write("],\"tags\":{\"highway\":\"secondary\",\"name\":\"Akre East-West $r\"}}")
                }
                // Vertical roads
                for (c in 0 until cols) {
                    wayId++
                    w.write(",{\"type\":\"way\",\"id\":")
                    w.write(wayId.toString())
                    w.write(",\"nodes\":[")
                    for (r in 0 until rows) {
                        if (r > 0) w.write(",")
                        w.write((r * cols + c + 1).toString())
                    }
                    w.write("],\"tags\":{\"highway\":\"residential\",\"name\":\"Akre Cross $c\"}}")
                }
                w.write("]}")
            }

            assertTrue("Expected multi-MB chunk (>= 1.8 MB), got ${chunkFile.length()}", chunkFile.length() >= 1_800_000L)

            val runtime = Runtime.getRuntime()
            System.gc()
            val t0 = System.nanoTime()
            val region = OfflineRoutingEngine.compileLocalOverpassChunksToTsr(
                ctx = ctx,
                regionName = "QA Multi-MB Grid",
                packId = packId,
                minLat = 36.79,
                minLng = 42.89,
                maxLat = 36.91,
                maxLng = 43.03,
                chunkFiles = listOf(chunkFile)
            )
            val compileMs = (System.nanoTime() - t0) / 1_000_000L
            val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)

            assertNotNull("Expected compiled .tsr from multi-MB SQLite staging", region)
            assertEquals(rows * cols, region!!.nodeCount)
            assertTrue("Expected >= 59000 bidirectional edges, got ${region.edgeCount}", region.edgeCount >= 59_000)

            val (route, err) = OfflineRoutingEngine.routeOffline(
                ctx = ctx,
                startLat = 36.8002,
                startLng = 42.9002,
                endLat = 36.80 + (rows - 1) * 0.0008,
                endLng = 42.90 + (cols - 1) * 0.0009,
                mode = TravelMode.CAR
            )
            assertNotNull("Expected corner-to-corner offline route (err=$err)", route)
            assertTrue("Expected route distance > 15 km, got ${route!!.totalDistanceMeters}", route.totalDistanceMeters > 15_000.0)
            assertTrue("Expected turn-by-turn steps", route.steps.isNotEmpty())

            val tWarm1 = System.nanoTime()
            val (route2, err2) = OfflineRoutingEngine.routeOffline(
                ctx = ctx,
                startLat = 36.8002,
                startLng = 42.9002,
                endLat = 36.80 + (rows - 1) * 0.0008,
                endLng = 42.90 + (cols - 1) * 0.0009,
                mode = TravelMode.CAR
            )
            val warmRepeatMs = (System.nanoTime() - tWarm1) / 1_000_000L
            assertNotNull("Expected deterministic cached route (err=$err2)", route2)
            assertEquals(route.totalDistanceMeters, route2!!.totalDistanceMeters, 0.01)
            assertEquals(route.steps.size, route2.steps.size)

            val tWarm2 = System.nanoTime()
            val (route3, err3) = OfflineRoutingEngine.routeOffline(
                ctx = ctx,
                startLat = 36.805,
                startLng = 42.905,
                endLat = 36.80 + (rows - 6) * 0.0008,
                endLng = 42.90 + (cols - 6) * 0.0009,
                mode = TravelMode.CAR
            )
            val warmShiftedMs = (System.nanoTime() - tWarm2) / 1_000_000L
            assertNotNull("Expected shifted interior route via SpatialNodeGrid (err=$err3)", route3)
            assertTrue("Expected shifted route distance > 10 km, got ${route3!!.totalDistanceMeters}", route3.totalDistanceMeters > 10_000.0)

            println(
                "[CI-STRESS] SQLite staging multi-MB: chunk=${"%.2f".format(Locale.US, chunkFile.length() / (1024.0 * 1024.0))} MB, " +
                    "compile=${compileMs} ms, nodes=${region.nodeCount}, edges=${region.edgeCount}, " +
                    "tsr=${region.sizeBytes / 1024} KB, routeKm=${"%.2f".format(Locale.US, route.totalDistanceMeters / 1000.0)}, " +
                    "warmRepeat=${warmRepeatMs}ms, warmShifted=${warmShiftedMs}ms, heapUsed=${"%.1f".format(Locale.US, usedMb)} MB"
            )
        } finally {
            OfflineRoutingEngine.deletePackRegion(ctx, packId)
            chunkFile.delete()
        }
    }

    @Test
    fun testStreamingKmlNestedFoldersAndBareAmpersandRecovery() {
        val sb = StringBuilder(380 * 1024)
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document><name>Substation & Grid</name>\n")
        sb.append("<Folder><name>Feeder 1 & Main</name>\n<Folder><name>Zone A</name>\n")
        for (i in 1..2000) {
            val lat = 36.84 + (i % 50) * 0.0002
            val lng = 42.94 + (i / 50) * 0.0002
            sb.append("<Placemark><name>Pole &amp; Tap #$i</name>")
            sb.append("<description>Feeder R&D check #$i — transformer & fuse bay inspectable payload</description>")
            sb.append("<Point><coordinates>$lng,$lat,0</coordinates></Point></Placemark>\n")
        }
        sb.append("<Placemark><name>MultiBay Junction</name><MultiGeometry>")
        sb.append("<Point><coordinates>42.9501,36.8501,0</coordinates></Point>")
        sb.append("<Point><coordinates>42.9505,36.8505,0</coordinates></Point>")
        sb.append("<LineString><coordinates>42.9501,36.8501,0 42.9505,36.8505,0</coordinates></LineString>")
        sb.append("</MultiGeometry></Placemark>\n")
        sb.append("</Folder></Folder></Document></kml>")

        val payloadBytes = sb.toString().toByteArray(Charsets.UTF_8)
        assertTrue("Expected KML > 256 KB to exercise temp-file spill, got ${payloadBytes.size}", payloadBytes.size > 300 * 1024)

        val parsed = KmlParser.parse(ByteArrayInputStream(payloadBytes), "layer_kml_test")
        assertEquals("Substation & Grid", parsed.documentName)
        assertTrue("Expected >= 2002 parsed pins, got ${parsed.pins.size}", parsed.pins.size >= 2002)
        assertEquals(1, parsed.tracks.size)
        assertEquals("Feeder 1 & Main / Zone A", parsed.pins.first().folderPath)
        assertEquals("Feeder 1 & Main / Zone A", parsed.tracks.first().folderPath)

        val exportedXml = KmlCsvExporter.buildKmlXml("Export", parsed.pins.take(10), parsed.tracks)
        val reparsed = KmlParser.parse(ByteArrayInputStream(exportedXml.toByteArray(Charsets.UTF_8)), "layer_kml_test")
        assertEquals(10, reparsed.pins.size)
        assertEquals(1, reparsed.tracks.size)
        assertEquals("Feeder 1 & Main / Zone A", reparsed.pins.first().folderPath)
        assertEquals("Feeder 1 & Main / Zone A", reparsed.tracks.first().folderPath)
    }

    @Test
    fun testDevicePrefsPruneOrphanedPinReferences() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        if (!DevicePrefs.isBookmarked(ctx, "p_keep_1")) DevicePrefs.toggleBookmark(ctx, "p_keep_1")
        if (!DevicePrefs.isBookmarked(ctx, "p_orphan_1")) DevicePrefs.toggleBookmark(ctx, "p_orphan_1")
        if (!DevicePrefs.isBookmarked(ctx, "p_orphan_2")) DevicePrefs.toggleBookmark(ctx, "p_orphan_2")

        if (!DevicePrefs.isPinLocalSent(ctx, "p_keep_1")) DevicePrefs.toggleLocalSentPin(ctx, "p_keep_1")
        if (!DevicePrefs.isPinLocalSent(ctx, "p_orphan_1")) DevicePrefs.toggleLocalSentPin(ctx, "p_orphan_1")
        if (!DevicePrefs.isPinLocalSent(ctx, "p_orphan_2")) DevicePrefs.toggleLocalSentPin(ctx, "p_orphan_2")

        DevicePrefs.prunePinReferencesKeeping(ctx, setOf("p_keep_1"))

        val bookmarks = DevicePrefs.getBookmarks(ctx)
        val localSent = DevicePrefs.getLocalSentPinIds(ctx)
        assertTrue(bookmarks.contains("p_keep_1"))
        assertFalse(bookmarks.contains("p_orphan_1"))
        assertFalse(bookmarks.contains("p_orphan_2"))
        assertTrue(localSent.contains("p_keep_1"))
        assertFalse(localSent.contains("p_orphan_1"))
        assertFalse(localSent.contains("p_orphan_2"))
    }
}

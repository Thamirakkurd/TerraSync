package com.terrasync.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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

        val reloaded = LocalFolderStore.loadAll(ctx)
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
}

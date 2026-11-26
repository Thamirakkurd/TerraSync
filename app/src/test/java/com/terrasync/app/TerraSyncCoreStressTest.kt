package com.terrasync.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.zip.GZIPOutputStream

class TerraSyncCoreStressTest {

    @Test
    fun testStreamingRoadGraphUnder128MBHeap() {
        val totalNodes = 480_000
        val totalWays = 60_000
        val tmpFile = File.createTempFile("qa_overpass_stress_", ".json")
        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(tmpFile), StandardCharsets.UTF_8), 64 * 1024).use { w ->
                w.write("{\"version\":0.6,\"elements\":[")
                for (i in 1..totalNodes) {
                    if (i > 1) w.write(",")
                    val lat = 36.80 + (i % 1000) * 0.0001
                    val lon = 42.90 + (i / 1000) * 0.0001
                    w.write("{\"type\":\"node\",\"id\":")
                    w.write(i.toString())
                    w.write(",\"lat\":")
                    w.write(lat.toString())
                    w.write(",\"lon\":")
                    w.write(lon.toString())
                    w.write("}")
                }
                for (j in 1..totalWays) {
                    val base = ((j - 1) * 5) % (totalNodes - 6) + 1
                    w.write(",{\"type\":\"way\",\"id\":")
                    w.write((1_000_000 + j).toString())
                    w.write(",\"nodes\":[")
                    w.write(base.toString())
                    w.write(",")
                    w.write((base + 1).toString())
                    w.write(",")
                    w.write((base + 2).toString())
                    w.write(",")
                    w.write((base + 3).toString())
                    w.write(",")
                    w.write((base + 4).toString())
                    w.write("],\"tags\":{\"highway\":\"residential\",\"name\":\"Asia Road North\",\"oneway\":\"yes\"}}")
                }
                w.write("]}")
            }

            assertTrue("Expected >= 34 MB synthetic Overpass JSON, got ${tmpFile.length()}", tmpFile.length() >= 34L * 1024L * 1024L)

            val runtime = Runtime.getRuntime()
            System.gc()
            var peakUsedMb = 0.0
            fun sampleHeap() {
                var usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
                if (usedMb >= 90.0) {
                    System.gc()
                    usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024.0 * 1024.0)
                }
                if (usedMb > peakUsedMb) peakUsedMb = usedMb
            }

            var nodeCount = 0
            val (parsedOk, remarkErr) = FileInputStream(tmpFile).use { fis ->
                OfflineRoutingEngine.streamOverpassNodes(fis) { _, _, _ ->
                    nodeCount++
                    if ((nodeCount and 0x3FFF) == 0) sampleHeap()
                }
            }
            assertTrue("Expected elements array parsed (remark=$remarkErr)", parsedOk)
            assertEquals(totalNodes, nodeCount)

            var wayCount = 0
            var edgeCount = 0
            FileInputStream(tmpFile).use { fis ->
                OfflineRoutingEngine.streamOverpassWays(fis) { _, hw, _, roadName, _, _, nodeIds ->
                    wayCount++
                    assertEquals("residential", hw)
                    assertEquals("Asia Road North", roadName)
                    edgeCount += (nodeIds.size - 1)
                    if ((wayCount and 0x1FFF) == 0) sampleHeap()
                }
            }
            sampleHeap()
            assertEquals(totalWays, wayCount)
            assertEquals(totalWays * 4, edgeCount)
            assertTrue("Peak JVM heap ${"%.1f".format(peakUsedMb)} MB exceeded 105 MB limit", peakUsedMb < 105.0)

            val baos = ByteArrayOutputStream()
            val nowMs = 1_764_150_000_000L
            DataOutputStream(GZIPOutputStream(baos)).use { dos ->
                OfflineRoutingEngine.writeTsrHeader(
                    dos = dos,
                    regionName = "Kurdistan QA",
                    sLat = 34.45,
                    wLng = 42.35,
                    nLat = 37.38,
                    eLng = 46.35,
                    now = nowMs,
                    nodeCount = nodeCount,
                    edgeCount = edgeCount,
                    nameCount = 128
                )
            }
            val gzBytes = baos.toByteArray()
            val decoded = OfflineRoutingEngine.readTsrHeaderFromStream(
                fileName = "pack_kurdistan_north.tsr",
                sizeBytes = gzBytes.size.toLong(),
                input = ByteArrayInputStream(gzBytes)
            )
            assertNotNull(decoded)
            assertEquals("Kurdistan QA", decoded!!.name)
            assertEquals(34.45, decoded.minLat, 1e-6)
            assertEquals(46.35, decoded.maxLng, 1e-6)
            assertEquals(nodeCount, decoded.nodeCount)
            assertEquals(edgeCount, decoded.edgeCount)
            assertEquals(gzBytes.size.toLong(), decoded.sizeBytes)
            assertEquals(nowMs, decoded.downloadedAt)
        } finally {
            tmpFile.delete()
        }
    }

    @Test
    fun testDailyReport1800PinsPerformanceAndExactEquivalence() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 26, 10, 15, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val inHoursMs = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 19)
        cal.set(Calendar.MINUTE, 45)
        val afterHoursMs = cal.timeInMillis

        val pins = ArrayList<PinItem>(1800)
        val teams = arrayOf("Team 1", "Team 2", "Team 3", "Team 4")
        val doneNotes = arrayOf("Meter Linked", "Inaccessible", "No Bill")
        for (i in 0 until 1800) {
            val mod10 = i % 10
            val status = when {
                mod10 < 6 -> "done"
                mod10 < 8 -> "issue"
                else -> "pending"
            }
            val reason = when (status) {
                "done" -> doneNotes[i % doneNotes.size]
                "issue" -> if (i % 20 == 6) "Need Electricity - Transformer access blocked" else "Need Electricity"
                else -> ""
            }
            val team = teams[i / 450]
            val idHex = (0xA100 + (i / 450)).toString(16).uppercase()
            val stamp = "$team (DEV • #$idHex)"
            val isLate = (i % 5 == 0) && status != "pending"
            val ts = if (isLate) afterHoursMs else inHoursMs
            val timeLabel = if (isLate) "26 Nov 19:45" else "26 Nov 10:15"
            val hist = if (status == "pending") {
                ""
            } else {
                "$timeLabel • $stamp → ${status.replaceFirstChar { it.uppercase() }}: $reason"
            }
            pins += PinItem(
                id = "pin_$i",
                layerId = "local_stress",
                name = "Stress #${"%04d".format(i + 1)} - گەرەکا سەرهلدان - Feeder ${i % 12}",
                description = "Note $i",
                lat = 36.85 + (i / 45) * 0.002,
                lng = 42.94 + (i % 45) * 0.002,
                status = status,
                issueReason = reason,
                updatedAt = ts,
                isEdited = status != "pending",
                editedBy = if (status == "pending") "" else stamp,
                editedAt = if (status == "pending") 0L else ts,
                editHistory = hist
            )
        }

        val fastLate = PinEditHistoryAudit.countOutsideWorkingHours(pins, 8 * 60, 17 * 60)
        val fullLate = PinEditHistoryAudit.collectFolderHistory(pins, 8 * 60, 17 * 60).count { it.isOutsideWorkHours }
        assertEquals(fullLate, fastLate)
        assertEquals(360, fastLate)

        DailyReportGenerator.buildReport(
            folderName = "Warmup",
            pins = pins.take(10),
            isCloudLive = false,
            workStartMin = 8 * 60,
            workEndMin = 17 * 60
        )

        val t0 = System.nanoTime()
        val report = DailyReportGenerator.buildReport(
            folderName = "QA Stress 1800",
            pins = pins,
            isCloudLive = false,
            workStartMin = 8 * 60,
            workEndMin = 17 * 60
        )
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000L

        assertEquals(1800, report.totalPins)
        assertEquals(1080, report.doneCount)
        assertEquals(360, report.issueCount)
        assertEquals(360, report.pendingCount)
        assertTrue(report.whatsappText.contains("QA Stress 1800"))
        assertTrue(report.whatsappText.contains("*360* edits"))
        assertTrue("Expected < 1000 ms for 1,800-pin report, got ${elapsedMs} ms", elapsedMs < 1000L)
    }

    @Test
    fun testGeoIntentParserAndPinEditHistoryBoundary() {
        val labeled = GeoIntentParser.parseGeoString(
            "geo:36.853238,42.952074?q=36.853238,42.952074(Test%20Feeder%20104)"
        )
        assertNotNull(labeled)
        assertEquals(36.853238, labeled!!.lat!!, 1e-6)
        assertEquals(42.952074, labeled.lng!!, 1e-6)
        assertEquals("Test Feeder 104", labeled.label)

        val unlabeled = GeoIntentParser.parseGeoString("geo:36.853238,42.952074")
        assertNotNull(unlabeled)
        assertEquals(36.853238, unlabeled!!.lat!!, 1e-6)
        assertEquals(42.952074, unlabeled.lng!!, 1e-6)
        assertEquals("", unlabeled.label)

        val startMin = 22 * 60
        val endMin = 6 * 60
        assertEquals(false, PinEditHistoryAudit.isOutsideWorkingHours(23 * 60 + 30, startMin, endMin))
        assertEquals(false, PinEditHistoryAudit.isOutsideWorkingHours(1 * 60 + 15, startMin, endMin))
        assertEquals(true, PinEditHistoryAudit.isOutsideWorkingHours(14 * 60, startMin, endMin))
        assertEquals(false, PinEditHistoryAudit.isHistoryLineOutsideWorkHours("26 Nov 23:45 • Team 1 → Done: Meter Linked", startMin, endMin))
        assertEquals(true, PinEditHistoryAudit.isHistoryLineOutsideWorkHours("26 Nov 14:10 • Team 1 → Issue: Closed", startMin, endMin))
    }
}

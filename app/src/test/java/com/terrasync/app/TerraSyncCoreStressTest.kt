package com.terrasync.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
import java.util.Locale
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

            val streamT0 = System.nanoTime()
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
            val streamElapsedMs = ((System.nanoTime() - streamT0) / 1_000_000L).coerceAtLeast(1L)
            sampleHeap()
            assertEquals(totalWays, wayCount)
            assertEquals(totalWays * 4, edgeCount)
            assertTrue("Peak JVM heap ${"%.1f".format(Locale.US, peakUsedMb)} MB exceeded 105 MB limit", peakUsedMb < 105.0)
            val fileMb = tmpFile.length() / (1024.0 * 1024.0)
            val throughputMbPerSec = (fileMb * 2.0) / (streamElapsedMs / 1000.0)
            println(
                "[CI-STRESS] Overpass stream (-Xmx128m): file=${"%.2f".format(Locale.US, fileMb)} MB " +
                    "(2 passes), elapsed=${streamElapsedMs} ms, speed=${"%.2f".format(Locale.US, throughputMbPerSec)} MB/s, " +
                    "nodes=$nodeCount, ways=$wayCount, edges=$edgeCount, peakHeap=${"%.1f".format(Locale.US, peakUsedMb)} MB"
            )

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
        println("[CI-STRESS] DailyReport 1800 pins: elapsed=${elapsedMs} ms, done=${report.doneCount}, issue=${report.issueCount}, pending=${report.pendingCount}")
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

    @Test
    fun testSpeedFallbackEstimatorIgnoresFusedEchoAndResetsOnStop() {
        var st = SpeedFallbackEstimator.SpeedSampleState()
        // 1. Initial GPS fix (no native speed) establishes anchor cleanly at 0 m/s
        st = SpeedFallbackEstimator.update(
            prev = st, lat = 36.850500, lng = 42.948500, provider = "gps",
            hasSpeed = false, speedMps = 0f, accurate = true, elapsedMs = 1_000L
        )
        assertEquals(0f, st.derivedSpeedMps, 0.001f)

        // 2. GPS advances ~22.2 m eastward after 1.0 s -> ~22.2 m/s (~80 km/h)
        st = SpeedFallbackEstimator.update(
            prev = st, lat = 36.850500, lng = 42.948750, provider = "gps",
            hasSpeed = false, speedMps = 0f, accurate = true, elapsedMs = 2_000L
        )
        assertTrue("Expected derivedSpeedMps > 15 m/s, got ${st.derivedSpeedMps}", st.derivedSpeedMps > 15f)
        val computedSpeed = st.derivedSpeedMps

        // 3. FUSED_PROVIDER echoes identical coordinate 0.4 s later -> must preserve derivedSpeedMps
        st = SpeedFallbackEstimator.update(
            prev = st, lat = 36.850500, lng = 42.948750, provider = "fused",
            hasSpeed = false, speedMps = 0f, accurate = true, elapsedMs = 2_400L
        )
        assertEquals(computedSpeed, st.derivedSpeedMps, 0.001f)

        // 4. Next moving GPS fix 1.0 s after step 2 calculates new positive speed
        st = SpeedFallbackEstimator.update(
            prev = st, lat = 36.850500, lng = 42.949000, provider = "gps",
            hasSpeed = false, speedMps = 0f, accurate = true, elapsedMs = 3_000L
        )
        assertTrue("Expected continued positive speed, got ${st.derivedSpeedMps}", st.derivedSpeedMps > 15f)

        // 5. Same provider stays stationary 1.2 s -> resets speed to 0
        st = SpeedFallbackEstimator.update(
            prev = st, lat = 36.850500, lng = 42.949000, provider = "gps",
            hasSpeed = false, speedMps = 0f, accurate = true, elapsedMs = 4_200L
        )
        assertEquals(0f, st.derivedSpeedMps, 0.001f)
    }

    @Test
    fun testWorkHourPresetsParseSerializeAndClamp() {
        val defaults = DevicePrefs.parseWorkHourPresets(null)
        assertEquals(3, defaults.size)
        assertEquals(8 * 60 to 17 * 60, defaults[1])

        val serialized = DevicePrefs.serializeWorkHourPresets(
            listOf(
                22 * 60 to 6 * 60,
                22 * 60 to 6 * 60, // duplicate
                9 * 60 to 9 * 60,   // equal start/end ignored
                6 * 60 to 14 * 60
            )
        )
        val parsed = DevicePrefs.parseWorkHourPresets(serialized)
        assertEquals(listOf(22 * 60 to 6 * 60, 6 * 60 to 14 * 60), parsed)
    }

    @Test
    fun testCsvFormulaEscapingAndBadiniDialectCompatibility() {
        assertEquals("\"'=SUM(A1:A2)\"", KmlCsvExporter.csvEscape("=SUM(A1:A2)"))
        assertEquals("\"'+12V fault\"", KmlCsvExporter.csvEscape("+12V fault"))
        assertEquals("\"'-3 phase\"", KmlCsvExporter.csvEscape("-3 phase"))
        assertEquals("\"'@pole\"", KmlCsvExporter.csvEscape("@pole"))
        assertEquals("\"Feeder \"\"A\"\" | Note\"", KmlCsvExporter.csvEscape("Feeder \"A\"\nNote"))

        assertEquals(AppLanguage.KURDISH_SORANI, AppLanguage.fromId("ku"))
        assertEquals(AppLanguage.KURDISH_BADINI, AppLanguage.fromId("ku_badini"))
        assertTrue(AppLanguage.KURDISH_SORANI.isRtl)
        assertTrue(AppLanguage.KURDISH_BADINI.isRtl)
    }

    @Test
    fun testSpeedFallbackEstimatorRejectsCrossProviderJumpAndTracks50Kmh() {
        // 1. Cross-provider jump (network/fused -> gps at different coordinate) must reset to 0, never spike
        var st = SpeedFallbackEstimator.SpeedSampleState(
            derivedSpeedMps = 0f,
            lat = 36.850500,
            lng = 42.948500,
            provider = "fused",
            elapsedMs = 1_000L
        )
        st = SpeedFallbackEstimator.update(
            prev = st,
            lat = 36.850750, // ~27.8m north
            lng = 42.948500,
            provider = "gps",
            hasSpeed = false,
            speedMps = 0f,
            accurate = true,
            elapsedMs = 1_500L
        )
        assertEquals("Cross-provider jump must not spike speed", 0f, st.derivedSpeedMps, 0.001f)
        assertEquals("gps", st.provider)

        // 2. Network provider updates & inaccurate fixes must always return 0 m/s
        val netState = SpeedFallbackEstimator.update(
            prev = st,
            lat = 36.851000,
            lng = 42.948500,
            provider = "network",
            hasSpeed = true,
            speedMps = 18.0f,
            accurate = true,
            elapsedMs = 2_000L
        )
        assertEquals(0f, netState.derivedSpeedMps, 0.001f)

        val inaccurateState = SpeedFallbackEstimator.update(
            prev = st,
            lat = 36.851000,
            lng = 42.948500,
            provider = "gps",
            hasSpeed = true,
            speedMps = 20.8f,
            accurate = false,
            elapsedMs = 2_500L
        )
        assertEquals(0f, inaccurateState.derivedSpeedMps, 0.001f)

        // 3. Four sequential moving GPS fixes at ~13.9 m/s (~50 km/h) with native speed & derived fallback
        var navState = SpeedFallbackEstimator.SpeedSampleState()
        for (step in 1..4) {
            navState = SpeedFallbackEstimator.update(
                prev = navState,
                lat = 36.850500,
                lng = 42.948500 + step * 0.000155,
                provider = "gps",
                hasSpeed = true,
                speedMps = 13.9f,
                accurate = true,
                elapsedMs = step * 1_000L
            )
            assertEquals(13.9f, navState.derivedSpeedMps, 0.01f)
        }

        // Fallback calculation (hasSpeed=false) across ~13.9m steps at 1.0s intervals (~0.000155 deg lng @ 36.85 lat)
        var fbState = SpeedFallbackEstimator.SpeedSampleState()
        for (step in 0..3) {
            fbState = SpeedFallbackEstimator.update(
                prev = fbState,
                lat = 36.850500,
                lng = 42.948500 + step * 0.000155,
                provider = "gps",
                hasSpeed = false,
                speedMps = 0f,
                accurate = true,
                elapsedMs = 10_000L + step * 1_000L
            )
            if (step == 0) {
                assertEquals(0f, fbState.derivedSpeedMps, 0.001f)
            } else {
                val kmh = fbState.derivedSpeedMps * 3.6f
                assertTrue("Expected ~50 km/h (46..54 km/h), got $kmh", kmh in 46.0f..54.0f)
            }
        }
    }

    @Test
    fun testFormatEtaDurationHoursAndMinutes() {
        assertEquals("1 min", formatEtaDuration(30.0))
        assertEquals("17 min", formatEtaDuration(1020.0))
        assertEquals("1h", formatEtaDuration(3600.0))
        assertEquals("1h 48m", formatEtaDuration(6480.0))
        assertEquals("6h 35m", formatEtaDuration(23700.0))
        assertEquals("22h 43m", formatEtaDuration(81780.0))
    }

    @Test
    fun testDesignSystemBidiIsolationAndTimeFormatters() {
        assertEquals("\u2068Feeder A\u2069", DesignSystem.isolateBidi("Feeder A"))
        assertEquals("   ", DesignSystem.isolateBidi("   "))
        assertEquals("\u2068دهۆک\u2069 • \u20681.2 km\u2069", DesignSystem.joinBidi("دهۆک", "1.2 km", ""))

        val sampleMs = 1701001800000L
        assertTrue(Regex("""\d{2} [A-Za-z]{3} \d{2}:\d{2}""").matches(TimeFormatters.canonicalHistory(sampleMs)))
        assertTrue(Regex("""\d{2} [A-Za-z]{3}""").matches(TimeFormatters.dayMonth(sampleMs)))
        assertTrue(Regex("""\d{2}:\d{2}""").matches(TimeFormatters.hourMinute(sampleMs)))
        assertTrue(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(TimeFormatters.csvTimestamp(sampleMs)))
        assertTrue(Regex("""\d{8}_\d{4}""").matches(TimeFormatters.fileStamp(sampleMs)))
        val reportTs = TimeFormatters.reportTimestamp(sampleMs)
        assertTrue(reportTs.isNotBlank() && reportTs.contains("•"))
    }

    @Test
    fun testMapViewModelStateTransitions() {
        val vm = MapViewModel()
        vm.selectPin("p2", listOf("p1", "p2", "p3"))
        assertEquals("p2", vm.selectedPinId)
        assertEquals(1, vm.selectedStackedIndex)
        assertEquals("p3", vm.cycleStackedPin(1))
        assertEquals("p1", vm.cycleStackedPin(1))
        assertEquals("p3", vm.cycleStackedPin(-1))

        vm.selectTappedLocation(36.85, 42.95, "Tap Place", "Duhok")
        assertNull(vm.selectedPinId)
        assertTrue(vm.selectedStackedPinIds.isEmpty())
        assertEquals(GeoPoint(36.85, 42.95), vm.selectedMarkerGeoPoint)
        assertEquals("Tap Place", vm.tappedLocation?.title)

        assertFalse(vm.toggleStatusFilter("pending"))
        assertFalse(vm.visibleStatuses.contains("pending"))
        vm.hideSentPins = true
        vm.resetFilters()
        assertEquals(MapViewModel.ALL_STATUSES, vm.visibleStatuses)
        assertFalse(vm.hideSentPins)

        vm.setRouteOrigin(36.84, 42.94, "Origin")
        assertEquals("Origin", vm.routeOrigin?.label)
        val route = NavRoute(listOf(42.94 to 36.84, 42.95 to 36.85), 1400.0, 110.0, emptyList())
        vm.previewRoute = route
        vm.startNavigation(route, 36.85, 42.95, "Dest")
        assertNull(vm.previewRoute)
        assertNotNull(vm.activeNavRoute)
        assertTrue(vm.isFollowingGps)
        vm.stopNavigation()
        vm.clearRouteOrigin()
        assertNull(vm.activeNavRoute)
        assertNull(vm.routeOrigin)

        vm.applyRulerMode(true)
        vm.addRulerPoint(36.8, 42.9)
        vm.addRulerPoint(36.81, 42.91)
        assertTrue(vm.undoRulerPoint())
        assertEquals(1, vm.rulerPoints.size)
        vm.applyRulerMode(false)
        assertTrue(vm.rulerPoints.isEmpty())

        vm.applyLassoMode(true)
        vm.lassoPendingPinIds = listOf("p1")
        vm.applyLassoMode(false)
        assertTrue(vm.lassoPendingPinIds.isEmpty())

        vm.setSearchState("feeder", "pending")
        assertEquals("feeder", vm.searchQuery)
        assertEquals("pending", vm.searchQuickFilter)
        vm.clearSelection()
        assertNull(vm.selectedMarkerGeoPoint)
        assertNull(vm.tappedLocation)
    }

    @Test
    fun testPinEditHistoryAuditRecomputeHelper() {
        val empty = PinEditHistoryAudit.recomputeFromHistoryLines(emptyList(), 9999L)
        assertEquals("pending", empty.status)
        assertEquals("", empty.issueReason)
        assertFalse(empty.isEdited)
        assertEquals("", empty.editedBy)
        assertEquals(0L, empty.editedAt)

        val issueLines = listOf(
            "24 Nov 18:42 • Ali (#A1B2) → issue: Need Fuse",
            "24 Nov 18:40 • Ali (#A1B2) → done: Meter Linked"
        )
        val issueState = PinEditHistoryAudit.recomputeFromHistoryLines(issueLines, 9999L)
        assertEquals("issue", issueState.status)
        assertEquals("Need Fuse", issueState.issueReason)
        assertTrue(issueState.isEdited)
        assertEquals("Ali (#A1B2)", issueState.editedBy)
        assertTrue(issueState.editedAt > 0L)

        val revertedToDone = PinEditHistoryAudit.recomputeFromHistoryLines(issueLines.drop(1), 9999L)
        assertEquals("done", revertedToDone.status)
        assertEquals("Meter Linked", revertedToDone.issueReason)
        assertTrue(revertedToDone.isEdited)

        val textOnly = PinEditHistoryAudit.recomputeFromHistoryLines(
            listOf("24 Nov 18:45 • Omar (#C3D4) edited text"),
            9999L
        )
        assertEquals("pending", textOnly.status)
        assertEquals("", textOnly.issueReason)
        assertTrue(textOnly.isEdited)
        assertEquals("Omar (#C3D4)", textOnly.editedBy)
    }

    @Test
    fun testStackedBucketKeyAndExactStackedPins() {
        assertTrue(stackedBucketKey(-12, 34) != stackedBucketKey(12, 34))
        assertTrue(stackedBucketKey(-1, -1) != stackedBucketKey(0, 0))

        val s1 = PinItem("p1", "l1", "Base", "", 36.850000, 42.950000, "pending")
        val s2 = PinItem("p2", "l1", "Stacked Issue", "", 36.850005, 42.950005, "issue")
        val sHidden = PinItem("p3", "l2", "Hidden Layer", "", 36.850002, 42.950002, "pending")
        val sFiltered = PinItem("p4", "l1", "Filtered Done", "", 36.850003, 42.950003, "done")
        val sFar = PinItem("p5", "l1", "Far", "", 36.851000, 42.950000, "pending")

        val snap = buildFolderIndexSnapshot(
            mergedLayers = listOf(LayerItem("l1", "F1"), LayerItem("l2", "F2")),
            mergedPins = listOf(s1, s2, sHidden, sFiltered, sFar),
            mergedTracks = emptyList(),
            version = 7L,
            isLiveConnected = false
        )

        val stacked = findExactStackedPinsFromSnapshot(
            snapshot = snap,
            pin = s1,
            hiddenLayerIds = setOf("l2"),
            visibleStatuses = setOf("pending", "issue"),
            sentIds = emptySet()
        )
        assertEquals(listOf("p1", "p2"), stacked.map { it.id })

        val withoutSent = findExactStackedPinsFromSnapshot(
            snapshot = snap,
            pin = s1,
            hiddenLayerIds = setOf("l2"),
            visibleStatuses = setOf("pending", "issue", "done"),
            sentIds = setOf("p2")
        )
        assertEquals(listOf("p1", "p4"), withoutSent.map { it.id })
    }

    @Test
    fun testTempCacheCleanerPurgesDemAndExportsOnly() {
        val root = java.nio.file.Files.createTempDirectory("ts_cache").toFile()
        try {
            java.io.File(root, "terrarium_dem/12").mkdirs()
            java.io.File(root, "terrarium_dem/12/a.png").writeBytes(ByteArray(100))
            java.io.File(root, "exports").mkdirs()
            java.io.File(root, "exports/x.kml").writeBytes(ByteArray(50))
            java.io.File(root, "keep").mkdirs()
            java.io.File(root, "keep/keep.bin").writeBytes(ByteArray(7))
            val freed = TempCacheCleaner.purge(root)
            assertEquals(150L, freed)
            assertTrue(java.io.File(root, "terrarium_dem").isDirectory)
            assertTrue(java.io.File(root, "exports").isDirectory)
            assertEquals(0, java.io.File(root, "terrarium_dem").listFiles()?.size ?: -1)
            assertEquals(0, java.io.File(root, "exports").listFiles()?.size ?: -1)
            assertTrue(java.io.File(root, "keep/keep.bin").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun testKurdishStyleSanitizationAndPinAeShaping() {
        val legacyCached = """{"version":8,"glyphs":"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf","font-faces":{"Noto Sans Regular":[{"url":"asset://fonts/NotoSansArabic-Regular.ttf"}]},"layers":[]}"""
        val sanitized = MapLayerRenderer.sanitizeStyleJson(legacyCached)
        assertFalse(sanitized.contains("font-faces"))
        assertTrue(sanitized.contains("glyphs"))
        assertFalse(MapLayerRenderer.STYLE_SATELLITE.contains("font-faces"))
        assertTrue(MapLayerRenderer.STYLE_SATELLITE.contains("\"maxzoom\": 17"))

        // Kurdish AE (U+06D5): joins right after dual-joining letters, isolated after non-left-joining letters
        assertEquals("ه\uFEEAولێر", MapLayerRenderer.shapeKurdishAeForMapLibre("هەولێر"))
        assertEquals("د\uFEE9نگ", MapLayerRenderer.shapeKurdishAeForMapLibre("دەنگ"))
        assertEquals("ق\uFEEAڵا", MapLayerRenderer.shapeKurdishAeForMapLibre("قەڵا"))
        assertEquals("ن\uFEEAخۆشخان\uFEEA", MapLayerRenderer.shapeKurdishAeForMapLibre("نەخۆشخانە"))
        assertEquals("Erbil 100m", MapLayerRenderer.shapeKurdishAeForMapLibre("Erbil 100m"))

        assertEquals(20, RoutingEngine.mapTileOkHttpClient.dispatcher.maxRequestsPerHost)
        assertTrue(RoutingEngine.mapTileOkHttpClient.dispatcher.maxRequests >= 64)
        assertEquals(
            "https://github.com/Thamirakkurd/TerraSync/releases/download/road-packs/pack_kurdistan_north.tsr",
            OfflineRoutingEngine.prebuiltPackUrl(OfflineMapManager.PACK_KURDISTAN_ID)
        )
    }

}

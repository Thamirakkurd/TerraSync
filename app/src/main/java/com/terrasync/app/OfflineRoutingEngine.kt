package com.terrasync.app

import android.content.Context
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.PriorityQueue
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class OfflineRoadRegion(
    val fileName: String,
    val name: String,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
    val nodeCount: Int,
    val edgeCount: Int,
    val sizeBytes: Long,
    val downloadedAt: Long
) {
    fun contains(lat: Double, lng: Double, padDeg: Double = 0.015): Boolean {
        return lat in (minLat - padDeg)..(maxLat + padDeg) &&
            lng in (minLng - padDeg)..(maxLng + padDeg)
    }
}

object OfflineRoutingEngine {

    private const val MAGIC = 0x54535231 // TSR1
    private const val FLAG_CAR = 1
    private const val FLAG_BIKE = 2
    private const val FLAG_WALK = 4

    private class RoadGraph(
        val meta: OfflineRoadRegion,
        val lats: FloatArray,
        val lngs: FloatArray,
        val adjHead: IntArray,
        val edgeTo: IntArray,
        val edgeNext: IntArray,
        val edgeDist: FloatArray,
        val edgeSpeed: FloatArray,
        val edgeFlags: ByteArray,
        val edgeNameIdx: IntArray,
        val names: Array<String>
    )

    @Volatile private var cachedGraph: RoadGraph? = null

    private fun dir(ctx: Context): File = File(ctx.filesDir, "offline_routes").apply { mkdirs() }

    private fun safeName(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(36).ifBlank { "region" }

    fun listRegions(ctx: Context): List<OfflineRoadRegion> {
        val files = dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".tsr") } ?: return emptyList()
        return files.mapNotNull { readHeader(it) }.sortedByDescending { it.downloadedAt }
    }

    fun deleteRegion(ctx: Context, fileName: String): Boolean {
        val f = File(dir(ctx), fileName)
        if (cachedGraph?.meta?.fileName == fileName) cachedGraph = null
        return f.exists() && f.delete()
    }

    fun hasAnyRegion(ctx: Context): Boolean {
        val files = dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".tsr") }
        return !files.isNullOrEmpty()
    }

    private fun readHeader(file: File): OfflineRoadRegion? {
        return runCatching {
            DataInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(file)))).use { dis ->
                val magic = dis.readInt()
                if (magic != MAGIC) return null
                val name = dis.readUTF()
                val minLat = dis.readDouble()
                val minLng = dis.readDouble()
                val maxLat = dis.readDouble()
                val maxLng = dis.readDouble()
                val downloadedAt = dis.readLong()
                val nodeCount = dis.readInt()
                val edgeCount = dis.readInt()
                OfflineRoadRegion(
                    fileName = file.name,
                    name = name,
                    minLat = minLat,
                    minLng = minLng,
                    maxLat = maxLat,
                    maxLng = maxLng,
                    nodeCount = nodeCount,
                    edgeCount = edgeCount,
                    sizeBytes = file.length(),
                    downloadedAt = downloadedAt
                )
            }
        }.getOrNull()
    }

    private fun loadGraph(ctx: Context, fileName: String): RoadGraph? {
        cachedGraph?.let { if (it.meta.fileName == fileName) return it }
        val file = File(dir(ctx), fileName)
        if (!file.exists()) return null
        return runCatching {
            DataInputStream(GZIPInputStream(BufferedInputStream(FileInputStream(file)))).use { dis ->
                val magic = dis.readInt()
                if (magic != MAGIC) return null
                val name = dis.readUTF()
                val minLat = dis.readDouble()
                val minLng = dis.readDouble()
                val maxLat = dis.readDouble()
                val maxLng = dis.readDouble()
                val downloadedAt = dis.readLong()
                val nodeCount = dis.readInt()
                val edgeCount = dis.readInt()
                val nameCount = dis.readInt()
                val names = Array(nameCount) { dis.readUTF() }

                val lats = FloatArray(nodeCount)
                val lngs = FloatArray(nodeCount)
                for (i in 0 until nodeCount) {
                    lats[i] = dis.readFloat()
                    lngs[i] = dis.readFloat()
                }

                val adjHead = IntArray(nodeCount) { -1 }
                val edgeTo = IntArray(edgeCount)
                val edgeNext = IntArray(edgeCount)
                val edgeDist = FloatArray(edgeCount)
                val edgeSpeed = FloatArray(edgeCount)
                val edgeFlags = ByteArray(edgeCount)
                val edgeNameIdx = IntArray(edgeCount)

                for (e in 0 until edgeCount) {
                    val from = dis.readInt()
                    val to = dis.readInt()
                    val dist = dis.readFloat()
                    val spd = dis.readFloat()
                    val flg = dis.readByte()
                    val nIdx = dis.readInt()
                    edgeTo[e] = to
                    edgeDist[e] = dist
                    edgeSpeed[e] = spd
                    edgeFlags[e] = flg
                    edgeNameIdx[e] = nIdx
                    edgeNext[e] = adjHead[from]
                    adjHead[from] = e
                }

                val meta = OfflineRoadRegion(
                    fileName = file.name,
                    name = name,
                    minLat = minLat,
                    minLng = minLng,
                    maxLat = maxLat,
                    maxLng = maxLng,
                    nodeCount = nodeCount,
                    edgeCount = edgeCount,
                    sizeBytes = file.length(),
                    downloadedAt = downloadedAt
                )
                RoadGraph(meta, lats, lngs, adjHead, edgeTo, edgeNext, edgeDist, edgeSpeed, edgeFlags, edgeNameIdx, names).also {
                    cachedGraph = it
                }
            }
        }.getOrNull()
    }

    fun downloadRoadGraph(
        ctx: Context,
        regionName: String,
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
        onProgress: (String) -> Unit,
        onDone: (OfflineRoadRegion?, String?) -> Unit
    ) {
        Thread {
            val sLat = minOf(minLat, maxLat)
            val nLat = maxOf(minLat, maxLat)
            val wLng = minOf(minLng, maxLng)
            val eLng = maxOf(minLng, maxLng)

            val query = """
                [out:json][timeout:60];
                way["highway"~"^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|track|path|footway|cycleway|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link|road)$"]($sLat,$wLng,$nLat,$eLng);
                out body;
                >;
                out skel qt;
            """.trimIndent()

            val endpoints = listOf(
                "https://overpass-api.de/api/interpreter",
                "https://overpass.kumi.systems/api/interpreter",
                "https://lz4.overpass-api.de/api/interpreter"
            )

            var jsonText: String? = null
            var lastErr = "Could not download road data"
            for ((idx, ep) in endpoints.withIndex()) {
                onProgress("Downloading OSM road network (${idx + 1}/${endpoints.size})...")
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(ep).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        connectTimeout = 20000
                        readTimeout = 70000
                        setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                        setRequestProperty("User-Agent", RoutingEngine.userAgent)
                    }
                    OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { w ->
                        w.write("data=" + URLEncoder.encode(query, "UTF-8"))
                    }
                    if (conn.responseCode == 200) {
                        jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                        break
                    } else {
                        lastErr = "Overpass server HTTP ${conn.responseCode}"
                    }
                } catch (e: Exception) {
                    lastErr = e.message ?: "Network error"
                } finally {
                    conn?.disconnect()
                }
            }

            if (jsonText.isNullOrBlank()) {
                onDone(null, lastErr)
                return@Thread
            }

            try {
                onProgress("Building offline routing graph...")
                val root = JSONObject(jsonText)
                val elems = root.optJSONArray("elements") ?: run {
                    onDone(null, "Empty road dataset")
                    return@Thread
                }

                val osmNodes = HashMap<Long, Pair<Float, Float>>(elems.length() / 2)
                val ways = ArrayList<JSONObject>()
                for (i in 0 until elems.length()) {
                    val el = elems.getJSONObject(i)
                    when (el.optString("type")) {
                        "node" -> {
                            val id = el.optLong("id")
                            val lat = el.optDouble("lat").toFloat()
                            val lon = el.optDouble("lon").toFloat()
                            osmNodes[id] = lat to lon
                        }
                        "way" -> ways.add(el)
                    }
                }

                val nodeIndexMap = HashMap<Long, Int>()
                val lats = ArrayList<Float>()
                val lngs = ArrayList<Float>()
                fun getIdx(osmId: Long): Int {
                    val existing = nodeIndexMap[osmId]
                    if (existing != null) return existing
                    val pt = osmNodes[osmId] ?: return -1
                    val idx = lats.size
                    nodeIndexMap[osmId] = idx
                    lats.add(pt.first)
                    lngs.add(pt.second)
                    return idx
                }

                val nameToIdx = LinkedHashMap<String, Int>()
                nameToIdx[""] = 0
                fun nameIdx(name: String): Int = nameToIdx.getOrPut(name) { nameToIdx.size }

                class TmpEdge(val from: Int, val to: Int, val dist: Float, val speed: Float, val flags: Byte, val nIdx: Int)
                val edges = ArrayList<TmpEdge>(ways.size * 4)

                for (way in ways) {
                    val tags = way.optJSONObject("tags") ?: continue
                    val hw = tags.optString("highway", "")
                    val access = tags.optString("access", "")
                    if (access == "private" || access == "no") continue

                    val roadName = tags.optString("name", "").trim()
                    val nIdx = nameIdx(roadName)
                    val onewayTag = tags.optString("oneway", "")
                    val junction = tags.optString("junction", "")
                    val isOneWayFwd = onewayTag == "yes" || onewayTag == "true" || onewayTag == "1" ||
                        junction == "roundabout" || hw == "motorway" || hw == "motorway_link"
                    val isOneWayRev = onewayTag == "-1"

                    val (carOk, bikeOk, walkOk, carSpeed) = when (hw) {
                        "motorway", "motorway_link" -> Quad(true, false, false, 95f)
                        "trunk", "trunk_link" -> Quad(true, true, true, 80f)
                        "primary", "primary_link" -> Quad(true, true, true, 65f)
                        "secondary", "secondary_link" -> Quad(true, true, true, 55f)
                        "tertiary", "tertiary_link" -> Quad(true, true, true, 45f)
                        "unclassified", "road" -> Quad(true, true, true, 40f)
                        "residential" -> Quad(true, true, true, 30f)
                        "living_street", "service" -> Quad(true, true, true, 20f)
                        "track" -> Quad(true, true, true, 16f)
                        "cycleway" -> Quad(false, true, true, 15f)
                        "path", "footway" -> Quad(false, true, true, 10f)
                        else -> Quad(false, false, false, 15f)
                    }

                    val fwdFlags = ((if (carOk && !isOneWayRev) FLAG_CAR else 0) or
                        (if (bikeOk && !isOneWayRev) FLAG_BIKE else 0) or
                        (if (walkOk) FLAG_WALK else 0)).toByte()
                    val revFlags = ((if (carOk && !isOneWayFwd) FLAG_CAR else 0) or
                        (if (bikeOk && !isOneWayFwd) FLAG_BIKE else 0) or
                        (if (walkOk) FLAG_WALK else 0)).toByte()

                    val nds = way.optJSONArray("nodes") ?: continue
                    var prev = -1
                    for (j in 0 until nds.length()) {
                        val curr = getIdx(nds.optLong(j))
                        if (prev >= 0 && curr >= 0 && prev != curr) {
                            val d = RoutingEngine.haversineMeters(
                                lats[prev].toDouble(), lngs[prev].toDouble(),
                                lats[curr].toDouble(), lngs[curr].toDouble()
                            ).toFloat().coerceAtLeast(0.5f)
                            if (fwdFlags.toInt() != 0) edges.add(TmpEdge(prev, curr, d, carSpeed, fwdFlags, nIdx))
                            if (revFlags.toInt() != 0) edges.add(TmpEdge(curr, prev, d, carSpeed, revFlags, nIdx))
                        }
                        if (curr >= 0) prev = curr
                    }
                }

                if (lats.isEmpty() || edges.isEmpty()) {
                    onDone(null, "No routable roads found in this area")
                    return@Thread
                }

                onProgress("Saving compressed road graph (${lats.size} intersections)...")
                val fileName = "${safeName(regionName)}_${System.currentTimeMillis() % 10000}.tsr"
                val outFile = File(dir(ctx), fileName)
                val now = System.currentTimeMillis()
                DataOutputStream(GZIPOutputStream(BufferedOutputStream(FileOutputStream(outFile)))).use { dos ->
                    dos.writeInt(MAGIC)
                    dos.writeUTF(regionName)
                    dos.writeDouble(sLat)
                    dos.writeDouble(wLng)
                    dos.writeDouble(nLat)
                    dos.writeDouble(eLng)
                    dos.writeLong(now)
                    dos.writeInt(lats.size)
                    dos.writeInt(edges.size)
                    dos.writeInt(nameToIdx.size)
                    for (nm in nameToIdx.keys) dos.writeUTF(nm)
                    for (i in 0 until lats.size) {
                        dos.writeFloat(lats[i])
                        dos.writeFloat(lngs[i])
                    }
                    for (e in edges) {
                        dos.writeInt(e.from)
                        dos.writeInt(e.to)
                        dos.writeFloat(e.dist)
                        dos.writeFloat(e.speed)
                        dos.writeByte(e.flags.toInt())
                        dos.writeInt(e.nIdx)
                    }
                }
                val meta = OfflineRoadRegion(
                    fileName = fileName,
                    name = regionName,
                    minLat = sLat,
                    minLng = wLng,
                    maxLat = nLat,
                    maxLng = eLng,
                    nodeCount = lats.size,
                    edgeCount = edges.size,
                    sizeBytes = outFile.length(),
                    downloadedAt = now
                )
                cachedGraph = null
                onDone(meta, null)
            } catch (e: Exception) {
                onDone(null, e.message ?: "Failed to compile offline graph")
            }
        }.start()
    }

    private data class Quad(val car: Boolean, val bike: Boolean, val walk: Boolean, val speed: Float)

    fun routeOffline(
        ctx: Context,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        mode: TravelMode
    ): Pair<NavRoute?, String?> {
        val regions = listRegions(ctx)
        if (regions.isEmpty()) {
            return null to "No offline road graphs downloaded (Settings → Offline Maps)"
        }
        val region = regions.firstOrNull { it.contains(startLat, startLng) && it.contains(endLat, endLng) }
            ?: regions.firstOrNull { it.contains(startLat, startLng, 0.08) && it.contains(endLat, endLng, 0.08) }
            ?: return null to "Route outside downloaded offline road regions"

        val g = loadGraph(ctx, region.fileName) ?: return null to "Could not load offline road graph"
        val modeFlag = when (mode) {
            TravelMode.CAR -> FLAG_CAR
            TravelMode.BICYCLE -> FLAG_BIKE
            TravelMode.WALKING -> FLAG_WALK
        }

        val startNode = findNearestNode(g, startLat, startLng, modeFlag)
        val endNode = findNearestNode(g, endLat, endLng, modeFlag)
        if (startNode < 0 || endNode < 0) {
            return null to "No nearby road in offline graph"
        }
        if (startNode == endNode) {
            val geom = listOf(startLng to startLat, endLng to endLat)
            val d = RoutingEngine.haversineMeters(startLat, startLng, endLat, endLng)
            val steps = listOf(NavStep("Arrive at destination", "🏁", d, endLat, endLng, d))
            return NavRoute(geom, d, d / 5.0, steps, mode, "Offline") to null
        }

        val n = g.lats.size
        val gScore = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val prevNode = IntArray(n) { -1 }
        val prevEdge = IntArray(n) { -1 }
        val closed = BooleanArray(n)

        val maxSpeedMps = when (mode) {
            TravelMode.CAR -> 100.0 / 3.6
            TravelMode.BICYCLE -> 22.0 / 3.6
            TravelMode.WALKING -> 5.5 / 3.6
        }

        class QNode(val u: Int, val f: Double) : Comparable<QNode> {
            override fun compareTo(other: QNode): Int = f.compareTo(other.f)
        }

        val endLatD = g.lats[endNode].toDouble()
        val endLngD = g.lngs[endNode].toDouble()
        val pq = PriorityQueue<QNode>()
        gScore[startNode] = 0.0
        pq.add(QNode(startNode, RoutingEngine.haversineMeters(g.lats[startNode].toDouble(), g.lngs[startNode].toDouble(), endLatD, endLngD) / maxSpeedMps))

        var found = false
        while (pq.isNotEmpty()) {
            val cur = pq.poll() ?: break
            val u = cur.u
            if (closed[u]) continue
            closed[u] = true
            if (u == endNode) {
                found = true
                break
            }
            var e = g.adjHead[u]
            while (e != -1) {
                if ((g.edgeFlags[e].toInt() and modeFlag) != 0) {
                    val v = g.edgeTo[e]
                    if (!closed[v]) {
                        val spdKmh = when (mode) {
                            TravelMode.CAR -> g.edgeSpeed[e].toDouble().coerceAtLeast(10.0)
                            TravelMode.BICYCLE -> 16.0
                            TravelMode.WALKING -> 4.8
                        }
                        val costSec = g.edgeDist[e].toDouble() / (spdKmh / 3.6)
                        val tentative = gScore[u] + costSec
                        if (tentative < gScore[v]) {
                            gScore[v] = tentative
                            prevNode[v] = u
                            prevEdge[v] = e
                            val h = RoutingEngine.haversineMeters(g.lats[v].toDouble(), g.lngs[v].toDouble(), endLatD, endLngD) / maxSpeedMps
                            pq.add(QNode(v, tentative + h))
                        }
                    }
                }
                e = g.edgeNext[e]
            }
        }

        if (!found) {
            return null to "No offline road connection found between points"
        }

        val pathNodes = ArrayList<Int>()
        val pathEdges = ArrayList<Int>()
        var cur = endNode
        while (cur != -1) {
            pathNodes.add(cur)
            val pe = prevEdge[cur]
            if (pe != -1) pathEdges.add(pe)
            cur = prevNode[cur]
        }
        pathNodes.reverse()
        pathEdges.reverse()

        val geom = ArrayList<Pair<Double, Double>>(pathNodes.size + 2)
        geom.add(startLng to startLat)
        for (idx in pathNodes) {
            geom.add(g.lngs[idx].toDouble() to g.lats[idx].toDouble())
        }
        geom.add(endLng to endLat)

        var totalDist = 0.0
        var totalDur = 0.0
        val cumDists = DoubleArray(pathNodes.size)
        for (i in pathEdges.indices) {
            val e = pathEdges[i]
            val d = g.edgeDist[e].toDouble()
            val spdKmh = when (mode) {
                TravelMode.CAR -> g.edgeSpeed[e].toDouble().coerceAtLeast(10.0)
                TravelMode.BICYCLE -> 16.0
                TravelMode.WALKING -> 4.8
            }
            totalDist += d
            totalDur += d / (spdKmh / 3.6)
            cumDists[i + 1] = totalDist
        }

        val steps = buildOfflineSteps(g, pathNodes, pathEdges, cumDists, totalDist, endLat, endLng)
        return NavRoute(geom, totalDist, totalDur, steps, mode, "Offline (${region.name})") to null
    }

    private fun findNearestNode(g: RoadGraph, lat: Double, lng: Double, modeFlag: Int): Int {
        var bestIdx = -1
        var bestDist = Double.MAX_VALUE
        val cosLat = cos(Math.toRadians(lat))
        for (i in g.lats.indices) {
            // Ensure node has at least one edge matching mode
            var e = g.adjHead[i]
            var ok = false
            while (e != -1) {
                if ((g.edgeFlags[e].toInt() and modeFlag) != 0) {
                    ok = true
                    break
                }
                e = g.edgeNext[e]
            }
            if (!ok) continue
            val dLat = (g.lats[i] - lat) * 111132.0
            val dLng = (g.lngs[i] - lng) * 111320.0 * cosLat
            val dSq = dLat * dLat + dLng * dLng
            if (dSq < bestDist) {
                bestDist = dSq
                bestIdx = i
            }
        }
        return if (bestIdx >= 0 && sqrt(bestDist) <= 3500.0) bestIdx else -1
    }

    private fun bearingDeg(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLam = Math.toRadians(lng2 - lng1)
        val y = sin(dLam) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLam)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun buildOfflineSteps(
        g: RoadGraph,
        nodes: List<Int>,
        edges: List<Int>,
        cumDists: DoubleArray,
        totalDist: Double,
        endLat: Double,
        endLng: Double
    ): List<NavStep> {
        val steps = ArrayList<NavStep>()
        if (edges.isEmpty()) {
            steps.add(NavStep("Arrive at destination", "🏁", totalDist, endLat, endLng, totalDist))
            return steps
        }
        val firstRoad = g.names.getOrElse(g.edgeNameIdx[edges.first()]) { "" }
        val ontoFirst = if (firstRoad.isNotBlank()) " onto $firstRoad" else ""
        steps.add(NavStep("Head out$ontoFirst", "↑", 0.0, g.lats[nodes[0]].toDouble(), g.lngs[nodes[0]].toDouble(), 0.0))

        var lastStepDist = 0.0
        for (i in 1 until nodes.size - 1) {
            val prevNode = nodes[i - 1]
            val curNode = nodes[i]
            val nextNode = nodes[i + 1]
            val b1 = bearingDeg(g.lats[prevNode].toDouble(), g.lngs[prevNode].toDouble(), g.lats[curNode].toDouble(), g.lngs[curNode].toDouble())
            val b2 = bearingDeg(g.lats[curNode].toDouble(), g.lngs[curNode].toDouble(), g.lats[nextNode].toDouble(), g.lngs[nextNode].toDouble())
            var delta = (b2 - b1 + 540.0) % 360.0 - 180.0
            val prevRoad = g.names.getOrElse(g.edgeNameIdx[edges[i - 1]]) { "" }
            val nextRoad = g.names.getOrElse(g.edgeNameIdx[edges[i]]) { "" }
            val roadChanged = nextRoad.isNotBlank() && nextRoad != prevRoad
            val distHere = cumDists[i]

            if ((abs(delta) >= 38.0 || roadChanged) && (distHere - lastStepDist >= 25.0)) {
                val onto = if (nextRoad.isNotBlank()) " onto $nextRoad" else ""
                val (arrow, instr) = when {
                    delta > 125.0 -> "↘" to "Sharp right$onto"
                    delta > 45.0 -> "↱" to "Turn right$onto"
                    delta > 25.0 -> "↗" to "Keep right$onto"
                    delta < -125.0 -> "↙" to "Sharp left$onto"
                    delta < -45.0 -> "↰" to "Turn left$onto"
                    delta < -25.0 -> "↖" to "Keep left$onto"
                    else -> "↑" to "Continue$onto"
                }
                steps.add(NavStep(instr, arrow, distHere - lastStepDist, g.lats[curNode].toDouble(), g.lngs[curNode].toDouble(), distHere))
                lastStepDist = distHere
            }
        }
        steps.add(NavStep("Arrive at destination", "🏁", (totalDist - lastStepDist).coerceAtLeast(0.0), endLat, endLng, totalDist))
        return steps
    }
}

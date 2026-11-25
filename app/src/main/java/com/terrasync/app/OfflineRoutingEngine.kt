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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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

    fun areaDegSq(): Double {
        return ((maxLat - minLat).coerceAtLeast(0.0001)) * ((maxLng - minLng).coerceAtLeast(0.0001))
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
        val nodeOutFlags: ByteArray,
        val nodeInFlags: ByteArray,
        val names: Array<String>
    )

    private val graphCacheLock = Any()
    @Volatile private var cachedGraph: RoadGraph? = null
    private data class CachedHeader(val lastModified: Long, val sizeBytes: Long, val region: OfflineRoadRegion)
    private val headerCache = ConcurrentHashMap<String, CachedHeader>()
    private val activeDownloads = ConcurrentHashMap<String, AtomicBoolean>()

    private fun dir(ctx: Context): File = File(ctx.filesDir, "offline_routes").apply { mkdirs() }

    private fun safeName(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(36).ifBlank { "region" }

    fun packFileName(packId: String): String = "pack_${safeName(packId)}.tsr"

    fun getPackRegion(ctx: Context, packId: String): OfflineRoadRegion? {
        val fileName = packFileName(packId)
        val f = File(dir(ctx), fileName)
        if (!f.exists() || !f.isFile) {
            headerCache.remove(fileName)
            return null
        }
        return readHeader(f)
    }

    fun isDownloading(packId: String): Boolean = activeDownloads.containsKey(packId)

    fun cancelDownload(packId: String) {
        activeDownloads[packId]?.set(true)
    }

    fun deletePackRegion(ctx: Context, packId: String): Boolean {
        cancelDownload(packId)
        return deleteRegion(ctx, packFileName(packId))
    }

    fun listRegions(ctx: Context): List<OfflineRoadRegion> {
        val files = dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".tsr") } ?: return emptyList()
        return files.mapNotNull { readHeader(it) }.sortedByDescending { it.downloadedAt }
    }

    fun deleteRegion(ctx: Context, fileName: String): Boolean {
        val f = File(dir(ctx), fileName)
        headerCache.remove(fileName)
        synchronized(graphCacheLock) {
            if (cachedGraph?.meta?.fileName == fileName) cachedGraph = null
        }
        return f.exists() && f.delete()
    }

    fun hasAnyRegion(ctx: Context): Boolean {
        val files = dir(ctx).listFiles { f -> f.isFile && f.name.endsWith(".tsr") }
        return !files.isNullOrEmpty()
    }

    private fun readHeader(file: File): OfflineRoadRegion? {
        val lm = file.lastModified()
        val len = file.length()
        val cached = headerCache[file.name]
        if (cached != null && cached.lastModified == lm && cached.sizeBytes == len) {
            return cached.region
        }
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
                    sizeBytes = len,
                    downloadedAt = downloadedAt
                ).also { reg ->
                    headerCache[file.name] = CachedHeader(lm, len, reg)
                }
            }
        }.getOrNull()
    }

    private fun loadGraph(ctx: Context, fileName: String): RoadGraph? = synchronized(graphCacheLock) {
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
                val nodeOutFlags = ByteArray(nodeCount)
                val nodeInFlags = ByteArray(nodeCount)

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
                    nodeOutFlags[from] = (nodeOutFlags[from].toInt() or flg.toInt()).toByte()
                    nodeInFlags[to] = (nodeInFlags[to].toInt() or flg.toInt()).toByte()
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
                RoadGraph(
                    meta, lats, lngs, adjHead, edgeTo, edgeNext, edgeDist, edgeSpeed,
                    edgeFlags, edgeNameIdx, nodeOutFlags, nodeInFlags, names
                ).also {
                    cachedGraph = it
                }
            }
        }.getOrNull()
    }

    fun downloadPackRoadGraph(
        ctx: Context,
        packId: String,
        regionName: String,
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
        onProgress: (String) -> Unit,
        onDone: (OfflineRoadRegion?, String?) -> Unit
    ) {
        downloadRoadGraphInternal(ctx, packId, regionName, minLat, minLng, maxLat, maxLng, onProgress, onDone)
    }

    private fun downloadRoadGraphInternal(
        ctx: Context,
        packId: String?,
        regionName: String,
        minLat: Double,
        minLng: Double,
        maxLat: Double,
        maxLng: Double,
        onProgress: (String) -> Unit,
        onDone: (OfflineRoadRegion?, String?) -> Unit
    ) {
        val jobKey = packId ?: safeName(regionName)
        val cancelFlag = AtomicBoolean(false)
        if (activeDownloads.putIfAbsent(jobKey, cancelFlag) != null) {
            onDone(null, "Road download already running")
            return
        }

        Thread {
            try {
                val sLat = minOf(minLat, maxLat)
                val nLat = maxOf(minLat, maxLat)
                val wLng = minOf(minLng, maxLng)
                val eLng = maxOf(minLng, maxLng)
                val maxSpan = maxOf(nLat - sLat, eLng - wLng)

                val fullStreetRegex =
                    "^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service|track|path|footway|cycleway|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link|road)$"
                val arterialRegex =
                    "^(motorway|trunk|primary|secondary|tertiary|unclassified|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"
                val highwayOnlyRegex =
                    "^(motorway|trunk|primary|secondary|tertiary|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$"

                fun buildQuery(regex: String, aLat: Double, aLng: Double, bLat: Double, bLng: Double): String = """
                    [out:json][timeout:60];
                    way["highway"~"$regex"]($aLat,$aLng,$bLat,$bLng);
                    out body;
                    >;
                    out skel qt;
                """.trimIndent()

                val chunks: List<Pair<String, String>> = if (packId == OfflineMapManager.PACK_KURDISTAN_ID) {
                    listOf(
                        "Kurdistan highways" to buildQuery(arterialRegex, sLat, wLng, nLat, eLng),
                        "Duhok, Akre & Zakho streets" to buildQuery(fullStreetRegex, 36.65, 42.55, 37.22, 44.08),
                        "Erbil streets" to buildQuery(fullStreetRegex, 36.05, 43.88, 36.32, 44.18),
                        "Sulaymaniyah streets" to buildQuery(fullStreetRegex, 35.48, 45.32, 35.65, 45.55)
                    )
                } else {
                    val reg = if (maxSpan <= 1.4) fullStreetRegex else highwayOnlyRegex
                    listOf("$regionName roads" to buildQuery(reg, sLat, wLng, nLat, eLng))
                }

                val endpoints = listOf(
                    "https://overpass-api.de/api/interpreter",
                    "https://overpass.kumi.systems/api/interpreter",
                    "https://lz4.overpass-api.de/api/interpreter"
                )

                val seenWayIds = HashSet<Long>(16384)
                val nodeIndexMap = HashMap<Long, Int>(65536)
                val lats = ArrayList<Float>(65536)
                val lngs = ArrayList<Float>(65536)
                val nameToIdx = LinkedHashMap<String, Int>()
                nameToIdx[""] = 0
                fun nameIdx(name: String): Int = nameToIdx.getOrPut(name) { nameToIdx.size }

                class TmpEdge(val from: Int, val to: Int, val dist: Float, val speed: Float, val flags: Byte, val nIdx: Int)
                val edges = ArrayList<TmpEdge>(65536)

                var fetchedAny = false
                var lastErr = "Could not download road data"

                for ((cIdx, pair) in chunks.withIndex()) {
                    val (chunkLabel, query) = pair
                    if (cancelFlag.get()) {
                        onDone(null, "Cancelled")
                        return@Thread
                    }
                    var elems: org.json.JSONArray? = null
                    for (ep in endpoints) {
                        if (cancelFlag.get()) {
                            onDone(null, "Cancelled")
                            return@Thread
                        }
                        onProgress("Downloading roads (${cIdx + 1}/${chunks.size}: $chunkLabel)...")
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
                                val rawBody = conn.inputStream.bufferedReader().use { it.readText() }
                                val parsedArr = JSONObject(rawBody).optJSONArray("elements")
                                if (parsedArr != null) {
                                    elems = parsedArr
                                    break
                                } else {
                                    lastErr = "Overpass returned empty response"
                                }
                            } else {
                                lastErr = "Overpass server HTTP ${conn.responseCode}"
                            }
                        } catch (e: Exception) {
                            lastErr = e.message ?: "Network error"
                        } finally {
                            conn?.disconnect()
                        }
                    }

                    val chunkElems = elems ?: continue
                    fetchedAny = true
                    val chunkNodes = HashMap<Long, Pair<Float, Float>>(16384)
                    for (i in 0 until chunkElems.length()) {
                        val el = chunkElems.getJSONObject(i)
                        if (el.optString("type") == "node") {
                            val id = el.optLong("id")
                            chunkNodes[id] = el.optDouble("lat").toFloat() to el.optDouble("lon").toFloat()
                        }
                    }

                    fun getIdx(osmId: Long): Int {
                        val existing = nodeIndexMap[osmId]
                        if (existing != null) return existing
                        val pt = chunkNodes[osmId] ?: return -1
                        val idx = lats.size
                        nodeIndexMap[osmId] = idx
                        lats.add(pt.first)
                        lngs.add(pt.second)
                        return idx
                    }

                    for (i in 0 until chunkElems.length()) {
                        val way = chunkElems.getJSONObject(i)
                        if (way.optString("type") != "way") continue
                        val wid = way.optLong("id")
                        if (!seenWayIds.add(wid)) continue
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
                    chunkNodes.clear()
                }

                if (cancelFlag.get()) {
                    onDone(null, "Cancelled")
                    return@Thread
                }
                if (!fetchedAny || edges.isEmpty() || lats.isEmpty()) {
                    onDone(null, if (!fetchedAny) lastErr else "No routable roads found in this area")
                    return@Thread
                }

                onProgress("Building offline road graph...")

                onProgress("Saving offline roads (${lats.size} nodes)...")
                val fileName = if (packId != null) packFileName(packId) else "${safeName(regionName)}_${System.currentTimeMillis() % 10000}.tsr"
                val outFile = File(dir(ctx), fileName)
                val tmpFile = File(dir(ctx), "$fileName.tmp")
                val now = System.currentTimeMillis()
                DataOutputStream(GZIPOutputStream(BufferedOutputStream(FileOutputStream(tmpFile)))).use { dos ->
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
                if (cancelFlag.get()) {
                    tmpFile.delete()
                    onDone(null, "Cancelled")
                    return@Thread
                }
                if (outFile.exists()) outFile.delete()
                if (!tmpFile.renameTo(outFile)) {
                    tmpFile.copyTo(outFile, overwrite = true)
                    tmpFile.delete()
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
                synchronized(graphCacheLock) {
                    if (cachedGraph?.meta?.fileName == fileName) cachedGraph = null
                }
                headerCache[fileName] = CachedHeader(outFile.lastModified(), outFile.length(), meta)
                onDone(meta, null)
            } catch (oom: OutOfMemoryError) {
                onDone(null, "Not enough memory to process road data")
            } catch (t: Throwable) {
                onDone(null, t.message ?: "Failed to compile offline graph")
            } finally {
                activeDownloads.remove(jobKey, cancelFlag)
            }
        }.apply {
            name = "terrasync-road-dl-$jobKey"
            isDaemon = true
            start()
        }
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
            return null to "No offline maps with routing downloaded (Settings → Offline Maps)"
        }
        // Prefer the smallest (most detailed) matching bounding box first
        val sortedByDetail = regions.sortedBy { it.areaDegSq() }
        val candidates = sortedByDetail.filter { it.contains(startLat, startLng) && it.contains(endLat, endLng) }
            .ifEmpty { sortedByDetail.filter { it.contains(startLat, startLng, 0.08) && it.contains(endLat, endLng, 0.08) } }
        if (candidates.isEmpty()) {
            return null to "Route outside downloaded offline map regions"
        }

        val modeFlag = when (mode) {
            TravelMode.CAR -> FLAG_CAR
            TravelMode.BICYCLE -> FLAG_BIKE
            TravelMode.WALKING -> FLAG_WALK
        }

        var lastFailure = "Could not compute offline route"
        for (region in candidates) {
            val g = loadGraph(ctx, region.fileName) ?: continue
            val res = computeAStar(g, startLat, startLng, endLat, endLng, mode, modeFlag)
            if (res.first != null) return res
            if (!res.second.isNullOrBlank()) lastFailure = res.second!!
        }
        return null to lastFailure
    }

    private fun computeAStar(
        g: RoadGraph,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        mode: TravelMode,
        modeFlag: Int
    ): Pair<NavRoute?, String?> {
        val startCandidates = findNearestNodes(g, startLat, startLng, modeFlag, needIncoming = false, maxCount = 3)
        val endCandidates = findNearestNodes(g, endLat, endLng, modeFlag, needIncoming = true, maxCount = 3)
        if (startCandidates.isEmpty() || endCandidates.isEmpty()) {
            return null to "No nearby road in offline map"
        }

        // Try closest pair first, then fall back to secondary nearby nodes if snapped onto a disconnected OSM stub
        val pairs = buildList {
            add(startCandidates[0] to endCandidates[0])
            for (s in startCandidates) {
                for (e in endCandidates) {
                    if (s == startCandidates[0] && e == endCandidates[0]) continue
                    add(s to e)
                }
            }
        }

        var lastError = "No offline road connection found between points"
        for ((startNode, endNode) in pairs) {
            val res = runAStarBetweenNodes(g, startNode, endNode, startLat, startLng, endLat, endLng, mode, modeFlag)
            if (res.first != null) return res
            if (!res.second.isNullOrBlank()) lastError = res.second!!
        }
        return null to lastError
    }

    private fun runAStarBetweenNodes(
        g: RoadGraph,
        startNode: Int,
        endNode: Int,
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        mode: TravelMode,
        modeFlag: Int
    ): Pair<NavRoute?, String?> {
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
        val invMaxSpeed = 0.985 / maxSpeedMps // slight safety margin keeps equirectangular heuristic admissible

        class QNode(val u: Int, val f: Double) : Comparable<QNode> {
            override fun compareTo(other: QNode): Int = f.compareTo(other.f)
        }

        val endLatD = g.lats[endNode].toDouble()
        val endLngD = g.lngs[endNode].toDouble()
        val mPerDegLng = 111320.0 * cos(Math.toRadians((startLat + endLatD) * 0.5))

        fun fastHeuristicSec(lat: Float, lng: Float): Double {
            val dy = (lat - endLatD) * 111132.0
            val dx = (lng - endLngD) * mPerDegLng
            return sqrt(dx * dx + dy * dy) * invMaxSpeed
        }

        val pq = PriorityQueue<QNode>(256)
        gScore[startNode] = 0.0
        pq.add(QNode(startNode, fastHeuristicSec(g.lats[startNode], g.lngs[startNode])))

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
                            pq.add(QNode(v, tentative + fastHeuristicSec(g.lats[v], g.lngs[v])))
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

        val approachSpeedMps = when (mode) {
            TravelMode.CAR -> 25.0 / 3.6
            TravelMode.BICYCLE -> 14.0 / 3.6
            TravelMode.WALKING -> 4.8 / 3.6
        }
        val firstNode = pathNodes.first()
        val lastNode = pathNodes.last()
        val startApproach = RoutingEngine.haversineMeters(
            startLat, startLng, g.lats[firstNode].toDouble(), g.lngs[firstNode].toDouble()
        )
        val endApproach = RoutingEngine.haversineMeters(
            g.lats[lastNode].toDouble(), g.lngs[lastNode].toDouble(), endLat, endLng
        )

        var totalDist = startApproach
        var totalDur = startApproach / approachSpeedMps
        val cumDists = DoubleArray(pathNodes.size)
        cumDists[0] = startApproach
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
        totalDist += endApproach
        totalDur += endApproach / approachSpeedMps

        val steps = buildOfflineSteps(g, pathNodes, pathEdges, cumDists, totalDist, endLat, endLng)
        return NavRoute(geom, totalDist, totalDur, steps, mode, "Offline") to null
    }

    private fun findNearestNodes(
        g: RoadGraph,
        lat: Double,
        lng: Double,
        modeFlag: Int,
        needIncoming: Boolean = false,
        maxCount: Int = 3
    ): List<Int> {
        val maxDistSq = 3500.0 * 3500.0
        val cosLat = cos(Math.toRadians(lat))
        val mPerDegLng = 111320.0 * cosLat
        val flagsArr = if (needIncoming) g.nodeInFlags else g.nodeOutFlags
        val bestIdx = IntArray(maxCount) { -1 }
        val bestDistSq = DoubleArray(maxCount) { maxDistSq }
        var worstPos = 0

        for (i in g.lats.indices) {
            if ((flagsArr[i].toInt() and modeFlag) == 0) continue
            val dLat = (g.lats[i] - lat) * 111132.0
            val dLatSq = dLat * dLat
            val limit = bestDistSq[worstPos]
            if (dLatSq >= limit) continue
            val dLng = (g.lngs[i] - lng) * mPerDegLng
            val dSq = dLatSq + dLng * dLng
            if (dSq < limit) {
                bestIdx[worstPos] = i
                bestDistSq[worstPos] = dSq
                // Recompute worst slot among the small top-K array
                var maxD = bestDistSq[0]
                var maxP = 0
                for (k in 1 until maxCount) {
                    if (bestDistSq[k] > maxD) {
                        maxD = bestDistSq[k]
                        maxP = k
                    }
                }
                worstPos = maxP
            }
        }
        return (0 until maxCount)
            .filter { bestIdx[it] >= 0 }
            .sortedBy { bestDistSq[it] }
            .map { bestIdx[it] }
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
            val distHere = cumDists[i]
            if (distHere - lastStepDist < 30.0) continue

            // Look ~20m behind and ~20m ahead so curved OSM road segments produce accurate turn angles
            var backIdx = i - 1
            while (backIdx > 0 && (distHere - cumDists[backIdx]) < 20.0) backIdx--
            var fwdIdx = i + 1
            while (fwdIdx < nodes.size - 1 && (cumDists[fwdIdx] - distHere) < 20.0) fwdIdx++

            val bNode = nodes[backIdx]
            val cNode = nodes[i]
            val fNode = nodes[fwdIdx]
            val b1 = bearingDeg(g.lats[bNode].toDouble(), g.lngs[bNode].toDouble(), g.lats[cNode].toDouble(), g.lngs[cNode].toDouble())
            val b2 = bearingDeg(g.lats[cNode].toDouble(), g.lngs[cNode].toDouble(), g.lats[fNode].toDouble(), g.lngs[fNode].toDouble())
            val delta = (b2 - b1 + 540.0) % 360.0 - 180.0

            val prevRoad = g.names.getOrElse(g.edgeNameIdx[edges[i - 1]]) { "" }
            val nextRoad = g.names.getOrElse(g.edgeNameIdx[edges[i]]) { "" }
            val roadChanged = nextRoad.isNotBlank() && nextRoad != prevRoad

            if (abs(delta) >= 40.0 || roadChanged) {
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
                steps.add(NavStep(instr, arrow, distHere - lastStepDist, g.lats[cNode].toDouble(), g.lngs[cNode].toDouble(), distHere))
                lastStepDist = distHere
            }
        }
        steps.add(NavStep("Arrive at destination", "🏁", (totalDist - lastStepDist).coerceAtLeast(0.0), endLat, endLng, totalDist))
        return steps
    }
}

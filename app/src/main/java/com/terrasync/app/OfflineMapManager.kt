package com.terrasync.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

data class OfflinePackSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double,
    val minZoom: Double,
    val maxZoom: Double
)

data class OfflinePackState(
    val isDownloaded: Boolean = false,
    val isDownloading: Boolean = false,
    val progressPercent: Int = 0,
    val completedResources: Long = 0L,
    val requiredResources: Long = 0L,
    val sizeBytes: Long = 0L,
    val statusText: String = "Not downloaded (online only)",
    val mapError: String? = null,
    val hasRoadGraph: Boolean = false,
    val isRoadDownloading: Boolean = false,
    val isRoadQueued: Boolean = false,
    val roadProgressPercent: Int = 0,
    val roadSizeBytes: Long = 0L,
    val roadStatusText: String = "",
    val roadError: String? = null,
    val hasPartialRoadData: Boolean = false,
    val hasPartialMapData: Boolean = false
) {
    val hasAnyData: Boolean
        get() = isDownloaded || isDownloading || completedResources > 0L || hasPartialMapData ||
            hasRoadGraph || isRoadDownloading || hasPartialRoadData
}

data class ActiveDownloadSummary(
    val packId: String,
    val packTitle: String,
    val stageLabel: String,
    val detailText: String,
    val percent: Int,
    val indeterminate: Boolean
)

class OfflineMapManager private constructor(context: Context) {

    companion object {
        const val STYLE_STREET_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val STYLE_DARK_URL = "https://tiles.openfreemap.org/styles/fiord"
        const val PACK_KURDISTAN_ID = "kurdistan_north"
        const val PACK_SOUTHERN_IRAQ_ID = "iraq_south"
        internal const val PREBUILT_MAP_PACK_BASE_URL =
            "https://github.com/Thamirakkurd/TerraSync/releases/download/road-packs"
        private const val MIN_FREE_BYTES_KURDISTAN = 250L * 1024L * 1024L
        private const val MIN_FREE_BYTES_SOUTH = 150L * 1024L * 1024L

        internal fun prebuiltMapPackUrl(packId: String): String =
            "$PREBUILT_MAP_PACK_BASE_URL/${LocalPmTilesStore.packFileName(packId)}"

        val REGIONAL_PACKS = listOf(
            OfflinePackSpec(
                id = PACK_KURDISTAN_ID,
                title = "Kurdistan",
                subtitle = "Duhok, Akre, Zakho, Erbil, Sulaymaniyah, Halabja & Kirkuk (highways + embedded HD streets)",
                minLat = 34.45,
                maxLat = 37.38,
                minLng = 42.35,
                maxLng = 46.35,
                minZoom = 6.0,
                maxZoom = 13.0
            ),
            OfflinePackSpec(
                id = PACK_SOUTHERN_IRAQ_ID,
                title = "Southern Iraq",
                subtitle = "Baghdad, Anbar, Diyala, Karbala, Najaf, Wasit, Dhi Qar, Basra & highways",
                minLat = 29.05,
                maxLat = 34.45,
                minLng = 38.75,
                maxLng = 48.65,
                minZoom = 6.0,
                maxZoom = 11.0
            )
        )

        @Volatile private var instance: OfflineMapManager? = null

        fun getInstance(context: Context): OfflineMapManager {
            return instance ?: synchronized(this) {
                instance ?: OfflineMapManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appCtx: Context = context.applicationContext
    private val manager: OfflineManager = OfflineManager.getInstance(appCtx).apply {
        setOfflineMapboxTileCountLimit(80000L)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val regionHandles = ConcurrentHashMap<String, OfflineRegion>()
    private val states = ConcurrentHashMap<String, OfflinePackState>()
    private val roadProgressNotes = ConcurrentHashMap<String, String>()
    private val roadProgressPercents = ConcurrentHashMap<String, Int>()
    private val roadErrors = ConcurrentHashMap<String, String>()
    private val mapErrors = ConcurrentHashMap<String, String>()
    private val autoChainRoads = ConcurrentHashMap<String, Boolean>()
    private val activeTilePacks = ConcurrentHashMap.newKeySet<String>()
    private val pmTilesDownloads = ConcurrentHashMap<String, ParallelFileDownloader.Handle>()
    private val pmTilesDiskCache = ConcurrentHashMap<String, LocalPmTilesStore.PackInfo>()
    private val pmTilesPartialCache = ConcurrentHashMap<String, ParallelFileDownloader.PartialProgress>()
    private val pendingDownloads = ConcurrentHashMap<String, Pair<OfflinePackSpec, Float>>()
    private val lastProgressEmitMs = ConcurrentHashMap<String, Long>()
    private data class RoadDiskSnapshot(val region: OfflineRoadRegion?, val hasPartial: Boolean)
    private val roadDiskCache = ConcurrentHashMap<String, RoadDiskSnapshot>()
    private val diskRefreshInFlight = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var regionsLoaded = false
    @Volatile private var uiListener: (() -> Unit)? = null
    @Volatile private var serviceListener: (() -> Unit)? = null
    @Volatile private var mapPackInstalledListener: ((String) -> Unit)? = null

    init {
        DevicePrefs.getPendingRoadChainPackIds(appCtx).forEach { packId ->
            autoChainRoads[packId] = true
        }
        REGIONAL_PACKS.forEach { refreshRoadDiskCacheAsync(it.id) }
    }

    private fun setRoadChainPending(packId: String, pending: Boolean) {
        if (pending) {
            autoChainRoads[packId] = true
        } else {
            autoChainRoads.remove(packId)
        }
        DevicePrefs.setPackRoadChainPending(appCtx, packId, pending)
    }

    private fun getCachedRoadSnapshot(packId: String): RoadDiskSnapshot {
        roadDiskCache[packId]?.let { return it }
        refreshRoadDiskCacheAsync(packId)
        return RoadDiskSnapshot(null, false)
    }

    private fun refreshRoadDiskCacheAsync(
        packId: String,
        force: Boolean = false,
        onDone: ((RoadDiskSnapshot) -> Unit)? = null
    ) {
        if (!force && !diskRefreshInFlight.add(packId)) return
        if (force) diskRefreshInFlight.add(packId)
        RoutingEngine.batchIoExecutor.execute {
            try {
                val pmInfo = LocalPmTilesStore.getPackInfo(appCtx, packId)
                if (pmInfo != null) {
                    pmTilesDiskCache[packId] = pmInfo
                    pmTilesPartialCache.remove(packId)
                } else {
                    pmTilesDiskCache.remove(packId)
                    val partFile = File(LocalPmTilesStore.dir(appCtx), "${LocalPmTilesStore.packFileName(packId)}.part")
                    ParallelFileDownloader.readPartialProgress(partFile)?.let {
                        pmTilesPartialCache[packId] = it
                    } ?: pmTilesPartialCache.remove(packId)
                }
                val snap = RoadDiskSnapshot(
                    region = OfflineRoutingEngine.getPackRegion(appCtx, packId),
                    hasPartial = OfflineRoutingEngine.hasPartialPackData(appCtx, packId)
                )
                roadDiskCache[packId] = snap
                runOnMain {
                    if (states.containsKey(packId) || uiListener != null || regionsLoaded) {
                        syncRoadFields(packId)
                    }
                    onDone?.invoke(snap)
                }
            } finally {
                diskRefreshInFlight.remove(packId)
            }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun notifyListeners() {
        runOnMain {
            uiListener?.invoke()
            serviceListener?.invoke()
        }
    }

    fun setStateListener(listener: (() -> Unit)?) {
        uiListener = listener
    }

    fun setServiceListener(listener: (() -> Unit)?) {
        serviceListener = listener
    }

    fun setOnMapPackInstalledListener(listener: ((String) -> Unit)?) {
        mapPackInstalledListener = listener
    }

    private fun isPmTilesDownloading(packId: String): Boolean {
        val h = pmTilesDownloads[packId] ?: return false
        return !h.cancelled.get()
    }

    fun hasAnyActiveDownload(): Boolean {
        if (pendingDownloads.isNotEmpty() || activeTilePacks.isNotEmpty() ||
            pmTilesDownloads.values.any { !it.cancelled.get() } ||
            autoChainRoads.values.any { it }
        ) {
            return true
        }
        return REGIONAL_PACKS.any { pack ->
            val st = getState(pack.id)
            st.isDownloading || st.isRoadDownloading || OfflineRoutingEngine.isDownloading(pack.id)
        }
    }

    fun getActiveSummary(): ActiveDownloadSummary? {
        for (pack in REGIONAL_PACKS) {
            val st = getState(pack.id)
            if (st.isDownloading) {
                val mb = formatSize(st.sizeBytes)
                val detail = if (st.requiredResources > 0) {
                    "${st.progressPercent}% (${st.completedResources}/${st.requiredResources}) • $mb"
                } else {
                    st.statusText.ifBlank { "Downloading ${st.progressPercent}%" }
                }
                return ActiveDownloadSummary(
                    packId = pack.id,
                    packTitle = pack.title,
                    stageLabel = "Map Tiles",
                    detailText = detail,
                    percent = st.progressPercent,
                    indeterminate = st.requiredResources <= 0L && st.progressPercent <= 0
                )
            }
            if (st.isRoadDownloading || OfflineRoutingEngine.isDownloading(pack.id)) {
                return ActiveDownloadSummary(
                    packId = pack.id,
                    packTitle = pack.title,
                    stageLabel = "Road Routing",
                    detailText = st.roadStatusText.ifBlank { "Downloading road graph..." },
                    percent = st.roadProgressPercent,
                    indeterminate = st.roadProgressPercent <= 0
                )
            }
            if (autoChainRoads[pack.id] == true && st.isDownloaded && !st.hasRoadGraph) {
                return ActiveDownloadSummary(
                    packId = pack.id,
                    packTitle = pack.title,
                    stageLabel = "Road Routing",
                    detailText = "Starting offline roads...",
                    percent = 0,
                    indeterminate = true
                )
            }
        }
        return null
    }

    fun getState(packId: String): OfflinePackState {
        return states[packId] ?: buildIdleState(packId)
    }

    private fun buildIdleState(packId: String): OfflinePackState {
        val snap = getCachedRoadSnapshot(packId)
        val road = snap.region
        val roadDl = OfflineRoutingEngine.isDownloading(packId)
        val partialRoad = snap.hasPartial
        val roadNote = roadProgressNotes[packId].orEmpty()
        val rErr = roadErrors[packId]
        val mErr = mapErrors[packId]
        val pmPack = pmTilesDiskCache[packId]
        val pmPartial = if (pmPack == null) pmTilesPartialCache[packId] else null
        val isMapReady = pmPack != null
        val mapSize = pmPack?.sizeBytes ?: pmPartial?.downloadedBytes ?: 0L
        val pausedText = pmPartial?.let {
            if (it.totalBytes > 0L) {
                "Paused ${it.percent}% (${formatSize(it.downloadedBytes)} / ${formatSize(it.totalBytes)})"
            } else {
                "Paused ${it.percent}% (${formatSize(it.downloadedBytes)})"
            }
        }
        return OfflinePackState(
            isDownloaded = isMapReady,
            isDownloading = isPmTilesDownloading(packId),
            progressPercent = when {
                isMapReady -> 100
                pmPartial != null -> pmPartial.percent
                else -> 0
            },
            sizeBytes = mapSize,
            statusText = when {
                mErr != null && !isMapReady -> mErr
                isMapReady && road != null ->
                    "Map ${formatSize(mapSize)} + Roads ${formatSize(road.sizeBytes)} (fully offline)"
                isMapReady -> "Map Ready (${formatSize(mapSize)} • z${pmPack!!.header.minZoom}–${pmPack.header.maxZoom})"
                pausedText != null -> pausedText
                road != null -> "Roads ready (${formatSize(road.sizeBytes)}) • Map not downloaded"
                else -> "Not downloaded (online only)"
            },
            mapError = mErr,
            hasRoadGraph = road != null,
            isRoadDownloading = roadDl,
            isRoadQueued = !roadDl && road == null && (autoChainRoads[packId] == true),
            roadProgressPercent = if (roadDl) (roadProgressPercents[packId] ?: 0) else 0,
            roadSizeBytes = road?.sizeBytes ?: 0L,
            roadStatusText = when {
                roadDl -> roadNote.ifBlank { "Downloading offline roads..." }
                road != null -> "Offline routing ready (${formatSize(road.sizeBytes)})"
                rErr != null -> rErr
                !roadDl && road == null && (autoChainRoads[packId] == true) -> "Queued after map tiles finish"
                isMapReady -> "Offline routing not downloaded yet"
                else -> ""
            },
            roadError = rErr,
            hasPartialRoadData = partialRoad,
            hasPartialMapData = pmPartial != null
        )
    }

    fun formatSize(bytes: Long): String {
        val kb = (bytes / 1024L).coerceAtLeast(0L)
        return if (kb >= 1024L) {
            "${"%.1f".format(Locale.US, bytes / (1024.0 * 1024.0))} MB"
        } else {
            "$kb KB"
        }
    }

    fun refreshAll(onReady: (() -> Unit)? = null) {
        REGIONAL_PACKS.forEach { refreshRoadDiskCacheAsync(it.id, force = true) }
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val foundIds = mutableSetOf<String>()
                val validIds = REGIONAL_PACKS.map { it.id }.toSet()
                val matchedRegions = mutableListOf<Pair<String, OfflineRegion>>()
                offlineRegions?.forEach { reg ->
                    val id = decodePackId(reg.metadata) ?: return@forEach
                    if (id !in validIds) return@forEach
                    if (foundIds.add(id)) {
                        regionHandles[id] = reg
                        matchedRegions.add(id to reg)
                    } else {
                        reg.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        reg.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                            override fun onDelete() {}
                            override fun onError(error: String) {}
                        })
                    }
                }
                validIds.forEach { id ->
                    if (id !in foundIds) {
                        regionHandles.remove(id)
                        if (!isPmTilesDownloading(id)) {
                            states[id] = buildIdleState(id)
                        }
                    } else if (pmTilesDiskCache.containsKey(id) && !isPmTilesDownloading(id)) {
                        states[id] = buildIdleState(id)
                    }
                }
                regionsLoaded = true
                if (pendingDownloads.isNotEmpty()) {
                    val queued = pendingDownloads.values.toList()
                    pendingDownloads.clear()
                    queued.forEach { (pack, ratio) -> startLegacyTileDownload(pack, ratio) }
                }
                notifyListeners()
                if (matchedRegions.isEmpty()) {
                    onReady?.invoke()
                } else {
                    val remaining = AtomicInteger(matchedRegions.size)
                    matchedRegions.forEach { (id, reg) ->
                        reg.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                            override fun onStatus(status: OfflineRegionStatus?) {
                                if (status != null && !pmTilesDiskCache.containsKey(id) && !isPmTilesDownloading(id)) {
                                    updateStateFromStatus(id, status, forceEmit = true)
                                }
                                if (remaining.decrementAndGet() == 0) onReady?.invoke()
                            }
                            override fun onError(error: String?) {
                                if (remaining.decrementAndGet() == 0) onReady?.invoke()
                            }
                        })
                    }
                }
            }

            override fun onError(error: String) {
                onReady?.invoke()
            }
        })
    }

    private fun startLegacyTileDownload(pack: OfflinePackSpec, pixelRatio: Float) {
        if (!regionsLoaded) {
            pendingDownloads[pack.id] = pack to pixelRatio
            val cur = getState(pack.id)
            states[pack.id] = cur.copy(
                isDownloading = true,
                mapError = null,
                roadError = null,
                isRoadQueued = autoChainRoads[pack.id] == true,
                statusText = "Preparing offline pack..."
            )
            notifyListeners()
            refreshAll()
            return
        }
        activeTilePacks.add(pack.id)
        val existing = regionHandles[pack.id]
        if (existing != null) {
            attachObserverAndStart(pack.id, existing)
        } else {
            val bounds = LatLngBounds.Builder()
                .include(LatLng(pack.minLat, pack.minLng))
                .include(LatLng(pack.maxLat, pack.maxLng))
                .build()
            createAndStart(pack.id, bounds, pack.minZoom, pack.maxZoom, pixelRatio)
        }
    }

    /**
     * Pre-warms essential styles (Day + Night), TileJSON, sprites, and core Latin/Arabic glyph
     * ranges so downloaded PMTiles packs work 100% offline in Airplane Mode on both themes.
     */
    private fun prewarmOfflineMapAssets() {
        val stylesDir = File(appCtx.filesDir, "styles").apply { mkdirs() }
        val stylePairs = listOf(
            STYLE_STREET_URL to File(stylesDir, "vector_day.json"),
            STYLE_DARK_URL to File(stylesDir, "vector_night.json")
        )
        val dynamicSpriteUrls = LinkedHashSet<String>()
        for ((url, target) in stylePairs) {
            runCatching {
                val req = Request.Builder().url(url).get().build()
                RoutingEngine.mapTileOkHttpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val raw = resp.body?.string()?.takeIf { it.isNotBlank() } ?: return@use
                        val cleaned = MapLayerRenderer.sanitizeStyleJson(raw)
                        runCatching {
                            val spriteBase = JSONObject(cleaned).optString("sprite", "").trim()
                            if (spriteBase.startsWith("http")) {
                                dynamicSpriteUrls.add("$spriteBase.json")
                                dynamicSpriteUrls.add("$spriteBase.png")
                                dynamicSpriteUrls.add("$spriteBase@2x.json")
                                dynamicSpriteUrls.add("$spriteBase@2x.png")
                            }
                        }
                        val tmp = File(stylesDir, "${target.name}.tmp")
                        tmp.writeText(cleaned, Charsets.UTF_8)
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                    }
                }
            }
        }
        // Core TileJSON, dynamically discovered sprites, and Latin/Arabic/Punctuation glyph PBFs
        val staticUrls = dynamicSpriteUrls.toList() + listOf(
            "https://tiles.openfreemap.org/planet",
            "https://tiles.openfreemap.org/backup_planet",
            "https://tiles.openfreemap.org/sprites/ofm_f384/ofm.json",
            "https://tiles.openfreemap.org/sprites/ofm_f384/ofm.png",
            "https://tiles.openfreemap.org/sprites/ofm_f384/ofm@2x.json",
            "https://tiles.openfreemap.org/sprites/ofm_f384/ofm@2x.png",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/0-255.pbf",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/256-511.pbf",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/1536-1791.pbf",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/8192-8447.pbf",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/65024-65279.pbf",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/0-255.pbf",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/256-511.pbf",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/1536-1791.pbf",
            "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Bold/65024-65279.pbf"
        )
        for (url in staticUrls) {
            runCatching {
                val req = Request.Builder().url(url).get().build()
                RoutingEngine.mapTileOkHttpClient.newCall(req).execute().use { resp ->
                    resp.body?.bytes()
                }
            }
        }
    }

    private fun startPrebuiltMapDownload(pack: OfflinePackSpec, pixelRatio: Float) {
        if (isPmTilesDownloading(pack.id)) return
        val handle = ParallelFileDownloader.Handle()
        pmTilesDownloads[pack.id] = handle

        val cur = getState(pack.id)
        val resuming = cur.hasPartialMapData && cur.progressPercent > 0
        states[pack.id] = cur.copy(
            isDownloading = true,
            progressPercent = if (resuming) cur.progressPercent else 0,
            completedResources = 0L,
            requiredResources = 0L,
            mapError = null,
            roadError = null,
            isRoadQueued = autoChainRoads[pack.id] == true,
            statusText = if (resuming) "Resuming ${pack.title} map..." else "Downloading ${pack.title} map..."
        )
        notifyListeners()

        Thread({
            val outFile = LocalPmTilesStore.packFile(appCtx, pack.id)
            val partFile = File(LocalPmTilesStore.dir(appCtx), "${outFile.name}.part")
            var lastEmitMs = 0L
            var downloadedOk = false
            var cancelled = false
            var packInfo: LocalPmTilesStore.PackInfo? = null

            try {
                val ok = ParallelFileDownloader.downloadToFile(
                    url = prebuiltMapPackUrl(pack.id),
                    partFile = partFile,
                    label = "${pack.title} map",
                    handle = handle
                ) { downloadedBytes, _, pct, statusText, force ->
                    val now = SystemClock.elapsedRealtime()
                    if (!force && now - lastEmitMs < 350L) return@downloadToFile
                    lastEmitMs = now
                    runOnMain {
                        if (!isPmTilesDownloading(pack.id)) return@runOnMain
                        val prev = states[pack.id] ?: buildIdleState(pack.id)
                        states[pack.id] = prev.copy(
                            isDownloading = true,
                            progressPercent = pct,
                            completedResources = 0L,
                            requiredResources = 0L,
                            sizeBytes = downloadedBytes,
                            statusText = statusText,
                            mapError = null
                        )
                        notifyListeners()
                    }
                }

                if (ok) {
                    runOnMain {
                        val prev = states[pack.id] ?: buildIdleState(pack.id)
                        states[pack.id] = prev.copy(
                            isDownloading = true,
                            progressPercent = 99,
                            statusText = "Verifying ${pack.title} map..."
                        )
                        notifyListeners()
                    }
                    val header = LocalPmTilesStore.validateHeader(partFile)
                    if (header != null) {
                        if (outFile.exists()) outFile.delete()
                        if (!partFile.renameTo(outFile)) {
                            partFile.copyTo(outFile, overwrite = true)
                            partFile.delete()
                        }
                        ParallelFileDownloader.metaFileFor(partFile).delete()
                        pmTilesPartialCache.remove(pack.id)
                        packInfo = LocalPmTilesStore.registerPack(appCtx, pack.id)
                        if (packInfo != null) {
                            prewarmOfflineMapAssets()
                            downloadedOk = true
                        }
                    } else {
                        ParallelFileDownloader.clearPartialFiles(partFile)
                        pmTilesPartialCache.remove(pack.id)
                    }
                }
            } catch (_: ParallelFileDownloader.CancelledException) {
                cancelled = true
                ParallelFileDownloader.readPartialProgress(partFile)?.let {
                    pmTilesPartialCache[pack.id] = it
                }
            } catch (_: Exception) {
                if (handle.cancelled.get()) {
                    cancelled = true
                    ParallelFileDownloader.readPartialProgress(partFile)?.let {
                        pmTilesPartialCache[pack.id] = it
                    }
                }
            } finally {
                pmTilesDownloads.remove(pack.id, handle)
            }

            runOnMain {
                val info = packInfo
                if (downloadedOk && info != null) {
                    pmTilesDiskCache[pack.id] = info
                    manager.invalidateAmbientCache(null)
                    mapPackInstalledListener?.invoke(pack.id)
                    val chainRoads = autoChainRoads[pack.id] == true
                    val hasRoad = getCachedRoadSnapshot(pack.id).region != null
                    states[pack.id] = buildIdleState(pack.id)
                    notifyListeners()
                    if (chainRoads && !hasRoad && !OfflineRoutingEngine.isDownloading(pack.id)) {
                        mainHandler.postDelayed({
                            val stillNoRoad = getCachedRoadSnapshot(pack.id).region == null
                            if (autoChainRoads[pack.id] == true && !OfflineRoutingEngine.isDownloading(pack.id) && stillNoRoad) {
                                if (!OfflineRoutingEngine.hasEnoughStorage(appCtx, OfflineRoutingEngine.MIN_FREE_BYTES_ROADS)) {
                                    val freeStr = formatSize(OfflineRoutingEngine.availableStorageBytes(appCtx))
                                    val msg = "Not enough free storage for offline roads ($freeStr free)"
                                    roadErrors[pack.id] = msg
                                    setRoadChainPending(pack.id, false)
                                    syncRoadFields(pack.id, forceNotDownloading = true)
                                    if (!hasAnyActiveDownload()) OfflineDownloadService.stop(appCtx)
                                } else {
                                    startEmbeddedRoadDownload(pack.id, pack.title, pack.minLat, pack.minLng, pack.maxLat, pack.maxLng)
                                    OfflineDownloadService.startOrUpdate(appCtx)
                                }
                            } else {
                                setRoadChainPending(pack.id, false)
                                if (!hasAnyActiveDownload()) OfflineDownloadService.stop(appCtx)
                            }
                        }, 250L)
                    } else {
                        setRoadChainPending(pack.id, false)
                        if (!hasAnyActiveDownload()) OfflineDownloadService.stop(appCtx)
                    }
                } else if (cancelled) {
                    states[pack.id] = buildIdleState(pack.id)
                    notifyListeners()
                    if (!hasAnyActiveDownload()) OfflineDownloadService.stop(appCtx)
                } else {
                    // Pre-built PMTiles asset unavailable -> fall back seamlessly to legacy tile downloader
                    startLegacyTileDownload(pack, pixelRatio)
                }
            }
        }, "terrasync-map-dl-${pack.id}").apply {
            handle.workerThread = this
            isDaemon = true
            start()
        }
    }

    /**
     * Sequential two-stage download:
     * Stage 1 downloads pre-built PMTiles vector map archive (4-stream parallel HTTP Range,
     * falling back to legacy tile-by-tile download if unavailable); Stage 2 (Road Routing)
     * starts right after Stage 1 completes.
     */
    fun downloadPack(pack: OfflinePackSpec, pixelRatio: Float) {
        mapErrors.remove(pack.id)
        roadErrors.remove(pack.id)
        val cur = getState(pack.id)

        if (cur.isDownloaded && !cur.hasRoadGraph) {
            downloadRoadGraphOnly(pack)
            return
        }

        val reqBytes = if (pack.id == PACK_KURDISTAN_ID) MIN_FREE_BYTES_KURDISTAN else MIN_FREE_BYTES_SOUTH
        if (!OfflineRoutingEngine.hasEnoughStorage(appCtx, reqBytes)) {
            val freeStr = formatSize(OfflineRoutingEngine.availableStorageBytes(appCtx))
            val msg = "Not enough free storage (need ${formatSize(reqBytes)}, $freeStr free)"
            mapErrors[pack.id] = msg
            states[pack.id] = cur.copy(
                isDownloading = false,
                isRoadQueued = false,
                mapError = msg,
                statusText = msg
            )
            notifyListeners()
            return
        }

        // Guarantee mutual exclusion with road downloader
        if (OfflineRoutingEngine.isDownloading(pack.id)) {
            OfflineRoutingEngine.cancelDownload(pack.id)
        }
        val needRoads = getCachedRoadSnapshot(pack.id).region == null && !cur.hasRoadGraph
        setRoadChainPending(pack.id, needRoads)

        startPrebuiltMapDownload(pack, pixelRatio)
        OfflineDownloadService.startOrUpdate(appCtx)
    }

    fun downloadRoadGraphOnly(pack: OfflinePackSpec) {
        if (OfflineRoutingEngine.isDownloading(pack.id)) return
        if (!OfflineRoutingEngine.hasEnoughStorage(appCtx, OfflineRoutingEngine.MIN_FREE_BYTES_ROADS)) {
            val freeStr = formatSize(OfflineRoutingEngine.availableStorageBytes(appCtx))
            val msg = "Not enough free storage for offline roads (need ${formatSize(OfflineRoutingEngine.MIN_FREE_BYTES_ROADS)}, $freeStr free)"
            roadErrors[pack.id] = msg
            syncRoadFields(pack.id, forceNotDownloading = true)
            return
        }
        // Never download tiles and roads simultaneously
        activeTilePacks.remove(pack.id)
        pmTilesDownloads.remove(pack.id)?.cancelAll()
        setRoadChainPending(pack.id, false)
        pendingDownloads.remove(pack.id)
        regionHandles[pack.id]?.let { reg ->
            reg.setDownloadState(OfflineRegion.STATE_INACTIVE)
        }
        roadErrors.remove(pack.id)
        startEmbeddedRoadDownload(pack.id, pack.title, pack.minLat, pack.minLng, pack.maxLat, pack.maxLng)
        OfflineDownloadService.startOrUpdate(appCtx)
    }

    private fun startEmbeddedRoadDownload(
        packId: String,
        label: String,
        sLat: Double,
        wLng: Double,
        nLat: Double,
        eLng: Double
    ) {
        roadErrors.remove(packId)
        roadProgressPercents[packId] = 0
        roadProgressNotes[packId] = "Starting offline roads..."
        syncRoadFields(packId, isStarting = true)
        setRoadChainPending(packId, false)
        OfflineRoutingEngine.downloadPackRoadGraph(
            appCtx,
            packId,
            label,
            sLat,
            wLng,
            nLat,
            eLng,
            onProgress = { msg, pct ->
                runOnMain {
                    roadProgressNotes[packId] = msg
                    roadProgressPercents[packId] = pct
                    syncRoadFields(packId)
                }
            },
            onDone = { reg, err ->
                runOnMain {
                    if (err == "Cancelled" && OfflineRoutingEngine.isDownloading(packId)) {
                        return@runOnMain
                    }
                    roadProgressNotes.remove(packId)
                    roadProgressPercents.remove(packId)
                    if (reg != null) {
                        roadErrors.remove(packId)
                        roadDiskCache[packId] = RoadDiskSnapshot(reg, false)
                    } else if (err != null && err != "Cancelled") {
                        roadErrors[packId] = err
                        refreshRoadDiskCacheAsync(packId, force = true)
                    } else {
                        refreshRoadDiskCacheAsync(packId, force = true)
                    }
                    syncRoadFields(packId, forceNotDownloading = !OfflineRoutingEngine.isDownloading(packId))
                    if (!hasAnyActiveDownload()) {
                        OfflineDownloadService.stop(appCtx)
                    }
                }
            }
        )
    }

    private fun syncRoadFields(
        packId: String,
        isStarting: Boolean = false,
        forceNotDownloading: Boolean = false
    ) {
        val cur = states[packId] ?: buildIdleState(packId)
        val pmPack = pmTilesDiskCache[packId]
        val isMapReady = cur.isDownloaded || pmPack != null
        val mapSize = if (pmPack != null) pmPack.sizeBytes else cur.sizeBytes
        val snap = getCachedRoadSnapshot(packId)
        val road = snap.region
        val roadDl = if (forceNotDownloading) false else (isStarting || OfflineRoutingEngine.isDownloading(packId))
        val partialRoad = snap.hasPartial
        val note = roadProgressNotes[packId].orEmpty()
        val rErr = roadErrors[packId]
        val queued = !roadDl && road == null && (autoChainRoads[packId] == true)
        val pct = if (roadDl) (roadProgressPercents[packId] ?: cur.roadProgressPercent) else 0
        val roadText = when {
            roadDl -> note.ifBlank { "Downloading offline roads..." }
            road != null -> "Offline routing ready (${formatSize(road.sizeBytes)})"
            rErr != null -> rErr
            queued -> "Queued after map tiles finish"
            else -> if (isMapReady) "Offline routing not downloaded yet" else ""
        }
        val nextStatus = when {
            isMapReady && road != null ->
                "Map ${formatSize(mapSize)} + Roads ${formatSize(road.sizeBytes)} (fully offline)"
            isMapReady && !cur.isDownloading ->
                "Map Ready (${formatSize(mapSize)})"
            else -> cur.statusText
        }
        states[packId] = cur.copy(
            isDownloaded = isMapReady,
            progressPercent = if (isMapReady) 100 else cur.progressPercent,
            sizeBytes = mapSize,
            statusText = nextStatus,
            hasRoadGraph = road != null,
            isRoadDownloading = roadDl,
            isRoadQueued = queued,
            roadProgressPercent = pct,
            roadSizeBytes = road?.sizeBytes ?: 0L,
            roadStatusText = roadText,
            roadError = rErr,
            hasPartialRoadData = partialRoad
        )
        notifyListeners()
    }

    private fun createAndStart(
        packId: String,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double,
        pixelRatio: Float
    ) {
        val cur = getState(packId)
        states[packId] = cur.copy(
            isDownloading = true,
            mapError = null,
            isRoadQueued = autoChainRoads[packId] == true,
            statusText = "Starting map download..."
        )
        notifyListeners()

        val def = OfflineTilePyramidRegionDefinition(
            STYLE_STREET_URL,
            bounds,
            minZoom,
            maxZoom,
            pixelRatio.coerceIn(1f, 2f),
            false
        )
        val meta = JSONObject().put("id", packId).toString().toByteArray(Charsets.UTF_8)
        manager.createOfflineRegion(def, meta, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                regionHandles[packId] = offlineRegion
                attachObserverAndStart(packId, offlineRegion)
            }

            override fun onError(error: String) {
                activeTilePacks.remove(packId)
                setRoadChainPending(packId, false)
                mapErrors[packId] = "Download failed: $error"
                states[packId] = getState(packId).copy(
                    isDownloading = false,
                    isRoadQueued = false,
                    mapError = "Download failed: $error",
                    statusText = "Download failed: $error"
                )
                notifyListeners()
                if (!hasAnyActiveDownload()) {
                    OfflineDownloadService.stop(appCtx)
                }
            }
        })
    }

    private fun attachObserverAndStart(packId: String, region: OfflineRegion) {
        activeTilePacks.add(packId)
        lastProgressEmitMs.remove(packId)
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (status.isComplete) {
                    activeTilePacks.remove(packId)
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    region.setObserver(null)
                    val chainRoads = autoChainRoads[packId] == true
                    val hasRoad = getCachedRoadSnapshot(packId).region != null
                    if (chainRoads && !hasRoad && !OfflineRoutingEngine.isDownloading(packId)) {
                        updateStateFromStatus(packId, status, forceEmit = true)
                        val pack = REGIONAL_PACKS.firstOrNull { it.id == packId }
                        if (pack != null) {
                            mainHandler.postDelayed({
                                val stillNoRoad = getCachedRoadSnapshot(pack.id).region == null
                                if (autoChainRoads[pack.id] == true && !OfflineRoutingEngine.isDownloading(pack.id) && stillNoRoad) {
                                    if (!OfflineRoutingEngine.hasEnoughStorage(appCtx, OfflineRoutingEngine.MIN_FREE_BYTES_ROADS)) {
                                        val freeStr = formatSize(OfflineRoutingEngine.availableStorageBytes(appCtx))
                                        val msg = "Not enough free storage for offline roads ($freeStr free)"
                                        roadErrors[pack.id] = msg
                                        setRoadChainPending(pack.id, false)
                                        syncRoadFields(pack.id, forceNotDownloading = true)
                                        if (!hasAnyActiveDownload()) OfflineDownloadService.stop(appCtx)
                                    } else {
                                        startEmbeddedRoadDownload(pack.id, pack.title, pack.minLat, pack.minLng, pack.maxLat, pack.maxLng)
                                        OfflineDownloadService.startOrUpdate(appCtx)
                                    }
                                } else {
                                    setRoadChainPending(pack.id, false)
                                    if (!hasAnyActiveDownload()) {
                                        OfflineDownloadService.stop(appCtx)
                                    }
                                }
                            }, 400L)
                        } else {
                            setRoadChainPending(packId, false)
                            if (!hasAnyActiveDownload()) {
                                OfflineDownloadService.stop(appCtx)
                            }
                        }
                    } else {
                        setRoadChainPending(packId, false)
                        updateStateFromStatus(packId, status, forceEmit = true)
                        if (!hasAnyActiveDownload()) {
                            OfflineDownloadService.stop(appCtx)
                        }
                    }
                } else {
                    updateStateFromStatus(packId, status, forceEmit = false)
                }
            }

            override fun onError(error: OfflineRegionError) {
                mapErrors[packId] = "Tile notice: ${error.message}"
                states[packId] = getState(packId).copy(
                    mapError = "Tile notice: ${error.message}",
                    statusText = "Tile notice: ${error.message}"
                )
                notifyListeners()
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                activeTilePacks.remove(packId)
                setRoadChainPending(packId, false)
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                mapErrors[packId] = "Tile limit reached ($limit)"
                states[packId] = getState(packId).copy(
                    isDownloading = false,
                    isRoadQueued = false,
                    mapError = "Tile limit reached ($limit)",
                    statusText = "Tile limit reached ($limit)"
                )
                notifyListeners()
                if (!hasAnyActiveDownload()) {
                    OfflineDownloadService.stop(appCtx)
                }
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    fun pauseDownload(packId: String) {
        activeTilePacks.remove(packId)
        pmTilesDownloads.remove(packId)?.cancelAll()
        pendingDownloads.remove(packId)
        setRoadChainPending(packId, false)
        OfflineRoutingEngine.cancelDownload(packId)
        roadProgressNotes.remove(packId)
        roadProgressPercents.remove(packId)
        val reg = regionHandles[packId]
        if (reg != null) {
            reg.setDownloadState(OfflineRegion.STATE_INACTIVE)
            reg.setObserver(null)
        }
        val cur = getState(packId)
        val partFile = File(LocalPmTilesStore.dir(appCtx), "${LocalPmTilesStore.packFileName(packId)}.part")
        val partial = ParallelFileDownloader.readPartialProgress(partFile)
        if (partial != null) pmTilesPartialCache[packId] = partial
        val hasPartialPm = !cur.isDownloaded && (partial != null || (cur.completedResources == 0L && cur.sizeBytes > 0L && cur.progressPercent > 0))
        val pausedPct = partial?.percent ?: cur.progressPercent
        val pausedBytes = partial?.downloadedBytes ?: cur.sizeBytes
        val pausedTotal = partial?.totalBytes ?: -1L
        states[packId] = cur.copy(
            isDownloading = false,
            isRoadDownloading = false,
            isRoadQueued = false,
            progressPercent = if (hasPartialPm) pausedPct else cur.progressPercent,
            sizeBytes = if (hasPartialPm) pausedBytes else cur.sizeBytes,
            hasPartialMapData = hasPartialPm,
            statusText = when {
                cur.isDownloaded -> cur.statusText
                cur.completedResources > 0L -> "Paused ${cur.progressPercent}% (${cur.completedResources}/${cur.requiredResources}) • ${formatSize(cur.sizeBytes)}"
                hasPartialPm && pausedTotal > 0L -> "Paused $pausedPct% (${formatSize(pausedBytes)} / ${formatSize(pausedTotal)})"
                hasPartialPm -> "Paused $pausedPct% (${formatSize(pausedBytes)})"
                else -> "Not downloaded (online only)"
            }
        )
        syncRoadFields(packId, forceNotDownloading = true)
        if (!hasAnyActiveDownload()) {
            OfflineDownloadService.stop(appCtx)
        }
    }

    fun pauseAllDownloads() {
        REGIONAL_PACKS.forEach { pack ->
            val st = getState(pack.id)
            if (st.isDownloading || isPmTilesDownloading(pack.id) || st.isRoadDownloading || OfflineRoutingEngine.isDownloading(pack.id) || pendingDownloads.containsKey(pack.id)) {
                pauseDownload(pack.id)
            }
        }
    }

    fun deletePack(packId: String, onDone: (String) -> Unit) {
        activeTilePacks.remove(packId)
        pmTilesDownloads.remove(packId)?.cancelAll()
        pmTilesDiskCache.remove(packId)
        pmTilesPartialCache.remove(packId)
        pendingDownloads.remove(packId)
        setRoadChainPending(packId, false)
        mapErrors.remove(packId)
        roadErrors.remove(packId)
        roadProgressNotes.remove(packId)
        roadProgressPercents.remove(packId)
        roadDiskCache[packId] = RoadDiskSnapshot(null, false)
        RoutingEngine.batchIoExecutor.execute {
            val removedMap = LocalPmTilesStore.removePack(appCtx, packId)
            val removedRoads = OfflineRoutingEngine.deletePackRegion(appCtx, packId)
            runOnMain {
                val reg = regionHandles[packId]
                if (reg == null) {
                    states[packId] = buildIdleState(packId)
                    notifyListeners()
                    if (!hasAnyActiveDownload()) OfflineDownloadService.stop(appCtx)
                    onDone(
                        when {
                            removedMap && removedRoads -> "Offline map & routing deleted"
                            removedMap -> "Offline map deleted"
                            removedRoads -> "Offline roads removed"
                            else -> "Already removed"
                        }
                    )
                    return@runOnMain
                }
                reg.setDownloadState(OfflineRegion.STATE_INACTIVE)
                reg.setObserver(null)
                reg.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                    override fun onDelete() {
                        regionHandles.remove(packId)
                        states[packId] = buildIdleState(packId)
                        notifyListeners()
                        if (!hasAnyActiveDownload()) OfflineDownloadService.stop(appCtx)
                        onDone("Offline map & routing deleted")
                    }

                    override fun onError(error: String) {
                        states[packId] = buildIdleState(packId)
                        notifyListeners()
                        if (!hasAnyActiveDownload()) OfflineDownloadService.stop(appCtx)
                        onDone("Delete error: $error")
                    }
                })
            }
        }
    }

    fun estimateAmbientCacheBytes(): Long {
        val dbBytes = listOf("mbgl-offline.db", "mbgl-offline.db-wal", "mbgl-offline.db-shm")
            .sumOf { name -> File(appCtx.filesDir, name).takeIf { it.exists() }?.length() ?: 0L }
        val offlinePackBytes = states.entries.sumOf { (id, st) ->
            if (regionHandles.containsKey(id) && !pmTilesDiskCache.containsKey(id)) st.sizeBytes else 0L
        }
        val ambientDbBytes = (dbBytes - offlinePackBytes).coerceAtLeast(0L)
        fun dirBytes(dir: File?): Long {
            if (dir == null || !dir.exists()) return 0L
            var sum = 0L
            dir.walkTopDown().maxDepth(4).forEach { f ->
                if (f.isFile) sum += f.length()
            }
            return sum
        }
        return ambientDbBytes + dirBytes(appCtx.cacheDir)
    }

    fun clearAmbientCache(onDone: (String) -> Unit) {
        manager.clearAmbientCache(object : OfflineManager.FileSourceCallback {
            override fun onSuccess() = purgeTempDirsThen("Temporary map cache cleared", onDone)
            override fun onError(message: String) = purgeTempDirsThen("Cache error: $message", onDone)
        })
    }

    private fun purgeTempDirsThen(msg: String, onDone: (String) -> Unit) {
        AppExecutors.batchIoExecutor.execute {
            runCatching { TempCacheCleaner.purge(appCtx.cacheDir) }
            AppExecutors.postMain(Runnable { onDone(msg) })
        }
    }

    private fun updateStateFromStatus(packId: String, status: OfflineRegionStatus, forceEmit: Boolean = false) {
        if (status.isComplete) {
            activeTilePacks.remove(packId)
        }
        val now = SystemClock.elapsedRealtime()
        val prevEmit = lastProgressEmitMs[packId] ?: 0L
        val active = !status.isComplete &&
            (status.downloadState == OfflineRegion.STATE_ACTIVE || activeTilePacks.contains(packId))
        if (!forceEmit && active && (now - prevEmit) < 350L) {
            return
        }
        lastProgressEmitMs[packId] = now
        if (active || status.isComplete) {
            mapErrors.remove(packId)
        }

        val req = status.requiredResourceCount
        val done = status.completedResourceCount
        val pct = if (req > 0) ((done * 100L) / req).toInt().coerceIn(0, 100) else 0
        val sizeStr = formatSize(status.completedResourceSize)
        val snap = getCachedRoadSnapshot(packId)
        val road = snap.region
        val roadDl = OfflineRoutingEngine.isDownloading(packId)
        val partialRoad = snap.hasPartial
        val note = roadProgressNotes[packId].orEmpty()
        val rErr = roadErrors[packId]
        val mErr = mapErrors[packId]
        val queued = !roadDl && road == null && (autoChainRoads[packId] == true)

        val text = when {
            mErr != null && !status.isComplete -> mErr
            status.isComplete && road != null ->
                "Map $sizeStr + Roads ${formatSize(road.sizeBytes)} (fully offline)"
            status.isComplete ->
                "Map Ready ($sizeStr)"
            active ->
                "Downloading $pct% ($done/$req) • $sizeStr"
            done > 0 ->
                "Paused $pct% ($done/$req) • $sizeStr"
            else ->
                "Not downloaded (online only)"
        }
        val roadText = when {
            roadDl -> note.ifBlank { "Downloading offline roads..." }
            road != null -> "Offline routing ready (${formatSize(road.sizeBytes)})"
            rErr != null -> rErr
            queued -> "Queued after map tiles finish"
            else -> if (status.isComplete) "Offline routing not downloaded yet" else ""
        }
        states[packId] = OfflinePackState(
            isDownloaded = status.isComplete,
            isDownloading = active,
            progressPercent = pct,
            completedResources = done,
            requiredResources = req,
            sizeBytes = status.completedResourceSize,
            statusText = text,
            mapError = mErr,
            hasRoadGraph = road != null,
            isRoadDownloading = roadDl,
            isRoadQueued = queued,
            roadProgressPercent = if (roadDl) (roadProgressPercents[packId] ?: 0) else 0,
            roadSizeBytes = road?.sizeBytes ?: 0L,
            roadStatusText = roadText,
            roadError = rErr,
            hasPartialRoadData = partialRoad
        )
        notifyListeners()
    }

    private fun decodePackId(metadata: ByteArray): String? {
        return runCatching {
            JSONObject(String(metadata, Charsets.UTF_8)).optString("id").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

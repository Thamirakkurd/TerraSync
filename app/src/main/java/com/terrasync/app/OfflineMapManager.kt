package com.terrasync.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
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
    val hasRoadGraph: Boolean = false,
    val isRoadDownloading: Boolean = false,
    val roadSizeBytes: Long = 0L,
    val roadStatusText: String = ""
)

class OfflineMapManager(private val context: Context) {

    companion object {
        const val STYLE_STREET_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val STYLE_DARK_URL = "https://tiles.openfreemap.org/styles/fiord"
        const val PACK_KURDISTAN_ID = "kurdistan_north"
        const val PACK_SOUTHERN_IRAQ_ID = "iraq_south"

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
    }

    private val appCtx: Context = context.applicationContext
    private val manager: OfflineManager = OfflineManager.getInstance(appCtx).apply {
        setOfflineMapboxTileCountLimit(80000L)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val regionHandles = ConcurrentHashMap<String, OfflineRegion>()
    private val states = ConcurrentHashMap<String, OfflinePackState>()
    private val roadProgressNotes = ConcurrentHashMap<String, String>()
    private val pendingDownloads = ConcurrentHashMap<String, Pair<OfflinePackSpec, Float>>()
    @Volatile private var regionsLoaded = false
    @Volatile private var uiListener: (() -> Unit)? = null

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    fun setStateListener(listener: (() -> Unit)?) {
        uiListener = listener
    }

    fun getState(packId: String): OfflinePackState {
        return states[packId] ?: buildIdleState(packId)
    }

    private fun buildIdleState(packId: String): OfflinePackState {
        val road = OfflineRoutingEngine.getPackRegion(appCtx, packId)
        val roadDl = OfflineRoutingEngine.isDownloading(packId)
        val roadNote = roadProgressNotes[packId].orEmpty()
        return OfflinePackState(
            statusText = if (road != null) "Roads ready (${formatSize(road.sizeBytes)}) • Map not downloaded" else "Not downloaded (online only)",
            hasRoadGraph = road != null,
            isRoadDownloading = roadDl,
            roadSizeBytes = road?.sizeBytes ?: 0L,
            roadStatusText = when {
                roadDl -> roadNote.ifBlank { "Downloading offline roads..." }
                road != null -> "Offline routing ready (${formatSize(road.sizeBytes)})"
                else -> ""
            }
        )
    }

    private fun formatSize(bytes: Long): String {
        val kb = (bytes / 1024L).coerceAtLeast(1L)
        return if (kb >= 1024L) "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB" else "$kb KB"
    }

    fun refreshAll(onReady: (() -> Unit)? = null) {
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
                        // Remove duplicate orphaned region for the same packId
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
                        states[id] = buildIdleState(id)
                    }
                }
                regionsLoaded = true
                if (pendingDownloads.isNotEmpty()) {
                    val queued = pendingDownloads.values.toList()
                    pendingDownloads.clear()
                    queued.forEach { (pack, ratio) -> startTileDownload(pack, ratio) }
                }
                runOnMain { uiListener?.invoke() }
                if (matchedRegions.isEmpty()) {
                    onReady?.invoke()
                } else {
                    val remaining = AtomicInteger(matchedRegions.size)
                    matchedRegions.forEach { (id, reg) ->
                        reg.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                            override fun onStatus(status: OfflineRegionStatus?) {
                                if (status != null) updateStateFromStatus(id, status)
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

    private fun startTileDownload(pack: OfflinePackSpec, pixelRatio: Float) {
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

    fun downloadPack(pack: OfflinePackSpec, pixelRatio: Float) {
        if (!regionsLoaded) {
            pendingDownloads[pack.id] = pack to pixelRatio
            states[pack.id] = getState(pack.id).copy(
                isDownloading = true,
                statusText = "Preparing offline pack..."
            )
            runOnMain { uiListener?.invoke() }
            refreshAll()
        } else {
            startTileDownload(pack, pixelRatio)
        }
        if (OfflineRoutingEngine.getPackRegion(appCtx, pack.id) == null && !OfflineRoutingEngine.isDownloading(pack.id)) {
            startEmbeddedRoadDownload(pack.id, pack.title, pack.minLat, pack.minLng, pack.maxLat, pack.maxLng)
        }
    }

    fun downloadRoadGraphOnly(pack: OfflinePackSpec) {
        if (OfflineRoutingEngine.isDownloading(pack.id)) return
        startEmbeddedRoadDownload(pack.id, pack.title, pack.minLat, pack.minLng, pack.maxLat, pack.maxLng)
    }

    private fun startEmbeddedRoadDownload(
        packId: String,
        label: String,
        sLat: Double,
        wLng: Double,
        nLat: Double,
        eLng: Double
    ) {
        roadProgressNotes[packId] = "Starting offline roads..."
        syncRoadFields(packId)
        OfflineRoutingEngine.downloadPackRoadGraph(
            appCtx,
            packId,
            label,
            sLat,
            wLng,
            nLat,
            eLng,
            onProgress = { msg ->
                runOnMain {
                    roadProgressNotes[packId] = msg
                    syncRoadFields(packId)
                }
            },
            onDone = { reg, err ->
                runOnMain {
                    if (reg != null) {
                        roadProgressNotes.remove(packId)
                    } else if (err != null && err != "Cancelled") {
                        roadProgressNotes[packId] = "Roads failed: $err"
                    } else {
                        roadProgressNotes.remove(packId)
                    }
                    syncRoadFields(packId)
                }
            }
        )
    }

    private fun syncRoadFields(packId: String) {
        val cur = states[packId] ?: buildIdleState(packId)
        val road = OfflineRoutingEngine.getPackRegion(appCtx, packId)
        val roadDl = OfflineRoutingEngine.isDownloading(packId)
        val note = roadProgressNotes[packId].orEmpty()
        val roadText = when {
            roadDl -> note.ifBlank { "Downloading offline roads..." }
            road != null -> "Offline routing ready (${formatSize(road.sizeBytes)})"
            note.isNotBlank() -> note
            else -> if (cur.isDownloaded) "Offline routing not downloaded yet" else ""
        }
        states[packId] = cur.copy(
            hasRoadGraph = road != null,
            isRoadDownloading = roadDl,
            roadSizeBytes = road?.sizeBytes ?: 0L,
            roadStatusText = roadText
        )
        runOnMain { uiListener?.invoke() }
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
            statusText = "Starting map download..."
        )
        uiListener?.invoke()

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
                states[packId] = getState(packId).copy(isDownloading = false, statusText = "Download failed: $error")
                uiListener?.invoke()
            }
        })
    }

    private fun attachObserverAndStart(packId: String, region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                updateStateFromStatus(packId, status)
                if (status.isComplete) {
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                }
            }

            override fun onError(error: OfflineRegionError) {
                states[packId] = getState(packId).copy(
                    statusText = "Network notice: ${error.message}"
                )
                uiListener?.invoke()
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                states[packId] = getState(packId).copy(isDownloading = false, statusText = "Tile limit reached ($limit)")
                uiListener?.invoke()
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    fun pauseDownload(packId: String) {
        OfflineRoutingEngine.cancelDownload(packId)
        roadProgressNotes.remove(packId)
        val reg = regionHandles[packId]
        if (reg != null) reg.setDownloadState(OfflineRegion.STATE_INACTIVE)
        val cur = getState(packId)
        states[packId] = cur.copy(
            isDownloading = false,
            isRoadDownloading = false,
            statusText = if (cur.isDownloaded) cur.statusText else "Paused (${cur.progressPercent}%)"
        )
        syncRoadFields(packId)
    }

    fun deletePack(packId: String, onDone: (String) -> Unit) {
        val removedRoads = OfflineRoutingEngine.deletePackRegion(appCtx, packId)
        roadProgressNotes.remove(packId)
        val reg = regionHandles[packId]
        if (reg == null) {
            states[packId] = buildIdleState(packId)
            uiListener?.invoke()
            onDone(if (removedRoads) "Offline roads removed" else "Already removed")
            return
        }
        reg.setDownloadState(OfflineRegion.STATE_INACTIVE)
        reg.setObserver(null)
        reg.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                regionHandles.remove(packId)
                states[packId] = buildIdleState(packId)
                uiListener?.invoke()
                onDone("Offline map & routing deleted")
            }

            override fun onError(error: String) {
                onDone("Delete error: $error")
            }
        })
    }

    fun clearAmbientCache(onDone: (String) -> Unit) {
        manager.clearAmbientCache(object : OfflineManager.FileSourceCallback {
            override fun onSuccess() = onDone("Temporary map cache cleared")
            override fun onError(message: String) = onDone("Cache error: $message")
        })
    }

    private fun updateStateFromStatus(packId: String, status: OfflineRegionStatus) {
        val req = status.requiredResourceCount
        val done = status.completedResourceCount
        val pct = if (req > 0) ((done * 100L) / req).toInt().coerceIn(0, 100) else 0
        val mb = status.completedResourceSize / (1024.0 * 1024.0)
        val active = status.downloadState == OfflineRegion.STATE_ACTIVE && !status.isComplete
        val road = OfflineRoutingEngine.getPackRegion(appCtx, packId)
        val roadDl = OfflineRoutingEngine.isDownloading(packId)
        val note = roadProgressNotes[packId].orEmpty()

        val text = when {
            status.isComplete && road != null ->
                "Map ${"%.1f".format(mb)} MB + Roads ${formatSize(road.sizeBytes)} (fully offline)"
            status.isComplete && roadDl ->
                "Map ${"%.1f".format(mb)} MB • ${note.ifBlank { "Downloading roads..." }}"
            status.isComplete ->
                "Map ${"%.1f".format(mb)} MB • Routing not downloaded yet"
            active ->
                "Downloading map $pct% ($done/$req) • ${"%.1f".format(mb)} MB"
            done > 0 ->
                "Paused $pct% • ${"%.1f".format(mb)} MB"
            else ->
                "Not downloaded (online only)"
        }
        val roadText = when {
            roadDl -> note.ifBlank { "Downloading offline roads..." }
            road != null -> "Offline routing ready (${formatSize(road.sizeBytes)})"
            note.isNotBlank() -> note
            else -> if (status.isComplete) "Tap 'Add Offline Roads' to enable offline routing" else ""
        }
        states[packId] = OfflinePackState(
            isDownloaded = status.isComplete,
            isDownloading = active,
            progressPercent = pct,
            completedResources = done,
            requiredResources = req,
            sizeBytes = status.completedResourceSize,
            statusText = text,
            hasRoadGraph = road != null,
            isRoadDownloading = roadDl,
            roadSizeBytes = road?.sizeBytes ?: 0L,
            roadStatusText = roadText
        )
        uiListener?.invoke()
    }

    private fun decodePackId(metadata: ByteArray): String? {
        return runCatching {
            JSONObject(String(metadata, Charsets.UTF_8)).optString("id").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

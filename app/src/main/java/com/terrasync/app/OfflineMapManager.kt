package com.terrasync.app

import android.content.Context
import org.json.JSONObject
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
    val statusText: String = "Not downloaded (online only)"
)

class OfflineMapManager(private val context: Context) {

    companion object {
        const val STYLE_STREET_URL = "https://tiles.openfreemap.org/styles/liberty"
        const val CUSTOM_VIEW_ID = "custom_area_hd"

        val REGIONAL_PACKS = listOf(
            OfflinePackSpec(
                id = "kurdistan_north",
                title = "Northern Iraq (Kurdistan Region)",
                subtitle = "Duhok, Zakho, Akre, Erbil, Sulaymaniyah, Halabja, Kirkuk, Nineveh",
                minLat = 34.45,
                maxLat = 37.38,
                minLng = 42.35,
                maxLng = 46.35,
                minZoom = 6.0,
                maxZoom = 12.0
            ),
            OfflinePackSpec(
                id = "iraq_south",
                title = "Central & Southern Iraq",
                subtitle = "Baghdad, Anbar, Diyala, Karbala, Najaf, Wasit, Dhi Qar, Basra",
                minLat = 29.05,
                maxLat = 34.45,
                minLng = 38.75,
                maxLng = 48.65,
                minZoom = 6.0,
                maxZoom = 11.0
            )
        )
    }

    private val manager: OfflineManager = OfflineManager.getInstance(context.applicationContext).apply {
        setOfflineMapboxTileCountLimit(50000L)
    }

    private val regionHandles = mutableMapOf<String, OfflineRegion>()
    private val states = mutableMapOf<String, OfflinePackState>()
    private var uiListener: (() -> Unit)? = null

    fun setStateListener(listener: (() -> Unit)?) {
        uiListener = listener
    }

    fun getState(packId: String): OfflinePackState {
        return states[packId] ?: OfflinePackState()
    }

    fun refreshAll(onReady: (() -> Unit)? = null) {
        manager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val foundIds = mutableSetOf<String>()
                offlineRegions?.forEach { reg ->
                    val id = decodePackId(reg.metadata) ?: return@forEach
                    foundIds.add(id)
                    regionHandles[id] = reg
                    reg.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            if (status != null) {
                                updateStateFromStatus(id, status)
                            }
                            onReady?.invoke()
                        }

                        override fun onError(error: String?) {
                            onReady?.invoke()
                        }
                    })
                }
                val known = REGIONAL_PACKS.map { it.id } + CUSTOM_VIEW_ID
                known.forEach { id ->
                    if (id !in foundIds) {
                        regionHandles.remove(id)
                        states[id] = OfflinePackState()
                    }
                }
                uiListener?.invoke()
                onReady?.invoke()
            }

            override fun onError(error: String) {
                onReady?.invoke()
            }
        })
    }

    fun downloadPack(pack: OfflinePackSpec, pixelRatio: Float) {
        val existing = regionHandles[pack.id]
        if (existing != null) {
            attachObserverAndStart(pack.id, existing)
            return
        }
        val bounds = LatLngBounds.Builder()
            .include(LatLng(pack.minLat, pack.minLng))
            .include(LatLng(pack.maxLat, pack.maxLng))
            .build()
        createAndStart(pack.id, bounds, pack.minZoom, pack.maxZoom, pixelRatio)
    }

    fun downloadCustomBounds(bounds: LatLngBounds, pixelRatio: Float) {
        val existing = regionHandles[CUSTOM_VIEW_ID]
        if (existing != null) {
            existing.setDownloadState(OfflineRegion.STATE_INACTIVE)
            existing.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() {
                    regionHandles.remove(CUSTOM_VIEW_ID)
                    createAndStart(CUSTOM_VIEW_ID, bounds, 10.0, 14.0, pixelRatio)
                }

                override fun onError(error: String) {
                    states[CUSTOM_VIEW_ID] = OfflinePackState(statusText = "Error: $error")
                    uiListener?.invoke()
                }
            })
        } else {
            createAndStart(CUSTOM_VIEW_ID, bounds, 10.0, 14.0, pixelRatio)
        }
    }

    private fun createAndStart(
        packId: String,
        bounds: LatLngBounds,
        minZoom: Double,
        maxZoom: Double,
        pixelRatio: Float
    ) {
        states[packId] = OfflinePackState(
            isDownloading = true,
            statusText = "Starting download..."
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
                states[packId] = OfflinePackState(statusText = "Download failed: $error")
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
                states[packId] = (states[packId] ?: OfflinePackState()).copy(
                    statusText = "Network notice: ${error.message}"
                )
                uiListener?.invoke()
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                states[packId] = OfflinePackState(statusText = "Tile limit reached ($limit)")
                uiListener?.invoke()
            }
        })
        region.setDownloadState(OfflineRegion.STATE_ACTIVE)
    }

    fun pauseDownload(packId: String) {
        val reg = regionHandles[packId] ?: return
        reg.setDownloadState(OfflineRegion.STATE_INACTIVE)
        val cur = states[packId] ?: OfflinePackState()
        states[packId] = cur.copy(
            isDownloading = false,
            statusText = "Paused (${cur.progressPercent}%)"
        )
        uiListener?.invoke()
    }

    fun deletePack(packId: String, onDone: (String) -> Unit) {
        val reg = regionHandles[packId]
        if (reg == null) {
            states[packId] = OfflinePackState()
            uiListener?.invoke()
            onDone("Already removed")
            return
        }
        reg.setDownloadState(OfflineRegion.STATE_INACTIVE)
        reg.setObserver(null)
        reg.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() {
                regionHandles.remove(packId)
                states[packId] = OfflinePackState()
                uiListener?.invoke()
                onDone("Offline pack deleted")
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
        val text = when {
            status.isComplete -> "Downloaded • ${"%.1f".format(mb)} MB (ready offline)"
            active -> "Downloading $pct% ($done/$req) • ${"%.1f".format(mb)} MB"
            done > 0 -> "Paused $pct% • ${"%.1f".format(mb)} MB"
            else -> "Not downloaded (online only)"
        }
        states[packId] = OfflinePackState(
            isDownloaded = status.isComplete,
            isDownloading = active,
            progressPercent = pct,
            completedResources = done,
            requiredResources = req,
            sizeBytes = status.completedResourceSize,
            statusText = text
        )
        uiListener?.invoke()
    }

    private fun decodePackId(metadata: ByteArray): String? {
        return runCatching {
            JSONObject(String(metadata, Charsets.UTF_8)).optString("id").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}

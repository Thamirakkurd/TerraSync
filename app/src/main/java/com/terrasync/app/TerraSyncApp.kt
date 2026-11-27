package com.terrasync.app

import android.app.Application
import android.os.Build
import org.maplibre.android.MapLibre
import org.maplibre.android.module.http.HttpRequestUtil

/**
 * Process-wide bootstrap so MainActivity, OfflineDownloadService, and TerraSyncCarAppService
 * always share an initialized MapLibre OkHttp tile interceptor and local PMTiles store.
 */
class TerraSyncApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val ver = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V").ifBlank { "3.13.5" }
        RoutingEngine.userAgent = "TerraSync/$ver (https://github.com/Thamirakkurd/TerraSync)"
        RoutingEngine.appContext = applicationContext

        // Robolectric runs on the host JVM without Android native MapLibre .so binaries.
        if ("robolectric".equals(Build.FINGERPRINT, ignoreCase = true)) return

        runCatching {
            MapLibre.getInstance(this)
            HttpRequestUtil.setOkHttpClient(RoutingEngine.mapTileOkHttpClient)
        }

        AppExecutors.batchIoExecutor.execute {
            LocalPmTilesStore.ensureLoaded(applicationContext)
            OfflineRoutingEngine.cleanupOrphanedTempFiles(applicationContext)
        }
    }
}

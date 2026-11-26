package com.terrasync.app

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-wide bounded thread pools and main-thread dispatcher shared across
 * RoutingEngine, TerraSyncRepository, controllers, and UI modules.
 */
object AppExecutors {

    private val uiCounter = AtomicInteger(1)
    private val ioCounter = AtomicInteger(1)

    val uiQueryExecutor: ExecutorService = Executors.newFixedThreadPool(3) { r ->
        Thread(r, "terrasync-ui-query-${uiCounter.getAndIncrement()}").apply { isDaemon = true }
    }

    val batchIoExecutor: ExecutorService = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "terrasync-batch-io-${ioCounter.getAndIncrement()}").apply { isDaemon = true }
    }

    val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    fun postMain(r: Runnable) {
        if (isMainThread()) {
            r.run()
        } else {
            mainHandler.post(r)
        }
    }
}

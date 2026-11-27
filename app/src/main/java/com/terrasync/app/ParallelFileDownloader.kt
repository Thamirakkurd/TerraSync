package com.terrasync.app

import android.os.SystemClock
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * High-throughput 4-stream parallel HTTP Range downloader shared by offline road packs (.tsr)
 * and offline vector map packs (.pmtiles). Falls back automatically to single-stream download
 * when Range requests are unavailable.
 */
object ParallelFileDownloader {

    class CancelledException : Exception("Cancelled")

    class Handle {
        val cancelled = AtomicBoolean(false)
        @Volatile var activeCall: Call? = null
        val activeCalls = CopyOnWriteArrayList<Call>()
        @Volatile var workerThread: Thread? = null

        fun registerCall(call: Call) {
            activeCall = call
            activeCalls.add(call)
        }

        fun unregisterCall(call: Call) {
            activeCalls.remove(call)
            if (activeCall === call) activeCall = null
        }

        fun cancelAll() {
            cancelled.set(true)
            val single = activeCall
            activeCall = null
            single?.cancel()
            for (c in activeCalls) {
                runCatching { c.cancel() }
            }
            activeCalls.clear()
            workerThread?.interrupt()
        }
    }

    val client: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 16
            maxRequestsPerHost = 8
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(8, 60, TimeUnit.SECONDS))
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", RoutingEngine.userAgent)
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    fun formatSize(bytes: Long): String {
        val kb = (bytes / 1024L).coerceAtLeast(1L)
        return if (kb >= 1024L) {
            "${"%.1f".format(Locale.US, bytes / (1024.0 * 1024.0))} MB"
        } else {
            "$kb KB"
        }
    }

    /**
     * Streams [url] into [partFile] using 4 parallel HTTP Range connections when supported
     * (and size >= 2 MB), otherwise single-stream GET.
     *
     * @return true if the file downloaded completely; false on HTTP 404/unavailable/network error.
     * @throws CancelledException if cancelled via [handle].
     */
    fun downloadToFile(
        url: String,
        partFile: File,
        label: String,
        handle: Handle,
        onProgress: (downloadedBytes: Long, totalBytes: Long, pct: Int, statusText: String, force: Boolean) -> Unit
    ): Boolean {
        partFile.parentFile?.mkdirs()
        partFile.delete()
        val startMs = SystemClock.elapsedRealtime()

        fun checkCancelled() {
            if (handle.cancelled.get()) throw CancelledException()
        }

        fun formatSpeed(bytes: Long): String {
            val elapsedSec = ((SystemClock.elapsedRealtime() - startMs).coerceAtLeast(250L)) / 1000.0
            val bps = bytes / elapsedSec
            return if (bps >= 1024.0 * 1024.0) {
                "${"%.1f".format(Locale.US, bps / (1024.0 * 1024.0))} MB/s"
            } else {
                "${(bps / 1024.0).toInt().coerceAtLeast(1)} KB/s"
            }
        }

        fun downloadParallelRanges(finalUrl: String, totalBytes: Long): Boolean {
            val numParts = 4
            val chunkSize = totalBytes / numParts
            val downloaded = AtomicLong(0L)
            val firstFailure = AtomicReference<Throwable?>(null)
            val latch = CountDownLatch(numParts)

            RandomAccessFile(partFile, "rw").use { raf ->
                raf.setLength(totalBytes)
            }

            val workers = ArrayList<Thread>(numParts)
            for (partIdx in 0 until numParts) {
                val rangeStart = partIdx * chunkSize
                val rangeEnd = if (partIdx == numParts - 1) totalBytes - 1L else ((partIdx + 1) * chunkSize) - 1L
                val worker = Thread({
                    var partCall: Call? = null
                    try {
                        checkCancelled()
                        val req = Request.Builder()
                            .url(finalUrl)
                            .header("Range", "bytes=$rangeStart-$rangeEnd")
                            .get()
                            .build()
                        val activeCall = client.newCall(req)
                        partCall = activeCall
                        handle.registerCall(activeCall)
                        checkCancelled()
                        activeCall.execute().use { resp ->
                            val body = resp.body
                            if (resp.code != 206 || body == null) {
                                throw IOException("Range HTTP ${resp.code}")
                            }
                            val buf = ByteArray(64 * 1024)
                            var writtenPart = 0L
                            val expectedPart = rangeEnd - rangeStart + 1L
                            BufferedInputStream(body.byteStream(), 64 * 1024).use { input ->
                                RandomAccessFile(partFile, "rw").use { raf ->
                                    raf.seek(rangeStart)
                                    while (true) {
                                        checkCancelled()
                                        if (firstFailure.get() != null) return@use
                                        val n = input.read(buf)
                                        if (n <= 0) break
                                        raf.write(buf, 0, n)
                                        writtenPart += n
                                        val totalDone = downloaded.addAndGet(n.toLong())
                                        val pct = ((totalDone * 98L) / totalBytes).toInt().coerceIn(0, 98)
                                        onProgress(
                                            totalDone,
                                            totalBytes,
                                            pct,
                                            "Downloading $label (${formatSize(totalDone)} / ${formatSize(totalBytes)} • ${formatSpeed(totalDone)})",
                                            false
                                        )
                                    }
                                }
                            }
                            if (firstFailure.get() == null && writtenPart != expectedPart) {
                                throw IOException("Truncated range ($writtenPart/$expectedPart)")
                            }
                        }
                    } catch (tErr: Throwable) {
                        firstFailure.compareAndSet(null, tErr)
                        if (tErr is CancelledException || tErr is CancellationException) {
                            handle.cancelAll()
                        }
                    } finally {
                        partCall?.let { handle.unregisterCall(it) }
                        latch.countDown()
                    }
                }, "terrasync-dl-part-$partIdx").apply {
                    isDaemon = true
                    start()
                }
                workers.add(worker)
            }

            while (!latch.await(200L, TimeUnit.MILLISECONDS)) {
                if (handle.cancelled.get()) {
                    handle.cancelAll()
                    workers.forEach { it.interrupt() }
                    throw CancelledException()
                }
            }
            checkCancelled()
            val err = firstFailure.get()
            if (err != null) {
                if (err is CancelledException || err is CancellationException || handle.cancelled.get()) {
                    throw CancelledException()
                }
                return false
            }
            return partFile.length() == totalBytes
        }

        fun downloadSingleStream(targetUrl: String): Boolean {
            val req = Request.Builder().url(targetUrl).get().build()
            val activeCall = client.newCall(req)
            handle.registerCall(activeCall)
            try {
                checkCancelled()
                activeCall.execute().use { resp ->
                    val body = resp.body
                    if (resp.code != 200 || body == null) return false
                    val totalBytes = body.contentLength()
                    var bytesWritten = 0L
                    val buf = ByteArray(64 * 1024)
                    BufferedInputStream(body.byteStream(), 64 * 1024).use { input ->
                        FileOutputStream(partFile).use { fos ->
                            BufferedOutputStream(fos, 64 * 1024).use { output ->
                                while (true) {
                                    checkCancelled()
                                    val n = input.read(buf)
                                    if (n <= 0) break
                                    output.write(buf, 0, n)
                                    bytesWritten += n
                                    if (totalBytes > 0L) {
                                        val pct = ((bytesWritten * 98L) / totalBytes).toInt().coerceIn(0, 98)
                                        onProgress(
                                            bytesWritten,
                                            totalBytes,
                                            pct,
                                            "Downloading $label (${formatSize(bytesWritten)} / ${formatSize(totalBytes)} • ${formatSpeed(bytesWritten)})",
                                            false
                                        )
                                    } else {
                                        val pct = (bytesWritten / (256L * 1024L)).toInt().coerceIn(1, 95)
                                        onProgress(
                                            bytesWritten,
                                            -1L,
                                            pct,
                                            "Downloading $label (${formatSize(bytesWritten)} • ${formatSpeed(bytesWritten)})",
                                            false
                                        )
                                    }
                                }
                                output.flush()
                                runCatching { fos.fd.sync() }
                            }
                        }
                    }
                    return bytesWritten > 64L
                }
            } finally {
                handle.unregisterCall(activeCall)
            }
        }

        try {
            onProgress(0L, -1L, 0, "Downloading $label...", true)
            var finalUrl = url
            var contentLength = -1L
            var supportsRanges = false
            var headNotFound = false

            val headCall = client.newCall(Request.Builder().url(url).head().build())
            handle.registerCall(headCall)
            try {
                checkCancelled()
                headCall.execute().use { headResp ->
                    if (headResp.code == 404 || headResp.code == 410) {
                        headNotFound = true
                    } else if (headResp.isSuccessful) {
                        finalUrl = headResp.request.url.toString()
                        contentLength = headResp.header("Content-Length")?.toLongOrNull() ?: -1L
                        supportsRanges = headResp.header("Accept-Ranges")
                            ?.equals("bytes", ignoreCase = true) == true
                    }
                }
            } finally {
                handle.unregisterCall(headCall)
            }

            if (headNotFound) return false
            checkCancelled()

            val ok = if (supportsRanges && contentLength >= 2L * 1024L * 1024L) {
                downloadParallelRanges(finalUrl, contentLength) || run {
                    partFile.delete()
                    downloadSingleStream(finalUrl)
                }
            } else {
                downloadSingleStream(finalUrl)
            }
            if (!ok) partFile.delete()
            return ok
        } catch (ce: CancelledException) {
            partFile.delete()
            throw ce
        } catch (ce: CancellationException) {
            partFile.delete()
            throw CancelledException()
        } catch (_: Exception) {
            partFile.delete()
            if (handle.cancelled.get()) throw CancelledException()
            return false
        } finally {
            handle.activeCalls.clear()
            handle.activeCall = null
        }
    }
}

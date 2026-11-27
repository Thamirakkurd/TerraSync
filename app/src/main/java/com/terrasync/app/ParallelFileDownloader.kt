package com.terrasync.app

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
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
 * Resumable 4-stream parallel HTTP Range downloader shared by offline road packs (.tsr)
 * and offline vector map packs (.pmtiles). Persists chunk offsets in a sidecar `.meta`
 * file so tapping Pause keeps all downloaded bytes and Resume continues where it left off.
 */
object ParallelFileDownloader {

    class CancelledException : Exception("Cancelled")

    data class PartialProgress(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percent: Int
    )

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

    fun metaFileFor(partFile: File): File = File(partFile.parentFile, "${partFile.name}.meta")

    fun clearPartialFiles(partFile: File) {
        runCatching { partFile.delete() }
        runCatching { metaFileFor(partFile).delete() }
    }

    fun readPartialProgress(partFile: File): PartialProgress? {
        if (!partFile.exists() || !partFile.isFile) return null
        val metaFile = metaFileFor(partFile)
        if (!metaFile.exists() || !metaFile.isFile) return null
        return runCatching {
            val obj = JSONObject(metaFile.readText(Charsets.UTF_8))
            val total = obj.optLong("totalBytes", -1L)
            val mode = obj.optString("mode", "parallel")
            val done: Long = if (mode == "parallel") {
                if (total <= 0L || partFile.length() != total) return null
                val arr = obj.optJSONArray("partDone") ?: return null
                var sum = 0L
                for (i in 0 until arr.length()) sum += arr.optLong(i, 0L).coerceAtLeast(0L)
                sum.coerceAtMost(total)
            } else {
                partFile.length().coerceAtLeast(0L)
            }
            if (done <= 0L) return null
            val pct = if (total > 0L) ((done * 98L) / total).toInt().coerceIn(1, 98) else 1
            PartialProgress(done, total, pct)
        }.getOrNull()
    }

    private fun writeParallelMeta(
        metaFile: File,
        totalBytes: Long,
        numParts: Int,
        partDone: LongArray,
        lock: Any
    ) {
        synchronized(lock) {
            runCatching {
                val arr = JSONArray()
                for (v in partDone) arr.put(v)
                val obj = JSONObject()
                    .put("mode", "parallel")
                    .put("totalBytes", totalBytes)
                    .put("numParts", numParts)
                    .put("partDone", arr)
                val tmp = File(metaFile.parentFile, "${metaFile.name}.tmp")
                tmp.writeText(obj.toString(), Charsets.UTF_8)
                if (!tmp.renameTo(metaFile)) {
                    tmp.copyTo(metaFile, overwrite = true)
                    tmp.delete()
                }
            }
        }
    }

    private fun writeSingleMeta(metaFile: File, totalBytes: Long, downloaded: Long) {
        runCatching {
            val obj = JSONObject()
                .put("mode", "single")
                .put("totalBytes", totalBytes)
                .put("downloaded", downloaded)
            val tmp = File(metaFile.parentFile, "${metaFile.name}.tmp")
            tmp.writeText(obj.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(metaFile)) {
                tmp.copyTo(metaFile, overwrite = true)
                tmp.delete()
            }
        }
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
     * Streams [url] into [partFile] using 4 resumable parallel HTTP Range connections when supported
     * (and size >= 2 MB), otherwise resumable single-stream GET.
     *
     * On pause/cancellation, preserves [partFile] + `.meta` on disk and throws [CancelledException].
     * On full completion, removes `.meta` and returns `true`.
     */
    fun downloadToFile(
        url: String,
        partFile: File,
        label: String,
        handle: Handle,
        onProgress: (downloadedBytes: Long, totalBytes: Long, pct: Int, statusText: String, force: Boolean) -> Unit
    ): Boolean {
        partFile.parentFile?.mkdirs()
        val metaFile = metaFileFor(partFile)
        val startMs = SystemClock.elapsedRealtime()

        fun checkCancelled() {
            if (handle.cancelled.get()) throw CancelledException()
        }

        fun formatSpeed(sessionBytes: Long): String {
            val elapsedSec = ((SystemClock.elapsedRealtime() - startMs).coerceAtLeast(250L)) / 1000.0
            val bps = sessionBytes.coerceAtLeast(0L) / elapsedSec
            return if (bps >= 1024.0 * 1024.0) {
                "${"%.1f".format(Locale.US, bps / (1024.0 * 1024.0))} MB/s"
            } else {
                "${(bps / 1024.0).toInt().coerceAtLeast(1)} KB/s"
            }
        }

        fun loadExistingParallelPartDone(totalBytes: Long, numParts: Int, chunkSize: Long): LongArray? {
            if (!partFile.exists() || partFile.length() != totalBytes || !metaFile.exists()) return null
            return runCatching {
                val obj = JSONObject(metaFile.readText(Charsets.UTF_8))
                if (obj.optString("mode") != "parallel") return null
                if (obj.optLong("totalBytes", -1L) != totalBytes) return null
                if (obj.optInt("numParts", -1) != numParts) return null
                val arr = obj.optJSONArray("partDone") ?: return null
                if (arr.length() != numParts) return null
                LongArray(numParts) { idx ->
                    val rangeStart = idx * chunkSize
                    val rangeEnd = if (idx == numParts - 1) totalBytes - 1L else ((idx + 1) * chunkSize) - 1L
                    val maxLen = rangeEnd - rangeStart + 1L
                    arr.optLong(idx, 0L).coerceIn(0L, maxLen)
                }
            }.getOrNull()
        }

        fun downloadParallelRanges(finalUrl: String, totalBytes: Long): Boolean {
            val numParts = 4
            val chunkSize = totalBytes / numParts
            val existingDone = loadExistingParallelPartDone(totalBytes, numParts, chunkSize)
            val partDone = existingDone ?: LongArray(numParts)
            val metaLock = Any()

            if (existingDone == null) {
                clearPartialFiles(partFile)
                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.setLength(totalBytes)
                }
                writeParallelMeta(metaFile, totalBytes, numParts, partDone, metaLock)
            }

            val initialBytes = partDone.sum()
            val downloaded = AtomicLong(initialBytes)
            val lastMetaFlushMs = AtomicLong(SystemClock.elapsedRealtime())
            val firstFailure = AtomicReference<Throwable?>(null)
            val latch = CountDownLatch(numParts)

            if (initialBytes > 0L) {
                val initPct = ((initialBytes * 98L) / totalBytes).toInt().coerceIn(0, 98)
                onProgress(
                    initialBytes,
                    totalBytes,
                    initPct,
                    "Resuming $label (${formatSize(initialBytes)} / ${formatSize(totalBytes)})...",
                    true
                )
            }

            val workers = ArrayList<Thread>(numParts)
            for (partIdx in 0 until numParts) {
                val rangeStart = partIdx * chunkSize
                val rangeEnd = if (partIdx == numParts - 1) totalBytes - 1L else ((partIdx + 1) * chunkSize) - 1L
                val expectedPart = rangeEnd - rangeStart + 1L
                val alreadyDone = partDone[partIdx]

                if (alreadyDone >= expectedPart) {
                    latch.countDown()
                    continue
                }

                val resumeStart = rangeStart + alreadyDone
                val worker = Thread({
                    var partCall: Call? = null
                    var writtenPart = alreadyDone
                    try {
                        checkCancelled()
                        val req = Request.Builder()
                            .url(finalUrl)
                            .header("Range", "bytes=$resumeStart-$rangeEnd")
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
                            BufferedInputStream(body.byteStream(), 64 * 1024).use { input ->
                                RandomAccessFile(partFile, "rw").use { raf ->
                                    raf.seek(resumeStart)
                                    while (true) {
                                        checkCancelled()
                                        if (firstFailure.get() != null) return@use
                                        val n = input.read(buf)
                                        if (n <= 0) break
                                        raf.write(buf, 0, n)
                                        writtenPart += n
                                        synchronized(metaLock) { partDone[partIdx] = writtenPart }
                                        val totalDone = downloaded.addAndGet(n.toLong())
                                        val now = SystemClock.elapsedRealtime()
                                        val prevFlush = lastMetaFlushMs.get()
                                        if (now - prevFlush >= 500L && lastMetaFlushMs.compareAndSet(prevFlush, now)) {
                                            writeParallelMeta(metaFile, totalBytes, numParts, partDone, metaLock)
                                        }
                                        val pct = ((totalDone * 98L) / totalBytes).toInt().coerceIn(0, 98)
                                        onProgress(
                                            totalDone,
                                            totalBytes,
                                            pct,
                                            "Downloading $label (${formatSize(totalDone)} / ${formatSize(totalBytes)} • ${formatSpeed(totalDone - initialBytes)})",
                                            false
                                        )
                                    }
                                }
                            }
                        }
                        if (firstFailure.get() == null && writtenPart != expectedPart) {
                            throw IOException("Truncated range ($writtenPart/$expectedPart)")
                        }
                    } catch (tErr: Throwable) {
                        synchronized(metaLock) { partDone[partIdx] = writtenPart }
                        firstFailure.compareAndSet(null, tErr)
                        if (tErr is CancelledException || tErr is CancellationException) {
                            handle.cancelAll()
                        }
                    } finally {
                        synchronized(metaLock) { partDone[partIdx] = writtenPart }
                        writeParallelMeta(metaFile, totalBytes, numParts, partDone, metaLock)
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
                    latch.await(1500L, TimeUnit.MILLISECONDS)
                    writeParallelMeta(metaFile, totalBytes, numParts, partDone, metaLock)
                    throw CancelledException()
                }
            }
            writeParallelMeta(metaFile, totalBytes, numParts, partDone, metaLock)
            checkCancelled()
            val err = firstFailure.get()
            if (err != null) {
                if (err is CancelledException || err is CancellationException || handle.cancelled.get()) {
                    throw CancelledException()
                }
                return false
            }
            val complete = partFile.length() == totalBytes && partDone.sum() == totalBytes
            if (complete) runCatching { metaFile.delete() }
            return complete
        }

        fun downloadSingleStream(targetUrl: String, knownTotalBytes: Long, supportsRanges: Boolean): Boolean {
            val canResume = supportsRanges && partFile.exists() && partFile.length() > 0L &&
                metaFile.exists() && runCatching {
                    val obj = JSONObject(metaFile.readText(Charsets.UTF_8))
                    obj.optString("mode") == "single" &&
                        (knownTotalBytes <= 0L || obj.optLong("totalBytes", -1L) == knownTotalBytes)
                }.getOrDefault(false)

            val existingBytes = if (canResume) partFile.length() else 0L
            if (!canResume) clearPartialFiles(partFile)

            val reqBuilder = Request.Builder().url(targetUrl).get()
            if (existingBytes > 0L) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
            }
            val activeCall = client.newCall(reqBuilder.build())
            handle.registerCall(activeCall)
            var bytesWritten = existingBytes
            var totalBytes = knownTotalBytes
            var lastFlushMs = SystemClock.elapsedRealtime()
            try {
                checkCancelled()
                activeCall.execute().use { resp ->
                    val body = resp.body
                    val isPartialResp = existingBytes > 0L && resp.code == 206
                    if ((resp.code != 200 && !isPartialResp) || body == null) return false
                    val appendMode = isPartialResp
                    if (!appendMode) {
                        bytesWritten = 0L
                        totalBytes = body.contentLength().takeIf { it > 0L } ?: knownTotalBytes
                    } else if (totalBytes <= 0L && body.contentLength() > 0L) {
                        totalBytes = existingBytes + body.contentLength()
                    }
                    val sessionStartBytes = bytesWritten
                    val buf = ByteArray(64 * 1024)
                    BufferedInputStream(body.byteStream(), 64 * 1024).use { input ->
                        FileOutputStream(partFile, appendMode).use { fos ->
                            BufferedOutputStream(fos, 64 * 1024).use { output ->
                                while (true) {
                                    checkCancelled()
                                    val n = input.read(buf)
                                    if (n <= 0) break
                                    output.write(buf, 0, n)
                                    bytesWritten += n
                                    val now = SystemClock.elapsedRealtime()
                                    if (now - lastFlushMs >= 500L) {
                                        lastFlushMs = now
                                        output.flush()
                                        writeSingleMeta(metaFile, totalBytes, bytesWritten)
                                    }
                                    if (totalBytes > 0L) {
                                        val pct = ((bytesWritten * 98L) / totalBytes).toInt().coerceIn(0, 98)
                                        onProgress(
                                            bytesWritten,
                                            totalBytes,
                                            pct,
                                            "Downloading $label (${formatSize(bytesWritten)} / ${formatSize(totalBytes)} • ${formatSpeed(bytesWritten - sessionStartBytes)})",
                                            false
                                        )
                                    } else {
                                        val pct = (bytesWritten / (256L * 1024L)).toInt().coerceIn(1, 95)
                                        onProgress(
                                            bytesWritten,
                                            -1L,
                                            pct,
                                            "Downloading $label (${formatSize(bytesWritten)} • ${formatSpeed(bytesWritten - sessionStartBytes)})",
                                            false
                                        )
                                    }
                                }
                                output.flush()
                                runCatching { fos.fd.sync() }
                            }
                        }
                    }
                    val ok = bytesWritten > 64L && (totalBytes <= 0L || bytesWritten >= totalBytes)
                    if (ok) runCatching { metaFile.delete() }
                    return ok
                }
            } finally {
                if (bytesWritten > 0L && (totalBytes <= 0L || bytesWritten < totalBytes)) {
                    writeSingleMeta(metaFile, totalBytes, bytesWritten)
                }
                handle.unregisterCall(activeCall)
            }
        }

        try {
            val prevPartial = readPartialProgress(partFile)
            if (prevPartial != null) {
                onProgress(
                    prevPartial.downloadedBytes,
                    prevPartial.totalBytes,
                    prevPartial.percent,
                    "Resuming $label (${formatSize(prevPartial.downloadedBytes)} / ${formatSize(prevPartial.totalBytes)})...",
                    true
                )
            } else {
                onProgress(0L, -1L, 0, "Downloading $label...", true)
            }

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
                    if (handle.cancelled.get()) throw CancelledException()
                    downloadSingleStream(finalUrl, contentLength, supportsRanges = true)
                }
            } else {
                downloadSingleStream(finalUrl, contentLength, supportsRanges)
            }
            return ok
        } catch (ce: CancelledException) {
            throw ce
        } catch (_: CancellationException) {
            throw CancelledException()
        } catch (_: Exception) {
            if (handle.cancelled.get()) throw CancelledException()
            return false
        } finally {
            handle.activeCalls.clear()
            handle.activeCall = null
        }
    }
}

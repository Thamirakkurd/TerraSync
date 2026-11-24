package com.terrasync.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class UpdateInfo(
    val versionName: String,
    val tagName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long,
    val htmlUrl: String,
    val isPreRelease: Boolean = false
)

object AppUpdater {
    private const val LATEST_RELEASE_API = "https://api.github.com/repos/Thamirakkurd/TerraSync/releases/latest"
    private const val ALL_RELEASES_API = "https://api.github.com/repos/Thamirakkurd/TerraSync/releases?per_page=10"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Suppress("DEPRECATION")
    fun getInstalledVersionName(ctx: Context): String {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager.getPackageInfo(ctx.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            }
            (info.versionName ?: "2.7.0").trim().removePrefix("v").removePrefix("V")
        }.getOrDefault("2.7.0")
    }

    fun isNewerVersion(remoteRaw: String, currentRaw: String): Boolean {
        return compareVersions(remoteRaw, currentRaw) > 0
    }

    private fun compareVersions(aRaw: String, bRaw: String): Int {
        val aClean = aRaw.trim().removePrefix("v").removePrefix("V")
        val bClean = bRaw.trim().removePrefix("v").removePrefix("V")
        val aBase = parseBaseParts(aClean)
        val bBase = parseBaseParts(bClean)
        val len = maxOf(aBase.size, bBase.size)
        for (i in 0 until len) {
            val av = aBase.getOrElse(i) { 0 }
            val bv = bBase.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        val aPre = aClean.substringAfter("-", "")
        val bPre = bClean.substringAfter("-", "")
        if (aPre.isBlank() && bPre.isBlank()) return 0
        if (aPre.isBlank()) return 1   // stable > prerelease
        if (bPre.isBlank()) return -1  // prerelease < stable
        return preReleaseRank(aPre).compareTo(preReleaseRank(bPre))
    }

    private fun parseBaseParts(clean: String): List<Int> {
        return clean.substringBefore("-").split(".").map { seg ->
            seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    private fun preReleaseRank(pre: String): Int {
        val lower = pre.lowercase()
        val tier = when {
            lower.startsWith("rc") -> 600
            lower.startsWith("beta") -> 300
            lower.startsWith("alpha") -> 100
            else -> 10
        }
        val num = Regex("\\d+").find(lower)?.value?.toIntOrNull()?.coerceIn(0, 99) ?: 0
        return tier + num
    }

    fun cleanupOldApks(ctx: Context) {
        runCatching {
            val dir = File(ctx.cacheDir, "updates")
            if (dir.exists()) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && (f.name.endsWith(".apk") || f.name.endsWith(".tmp"))) {
                        f.delete()
                    }
                }
            }
        }
    }

    private fun parseReleaseJson(json: JSONObject): UpdateInfo? {
        if (json.optBoolean("draft", false)) return null
        val tag = json.optString("tag_name", "").trim()
        val ver = tag.removePrefix("v").removePrefix("V")
        if (ver.isBlank()) return null

        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl = ""
        var apkName = "TerraSync-$tag.apk"
        var apkSize = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name", "")
            if (name.lowercase().endsWith(".apk")) {
                apkUrl = asset.optString("browser_download_url", "")
                apkName = name
                apkSize = asset.optLong("size", 0L)
                break
            }
        }
        if (apkUrl.isBlank()) return null
        val isPre = json.optBoolean("prerelease", false) || ver.contains("-")
        val notes = json.optString("body", "").trim()
        val htmlUrl = json.optString("html_url", "https://github.com/Thamirakkurd/TerraSync/releases")
        return UpdateInfo(ver, tag, notes, apkUrl, apkName, apkSize, htmlUrl, isPre)
    }

    fun checkForUpdate(
        ctx: Context,
        includeBeta: Boolean = false,
        onResult: (update: UpdateInfo?, error: String?) -> Unit
    ) {
        val currentVersion = getInstalledVersionName(ctx)
        Thread {
            try {
                val url = if (includeBeta) ALL_RELEASES_API else LATEST_RELEASE_API
                val req = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "TerraSync/$currentVersion (https://github.com/Thamirakkurd/TerraSync)")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val msg = if (resp.code == 403 || resp.code == 429) {
                            "GitHub rate limit reached. Try again later."
                        } else {
                            "Update check failed (HTTP ${resp.code})"
                        }
                        onResult(null, msg)
                        return@Thread
                    }
                    val body = resp.body?.string().orEmpty()
                    val best: UpdateInfo? = if (includeBeta) {
                        val arr = JSONArray(body)
                        var highest: UpdateInfo? = null
                        for (i in 0 until arr.length()) {
                            val cand = parseReleaseJson(arr.getJSONObject(i)) ?: continue
                            if (highest == null || compareVersions(cand.versionName, highest.versionName) > 0) {
                                highest = cand
                            }
                        }
                        highest
                    } else {
                        val parsed = parseReleaseJson(JSONObject(body))
                        if (parsed != null && !parsed.isPreRelease) parsed else null
                    }

                    if (best != null && isNewerVersion(best.versionName, currentVersion)) {
                        onResult(best, null)
                    } else {
                        onResult(null, null)
                    }
                }
            } catch (e: Exception) {
                onResult(null, e.message ?: "Unable to reach GitHub")
            }
        }.start()
    }

    fun downloadApk(
        ctx: Context,
        info: UpdateInfo,
        onProgress: (percent: Int, downloadedMb: Double, totalMb: Double) -> Unit,
        onDone: (file: File?, error: String?) -> Unit
    ): () -> Unit {
        val cancelled = AtomicBoolean(false)
        val currentVersion = getInstalledVersionName(ctx)
        val req = Request.Builder()
            .url(info.apkUrl)
            .header("User-Agent", "TerraSync/$currentVersion (https://github.com/Thamirakkurd/TerraSync)")
            .build()
        val call = client.newCall(req)

        Thread {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val safeName = info.apkName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "TerraSync-update.apk" }
            val tmpFile = File(dir, "$safeName.tmp")
            val finalFile = File(dir, safeName)
            try {
                if (tmpFile.exists()) tmpFile.delete()
                if (finalFile.exists()) finalFile.delete()

                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        onDone(null, "Download failed (HTTP ${resp.code})")
                        return@Thread
                    }
                    val respBody = resp.body ?: run {
                        onDone(null, "Empty download response")
                        return@Thread
                    }
                    val totalBytes = respBody.contentLength().takeIf { it > 0L } ?: info.sizeBytes
                    val totalMb = if (totalBytes > 0L) totalBytes / (1024.0 * 1024.0) else 0.0

                    respBody.byteStream().use { input ->
                        FileOutputStream(tmpFile).use { output ->
                            val buf = ByteArray(16 * 1024)
                            var downloaded = 0L
                            var lastEmit = 0L
                            while (true) {
                                if (cancelled.get()) {
                                    tmpFile.delete()
                                    onDone(null, "Download cancelled")
                                    return@Thread
                                }
                                val read = input.read(buf)
                                if (read == -1) break
                                output.write(buf, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastEmit >= 120L || (totalBytes > 0L && downloaded >= totalBytes)) {
                                    lastEmit = now
                                    val pct = if (totalBytes > 0L) {
                                        ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                    } else -1
                                    onProgress(pct, downloaded / (1024.0 * 1024.0), totalMb)
                                }
                            }
                            output.flush()
                        }
                    }
                }

                if (cancelled.get()) {
                    tmpFile.delete()
                    onDone(null, "Download cancelled")
                    return@Thread
                }
                if (!tmpFile.renameTo(finalFile)) {
                    tmpFile.copyTo(finalFile, overwrite = true)
                    tmpFile.delete()
                }
                onDone(finalFile, null)
            } catch (e: Exception) {
                tmpFile.delete()
                if (cancelled.get()) {
                    onDone(null, "Download cancelled")
                } else {
                    onDone(null, e.message ?: "Download failed")
                }
            }
        }.start()

        return {
            cancelled.set(true)
            call.cancel()
        }
    }

    fun canInstallApks(ctx: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()
    }

    fun openInstallPermissionSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${ctx.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }
    }

    fun launchApkInstaller(ctx: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}

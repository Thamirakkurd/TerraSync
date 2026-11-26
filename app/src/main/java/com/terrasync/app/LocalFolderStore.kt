package com.terrasync.app

import android.content.Context
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.Executors

data class LocalStoreSnapshot(
    val layers: List<LayerItem>,
    val pins: List<PinItem>,
    val tracks: List<TrackItem>
)

object LocalFolderStore {

    private const val FILE_NAME = "local_folders_v1.json"
    private val lock = Any()
    private val diskLock = Any()
    private val ioExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "terrasync-local-store").apply { isDaemon = true }
    }
    @Volatile private var cachedSnapshot: LocalStoreSnapshot? = null
    private val pendingWrite = java.util.concurrent.atomic.AtomicReference<LocalStoreSnapshot?>(null)
    private val writeScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    fun isLocalLayerId(layerId: String): Boolean = layerId.startsWith("local_")

    fun newLocalLayerId(): String = "local_${UUID.randomUUID()}"

    private fun storeFile(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun getCachedOrNull(): LocalStoreSnapshot? = cachedSnapshot

    internal fun clearCacheForTest() = synchronized(lock) {
        cachedSnapshot = null
    }

    internal fun flushPendingWritesForTest(ctx: Context) {
        val appCtx = ctx.applicationContext
        ioExecutor.submit {
            val remaining = pendingWrite.getAndSet(null)
            if (remaining != null) {
                persistSnapshotToDisk(appCtx, remaining)
            }
        }.get()
    }

    private fun JsonReader.nextStringOrEmpty(): String =
        if (peek() == JsonToken.NULL) { skipValue(); "" } else nextString()

    private fun JsonReader.nextLongOrZero(): Long =
        if (peek() == JsonToken.NULL) { skipValue(); 0L } else nextLong()

    private fun JsonReader.nextDoubleOrZero(): Double =
        if (peek() == JsonToken.NULL) { skipValue(); 0.0 } else nextDouble()

    private fun JsonReader.nextBooleanOrFalse(): Boolean =
        if (peek() == JsonToken.NULL) { skipValue(); false } else nextBoolean()

    internal fun readSnapshotFromStream(input: InputStream): LocalStoreSnapshot {
        val layers = mutableListOf<LayerItem>()
        val pins = mutableListOf<PinItem>()
        val tracks = mutableListOf<TrackItem>()
        JsonReader(InputStreamReader(BufferedInputStream(input, 32 * 1024), Charsets.UTF_8)).use { jr ->
            jr.beginObject()
            while (jr.hasNext()) {
                when (jr.nextName()) {
                    "layers" -> {
                        if (jr.peek() == JsonToken.BEGIN_ARRAY) {
                            jr.beginArray()
                            while (jr.hasNext()) {
                                var id = ""
                                var name = "Local Folder"
                                var createdBy = ""
                                var creatorDeviceId = ""
                                var updatedAt = 0L
                                jr.beginObject()
                                while (jr.hasNext()) {
                                    when (jr.nextName()) {
                                        "id" -> id = jr.nextStringOrEmpty()
                                        "name" -> name = jr.nextStringOrEmpty().ifBlank { "Local Folder" }
                                        "createdBy" -> createdBy = jr.nextStringOrEmpty()
                                        "creatorDeviceId" -> creatorDeviceId = jr.nextStringOrEmpty()
                                        "updatedAt" -> updatedAt = jr.nextLongOrZero()
                                        else -> jr.skipValue()
                                    }
                                }
                                jr.endObject()
                                layers += LayerItem(
                                    id = id,
                                    name = name,
                                    shareCode = "",
                                    createdBy = createdBy,
                                    creatorDeviceId = creatorDeviceId,
                                    updatedAt = updatedAt
                                )
                            }
                            jr.endArray()
                        } else jr.skipValue()
                    }
                    "pins" -> {
                        if (jr.peek() == JsonToken.BEGIN_ARRAY) {
                            jr.beginArray()
                            while (jr.hasNext()) {
                                var id = ""
                                var layerId = ""
                                var name = "Pin"
                                var description = ""
                                var lat = 0.0
                                var lng = 0.0
                                var status = "pending"
                                var issueReason = ""
                                var updatedAt = 0L
                                var isEdited = false
                                var editedBy = ""
                                var editedAt = 0L
                                var editHistory = ""
                                var folderPath = ""
                                jr.beginObject()
                                while (jr.hasNext()) {
                                    when (jr.nextName()) {
                                        "id" -> id = jr.nextStringOrEmpty()
                                        "layerId" -> layerId = jr.nextStringOrEmpty()
                                        "name" -> name = jr.nextStringOrEmpty().ifBlank { "Pin" }
                                        "description" -> description = jr.nextStringOrEmpty()
                                        "lat" -> lat = jr.nextDoubleOrZero()
                                        "lng" -> lng = jr.nextDoubleOrZero()
                                        "status" -> status = PinStatus.fromRaw(jr.nextStringOrEmpty()).key
                                        "issueReason" -> issueReason = jr.nextStringOrEmpty()
                                        "updatedAt" -> updatedAt = jr.nextLongOrZero()
                                        "isEdited" -> isEdited = jr.nextBooleanOrFalse()
                                        "editedBy" -> editedBy = jr.nextStringOrEmpty()
                                        "editedAt" -> editedAt = jr.nextLongOrZero()
                                        "editHistory" -> editHistory = jr.nextStringOrEmpty()
                                        "folderPath" -> folderPath = jr.nextStringOrEmpty()
                                        else -> jr.skipValue()
                                    }
                                }
                                jr.endObject()
                                pins += PinItem(
                                    id = id,
                                    layerId = layerId,
                                    name = name,
                                    description = description,
                                    lat = lat,
                                    lng = lng,
                                    status = status,
                                    issueReason = issueReason,
                                    updatedAt = updatedAt,
                                    isEdited = isEdited,
                                    editedBy = editedBy,
                                    editedAt = editedAt,
                                    editHistory = editHistory,
                                    folderPath = folderPath
                                )
                            }
                            jr.endArray()
                        } else jr.skipValue()
                    }
                    "tracks" -> {
                        if (jr.peek() == JsonToken.BEGIN_ARRAY) {
                            jr.beginArray()
                            while (jr.hasNext()) {
                                var id = ""
                                var layerId = ""
                                var name = "Track"
                                var coords = ""
                                var updatedAt = 0L
                                var folderPath = ""
                                jr.beginObject()
                                while (jr.hasNext()) {
                                    when (jr.nextName()) {
                                        "id" -> id = jr.nextStringOrEmpty()
                                        "layerId" -> layerId = jr.nextStringOrEmpty()
                                        "name" -> name = jr.nextStringOrEmpty().ifBlank { "Track" }
                                        "coords" -> coords = jr.nextStringOrEmpty()
                                        "updatedAt" -> updatedAt = jr.nextLongOrZero()
                                        "folderPath" -> folderPath = jr.nextStringOrEmpty()
                                        else -> jr.skipValue()
                                    }
                                }
                                jr.endObject()
                                tracks += TrackItem(
                                    id = id,
                                    layerId = layerId,
                                    name = name,
                                    coords = coords,
                                    updatedAt = updatedAt,
                                    folderPath = folderPath
                                )
                            }
                            jr.endArray()
                        } else jr.skipValue()
                    }
                    else -> jr.skipValue()
                }
            }
            jr.endObject()
        }
        return LocalStoreSnapshot(layers.sortedByDescending { it.updatedAt }, pins, tracks)
    }

    fun preloadAsync(ctx: Context, onLoaded: ((LocalStoreSnapshot) -> Unit)? = null) {
        val existing = cachedSnapshot
        if (existing != null) {
            onLoaded?.invoke(existing)
            return
        }
        val appCtx = ctx.applicationContext
        ioExecutor.execute {
            val snap = loadAll(appCtx)
            onLoaded?.invoke(snap)
        }
    }

    fun loadAll(ctx: Context): LocalStoreSnapshot {
        cachedSnapshot?.let { return it }
        return synchronized(lock) {
            cachedSnapshot?.let { return it }
            val f = storeFile(ctx)
            val staleTmp = File(ctx.filesDir, "$FILE_NAME.tmp")
            val staleTmp2 = File(ctx.filesDir, "$FILE_NAME.tmp2")
            val loaded = synchronized(diskLock) {
                if (staleTmp.exists()) runCatching { staleTmp.delete() }
                if (staleTmp2.exists()) runCatching { staleTmp2.delete() }
                if (!f.exists()) {
                    LocalStoreSnapshot(emptyList(), emptyList(), emptyList())
                } else {
                    runCatching {
                        FileInputStream(f).use { fis -> readSnapshotFromStream(fis) }
                    }.getOrElse {
                        LocalStoreSnapshot(emptyList(), emptyList(), emptyList())
                    }
                }
            }
            cachedSnapshot = loaded
            loaded
        }
    }

    private fun writeAll(ctx: Context, snap: LocalStoreSnapshot) {
        val sortedSnap = snap.copy(layers = snap.layers.sortedByDescending { it.updatedAt })
        cachedSnapshot = sortedSnap
        pendingWrite.set(sortedSnap)
        val appCtx = ctx.applicationContext
        if (writeScheduled.compareAndSet(false, true)) {
            ioExecutor.execute {
                while (true) {
                    writeScheduled.set(false)
                    val toWrite = pendingWrite.getAndSet(null) ?: break
                    persistSnapshotToDisk(appCtx, toWrite)
                    if (pendingWrite.get() == null) break
                    if (!writeScheduled.compareAndSet(false, true)) break
                }
            }
        }
    }

    private fun persistSnapshotToDisk(appCtx: Context, sortedSnap: LocalStoreSnapshot) {
        synchronized(diskLock) {
            runCatching {
                val tmp = File(appCtx.filesDir, "$FILE_NAME.tmp")
                FileOutputStream(tmp).use { fos ->
                    JsonWriter(BufferedWriter(OutputStreamWriter(fos, Charsets.UTF_8), 32 * 1024)).use { jw ->
                        jw.beginObject()
                        jw.name("layers")
                        jw.beginArray()
                        sortedSnap.layers.forEach { l ->
                            jw.beginObject()
                            jw.name("id").value(l.id)
                            jw.name("name").value(l.name)
                            jw.name("createdBy").value(l.createdBy)
                            jw.name("creatorDeviceId").value(l.creatorDeviceId)
                            jw.name("updatedAt").value(l.updatedAt)
                            jw.endObject()
                        }
                        jw.endArray()

                        jw.name("pins")
                        jw.beginArray()
                        sortedSnap.pins.forEach { p ->
                            jw.beginObject()
                            jw.name("id").value(p.id)
                            jw.name("layerId").value(p.layerId)
                            jw.name("name").value(p.name)
                            jw.name("description").value(p.description)
                            jw.name("lat").value(if (p.lat.isFinite()) p.lat else 0.0)
                            jw.name("lng").value(if (p.lng.isFinite()) p.lng else 0.0)
                            jw.name("status").value(p.status)
                            jw.name("issueReason").value(p.issueReason)
                            jw.name("updatedAt").value(p.updatedAt)
                            jw.name("isEdited").value(p.isEdited)
                            jw.name("editedBy").value(p.editedBy)
                            jw.name("editedAt").value(p.editedAt)
                            jw.name("editHistory").value(p.editHistory)
                            if (p.folderPath.isNotBlank()) jw.name("folderPath").value(p.folderPath)
                            jw.endObject()
                        }
                        jw.endArray()

                        jw.name("tracks")
                        jw.beginArray()
                        sortedSnap.tracks.forEach { t ->
                            jw.beginObject()
                            jw.name("id").value(t.id)
                            jw.name("layerId").value(t.layerId)
                            jw.name("name").value(t.name)
                            jw.name("coords").value(t.coords)
                            jw.name("updatedAt").value(t.updatedAt)
                            if (t.folderPath.isNotBlank()) jw.name("folderPath").value(t.folderPath)
                            jw.endObject()
                        }
                        jw.endArray()
                        jw.endObject()
                        jw.flush()
                        fos.fd.sync()
                    }
                }
                val dst = storeFile(appCtx)
                if (!tmp.renameTo(dst)) {
                    val backupTmp = File(appCtx.filesDir, "$FILE_NAME.tmp2")
                    FileInputStream(tmp).use { input ->
                        FileOutputStream(backupTmp).use { output ->
                            input.copyTo(output, bufferSize = 32 * 1024)
                            output.flush()
                            output.fd.sync()
                        }
                    }
                    if (!backupTmp.renameTo(dst)) {
                        backupTmp.copyTo(dst, overwrite = true)
                        backupTmp.delete()
                    }
                    tmp.delete()
                }
            }.onFailure { android.util.Log.e("LocalFolderStore", "persistSnapshotToDisk failed", it) }
        }
    }

    fun saveNewLayer(
        ctx: Context,
        layerId: String,
        layerName: String,
        createdBy: String,
        creatorDeviceId: String,
        parsed: ParsedKml
    ) = synchronized(lock) {
        val cur = loadAll(ctx)
        val now = System.currentTimeMillis()
        val layer = LayerItem(
            id = layerId,
            name = layerName,
            shareCode = "",
            createdBy = createdBy,
            creatorDeviceId = creatorDeviceId,
            updatedAt = now
        )
        val fixedPins = parsed.pins.map {
            it.copy(
                layerId = layerId,
                status = PinStatus.fromRaw(it.status).key,
                updatedAt = if (it.updatedAt > 0L) it.updatedAt else now
            )
        }
        val fixedTracks = parsed.tracks.map {
            it.copy(
                layerId = layerId,
                updatedAt = if (it.updatedAt > 0L) it.updatedAt else now
            )
        }
        writeAll(
            ctx,
            LocalStoreSnapshot(
                layers = cur.layers.filterNot { it.id == layerId } + layer,
                pins = cur.pins.filterNot { it.layerId == layerId } + fixedPins,
                tracks = cur.tracks.filterNot { it.layerId == layerId } + fixedTracks
            )
        )
    }

    fun appendToLayer(
        ctx: Context,
        layerId: String,
        newPins: List<PinItem>,
        newTracks: List<TrackItem>
    ) = synchronized(lock) {
        val cur = loadAll(ctx)
        val now = System.currentTimeMillis()
        val updatedLayers = cur.layers.map { if (it.id == layerId) it.copy(updatedAt = now) else it }
        val fixedPins = newPins.map { it.copy(id = UUID.randomUUID().toString(), layerId = layerId, updatedAt = now) }
        val fixedTracks = newTracks.map { it.copy(id = UUID.randomUUID().toString(), layerId = layerId, updatedAt = now) }
        writeAll(ctx, LocalStoreSnapshot(updatedLayers, cur.pins + fixedPins, cur.tracks + fixedTracks))
    }

    fun addManualPin(
        ctx: Context,
        name: String,
        description: String,
        lat: Double,
        lng: Double,
        deviceStamp: String,
        deviceId: String
    ): PinItem = synchronized(lock) {
        val cur = loadAll(ctx)
        val now = System.currentTimeMillis()
        val cleanId = deviceId.filter { it.isLetterOrDigit() }.uppercase().ifBlank { "LOCAL" }
        val layerId = "local_manual_$cleanId"
        val existingLayer = cur.layers.firstOrNull { it.id == layerId }
        val updatedLayer = existingLayer?.copy(updatedAt = now) ?: LayerItem(
            id = layerId,
            name = "My Field Pins",
            shareCode = "",
            createdBy = deviceStamp,
            creatorDeviceId = cleanId,
            updatedAt = now
        )
        val pin = PinItem(
            id = UUID.randomUUID().toString(),
            layerId = layerId,
            name = name.ifBlank { "New Pin" },
            description = description,
            lat = lat,
            lng = lng,
            status = "pending",
            issueReason = "",
            updatedAt = now,
            isEdited = false,
            editedBy = "",
            editedAt = 0L,
            editHistory = ""
        )
        val nextLayers = cur.layers.filterNot { it.id == layerId } + updatedLayer
        writeAll(ctx, cur.copy(layers = nextLayers, pins = cur.pins + pin))
        pin
    }

    fun updatePinStatus(
        ctx: Context,
        pin: PinItem,
        status: String,
        deviceStamp: String,
        issueReason: String = ""
    ): PinItem = synchronized(lock) {
        val cur = loadAll(ctx)
        val now = System.currentTimeMillis()
        val cleanReason = if (status.equals("issue", true) || status.equals("done", true)) {
            sanitizeHistoryToken(issueReason)
        } else ""
        val safeStamp = sanitizeHistoryToken(deviceStamp)
        val detail = if (cleanReason.isNotBlank()) "${statusLabel(status)}: $cleanReason" else statusLabel(status)
        val line = "${formatTime(now)} • $safeStamp → $detail"
        val history = PinEditHistoryAudit.prependHistoryLine(line, pin.editHistory)
        val updated = pin.copy(
            status = status,
            issueReason = cleanReason,
            updatedAt = now,
            isEdited = true,
            editedBy = deviceStamp,
            editedAt = now,
            editHistory = history
        )
        writeAll(ctx, cur.copy(pins = cur.pins.map { if (it.id == pin.id) updated else it }))
        updated
    }

    fun updatePinsStatusBatch(
        ctx: Context,
        targetPins: List<PinItem>,
        status: String,
        deviceStamp: String,
        issueReason: String = ""
    ): List<PinItem> = synchronized(lock) {
        if (targetPins.isEmpty()) return emptyList()
        val cur = loadAll(ctx)
        val now = System.currentTimeMillis()
        val cleanReason = if (status.equals("issue", true) || status.equals("done", true)) {
            sanitizeHistoryToken(issueReason)
        } else ""
        val safeStamp = sanitizeHistoryToken(deviceStamp)
        val detail = if (cleanReason.isNotBlank()) "${statusLabel(status)}: $cleanReason" else statusLabel(status)
        val line = "${formatTime(now)} • $safeStamp → $detail"
        val targetIds = targetPins.mapTo(HashSet(targetPins.size)) { it.id }
        val updatedList = ArrayList<PinItem>(targetPins.size)
        val nextPins = cur.pins.map { existing ->
            if (existing.id in targetIds) {
                val history = PinEditHistoryAudit.prependHistoryLine(line, existing.editHistory)
                existing.copy(
                    status = status,
                    issueReason = cleanReason,
                    updatedAt = now,
                    isEdited = true,
                    editedBy = deviceStamp,
                    editedAt = now,
                    editHistory = history
                ).also { updatedList.add(it) }
            } else {
                existing
            }
        }
        writeAll(ctx, cur.copy(pins = nextPins))
        updatedList
    }

    fun updatePinText(
        ctx: Context,
        pin: PinItem,
        newName: String,
        newDesc: String,
        deviceStamp: String
    ): PinItem = synchronized(lock) {
        val cur = loadAll(ctx)
        val now = System.currentTimeMillis()
        val line = "${formatTime(now)} • ${sanitizeHistoryToken(deviceStamp)} edited text"
        val history = PinEditHistoryAudit.prependHistoryLine(line, pin.editHistory)
        val updated = pin.copy(
            name = newName.ifBlank { pin.name },
            description = newDesc,
            updatedAt = now,
            isEdited = true,
            editedBy = deviceStamp,
            editedAt = now,
            editHistory = history
        )
        writeAll(ctx, cur.copy(pins = cur.pins.map { if (it.id == pin.id) updated else it }))
        updated
    }

    fun deletePinHistoryEntry(ctx: Context, pin: PinItem, lineIndex: Int): PinItem = synchronized(lock) {
        val lines = pin.editHistory.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (lineIndex !in lines.indices) return pin
        lines.removeAt(lineIndex)
        val newHistory = lines.joinToString("\n")
        val now = System.currentTimeMillis()

        val latestStatusLine = lines.firstOrNull { it.contains("→") }
        var newStatus = "pending"
        var newReason = ""
        var newEditedBy = ""
        val newIsEdited = lines.isNotEmpty()
        val newEditedAt = if (lines.isNotEmpty()) {
            PinEditHistoryAudit.parseLineTimestampMs(lines.first()).takeIf { it > 0L } ?: now
        } else 0L

        if (latestStatusLine != null) {
            val left = latestStatusLine.substringBefore("→").trim()
            val right = latestStatusLine.substringAfter("→").trim()
            newEditedBy = left.substringAfter("•", left).trim()
            val statusToken = right.substringBefore(":").trim().lowercase()
            val reasonPart = if (right.contains(":")) right.substringAfter(":").trim() else ""
            newStatus = when (statusToken) {
                "done" -> "done"
                "issue" -> "issue"
                else -> "pending"
            }
            newReason = if (newStatus == "done" || newStatus == "issue") reasonPart else ""
        } else if (lines.isNotEmpty()) {
            val first = lines.first()
            val afterBullet = first.substringAfter("•", first).trim()
            newEditedBy = if (afterBullet.endsWith("edited text", ignoreCase = true)) {
                afterBullet.dropLast("edited text".length).trim()
            } else afterBullet
        }

        val updated = pin.copy(
            status = newStatus,
            issueReason = newReason,
            updatedAt = now,
            isEdited = newIsEdited,
            editedBy = newEditedBy,
            editedAt = newEditedAt,
            editHistory = newHistory
        )
        val cur = loadAll(ctx)
        writeAll(ctx, cur.copy(pins = cur.pins.map { if (it.id == pin.id) updated else it }))
        updated
    }

    fun resetPinStatusToPending(ctx: Context, pin: PinItem): PinItem = synchronized(lock) {
        val now = System.currentTimeMillis()
        val updated = pin.copy(
            status = "pending",
            issueReason = "",
            updatedAt = now,
            isEdited = false,
            editedBy = "",
            editedAt = 0L,
            editHistory = ""
        )
        val cur = loadAll(ctx)
        writeAll(ctx, cur.copy(pins = cur.pins.map { if (it.id == pin.id) updated else it }))
        updated
    }

    fun restorePinStatusSnapshot(ctx: Context, previousPin: PinItem): PinItem = synchronized(lock) {
        val cur = loadAll(ctx)
        val now = System.currentTimeMillis()
        val restored = previousPin.copy(updatedAt = now)
        writeAll(ctx, cur.copy(pins = cur.pins.map { if (it.id == previousPin.id) restored else it }))
        restored
    }

    fun renameLayer(ctx: Context, layerId: String, newName: String) = synchronized(lock) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        val cur = loadAll(ctx)
        val now = System.currentTimeMillis()
        writeAll(ctx, cur.copy(layers = cur.layers.map { if (it.id == layerId) it.copy(name = clean, updatedAt = now) else it }))
    }

    fun deletePin(ctx: Context, pinId: String) = synchronized(lock) {
        val cur = loadAll(ctx)
        writeAll(ctx, cur.copy(pins = cur.pins.filterNot { it.id == pinId }))
    }

    fun deletePins(ctx: Context, pinIds: Collection<String>) = synchronized(lock) {
        if (pinIds.isEmpty()) return
        val drop = pinIds.toSet()
        val cur = loadAll(ctx)
        writeAll(ctx, cur.copy(pins = cur.pins.filterNot { it.id in drop }))
    }

    fun deleteLayer(ctx: Context, layerId: String) = synchronized(lock) {
        val cur = loadAll(ctx)
        writeAll(
            ctx,
            LocalStoreSnapshot(
                layers = cur.layers.filterNot { it.id == layerId },
                pins = cur.pins.filterNot { it.layerId == layerId },
                tracks = cur.tracks.filterNot { it.layerId == layerId }
            )
        )
    }

    private fun sanitizeHistoryToken(raw: String): String {
        return raw.replace(Regex("[\\r\\n]+"), " ").replace("→", "-").trim()
    }

    private fun formatTime(ts: Long): String = TimeFormatters.canonicalHistory(ts)
}

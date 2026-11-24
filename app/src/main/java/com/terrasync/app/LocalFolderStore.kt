package com.terrasync.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
    private val ioExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var cachedSnapshot: LocalStoreSnapshot? = null

    fun isLocalLayerId(layerId: String): Boolean = layerId.startsWith("local_")

    fun newLocalLayerId(): String = "local_${UUID.randomUUID()}"

    private fun storeFile(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    fun loadAll(ctx: Context): LocalStoreSnapshot {
        cachedSnapshot?.let { return it }
        return synchronized(lock) {
            cachedSnapshot?.let { return it }
            val f = storeFile(ctx)
            val loaded = if (!f.exists()) {
                LocalStoreSnapshot(emptyList(), emptyList(), emptyList())
            } else {
                runCatching {
                    val root = JSONObject(f.readText(Charsets.UTF_8))
                    val layers = mutableListOf<LayerItem>()
                    val pins = mutableListOf<PinItem>()
                    val tracks = mutableListOf<TrackItem>()

                    val lArr = root.optJSONArray("layers") ?: JSONArray()
                    for (i in 0 until lArr.length()) {
                        val o = lArr.getJSONObject(i)
                        layers += LayerItem(
                            id = o.optString("id"),
                            name = o.optString("name", "Local Folder"),
                            shareCode = "",
                            createdBy = o.optString("createdBy"),
                            creatorDeviceId = o.optString("creatorDeviceId"),
                            updatedAt = o.optLong("updatedAt", 0L)
                        )
                    }

                    val pArr = root.optJSONArray("pins") ?: JSONArray()
                    for (i in 0 until pArr.length()) {
                        val o = pArr.getJSONObject(i)
                        pins += PinItem(
                            id = o.optString("id"),
                            layerId = o.optString("layerId"),
                            name = o.optString("name", "Pin"),
                            description = o.optString("description"),
                            lat = o.optDouble("lat"),
                            lng = o.optDouble("lng"),
                            status = o.optString("status", "pending"),
                            issueReason = o.optString("issueReason"),
                            updatedAt = o.optLong("updatedAt", 0L),
                            isEdited = o.optBoolean("isEdited", false),
                            editedBy = o.optString("editedBy"),
                            editedAt = o.optLong("editedAt", 0L),
                            editHistory = o.optString("editHistory")
                        )
                    }

                    val tArr = root.optJSONArray("tracks") ?: JSONArray()
                    for (i in 0 until tArr.length()) {
                        val o = tArr.getJSONObject(i)
                        tracks += TrackItem(
                            id = o.optString("id"),
                            layerId = o.optString("layerId"),
                            name = o.optString("name", "Track"),
                            coords = o.optString("coords"),
                            updatedAt = o.optLong("updatedAt", 0L)
                        )
                    }
                    LocalStoreSnapshot(layers.sortedByDescending { it.updatedAt }, pins, tracks)
                }.getOrElse {
                    LocalStoreSnapshot(emptyList(), emptyList(), emptyList())
                }
            }
            cachedSnapshot = loaded
            loaded
        }
    }

    private fun writeAll(ctx: Context, snap: LocalStoreSnapshot) {
        val sortedSnap = snap.copy(layers = snap.layers.sortedByDescending { it.updatedAt })
        cachedSnapshot = sortedSnap
        val appCtx = ctx.applicationContext
        ioExecutor.execute {
            runCatching {
                val root = JSONObject()
                val lArr = JSONArray()
                sortedSnap.layers.forEach { l ->
                    lArr.put(JSONObject().apply {
                        put("id", l.id)
                        put("name", l.name)
                        put("createdBy", l.createdBy)
                        put("creatorDeviceId", l.creatorDeviceId)
                        put("updatedAt", l.updatedAt)
                    })
                }
                val pArr = JSONArray()
                sortedSnap.pins.forEach { p ->
                    pArr.put(JSONObject().apply {
                        put("id", p.id)
                        put("layerId", p.layerId)
                        put("name", p.name)
                        put("description", p.description)
                        put("lat", p.lat)
                        put("lng", p.lng)
                        put("status", p.status)
                        put("issueReason", p.issueReason)
                        put("updatedAt", p.updatedAt)
                        put("isEdited", p.isEdited)
                        put("editedBy", p.editedBy)
                        put("editedAt", p.editedAt)
                        put("editHistory", p.editHistory)
                    })
                }
                val tArr = JSONArray()
                sortedSnap.tracks.forEach { t ->
                    tArr.put(JSONObject().apply {
                        put("id", t.id)
                        put("layerId", t.layerId)
                        put("name", t.name)
                        put("coords", t.coords)
                        put("updatedAt", t.updatedAt)
                    })
                }
                root.put("layers", lArr)
                root.put("pins", pArr)
                root.put("tracks", tArr)
                val tmp = File(appCtx.filesDir, "$FILE_NAME.tmp")
                tmp.writeText(root.toString(), Charsets.UTF_8)
                val dst = storeFile(appCtx)
                if (!tmp.renameTo(dst)) {
                    tmp.copyTo(dst, overwrite = true)
                    tmp.delete()
                }
            }
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
        val fixedPins = parsed.pins.map { it.copy(layerId = layerId, updatedAt = now) }
        val fixedTracks = parsed.tracks.map { it.copy(layerId = layerId, updatedAt = now) }
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
            editedBy = deviceStamp,
            editedAt = now,
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
        val history = listOf(line, pin.editHistory).filter { it.isNotBlank() }.joinToString("\n").take(1500)
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
        val history = listOf(line, pin.editHistory).filter { it.isNotBlank() }.joinToString("\n").take(1500)
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
        val newEditedAt = if (lines.isNotEmpty()) now else 0L

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
            newEditedBy = first.substringAfter("•", first).substringBefore(" edited text").trim()
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

    private fun formatTime(ts: Long): String = SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(ts))
}

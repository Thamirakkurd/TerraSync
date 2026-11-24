package com.terrasync.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.PersistentCacheSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class SyncRepo {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        runCatching {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .setSizeBytes(100L * 1024L * 1024L)
                        .build()
                )
                .build()
        }
    }

    private val regs = mutableListOf<ListenerRegistration>()
    private val layerMap = mutableMapOf<String, LayerItem>()
    private val pinsByLayer = mutableMapOf<String, List<PinItem>>()
    private val tracksByLayer = mutableMapOf<String, List<TrackItem>>()
    private var connected: Boolean = false
    private var listenerCallback: ((List<LayerItem>, List<PinItem>, List<TrackItem>, Boolean) -> Unit)? = null

    fun syncJoinedLayers(
        joinedLayerIds: Set<String>,
        onUpdate: (List<LayerItem>, List<PinItem>, List<TrackItem>, Boolean) -> Unit
    ) {
        listenerCallback = onUpdate
        stopListening()
        layerMap.clear()
        pinsByLayer.clear()
        tracksByLayer.clear()

        if (joinedLayerIds.isEmpty()) {
            connected = true
            emit()
            return
        }

        joinedLayerIds.forEach { layerId ->
            regs += db.collection("layers").document(layerId).addSnapshotListener { doc, err ->
                if (err != null) {
                    connected = false
                    emit()
                    return@addSnapshotListener
                }
                connected = true
                if (doc != null && doc.exists()) {
                    layerMap[layerId] = LayerItem(
                        id = doc.id,
                        name = doc.getString("name") ?: "Layer",
                        shareCode = doc.getString("shareCode") ?: "",
                        createdBy = doc.getString("createdBy") ?: "",
                        creatorDeviceId = doc.getString("creatorDeviceId") ?: "",
                        updatedAt = doc.getLong("updatedAt") ?: 0L
                    )
                } else {
                    layerMap.remove(layerId)
                }
                emit()
            }

            regs += db.collection("pins").whereEqualTo("layerId", layerId).addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                connected = true
                pinsByLayer[layerId] = snap?.documents?.mapNotNull { d ->
                    val lat = d.getDouble("lat") ?: return@mapNotNull null
                    val lng = d.getDouble("lng") ?: return@mapNotNull null
                    PinItem(
                        id = d.id,
                        layerId = d.getString("layerId") ?: layerId,
                        name = d.getString("name") ?: "Pin",
                        description = d.getString("description") ?: "",
                        lat = lat,
                        lng = lng,
                        status = d.getString("status") ?: "pending",
                        issueReason = d.getString("issueReason") ?: "",
                        updatedAt = d.getLong("updatedAt") ?: 0L,
                        isEdited = d.getBoolean("isEdited") ?: false,
                        editedBy = d.getString("editedBy") ?: "",
                        editedAt = d.getLong("editedAt") ?: 0L,
                        editHistory = d.getString("editHistory") ?: ""
                    )
                } ?: emptyList()
                emit()
            }

            regs += db.collection("tracks").whereEqualTo("layerId", layerId).addSnapshotListener { snap, err ->
                if (err != null) return@addSnapshotListener
                connected = true
                tracksByLayer[layerId] = snap?.documents?.mapNotNull { d ->
                    val coords = d.getString("coords") ?: return@mapNotNull null
                    TrackItem(
                        id = d.id,
                        layerId = d.getString("layerId") ?: layerId,
                        name = d.getString("name") ?: "Track",
                        coords = coords,
                        updatedAt = d.getLong("updatedAt") ?: 0L
                    )
                } ?: emptyList()
                emit()
            }
        }
    }

    private fun emit() {
        val sortedLayers = layerMap.values.sortedByDescending { it.updatedAt }
        val allPins = pinsByLayer.values.flatten()
        val allTracks = tracksByLayer.values.flatten()
        listenerCallback?.invoke(sortedLayers, allPins, allTracks, connected)
    }

    fun stopListening() {
        regs.forEach { it.remove() }
        regs.clear()
    }

    fun generateShareCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun importLayer(
        layerId: String,
        layerName: String,
        createdBy: String,
        creatorDeviceId: String,
        parsed: ParsedKml,
        onDone: (Boolean, String, String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val shareCode = generateShareCode()
        val ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

        ops += db.collection("layers").document(layerId) to mapOf(
            "id" to layerId,
            "name" to layerName,
            "shareCode" to shareCode,
            "createdBy" to createdBy,
            "creatorDeviceId" to creatorDeviceId,
            "updatedAt" to now
        )

        parsed.pins.forEach { p ->
            ops += db.collection("pins").document(p.id) to mapOf(
                "id" to p.id,
                "layerId" to layerId,
                "name" to p.name,
                "description" to p.description,
                "lat" to p.lat,
                "lng" to p.lng,
                "status" to p.status,
                "issueReason" to p.issueReason,
                "updatedAt" to now,
                "isEdited" to p.isEdited,
                "editedBy" to p.editedBy,
                "editedAt" to p.editedAt,
                "editHistory" to p.editHistory
            )
        }

        parsed.tracks.forEach { t ->
            ops += db.collection("tracks").document(t.id) to mapOf(
                "id" to t.id,
                "layerId" to layerId,
                "name" to t.name,
                "coords" to t.coords,
                "updatedAt" to now
            )
        }

        // Queue all chunks immediately so large imports work offline in Firestore cache without stalling
        ops.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit()
        }
        onDone(true, "Imported ${parsed.pins.size} pins (Code: $shareCode)", shareCode)
    }

    fun combineLayers(
        newLayerId: String,
        layerName: String,
        createdBy: String,
        creatorDeviceId: String,
        sourcePins: List<PinItem>,
        sourceTracks: List<TrackItem>,
        onDone: (Boolean, String, String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val shareCode = generateShareCode()
        val ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

        ops += db.collection("layers").document(newLayerId) to mapOf(
            "id" to newLayerId,
            "name" to layerName,
            "shareCode" to shareCode,
            "createdBy" to createdBy,
            "creatorDeviceId" to creatorDeviceId,
            "updatedAt" to now
        )

        sourcePins.forEach { p ->
            val newId = UUID.randomUUID().toString()
            ops += db.collection("pins").document(newId) to mapOf(
                "id" to newId,
                "layerId" to newLayerId,
                "name" to p.name,
                "description" to p.description,
                "lat" to p.lat,
                "lng" to p.lng,
                "status" to p.status,
                "issueReason" to p.issueReason,
                "updatedAt" to (p.updatedAt.takeIf { it > 0L } ?: now),
                "isEdited" to p.isEdited,
                "editedBy" to p.editedBy,
                "editedAt" to p.editedAt,
                "editHistory" to p.editHistory
            )
        }

        sourceTracks.forEach { t ->
            val newId = UUID.randomUUID().toString()
            ops += db.collection("tracks").document(newId) to mapOf(
                "id" to newId,
                "layerId" to newLayerId,
                "name" to t.name,
                "coords" to t.coords,
                "updatedAt" to now
            )
        }

        ops.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit()
        }
        onDone(true, "Combined ${sourcePins.size} pins into \"$layerName\" (Code: $shareCode)", shareCode)
    }

    fun joinLayerByCode(rawCode: String, onDone: (Boolean, String, String?) -> Unit) {
        val code = rawCode.trim().uppercase().replace("-", "")
        if (code.length < 4) {
            onDone(false, "Enter a valid share code", null)
            return
        }
        db.collection("layers")
            .whereEqualTo("shareCode", code)
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                val doc = snap.documents.firstOrNull()
                if (doc == null) {
                    onDone(false, "No layer found for code $code", null)
                } else {
                    val name = doc.getString("name") ?: "Shared Layer"
                    onDone(true, "Joined \"$name\"", doc.id)
                }
            }
            .addOnFailureListener { e ->
                onDone(false, e.message ?: "Lookup failed", null)
            }
    }

    fun updatePinStatus(pin: PinItem, status: String, deviceStamp: String, issueReason: String = "") {
        val now = System.currentTimeMillis()
        val cleanReason = if (status.equals("issue", ignoreCase = true) || status.equals("done", ignoreCase = true)) issueReason.trim() else ""
        val detail = if (cleanReason.isNotBlank()) "${statusLabel(status)}: $cleanReason" else statusLabel(status)
        val line = "${formatTime(now)} • $deviceStamp → $detail"
        val history = listOf(line, pin.editHistory).filter { it.isNotBlank() }.joinToString("\n").take(1500)
        db.collection("pins").document(pin.id).update(
            mapOf(
                "status" to status,
                "issueReason" to cleanReason,
                "updatedAt" to now,
                "isEdited" to true,
                "editedBy" to deviceStamp,
                "editedAt" to now,
                "editHistory" to history
            )
        )
    }

    fun updatePinText(pin: PinItem, newName: String, newDesc: String, deviceStamp: String) {
        val now = System.currentTimeMillis()
        val line = "${formatTime(now)} • $deviceStamp edited text"
        val history = listOf(line, pin.editHistory).filter { it.isNotBlank() }.joinToString("\n").take(1500)
        db.collection("pins").document(pin.id).update(
            mapOf(
                "name" to newName.ifBlank { pin.name },
                "description" to newDesc,
                "updatedAt" to now,
                "isEdited" to true,
                "editedBy" to deviceStamp,
                "editedAt" to now,
                "editHistory" to history
            )
        )
    }

    fun addManualPin(
        name: String,
        description: String,
        lat: Double,
        lng: Double,
        deviceStamp: String,
        deviceId: String
    ): String {
        val now = System.currentTimeMillis()
        val cleanId = deviceId.filter { it.isLetterOrDigit() }.uppercase().ifBlank { "LOCAL" }
        val layerId = "manual_$cleanId"
        val shareCode = "FP$cleanId".take(6)
        val id = UUID.randomUUID().toString()
        val batch = db.batch()
        batch.set(
            db.collection("layers").document(layerId),
            mapOf(
                "id" to layerId,
                "name" to "My Field Pins",
                "shareCode" to shareCode,
                "createdBy" to deviceStamp,
                "creatorDeviceId" to cleanId,
                "updatedAt" to now
            )
        )
        batch.set(
            db.collection("pins").document(id),
            mapOf(
                "id" to id,
                "layerId" to layerId,
                "name" to name.ifBlank { "New Pin" },
                "description" to description,
                "lat" to lat,
                "lng" to lng,
                "status" to "pending",
                "issueReason" to "",
                "updatedAt" to now,
                "isEdited" to false,
                "editedBy" to deviceStamp,
                "editedAt" to now,
                "editHistory" to ""
            )
        )
        batch.commit()
        return layerId
    }

    fun renameLayer(layerId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        db.collection("layers").document(layerId).update(
            mapOf(
                "name" to clean,
                "updatedAt" to System.currentTimeMillis()
            )
        )
    }

    fun deletePinHistoryEntry(pin: PinItem, lineIndex: Int) {
        val lines = pin.editHistory.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (lineIndex !in lines.indices) return
        lines.removeAt(lineIndex)
        val newHistory = lines.joinToString("\n")
        val now = System.currentTimeMillis()

        // Recalculate live status & note from the newest remaining status entry ("→")
        val latestStatusLine = lines.firstOrNull { it.contains("→") }
        var newStatus = "pending"
        var newReason = ""
        var newEditedBy = ""
        var newIsEdited = lines.isNotEmpty()
        var newEditedAt = if (lines.isNotEmpty()) pin.editedAt.takeIf { it > 0L } ?: now else 0L

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

        db.collection("pins").document(pin.id).update(
            mapOf(
                "status" to newStatus,
                "issueReason" to newReason,
                "updatedAt" to now,
                "isEdited" to newIsEdited,
                "editedBy" to newEditedBy,
                "editedAt" to newEditedAt,
                "editHistory" to newHistory
            )
        )
    }

    fun deletePin(pinId: String) {
        db.collection("pins").document(pinId).delete()
    }

    fun deleteLayer(layerId: String, pinIds: List<String>, trackIds: List<String>) {
        val refs = mutableListOf(db.collection("layers").document(layerId))
        pinIds.forEach { refs += db.collection("pins").document(it) }
        trackIds.forEach { refs += db.collection("tracks").document(it) }
        refs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit()
        }
    }

    private fun formatTime(ts: Long): String {
        return SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(ts))
    }
}

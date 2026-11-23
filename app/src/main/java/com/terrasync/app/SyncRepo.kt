package com.terrasync.app

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.UUID

class SyncRepo {
    private val db = FirebaseFirestore.getInstance()
    private val regs = mutableListOf<ListenerRegistration>()

    private var layers: List<LayerItem> = emptyList()
    private var pins: List<PinItem> = emptyList()
    private var tracks: List<TrackItem> = emptyList()
    private var connected: Boolean = false

    fun startListening(
        onUpdate: (List<LayerItem>, List<PinItem>, List<TrackItem>, Boolean) -> Unit
    ) {
        stopListening()

        regs += db.collection("layers").addSnapshotListener { snap, err ->
            if (err != null) {
                connected = false
                onUpdate(layers, pins, tracks, false)
                return@addSnapshotListener
            }
            connected = true
            layers = snap?.documents?.map { d ->
                LayerItem(
                    id = d.id,
                    name = d.getString("name") ?: "Layer",
                    visible = d.getBoolean("visible") ?: true,
                    updatedAt = d.getLong("updatedAt") ?: 0L
                )
            }?.sortedByDescending { it.updatedAt } ?: emptyList()
            onUpdate(layers, pins, tracks, connected)
        }

        regs += db.collection("pins").addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
            connected = true
            pins = snap?.documents?.mapNotNull { d ->
                val lat = d.getDouble("lat") ?: return@mapNotNull null
                val lng = d.getDouble("lng") ?: return@mapNotNull null
                PinItem(
                    id = d.id,
                    layerId = d.getString("layerId") ?: "manual",
                    name = d.getString("name") ?: "Pin",
                    description = d.getString("description") ?: "",
                    lat = lat,
                    lng = lng,
                    status = d.getString("status") ?: "pending",
                    updatedAt = d.getLong("updatedAt") ?: 0L
                )
            } ?: emptyList()
            onUpdate(layers, pins, tracks, connected)
        }

        regs += db.collection("tracks").addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
            connected = true
            tracks = snap?.documents?.mapNotNull { d ->
                val coords = d.getString("coords") ?: return@mapNotNull null
                TrackItem(
                    id = d.id,
                    layerId = d.getString("layerId") ?: "manual",
                    name = d.getString("name") ?: "Track",
                    coords = coords,
                    updatedAt = d.getLong("updatedAt") ?: 0L
                )
            } ?: emptyList()
            onUpdate(layers, pins, tracks, connected)
        }
    }

    fun stopListening() {
        regs.forEach { it.remove() }
        regs.clear()
    }

    fun importLayer(
        layerId: String,
        layerName: String,
        parsed: ParsedKml,
        onDone: (Boolean, String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val ops = mutableListOf<() -> Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()

        ops += {
            db.collection("layers").document(layerId) to mapOf(
                "id" to layerId,
                "name" to layerName,
                "visible" to true,
                "updatedAt" to now
            )
        }
        parsed.pins.forEach { p ->
            ops += {
                db.collection("pins").document(p.id) to mapOf(
                    "id" to p.id,
                    "layerId" to layerId,
                    "name" to p.name,
                    "description" to p.description,
                    "lat" to p.lat,
                    "lng" to p.lng,
                    "status" to p.status,
                    "updatedAt" to now
                )
            }
        }
        parsed.tracks.forEach { t ->
            ops += {
                db.collection("tracks").document(t.id) to mapOf(
                    "id" to t.id,
                    "layerId" to layerId,
                    "name" to t.name,
                    "coords" to t.coords,
                    "updatedAt" to now
                )
            }
        }

        val chunks = ops.chunked(400)
        fun commitChunk(idx: Int) {
            if (idx >= chunks.size) {
                onDone(true, "Synced ${parsed.pins.size} pins, ${parsed.tracks.size} tracks")
                return
            }
            val batch = db.batch()
            chunks[idx].forEach { item ->
                val (ref, data) = item()
                batch.set(ref, data)
            }
            batch.commit()
                .addOnSuccessListener { commitChunk(idx + 1) }
                .addOnFailureListener { e -> onDone(false, e.message ?: "Sync failed") }
        }
        commitChunk(0)
    }

    fun updatePinStatus(pinId: String, status: String) {
        db.collection("pins").document(pinId).update(
            mapOf("status" to status, "updatedAt" to System.currentTimeMillis())
        )
    }

    fun addManualPin(name: String, lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val batch = db.batch()
        batch.set(
            db.collection("layers").document("manual"),
            mapOf("id" to "manual", "name" to "Manual Pins", "visible" to true, "updatedAt" to now)
        )
        batch.set(
            db.collection("pins").document(id),
            mapOf(
                "id" to id,
                "layerId" to "manual",
                "name" to name.ifBlank { "New Pin" },
                "description" to "",
                "lat" to lat,
                "lng" to lng,
                "status" to "pending",
                "updatedAt" to now
            )
        )
        batch.commit()
    }

    fun deletePin(pinId: String) {
        db.collection("pins").document(pinId).delete()
    }

    fun toggleLayerVisibility(layerId: String, visible: Boolean) {
        db.collection("layers").document(layerId).update("visible", visible)
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
}

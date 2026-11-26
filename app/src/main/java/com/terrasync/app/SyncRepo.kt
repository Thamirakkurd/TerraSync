package com.terrasync.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SyncRepo(context: Context? = null) {
    private val appCtx: Context? = context?.applicationContext
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

    private val auth: FirebaseAuth? = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val layerRegs = mutableMapOf<String, List<ListenerRegistration>>()
    private val layerMap = mutableMapOf<String, LayerItem>()
    private val pinsByLayer = mutableMapOf<String, List<PinItem>>()
    private val tracksByLayer = mutableMapOf<String, List<TrackItem>>()
    private val liveListenerStates = mutableMapOf<String, Boolean>()
    private val pendingWritesByLayer = ConcurrentHashMap<String, Int>()
    private var connected: Boolean = false
    private var listenerCallback: ((List<LayerItem>, List<PinItem>, List<TrackItem>, Boolean) -> Unit)? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pendingEmitRunnable: Runnable? = null

    fun isLayerLive(layerId: String): Boolean {
        if (!isOnline()) return false
        return liveListenerStates["pins:$layerId"] == true
    }

    private fun computeConnected(): Boolean {
        if (!isOnline()) return false
        if (liveListenerStates.isEmpty()) return true
        val pinStates = liveListenerStates.filterKeys { it.startsWith("pins:") }
        return if (pinStates.isNotEmpty()) {
            pinStates.values.all { it }
        } else {
            liveListenerStates.values.all { it }
        }
    }

    init {
        ensureAuthenticated()
        registerNetworkMonitor()
    }

    private fun registerNetworkMonitor() {
        val ctx = appCtx ?: return
        runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    mainHandler.post {
                        val next = computeConnected()
                        if (next != connected) {
                            connected = next
                            emitNow()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    mainHandler.post {
                        val next = computeConnected()
                        if (next != connected) {
                            connected = next
                            emitNow()
                        }
                    }
                }
            }
            cm.registerDefaultNetworkCallback(cb)
            networkCallback = cb
        }
    }

    fun currentUid(): String? = runCatching { auth?.currentUser?.uid }.getOrNull()

    fun ensureAuthenticated(onReady: ((String?) -> Unit)? = null) {
        val a = auth
        if (a == null) {
            onReady?.invoke(null)
            return
        }
        val existing = a.currentUser
        if (existing != null) {
            onReady?.invoke(existing.uid)
            return
        }
        runCatching {
            a.signInAnonymously()
                .addOnSuccessListener { res -> onReady?.invoke(res.user?.uid) }
                .addOnFailureListener { onReady?.invoke(null) }
        }.onFailure {
            onReady?.invoke(null)
        }
    }

    private fun isOnline(): Boolean {
        val ctx = appCtx ?: RoutingEngine.appContext ?: return true
        return runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    private fun commitOpsInBatches(
        ops: List<Pair<DocumentReference, Map<String, Any>>>,
        successMsg: String,
        shareCode: String,
        onDone: (Boolean, String, String) -> Unit,
        requireServerCommit: Boolean = false
    ) {
        val chunks = ops.chunked(400)
        if (chunks.isEmpty()) {
            onDone(true, successMsg, shareCode)
            return
        }

        val online = isOnline()
        if (requireServerCommit && !online) {
            onDone(false, "Internet connection required to upload to cloud — local copy kept safely", "")
            return
        }

        val remaining = AtomicInteger(chunks.size)
        val firstError = AtomicReference<String?>(null)
        val finished = AtomicBoolean(false)

        val timeoutRunnable = Runnable {
            if (finished.compareAndSet(false, true)) {
                val err = firstError.get()
                if (err != null) {
                    onDone(false, "Sync error: $err", shareCode)
                } else if (requireServerCommit) {
                    onDone(false, "Upload timed out — local copy kept safely", "")
                } else {
                    onDone(true, "$successMsg (saved locally — syncing)", shareCode)
                }
            }
        }
        if (online) {
            mainHandler.postDelayed(timeoutRunnable, if (requireServerCommit) 25_000L else 12_000L)
        }

        chunks.forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (ref, data) -> batch.set(ref, data) }
            batch.commit()
                .addOnFailureListener { e ->
                    firstError.compareAndSet(null, e.message ?: "Batch write failed")
                }
                .addOnCompleteListener {
                    if (remaining.decrementAndGet() == 0 && finished.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        val err = firstError.get()
                        if (err != null) {
                            onDone(false, "Sync error: $err", shareCode)
                        } else {
                            onDone(true, successMsg, shareCode)
                        }
                    }
                }
        }

        if (!online && finished.compareAndSet(false, true)) {
            onDone(true, "$successMsg (offline — will sync when online)", shareCode)
        }
    }

    fun syncJoinedLayers(
        joinedLayerIds: Set<String>,
        onUpdate: (List<LayerItem>, List<PinItem>, List<TrackItem>, Boolean) -> Unit
    ) {
        listenerCallback = onUpdate

        val removed = layerRegs.keys - joinedLayerIds
        removed.forEach { layerId ->
            layerRegs.remove(layerId)?.forEach { it.remove() }
            layerMap.remove(layerId)
            pinsByLayer.remove(layerId)
            tracksByLayer.remove(layerId)
            pendingWritesByLayer.remove(layerId)
            liveListenerStates.remove("layer:$layerId")
            liveListenerStates.remove("pins:$layerId")
            liveListenerStates.remove("tracks:$layerId")
        }

        fun updateConnectedFlag(key: String, isLive: Boolean) {
            liveListenerStates[key] = isLive
            connected = computeConnected()
        }

        if (joinedLayerIds.isEmpty()) {
            liveListenerStates.clear()
            pendingWritesByLayer.clear()
            connected = isOnline()
            emitNow()
            return
        }

        val added = joinedLayerIds - layerRegs.keys
        added.forEach { layerId ->
            val regsForLayer = mutableListOf<ListenerRegistration>()
            regsForLayer += db.collection("layers").document(layerId)
                .addSnapshotListener(MetadataChanges.INCLUDE) { doc, err ->
                    if (err != null) {
                        updateConnectedFlag("layer:$layerId", false)
                        emit()
                        return@addSnapshotListener
                    }
                    updateConnectedFlag("layer:$layerId", doc?.metadata?.isFromCache != true)
                    if (doc != null && doc.exists()) {
                        layerMap[layerId] = LayerItem(
                            id = doc.id,
                            name = doc.getString("name") ?: "Layer",
                            shareCode = doc.getString("shareCode") ?: "",
                            createdBy = doc.getString("createdBy") ?: "",
                            creatorDeviceId = doc.getString("creatorDeviceId") ?: "",
                            creatorUid = doc.getString("creatorUid") ?: "",
                            updatedAt = doc.getLong("updatedAt") ?: 0L
                        )
                    } else {
                        layerMap.remove(layerId)
                        pinsByLayer.remove(layerId)
                        tracksByLayer.remove(layerId)
                    }
                    emit()
                }

            regsForLayer += db.collection("pins").whereEqualTo("layerId", layerId)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                    if (err != null) {
                        updateConnectedFlag("pins:$layerId", false)
                        emit()
                        return@addSnapshotListener
                    }
                    updateConnectedFlag("pins:$layerId", snap?.metadata?.isFromCache != true)
                    pendingWritesByLayer[layerId] = snap?.documents?.count { it.metadata.hasPendingWrites() } ?: 0
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
                            status = PinStatus.fromRaw(d.getString("status")).key,
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

            regsForLayer += db.collection("tracks").whereEqualTo("layerId", layerId)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                    if (err != null) {
                        updateConnectedFlag("tracks:$layerId", false)
                        emit()
                        return@addSnapshotListener
                    }
                    updateConnectedFlag("tracks:$layerId", snap?.metadata?.isFromCache != true)
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
            layerRegs[layerId] = regsForLayer
        }

        if (removed.isNotEmpty() || added.isEmpty()) {
            connected = computeConnected()
            emitNow()
        }
    }

    private fun emit() {
        pendingEmitRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            pendingEmitRunnable = null
            emitNow()
        }
        pendingEmitRunnable = r
        mainHandler.postDelayed(r, 40L)
    }

    private fun emitNow() {
        pendingEmitRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingEmitRunnable = null
        val sortedLayers = layerMap.values.sortedByDescending { it.updatedAt }
        val allPins = pinsByLayer.values.flatten()
        val allTracks = tracksByLayer.values.flatten()
        listenerCallback?.invoke(sortedLayers, allPins, allTracks, connected)
    }

    fun stopListening() {
        pendingEmitRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingEmitRunnable = null
        layerRegs.values.flatten().forEach { it.remove() }
        layerRegs.clear()
        layerMap.clear()
        pinsByLayer.clear()
        tracksByLayer.clear()
        liveListenerStates.clear()
        pendingWritesByLayer.clear()
        listenerCallback = null
        mainHandler.removeCallbacksAndMessages(null)
        val cb = networkCallback
        networkCallback = null
        if (cb != null) {
            val ctx = appCtx
            if (ctx != null) {
                runCatching {
                    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    cm.unregisterNetworkCallback(cb)
                }
            }
        }
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
        requireServerCommit: Boolean = false,
        onDone: (Boolean, String, String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val shareCode = generateShareCode()
        val ops = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()

        ops += db.collection("layers").document(layerId) to mapOf(
            "id" to layerId,
            "name" to layerName,
            "shareCode" to shareCode,
            "createdBy" to createdBy,
            "creatorDeviceId" to creatorDeviceId,
            "creatorUid" to (currentUid() ?: ""),
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

        commitOpsInBatches(
            ops,
            "Imported ${parsed.pins.size} pins into \"$layerName\" (Code: $shareCode)",
            shareCode,
            onDone,
            requireServerCommit = requireServerCommit
        )
    }

    fun appendToCloudLayer(
        layerId: String,
        layerName: String,
        newPins: List<PinItem>,
        newTracks: List<TrackItem>,
        onDone: (Boolean, String, String) -> Unit
    ) {
        val now = System.currentTimeMillis()
        val ops = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()

        newPins.forEach { p ->
            val newId = UUID.randomUUID().toString()
            ops += db.collection("pins").document(newId) to mapOf(
                "id" to newId,
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

        newTracks.forEach { t ->
            val newId = UUID.randomUUID().toString()
            ops += db.collection("tracks").document(newId) to mapOf(
                "id" to newId,
                "layerId" to layerId,
                "name" to t.name,
                "coords" to t.coords,
                "updatedAt" to now
            )
        }

        db.collection("layers").document(layerId)
            .set(mapOf("updatedAt" to now), SetOptions.merge())
        commitOpsInBatches(
            ops,
            "Merged ${newPins.size} pins into \"$layerName\"",
            "",
            onDone
        )
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
        val ops = mutableListOf<Pair<DocumentReference, Map<String, Any>>>()

        ops += db.collection("layers").document(newLayerId) to mapOf(
            "id" to newLayerId,
            "name" to layerName,
            "shareCode" to shareCode,
            "createdBy" to createdBy,
            "creatorDeviceId" to creatorDeviceId,
            "creatorUid" to (currentUid() ?: ""),
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

        commitOpsInBatches(
            ops,
            "Combined ${sourcePins.size} pins into \"$layerName\" (Code: $shareCode)",
            shareCode,
            onDone
        )
    }

    fun joinLayerByCode(rawCode: String, onDone: (Boolean, String, String?) -> Unit) {
        val code = rawCode.trim().uppercase().replace("-", "").replace(" ", "")
        if (code.length < 4) {
            onDone(false, "Enter a valid share code", null)
            return
        }
        if (!isOnline()) {
            onDone(false, "Internet connection required to join a shared folder", null)
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

    private fun sanitizeHistoryToken(raw: String): String {
        return raw.replace(Regex("[\\r\\n]+"), " ").replace("→", "-").trim()
    }

    private fun buildHistory(newLine: String, existingHistory: String): String {
        return PinEditHistoryAudit.prependHistoryLine(newLine, existingHistory)
    }

    private fun updatePinWithHistory(
        pinId: String,
        newHistoryLine: String,
        fallbackHistory: String,
        baseFields: Map<String, Any>,
        onError: ((String) -> Unit)?
    ) {
        val docRef = db.collection("pins").document(pinId)
        val fallbackPayload = baseFields + ("editHistory" to buildHistory(newHistoryLine, fallbackHistory))
        if (!isOnline()) {
            docRef.update(fallbackPayload)
                .addOnFailureListener { e -> onError?.invoke(e.message ?: "Failed to save pin update") }
            return
        }
        db.runTransaction { tx ->
            val snap = tx.get(docRef)
            val liveHistory = snap.getString("editHistory") ?: fallbackHistory
            val mergedHistory = buildHistory(newHistoryLine, liveHistory)
            tx.update(docRef, baseFields + ("editHistory" to mergedHistory))
            null
        }.addOnFailureListener { e ->
            val code = (e as? FirebaseFirestoreException)?.code
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                docRef.update(fallbackPayload)
                    .addOnFailureListener { err -> onError?.invoke(err.message ?: "Failed to save pin update") }
            } else {
                onError?.invoke(e.message ?: "Failed to save pin update")
            }
        }
    }

    fun updatePinStatus(
        pin: PinItem,
        status: String,
        deviceStamp: String,
        issueReason: String = "",
        onError: ((String) -> Unit)? = null
    ): PinItem {
        val now = System.currentTimeMillis()
        val cleanReason = if (status.equals("issue", ignoreCase = true) || status.equals("done", ignoreCase = true)) {
            sanitizeHistoryToken(issueReason)
        } else ""
        val safeStamp = sanitizeHistoryToken(deviceStamp)
        val detail = if (cleanReason.isNotBlank()) "${statusLabel(status)}: $cleanReason" else statusLabel(status)
        val line = "${formatTime(now)} • $safeStamp → $detail"
        updatePinWithHistory(
            pinId = pin.id,
            newHistoryLine = line,
            fallbackHistory = pin.editHistory,
            baseFields = mapOf(
                "status" to status,
                "issueReason" to cleanReason,
                "updatedAt" to now,
                "isEdited" to true,
                "editedBy" to deviceStamp,
                "editedAt" to now
            ),
            onError = onError
        )
        return pin.copy(
            status = status,
            issueReason = cleanReason,
            updatedAt = now,
            isEdited = true,
            editedBy = deviceStamp,
            editedAt = now,
            editHistory = buildHistory(line, pin.editHistory)
        )
    }

    fun updatePinsStatusBatch(
        targetPins: List<PinItem>,
        status: String,
        deviceStamp: String,
        issueReason: String = "",
        onError: ((String) -> Unit)? = null
    ): List<PinItem> {
        if (targetPins.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val cleanReason = if (status.equals("issue", ignoreCase = true) || status.equals("done", ignoreCase = true)) {
            sanitizeHistoryToken(issueReason)
        } else ""
        val safeStamp = sanitizeHistoryToken(deviceStamp)
        val detail = if (cleanReason.isNotBlank()) "${statusLabel(status)}: $cleanReason" else statusLabel(status)
        val line = "${formatTime(now)} • $safeStamp → $detail"
        val updatedPins = ArrayList<PinItem>(targetPins.size)
        val reported = AtomicBoolean(false)

        targetPins.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { pin ->
                val mergedHistory = buildHistory(line, pin.editHistory)
                val ref = db.collection("pins").document(pin.id)
                batch.update(
                    ref,
                    mapOf(
                        "status" to status,
                        "issueReason" to cleanReason,
                        "updatedAt" to now,
                        "isEdited" to true,
                        "editedBy" to deviceStamp,
                        "editedAt" to now,
                        "editHistory" to mergedHistory
                    )
                )
                updatedPins.add(
                    pin.copy(
                        status = status,
                        issueReason = cleanReason,
                        updatedAt = now,
                        isEdited = true,
                        editedBy = deviceStamp,
                        editedAt = now,
                        editHistory = mergedHistory
                    )
                )
            }
            batch.commit().addOnFailureListener { e ->
                if (reported.compareAndSet(false, true)) {
                    onError?.invoke(e.message ?: "Failed to update selected pins")
                }
            }
        }
        return updatedPins
    }

    fun updatePinText(
        pin: PinItem,
        newName: String,
        newDesc: String,
        deviceStamp: String,
        onError: ((String) -> Unit)? = null
    ): PinItem {
        val now = System.currentTimeMillis()
        val resolvedName = newName.ifBlank { pin.name }
        val line = "${formatTime(now)} • ${sanitizeHistoryToken(deviceStamp)} edited text"
        updatePinWithHistory(
            pinId = pin.id,
            newHistoryLine = line,
            fallbackHistory = pin.editHistory,
            baseFields = mapOf(
                "name" to resolvedName,
                "description" to newDesc,
                "updatedAt" to now,
                "isEdited" to true,
                "editedBy" to deviceStamp,
                "editedAt" to now
            ),
            onError = onError
        )
        return pin.copy(
            name = resolvedName,
            description = newDesc,
            updatedAt = now,
            isEdited = true,
            editedBy = deviceStamp,
            editedAt = now,
            editHistory = buildHistory(line, pin.editHistory)
        )
    }

    fun addManualPin(
        name: String,
        description: String,
        lat: Double,
        lng: Double,
        deviceStamp: String,
        deviceId: String,
        onError: ((String) -> Unit)? = null
    ): String {
        val now = System.currentTimeMillis()
        val cleanId = deviceId.filter { it.isLetterOrDigit() }.uppercase().ifBlank { "LOCAL" }
        val layerId = "manual_$cleanId"
        val shareCode = generateShareCode()
        val id = UUID.randomUUID().toString()
        val layerRef = db.collection("layers").document(layerId)
        val pinRef = db.collection("pins").document(id)
        val newLayerData = mapOf(
            "id" to layerId,
            "name" to "My Field Pins",
            "shareCode" to shareCode,
            "createdBy" to deviceStamp,
            "creatorDeviceId" to cleanId,
            "creatorUid" to (currentUid() ?: ""),
            "updatedAt" to now
        )
        val pinData = mapOf(
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
            "editedBy" to "",
            "editedAt" to 0L,
            "editHistory" to ""
        )

        fun commitOfflineFallback() {
            val batch = db.batch()
            if (layerMap.containsKey(layerId)) {
                batch.set(layerRef, mapOf("updatedAt" to now), SetOptions.merge())
            } else {
                batch.set(layerRef, newLayerData, SetOptions.merge())
            }
            batch.set(pinRef, pinData)
            batch.commit().addOnFailureListener { e ->
                onError?.invoke(e.message ?: "Failed to save pin")
            }
        }

        if (!isOnline()) {
            commitOfflineFallback()
            return layerId
        }

        db.runTransaction { tx ->
            val layerSnap = tx.get(layerRef)
            if (layerSnap.exists()) {
                tx.update(layerRef, "updatedAt", now)
            } else {
                tx.set(layerRef, newLayerData)
            }
            tx.set(pinRef, pinData)
            null
        }.addOnFailureListener { e ->
            val code = (e as? FirebaseFirestoreException)?.code
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                commitOfflineFallback()
            } else {
                onError?.invoke(e.message ?: "Failed to save pin")
            }
        }
        return layerId
    }

    fun renameLayer(layerId: String, newName: String, onError: ((String) -> Unit)? = null) {
        val clean = newName.trim()
        if (clean.isBlank()) return
        db.collection("layers").document(layerId).update(
            mapOf(
                "name" to clean,
                "updatedAt" to System.currentTimeMillis()
            )
        ).addOnFailureListener { e ->
            onError?.invoke(e.message ?: "Failed to rename folder")
        }
    }

    private fun recomputeHistoryFields(lines: List<String>, now: Long): Map<String, Any> {
        val newHistory = lines.joinToString("\n")
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

        return mapOf(
            "status" to newStatus,
            "issueReason" to newReason,
            "updatedAt" to now,
            "isEdited" to newIsEdited,
            "editedBy" to newEditedBy,
            "editedAt" to newEditedAt,
            "editHistory" to newHistory
        )
    }

    fun deletePinHistoryEntry(pin: PinItem, lineIndex: Int, onError: ((String) -> Unit)? = null): PinItem {
        val lines = pin.editHistory.lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (lineIndex !in lines.indices) return pin
        val targetLine = lines.removeAt(lineIndex)
        val now = System.currentTimeMillis()
        val optimisticFields = recomputeHistoryFields(lines, now)
        val docRef = db.collection("pins").document(pin.id)

        if (!isOnline()) {
            docRef.update(optimisticFields)
                .addOnFailureListener { e -> onError?.invoke(e.message ?: "Failed to delete edit") }
        } else {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val liveLines = (snap.getString("editHistory") ?: pin.editHistory)
                    .lines().map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                val liveIdx = liveLines.indexOf(targetLine)
                if (liveIdx >= 0) {
                    liveLines.removeAt(liveIdx)
                } else if (lineIndex in liveLines.indices) {
                    liveLines.removeAt(lineIndex)
                }
                tx.update(docRef, recomputeHistoryFields(liveLines, now))
                null
            }.addOnFailureListener { e ->
                val code = (e as? FirebaseFirestoreException)?.code
                if (code == FirebaseFirestoreException.Code.UNAVAILABLE || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                    docRef.update(optimisticFields)
                        .addOnFailureListener { err -> onError?.invoke(err.message ?: "Failed to delete edit") }
                } else {
                    onError?.invoke(e.message ?: "Failed to delete edit")
                }
            }
        }

        return pin.copy(
            status = optimisticFields["status"] as String,
            issueReason = optimisticFields["issueReason"] as String,
            updatedAt = now,
            isEdited = optimisticFields["isEdited"] as Boolean,
            editedBy = optimisticFields["editedBy"] as String,
            editedAt = optimisticFields["editedAt"] as Long,
            editHistory = optimisticFields["editHistory"] as String
        )
    }

    fun resetPinStatusToPending(pin: PinItem, onError: ((String) -> Unit)? = null): PinItem {
        val now = System.currentTimeMillis()
        db.collection("pins").document(pin.id).update(
            mapOf(
                "status" to "pending",
                "issueReason" to "",
                "updatedAt" to now,
                "isEdited" to false,
                "editedBy" to "",
                "editedAt" to 0L,
                "editHistory" to ""
            )
        ).addOnFailureListener { e ->
            onError?.invoke(e.message ?: "Failed to reset pin status")
        }
        return pin.copy(
            status = "pending",
            issueReason = "",
            updatedAt = now,
            isEdited = false,
            editedBy = "",
            editedAt = 0L,
            editHistory = ""
        )
    }

    fun pendingUploadCount(): Int = pendingWritesByLayer.values.sum()

    fun restorePinStatusSnapshot(previousPin: PinItem, onError: ((String) -> Unit)? = null): PinItem {
        val now = System.currentTimeMillis()
        db.collection("pins").document(previousPin.id).update(
            mapOf(
                "status" to previousPin.status,
                "issueReason" to previousPin.issueReason,
                "updatedAt" to now,
                "isEdited" to previousPin.isEdited,
                "editedBy" to previousPin.editedBy,
                "editedAt" to previousPin.editedAt,
                "editHistory" to previousPin.editHistory
            )
        ).addOnFailureListener { e ->
            onError?.invoke(e.message ?: "Failed to restore pin status")
        }
        return previousPin.copy(updatedAt = now)
    }

    fun deletePin(pinId: String, onError: ((String) -> Unit)? = null) {
        db.collection("pins").document(pinId).delete()
            .addOnFailureListener { e -> onError?.invoke(e.message ?: "Failed to delete pin") }
    }

    fun deletePinsBatch(pinIds: Collection<String>, onError: ((String) -> Unit)? = null) {
        if (pinIds.isEmpty()) return
        val reported = AtomicBoolean(false)
        pinIds.distinct().chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id -> batch.delete(db.collection("pins").document(id)) }
            batch.commit().addOnFailureListener { e ->
                if (reported.compareAndSet(false, true)) {
                    onError?.invoke(e.message ?: "Failed to delete selected pins")
                }
            }
        }
    }

    fun deleteLayer(layerId: String, pinIds: List<String>, trackIds: List<String>, onError: ((String) -> Unit)? = null) {
        val refs = mutableListOf(db.collection("layers").document(layerId))
        pinIds.forEach { refs += db.collection("pins").document(it) }
        trackIds.forEach { refs += db.collection("tracks").document(it) }
        val reported = AtomicBoolean(false)
        refs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().addOnFailureListener { e ->
                if (reported.compareAndSet(false, true)) {
                    onError?.invoke(e.message ?: "Failed to delete folder from cloud")
                }
            }
        }
    }

    private fun formatTime(ts: Long): String {
        return SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(ts))
    }
}

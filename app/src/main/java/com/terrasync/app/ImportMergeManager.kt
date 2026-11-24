package com.terrasync.app

import java.util.UUID

enum class ImportDestination {
    LOCAL_ONLY,
    NEW_CLOUD_FOLDER,
    MERGE_EXISTING
}

data class ImportBatchRecord(
    val batchId: String,
    val folderId: String,
    val sourceLabel: String,
    val pinIds: List<String>,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class MergeApplyResult(
    val batchId: String,
    val addedCount: Int,
    val updatedPins: List<PinItem>,
    val batchRecord: ImportBatchRecord
)

object ImportMergeManager {

    private val batchHistory = ArrayDeque<ImportBatchRecord>()

    fun prepareMergedPins(
        existingPins: List<PinItem>,
        incomingPins: List<PinItem>,
        folderId: String,
        sourceLabel: String,
        preserveStatuses: Boolean = true
    ): MergeApplyResult {
        val batchId = "batch_${UUID.randomUUID()}"
        val addedIds = mutableListOf<String>()

        val appendedPins = incomingPins.mapIndexed { idx, rawPin ->
            val freshId = "${batchId}_${idx}_${UUID.randomUUID().toString().take(6)}"
            addedIds.add(freshId)
            rawPin.copy(
                id = freshId,
                layerId = folderId,
                status = if (preserveStatuses) rawPin.status else "pending",
                issueReason = if (preserveStatuses) rawPin.issueReason else ""
            )
        }

        val record = ImportBatchRecord(
            batchId = batchId,
            folderId = folderId,
            sourceLabel = sourceLabel,
            pinIds = addedIds
        )
        batchHistory.addLast(record)
        while (batchHistory.size > 20) batchHistory.removeFirst()

        return MergeApplyResult(
            batchId = batchId,
            addedCount = appendedPins.size,
            updatedPins = existingPins + appendedPins,
            batchRecord = record
        )
    }

    fun lastBatchForFolder(folderId: String): ImportBatchRecord? =
        batchHistory.lastOrNull { it.folderId == folderId && it.pinIds.isNotEmpty() }

    fun undoBatch(currentPins: List<PinItem>, batchId: String): Pair<List<PinItem>, Int> {
        val targetRecord = batchHistory.lastOrNull { it.batchId == batchId }
        val idSet = targetRecord?.pinIds?.toSet().orEmpty()
        val remaining = currentPins.filterNot { it.id in idSet }
        val removedCount = (currentPins.size - remaining.size).coerceAtLeast(0)
        if (targetRecord != null) batchHistory.remove(targetRecord)
        return remaining to removedCount
    }
}

package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.model.LevelDefinition

enum class V6MigrationOperation { KEEP, REPLACE, APPEND }

data class V6MigrationEntry(
    val levelId: String,
    val operation: V6MigrationOperation,
    val oldFingerprint: String?,
    val newFingerprint: String,
)

data class V6PerformanceRecord(
    val boardFingerprint: String,
    val stars: Int,
    val lowestActions: Int?,
    val lowestOverloads: Int?,
    val lowestHints: Int?,
)

data class V6ProgressSnapshot(
    val completedLevelIds: Set<String>,
    val claimedRewardLevelIds: Set<String>,
    val highestUnlockedLevel: Int,
    val currentLevelId: String?,
    val inProgressLevelId: String?,
    val currentRecords: Map<String, V6PerformanceRecord>,
    val legacyRecords: Map<String, List<V6PerformanceRecord>>,
    val coinBalance: Int,
)

data class V6MigrationResult(
    val safe: Boolean,
    val entries: List<V6MigrationEntry>,
    val migrated: V6ProgressSnapshot?,
    val restartedChangedInProgressLevel: Boolean,
    val rejectionReasons: List<String>,
)

object CampaignMigrationV6 {
    fun assess(
        source: List<LevelDefinition>,
        target: List<LevelDefinition>,
        snapshot: V6ProgressSnapshot,
    ): V6MigrationResult {
        val sourceById = source.associateBy { it.id }
        val targetById = target.associateBy { it.id }
        val reasons = mutableListOf<String>()
        if (sourceById.size != source.size) reasons += "SOURCE_DUPLICATE_ID"
        if (targetById.size != target.size) reasons += "TARGET_DUPLICATE_ID"
        if (!sourceById.keys.containsAll(snapshot.completedLevelIds)) reasons += "UNKNOWN_COMPLETION_ID"
        if (!sourceById.keys.containsAll(snapshot.claimedRewardLevelIds)) reasons += "UNKNOWN_REWARD_ID"
        if (!targetById.keys.containsAll(sourceById.keys)) reasons += "TARGET_REMOVES_STABLE_ID"
        if (target.take(source.size).map { it.id } != source.map { it.id }) reasons += "TARGET_REORDERS_STABLE_IDS"
        val entries = target.map { newLevel ->
            val oldLevel = sourceById[newLevel.id]
            val oldFingerprint = oldLevel?.metadata?.contentFingerprint
                ?: oldLevel?.let { com.rameshta.magnetrail.core.content.ContentFingerprint.of(it) }
            val newFingerprint = newLevel.metadata?.contentFingerprint
                ?: com.rameshta.magnetrail.core.content.ContentFingerprint.of(newLevel)
            val operation = when {
                oldLevel == null -> V6MigrationOperation.APPEND
                oldFingerprint == newFingerprint -> V6MigrationOperation.KEEP
                else -> V6MigrationOperation.REPLACE
            }
            if (operation == V6MigrationOperation.REPLACE && newLevel.metadata?.previousContentFingerprint != oldFingerprint) {
                reasons += "REPLACEMENT_PREVIOUS_FINGERPRINT_MISMATCH:${newLevel.id}"
            }
            V6MigrationEntry(newLevel.id, operation, oldFingerprint, newFingerprint)
        }
        if (reasons.isNotEmpty()) return V6MigrationResult(false, entries, null, false, reasons.distinct().sorted())
        val changedIds = entries.filter { it.operation == V6MigrationOperation.REPLACE }.mapTo(hashSetOf()) { it.levelId }
        val currentRecords = snapshot.currentRecords.toMutableMap()
        val legacyRecords = snapshot.legacyRecords.mapValues { it.value.toMutableList() }.toMutableMap()
        changedIds.sorted().forEach { levelId ->
            currentRecords.remove(levelId)?.let { record ->
                legacyRecords.getOrPut(levelId) { mutableListOf() }.add(record)
            }
        }
        val restart = snapshot.inProgressLevelId in changedIds
        val migrated = snapshot.copy(
            completedLevelIds = snapshot.completedLevelIds.toSet(),
            claimedRewardLevelIds = snapshot.claimedRewardLevelIds.toSet(),
            highestUnlockedLevel = snapshot.highestUnlockedLevel.coerceAtMost(target.size),
            currentLevelId = snapshot.currentLevelId?.takeIf { it in targetById },
            inProgressLevelId = snapshot.inProgressLevelId?.takeUnless { it in changedIds },
            currentRecords = currentRecords,
            legacyRecords = legacyRecords.mapValues { it.value.toList() },
            coinBalance = snapshot.coinBalance,
        )
        return V6MigrationResult(true, entries, migrated, restart, emptyList())
    }
}

package com.rameshta.magnetrail.core.generation.v5

import kotlinx.serialization.Serializable

@Serializable
data class LegacyPlayerSnapshotV1(
    val completedLevelIds: Set<String>,
    val bestActionsByLevel: Map<String, Int>,
    val starsByLevel: Map<String, Int>,
    val claimedRewardLevelIds: Set<String>,
    val dailyStateFingerprint: String,
    val settingsFingerprint: String,
    val economyBalance: Int,
)

@Serializable
enum class D2MigrationStatusV1 { SAFE, BLOCKED }

@Serializable
data class D2MigrationAssessmentV1(
    val status: D2MigrationStatusV1,
    val reasonCodes: List<String>,
    val migratedSnapshot: LegacyPlayerSnapshotV1?,
)

/**
 * Offline migration proof helper. It never reads or writes app storage. D2 staging deliberately
 * supplies no mapping, which yields BLOCKED instead of guessing that a new puzzle is equivalent
 * to an old stable ID.
 */
object D2MigrationSafety {
    fun assess(
        snapshot: LegacyPlayerSnapshotV1,
        oldLevelIds: Set<String>,
        newLevelIds: Set<String>,
        explicitlyApprovedIdMap: Map<String, String>?,
    ): D2MigrationAssessmentV1 {
        val reasons = mutableListOf<String>()
        if (!oldLevelIds.containsAll(snapshot.completedLevelIds)) reasons += "UNKNOWN_LEGACY_COMPLETION_ID"
        if (oldLevelIds intersect newLevelIds != emptySet<String>()) reasons += "NEW_CONTENT_REUSES_LEGACY_ID"
        if (explicitlyApprovedIdMap == null) reasons += "MIGRATION_MAPPING_NOT_APPROVED"
        if (explicitlyApprovedIdMap != null) {
            if (explicitlyApprovedIdMap.keys != oldLevelIds) reasons += "MIGRATION_MAPPING_INCOMPLETE"
            if (explicitlyApprovedIdMap.values.toSet().size != explicitlyApprovedIdMap.size) {
                reasons += "MIGRATION_MAPPING_NOT_ONE_TO_ONE"
            }
            if (!newLevelIds.containsAll(explicitlyApprovedIdMap.values)) reasons += "MIGRATION_TARGET_UNKNOWN"
        }
        if (reasons.isNotEmpty()) return D2MigrationAssessmentV1(
            D2MigrationStatusV1.BLOCKED,
            reasons.distinct().sorted(),
            null,
        )
        val mapping = requireNotNull(explicitlyApprovedIdMap)
        fun remap(id: String): String = requireNotNull(mapping[id])
        val migrated = snapshot.copy(
            completedLevelIds = snapshot.completedLevelIds.mapTo(mutableSetOf(), ::remap),
            bestActionsByLevel = snapshot.bestActionsByLevel.mapKeys { remap(it.key) },
            starsByLevel = snapshot.starsByLevel.mapKeys { remap(it.key) },
            claimedRewardLevelIds = snapshot.claimedRewardLevelIds.mapTo(mutableSetOf(), ::remap),
            // Daily identity, settings, and economy are deliberately content-ID independent.
            dailyStateFingerprint = snapshot.dailyStateFingerprint,
            settingsFingerprint = snapshot.settingsFingerprint,
            economyBalance = snapshot.economyBalance,
        )
        return D2MigrationAssessmentV1(D2MigrationStatusV1.SAFE, emptyList(), migrated)
    }
}

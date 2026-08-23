package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.DifficultyBand
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.GradingThresholds
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelMetadata
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationV6Test {
    @Test
    fun `fresh install and partial progress preserve earned state`() {
        val source = listOf(level("one", Direction.NORTH), level("two", Direction.EAST))
        val target = target(source, changed = setOf("two"))
        val fresh = CampaignMigrationV6.assess(source, target, snapshot())
        assertTrue(fresh.safe)
        assertEquals(emptySet<String>(), fresh.migrated?.completedLevelIds)

        val partial = CampaignMigrationV6.assess(
            source,
            target,
            snapshot(completed = setOf("one"), rewards = setOf("one"), balance = 170),
        )
        assertTrue(partial.safe)
        assertEquals(setOf("one"), partial.migrated?.completedLevelIds)
        assertEquals(setOf("one"), partial.migrated?.claimedRewardLevelIds)
        assertEquals(170, partial.migrated?.coinBalance)
    }

    @Test
    fun `complete progress survives replacement and changed records become legacy`() {
        val source = listOf(level("one", Direction.NORTH), level("two", Direction.EAST))
        val target = target(source, changed = setOf("two"))
        val oldFingerprint = requireNotNull(source[1].metadata).contentFingerprint
        val record = V6PerformanceRecord(oldFingerprint, 3, 1, 0, 0)
        val result = CampaignMigrationV6.assess(
            source,
            target,
            snapshot(
                completed = setOf("one", "two"),
                rewards = setOf("one", "two"),
                records = mapOf("two" to record),
            ),
        )
        assertTrue(result.safe)
        assertEquals(setOf("one", "two"), result.migrated?.completedLevelIds)
        assertNull(result.migrated?.currentRecords?.get("two"))
        assertEquals(listOf(record), result.migrated?.legacyRecords?.get("two"))
    }

    @Test
    fun `changed current board restarts without deleting earned value`() {
        val source = listOf(level("one", Direction.NORTH), level("two", Direction.EAST))
        val result = CampaignMigrationV6.assess(
            source,
            target(source, changed = setOf("two")),
            snapshot(completed = setOf("one"), rewards = setOf("one"), current = "two", inProgress = "two"),
        )
        assertTrue(result.safe)
        assertTrue(result.restartedChangedInProgressLevel)
        assertEquals("two", result.migrated?.currentLevelId)
        assertNull(result.migrated?.inProgressLevelId)
        assertEquals(setOf("one"), result.migrated?.completedLevelIds)
    }

    @Test
    fun `mismatched previous fingerprint and downgrade style removal fail closed`() {
        val source = listOf(level("one", Direction.NORTH), level("two", Direction.EAST))
        val changedWithoutLink = level("two", Direction.SOUTH)
        val mismatch = CampaignMigrationV6.assess(source, listOf(source[0], changedWithoutLink), snapshot())
        assertFalse(mismatch.safe)
        assertTrue(mismatch.rejectionReasons.any { it.startsWith("REPLACEMENT_PREVIOUS_FINGERPRINT_MISMATCH") })

        val removal = CampaignMigrationV6.assess(source, listOf(source[0]), snapshot())
        assertFalse(removal.safe)
        assertTrue(removal.rejectionReasons.contains("TARGET_REMOVES_STABLE_ID"))
    }

    private fun target(source: List<LevelDefinition>, changed: Set<String>): List<LevelDefinition> = source.map { old ->
        if (old.id !in changed) old else {
            val raw = old.copy(arrows = old.arrows.map { it.copy(printedDirection = it.printedDirection.opposite()) }, metadata = null)
            raw.copy(metadata = metadata(raw, requireNotNull(old.metadata).contentFingerprint))
        }
    }

    private fun level(id: String, direction: Direction): LevelDefinition {
        val raw = LevelDefinition(
            id, if (id == "one") 1 else 2, id, 2, 2,
            listOf(Arrow("A", Position(1, 1), direction)), emptyList(), emptyList(), listOf(listOf("A")),
        )
        return raw.copy(metadata = metadata(raw, null))
    }

    private fun metadata(level: LevelDefinition, previous: String?) = LevelMetadata(
        contentVersion = if (previous == null) 10 else 12,
        origin = LevelOrigin.GENERATOR_ASSISTED,
        generatorVersion = if (previous == null) 5 else 6,
        generatorSeed = 1,
        generationProfile = if (previous == null) "v5-test" else "v6-bucket-1",
        difficultyBand = DifficultyBand.DEVELOPING,
        certifiedSolutionLength = 1,
        solutionCount = 1,
        solutionCountCapped = false,
        validFirstActionCount = 1,
        exploredStateCount = 1,
        grading = GradingThresholds(1, 3),
        packId = "test",
        mechanicTags = listOf("MOVEMENT"),
        contentFingerprint = ContentFingerprint.of(level),
        previousContentFingerprint = previous,
    )

    private fun snapshot(
        completed: Set<String> = emptySet(),
        rewards: Set<String> = emptySet(),
        balance: Int = 150,
        current: String? = "one",
        inProgress: String? = null,
        records: Map<String, V6PerformanceRecord> = emptyMap(),
    ) = V6ProgressSnapshot(
        completed,
        rewards,
        highestUnlockedLevel = 1,
        currentLevelId = current,
        inProgressLevelId = inProgress,
        currentRecords = records,
        legacyRecords = emptyMap(),
        coinBalance = balance,
    )
}

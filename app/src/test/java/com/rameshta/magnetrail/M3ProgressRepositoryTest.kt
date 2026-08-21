package com.rameshta.magnetrail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.rameshta.magnetrail.core.economy.EconomyConfig
import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.data.AttemptSummary
import com.rameshta.magnetrail.data.DataStoreProgressRepository
import com.rameshta.magnetrail.data.HintSpendResult
import com.rameshta.magnetrail.data.PLAYER_PREFERENCES_SCHEMA_VERSION
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class M3ProgressRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `M2 migration preserves settings progress and moves exactly once`() = runTest {
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = 1
            stored[booleanPreferencesKey("sound_enabled")] = false
            stored[intPreferencesKey("highest_unlocked_level")] = 2
            stored[stringSetPreferencesKey("completed_level_ids")] = setOf("proto-001")
            stored[stringPreferencesKey("last_selected_level_id")] = "proto-002"
            stored[stringSetPreferencesKey("best_moves")] = setOf("proto-001:1")
        }
        val repository = repository(store)

        val first = repository.preferences.first()
        val second = repository.preferences.first()

        assertEquals(PLAYER_PREFERENCES_SCHEMA_VERSION, first.schemaVersion)
        assertFalse(first.settings.soundEnabled)
        assertEquals(setOf("proto-001"), first.progress.completedLevelIds)
        assertEquals("proto-002", first.progress.lastSelectedLevelId)
        assertEquals(1, first.progress.recordsByLevel["proto-001"]?.bestStars)
        assertEquals(1, first.progress.recordsByLevel["proto-001"]?.lowestActions)
        assertTrue("proto-001" in first.progress.firstClearRewardedLevelIds)
        assertEquals(EconomyConfig.STARTING_BALANCE, first.progress.coinBalance)
        assertEquals(first, second)
    }

    @Test
    fun `expanded catalog maps completed M2 level twelve to level thirteen unlock`() = runTest {
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = 1
            stored[intPreferencesKey("highest_unlocked_level")] = 12
            stored[stringSetPreferencesKey("completed_level_ids")] = setOf("proto-012")
            stored[stringPreferencesKey("last_selected_level_id")] = "proto-012"
        }
        val migrated = repository(store, campaignCatalog()).preferences.first()

        assertEquals(13, migrated.progress.highestUnlockedLevel)
        assertEquals("proto-012", migrated.progress.lastSelectedLevelId)
    }

    @Test
    fun `already migrated balance is never reinitialized`() = runTest {
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("coin_balance")] = 77
        }
        val repository = repository(store)

        assertEquals(77, repository.preferences.first().progress.coinBalance)
        assertEquals(77, repository.preferences.first().progress.coinBalance)
    }

    @Test
    fun `infinite selection and completion persist separately and are idempotent`() = runTest {
        val repository = repository(dataStore(this))
        val puzzleId = "infinite-v5-v5-d2-1-hard-6600001-${"a".repeat(64)}"
        val fingerprint = "sha256:${"a".repeat(64)}"

        repository.recordInfiniteSelection(puzzleId, fingerprint, "MASTER", 7)
        val first = repository.recordInfiniteCompletion(puzzleId, AttemptSummary(9, 2, 1))
        val replay = repository.recordInfiniteCompletion(puzzleId, AttemptSummary(12, 4, 2))
        repository.recordInfiniteSelection(puzzleId, fingerprint, "MASTER", 8)
        val nextOrdinal = repository.recordInfiniteCompletion(puzzleId, AttemptSummary(10, 1, 0))
        val progress = repository.preferences.first().progress

        assertTrue(first.firstCompletion)
        assertEquals(10, first.rewards.levelCompletionReward)
        assertEquals(160, first.rewards.resultingBalance)
        assertFalse(replay.firstCompletion)
        assertEquals(0, replay.rewards.total)
        assertEquals(160, replay.rewards.resultingBalance)
        assertEquals(1, replay.completedCount)
        assertTrue(nextOrdinal.firstCompletion)
        assertEquals(10, nextOrdinal.rewards.total)
        assertEquals(2, progress.infinite.completedCount)
        assertEquals(2, progress.infinite.currentStreak)
        assertEquals(puzzleId, progress.infinite.selectedPuzzleId)
        assertEquals("MASTER", progress.infinite.selectedDifficulty)
        assertEquals(8, progress.infinite.selectionOrdinal)
        assertEquals(listOf(9, 10), progress.infinite.history.sortedBy { it.ordinal }.map { it.actions })
        assertEquals(EconomyConfig.STARTING_BALANCE + 2 * EconomyConfig.LEVEL_COMPLETION_REWARD, progress.coinBalance)
        assertTrue(progress.completedLevelIds.isEmpty())
        assertEquals(1, progress.highestUnlockedLevel)
    }

    @Test
    fun `incremental campaign rewards and best records are idempotent`() = runTest {
        val repository = repository(dataStore(this))
        val first = repository.recordCampaignCompletion("proto-001", AttemptSummary(2, 1, 0))
        assertEquals(2, first.grade.stars)
        assertEquals(10, first.rewards.total)
        assertEquals(160, first.rewards.resultingBalance)

        val improved = repository.recordCampaignCompletion("proto-001", AttemptSummary(1, 0, 0))
        assertEquals(3, improved.grade.stars)
        assertEquals(0, improved.rewards.total)
        assertEquals(160, improved.rewards.resultingBalance)

        val replay = repository.recordCampaignCompletion("proto-001", AttemptSummary(3, 2, 1))
        assertEquals(0, replay.rewards.total)
        assertEquals(160, replay.rewards.resultingBalance)
        assertEquals(3, replay.bestRecord.bestStars)
        assertEquals(1, replay.bestRecord.lowestActions)
        assertEquals(0, replay.bestRecord.lowestOverloads)
        assertEquals(0, replay.bestRecord.lowestHints)
    }

    @Test
    fun `concurrent completion cannot double credit`() = runTest {
        val repository = repository(dataStore(this))
        val receipts = listOf(
            async { repository.recordCampaignCompletion("proto-001", AttemptSummary(1, 0, 0)) },
            async { repository.recordCampaignCompletion("proto-001", AttemptSummary(1, 0, 0)) },
        ).awaitAll()

        assertEquals(10, receipts.sumOf { it.rewards.total })
        assertEquals(160, repository.preferences.first().progress.coinBalance)
    }

    @Test
    fun `hint spend is atomic bounded and never negative`() = runTest {
        val repository = repository(dataStore(this))
        repeat(5) { index ->
            val result = repository.spendHintCoins() as HintSpendResult.Approved
            assertEquals(120 - index * 30, result.resultingBalance)
        }
        val insufficient = repository.spendHintCoins() as HintSpendResult.InsufficientBalance
        assertEquals(0, insufficient.balance)
        assertEquals(30, insufficient.required)
        assertEquals(0, repository.preferences.first().progress.coinBalance)
    }

    @Test
    fun `hint affordability boundaries spend exactly thirty and persist`() = runTest {
        suspend fun resultFor(balance: Int): Pair<HintSpendResult, Int> {
            val store = dataStore(this)
            store.edit { it[intPreferencesKey("coin_balance")] = balance }
            val repository = repository(store)
            val result = repository.spendHintCoins()
            return result to repository.preferences.first().progress.coinBalance
        }

        assertTrue(resultFor(0).first is HintSpendResult.InsufficientBalance)
        assertEquals(29, resultFor(29).second)
        assertEquals(0, resultFor(30).second)
        assertEquals(30, resultFor(60).second)
    }

    @Test
    fun `rapid hint requests can deduct one exact balance only once`() = runTest {
        val store = dataStore(this)
        store.edit { it[intPreferencesKey("coin_balance")] = 30 }
        val repository = repository(store)

        val results = listOf(
            async { repository.spendHintCoins() },
            async { repository.spendHintCoins() },
        ).awaitAll()

        assertEquals(1, results.count { it is HintSpendResult.Approved })
        assertEquals(1, results.count { it is HintSpendResult.InsufficientBalance })
        assertEquals(0, repository.preferences.first().progress.coinBalance)
    }

    @Test
    fun `daily rewards and streak handle replay next gap and backward clock`() = runTest {
        val repository = repository(dataStore(this))
        val first = repository.recordDailyCompletion("2026-08-19-v1")
        assertEquals(10, first.rewards.dailyReward)
        assertEquals(1, first.currentStreak)
        val replay = repository.recordDailyCompletion("2026-08-19-v1")
        assertEquals(0, replay.rewards.dailyReward)
        assertEquals(1, replay.currentStreak)
        val next = repository.recordDailyCompletion("2026-08-20-v1")
        assertEquals(2, next.currentStreak)
        val backward = repository.recordDailyCompletion("2026-08-18-v1")
        assertEquals(0, backward.rewards.dailyReward)
        assertEquals(2, backward.currentStreak)
        val gap = repository.recordDailyCompletion("2026-08-23-v1")
        assertEquals(1, gap.currentStreak)
        assertEquals(2, gap.bestStreak)
        assertEquals(180, gap.rewards.resultingBalance)
    }

    @Test
    fun `M5 2 expansion preserves level one to one hundred progress and continues at 101 once`() = runTest {
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 3
            stored[intPreferencesKey("generator_version")] = 1
            stored[intPreferencesKey("highest_unlocked_level")] = 100
            stored[stringSetPreferencesKey("completed_level_ids")] = setOf("campaign-100")
            stored[stringSetPreferencesKey("first_clear_rewarded_ids")] = setOf("campaign-100")
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-100"
            stored[intPreferencesKey("coin_balance")] = 777
            stored[booleanPreferencesKey("sound_enabled")] = false
            stored[stringSetPreferencesKey("completed_daily_ids")] = setOf("2026-08-18-v1")
            stored[intPreferencesKey("interstitial_eligible_completions")] = 4
        }
        val repository = repository(store, expanded150Catalog())

        val first = repository.preferences.first().progress
        val second = repository.preferences.first().progress

        assertEquals(101, first.highestUnlockedLevel)
        assertEquals("campaign-101", first.lastSelectedLevelId)
        assertEquals(setOf("campaign-100"), first.completedLevelIds)
        assertEquals(setOf("campaign-100"), first.firstClearRewardedLevelIds)
        assertEquals(777, first.coinBalance)
        assertEquals(4, first.contentVersion)
        assertEquals(2, first.generatorVersion)
        assertEquals(setOf("2026-08-18-v1"), first.completedDailyIds)
        assertEquals(4, first.monetization.interstitialEligibleCompletions)
        assertFalse(repository.preferences.first().settings.soundEnabled)
        assertEquals(first, second)
    }

    @Test
    fun `M5 2 fresh install starts at level one with expansion versions`() = runTest {
        val progress = repository(dataStore(this), expanded150Catalog()).preferences.first().progress

        assertEquals(1, progress.highestUnlockedLevel)
        assertEquals(campaignCatalog().levels.first().id, progress.lastSelectedLevelId)
        assertTrue(progress.completedLevelIds.isEmpty())
        assertTrue(progress.recordsByLevel.isEmpty())
        assertEquals(4, progress.contentVersion)
        assertEquals(2, progress.generatorVersion)
    }

    @Test
    fun `M5 2 full three star history through 100 is retained without new rewards`() = runTest {
        val originalIds = campaignCatalog().levels.take(100).map { it.id }.toSet()
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 3
            stored[intPreferencesKey("generator_version")] = 1
            stored[intPreferencesKey("highest_unlocked_level")] = 100
            stored[stringSetPreferencesKey("completed_level_ids")] = originalIds
            stored[stringSetPreferencesKey("first_clear_rewarded_ids")] = originalIds
            stored[stringSetPreferencesKey("level_records_v3")] = originalIds.mapTo(linkedSetOf()) { "$it|3|1|0|0" }
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-100"
            stored[intPreferencesKey("coin_balance")] = 9_999
        }

        val progress = repository(store, expanded150Catalog()).preferences.first().progress
        assertEquals(101, progress.highestUnlockedLevel)
        assertEquals("campaign-101", progress.lastSelectedLevelId)
        assertEquals(originalIds, progress.completedLevelIds)
        assertEquals(originalIds, progress.firstClearRewardedLevelIds)
        assertEquals(100, progress.recordsByLevel.size)
        assertTrue(progress.recordsByLevel.values.all { it.bestStars == 3 })
        val newIds = (101..150).mapTo(mutableSetOf()) { "campaign-$it" }
        assertTrue(progress.recordsByLevel.keys.intersect(newIds).isEmpty())
        assertEquals(9_999, progress.coinBalance)
    }

    @Test
    fun `M5 2 expansion keeps below 100 and incomplete level 100 selections unchanged`() = runTest {
        val belowStore = dataStore(this)
        belowStore.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 3
            stored[intPreferencesKey("highest_unlocked_level")] = 50
            stored[stringSetPreferencesKey("completed_level_ids")] = setOf("campaign-049")
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-050"
        }
        val below = repository(belowStore, expanded150Catalog()).preferences.first().progress
        assertEquals(50, below.highestUnlockedLevel)
        assertEquals("campaign-050", below.lastSelectedLevelId)

        val finalStore = dataStore(this)
        finalStore.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 3
            stored[intPreferencesKey("highest_unlocked_level")] = 100
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-100"
        }
        val incomplete = repository(finalStore, expanded150Catalog()).preferences.first().progress
        assertEquals(100, incomplete.highestUnlockedLevel)
        assertEquals("campaign-100", incomplete.lastSelectedLevelId)
    }

    @Test
    fun `M5 2 completed level 100 unlocks 101 while retaining an earlier selection`() = runTest {
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 3
            stored[intPreferencesKey("highest_unlocked_level")] = 100
            stored[stringSetPreferencesKey("completed_level_ids")] = setOf("campaign-100")
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-050"
        }
        val repository = repository(store, expanded150Catalog())
        val migrated = repository.preferences.first().progress

        assertEquals(101, migrated.highestUnlockedLevel)
        assertEquals("campaign-050", migrated.lastSelectedLevelId)
        repository.selectLevel("campaign-101")
        assertEquals("campaign-101", repository.preferences.first().progress.lastSelectedLevelId)
    }

    @Test
    fun `completing level 150 clamps progression and cannot duplicate first clear reward`() = runTest {
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 4
            stored[intPreferencesKey("generator_version")] = 2
            stored[intPreferencesKey("highest_unlocked_level")] = 150
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-150"
        }
        val repository = repository(store, expanded150Catalog())
        val first = repository.recordCampaignCompletion("campaign-150", AttemptSummary(6, 0, 0))
        val replay = repository.recordCampaignCompletion("campaign-150", AttemptSummary(6, 0, 0))

        assertTrue(first.rewards.total > 0)
        assertEquals(0, replay.rewards.total)
        assertEquals(150, repository.preferences.first().progress.highestUnlockedLevel)
    }

    @Test
    fun `Phase 1 completed level 150 unlocks 151 exactly once and preserves player state`() = runTest {
        val catalog = campaignCatalog()
        assertEquals(2_205, catalog.levels.size)
        val level150 = catalog.levels.single { it.number == 150 }
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 5
            stored[intPreferencesKey("generator_version")] = 3
            stored[intPreferencesKey("highest_unlocked_level")] = 150
            stored[stringSetPreferencesKey("completed_level_ids")] = setOf(level150.id)
            stored[stringSetPreferencesKey("first_clear_rewarded_ids")] = setOf(level150.id)
            stored[stringPreferencesKey("last_selected_level_id")] = level150.id
            stored[intPreferencesKey("coin_balance")] = 913
            stored[booleanPreferencesKey("sound_enabled")] = false
        }

        var repository = repository(store, catalog)
        val first = repository.preferences.first().progress
        val second = repository.preferences.first().progress

        assertEquals(9, first.contentVersion)
        assertEquals(5, first.generatorVersion)
        assertEquals(151, first.highestUnlockedLevel)
        assertEquals("campaign-151", first.lastSelectedLevelId)
        assertEquals(setOf(level150.id), first.completedLevelIds)
        assertEquals(setOf(level150.id), first.firstClearRewardedLevelIds)
        assertEquals(913, first.coinBalance)
        assertEquals(first, second)

        repository = repository(store, catalog)
        val restarted = repository.preferences.first()
        assertEquals(151, restarted.progress.highestUnlockedLevel)
        assertEquals("campaign-151", restarted.progress.lastSelectedLevelId)
        assertFalse(restarted.settings.soundEnabled)
        val replay = repository.recordCampaignCompletion(level150.id, AttemptSummary(7, 0, 0))
        assertEquals(0, replay.rewards.firstClearReward)
        assertEquals(913 + replay.rewards.newStarReward, replay.rewards.resultingBalance)
    }

    @Test
    fun `Phase 1 level 150 must be complete before 151 unlocks`() = runTest {
        val catalog = campaignCatalog()
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 5
            stored[intPreferencesKey("generator_version")] = 3
            stored[intPreferencesKey("highest_unlocked_level")] = 150
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-150"
        }

        val progress = repository(store, catalog).preferences.first().progress

        assertEquals(150, progress.highestUnlockedLevel)
        assertEquals("campaign-150", progress.lastSelectedLevelId)
    }

    @Test
    fun `completing level 200 unlocks appended level 201 exactly once`() = runTest {
        val catalog = campaignCatalog()
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 6
            stored[intPreferencesKey("generator_version")] = 4
            stored[intPreferencesKey("highest_unlocked_level")] = 200
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-200"
        }
        val repository = repository(store, catalog)

        val first = repository.recordCampaignCompletion("campaign-200", AttemptSummary(7, 0, 0))
        val replay = repository.recordCampaignCompletion("campaign-200", AttemptSummary(7, 0, 0))

        assertTrue(first.rewards.total > 0)
        assertEquals(0, replay.rewards.total)
        assertEquals(201, repository.preferences.first().progress.highestUnlockedLevel)
        assertEquals("campaign-200", repository.preferences.first().progress.lastSelectedLevelId)
    }

    @Test
    fun `content v8 migration moves a completed level 200 player to 201`() = runTest {
        val catalog = campaignCatalog()
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 7
            stored[intPreferencesKey("generator_version")] = 5
            stored[intPreferencesKey("highest_unlocked_level")] = 200
            stored[stringSetPreferencesKey("completed_level_ids")] = setOf("campaign-200")
            stored[stringSetPreferencesKey("first_clear_rewarded_ids")] = setOf("campaign-200")
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-200"
            stored[intPreferencesKey("coin_balance")] = 913
        }

        val first = repository(store, catalog).preferences.first().progress

        assertEquals(9, first.contentVersion)
        assertEquals(201, first.highestUnlockedLevel)
        assertEquals("campaign-201", first.lastSelectedLevelId)
        assertEquals(setOf("campaign-200"), first.completedLevelIds)
        assertEquals(setOf("campaign-200"), first.firstClearRewardedLevelIds)
        assertEquals(913, first.coinBalance)
    }

    @Test
    fun `content v9 migration moves a completed level 205 player to 206`() = runTest {
        val catalog = campaignCatalog()
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 8
            stored[intPreferencesKey("generator_version")] = 5
            stored[intPreferencesKey("highest_unlocked_level")] = 205
            stored[stringSetPreferencesKey("completed_level_ids")] = setOf("campaign-205")
            stored[stringSetPreferencesKey("first_clear_rewarded_ids")] = setOf("campaign-205")
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-205"
        }

        val progress = repository(store, catalog).preferences.first().progress

        assertEquals(9, progress.contentVersion)
        assertEquals(206, progress.highestUnlockedLevel)
        assertEquals("campaign-206", progress.lastSelectedLevelId)
        assertEquals(setOf("campaign-205"), progress.completedLevelIds)
        assertEquals(setOf("campaign-205"), progress.firstClearRewardedLevelIds)
    }

    @Test
    fun `Phase 0 board migration archives incomparable records and preserves earned value`() = runTest {
        val oldCatalog = expanded150Catalog()
        val oldLevel = oldCatalog.levels.first()
        val proposed = LevelParser().parseCatalog(
            checkNotNull(javaClass.getResource("/development/PHASE0_PROPOSED_CAMPAIGN_NOT_PROMOTED.json")).readText(),
        ).levels.first()
        val oldFingerprint = ContentFingerprint.exact(oldLevel)
        val newFingerprint = ContentFingerprint.exact(proposed)
        val migratedLevel = proposed.copy(
            metadata = requireNotNull(oldLevel.metadata).copy(
                contentVersion = 5,
                contentFingerprint = newFingerprint,
                previousContentFingerprint = oldFingerprint,
            ),
        )
        val migratedCatalog = LevelCatalog(
            schemaVersion = 2,
            ruleVersion = oldCatalog.ruleVersion,
            catalogId = "phase0-migration-test",
            levels = listOf(migratedLevel),
            contentVersion = 5,
            generatorVersion = 3,
        )
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = 5
            stored[intPreferencesKey("content_version")] = 4
            stored[intPreferencesKey("generator_version")] = 2
            stored[intPreferencesKey("highest_unlocked_level")] = 1
            stored[stringSetPreferencesKey("completed_level_ids")] = setOf(oldLevel.id)
            stored[stringSetPreferencesKey("first_clear_rewarded_ids")] = setOf(oldLevel.id)
            stored[stringPreferencesKey("last_selected_level_id")] = oldLevel.id
            stored[stringSetPreferencesKey("level_records_v3")] = setOf("${oldLevel.id}|3|1|0|0")
            stored[stringSetPreferencesKey("best_moves")] = setOf("${oldLevel.id}:1")
            stored[intPreferencesKey("coin_balance")] = 777
            stored[booleanPreferencesKey("sound_enabled")] = false
        }

        var repository = repository(store, migratedCatalog)
        val first = repository.preferences.first()
        val second = repository.preferences.first()
        val migrated = requireNotNull(first.progress.recordsByLevel[oldLevel.id])

        assertEquals(PLAYER_PREFERENCES_SCHEMA_VERSION, first.schemaVersion)
        assertEquals(5, first.progress.contentVersion)
        assertEquals(3, first.progress.generatorVersion)
        assertEquals(setOf(oldLevel.id), first.progress.completedLevelIds)
        assertEquals(setOf(oldLevel.id), first.progress.firstClearRewardedLevelIds)
        assertEquals(777, first.progress.coinBalance)
        assertFalse(first.settings.soundEnabled)
        assertEquals(3, migrated.bestStars)
        assertEquals(newFingerprint, migrated.boardFingerprint)
        assertEquals(null, migrated.lowestActions)
        assertTrue(first.progress.bestMovesByLevel.isEmpty())
        assertEquals(1, migrated.legacyRecords.size)
        assertEquals(oldFingerprint, migrated.legacyRecords.single().boardFingerprint)
        assertEquals(4, migrated.legacyRecords.single().sourceContentVersion)
        assertEquals(1, migrated.legacyRecords.single().lowestActions)
        assertEquals(first, second)

        val replay = repository.recordCampaignCompletion(oldLevel.id, AttemptSummary(1, 0, 0))
        assertEquals(0, replay.rewards.total)
        assertEquals(777, replay.rewards.resultingBalance)
        assertEquals(1, replay.bestRecord.lowestActions)
        assertEquals(1, replay.bestRecord.legacyRecords.size)

        repository = repository(store, migratedCatalog)
        val restarted = requireNotNull(repository.preferences.first().progress.recordsByLevel[oldLevel.id])
        assertEquals(newFingerprint, restarted.boardFingerprint)
        assertEquals(1, restarted.lowestActions)
        assertEquals(1, restarted.legacyRecords.size)
        assertEquals(oldFingerprint, restarted.legacyRecords.single().boardFingerprint)
    }

    @Test
    fun `D2 full campaign board revision preserves player value and archives all old performance`() = runTest {
        val oldCatalog = LevelParser().parseCatalog(
            checkNotNull(javaClass.getResource("/content/d2/promotion/D2_SOURCE_CONTENT_V6.json")).readText(),
        )
        val promotedCatalog = campaignCatalog()
        assertEquals(oldCatalog.levels.map { it.id }, promotedCatalog.levels.take(200).map { it.id })
        val oldIds = oldCatalog.levels.mapTo(linkedSetOf()) { it.id }
        val oldFingerprints = oldCatalog.levels.associate { it.id to ContentFingerprint.exact(it) }
        val store = dataStore(this)
        store.edit { stored ->
            stored[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            stored[intPreferencesKey("content_version")] = 6
            stored[intPreferencesKey("generator_version")] = 4
            stored[intPreferencesKey("highest_unlocked_level")] = 200
            stored[stringSetPreferencesKey("completed_level_ids")] = oldIds
            stored[stringSetPreferencesKey("first_clear_rewarded_ids")] = oldIds
            stored[stringPreferencesKey("last_selected_level_id")] = "campaign-200"
            stored[stringSetPreferencesKey("level_records_v3")] = oldIds.mapTo(linkedSetOf()) { "$it|3|5|1|1" }
            stored[stringSetPreferencesKey("level_record_fingerprints_v6")] = oldFingerprints
                .mapTo(linkedSetOf()) { (id, fingerprint) -> "$id|$fingerprint" }
            stored[stringSetPreferencesKey("best_moves")] = oldIds.mapTo(linkedSetOf()) { "$it:5" }
            stored[intPreferencesKey("coin_balance")] = 12_345
            stored[booleanPreferencesKey("sound_enabled")] = false
            stored[booleanPreferencesKey("high_contrast_fields")] = true
            stored[stringSetPreferencesKey("completed_daily_ids")] = setOf("2026-08-18-v1")
            stored[stringSetPreferencesKey("rewarded_daily_ids")] = setOf("2026-08-18-v1")
            stored[intPreferencesKey("current_daily_streak")] = 7
            stored[intPreferencesKey("best_daily_streak")] = 12
            stored[stringPreferencesKey("last_trusted_daily_date")] = "2026-08-18"
            stored[intPreferencesKey("interstitial_eligible_completions")] = 3
            stored[intPreferencesKey("interstitials_shown_on_date")] = 2
            stored[stringPreferencesKey("last_full_screen_ad_date")] = "2026-08-18"
            stored[stringPreferencesKey("rewarded_grant_date")] = "2026-08-18"
            stored[intPreferencesKey("rewarded_grants_on_date")] = 2
        }

        var repository = repository(store, promotedCatalog)
        val first = repository.preferences.first()
        val second = repository.preferences.first()
        val progress = first.progress

        assertEquals(9, progress.contentVersion)
        assertEquals(5, progress.generatorVersion)
        assertEquals(201, progress.highestUnlockedLevel)
        assertEquals("campaign-201", progress.lastSelectedLevelId)
        assertEquals(oldIds, progress.completedLevelIds)
        assertEquals(oldIds, progress.firstClearRewardedLevelIds)
        assertEquals(12_345, progress.coinBalance)
        assertTrue(progress.bestMovesByLevel.isEmpty())
        assertEquals(200, progress.recordsByLevel.size)
        progress.recordsByLevel.forEach { (id, record) ->
            assertEquals(3, record.bestStars)
            assertEquals(null, record.lowestActions)
            assertEquals(ContentFingerprint.exact(promotedCatalog.levels.single { it.id == id }), record.boardFingerprint)
            assertEquals(1, record.legacyRecords.size)
            assertEquals(oldFingerprints.getValue(id), record.legacyRecords.single().boardFingerprint)
            assertEquals(6, record.legacyRecords.single().sourceContentVersion)
            assertEquals(5, record.legacyRecords.single().lowestActions)
        }
        assertEquals(setOf("2026-08-18-v1"), progress.completedDailyIds)
        assertEquals(setOf("2026-08-18-v1"), progress.rewardedDailyIds)
        assertEquals(7, progress.currentStreak)
        assertEquals(12, progress.bestStreak)
        assertFalse(first.settings.soundEnabled)
        assertTrue(first.settings.highContrastFields)
        assertEquals(3, progress.monetization.interstitialEligibleCompletions)
        assertEquals(2, progress.monetization.interstitialsShownOnDate)
        assertEquals(2, progress.monetization.rewardedGrantsOnDate)
        assertEquals(first, second)

        val replay = repository.recordCampaignCompletion("proto-001", AttemptSummary(5, 0, 0))
        assertEquals(0, replay.rewards.total)
        assertEquals(12_345, replay.rewards.resultingBalance)
        repository = repository(store, promotedCatalog)
        val restarted = repository.preferences.first().progress
        assertEquals(1, restarted.recordsByLevel.getValue("proto-001").legacyRecords.size)
        assertEquals(5, restarted.recordsByLevel.getValue("proto-001").lowestActions)
        assertEquals(12_345, restarted.coinBalance)
    }

    private fun dataStore(scope: TestScope): DataStore<Preferences> {
        val target = File(temporaryFolder.newFolder(), "player.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { target }
    }

    private fun repository(
        store: DataStore<Preferences>,
        catalog: LevelCatalog = prototypeCatalog(),
    ) = DataStoreProgressRepository(
        dataStore = store,
        catalog = catalog,
        defaultReducedMotion = false,
        testMarker = Unit,
    )

    private fun campaignCatalog(): LevelCatalog = LevelParser().parseCatalog(
        checkNotNull(javaClass.getResource("/Magnetrail_Campaign_Levels_v3.json")).readText(),
    )

    private fun expanded150Catalog(): LevelCatalog {
        return LevelParser().parseCatalog(
            checkNotNull(javaClass.getResource("/content/m5_2_review_catalog.json")).readText(),
        )
    }
}

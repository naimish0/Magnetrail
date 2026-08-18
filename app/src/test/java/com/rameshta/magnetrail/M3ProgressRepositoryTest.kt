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
    fun `incremental campaign rewards and best records are idempotent`() = runTest {
        val repository = repository(dataStore(this))
        val first = repository.recordCampaignCompletion("proto-001", AttemptSummary(2, 1, 0))
        assertEquals(2, first.grade.stars)
        assertEquals(30, first.rewards.total)
        assertEquals(180, first.rewards.resultingBalance)

        val improved = repository.recordCampaignCompletion("proto-001", AttemptSummary(1, 0, 0))
        assertEquals(3, improved.grade.stars)
        assertEquals(5, improved.rewards.total)
        assertEquals(185, improved.rewards.resultingBalance)

        val replay = repository.recordCampaignCompletion("proto-001", AttemptSummary(3, 2, 1))
        assertEquals(0, replay.rewards.total)
        assertEquals(185, replay.rewards.resultingBalance)
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

        assertEquals(35, receipts.sumOf { it.rewards.total })
        assertEquals(185, repository.preferences.first().progress.coinBalance)
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
    fun `daily rewards and streak handle replay next gap and backward clock`() = runTest {
        val repository = repository(dataStore(this))
        val first = repository.recordDailyCompletion("2026-08-19-v1")
        assertEquals(50, first.rewards.dailyReward)
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
        assertEquals(300, gap.rewards.resultingBalance)
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
}

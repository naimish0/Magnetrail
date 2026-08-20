package com.rameshta.magnetrail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.rameshta.magnetrail.data.DataStoreProgressRepository
import com.rameshta.magnetrail.data.PLAYER_PREFERENCES_SCHEMA_VERSION
import com.rameshta.magnetrail.data.RewardedCreditGrantResult
import com.rameshta.magnetrail.data.RewardedSkipResult
import com.rameshta.magnetrail.data.RewardedSkipTarget
import com.rameshta.magnetrail.ads.RewardedCallbackLedger
import com.rameshta.magnetrail.ads.RewardedOutcome
import com.rameshta.magnetrail.game.GameAction
import com.rameshta.magnetrail.game.GameViewModel
import com.rameshta.magnetrail.game.HintOutcome
import com.rameshta.magnetrail.game.HintProvider
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class M4RewardedAndMigrationTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `reward callback is explicit and duplicate callbacks grant one outcome`() {
        val dismissedEarly = RewardedCallbackLedger("early")
        assertEquals(RewardedOutcome.DismissedWithoutReward, dismissedEarly.dismiss())

        val earned = RewardedCallbackLedger("earned")
        assertTrue(earned.rewardCallback())
        assertFalse(earned.rewardCallback())
        assertEquals(RewardedOutcome.Earned("earned"), earned.dismiss())
    }

    @Test
    fun `M3 migration preserves economy campaign daily and settings and is idempotent`() = runTest {
        val store = dataStore(this)
        store.edit { values ->
            values[intPreferencesKey("schema_version")] = 3
            values[booleanPreferencesKey("sound_enabled")] = false
            values[intPreferencesKey("highest_unlocked_level")] = 2
            values[stringSetPreferencesKey("completed_level_ids")] = setOf("proto-001")
            values[stringPreferencesKey("last_selected_level_id")] = "proto-002"
            values[stringSetPreferencesKey("level_records_v3")] = setOf("proto-001|3|1|0|0")
            values[stringSetPreferencesKey("first_clear_rewarded_ids")] = setOf("proto-001")
            values[intPreferencesKey("coin_balance")] = 211
            values[stringSetPreferencesKey("completed_daily_ids")] = setOf("2026-08-18-v1")
            values[intPreferencesKey("current_daily_streak")] = 2
            values[intPreferencesKey("best_daily_streak")] = 4
            values[stringPreferencesKey("last_trusted_daily_date")] = "2026-08-18"
        }
        val repository = repository(store)
        val first = repository.preferences.first()
        val second = repository.preferences.first()

        assertEquals(PLAYER_PREFERENCES_SCHEMA_VERSION, first.schemaVersion)
        assertFalse(first.settings.soundEnabled)
        assertEquals(211, first.progress.coinBalance)
        assertEquals(3, first.progress.recordsByLevel["proto-001"]?.bestStars)
        assertEquals(2, first.progress.currentStreak)
        assertEquals(4, first.progress.bestStreak)
        assertEquals(first, second)
    }

    @Test
    fun `reward transactions are durable idempotent capped and one-credit only`() = runTest {
        val store = dataStore(this)
        var repository = repository(store)
        val date = LocalDate.of(2026, 8, 19)

        assertEquals(RewardedCreditGrantResult.Granted, repository.grantRewardedHintCredit("txn-1", date))
        assertEquals(RewardedCreditGrantResult.Duplicate, repository.grantRewardedHintCredit("txn-1", date))
        assertEquals(RewardedCreditGrantResult.InventoryFull, repository.grantRewardedHintCredit("txn-2", date))
        repository = repository(store)
        assertEquals("txn-1", repository.preferences.first().progress.monetization.pendingAdHintTransactionId)
        assertTrue(repository.consumeRewardedHintCredit("txn-1"))
        assertFalse(repository.consumeRewardedHintCredit("txn-1"))

        for (index in 2..5) {
            assertEquals(
                RewardedCreditGrantResult.Granted,
                repository.grantRewardedHintCredit("txn-$index", date),
            )
            assertTrue(repository.consumeRewardedHintCredit("txn-$index"))
        }
        assertEquals(
            RewardedCreditGrantResult.DailyCapReached,
            repository.grantRewardedHintCredit("txn-6", date),
        )
        assertEquals(
            RewardedCreditGrantResult.DateRollback,
            repository.grantRewardedHintCredit("rollback", date.minusDays(1)),
        )
    }

    @Test
    fun `rewarded campaign skip atomically advances and grants ten coins once`() = runTest {
        val store = dataStore(this)
        val repository = repository(store)
        val before = repository.preferences.first().progress.coinBalance

        val applied = repository.recordRewardedSkip(
            "skip-campaign-1",
            RewardedSkipTarget.Campaign("proto-001"),
        ) as RewardedSkipResult.Applied

        assertEquals(10, applied.grantedCoins)
        assertEquals(before + 10, applied.resultingBalance)
        var progress = repository.preferences.first().progress
        assertTrue("proto-001" in progress.completedLevelIds)
        assertTrue("proto-001" in progress.firstClearRewardedLevelIds)
        assertEquals(2, progress.highestUnlockedLevel)
        assertEquals(before + 10, progress.coinBalance)

        assertEquals(
            RewardedSkipResult.Duplicate,
            repository.recordRewardedSkip(
                "skip-campaign-1",
                RewardedSkipTarget.Campaign("proto-001"),
            ),
        )
        progress = repository.preferences.first().progress
        assertEquals(before + 10, progress.coinBalance)
    }

    @Test
    fun `verified campaign skip receipt opens the next level`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository(dataStore(this))
        val viewModel = GameViewModel(
            catalog = prototypeCatalog(),
            progressRepository = repository,
        )
        advanceUntilIdle()
        val receipt = repository.recordRewardedSkip(
            "skip-campaign-navigation",
            RewardedSkipTarget.Campaign("proto-001"),
        ) as RewardedSkipResult.Applied

        viewModel.onAction(GameAction.ApplyRewardedSkip(receipt))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.currentLevelIndex)
        assertEquals("proto-002", viewModel.uiState.value.currentLevel.id)
        assertEquals(receipt.resultingBalance, viewModel.uiState.value.progress.coinBalance)
    }

    @Test
    fun `rewarded infinite skip closes current identity and grants ten coins once`() = runTest {
        val store = dataStore(this)
        val repository = repository(store)
        val before = repository.preferences.first().progress.coinBalance
        val puzzleId = "infinite-v5-skip-test"
        repository.recordInfiniteSelection(
            puzzleId = puzzleId,
            contentFingerprint = "sha256:" + "0".repeat(64),
            difficulty = "PROGRESSIVE",
            ordinal = 0,
        )

        val applied = repository.recordRewardedSkip(
            "skip-infinite-1",
            RewardedSkipTarget.Infinite(puzzleId),
        ) as RewardedSkipResult.Applied

        assertEquals(10, applied.grantedCoins)
        assertEquals(1, applied.completedCount)
        assertEquals(0, applied.currentStreak)
        val progress = repository.preferences.first().progress
        assertEquals(before + 10, progress.coinBalance)
        assertTrue(progress.infinite.history.single().completed)
        assertEquals(
            RewardedSkipResult.Duplicate,
            repository.recordRewardedSkip(
                "skip-infinite-1",
                RewardedSkipTarget.Infinite(puzzleId),
            ),
        )
        assertEquals(before + 10, repository.preferences.first().progress.coinBalance)
    }

    @Test
    fun `solver failure preserves rewarded credit`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository(dataStore(this))
        repository.grantRewardedHintCredit("txn-safe", LocalDate.of(2026, 8, 19))
        val viewModel = GameViewModel(
            catalog = prototypeCatalog(),
            progressRepository = repository,
            hintProvider = HintProvider { HintOutcome.NoSolution },
        )
        advanceUntilIdle()

        viewModel.onAction(GameAction.UseRewardedHintCredit("txn-safe"))
        advanceUntilIdle()

        assertEquals("txn-safe", repository.preferences.first().progress.monetization.pendingAdHintTransactionId)
        assertNull(viewModel.uiState.value.suggestedArrowId)
        assertEquals(0, viewModel.uiState.value.hintsUsed)
    }

    @Test
    fun `successful rewarded hint consumes credit and charges no coins`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = repository(dataStore(this))
        repository.grantRewardedHintCredit("txn-safe", LocalDate.of(2026, 8, 19))
        val before = repository.preferences.first().progress.coinBalance
        val viewModel = GameViewModel(
            catalog = prototypeCatalog(),
            progressRepository = repository,
            hintProvider = HintProvider { HintOutcome.SuggestedArrow("A") },
        )
        advanceUntilIdle()

        viewModel.onAction(GameAction.UseRewardedHintCredit("txn-safe"))
        advanceUntilIdle()

        val progress = repository.preferences.first().progress
        assertNull(progress.monetization.pendingAdHintTransactionId)
        assertEquals(before, progress.coinBalance)
        assertEquals("A", viewModel.uiState.value.suggestedArrowId)
        assertEquals(1, viewModel.uiState.value.hintsUsed)
    }

    @Test
    fun `fresh and partial corrupt M4 data recover conservatively`() = runTest {
        val store = dataStore(this)
        var restored = repository(store).preferences.first()
        assertEquals(PLAYER_PREFERENCES_SCHEMA_VERSION, restored.schemaVersion)
        assertFalse(restored.settings.diagnosticsEnabled)

        store.edit { values ->
            values[intPreferencesKey("schema_version")] = 4
            values[intPreferencesKey("rewarded_grants_on_date")] = 99
            values[intPreferencesKey("interstitials_shown_on_date")] = 99
            values[stringPreferencesKey("pending_ad_hint_transaction_id")] = ""
            values[stringPreferencesKey("rewarded_grant_date")] = "corrupt"
            values[stringPreferencesKey("last_full_screen_ad_date")] = "corrupt"
        }
        restored = repository(store).preferences.first()
        assertEquals(5, restored.progress.monetization.rewardedGrantsOnDate)
        assertEquals(4, restored.progress.monetization.interstitialsShownOnDate)
        assertNull(restored.progress.monetization.pendingAdHintTransactionId)
        assertEquals(LocalDate.MAX.toString(), restored.progress.monetization.rewardedGrantDate)
        assertEquals(LocalDate.MAX.toString(), restored.progress.monetization.lastFullScreenAdDate)
        assertEquals(
            RewardedCreditGrantResult.DateRollback,
            repository(store).grantRewardedHintCredit("blocked", LocalDate.of(2026, 8, 19)),
        )
    }

    private fun dataStore(scope: TestScope): DataStore<Preferences> {
        val target = File(temporaryFolder.newFolder(), "player.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { target }
    }

    private fun repository(store: DataStore<Preferences>) = DataStoreProgressRepository(
        dataStore = store,
        catalog = prototypeCatalog(),
        defaultReducedMotion = false,
        testMarker = Unit,
    )
}

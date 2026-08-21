package com.rameshta.magnetrail.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rameshta.magnetrail.core.daily.DailySeed
import com.rameshta.magnetrail.core.daily.StreakPolicy
import com.rameshta.magnetrail.core.daily.StreakState
import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.economy.EconomyConfig
import com.rameshta.magnetrail.core.economy.RewardPolicy
import com.rameshta.magnetrail.core.generation.GENERATOR_VERSION
import com.rameshta.magnetrail.core.grading.GradingPolicy
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.model.GradingThresholds
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import com.rameshta.magnetrail.crash.CrashReporter
import com.rameshta.magnetrail.crash.NoOpCrashReporter

internal val playerDataStoreCorruptionHandler = ReplaceFileCorruptionHandler<Preferences> {
    emptyPreferences()
}

private val Context.magnetrailDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "magnetrail_player_v1",
    corruptionHandler = playerDataStoreCorruptionHandler,
)

class DataStoreProgressRepository private constructor(
    private val dataStore: DataStore<Preferences>,
    private val catalog: LevelCatalog,
    private val defaultReducedMotion: Boolean,
    private val crashReporter: CrashReporter,
) : ProgressRepository {
    constructor(
        context: Context,
        catalog: LevelCatalog,
        defaultReducedMotion: Boolean,
        crashReporter: CrashReporter = NoOpCrashReporter,
    ) : this(
        dataStore = context.applicationContext.magnetrailDataStore,
        catalog = catalog,
        defaultReducedMotion = defaultReducedMotion,
        crashReporter = crashReporter,
    )

    internal constructor(
        dataStore: DataStore<Preferences>,
        catalog: LevelCatalog,
        defaultReducedMotion: Boolean,
        testMarker: Unit = Unit,
        crashReporter: CrashReporter = NoOpCrashReporter,
    ) : this(dataStore, catalog, defaultReducedMotion, crashReporter)

    override val preferences: Flow<PlayerPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .onStart { migrateToM4() }
        .map(::decode)
        .distinctUntilChanged()

    override suspend fun updateSetting(key: SettingKey, enabled: Boolean) {
        dataStore.edit { stored ->
            migrateStored(stored)
            stored[key.preferenceKey] = enabled
        }
    }

    override suspend fun selectLevel(levelId: String) {
        val selectedIndex = catalog.levels.indexOfFirst { it.id == levelId }
        if (selectedIndex < 0) return
        dataStore.edit { stored ->
            migrateStored(stored)
            val highestUnlocked = stored.highestUnlocked()
            if (selectedIndex < highestUnlocked) stored[Keys.lastSelectedLevelId] = levelId
        }
    }

    override suspend fun recordCompletion(levelId: String, moves: Int) {
        if (moves <= 0) return
        recordCampaignCompletion(levelId, AttemptSummary(moves, overloads = 0, hintsUsed = 0))
    }

    override suspend fun recordCampaignCompletion(
        levelId: String,
        attempt: AttemptSummary,
    ): CompletionReceipt {
        require(attempt.actions > 0 && attempt.overloads >= 0 && attempt.hintsUsed >= 0) {
            "Invalid completion attempt counters"
        }
        val completedIndex = catalog.levels.indexOfFirst { it.id == levelId }
        require(completedIndex >= 0) { "Unknown campaign level '$levelId'" }
        val level = catalog.levels[completedIndex]
        val thresholds = level.metadata?.grading ?: GradingThresholds(
            parActions = level.arrows.size,
            twoStarMaxActions = level.arrows.size + maxOf(2, (level.arrows.size + 3) / 4),
        )
        val grade = GradingPolicy.grade(
            actions = attempt.actions,
            overloads = attempt.overloads,
            hintsUsed = attempt.hintsUsed,
            thresholds = thresholds,
        )
        lateinit var receipt: CompletionReceipt
        dataStore.edit { stored ->
            migrateStored(stored)
            val currentHighest = stored.highestUnlocked()
            require(completedIndex < currentHighest) { "Level '$levelId' is locked" }
            val records = decodeVersionedRecords(stored).toMutableMap()
            val previous = records[levelId] ?: LevelRecord()
            val best = LevelRecord(
                bestStars = maxOf(previous.bestStars, grade.stars),
                lowestActions = minPositive(previous.lowestActions, attempt.actions),
                lowestOverloads = minNonNegative(previous.lowestOverloads, attempt.overloads),
                lowestHints = minNonNegative(previous.lowestHints, attempt.hintsUsed),
                boardFingerprint = currentFingerprint(level),
                legacyRecords = previous.legacyRecords,
            )
            records[levelId] = best

            val completed = stored[Keys.completedLevelIds].orEmpty().validLevelIds().toMutableSet()
            completed += levelId
            val firstClearRewarded = stored[Keys.firstClearRewardedIds].orEmpty().validLevelIds().toMutableSet()
            val isFirstClear = levelId !in firstClearRewarded
            val balance = stored.coinBalance()
            val rewards = RewardPolicy.campaignCompletion(
                previousBalance = balance,
                wasFirstClearRewarded = levelId in firstClearRewarded,
                previousBestStars = previous.bestStars,
                earnedStars = grade.stars,
            )
            firstClearRewarded += levelId

            stored[Keys.completedLevelIds] = completed
            stored[Keys.firstClearRewardedIds] = firstClearRewarded
            stored[Keys.highestUnlockedLevel] = maxOf(
                currentHighest,
                (completedIndex + 2).coerceAtMost(catalog.levels.size),
            )
            writeVersionedRecords(stored, records)
            stored[Keys.coinBalance] = rewards.resultingBalance
            if (isFirstClear) {
                stored[Keys.interstitialEligibleCompletions] =
                    (stored[Keys.interstitialEligibleCompletions] ?: 0).coerceAtLeast(0) + 1
            }
            receipt = CompletionReceipt(grade, best, rewards)
        }
        return receipt
    }

    override suspend fun recordDailyCompletion(dailyId: String): DailyCompletionReceipt {
        val date = parseDailyDate(dailyId)
        lateinit var receipt: DailyCompletionReceipt
        dataStore.edit { stored ->
            migrateStored(stored)
            val completed = stored[Keys.completedDailyIds].orEmpty().toMutableSet()
            val firstCompletion = completed.add(dailyId)
            val rewarded = stored[Keys.rewardedDailyIds].orEmpty().toMutableSet()
            val lastDate = stored[Keys.lastTrustedDailyDate]?.let(::parseDateOrNull)
            val trustworthyForwardDate = lastDate == null || date.isAfter(lastDate)
            val alreadyRewardedForDate = rewarded.any { parseDailyDateOrNull(it) == date }
            val rewardAllowed = firstCompletion && trustworthyForwardDate && !alreadyRewardedForDate
            val rewards = RewardPolicy.dailyCompletion(
                previousBalance = stored.coinBalance(),
                wasRewarded = !rewardAllowed,
            )
            if (rewardAllowed) rewarded += dailyId

            val previousStreak = StreakState(
                current = (stored[Keys.currentStreak] ?: 0).coerceAtLeast(0),
                best = maxOf(
                    (stored[Keys.bestStreak] ?: 0).coerceAtLeast(0),
                    (stored[Keys.currentStreak] ?: 0).coerceAtLeast(0),
                ),
                lastTrustedCompletionDate = lastDate,
            )
            val streak = if (firstCompletion) StreakPolicy.complete(date, previousStreak).state else previousStreak
            stored[Keys.completedDailyIds] = completed.boundedHistory()
            stored[Keys.rewardedDailyIds] = rewarded.boundedHistory()
            stored[Keys.currentStreak] = streak.current
            stored[Keys.bestStreak] = streak.best
            streak.lastTrustedCompletionDate?.let { stored[Keys.lastTrustedDailyDate] = it.toString() }
            stored[Keys.coinBalance] = rewards.resultingBalance
            receipt = DailyCompletionReceipt(
                rewards = rewards,
                currentStreak = streak.current,
                bestStreak = streak.best,
                firstCompletion = firstCompletion,
            )
        }
        return receipt
    }

    override suspend fun recordInfiniteSelection(
        puzzleId: String,
        contentFingerprint: String,
        difficulty: String,
        ordinal: Int,
    ) {
        require(puzzleId.startsWith("infinite-v") && isSha256(contentFingerprint))
        require(difficulty in INFINITE_DIFFICULTIES && ordinal >= 0)
        dataStore.edit { stored ->
            migrateStored(stored)
            val history = decodeInfiniteHistory(stored[Keys.infiniteHistory]).toMutableList()
            if (history.none { it.ordinal == ordinal }) {
                history += InfiniteHistoryEntry(ordinal, puzzleId, contentFingerprint, difficulty)
            }
            stored[Keys.infiniteSelectedPuzzleId] = puzzleId
            stored[Keys.infiniteSelectedDifficulty] = difficulty
            stored[Keys.infiniteSelectionOrdinal] = ordinal
            stored[Keys.infiniteHistory] = encodeInfiniteHistory(history)
        }
    }

    override suspend fun recordInfiniteCompletion(
        puzzleId: String,
        attempt: AttemptSummary,
    ): InfiniteCompletionReceipt {
        require(attempt.actions > 0 && attempt.overloads >= 0 && attempt.hintsUsed >= 0)
        lateinit var receipt: InfiniteCompletionReceipt
        dataStore.edit { stored ->
            migrateStored(stored)
            val history = decodeInfiniteHistory(stored[Keys.infiniteHistory]).toMutableList()
            val selectedOrdinal = (stored[Keys.infiniteSelectionOrdinal] ?: 0).coerceAtLeast(0)
            val index = history.indexOfLast { it.puzzleId == puzzleId && it.ordinal == selectedOrdinal }
            require(index >= 0) { "Unknown Infinite puzzle '$puzzleId'" }
            val previous = history[index]
            val firstCompletion = !previous.completed
            val rewards = RewardPolicy.infiniteCompletion(
                previousBalance = stored.coinBalance(),
                wasRewarded = !firstCompletion,
            )
            history[index] = previous.copy(
                completed = true,
                actions = minPositive(previous.actions, attempt.actions),
                overloads = minNonNegative(previous.overloads, attempt.overloads),
                hintsUsed = minNonNegative(previous.hintsUsed, attempt.hintsUsed),
            )
            val completedCount = (stored[Keys.infiniteCompletedCount] ?: 0).coerceAtLeast(0) +
                if (firstCompletion) 1 else 0
            val previousStreak = (stored[Keys.infiniteCurrentStreak] ?: 0).coerceAtLeast(0)
            val currentStreak = if (firstCompletion) previousStreak + 1 else previousStreak
            val bestStreak = maxOf((stored[Keys.infiniteBestStreak] ?: 0).coerceAtLeast(0), currentStreak)
            stored[Keys.infiniteHistory] = encodeInfiniteHistory(history)
            stored[Keys.infiniteCompletedCount] = completedCount
            stored[Keys.infiniteCurrentStreak] = currentStreak
            stored[Keys.infiniteBestStreak] = bestStreak
            stored[Keys.coinBalance] = rewards.resultingBalance
            receipt = InfiniteCompletionReceipt(
                firstCompletion = firstCompletion,
                completedCount = completedCount,
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                rewards = rewards,
            )
        }
        return receipt
    }

    override suspend fun spendHintCoins(): HintSpendResult {
        lateinit var result: HintSpendResult
        dataStore.edit { stored ->
            migrateStored(stored)
            val balance = stored.coinBalance()
            result = if (balance >= EconomyConfig.HINT_COST) {
                val remaining = balance - EconomyConfig.HINT_COST
                stored[Keys.coinBalance] = remaining
                HintSpendResult.Approved(remaining)
            } else {
                HintSpendResult.InsufficientBalance(balance, EconomyConfig.HINT_COST)
            }
        }
        return result
    }

    override suspend fun cacheDailyChallenge(cache: DailyCache) {
        require(cache.dailyId.isNotBlank() && cache.catalogJson.isNotBlank()) { "Daily cache cannot be blank" }
        dataStore.edit { stored ->
            migrateStored(stored)
            stored[Keys.dailyCacheId] = cache.dailyId
            stored[Keys.dailyCacheFingerprint] = cache.contentFingerprint
            stored[Keys.dailyCacheCatalogJson] = cache.catalogJson
        }
    }

    override suspend fun grantRewardedHintCredit(
        transactionId: String,
        localDate: LocalDate,
    ): RewardedCreditGrantResult {
        require(transactionId.isNotBlank() && transactionId.length <= 100) { "Invalid rewarded transaction ID" }
        lateinit var result: RewardedCreditGrantResult
        dataStore.edit { stored ->
            migrateStored(stored)
            val processed = stored[Keys.processedRewardTransactionIds].orEmpty()
            val pending = stored[Keys.pendingAdHintTransactionId]
            val storedDateRaw = stored[Keys.rewardedGrantDate]
            val storedDate = storedDateRaw?.let(::parseDateOrNull)
            result = when {
                transactionId in processed || transactionId == pending -> RewardedCreditGrantResult.Duplicate
                pending != null -> RewardedCreditGrantResult.InventoryFull
                storedDateRaw != null && storedDate == null -> RewardedCreditGrantResult.DateRollback
                storedDate != null && localDate.isBefore(storedDate) -> RewardedCreditGrantResult.DateRollback
                else -> {
                    val grants = if (storedDate == localDate) {
                        (stored[Keys.rewardedGrantsOnDate] ?: 0).coerceIn(0, MAX_REWARDED_GRANTS_PER_DAY)
                    } else {
                        0
                    }
                    if (grants >= MAX_REWARDED_GRANTS_PER_DAY) {
                        RewardedCreditGrantResult.DailyCapReached
                    } else {
                        stored[Keys.rewardedGrantDate] = localDate.toString()
                        stored[Keys.rewardedGrantsOnDate] = grants + 1
                        stored[Keys.pendingAdHintTransactionId] = transactionId
                        stored[Keys.processedRewardTransactionIds] = (processed + transactionId).toList()
                            .takeLast(MAX_REWARD_TRANSACTION_HISTORY)
                            .toSet()
                        RewardedCreditGrantResult.Granted
                    }
                }
            }
        }
        return result
    }

    override suspend fun consumeRewardedHintCredit(transactionId: String): Boolean {
        var consumed = false
        dataStore.edit { stored ->
            migrateStored(stored)
            if (stored[Keys.pendingAdHintTransactionId] == transactionId) {
                stored.remove(Keys.pendingAdHintTransactionId)
                consumed = true
            }
        }
        return consumed
    }

    override suspend fun recordRewardedSkip(
        transactionId: String,
        target: RewardedSkipTarget,
    ): RewardedSkipResult {
        require(transactionId.isNotBlank() && transactionId.length <= 100) { "Invalid rewarded transaction ID" }
        val campaignIndex = (target as? RewardedSkipTarget.Campaign)?.let { campaign ->
            catalog.levels.indexOfFirst { it.id == campaign.levelId }
                .also { require(it >= 0) { "Unknown campaign level '${campaign.levelId}'" } }
        }
        lateinit var result: RewardedSkipResult
        dataStore.edit { stored ->
            migrateStored(stored)
            val processed = stored[Keys.processedRewardTransactionIds].orEmpty()
            if (transactionId in processed) {
                result = RewardedSkipResult.Duplicate
                return@edit
            }

            var newlyProgressed = false
            when (target) {
                is RewardedSkipTarget.Campaign -> {
                    val index = requireNotNull(campaignIndex)
                    val currentHighest = stored.highestUnlocked()
                    require(index < currentHighest) { "Level '${target.levelId}' is locked" }
                    val completed = stored[Keys.completedLevelIds].orEmpty().validLevelIds().toMutableSet()
                    newlyProgressed = completed.add(target.levelId)
                    val rewarded = stored[Keys.firstClearRewardedIds].orEmpty().validLevelIds().toMutableSet()
                    val firstReward = rewarded.add(target.levelId)
                    stored[Keys.completedLevelIds] = completed
                    stored[Keys.firstClearRewardedIds] = rewarded
                    stored[Keys.highestUnlockedLevel] = maxOf(
                        currentHighest,
                        (index + 2).coerceAtMost(catalog.levels.size),
                    )
                    if (!firstReward) newlyProgressed = false
                }

                is RewardedSkipTarget.Infinite -> {
                    val history = decodeInfiniteHistory(stored[Keys.infiniteHistory]).toMutableList()
                    val selectedOrdinal = (stored[Keys.infiniteSelectionOrdinal] ?: 0).coerceAtLeast(0)
                    val index = history.indexOfLast {
                        it.puzzleId == target.puzzleId && it.ordinal == selectedOrdinal
                    }
                    require(index >= 0) { "Unknown Infinite puzzle '${target.puzzleId}'" }
                    newlyProgressed = !history[index].completed
                    if (newlyProgressed) {
                        history[index] = history[index].copy(completed = true)
                        stored[Keys.infiniteHistory] = encodeInfiniteHistory(history)
                        stored[Keys.infiniteCompletedCount] =
                            (stored[Keys.infiniteCompletedCount] ?: 0).coerceAtLeast(0) + 1
                        // A skip advances the journey but is not a solved-level streak.
                        stored[Keys.infiniteCurrentStreak] = 0
                    }
                }
            }

            val grantedCoins = if (newlyProgressed) EconomyConfig.LEVEL_COMPLETION_REWARD else 0
            val resultingBalance = stored.coinBalance() + grantedCoins
            stored[Keys.coinBalance] = resultingBalance
            stored[Keys.processedRewardTransactionIds] = (processed + transactionId).toList()
                .takeLast(MAX_REWARD_TRANSACTION_HISTORY)
                .toSet()
            result = RewardedSkipResult.Applied(
                resultingBalance = resultingBalance,
                grantedCoins = grantedCoins,
                completedCount = (stored[Keys.infiniteCompletedCount] ?: 0).coerceAtLeast(0),
                currentStreak = (stored[Keys.infiniteCurrentStreak] ?: 0).coerceAtLeast(0),
                bestStreak = maxOf(
                    (stored[Keys.infiniteBestStreak] ?: 0).coerceAtLeast(0),
                    (stored[Keys.infiniteCurrentStreak] ?: 0).coerceAtLeast(0),
                ),
            )
        }
        return result
    }

    override suspend fun recordFullScreenAdDismissal(
        localDate: LocalDate,
        wallTimeMillis: Long,
        interstitialShown: Boolean,
    ) {
        require(wallTimeMillis >= 0L)
        dataStore.edit { stored ->
            migrateStored(stored)
            val storedDateRaw = stored[Keys.lastFullScreenAdDate]
            val storedDate = storedDateRaw?.let(::parseDateOrNull)
            if (storedDateRaw != null && storedDate == null) return@edit
            if (storedDate != null && localDate.isBefore(storedDate)) return@edit
            val shown = if (storedDate == localDate) {
                (stored[Keys.interstitialsShownOnDate] ?: 0).coerceIn(0, MAX_INTERSTITIALS_PER_DAY)
            } else {
                0
            }
            stored[Keys.lastFullScreenAdWallTime] = maxOf(
                stored[Keys.lastFullScreenAdWallTime] ?: 0L,
                wallTimeMillis,
            )
            stored[Keys.lastFullScreenAdDate] = localDate.toString()
            if (interstitialShown) {
                stored[Keys.interstitialsShownOnDate] = (shown + 1).coerceAtMost(MAX_INTERSTITIALS_PER_DAY)
                stored[Keys.interstitialEligibleCompletions] = 0
            } else if (storedDate != localDate) {
                stored[Keys.interstitialsShownOnDate] = 0
            }
        }
    }

    private suspend fun migrateToM4() {
        dataStore.edit(::migrateStored)
    }

    private fun migrateStored(stored: MutablePreferences) {
        val previousContentVersion = stored[Keys.contentVersion] ?: 0
        val version = stored[Keys.schemaVersion]
        if (version != null && version !in setOf(
                M2_SCHEMA_VERSION,
                M3_SCHEMA_VERSION,
                M4_SCHEMA_VERSION,
                M5_SCHEMA_VERSION,
                M6_SCHEMA_VERSION,
                PLAYER_PREFERENCES_SCHEMA_VERSION,
            )
        ) {
            crashReporter.recordUnexpected(IllegalStateException("Unsupported player schema recovered"))
            stored.clear()
        }
        if (stored[Keys.schemaVersion] == null || stored[Keys.schemaVersion] == M2_SCHEMA_VERSION) {
            val completed = stored[Keys.completedLevelIds].orEmpty().validLevelIds()
            val oldBestMoves = decodeBestMoves(stored[Keys.bestMoves]).filterKeys { it in validLevelIds }
            val migratedRecords = completed.associateWith { id ->
                LevelRecord(
                    bestStars = 1,
                    lowestActions = oldBestMoves[id],
                    lowestOverloads = null,
                    lowestHints = null,
                )
            } + oldBestMoves.filterKeys { it !in completed }.mapValues { (_, moves) ->
                LevelRecord(lowestActions = moves)
            }
            stored[Keys.levelRecords] = encodeRecords(migratedRecords)
            stored[Keys.firstClearRewardedIds] = completed
            stored[Keys.coinBalance] = stored[Keys.coinBalance]?.coerceAtLeast(0)
                ?: EconomyConfig.STARTING_BALANCE
            stored[Keys.completedDailyIds] = stored[Keys.completedDailyIds].orEmpty().boundedHistory()
            stored[Keys.rewardedDailyIds] = stored[Keys.rewardedDailyIds].orEmpty().boundedHistory()
        }
        stored[Keys.interstitialEligibleCompletions] =
            (stored[Keys.interstitialEligibleCompletions] ?: 0).coerceAtLeast(0)
        stored[Keys.interstitialsShownOnDate] =
            (stored[Keys.interstitialsShownOnDate] ?: 0).coerceIn(0, MAX_INTERSTITIALS_PER_DAY)
        stored[Keys.rewardedGrantsOnDate] =
            (stored[Keys.rewardedGrantsOnDate] ?: 0).coerceIn(0, MAX_REWARDED_GRANTS_PER_DAY)
        stored[Keys.infiniteSelectionOrdinal] = (stored[Keys.infiniteSelectionOrdinal] ?: 0).coerceAtLeast(0)
        stored[Keys.infiniteCompletedCount] = (stored[Keys.infiniteCompletedCount] ?: 0).coerceAtLeast(0)
        stored[Keys.infiniteCurrentStreak] = (stored[Keys.infiniteCurrentStreak] ?: 0).coerceAtLeast(0)
        stored[Keys.infiniteBestStreak] = maxOf(
            (stored[Keys.infiniteBestStreak] ?: 0).coerceAtLeast(0),
            stored[Keys.infiniteCurrentStreak] ?: 0,
        )
        stored[Keys.infiniteHistory] = encodeInfiniteHistory(decodeInfiniteHistory(stored[Keys.infiniteHistory]))
        stored[Keys.pendingAdHintTransactionId]?.takeIf { it.isBlank() || it.length > 100 }?.let {
            stored.remove(Keys.pendingAdHintTransactionId)
        }
        stored[Keys.processedRewardTransactionIds] = stored[Keys.processedRewardTransactionIds].orEmpty()
            .filter { it.isNotBlank() && it.length <= 100 }
            .takeLast(MAX_REWARD_TRANSACTION_HISTORY)
            .toSet()
        migrateExpandedCampaignContinue(stored, previousContentVersion)
        migrateBoardRevisionRecords(stored, previousContentVersion)
        stored[Keys.schemaVersion] = PLAYER_PREFERENCES_SCHEMA_VERSION
        stored[Keys.economyVersion] = EconomyConfig.VERSION
        stored[Keys.contentVersion] = catalog.contentVersion
        stored[Keys.generatorVersion] = catalog.generatorVersion ?: GENERATOR_VERSION
        stored[Keys.dailyGeneratorVersion] = DailySeed.GENERATOR_VERSION
        stored[Keys.coinBalance] = stored.coinBalance()
        stored[Keys.highestUnlockedLevel] = stored.highestUnlocked()
    }

    /** Stable-ID bridges for each approved fixed-campaign expansion. */
    private fun migrateExpandedCampaignContinue(
        stored: MutablePreferences,
        previousContentVersion: Int,
    ) {
        if (previousContentVersion >= catalog.contentVersion) return
        val completed = stored[Keys.completedLevelIds].orEmpty().validLevelIds()
        listOf(
            CampaignExpansionBridge(introducedContentVersion = 4, oldFinalNumber = 100, continuationNumber = 101),
            CampaignExpansionBridge(introducedContentVersion = 6, oldFinalNumber = 150, continuationNumber = 151),
            CampaignExpansionBridge(introducedContentVersion = 8, oldFinalNumber = 200, continuationNumber = 201),
            CampaignExpansionBridge(introducedContentVersion = 9, oldFinalNumber = 205, continuationNumber = 206),
        ).forEach { bridge ->
            if (previousContentVersion >= bridge.introducedContentVersion) return@forEach
            val oldFinalIndex = catalog.levels.indexOfFirst { it.number == bridge.oldFinalNumber }
            val continuationIndex = catalog.levels.indexOfFirst { it.number == bridge.continuationNumber }
            if (oldFinalIndex < 0 || continuationIndex != oldFinalIndex + 1) return@forEach
            val oldFinalId = catalog.levels[oldFinalIndex].id
            val continuationId = catalog.levels[continuationIndex].id
            val selected = stored[Keys.lastSelectedLevelId]
            if (oldFinalId in completed) {
                stored[Keys.highestUnlockedLevel] = maxOf(
                    stored.highestUnlocked(),
                    continuationIndex + 1,
                )
                if (selected == null || selected == oldFinalId) {
                    stored[Keys.lastSelectedLevelId] = continuationId
                }
            }
        }
    }

    private data class CampaignExpansionBridge(
        val introducedContentVersion: Int,
        val oldFinalNumber: Int,
        val continuationNumber: Int,
    )

    /**
     * Separates incomparable board revisions without discarding earned value. Completion,
     * stars, first-clear rewards, unlocks and currency stay keyed by stable level ID. Move,
     * overload and hint minima are archived under the old board fingerprint, then restarted
     * for the current fingerprint.
     */
    private fun migrateBoardRevisionRecords(
        stored: MutablePreferences,
        previousContentVersion: Int,
    ) {
        val records = decodeVersionedRecords(stored).toMutableMap()
        records.entries.toList().forEach { (levelId, record) ->
            val level = catalog.levels.firstOrNull { it.id == levelId } ?: return@forEach
            val current = currentFingerprint(level)
            val metadata = level.metadata
            val source = when {
                record.boardFingerprint != null -> record.boardFingerprint
                metadata?.previousContentFingerprint != null -> metadata.previousContentFingerprint
                else -> current
            }
            if (source == current) {
                records[levelId] = record.copy(boardFingerprint = current)
                return@forEach
            }
            val earned = record.bestStars > 0 || record.lowestActions != null ||
                record.lowestOverloads != null || record.lowestHints != null
            val archive = if (earned) {
                LegacyLevelRecord(
                    boardFingerprint = source,
                    sourceContentVersion = previousContentVersion.coerceAtLeast(0),
                    bestStars = record.bestStars,
                    lowestActions = record.lowestActions,
                    lowestOverloads = record.lowestOverloads,
                    lowestHints = record.lowestHints,
                )
            } else {
                null
            }
            val history = (record.legacyRecords + listOfNotNull(archive))
                .distinctBy { it.boardFingerprint to it.sourceContentVersion }
                .takeLast(MAX_LEGACY_BOARD_RECORDS_PER_LEVEL)
            records[levelId] = record.copy(
                lowestActions = null,
                lowestOverloads = null,
                lowestHints = null,
                boardFingerprint = current,
                legacyRecords = history,
            )
        }
        if (records.isNotEmpty()) writeVersionedRecords(stored, records)
    }

    private fun decode(stored: Preferences): PlayerPreferences {
        if (stored[Keys.schemaVersion] != PLAYER_PREFERENCES_SCHEMA_VERSION) return defaultPreferences()
        val completedIds = stored[Keys.completedLevelIds].orEmpty().validLevelIds()
        val completedUnlock = completedIds.maxOfOrNull { id ->
            (catalog.levels.indexOfFirst { it.id == id } + 2).coerceAtMost(catalog.levels.size)
        } ?: 1
        val highestUnlocked = maxOf(stored.highestUnlocked(), completedUnlock)
        val requestedIndex = catalog.levels.indexOfFirst { it.id == stored[Keys.lastSelectedLevelId] }
        val lastSelected = if (requestedIndex in 0 until highestUnlocked) {
            catalog.levels[requestedIndex].id
        } else {
            catalog.levels[highestUnlocked - 1].id
        }
        val records = decodeVersionedRecords(stored)
            .filterKeys { it in validLevelIds }
            .mapValues { (_, value) -> value.sanitized() }
        val legacyBest = decodeBestMoves(stored[Keys.bestMoves])
            .filter { (id, moves) -> id in validLevelIds && moves > 0 }
        val bestMoves = records.mapNotNull { (id, record) -> record.lowestActions?.let { id to it } }.toMap() + legacyBest
        val trustedDailyDate = stored[Keys.lastTrustedDailyDate]?.takeIf { parseDateOrNull(it) != null }
        val currentStreak = if (trustedDailyDate == null) 0 else (stored[Keys.currentStreak] ?: 0).coerceAtLeast(0)
        val bestStreak = maxOf((stored[Keys.bestStreak] ?: 0).coerceAtLeast(0), currentStreak)

        return PlayerPreferences(
            settings = PlayerSettings(
                soundEnabled = stored[Keys.soundEnabled] ?: true,
                hapticsEnabled = stored[Keys.hapticsEnabled] ?: true,
                reducedMotion = stored[Keys.reducedMotion] ?: defaultReducedMotion,
                highContrastFields = stored[Keys.highContrastFields] ?: false,
                pathPreviewAssistance = stored[Keys.pathPreviewAssistance] ?: false,
                diagnosticsEnabled = stored[Keys.diagnosticsEnabled] ?: false,
            ),
            progress = PlayerProgress(
                highestUnlockedLevel = highestUnlocked,
                completedLevelIds = completedIds,
                lastSelectedLevelId = lastSelected,
                bestMovesByLevel = bestMoves,
                recordsByLevel = records,
                firstClearRewardedLevelIds = stored[Keys.firstClearRewardedIds].orEmpty().validLevelIds(),
                coinBalance = stored.coinBalance(),
                economyVersion = stored[Keys.economyVersion] ?: EconomyConfig.VERSION,
                completedDailyIds = stored[Keys.completedDailyIds].orEmpty().validDailyIds(),
                rewardedDailyIds = stored[Keys.rewardedDailyIds].orEmpty().validDailyIds(),
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                lastTrustedDailyDate = trustedDailyDate,
                dailyCache = decodeDailyCache(stored),
                contentVersion = stored[Keys.contentVersion] ?: catalog.contentVersion,
                generatorVersion = stored[Keys.generatorVersion] ?: catalog.generatorVersion ?: GENERATOR_VERSION,
                dailyGeneratorVersion = stored[Keys.dailyGeneratorVersion] ?: DailySeed.GENERATOR_VERSION,
                monetization = AdMonetizationState(
                    interstitialEligibleCompletions = (stored[Keys.interstitialEligibleCompletions] ?: 0).coerceAtLeast(0),
                    lastFullScreenAdWallTimeMillis = stored[Keys.lastFullScreenAdWallTime]?.takeIf { it >= 0L },
                    lastFullScreenAdDate = stored[Keys.lastFullScreenAdDate].conservativeDate(),
                    interstitialsShownOnDate = (stored[Keys.interstitialsShownOnDate] ?: 0)
                        .coerceIn(0, MAX_INTERSTITIALS_PER_DAY),
                    rewardedGrantDate = stored[Keys.rewardedGrantDate].conservativeDate(),
                    rewardedGrantsOnDate = (stored[Keys.rewardedGrantsOnDate] ?: 0)
                        .coerceIn(0, MAX_REWARDED_GRANTS_PER_DAY),
                    pendingAdHintTransactionId = stored[Keys.pendingAdHintTransactionId],
                    processedRewardTransactionIds = stored[Keys.processedRewardTransactionIds].orEmpty(),
                ),
                infinite = InfiniteProgress(
                    selectedPuzzleId = stored[Keys.infiniteSelectedPuzzleId]?.takeIf { it.startsWith("infinite-v") },
                    selectedDifficulty = stored[Keys.infiniteSelectedDifficulty]
                        ?.takeIf { it in INFINITE_DIFFICULTIES }
                        ?: "PROGRESSIVE",
                    selectionOrdinal = (stored[Keys.infiniteSelectionOrdinal] ?: 0).coerceAtLeast(0),
                    completedCount = (stored[Keys.infiniteCompletedCount] ?: 0).coerceAtLeast(0),
                    currentStreak = (stored[Keys.infiniteCurrentStreak] ?: 0).coerceAtLeast(0),
                    bestStreak = maxOf(
                        (stored[Keys.infiniteBestStreak] ?: 0).coerceAtLeast(0),
                        (stored[Keys.infiniteCurrentStreak] ?: 0).coerceAtLeast(0),
                    ),
                    history = decodeInfiniteHistory(stored[Keys.infiniteHistory]),
                ),
            ),
        )
    }

    private fun defaultPreferences(): PlayerPreferences = PlayerPreferences(
        settings = PlayerSettings(reducedMotion = defaultReducedMotion),
        progress = PlayerProgress(lastSelectedLevelId = catalog.levels.first().id),
    )

    private fun decodeDailyCache(stored: Preferences): DailyCache? {
        val id = stored[Keys.dailyCacheId]?.takeIf(String::isNotBlank) ?: return null
        val fingerprint = stored[Keys.dailyCacheFingerprint]?.takeIf(String::isNotBlank) ?: return null
        val json = stored[Keys.dailyCacheCatalogJson]?.takeIf(String::isNotBlank) ?: return null
        return DailyCache(id, fingerprint, json)
    }

    private fun Preferences.highestUnlocked(): Int = (this[Keys.highestUnlockedLevel] ?: 1)
        .coerceIn(1, catalog.levels.size)

    private fun Preferences.coinBalance(): Int = (this[Keys.coinBalance] ?: EconomyConfig.STARTING_BALANCE)
        .coerceAtLeast(0)

    private val validLevelIds: Set<String> get() = catalog.levels.mapTo(linkedSetOf()) { it.id }

    private fun Set<String>.validLevelIds(): Set<String> = filterTo(linkedSetOf()) { it in validLevelIds }

    private fun Set<String>.boundedHistory(): Set<String> = sortedDescending()
        .take(MAX_DAILY_HISTORY)
        .toCollection(linkedSetOf())

    private fun Set<String>.validDailyIds(): Set<String> = filterTo(linkedSetOf()) {
        parseDailyDateOrNull(it) != null
    }.boundedHistory()

    private val SettingKey.preferenceKey: Preferences.Key<Boolean>
        get() = when (this) {
            SettingKey.SOUND -> Keys.soundEnabled
            SettingKey.HAPTICS -> Keys.hapticsEnabled
            SettingKey.REDUCED_MOTION -> Keys.reducedMotion
            SettingKey.HIGH_CONTRAST_FIELDS -> Keys.highContrastFields
            SettingKey.PATH_PREVIEW_ASSISTANCE -> Keys.pathPreviewAssistance
            SettingKey.DIAGNOSTICS -> Keys.diagnosticsEnabled
        }

    private fun decodeBestMoves(values: Set<String>?): Map<String, Int> = values.orEmpty()
        .mapNotNull { encoded ->
            val separator = encoded.lastIndexOf(':')
            if (separator <= 0) return@mapNotNull null
            val moves = encoded.substring(separator + 1).toIntOrNull() ?: return@mapNotNull null
            encoded.substring(0, separator) to moves
        }
        .toMap()

    private fun encodeBestMoves(values: Map<String, Int>): Set<String> = values
        .filterValues { it > 0 }
        .mapTo(linkedSetOf()) { (id, moves) -> "$id:$moves" }

    private fun decodeRecords(values: Set<String>?): Map<String, LevelRecord> = values.orEmpty()
        .mapNotNull { encoded ->
            val parts = encoded.split('|')
            if (parts.size != 5 || parts[0].isBlank()) return@mapNotNull null
            val stars = parts[1].toIntOrNull() ?: return@mapNotNull null
            parts[0] to LevelRecord(
                bestStars = stars,
                lowestActions = parts[2].nullableInt(),
                lowestOverloads = parts[3].nullableInt(),
                lowestHints = parts[4].nullableInt(),
            )
        }
        .toMap()

    private fun decodeVersionedRecords(stored: Preferences): Map<String, LevelRecord> {
        val fingerprints = decodeRecordFingerprints(stored[Keys.levelRecordFingerprints])
        val histories = decodeLegacyRecords(stored[Keys.legacyLevelRecords])
        return decodeRecords(stored[Keys.levelRecords]).mapValues { (id, record) ->
            record.copy(
                boardFingerprint = fingerprints[id],
                legacyRecords = histories[id].orEmpty(),
            )
        }
    }

    private fun encodeRecords(records: Map<String, LevelRecord>): Set<String> = records
        .mapTo(linkedSetOf()) { (id, record) ->
            listOf(
                id,
                record.bestStars.coerceIn(0, 3),
                record.lowestActions ?: -1,
                record.lowestOverloads ?: -1,
                record.lowestHints ?: -1,
            ).joinToString("|")
        }

    private fun decodeRecordFingerprints(values: Set<String>?): Map<String, String> = values.orEmpty()
        .mapNotNull { encoded ->
            val separator = encoded.indexOf('|')
            if (separator <= 0) return@mapNotNull null
            val fingerprint = encoded.substring(separator + 1)
            if (!isSha256(fingerprint)) return@mapNotNull null
            encoded.substring(0, separator) to fingerprint
        }
        .toMap()

    private fun encodeRecordFingerprints(records: Map<String, LevelRecord>): Set<String> = records
        .mapNotNullTo(linkedSetOf()) { (id, record) ->
            record.boardFingerprint?.takeIf(::isSha256)?.let { "$id|$it" }
        }

    private fun decodeLegacyRecords(values: Set<String>?): Map<String, List<LegacyLevelRecord>> = values.orEmpty()
        .mapNotNull { encoded ->
            val parts = encoded.split('|')
            if (parts.size != 7 || parts[0].isBlank()) return@mapNotNull null
            val fingerprint = parts[2].takeUnless { it == "-" }
            if (fingerprint != null && !isSha256(fingerprint)) return@mapNotNull null
            val contentVersion = parts[1].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            val stars = parts[3].toIntOrNull() ?: return@mapNotNull null
            parts[0] to LegacyLevelRecord(
                boardFingerprint = fingerprint,
                sourceContentVersion = contentVersion,
                bestStars = stars.coerceIn(0, 3),
                lowestActions = parts[4].nullableInt()?.takeIf { it > 0 },
                lowestOverloads = parts[5].nullableInt(),
                lowestHints = parts[6].nullableInt(),
            )
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, rows) ->
            rows.distinctBy { it.boardFingerprint to it.sourceContentVersion }
                .takeLast(MAX_LEGACY_BOARD_RECORDS_PER_LEVEL)
        }

    private fun encodeLegacyRecords(records: Map<String, LevelRecord>): Set<String> = records
        .flatMapTo(linkedSetOf()) { (id, record) ->
            record.legacyRecords.takeLast(MAX_LEGACY_BOARD_RECORDS_PER_LEVEL).map { legacy ->
                listOf(
                    id,
                    legacy.sourceContentVersion.coerceAtLeast(0),
                    legacy.boardFingerprint ?: "-",
                    legacy.bestStars.coerceIn(0, 3),
                    legacy.lowestActions ?: -1,
                    legacy.lowestOverloads ?: -1,
                    legacy.lowestHints ?: -1,
                ).joinToString("|")
            }
        }

    private fun decodeInfiniteHistory(values: Set<String>?): List<InfiniteHistoryEntry> = values.orEmpty()
        .mapNotNull { encoded ->
            val parts = encoded.split('|')
            if (parts.size != 8) return@mapNotNull null
            val ordinal = parts[0].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            val puzzleId = parts[1].takeIf { it.startsWith("infinite-v") } ?: return@mapNotNull null
            val fingerprint = parts[2].takeIf(::isSha256) ?: return@mapNotNull null
            val difficulty = parts[3].takeIf {
                it in INFINITE_DIFFICULTIES
            } ?: return@mapNotNull null
            InfiniteHistoryEntry(
                ordinal = ordinal,
                puzzleId = puzzleId,
                contentFingerprint = fingerprint,
                difficulty = difficulty,
                completed = parts[4] == "1",
                actions = parts[5].nullableInt()?.takeIf { it > 0 },
                overloads = parts[6].nullableInt(),
                hintsUsed = parts[7].nullableInt(),
            )
        }
        .distinctBy(InfiniteHistoryEntry::ordinal)
        .sortedBy(InfiniteHistoryEntry::ordinal)
        .takeLast(MAX_INFINITE_HISTORY)

    private fun encodeInfiniteHistory(values: List<InfiniteHistoryEntry>): Set<String> = values
        .distinctBy(InfiniteHistoryEntry::ordinal)
        .sortedBy(InfiniteHistoryEntry::ordinal)
        .takeLast(MAX_INFINITE_HISTORY)
        .mapTo(linkedSetOf()) { entry ->
            listOf(
                entry.ordinal,
                entry.puzzleId,
                entry.contentFingerprint,
                entry.difficulty,
                if (entry.completed) 1 else 0,
                entry.actions ?: -1,
                entry.overloads ?: -1,
                entry.hintsUsed ?: -1,
            ).joinToString("|")
        }

    private fun writeVersionedRecords(
        stored: MutablePreferences,
        records: Map<String, LevelRecord>,
    ) {
        stored[Keys.levelRecords] = encodeRecords(records)
        stored[Keys.levelRecordFingerprints] = encodeRecordFingerprints(records)
        stored[Keys.legacyLevelRecords] = encodeLegacyRecords(records)
        stored[Keys.bestMoves] = encodeBestMoves(records.mapNotNull { (id, record) ->
            record.lowestActions?.let { id to it }
        }.toMap())
    }

    private fun String.nullableInt(): Int? = toIntOrNull()?.takeIf { it >= 0 }

    private fun LevelRecord.sanitized(): LevelRecord = LevelRecord(
        bestStars = bestStars.coerceIn(0, 3),
        lowestActions = lowestActions?.takeIf { it > 0 },
        lowestOverloads = lowestOverloads?.takeIf { it >= 0 },
        lowestHints = lowestHints?.takeIf { it >= 0 },
        boardFingerprint = boardFingerprint?.takeIf(::isSha256),
        legacyRecords = legacyRecords.map { legacy ->
            legacy.copy(
                boardFingerprint = legacy.boardFingerprint?.takeIf(::isSha256),
                sourceContentVersion = legacy.sourceContentVersion.coerceAtLeast(0),
                bestStars = legacy.bestStars.coerceIn(0, 3),
                lowestActions = legacy.lowestActions?.takeIf { it > 0 },
                lowestOverloads = legacy.lowestOverloads?.takeIf { it >= 0 },
                lowestHints = legacy.lowestHints?.takeIf { it >= 0 },
            )
        }.distinctBy { it.boardFingerprint to it.sourceContentVersion }
            .takeLast(MAX_LEGACY_BOARD_RECORDS_PER_LEVEL),
    )

    private fun currentFingerprint(level: com.rameshta.magnetrail.core.model.LevelDefinition): String =
        level.metadata?.contentFingerprint ?: ContentFingerprint.of(level)

    private fun isSha256(value: String): Boolean = value.startsWith("sha256:") && value.length == 71

    private fun minPositive(previous: Int?, candidate: Int): Int = minOf(previous ?: Int.MAX_VALUE, candidate)

    private fun minNonNegative(previous: Int?, candidate: Int): Int = minOf(previous ?: Int.MAX_VALUE, candidate)

    private fun parseDailyDate(dailyId: String): LocalDate = requireNotNull(parseDailyDateOrNull(dailyId)) {
        "Invalid daily ID '$dailyId'"
    }

    private fun parseDailyDateOrNull(dailyId: String): LocalDate? =
        parseDateOrNull(dailyId.substringBefore("-v"))

    private fun parseDateOrNull(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

    private fun String?.conservativeDate(): String? = when {
        this == null -> null
        parseDateOrNull(this) != null -> this
        else -> LocalDate.MAX.toString()
    }

    private object Keys {
        val schemaVersion = intPreferencesKey("schema_version")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val highContrastFields = booleanPreferencesKey("high_contrast_fields")
        val pathPreviewAssistance = booleanPreferencesKey("path_preview_assistance")
        val diagnosticsEnabled = booleanPreferencesKey("diagnostics_enabled")
        val highestUnlockedLevel = intPreferencesKey("highest_unlocked_level")
        val completedLevelIds = stringSetPreferencesKey("completed_level_ids")
        val lastSelectedLevelId = stringPreferencesKey("last_selected_level_id")
        val bestMoves = stringSetPreferencesKey("best_moves")
        val levelRecords = stringSetPreferencesKey("level_records_v3")
        val levelRecordFingerprints = stringSetPreferencesKey("level_record_fingerprints_v6")
        val legacyLevelRecords = stringSetPreferencesKey("legacy_level_records_v6")
        val firstClearRewardedIds = stringSetPreferencesKey("first_clear_rewarded_ids")
        val coinBalance = intPreferencesKey("coin_balance")
        val economyVersion = intPreferencesKey("economy_version")
        val completedDailyIds = stringSetPreferencesKey("completed_daily_ids")
        val rewardedDailyIds = stringSetPreferencesKey("rewarded_daily_ids")
        val currentStreak = intPreferencesKey("current_daily_streak")
        val bestStreak = intPreferencesKey("best_daily_streak")
        val lastTrustedDailyDate = stringPreferencesKey("last_trusted_daily_date")
        val dailyCacheId = stringPreferencesKey("daily_cache_id")
        val dailyCacheFingerprint = stringPreferencesKey("daily_cache_fingerprint")
        val dailyCacheCatalogJson = stringPreferencesKey("daily_cache_catalog_json")
        val contentVersion = intPreferencesKey("content_version")
        val generatorVersion = intPreferencesKey("generator_version")
        val dailyGeneratorVersion = intPreferencesKey("daily_generator_version")
        val interstitialEligibleCompletions = intPreferencesKey("interstitial_eligible_completions")
        val lastFullScreenAdWallTime = longPreferencesKey("last_full_screen_ad_wall_time")
        val lastFullScreenAdDate = stringPreferencesKey("last_full_screen_ad_date")
        val interstitialsShownOnDate = intPreferencesKey("interstitials_shown_on_date")
        val rewardedGrantDate = stringPreferencesKey("rewarded_grant_date")
        val rewardedGrantsOnDate = intPreferencesKey("rewarded_grants_on_date")
        val pendingAdHintTransactionId = stringPreferencesKey("pending_ad_hint_transaction_id")
        val processedRewardTransactionIds = stringSetPreferencesKey("processed_reward_transaction_ids")
        val infiniteSelectedPuzzleId = stringPreferencesKey("infinite_selected_puzzle_id_v1")
        val infiniteSelectedDifficulty = stringPreferencesKey("infinite_selected_difficulty_v1")
        val infiniteSelectionOrdinal = intPreferencesKey("infinite_selection_ordinal_v1")
        val infiniteCompletedCount = intPreferencesKey("infinite_completed_count_v1")
        val infiniteCurrentStreak = intPreferencesKey("infinite_current_streak_v1")
        val infiniteBestStreak = intPreferencesKey("infinite_best_streak_v1")
        val infiniteHistory = stringSetPreferencesKey("infinite_history_v1")
    }

    companion object {
        private const val M2_SCHEMA_VERSION = 1
        private const val M3_SCHEMA_VERSION = 3
        private const val M4_SCHEMA_VERSION = 4
        private const val M5_SCHEMA_VERSION = 5
        private const val M6_SCHEMA_VERSION = 6
        private const val MAX_DAILY_HISTORY = 512
        private const val MAX_REWARDED_GRANTS_PER_DAY = 5
        private const val MAX_INTERSTITIALS_PER_DAY = 4
        private const val MAX_REWARD_TRANSACTION_HISTORY = 16
        private const val MAX_LEGACY_BOARD_RECORDS_PER_LEVEL = 4
        private const val MAX_INFINITE_HISTORY = 100
        private val INFINITE_DIFFICULTIES =
            setOf("PROGRESSIVE", "RELAXED", "BALANCED", "CHALLENGING", "VERY_HARD", "EXPERT", "MASTER")
    }
}

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
import com.rameshta.magnetrail.core.economy.EconomyConfig
import com.rameshta.magnetrail.core.economy.RewardPolicy
import com.rameshta.magnetrail.core.generation.CONTENT_VERSION
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
            val records = decodeRecords(stored[Keys.levelRecords]).toMutableMap()
            val previous = records[levelId] ?: LevelRecord()
            val best = LevelRecord(
                bestStars = maxOf(previous.bestStars, grade.stars),
                lowestActions = minPositive(previous.lowestActions, attempt.actions),
                lowestOverloads = minNonNegative(previous.lowestOverloads, attempt.overloads),
                lowestHints = minNonNegative(previous.lowestHints, attempt.hintsUsed),
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
            stored[Keys.levelRecords] = encodeRecords(records)
            stored[Keys.bestMoves] = encodeBestMoves(records.mapNotNull { (id, record) ->
                record.lowestActions?.let { id to it }
            }.toMap())
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
        val version = stored[Keys.schemaVersion]
        if (version != null && version !in setOf(
                M2_SCHEMA_VERSION,
                M3_SCHEMA_VERSION,
                M4_SCHEMA_VERSION,
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
        stored[Keys.pendingAdHintTransactionId]?.takeIf { it.isBlank() || it.length > 100 }?.let {
            stored.remove(Keys.pendingAdHintTransactionId)
        }
        stored[Keys.processedRewardTransactionIds] = stored[Keys.processedRewardTransactionIds].orEmpty()
            .filter { it.isNotBlank() && it.length <= 100 }
            .takeLast(MAX_REWARD_TRANSACTION_HISTORY)
            .toSet()
        stored[Keys.schemaVersion] = PLAYER_PREFERENCES_SCHEMA_VERSION
        stored[Keys.economyVersion] = EconomyConfig.VERSION
        stored[Keys.contentVersion] = CONTENT_VERSION
        stored[Keys.generatorVersion] = GENERATOR_VERSION
        stored[Keys.dailyGeneratorVersion] = DailySeed.GENERATOR_VERSION
        stored[Keys.coinBalance] = stored.coinBalance()
        stored[Keys.highestUnlockedLevel] = stored.highestUnlocked()
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
        val records = decodeRecords(stored[Keys.levelRecords])
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
                contentVersion = stored[Keys.contentVersion] ?: CONTENT_VERSION,
                generatorVersion = stored[Keys.generatorVersion] ?: GENERATOR_VERSION,
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

    private fun String.nullableInt(): Int? = toIntOrNull()?.takeIf { it >= 0 }

    private fun LevelRecord.sanitized(): LevelRecord = LevelRecord(
        bestStars = bestStars.coerceIn(0, 3),
        lowestActions = lowestActions?.takeIf { it > 0 },
        lowestOverloads = lowestOverloads?.takeIf { it >= 0 },
        lowestHints = lowestHints?.takeIf { it >= 0 },
    )

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
    }

    companion object {
        private const val M2_SCHEMA_VERSION = 1
        private const val M3_SCHEMA_VERSION = 3
        private const val M4_SCHEMA_VERSION = 4
        private const val MAX_DAILY_HISTORY = 512
        private const val MAX_REWARDED_GRANTS_PER_DAY = 5
        private const val MAX_INTERSTITIALS_PER_DAY = 4
        private const val MAX_REWARD_TRANSACTION_HISTORY = 16
    }
}

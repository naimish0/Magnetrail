package com.rameshta.magnetrail.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.infinite.InfiniteDifficulty
import com.rameshta.magnetrail.core.infinite.InfiniteSelectionDecision
import com.rameshta.magnetrail.data.PlayerPreferences
import com.rameshta.magnetrail.data.PlayerProgress
import com.rameshta.magnetrail.data.ProgressRepository
import com.rameshta.magnetrail.data.AttemptSummary
import com.rameshta.magnetrail.data.CompletionReceipt
import com.rameshta.magnetrail.data.HintSpendResult
import com.rameshta.magnetrail.data.LevelRecord
import com.rameshta.magnetrail.data.SettingKey
import com.rameshta.magnetrail.data.withValue
import com.rameshta.magnetrail.daily.DailyChallengeService
import com.rameshta.magnetrail.daily.DateProvider
import com.rameshta.magnetrail.daily.SystemDateProvider
import com.rameshta.magnetrail.core.economy.EconomyConfig
import com.rameshta.magnetrail.core.daily.DailySeed
import com.rameshta.magnetrail.core.economy.RewardBreakdown
import com.rameshta.magnetrail.core.grading.GradingPolicy
import com.rameshta.magnetrail.core.model.GradingThresholds
import com.rameshta.magnetrail.feedback.FeedbackEvent
import com.rameshta.magnetrail.analytics.AnalyticsBuckets
import com.rameshta.magnetrail.analytics.AnalyticsEvent
import com.rameshta.magnetrail.analytics.AnalyticsTracker
import com.rameshta.magnetrail.analytics.NoOpAnalyticsTracker
import com.rameshta.magnetrail.crash.CrashReporter
import com.rameshta.magnetrail.crash.NoOpCrashReporter
import com.rameshta.magnetrail.daily.DailyLoadSource
import com.rameshta.magnetrail.infinite.InfiniteModeService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class GameViewModel(
    private val catalog: LevelCatalog,
    private val engine: GameEngine = DefaultGameEngine(),
    private val progressRepository: ProgressRepository? = null,
    private val hintProvider: HintProvider = SolverHintProvider(),
    private val dailyChallengeService: DailyChallengeService? = null,
    private val infiniteModeService: InfiniteModeService? = null,
    private val dateProvider: DateProvider = SystemDateProvider(),
    val debugUnlockAll: Boolean = false,
    private val analytics: AnalyticsTracker = NoOpAnalyticsTracker,
    private val crashReporter: CrashReporter = NoOpCrashReporter,
    private val elapsedMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) : ViewModel() {
    private val _uiState = MutableStateFlow(createLevelState(levelIndex = 0))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<FeedbackEvent>(extraBufferCapacity = 16)
    val feedbackEvents: SharedFlow<FeedbackEvent> = _feedbackEvents.asSharedFlow()

    private var hintJob: Job? = null
    private var hintGeneration = 0L
    private var attemptStartedMillis = elapsedMillis()

    init {
        progressRepository?.let { repository ->
            viewModelScope.launch {
                repository.preferences.collect(::applyPreferences)
            }
        }
    }

    fun onAction(action: GameAction) {
        when (action) {
            is GameAction.LaunchArrow -> launchArrow(action.arrowId)
            is GameAction.AnimationPhaseChanged -> updateAnimationPhase(action.phase)
            GameAction.AnimationCompleted -> completeAnimation()
            GameAction.Restart, GameAction.Replay -> restart()
            GameAction.NavigateHome -> navigateHome()
            GameAction.Play -> play()
            GameAction.OpenDailyChallenge -> openDailyChallenge()
            GameAction.OpenInfiniteMode -> openInfiniteMode()
            GameAction.CloseInfiniteMode -> closeOverlay(AppDestination.INFINITE)
            is GameAction.SelectInfiniteDifficulty -> selectInfiniteDifficulty(action.difficulty)
            GameAction.NewInfinitePuzzle -> newInfinitePuzzle()
            GameAction.OpenLevelSelection -> openLevelSelection()
            GameAction.CloseLevelSelection -> closeOverlay(AppDestination.LEVELS)
            GameAction.OpenSettings -> openSettings()
            GameAction.CloseSettings -> closeOverlay(AppDestination.SETTINGS)
            is GameAction.SelectLevel -> selectLevel(action.index)
            is GameAction.UpdateSetting -> updateSetting(action.key, action.enabled)
            GameAction.RequestHint -> requestHint()
            GameAction.UseCoinHint -> requestHint(HintPayment.Coins)
            is GameAction.UseRewardedHintCredit -> requestHint(HintPayment.Rewarded(action.transactionId))
            is GameAction.ShowHintMessage -> showHintMessage(action.message)
            is GameAction.ApplyRewardedSkip -> applyRewardedSkip(action.receipt)
            GameAction.NextLevel -> nextLevel()
        }
    }

    private fun launchArrow(arrowId: String) {
        val current = _uiState.value
        if (!current.inputEnabled || current.isComplete || current.inFlightResult != null) return
        if (current.boardState.arrow(arrowId) == null) return

        cancelHint(current)
        val state = _uiState.value
        val resolution = engine.resolve(state.boardState, PlayerAction(arrowId))
        _uiState.value = state.copy(
            inFlightResult = resolution,
            animationPhase = TurnAnimationPhase.IDLE,
            inputEnabled = false,
        )
        emit(FeedbackEvent.SELECT)
    }

    private fun updateAnimationPhase(phase: TurnAnimationPhase) {
        val state = _uiState.value
        if (state.inFlightResult == null || state.animationPhase == phase) return
        _uiState.value = state.copy(animationPhase = phase)
        when (phase) {
            TurnAnimationPhase.ROUTE -> emit(FeedbackEvent.ARROW_TRAVEL)
            TurnAnimationPhase.IMPACT -> emit(FeedbackEvent.COLLISION)
            TurnAnimationPhase.POLARITY_FLIP -> emit(FeedbackEvent.POLARITY_FLIP)
            TurnAnimationPhase.IDLE, TurnAnimationPhase.REWIND -> Unit
        }
    }

    private fun completeAnimation() {
        val state = _uiState.value
        val result = state.inFlightResult ?: return
        val nextActions = state.moves + 1
        val nextOverloads = state.overloads + if (result.success) 0 else 1
        val campaignWin = result.isWin && state.gameMode == GameMode.CAMPAIGN
        val wasFirstClear = campaignWin && state.currentLevel.id !in state.progress.firstClearRewardedLevelIds
        val completedIds = if (campaignWin) {
            state.progress.completedLevelIds + state.currentLevel.id
        } else {
            state.progress.completedLevelIds
        }
        val highestUnlocked = if (campaignWin) {
            maxOf(
                state.progress.highestUnlockedLevel,
                (state.currentLevelIndex + 2).coerceAtMost(state.levels.size),
            )
        } else {
            state.progress.highestUnlockedLevel
        }
        val bestMoves = if (campaignWin) {
            state.progress.bestMovesByLevel + (
                state.currentLevel.id to minOf(
                    state.progress.bestMovesByLevel[state.currentLevel.id] ?: Int.MAX_VALUE,
                    nextActions,
                )
            )
        } else {
            state.progress.bestMovesByLevel
        }
        val immediateReceipt = if (result.isWin) gradeLocally(state, nextActions, nextOverloads) else null
        val immediateRecords = if (campaignWin && immediateReceipt != null) {
            state.progress.recordsByLevel + (state.currentLevel.id to immediateReceipt.bestRecord)
        } else {
            state.progress.recordsByLevel
        }
        val completedDailyIds = if (result.isWin && state.gameMode == GameMode.DAILY && state.dailyId != null) {
            state.progress.completedDailyIds + state.dailyId
        } else {
            state.progress.completedDailyIds
        }

        _uiState.value = state.copy(
            boardState = result.resultingState,
            inFlightResult = null,
            animationPhase = TurnAnimationPhase.IDLE,
            inputEnabled = !result.isWin,
            isComplete = result.isWin,
            isDeadlocked = result.isDeadlocked,
            moves = nextActions,
            overloads = nextOverloads,
            completionReceipt = immediateReceipt,
            completionWasFirstClear = wasFirstClear,
            progress = state.progress.copy(
                highestUnlockedLevel = highestUnlocked,
                completedLevelIds = completedIds,
                bestMovesByLevel = bestMoves,
                recordsByLevel = immediateRecords,
                completedDailyIds = completedDailyIds,
            ),
        )
        emitTerminalFeedback(result)
        if (result.isWin) {
            emit(FeedbackEvent.BOARD_COMPLETION)
            persistCompletion(state, nextActions, nextOverloads)
            val grade = requireNotNull(immediateReceipt).grade
            when (state.gameMode) {
                GameMode.CAMPAIGN -> analytics.track(
                    AnalyticsEvent.LevelComplete(
                        levelId = state.currentLevel.id,
                        stars = grade.stars,
                        actions = nextActions,
                        overloads = nextOverloads,
                        hints = state.hintsUsed,
                        durationBucket = AnalyticsBuckets.duration((elapsedMillis() - attemptStartedMillis) / 1_000L),
                    ),
                )
                GameMode.DAILY -> analytics.track(
                    AnalyticsEvent.DailyComplete(
                        difficulty = state.currentLevel.metadata?.difficultyBand?.name?.lowercase() ?: "unknown",
                        stars = grade.stars,
                        streakBucket = AnalyticsBuckets.count(state.progress.currentStreak),
                    ),
                )
                GameMode.INFINITE -> analytics.track(
                    AnalyticsEvent.InfiniteComplete(
                        difficulty = state.infiniteDifficulty?.name?.lowercase() ?: "unknown",
                        actionsBucket = AnalyticsBuckets.count(nextActions),
                        overloadsBucket = AnalyticsBuckets.count(nextOverloads),
                        hintsBucket = AnalyticsBuckets.count(state.hintsUsed),
                    ),
                )
            }
        } else if (result.isDeadlocked && !state.isDeadlocked) {
            analytics.track(AnalyticsEvent.LevelDeadlock(state.currentLevel.id, AnalyticsBuckets.count(nextActions)))
        }
    }

    private fun gradeLocally(state: GameUiState, actions: Int, overloads: Int): CompletionReceipt {
        val thresholds = state.currentLevel.metadata?.grading ?: GradingThresholds(
            state.currentLevel.arrows.size,
            state.currentLevel.arrows.size + maxOf(2, (state.currentLevel.arrows.size + 3) / 4),
        )
        val grade = GradingPolicy.grade(actions, overloads, state.hintsUsed, thresholds)
        val previous = state.progress.recordsByLevel[state.currentLevel.id] ?: LevelRecord()
        val best = LevelRecord(
            bestStars = maxOf(previous.bestStars, grade.stars),
            lowestActions = minOf(previous.lowestActions ?: Int.MAX_VALUE, actions),
            lowestOverloads = minOf(previous.lowestOverloads ?: Int.MAX_VALUE, overloads),
            lowestHints = minOf(previous.lowestHints ?: Int.MAX_VALUE, state.hintsUsed),
        )
        return CompletionReceipt(
            grade = grade,
            bestRecord = best,
            rewards = RewardBreakdown(resultingBalance = state.progress.coinBalance),
        )
    }

    private fun persistCompletion(state: GameUiState, actions: Int, overloads: Int) {
        val repository = progressRepository ?: return
        val levelId = state.currentLevel.id
        val mode = state.gameMode
        val dailyId = state.dailyId
        val infinitePuzzleId = state.infinitePuzzleId
        viewModelScope.launch {
            if (mode == GameMode.CAMPAIGN) {
                val receipt = repository.recordCampaignCompletion(
                    levelId,
                    AttemptSummary(actions, overloads, state.hintsUsed),
                )
                val current = _uiState.value
                if (current.isComplete && current.gameMode == mode && current.currentLevel.id == levelId) {
                    _uiState.value = current.copy(
                        completionReceipt = receipt,
                        progress = current.progress.copy(
                            coinBalance = receipt.rewards.resultingBalance,
                            recordsByLevel = current.progress.recordsByLevel + (levelId to receipt.bestRecord),
                            firstClearRewardedLevelIds = current.progress.firstClearRewardedLevelIds + levelId,
                        ),
                    )
                }
            } else if (mode == GameMode.DAILY && dailyId != null) {
                val dailyReceipt = repository.recordDailyCompletion(dailyId)
                val current = _uiState.value
                if (current.isComplete && current.gameMode == mode && current.dailyId == dailyId) {
                    val localGrade = requireNotNull(current.completionReceipt)
                    _uiState.value = current.copy(
                        completionReceipt = localGrade.copy(rewards = dailyReceipt.rewards),
                        progress = current.progress.copy(
                            coinBalance = dailyReceipt.rewards.resultingBalance,
                            completedDailyIds = current.progress.completedDailyIds + dailyId,
                            rewardedDailyIds = if (dailyReceipt.rewards.dailyReward > 0) {
                                current.progress.rewardedDailyIds + dailyId
                            } else {
                                current.progress.rewardedDailyIds
                            },
                            currentStreak = dailyReceipt.currentStreak,
                            bestStreak = dailyReceipt.bestStreak,
                        ),
                    )
                }
            } else if (mode == GameMode.INFINITE && infinitePuzzleId != null) {
                val receipt = repository.recordInfiniteCompletion(
                    infinitePuzzleId,
                    AttemptSummary(actions, overloads, state.hintsUsed),
                )
                val current = _uiState.value
                if (current.isComplete && current.gameMode == mode && current.infinitePuzzleId == infinitePuzzleId) {
                    val localGrade = requireNotNull(current.completionReceipt)
                    _uiState.value = current.copy(
                        completionReceipt = localGrade.copy(rewards = receipt.rewards),
                        progress = current.progress.copy(
                            coinBalance = receipt.rewards.resultingBalance,
                            infinite = current.progress.infinite.copy(
                                completedCount = receipt.completedCount,
                                currentStreak = receipt.currentStreak,
                                bestStreak = receipt.bestStreak,
                                history = current.progress.infinite.history.map { entry ->
                                    if (entry.puzzleId == infinitePuzzleId) {
                                        entry.copy(
                                            completed = true,
                                            actions = actions,
                                            overloads = overloads,
                                            hintsUsed = state.hintsUsed,
                                        )
                                    } else {
                                        entry
                                    }
                                },
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun restart() {
        val current = _uiState.value
        if (!current.canRestart) return
        cancelHint(current)
        val state = _uiState.value
        _uiState.value = state.copy(
            boardState = state.initialState,
            inFlightResult = null,
            animationPhase = TurnAnimationPhase.IDLE,
            inputEnabled = true,
            isComplete = false,
            isDeadlocked = engine.isDeadlocked(state.initialState),
            moves = 0,
            overloads = 0,
            hintsUsed = 0,
            completionReceipt = null,
            completionWasFirstClear = false,
        )
        attemptStartedMillis = elapsedMillis()
        analytics.track(AnalyticsEvent.LevelRestart(state.currentLevel.id, AnalyticsBuckets.count(state.moves + 1)))
        emit(FeedbackEvent.RESTART)
    }

    private fun navigateHome() {
        val state = _uiState.value
        if (state.inFlightResult != null || state.isHintPurchaseInProgress) return
        cancelHint(state)
        val current = _uiState.value
        if (current.gameMode != GameMode.CAMPAIGN) {
            val selected = catalog.levels.indexOfFirst { it.id == current.progress.lastSelectedLevelId }
                .coerceAtLeast(0)
            _uiState.value = createLevelState(selected, current, AppDestination.HOME)
        } else if (current.isComplete && current.hasNextLevel) {
            val nextIndex = current.currentLevelIndex + 1
            _uiState.value = createLevelState(
                levelIndex = nextIndex,
                previous = current,
                destination = AppDestination.HOME,
            )
            progressRepository?.let { repository ->
                viewModelScope.launch { repository.selectLevel(catalog.levels[nextIndex].id) }
            }
        } else if (current.isComplete) {
            _uiState.value = createLevelState(
                levelIndex = current.currentLevelIndex,
                previous = current,
                destination = AppDestination.HOME,
            )
        } else {
            _uiState.value = current.copy(destination = AppDestination.HOME)
        }
    }

    private fun play() {
        val state = _uiState.value
        if (state.inFlightResult != null) return
        infiniteModeService?.let { service ->
            cancelHint(state)
            val decision = service.resumeOrSelect(InfiniteDifficulty.PROGRESSIVE, state.progress.infinite)
            openInfiniteDecision(decision, _uiState.value)
            return
        }
        _uiState.value = state.copy(destination = AppDestination.GAME)
        attemptStartedMillis = elapsedMillis()
        trackLevelStart(state, "home")
    }

    private fun openDailyChallenge() {
        val service = dailyChallengeService
        val state = _uiState.value
        if (service == null) {
            _uiState.value = state.copy(dailyError = "Daily Challenge is unavailable in this build.")
            return
        }
        if (state.inFlightResult != null || state.isDailyLoading || state.isHintPurchaseInProgress) return
        cancelHint(state)
        val loadingState = _uiState.value.copy(isDailyLoading = true, dailyError = null)
        _uiState.value = loadingState
        val date = dateProvider.currentLocalDate()
        viewModelScope.launch {
            runCatching { service.load(date, loadingState.progress.dailyCache) }
                .onSuccess { challenge ->
                    val current = _uiState.value
                    _uiState.value = createDailyState(challenge.level, current).copy(
                        dailyId = challenge.identity.dailyId,
                        dailyDateLabel = challenge.identity.localDate.toString(),
                        dailyLoadSource = challenge.source,
                        isDailyLoading = false,
                        destination = AppDestination.GAME,
                    )
                    attemptStartedMillis = elapsedMillis()
                    analytics.track(
                        AnalyticsEvent.DailyStart(
                            challenge.level.metadata?.difficultyBand?.name?.lowercase() ?: "unknown",
                        ),
                    )
                    if (challenge.source == DailyLoadSource.BUNDLED_FALLBACK) {
                        crashReporter.recordUnexpected(IllegalStateException("Daily generator fallback activated"))
                    }
                    progressRepository?.cacheDailyChallenge(challenge.cache)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isDailyLoading = false,
                        dailyError = error.message ?: "Daily Challenge could not be prepared.",
                    )
                }
        }
    }

    private fun openInfiniteMode() {
        val state = _uiState.value
        if (state.inFlightResult != null || state.isHintPurchaseInProgress) return
        if (!debugUnlockAll && !state.infiniteUnlocked) return
        cancelHint(state)
        _uiState.value = _uiState.value.copy(
            destination = AppDestination.INFINITE,
            returnDestination = AppDestination.HOME,
        )
    }

    private fun selectInfiniteDifficulty(difficulty: InfiniteDifficulty) {
        val state = _uiState.value
        val service = infiniteModeService ?: return
        if (state.destination != AppDestination.INFINITE || (!debugUnlockAll && !state.infiniteUnlocked)) return
        val decision = service.resumeOrSelect(difficulty, state.progress.infinite)
        openInfiniteDecision(decision, state)
    }

    private fun newInfinitePuzzle() {
        val state = _uiState.value
        val service = infiniteModeService ?: return
        val difficulty = state.infiniteDifficulty ?: return
        if (state.gameMode != GameMode.INFINITE || !state.isComplete) return
        openInfiniteDecision(service.selectNew(difficulty, state.progress.infinite), state)
    }

    private fun openInfiniteDecision(decision: InfiniteSelectionDecision, previous: GameUiState) {
        val identity = decision.identity
        val existingHistory = previous.progress.infinite.history
        val history = if (existingHistory.none { it.ordinal == decision.selectionOrdinal }) {
            (existingHistory + com.rameshta.magnetrail.data.InfiniteHistoryEntry(
                ordinal = decision.selectionOrdinal,
                puzzleId = identity.puzzleId,
                contentFingerprint = identity.contentHash,
                difficulty = decision.requestedDifficulty.name,
            )).takeLast(com.rameshta.magnetrail.core.infinite.INFINITE_HISTORY_LIMIT)
        } else {
            existingHistory
        }
        val progress = previous.progress.copy(
            infinite = previous.progress.infinite.copy(
                selectedPuzzleId = identity.puzzleId,
                selectedDifficulty = decision.requestedDifficulty.name,
                selectionOrdinal = decision.selectionOrdinal,
                history = history,
            ),
        )
        _uiState.value = createInfiniteState(decision, previous.copy(progress = progress))
        attemptStartedMillis = elapsedMillis()
        analytics.track(
            AnalyticsEvent.InfiniteStart(
                difficulty = decision.requestedDifficulty.name.lowercase(),
                fallback = decision.fallbackUsed,
            ),
        )
        progressRepository?.let { repository ->
            viewModelScope.launch {
                repository.recordInfiniteSelection(
                    puzzleId = identity.puzzleId,
                    contentFingerprint = identity.contentHash,
                    difficulty = decision.requestedDifficulty.name,
                    ordinal = decision.selectionOrdinal,
                )
            }
        }
    }

    private fun openLevelSelection() {
        val state = _uiState.value
        if (state.inFlightResult != null) return
        cancelHint(state)
        _uiState.value = _uiState.value.copy(
            destination = AppDestination.LEVELS,
            returnDestination = state.destination.takeIf { it != AppDestination.LEVELS }
                ?: AppDestination.HOME,
        )
    }

    private fun openSettings() {
        val state = _uiState.value
        if (state.inFlightResult != null) return
        _uiState.value = state.copy(
            destination = AppDestination.SETTINGS,
            returnDestination = state.destination.takeIf { it != AppDestination.SETTINGS }
                ?: AppDestination.HOME,
        )
    }

    private fun closeOverlay(overlay: AppDestination) {
        val state = _uiState.value
        if (state.destination != overlay) return
        _uiState.value = state.copy(destination = state.returnDestination)
    }

    private fun selectLevel(index: Int) {
        val state = _uiState.value
        if (index !in catalog.levels.indices || !state.isLevelUnlocked(index, debugUnlockAll)) return
        cancelHint(state)
        _uiState.value = createLevelState(
            levelIndex = index,
            previous = _uiState.value,
            destination = AppDestination.GAME,
        )
        attemptStartedMillis = elapsedMillis()
        trackLevelStart(_uiState.value, "level_selection")
        progressRepository?.let { repository ->
            viewModelScope.launch { repository.selectLevel(catalog.levels[index].id) }
        }
    }

    private fun nextLevel() {
        val state = _uiState.value
        if (state.inFlightResult != null || !state.isComplete) return
        if (state.gameMode == GameMode.DAILY) {
            navigateHome()
        } else if (state.gameMode == GameMode.INFINITE) {
            newInfinitePuzzle()
        } else if (state.hasNextLevel) {
            selectLevel(state.currentLevelIndex + 1)
        } else {
            _uiState.value = state.copy(
                destination = AppDestination.LEVELS,
                returnDestination = AppDestination.HOME,
            )
        }
    }

    private fun applyRewardedSkip(receipt: com.rameshta.magnetrail.data.RewardedSkipResult.Applied) {
        val state = _uiState.value
        if (!state.canRequestSkip) return
        cancelHint(state)
        when (state.gameMode) {
            GameMode.DAILY -> return
            GameMode.CAMPAIGN -> {
                val nextProgress = state.progress.copy(
                    highestUnlockedLevel = maxOf(
                        state.progress.highestUnlockedLevel,
                        (state.currentLevelIndex + 2).coerceAtMost(state.levels.size),
                    ),
                    completedLevelIds = state.progress.completedLevelIds + state.currentLevel.id,
                    firstClearRewardedLevelIds = state.progress.firstClearRewardedLevelIds + state.currentLevel.id,
                    coinBalance = receipt.resultingBalance,
                )
                val updated = state.copy(progress = nextProgress, hintMessage = null)
                if (state.hasNextLevel) {
                    val nextIndex = state.currentLevelIndex + 1
                    _uiState.value = createLevelState(nextIndex, updated, AppDestination.GAME)
                    progressRepository?.let { repository ->
                        viewModelScope.launch { repository.selectLevel(catalog.levels[nextIndex].id) }
                    }
                    attemptStartedMillis = elapsedMillis()
                } else {
                    _uiState.value = updated.copy(
                        destination = AppDestination.LEVELS,
                        returnDestination = AppDestination.HOME,
                    )
                }
            }
            GameMode.INFINITE -> {
                val puzzleId = state.infinitePuzzleId ?: return
                val progress = state.progress.copy(
                    coinBalance = receipt.resultingBalance,
                    infinite = state.progress.infinite.copy(
                        completedCount = receipt.completedCount,
                        currentStreak = receipt.currentStreak,
                        bestStreak = receipt.bestStreak,
                        history = state.progress.infinite.history.map { entry ->
                            if (entry.puzzleId == puzzleId &&
                                entry.ordinal == state.progress.infinite.selectionOrdinal
                            ) entry.copy(completed = true) else entry
                        },
                    ),
                )
                val service = infiniteModeService ?: return
                val difficulty = state.infiniteDifficulty ?: return
                val decision = service.selectNew(difficulty, progress.infinite, advance = true)
                openInfiniteDecision(decision, state.copy(progress = progress, hintMessage = null))
            }
        }
    }

    private fun updateSetting(key: SettingKey, enabled: Boolean) {
        val state = _uiState.value
        _uiState.value = state.copy(settings = state.settings.withValue(key, enabled))
        progressRepository?.let { repository ->
            viewModelScope.launch { repository.updateSetting(key, enabled) }
        }
        if (key == SettingKey.DIAGNOSTICS) {
            analytics.track(AnalyticsEvent.DiagnosticsSettingChanged(enabled))
        }
    }

    private fun showHintMessage(message: String) {
        _uiState.value = _uiState.value.copy(hintMessage = message.take(100))
    }

    private fun requestHint(payment: HintPayment = HintPayment.Coins) {
        val state = _uiState.value
        if (!state.canRequestHint || hintJob?.isActive == true) return
        if (payment is HintPayment.Coins && progressRepository != null && state.progress.coinBalance < EconomyConfig.HINT_COST) {
            _uiState.value = state.copy(
                hintMessage = "A hint costs ${EconomyConfig.HINT_COST} coins. Balance: ${state.progress.coinBalance}.",
            )
            return
        }
        val requestedBoard = state.boardState
        val requestedLevelId = state.currentLevel.id
        val generation = ++hintGeneration
        _uiState.value = state.copy(hintMessage = null)
        hintJob = viewModelScope.launch {
            val loadingJob = launch {
                delay(HINT_LOADING_DELAY_MILLIS)
                if (hintGeneration == generation) {
                    _uiState.value = _uiState.value.copy(isHintLoading = true, hintMessage = "Finding a clean move")
                }
            }
            val outcome = withTimeoutOrNull(HINT_TIMEOUT_MILLIS) {
                hintProvider.hintFor(requestedBoard)
            }
            loadingJob.cancel()
            val current = _uiState.value
            val stale = hintGeneration != generation ||
                current.currentLevel.id != requestedLevelId ||
                current.boardState != requestedBoard ||
                current.inFlightResult != null
            if (stale) return@launch

            when (outcome) {
                is HintOutcome.SuggestedArrow -> {
                    val valid = engine.validActions(current.boardState)
                        .any { it.arrowId == outcome.arrowId }
                    if (!valid) {
                        showHintFallback(current)
                        return@launch
                    }
                    val preview = if (current.settings.pathPreviewAssistance) {
                        engine.resolve(current.boardState, PlayerAction(outcome.arrowId))
                    } else {
                        null
                    }
                    val repository = progressRepository
                    if (repository == null && payment is HintPayment.Coins) {
                        showSuggestedHint(current, outcome.arrowId, preview, current.progress.coinBalance, "coins")
                    } else if (repository != null) {
                        _uiState.value = current.copy(
                            isHintLoading = false,
                            isHintPurchaseInProgress = true,
                            inputEnabled = false,
                            hintMessage = "Preparing hint",
                        )
                        when (payment) {
                            HintPayment.Coins -> when (val spend = repository.spendHintCoins()) {
                                is HintSpendResult.Approved -> {
                                    analytics.track(AnalyticsEvent.HintCoinSpend(AnalyticsBuckets.count(spend.resultingBalance)))
                                    val latest = _uiState.value
                                    val stillCurrent = hintGeneration == generation &&
                                        latest.currentLevel.id == requestedLevelId && latest.boardState == requestedBoard
                                    check(stillCurrent) { "Hint state changed during serialized coin transaction" }
                                    showSuggestedHint(latest, outcome.arrowId, preview, spend.resultingBalance, "coins")
                                }
                                is HintSpendResult.InsufficientBalance -> {
                                    _uiState.value = _uiState.value.copy(
                                        isHintPurchaseInProgress = false,
                                        inputEnabled = true,
                                        hintMessage = "A hint costs ${spend.required} coins. Balance: ${spend.balance}.",
                                        progress = _uiState.value.progress.copy(coinBalance = spend.balance),
                                    )
                                }
                            }
                            is HintPayment.Rewarded -> if (repository.consumeRewardedHintCredit(payment.transactionId)) {
                                val latest = _uiState.value
                                val stillCurrent = hintGeneration == generation &&
                                    latest.currentLevel.id == requestedLevelId && latest.boardState == requestedBoard
                                check(stillCurrent) { "Hint state changed during serialized coin transaction" }
                                showSuggestedHint(latest, outcome.arrowId, preview, latest.progress.coinBalance, "rewarded")
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    isHintPurchaseInProgress = false,
                                    inputEnabled = true,
                                    hintMessage = "No earned ad hint is available.",
                                )
                            }
                        }
                    } else {
                        showHintFallback(current)
                    }
                }
                HintOutcome.NoSolution, null -> showHintFallback(current)
            }
        }
    }

    private fun showSuggestedHint(
        state: GameUiState,
        arrowId: String,
        preview: com.rameshta.magnetrail.core.engine.ResolutionResult?,
        resultingBalance: Int,
        source: String,
    ) {
        _uiState.value = state.copy(
            isHintLoading = false,
            isHintPurchaseInProgress = false,
            inputEnabled = true,
            suggestedArrowId = arrowId,
            hintMessage = "Hint: Try arrow $arrowId",
            hintPreviewResult = preview,
            hintsUsed = state.hintsUsed + 1,
            progress = state.progress.copy(coinBalance = resultingBalance),
        )
        analytics.track(AnalyticsEvent.HintShown(source))
    }

    private fun showHintFallback(state: GameUiState) {
        _uiState.value = state.copy(
            isHintLoading = false,
            isHintPurchaseInProgress = false,
            hintMessage = "No hint is available. Restart to keep exploring.",
        )
    }

    private fun cancelHint(state: GameUiState) {
        hintGeneration += 1
        hintJob?.cancel()
        hintJob = null
        if (state.isHintLoading || state.suggestedArrowId != null || state.hintMessage != null) {
            _uiState.value = state.copy(
                isHintLoading = false,
                suggestedArrowId = null,
                hintMessage = null,
                hintPreviewResult = null,
            )
        }
    }

    private fun applyPreferences(preferences: PlayerPreferences) {
        val state = _uiState.value
        val selectedIndex = catalog.levels.indexOfFirst {
            it.id == preferences.progress.lastSelectedLevelId
        }.coerceAtLeast(0)
        val canRestoreSelection = state.destination == AppDestination.HOME &&
            state.gameMode == GameMode.CAMPAIGN &&
            state.inFlightResult == null && state.moves == 0 &&
            state.boardState == state.initialState
        _uiState.value = if (canRestoreSelection && selectedIndex != state.currentLevelIndex) {
            createLevelState(
                levelIndex = selectedIndex,
                previous = state.copy(
                    settings = preferences.settings,
                    progress = preferences.progress,
                    preferencesLoaded = true,
                ),
                destination = AppDestination.HOME,
            )
        } else {
            state.copy(
                settings = preferences.settings,
                progress = preferences.progress,
                preferencesLoaded = true,
                playDifficultyLabel = progressivePlayDifficulty(preferences.progress),
            )
        }
    }

    private fun emitTerminalFeedback(result: com.rameshta.magnetrail.core.engine.ResolutionResult) {
        val event = when (result.terminalEvent) {
            is TerminalEvent.PullCapture -> FeedbackEvent.PULL_CAPTURE
            is TerminalEvent.Exit -> if (result.polarityChange?.from == com.rameshta.magnetrail.core.model.Polarity.PUSH) {
                FeedbackEvent.PUSH_EXIT
            } else {
                FeedbackEvent.ARROW_EXIT
            }
            is TerminalEvent.InvalidPullExit -> FeedbackEvent.INVALID_MOVE
            is TerminalEvent.Collision -> null
        }
        event?.let(::emit)
    }

    private fun emit(event: FeedbackEvent) {
        _feedbackEvents.tryEmit(event)
    }

    private fun trackLevelStart(state: GameUiState, origin: String) {
        if (state.gameMode != GameMode.CAMPAIGN) return
        analytics.track(
            AnalyticsEvent.LevelStart(
                levelId = state.currentLevel.id,
                pack = state.currentLevel.metadata?.packId ?: "unknown",
                difficulty = state.currentLevel.metadata?.difficultyBand?.name?.lowercase() ?: "unknown",
                origin = origin,
            ),
        )
    }

    private sealed interface HintPayment {
        data object Coins : HintPayment
        data class Rewarded(val transactionId: String) : HintPayment
    }

    private fun createLevelState(
        levelIndex: Int,
        previous: GameUiState? = null,
        destination: AppDestination = AppDestination.HOME,
    ): GameUiState {
        require(catalog.levels.isNotEmpty()) { "Magnetrail requires at least one validated level" }
        val level = catalog.levels[levelIndex]
        val initialState = level.initialState()
        val existingProgress = previous?.progress ?: PlayerProgress(
            lastSelectedLevelId = catalog.levels.first().id,
        )
        val today = dateProvider.currentLocalDate()
        val todayIdentity = DailySeed.identity(today)
        return GameUiState(
            levels = catalog.levels.toList(),
            currentLevelIndex = levelIndex,
            currentLevel = level,
            initialState = initialState,
            boardState = initialState,
            destination = destination,
            returnDestination = previous?.returnDestination ?: AppDestination.HOME,
            settings = previous?.settings ?: com.rameshta.magnetrail.data.PlayerSettings(),
            progress = existingProgress.copy(lastSelectedLevelId = level.id),
            preferencesLoaded = previous?.preferencesLoaded ?: false,
            dailyId = todayIdentity.dailyId,
            dailyDateLabel = today.toString(),
            infiniteCatalogSize = infiniteModeService?.catalogSize ?: 0,
            playDifficultyLabel = progressivePlayDifficulty(existingProgress),
            isDeadlocked = engine.isDeadlocked(initialState),
        )
    }

    private fun createDailyState(
        level: com.rameshta.magnetrail.core.model.LevelDefinition,
        previous: GameUiState,
    ): GameUiState {
        val initialState = level.initialState()
        return GameUiState(
            levels = catalog.levels.toList(),
            currentLevelIndex = -1,
            currentLevel = level,
            initialState = initialState,
            boardState = initialState,
            destination = AppDestination.GAME,
            returnDestination = AppDestination.HOME,
            settings = previous.settings,
            progress = previous.progress,
            preferencesLoaded = previous.preferencesLoaded,
            gameMode = GameMode.DAILY,
            infiniteCatalogSize = previous.infiniteCatalogSize,
            playDifficultyLabel = previous.playDifficultyLabel,
            isDeadlocked = engine.isDeadlocked(initialState),
        )
    }

    private fun createInfiniteState(
        decision: InfiniteSelectionDecision,
        previous: GameUiState,
    ): GameUiState {
        val level = decision.level
        val initialState = level.initialState()
        return GameUiState(
            levels = catalog.levels.toList(),
            currentLevelIndex = -1,
            currentLevel = level,
            initialState = initialState,
            boardState = initialState,
            destination = AppDestination.GAME,
            returnDestination = AppDestination.INFINITE,
            settings = previous.settings,
            progress = previous.progress,
            preferencesLoaded = previous.preferencesLoaded,
            gameMode = GameMode.INFINITE,
            dailyId = null,
            dailyDateLabel = previous.dailyDateLabel,
            infinitePuzzleId = decision.identity.puzzleId,
            infiniteDifficulty = decision.requestedDifficulty,
            infiniteFallbackUsed = decision.fallbackUsed,
            infiniteSelectionReason = decision.reason,
            infiniteCatalogSize = previous.infiniteCatalogSize,
            playDifficultyLabel = decision.selectedProfile.toPlayerDifficultyName(),
            isDeadlocked = engine.isDeadlocked(initialState),
        )
    }

    private fun progressivePlayDifficulty(progress: PlayerProgress): String = infiniteModeService
        ?.previewProgressiveDifficulty(progress.infinite)
        ?: "Easy"

    private fun String.toPlayerDifficultyName(): String = when {
        endsWith("-very-hard") -> InfiniteDifficulty.VERY_HARD.displayName
        endsWith("-master") -> InfiniteDifficulty.MASTER.displayName
        endsWith("-expert") -> InfiniteDifficulty.EXPERT.displayName
        endsWith("-hard") -> InfiniteDifficulty.CHALLENGING.displayName
        endsWith("-medium") -> InfiniteDifficulty.BALANCED.displayName
        endsWith("-easy") -> InfiniteDifficulty.RELAXED.displayName
        else -> "Certified"
    }

    companion object {
        private const val HINT_LOADING_DELAY_MILLIS = 120L
        private const val HINT_TIMEOUT_MILLIS = 2_000L

        fun factory(
            catalog: LevelCatalog,
            repository: ProgressRepository,
            dailyChallengeService: DailyChallengeService,
            infiniteModeService: InfiniteModeService,
            debugUnlockAll: Boolean,
            analytics: AnalyticsTracker = NoOpAnalyticsTracker,
            crashReporter: CrashReporter = NoOpCrashReporter,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(GameViewModel::class.java)) {
                    "Unsupported ViewModel class: ${modelClass.name}"
                }
                return GameViewModel(
                    catalog = catalog,
                    progressRepository = repository,
                    dailyChallengeService = dailyChallengeService,
                    infiniteModeService = infiniteModeService,
                    debugUnlockAll = debugUnlockAll,
                    analytics = analytics,
                    crashReporter = crashReporter,
                ) as T
            }
        }
    }
}

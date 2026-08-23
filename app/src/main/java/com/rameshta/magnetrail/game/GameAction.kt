package com.rameshta.magnetrail.game

import com.rameshta.magnetrail.data.SettingKey
import com.rameshta.magnetrail.core.infinite.InfiniteDifficulty
import com.rameshta.magnetrail.data.RewardedSkipResult
import com.rameshta.magnetrail.playtest.HumanPlaytestGuessResponse

sealed interface GameAction {
    data class LaunchArrow(val arrowId: String) : GameAction

    data class AnimationPhaseChanged(val phase: TurnAnimationPhase) : GameAction

    data object AnimationCompleted : GameAction

    data object Restart : GameAction

    data object NavigateHome : GameAction

    data object Play : GameAction

    data object OpenDailyChallenge : GameAction

    data object OpenInfiniteMode : GameAction

    data object CloseInfiniteMode : GameAction

    data class SelectInfiniteDifficulty(val difficulty: InfiniteDifficulty) : GameAction

    data object NewInfinitePuzzle : GameAction

    data object OpenLevelSelection : GameAction

    data object CloseLevelSelection : GameAction

    data object OpenSettings : GameAction

    data object CloseSettings : GameAction

    data object OpenPrivacyPolicy : GameAction

    data object ClosePrivacyPolicy : GameAction

    data object OpenHumanPlaytest : GameAction

    data object CloseHumanPlaytest : GameAction

    data class UpdateHumanPlaytestParticipant(val value: String) : GameAction

    data object StartHumanPlaytest : GameAction

    data object ResumeHumanPlaytest : GameAction

    data object AbandonHumanPlaytestBoard : GameAction

    data class RateHumanPlaytestBoard(val rating: Int) : GameAction

    data class RateHumanPlaytestFairness(val rating: Int) : GameAction

    data class SetHumanPlaytestGuessRequired(val required: Boolean) : GameAction

    data class SetHumanPlaytestGuessResponse(val response: HumanPlaytestGuessResponse) : GameAction

    data class SetHumanPlaytestRepeatedStrategy(val repeated: Boolean) : GameAction

    data class UpdateHumanPlaytestComment(val value: String) : GameAction

    data object SubmitHumanPlaytestFeedback : GameAction

    data object ExportHumanPlaytest : GameAction

    data class HumanPlaytestExportFinished(val message: String) : GameAction

    data object ClearHumanPlaytest : GameAction

    data class SelectLevel(val index: Int) : GameAction

    data class UpdateSetting(val key: SettingKey, val enabled: Boolean) : GameAction

    data object RequestHint : GameAction

    data object UseCoinHint : GameAction

    data class UseRewardedHintCredit(val transactionId: String) : GameAction

    data class ShowHintMessage(val message: String) : GameAction

    data class ApplyRewardedSkip(val receipt: RewardedSkipResult.Applied) : GameAction

    data object Replay : GameAction

    data object NextLevel : GameAction
}

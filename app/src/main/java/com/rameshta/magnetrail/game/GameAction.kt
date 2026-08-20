package com.rameshta.magnetrail.game

import com.rameshta.magnetrail.data.SettingKey
import com.rameshta.magnetrail.core.infinite.InfiniteDifficulty
import com.rameshta.magnetrail.data.RewardedSkipResult

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

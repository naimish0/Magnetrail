package com.rameshta.magnetrail.game

import com.rameshta.magnetrail.data.SettingKey

sealed interface GameAction {
    data class LaunchArrow(val arrowId: String) : GameAction

    data class AnimationPhaseChanged(val phase: TurnAnimationPhase) : GameAction

    data object AnimationCompleted : GameAction

    data object Undo : GameAction

    data object Restart : GameAction

    data object NavigateHome : GameAction

    data object Play : GameAction

    data object OpenLevelSelection : GameAction

    data object CloseLevelSelection : GameAction

    data object OpenSettings : GameAction

    data object CloseSettings : GameAction

    data class SelectLevel(val index: Int) : GameAction

    data class UpdateSetting(val key: SettingKey, val enabled: Boolean) : GameAction

    data object RequestHint : GameAction

    data object Replay : GameAction

    data object NextLevel : GameAction
}

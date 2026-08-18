package com.rameshta.magnetrail.game

sealed interface GameAction {
    data class LaunchArrow(val arrowId: String) : GameAction

    data class AnimationPhaseChanged(val phase: TurnAnimationPhase) : GameAction

    data object AnimationCompleted : GameAction

    data object Undo : GameAction

    data object Restart : GameAction

    data object OpenLevelSelection : GameAction

    data object CloseLevelSelection : GameAction

    data class SelectLevel(val index: Int) : GameAction

    data object Replay : GameAction

    data object NextLevel : GameAction
}

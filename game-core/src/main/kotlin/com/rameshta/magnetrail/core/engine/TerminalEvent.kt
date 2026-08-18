package com.rameshta.magnetrail.core.engine

import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Position

enum class CollisionTargetType {
    ARROW,
    WALL,
    MAGNET,
}

data class CollisionTarget(
    val type: CollisionTargetType,
    val position: Position,
    val entityId: String? = null,
)

sealed interface TerminalEvent {
    data class Exit(
        val lastBoardPosition: Position,
        val direction: Direction,
    ) : TerminalEvent

    data class PullCapture(
        val magnetId: String,
        val position: Position,
    ) : TerminalEvent

    data class Collision(
        val target: CollisionTarget,
    ) : TerminalEvent

    data class InvalidPullExit(
        val magnetId: String,
        val lastBoardPosition: Position,
        val direction: Direction,
    ) : TerminalEvent
}

package com.rameshta.magnetrail.core.solver

import com.rameshta.magnetrail.core.engine.PlayerAction

data class SolverResult(
    val solvable: Boolean,
    val oneCleanSolution: List<PlayerAction>?,
    val solutionCount: Int,
    val solutionCountCapped: Boolean,
    val shortestDepth: Int?,
    val validFirstActions: List<PlayerAction>,
    val exploredStateCount: Int,
    val searchComplete: Boolean = true,
    val terminationReason: String? = null,
)

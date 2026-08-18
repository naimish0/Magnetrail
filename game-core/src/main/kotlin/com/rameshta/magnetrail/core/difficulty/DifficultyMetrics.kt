package com.rameshta.magnetrail.core.difficulty

import com.rameshta.magnetrail.core.model.DifficultyBand

data class DifficultyMetrics(
    val solutionLength: Int,
    val solutionCount: Int,
    val solutionCountCapped: Boolean,
    val validFirstActionCount: Int,
    val averageBranching: Double,
    val magnetControlledActions: Int,
    val polarityFlips: Int,
    val exploredStateCount: Int,
    val hasPull: Boolean,
    val hasPush: Boolean,
    val hasWalls: Boolean,
    val hasOcclusionOpportunity: Boolean,
    val hasCancellationOpportunity: Boolean,
    val hasDeadEndOpportunity: Boolean,
    val estimatedDifficultyScore: Int,
    val assignedBand: DifficultyBand,
)

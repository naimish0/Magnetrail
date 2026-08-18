package com.rameshta.magnetrail.core.grading

import com.rameshta.magnetrail.core.model.GradingThresholds

data class AttemptGrade(
    val stars: Int,
    val actions: Int,
    val overloads: Int,
    val hintsUsed: Int,
)

object GradingPolicy {
    const val VERSION = 1

    fun grade(
        actions: Int,
        overloads: Int,
        hintsUsed: Int,
        thresholds: GradingThresholds,
    ): AttemptGrade {
        require(actions > 0) { "A completed attempt must contain at least one action" }
        require(overloads >= 0 && hintsUsed >= 0) { "Attempt counters cannot be negative" }
        val stars = when {
            actions <= thresholds.parActions && hintsUsed == 0 -> 3
            actions <= thresholds.twoStarMaxActions -> 2
            else -> 1
        }
        return AttemptGrade(stars, actions, overloads, hintsUsed)
    }
}

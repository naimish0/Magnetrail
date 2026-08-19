package com.rameshta.magnetrail.core.generation.v5

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable
enum class CompletionTimeBucketV1 { FAST, EXPECTED, SLOW, VERY_SLOW }

@Serializable
data class PlayerPerformanceV1(
    val levelId: String,
    val difficultyBand: StructuralDifficultyBandV5,
    val completed: Boolean,
    val actions: Int,
    val parActions: Int,
    val failedLaunches: Int,
    val restarts: Int,
    val hintsUsed: Int,
    val undosUsed: Int,
    val deadlocks: Int,
    val completionTimeBucket: CompletionTimeBucketV1,
) {
    init {
        require(actions >= 0 && parActions > 0)
        require(listOf(failedLaunches, restarts, hintsUsed, undosUsed, deadlocks).all { it >= 0 })
    }
}

@Serializable
data class PlayerSkillStateV1(
    val skillScore: Int = 25,
    val recentCompletionPerformance: List<Int> = emptyList(),
    val recentFailurePressure: List<Int> = emptyList(),
    val recentHintUsage: List<Int> = emptyList(),
    val recentRestartUsage: List<Int> = emptyList(),
    val recentCompletionTimeBuckets: List<CompletionTimeBucketV1> = emptyList(),
    val skillVersion: Int = D2_SKILL_VERSION,
) {
    init {
        require(skillScore in 0..100)
        require(recentCompletionPerformance.size <= ROLLING_WINDOW_V1)
        require(recentFailurePressure.size <= ROLLING_WINDOW_V1)
        require(recentHintUsage.size <= ROLLING_WINDOW_V1)
        require(recentRestartUsage.size <= ROLLING_WINDOW_V1)
        require(recentCompletionTimeBuckets.size <= ROLLING_WINDOW_V1)
    }
}

@Serializable
data class MechanicExposureStateV1(
    val exposureCounts: Map<String, Int> = emptyMap(),
) {
    fun has(mechanic: String, minimum: Int = 1): Boolean = (exposureCounts[mechanic] ?: 0) >= minimum
}

@Serializable
data class ProgressionStateV1(
    val completedLevelIds: Set<String>,
    val progressionPosition: Int,
    val curriculumProgress: Int,
    val mechanicExposureState: MechanicExposureStateV1,
) {
    init {
        require(progressionPosition >= 0 && curriculumProgress >= 0)
    }
}

@Serializable
data class SelectableLevelV1(
    val levelId: String,
    val difficultyBand: StructuralDifficultyBandV5,
    val gridSize: Int,
    val mechanicTags: Set<String>,
    val requiredPriorMechanics: Set<String> = emptySet(),
    val minimumCurriculumProgress: Int = 0,
    val exactFingerprint: String,
    val symmetryFingerprint: String,
    val interactionFingerprint: String,
    val dependencyFingerprint: String,
    val strategyFingerprint: String,
    val mechanicCombinationFingerprint: String,
    val certified: Boolean = true,
) {
    init {
        require(levelId.isNotBlank() && gridSize in 3..9 && minimumCurriculumProgress >= 0)
    }
}

@Serializable
data class SelectionStateV1(
    val recentLevelIds: List<String> = emptyList(),
    val recentDifficultyHistory: List<StructuralDifficultyBandV5> = emptyList(),
    val recentGridSizes: List<Int> = emptyList(),
    val recentInteractionFingerprints: List<String> = emptyList(),
    val recentDependencyFingerprints: List<String> = emptyList(),
    val recentStrategyFingerprints: List<String> = emptyList(),
    val recentMechanicFingerprints: List<String> = emptyList(),
    val selectionOrdinal: Int = 0,
    val selectionVersion: Int = D2_SELECTION_VERSION,
) {
    init {
        require(selectionOrdinal >= 0)
    }
}

@Serializable
data class NextLevelDecisionV1(
    val selectedLevelId: String,
    val selectedDifficultyBand: StructuralDifficultyBandV5,
    val selectionReason: String,
    val selectionVersion: Int = D2_SELECTION_VERSION,
    val deterministicFallbackUsed: Boolean,
)

/** Transparent, bounded local skill update. No identity, economy, ad, or network signal exists. */
object PlayerSkillModelV1 {
    fun update(state: PlayerSkillStateV1, performance: PlayerPerformanceV1): PlayerSkillStateV1 {
        val efficiency = (performance.parActions.toDouble() / performance.actions.coerceAtLeast(1))
            .coerceIn(0.0, 1.0)
        val timeScore = when (performance.completionTimeBucket) {
            CompletionTimeBucketV1.FAST -> 1.0
            CompletionTimeBucketV1.EXPECTED -> 0.75
            CompletionTimeBucketV1.SLOW -> 0.40
            CompletionTimeBucketV1.VERY_SLOW -> 0.15
        }
        val completionQuality = if (performance.completed) 1.0 else 0.0
        val noRestart = (1.0 - performance.restarts * 0.25).coerceAtLeast(0.0)
        val noHint = (1.0 - performance.hintsUsed * 0.40).coerceAtLeast(0.0)
        val noDeadlock = (1.0 - performance.deadlocks * 0.35).coerceAtLeast(0.0)
        val rawPerformance = (
            completionQuality * 35.0 + efficiency * 20.0 + noRestart * 15.0 +
                noHint * 15.0 + noDeadlock * 10.0 + timeScore * 5.0
            ).roundToInt()
        val expectedForBand = 20 + performance.difficultyBand.rank * 14
        val challengeAdjustment = (expectedForBand - state.skillScore) * 0.04
        val evidenceAdjustment = (rawPerformance - 65) * 0.08
        val completionAdjustment = if (performance.completed) 0.5 else -1.0
        val delta = (challengeAdjustment + evidenceAdjustment + completionAdjustment)
            .roundToInt().coerceIn(-5, 5)
        val failurePressure = performance.failedLaunches + performance.deadlocks * 2 + performance.undosUsed / 2
        return PlayerSkillStateV1(
            skillScore = (state.skillScore + delta).coerceIn(0, 100),
            recentCompletionPerformance = appendWindow(state.recentCompletionPerformance, rawPerformance),
            recentFailurePressure = appendWindow(state.recentFailurePressure, failurePressure),
            recentHintUsage = appendWindow(state.recentHintUsage, performance.hintsUsed),
            recentRestartUsage = appendWindow(state.recentRestartUsage, performance.restarts),
            recentCompletionTimeBuckets = appendWindow(
                state.recentCompletionTimeBuckets,
                performance.completionTimeBucket,
            ),
        )
    }

    private fun <T> appendWindow(values: List<T>, value: T): List<T> =
        (values + value).takeLast(ROLLING_WINDOW_V1)
}

/**
 * Offline deterministic prototype. Runtime integration is intentionally outside D2. Ties are
 * resolved by a versioned stable hash and level ID, never by runtime randomness.
 */
class DifficultySelectionV1 {
    fun selectNextLevel(
        progressionState: ProgressionStateV1,
        playerSkillState: PlayerSkillStateV1,
        contentPool: List<SelectableLevelV1>,
        selectionState: SelectionStateV1,
        contentVersion: Int = D2_STAGING_CONTENT_VERSION,
    ): NextLevelDecisionV1 {
        require(contentPool.isNotEmpty())
        val available = contentPool.filter { level ->
            level.certified &&
                level.levelId !in progressionState.completedLevelIds &&
                progressionState.curriculumProgress >= level.minimumCurriculumProgress &&
                level.requiredPriorMechanics.all(progressionState.mechanicExposureState::has)
        }
        val source = available.ifEmpty {
            contentPool.filter { it.certified && it.levelId !in progressionState.completedLevelIds }
        }.ifEmpty { contentPool.filter { it.certified } }
        require(source.isNotEmpty()) { "Content pool has no certified fallback" }

        val target = targetBand(playerSkillState, progressionState, selectionState)
        val lastBand = selectionState.recentDifficultyHistory.lastOrNull()
        val maximumRank = when {
            lastBand == null -> minOf(target.rank, StructuralDifficultyBandV5.EASY.rank)
            else -> minOf(StructuralDifficultyBandV5.MASTER.rank, lastBand.rank + 1)
        }
        val curriculumMaximum = when {
            progressionState.curriculumProgress < 12 -> StructuralDifficultyBandV5.EASY.rank
            progressionState.curriculumProgress < 30 -> StructuralDifficultyBandV5.MEDIUM.rank
            progressionState.curriculumProgress < 60 -> StructuralDifficultyBandV5.HARD.rank
            progressionState.curriculumProgress < 100 -> StructuralDifficultyBandV5.EXPERT.rank
            else -> StructuralDifficultyBandV5.MASTER.rank
        }
        val bounded = source.filter { it.difficultyBand.rank <= minOf(maximumRank, curriculumMaximum) }
        val candidates = bounded.ifEmpty { source }
        val recentExact = selectionState.recentLevelIds.takeLast(8).toSet()
        val recentInteractions = selectionState.recentInteractionFingerprints.takeLast(4).toSet()
        val recentDependencies = selectionState.recentDependencyFingerprints.takeLast(4).toSet()
        val recentStrategies = selectionState.recentStrategyFingerprints.takeLast(4).toSet()
        val recentMechanics = selectionState.recentMechanicFingerprints.takeLast(3).toSet()
        val recentGrids = selectionState.recentGridSizes.takeLast(3)

        fun score(level: SelectableLevelV1): Int {
            var value = 1_000 - abs(level.difficultyBand.rank - target.rank) * 180
            if (level.levelId in recentExact) value -= 10_000
            if (level.interactionFingerprint in recentInteractions) value -= 240
            if (level.dependencyFingerprint in recentDependencies) value -= 220
            if (level.strategyFingerprint in recentStrategies) value -= 180
            if (level.mechanicCombinationFingerprint in recentMechanics) value -= 140
            value -= recentGrids.count { it == level.gridSize } * 70
            val newMechanics = level.mechanicTags.count { !progressionState.mechanicExposureState.has(it) }
            value += minOf(newMechanics, 1) * 60
            return value
        }
        val selected = candidates.sortedWith(
            compareByDescending<SelectableLevelV1>(::score)
                .thenBy { stableTieBreak(contentVersion, progressionState.progressionPosition, selectionState, it) }
                .thenBy { it.levelId },
        ).first()
        val fallback = selected !in bounded || available.isEmpty()
        val recovery = isStruggling(playerSkillState)
        val reason = buildString {
            append("Skill ${playerSkillState.skillScore}/100 targets ${target.name}; selected ${selected.difficultyBand.name}")
            if (recovery) append(" as a bounded recovery opportunity")
            append(" with curriculum gate ${progressionState.curriculumProgress}")
            append(" and anti-repetition scoring")
            if (fallback) append(" using deterministic fallback")
            append('.')
        }
        return NextLevelDecisionV1(
            selectedLevelId = selected.levelId,
            selectedDifficultyBand = selected.difficultyBand,
            selectionReason = reason,
            deterministicFallbackUsed = fallback,
        )
    }

    private fun targetBand(
        skill: PlayerSkillStateV1,
        progression: ProgressionStateV1,
        selection: SelectionStateV1,
    ): StructuralDifficultyBandV5 {
        val base = when (skill.skillScore) {
            in 0..14 -> StructuralDifficultyBandV5.TUTORIAL
            in 15..32 -> StructuralDifficultyBandV5.EASY
            in 33..51 -> StructuralDifficultyBandV5.MEDIUM
            in 52..70 -> StructuralDifficultyBandV5.HARD
            in 71..87 -> StructuralDifficultyBandV5.EXPERT
            else -> StructuralDifficultyBandV5.MASTER
        }
        if (progression.progressionPosition < 12) return minBand(base, StructuralDifficultyBandV5.EASY)
        if (isStruggling(skill)) return StructuralDifficultyBandV5.entries[(base.rank - 1).coerceAtLeast(0)]
        val recent = selection.recentDifficultyHistory.takeLast(3)
        if (recent.size == 3 && recent.all { it == base }) {
            return StructuralDifficultyBandV5.entries[(base.rank + 1).coerceAtMost(5)]
        }
        return base
    }

    private fun isStruggling(skill: PlayerSkillStateV1): Boolean {
        val failures = skill.recentFailurePressure.takeLast(3)
        val hints = skill.recentHintUsage.takeLast(3)
        val restarts = skill.recentRestartUsage.takeLast(3)
        return failures.size >= 2 && failures.average() >= 2.5 ||
            hints.sum() >= 2 || restarts.sum() >= 3
    }

    private fun minBand(
        first: StructuralDifficultyBandV5,
        second: StructuralDifficultyBandV5,
    ): StructuralDifficultyBandV5 = if (first.rank <= second.rank) first else second

    private fun stableTieBreak(
        contentVersion: Int,
        progressionPosition: Int,
        selection: SelectionStateV1,
        level: SelectableLevelV1,
    ): Long {
        var value = 1125899906842597L
        "$contentVersion|$progressionPosition|${selection.selectionOrdinal}|${selection.selectionVersion}|${level.levelId}"
            .forEach { value = value * 31 + it.code }
        return value and Long.MAX_VALUE
    }
}

const val ROLLING_WINDOW_V1 = 8

package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.solver.SolverResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * O(1) layout/structural uniqueness gates plus an exact semantic-neighbour verification bucket.
 *
 * V6.1 semantic similarity cannot exceed 0.75 when the solution-policy fingerprints differ:
 * descriptor similarity contributes at most 0.60 and rhythm contributes at most 0.15. Therefore
 * every possible >0.92 neighbour is in the exact solution-policy bucket. The bucket is only an
 * acceleration structure; [semanticSimilarityV61] is still evaluated exactly before rejection.
 */
class V61FingerprintIndex(fingerprints: Collection<V6FingerprintBundle> = emptyList()) {
    private val exact = linkedSetOf<String>()
    private val d4 = linkedSetOf<String>()
    private val arrows = linkedSetOf<String>()
    private val interactive = linkedSetOf<String>()
    private val perceptual = linkedSetOf<String>()
    private val relevance = linkedSetOf<String>()
    private val causal = linkedSetOf<String>()
    private val dags = linkedSetOf<String>()
    private val policies = linkedSetOf<String>()
    private val synthesis = linkedSetOf<String>()
    private val byPolicy = linkedMapOf<String, MutableList<V6FingerprintBundle>>()
    private val ordered = mutableListOf<V6FingerprintBundle>()

    init {
        fingerprints.forEach(::add)
    }

    val size: Int get() = ordered.size

    /** Visual gates that require no DAG, solver, occupancy, or causal analysis. */
    fun layoutDuplicateReason(level: LevelDefinition): String? = when {
        ContentFingerprint.exact(level) in exact -> "REJECT_EXACT_DUPLICATE"
        ContentFingerprint.symmetryNormalized(level) in d4 -> "REJECT_D4_DUPLICATE"
        ContentFingerprint.arrowLayoutSymmetryNormalized(level) in arrows -> "REJECT_ARROW_LAYOUT_DUPLICATE"
        ContentFingerprint.interactiveLayoutSymmetryNormalized(level) in interactive ->
            "REJECT_INTERACTIVE_LAYOUT_DUPLICATE"
        ContentFingerprint.perceptualTemplateSignature(level) in perceptual ->
            "REJECT_PERCEPTUAL_LAYOUT_DUPLICATE"
        else -> null
    }

    @Synchronized
    fun add(value: V6FingerprintBundle) {
        ordered += value
        exact += value.exactLayout
        d4 += value.d4Layout
        arrows += value.arrowLayout
        interactive += value.interactiveLayout
        perceptual += value.perceptualLayout
        relevance += value.relevancePrunedD4Layout
        causal += value.causalHypergraph
        dags += value.quotientDecisionDag
        policies += value.solutionPolicy
        if (!value.synthesisGraphIdentifier.startsWith("unavailable:")) synthesis += value.synthesisGraphIdentifier
        byPolicy.getOrPut(value.solutionPolicy, ::mutableListOf) += value
    }

    fun duplicateReason(candidate: V6FingerprintBundle): String? = when {
        candidate.exactLayout in exact -> "REJECT_EXACT_DUPLICATE"
        candidate.d4Layout in d4 -> "REJECT_D4_DUPLICATE"
        candidate.arrowLayout in arrows -> "REJECT_ARROW_LAYOUT_DUPLICATE"
        candidate.interactiveLayout in interactive -> "REJECT_INTERACTIVE_LAYOUT_DUPLICATE"
        candidate.perceptualLayout in perceptual -> "REJECT_PERCEPTUAL_LAYOUT_DUPLICATE"
        candidate.relevancePrunedD4Layout in relevance -> "REJECT_RELEVANCE_DUPLICATE"
        candidate.causalHypergraph in causal -> "REJECT_CAUSAL_DUPLICATE"
        candidate.quotientDecisionDag in dags -> "REJECT_DECISION_DAG_DUPLICATE"
        candidate.solutionPolicy in policies -> "REJECT_SOLUTION_POLICY_DUPLICATE"
        !candidate.synthesisGraphIdentifier.startsWith("unavailable:") &&
            candidate.synthesisGraphIdentifier in synthesis -> "REJECT_SYNTHESIS_GRAPH_DUPLICATE"
        exactNearSemantic(candidate).second > 0.92 -> "REJECT_NEAR_SEMANTIC_DUPLICATE"
        else -> null
    }

    fun attachExactNearest(candidate: V6FingerprintBundle): V6FingerprintBundle {
        val nearest = exactNearSemantic(candidate)
        return candidate.copy(
            nearestSemanticFingerprint = nearest.first,
            nearestSemanticSimilarity = nearest.second.takeIf { nearest.first != null },
        )
    }

    private fun exactNearSemantic(candidate: V6FingerprintBundle): Pair<String?, Double> {
        val possibleThresholdNeighbours = byPolicy[candidate.solutionPolicy].orEmpty()
        val nearest = possibleThresholdNeighbours.asSequence()
            .map { existing -> existing to semanticSimilarityV61(candidate, existing) }
            .maxWithOrNull(compareBy<Pair<V6FingerprintBundle, Double>> { it.second }
                .thenBy { it.first.exactLayout })
        return nearest?.first?.exactLayout to (nearest?.second ?: 0.0)
    }
}

internal fun duplicateRejectionCodeV6(reason: String): V6RejectionCode = when (reason) {
    "REJECT_EXACT_DUPLICATE" -> V6RejectionCode.EXACT_DUPLICATE
    "REJECT_D4_DUPLICATE" -> V6RejectionCode.D4_DUPLICATE
    "REJECT_ARROW_LAYOUT_DUPLICATE" -> V6RejectionCode.ARROW_LAYOUT_DUPLICATE
    "REJECT_INTERACTIVE_LAYOUT_DUPLICATE" -> V6RejectionCode.INTERACTIVE_LAYOUT_DUPLICATE
    "REJECT_PERCEPTUAL_LAYOUT_DUPLICATE" -> V6RejectionCode.PERCEPTUAL_LAYOUT_DUPLICATE
    "REJECT_RELEVANCE_DUPLICATE" -> V6RejectionCode.RELEVANCE_DUPLICATE
    "REJECT_CAUSAL_DUPLICATE" -> V6RejectionCode.CAUSAL_DUPLICATE
    "REJECT_DECISION_DAG_DUPLICATE" -> V6RejectionCode.DECISION_DAG_DUPLICATE
    "REJECT_SOLUTION_POLICY_DUPLICATE" -> V6RejectionCode.SOLUTION_POLICY_DUPLICATE
    "REJECT_SYNTHESIS_GRAPH_DUPLICATE" -> V6RejectionCode.SYNTHESIS_GRAPH_DUPLICATE
    "REJECT_NEAR_SEMANTIC_DUPLICATE" -> V6RejectionCode.NEAR_SEMANTIC_CLONE
    else -> error("Unknown V6 duplicate rejection reason '$reason'")
}

internal fun semanticSimilarityV61(first: V6FingerprintBundle, second: V6FingerprintBundle): Double {
    if (first.causalHypergraph == second.causalHypergraph ||
        first.quotientDecisionDag == second.quotientDecisionDag
    ) return 1.0
    val size = minOf(first.behaviouralDescriptor.size, second.behaviouralDescriptor.size)
    if (size == 0) return 0.0
    val distance = (0 until size).sumOf { index ->
        val scale = maxOf(
            1.0,
            kotlin.math.abs(first.behaviouralDescriptor[index]),
            kotlin.math.abs(second.behaviouralDescriptor[index]),
        )
        kotlin.math.abs(first.behaviouralDescriptor[index] - second.behaviouralDescriptor[index]) / scale
    } / size
    val rhythmBonus = if (first.mechanicRhythm == second.mechanicRhythm) 0.15 else 0.0
    val policyBonus = if (first.solutionPolicy == second.solutionPolicy) 0.25 else 0.0
    return ((1.0 - distance) * 0.60 + rhythmBonus + policyBonus).coerceIn(0.0, 1.0)
}

/** Shared, thread-safe analysis cache for deterministic speculative attempts. */
class V61AnalysisCache {
    private val dags = ConcurrentHashMap<String, DecisionDagAnalysisV6>()
    private val occupancies = ConcurrentHashMap<String, PurposefulOccupancyV6>()
    private val extractedSpecs = ConcurrentHashMap<String, ExtractedCausalSpecV6?>()
    private val causalVerifications = ConcurrentHashMap<String, CausalVerificationV6>()
    private val fingerprints = ConcurrentHashMap<String, V6FingerprintBundle>()
    private val difficulties = ConcurrentHashMap<String, AutomatedDifficultyAssessmentV61>()
    private val solvers = ConcurrentHashMap<String, SolverResult>()
    private val replays = ConcurrentHashMap<String, Boolean>()
    private val hits = AtomicLong()
    private val misses = AtomicLong()

    data class Stats(val hits: Long, val misses: Long) {
        val total: Long get() = hits + misses
        val hitRate: Double get() = if (total == 0L) 0.0 else hits.toDouble() / total
    }

    fun stats(): Stats = Stats(hits.get(), misses.get())

    /**
     * Release batch-local evidence after deterministic admission has consumed it. Counters remain
     * cumulative for reporting. Rejected speculative DAGs are intentionally not retained across
     * batches because their content fingerprints will never be requested again in this run.
     */
    fun clear() {
        dags.clear()
        occupancies.clear()
        extractedSpecs.clear()
        causalVerifications.clear()
        fingerprints.clear()
        difficulties.clear()
        solvers.clear()
        replays.clear()
    }

    fun decisionDag(level: LevelDefinition, states: Int, resolutions: Int, compute: () -> DecisionDagAnalysisV6) =
        cached(dags, "dag-v6|$states|$resolutions|${ContentFingerprint.exact(level)}", compute)

    fun occupancy(level: LevelDefinition, profile: V6Profile, checks: Int, compute: () -> PurposefulOccupancyV6) =
        cached(
            occupancies,
            "occupancy-v6|$checks|${profile.occupancy}|${profile.minimumPurposefulOccupiedRatio}|" +
                ContentFingerprint.exact(level),
            compute,
        )

    internal fun extractedSpec(
        level: LevelDefinition,
        identity: GeneratorV6Identity,
        profile: V6Profile,
        compute: () -> ExtractedCausalSpecV6?,
    ): ExtractedCausalSpecV6? {
        val key = "causal-extract-v6|${identity.grammarFamily}|${profile.minimumInteractingChains}|" +
            ContentFingerprint.exact(level)
        val existing = extractedSpecs[key]
        if (existing != null || extractedSpecs.containsKey(key)) {
            hits.incrementAndGet()
            return existing
        }
        misses.incrementAndGet()
        val computed = compute()
        if (computed != null) extractedSpecs.putIfAbsent(key, computed)
        return computed
    }

    fun causalVerification(level: LevelDefinition, spec: CausalHypergraphSpec, compute: () -> CausalVerificationV6) =
        cached(causalVerifications, "causal-verify-v6|${spec.hashCode()}|${ContentFingerprint.exact(level)}", compute)

    fun fingerprint(level: LevelDefinition, spec: CausalHypergraphSpec, compute: () -> V6FingerprintBundle) =
        cached(fingerprints, "semantic-fingerprint-v61|${spec.hashCode()}|${ContentFingerprint.exact(level)}", compute)

    fun difficulty(level: LevelDefinition, spec: CausalHypergraphSpec, compute: () -> AutomatedDifficultyAssessmentV61) =
        cached(difficulties, "difficulty-v61-schema-$V61_DIFFICULTY_SCHEMA_VERSION|${spec.hashCode()}|" +
            ContentFingerprint.exact(level), compute)

    fun solver(level: LevelDefinition, maxStates: Int, compute: () -> SolverResult) =
        cached(solvers, "solver-v1|$maxStates|${ContentFingerprint.exact(level)}", compute)

    fun replay(level: LevelDefinition, actionIds: List<String>, compute: () -> Boolean) =
        cached(replays, "production-replay-v1|${actionIds.joinToString(",")}|${ContentFingerprint.exact(level)}", compute)

    private fun <T : Any> cached(map: ConcurrentHashMap<String, T>, key: String, compute: () -> T): T {
        map[key]?.let {
            hits.incrementAndGet()
            return it
        }
        misses.incrementAndGet()
        return map.computeIfAbsent(key) { compute() }
    }
}

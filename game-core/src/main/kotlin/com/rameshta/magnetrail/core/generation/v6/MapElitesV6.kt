package com.rameshta.magnetrail.core.generation.v6

data class MapElitesCellV6(
    val causalFamily: CausalGrammarFamilyV6,
    val meaningfulDecisionBucket: Int,
    val trapDepthBucket: Int,
    val polarityMemoryBucket: Int,
    val posetDependencyBucket: Int,
    val purposefulOccupancyBucket: Int,
)

data class MapElitesEntryV6(
    val cell: MapElitesCellV6,
    val candidate: V6Candidate,
    val distanceToTarget: List<Double>,
    val guessDependence: Double,
    val purposefulRelevance: Double,
    val semanticNovelty: Double,
    val readabilityMargin: Double,
    val analysisCost: Int,
)

class DeterministicMapElitesV6 {
    private val archive = linkedMapOf<MapElitesCellV6, MapElitesEntryV6>()

    fun entries(): List<MapElitesEntryV6> = archive.entries.sortedBy { it.key.stableKey() }.map { it.value }

    fun insert(candidate: V6Candidate): Boolean {
        require(candidate.accepted)
        val analysis = requireNotNull(candidate.decisionAnalysis)
        val metrics = requireNotNull(analysis.metrics)
        val occupancy = requireNotNull(candidate.occupancy)
        val fingerprints = requireNotNull(candidate.fingerprints)
        val features = HumanCognitiveFeatureExtractorV1.extract(analysis, candidate.spec.interactingChainCount)
        val profile = candidate.profile
        val cell = cell(candidate)
        val targetDecisions = when {
            profile.meaningfulDecisionRange.last == Int.MAX_VALUE -> profile.meaningfulDecisionRange.first.toDouble()
            else -> (profile.meaningfulDecisionRange.first + profile.meaningfulDecisionRange.last) / 2.0
        }
        val targetOccupancy = (profile.occupancy.start + profile.occupancy.endInclusive) / 2.0
        val entry = MapElitesEntryV6(
            cell = cell,
            candidate = candidate,
            distanceToTarget = listOf(
                kotlin.math.abs(metrics.meaningfulDecisionCount - targetDecisions),
                kotlin.math.abs(metrics.successfulLosingBranchCount - profile.minimumPersistentTraps).toDouble(),
                kotlin.math.abs(metrics.minimumLookaheadProofDepth - profile.minimumLookahead).toDouble(),
                kotlin.math.abs(occupancy.occupiedRatio - targetOccupancy),
            ),
            guessDependence = features.guessDependence,
            purposefulRelevance = occupancy.purposefulOccupiedRatio,
            semanticNovelty = 1.0 - (fingerprints.nearestSemanticSimilarity ?: 0.0),
            readabilityMargin = minOf(
                occupancy.occupiedRatio - profile.occupancy.start,
                profile.occupancy.endInclusive - occupancy.occupiedRatio,
            ),
            analysisCost = analysis.nodes.size + occupancy.counterfactualChecks,
        )
        val current = archive[cell]
        if (current == null || comparator.compare(entry, current) < 0) {
            archive[cell] = entry
            return true
        }
        return false
    }

    /** Parallel callers must merge through this stable order; insertion order never affects output. */
    fun merge(candidates: Collection<V6Candidate>): Int = candidates.sortedWith(
        compareBy<V6Candidate> { it.identity.seed }
            .thenBy { it.fingerprints?.exactLayout.orEmpty() }
            .thenBy { it.identity.stableKey },
    ).count { insert(it) }

    fun saturated(requiredCells: Int, attemptsWithoutImprovement: Int, saturationAttemptCap: Int): Boolean {
        require(requiredCells > 0 && saturationAttemptCap > 0)
        return archive.size >= requiredCells || attemptsWithoutImprovement >= saturationAttemptCap
    }

    private fun cell(candidate: V6Candidate): MapElitesCellV6 {
        val metrics = requireNotNull(candidate.decisionAnalysis?.metrics)
        val occupancy = requireNotNull(candidate.occupancy)
        return MapElitesCellV6(
            causalFamily = candidate.spec.family,
            meaningfulDecisionBucket = bucket(metrics.meaningfulDecisionCount, 1, 2, 4, 6),
            trapDepthBucket = bucket(metrics.successfulLosingBranchCount + metrics.maximumDelayedDeadlockDepth, 0, 2, 5, 9),
            polarityMemoryBucket = bucket(metrics.polarityMemorySpan, 0, 1, 3, 5),
            posetDependencyBucket = bucket(
                metrics.transitiveReduction.size + metrics.partialOrderWidth,
                1,
                3,
                6,
                10,
            ),
            purposefulOccupancyBucket = (occupancy.purposefulOccupiedRatio * 10).toInt().coerceIn(0, 10),
        )
    }

    private fun bucket(value: Int, vararg boundaries: Int): Int = boundaries.count { value > it }

    private fun MapElitesCellV6.stableKey(): String = listOf(
        causalFamily.ordinal,
        meaningfulDecisionBucket,
        trapDepthBucket,
        polarityMemoryBucket,
        posetDependencyBucket,
        purposefulOccupancyBucket,
    ).joinToString(":")

    companion object {
        private val comparator = Comparator<MapElitesEntryV6> { first, second ->
            compareLists(first.distanceToTarget, second.distanceToTarget)
                .takeIf { it != 0 }
                ?: first.guessDependence.compareTo(second.guessDependence).takeIf { it != 0 }
                ?: second.purposefulRelevance.compareTo(first.purposefulRelevance).takeIf { it != 0 }
                ?: second.semanticNovelty.compareTo(first.semanticNovelty).takeIf { it != 0 }
                ?: second.readabilityMargin.compareTo(first.readabilityMargin).takeIf { it != 0 }
                ?: first.analysisCost.compareTo(second.analysisCost).takeIf { it != 0 }
                ?: first.candidate.fingerprints?.exactLayout.orEmpty()
                    .compareTo(second.candidate.fingerprints?.exactLayout.orEmpty())
        }

        private fun compareLists(first: List<Double>, second: List<Double>): Int {
            for (index in 0 until minOf(first.size, second.size)) {
                val result = first[index].compareTo(second[index])
                if (result != 0) return result
            }
            return first.size.compareTo(second.size)
        }
    }
}

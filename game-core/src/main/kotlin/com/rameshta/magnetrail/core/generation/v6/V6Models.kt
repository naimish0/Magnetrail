package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.LevelDefinition

const val GENERATOR_VERSION_V6 = 6
const val V6_SCHEMA_VERSION = 1
const val V6_HUMAN_BAND_COUNT = 5
const val V6_PILOT_BOARD_COUNT = 30
const val V6_REALIZER_VERSION = "state-conditioned-engine-guided-cegis-v3"
const val V6_DECISION_ANALYZER_VERSION = "complete-easiest-policy-dag-v2"
const val HUMAN_LIKE_DIFFICULTY_V1 = "human-like-difficulty-v1-five-band-v2"

enum class V6CertificationStatus {
    BASELINE_VERIFIED,
    V6_TECHNICALLY_CERTIFIED,
    AWAITING_HUMAN_CALIBRATION,
    HUMAN_MODEL_CERTIFIED,
    FULL_CAMPAIGN_STAGING_CERTIFIED,
    CAMPAIGN_CERTIFIED,
    PROMOTED_V6_CAMPAIGN,
    FAIL_NO_PROMOTION,
    BLOCKED_RULESET_CEILING,
    AUTOMATED_CAMPAIGN_CERTIFIED,
    AUTOMATED_CAMPAIGN_REJECTED,
}

data class GeneratorV6Identity(
    val seed: Long,
    val bucket: Int,
    val grammarFamily: CausalGrammarFamilyV6,
    val graphInstance: Int,
    val refinementIteration: Int = 0,
    val generatorVersion: Int = GENERATOR_VERSION_V6,
    val schemaVersion: Int = V6_SCHEMA_VERSION,
) {
    init {
        require(generatorVersion == GENERATOR_VERSION_V6)
        require(schemaVersion == V6_SCHEMA_VERSION)
        require(bucket in 1..V6_HUMAN_BAND_COUNT)
        require(graphInstance >= 0)
        require(refinementIteration in 0..2)
    }

    val stableKey: String
        get() = "v$generatorVersion-b$bucket-${grammarFamily.name.lowercase()}-$seed-$graphInstance-r$refinementIteration"
}

enum class CausalGrammarFamilyV6 {
    POLARITY_LOCK_RELEASE,
    OCCLUSION_REVEAL_CHAIN,
    CANCELLATION_RELEASE,
    COMPETING_CONTROLLER_HANDOFF,
    FORK_JOIN_COUPLED,
    INTERACTING_CHAINS_DELAYED_TRAPS,
}

enum class LogicalEntityKindV6 { ARROW, MAGNET, WALL }

data class LogicalEntityRole(
    val key: String,
    val kind: LogicalEntityKindV6,
    val chain: Int = 0,
    val ordinal: Int = 0,
) {
    init {
        require(key.isNotBlank())
        require(chain >= 0 && ordinal >= 0)
    }
}

enum class LogicalActionKindV6 { REQUIRED, OPTIONAL_WINNING, SUCCESSFUL_TRAP }

data class LogicalActionRole(
    val key: String,
    val arrowRoleKey: String,
    val kind: LogicalActionKindV6,
    val chain: Int = 0,
    val ordinal: Int = 0,
) {
    init {
        require(key.isNotBlank() && arrowRoleKey.isNotBlank())
        require(chain >= 0 && ordinal >= 0)
    }
}

enum class StateConditionTypeV6 {
    ARROW_PRESENT,
    ARROW_ABSENT,
    MAGNET_PULL,
    MAGNET_PUSH,
    CONTROLLER_IS,
    CONTROLLER_NONE,
    CANCELLATION_ACTIVE,
    STATE_SOLVABLE,
    STATE_UNSOLVABLE,
    ACTION_SUCCEEDS,
    ACTION_FAILS,
}

data class StateCondition(
    val type: StateConditionTypeV6,
    val subjectRoleKey: String,
    val valueRoleKey: String? = null,
) {
    init {
        require(subjectRoleKey.isNotBlank())
    }
}

enum class CausalEffectV6 {
    MUST_PRECEDE,
    ENABLES_SUCCESS,
    DISABLES_SUCCESS,
    REVEALS_CONTROLLER,
    OCCLUDES_CONTROLLER,
    CHANGES_CONTROLLER,
    FLIPS_REQUIRED_POLARITY,
    RELEASES_CANCELLATION,
    CREATES_CANCELLATION,
    SUCCESSFUL_TRAP,
    FORK_JOIN_DEPENDENCY,
    NON_COMMUTING_CHOICE,
}

data class CausalHyperedge(
    val key: String,
    val preconditions: List<StateCondition>,
    val triggeringActionRoleKey: String,
    val effect: CausalEffectV6,
    val affectedRoleKeys: List<String>,
    val requiredCounterfactual: Boolean = effect !in setOf(
        CausalEffectV6.FLIPS_REQUIRED_POLARITY,
        CausalEffectV6.NON_COMMUTING_CHOICE,
    ),
) {
    init {
        require(key.isNotBlank() && triggeringActionRoleKey.isNotBlank())
        require(affectedRoleKeys.isNotEmpty())
    }
}

data class SolutionPartialOrder(
    val actionRoleKeys: Set<String>,
    val precedence: Set<Pair<String, String>>,
) {
    init {
        require(precedence.all { it.first in actionRoleKeys && it.second in actionRoleKeys && it.first != it.second })
        require(isAcyclic()) { "Solution partial order contains a cycle" }
    }

    fun deterministicTopologicalOrder(): List<String> {
        val remaining = actionRoleKeys.toMutableSet()
        val result = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            val next = remaining.filter { candidate ->
                precedence.none { (before, after) -> after == candidate && before in remaining }
            }.minOrNull() ?: error("Cyclic partial order")
            result += next
            remaining -= next
        }
        return result
    }

    private fun isAcyclic(): Boolean = runCatching { deterministicTopologicalOrder() }.isSuccess
}

data class SuccessfulTrapContract(
    val actionRoleKey: String,
    val minimumDelayedConsequenceDepth: Int,
    val requiredAtCorrectPoint: Boolean = true,
) {
    init {
        require(actionRoleKey.isNotBlank())
        require(minimumDelayedConsequenceDepth >= 0)
    }
}

data class CausalHypergraphSpec(
    val family: CausalGrammarFamilyV6,
    val entities: List<LogicalEntityRole>,
    val actions: List<LogicalActionRole>,
    val hyperedges: List<CausalHyperedge>,
    val solutionPartialOrder: SolutionPartialOrder,
    val trapContracts: List<SuccessfulTrapContract>,
    val interactingChainCount: Int,
    val schemaVersion: Int = V6_SCHEMA_VERSION,
) {
    init {
        require(schemaVersion == V6_SCHEMA_VERSION)
        require(entities.map { it.key }.distinct().size == entities.size)
        require(actions.map { it.key }.distinct().size == actions.size)
        require(hyperedges.map { it.key }.distinct().size == hyperedges.size)
        val entityKeys = entities.mapTo(hashSetOf()) { it.key }
        val actionKeys = actions.mapTo(hashSetOf()) { it.key }
        require(actions.all { it.arrowRoleKey in entityKeys })
        require(hyperedges.all { it.triggeringActionRoleKey in actionKeys })
        require(solutionPartialOrder.actionRoleKeys.all { it in actionKeys })
        require(trapContracts.all { it.actionRoleKey in actionKeys })
        require(interactingChainCount >= 0)
    }
}

data class CausalWitness(
    val hyperedgeKey: String,
    val preStateKey: String,
    val preState: BoardState,
    val triggerArrowId: String,
    val resolution: ResolutionResult,
    val postStateKey: String,
    val observedControllerId: String?,
    val observedRoute: List<String>,
    val observedTerminalEvent: String,
    val observedPolarityChange: String?,
    val claimedEffect: CausalEffectV6,
    val affectedArrowId: String?,
    val beforeAffectedResolution: ResolutionResult?,
    val afterAffectedResolution: ResolutionResult?,
    val counterfactualEvidence: String?,
)

data class V6SearchBudgets(
    val constructionAttempts: Int = 6,
    val realizationStates: Int = 100_000,
    val realizationNogoods: Int = 20_000,
    val realizationMillis: Long = 1_000,
    val decisionDagStates: Int = 200_000,
    val decisionDagResolutions: Int = 2_000_000,
    val counterfactualChecks: Int = 500_000,
    val canonicalBacktrackingStates: Int = 200_000,
) {
    init {
        require(constructionAttempts > 0)
        require(realizationStates > 0 && realizationNogoods > 0 && realizationMillis > 0)
        require(decisionDagStates > 0 && decisionDagResolutions > 0)
        require(counterfactualChecks > 0 && canonicalBacktrackingStates > 0)
    }
}

data class V6Profile(
    val bucket: Int,
    val occupancy: ClosedFloatingPointRange<Double>,
    val minimumPurposefulOccupiedRatio: Double,
    val maximumInertOccupiedRatio: Double = 0.10,
    val meaningfulDecisionRange: IntRange,
    val minimumPersistentTraps: Int,
    val minimumLookahead: Int,
    val maximumHardestWinningShare: Double?,
    val minimumInteractingChains: Int,
    val budgets: V6SearchBudgets = V6SearchBudgets(),
) {
    init {
        require(bucket in 1..V6_HUMAN_BAND_COUNT)
        require(occupancy.start in 0.0..1.0 && occupancy.endInclusive in 0.0..1.0)
        require(minimumPurposefulOccupiedRatio in 0.0..1.0)
        require(maximumInertOccupiedRatio in 0.0..1.0)
        require(minimumPersistentTraps >= 0 && minimumLookahead >= 0 && minimumInteractingChains >= 0)
        require(maximumHardestWinningShare == null || maximumHardestWinningShare in 0.0..1.0)
    }
}

object V6Profiles {
    val all: List<V6Profile> = listOf(
        profile(1, 0.45..0.65, 0..1, 0, 0, null, 0),
        profile(2, 0.45..0.65, 1..2, 0, 1, null, 1),
        profile(3, 0.55..0.70, 2..3, 1, 2, 0.67, 1),
        profile(4, 0.60..0.75, 3..4, 2, 3, 0.50, 2),
        profile(5, 0.65..0.80, 4..6, 3, 4, 0.40, 2),
    )

    fun forBucket(bucket: Int): V6Profile = all.single { it.bucket == bucket }

    private fun profile(
        bucket: Int,
        occupancy: ClosedFloatingPointRange<Double>,
        decisions: IntRange,
        traps: Int,
        lookahead: Int,
        winningShare: Double?,
        chains: Int,
    ) = V6Profile(
        bucket = bucket,
        occupancy = occupancy,
        minimumPurposefulOccupiedRatio = if (bucket <= 4) 0.85 else 0.90,
        meaningfulDecisionRange = decisions,
        minimumPersistentTraps = traps,
        minimumLookahead = lookahead,
        maximumHardestWinningShare = winningShare,
        minimumInteractingChains = chains,
    )
}

enum class V6RejectionCode {
    INVALID_LOGICAL_SPEC,
    REALIZATION_ATTEMPT_CAP,
    REALIZATION_STATE_CAP,
    REALIZATION_TIME_CAP,
    REALIZATION_NOGOOD_CAP,
    SCHEMA_INVALID,
    DESIGNED_REPLAY_FAILED,
    SOLVER_DISAGREEMENT,
    DECISION_ANALYSIS_TRUNCATED,
    CAUSAL_WITNESS_MISSING,
    SUCCESSFUL_TRAP_INVALID,
    IMMEDIATE_FAILURE_MISLABELED,
    OCCUPANCY_OUT_OF_RANGE,
    PURPOSEFUL_RATIO_LOW,
    INERT_RATIO_HIGH,
    MAGNET_WITHOUT_WITNESS,
    WALL_WITHOUT_WITNESS,
    BEHAVIOURAL_GATE_FAILED,
    SIMPLE_POLICY_SOLVES_HIGH_BUCKET,
    REQUIRED_GUESSING,
    V4_REJECTED,
    QUALITY_REJECTED,
    EXACT_DUPLICATE,
    D4_DUPLICATE,
    CAUSAL_DUPLICATE,
    DECISION_DAG_DUPLICATE,
    SOLUTION_POLICY_DUPLICATE,
    NEAR_SEMANTIC_CLONE,
    CANONICALIZATION_CAP,
    OOD_DIFFICULTY,
    LOW_CONFIDENCE_DIFFICULTY,
    HUMAN_MODEL_NOT_CERTIFIED,
    CEGIS_REFINEMENT_EXHAUSTED,
}

data class V6Rejection(
    val code: V6RejectionCode,
    val detail: String,
    val counterexampleKey: String? = null,
)

data class V6FingerprintBundle(
    val exactLayout: String,
    val d4Layout: String,
    val arrowLayout: String,
    val interactiveLayout: String,
    val perceptualLayout: String,
    val relevancePrunedD4Layout: String,
    val causalHypergraph: String,
    val quotientDecisionDag: String,
    val solutionPolicy: String,
    val meaningfulDecisionTrace: String,
    val mechanicRhythm: String,
    val behaviouralDescriptor: List<Double>,
    val productionStateTransitionTrace: String = "unavailable:production-transition-trace",
    val causalFamilyIdentifier: String = "unavailable:causal-family",
    val strategyBehaviourClusterIdentifier: String = "unavailable:strategy-cluster",
    val synthesisGraphIdentifier: String = "unavailable:synthesis-graph",
    val nearestSemanticFingerprint: String? = null,
    val nearestSemanticSimilarity: Double? = null,
)

data class V6Candidate(
    val identity: GeneratorV6Identity,
    val profile: V6Profile,
    val spec: CausalHypergraphSpec,
    val level: LevelDefinition?,
    val roleBindings: Map<String, String>,
    val witnesses: List<CausalWitness> = emptyList(),
    val decisionAnalysis: DecisionDagAnalysisV6? = null,
    val occupancy: PurposefulOccupancyV6? = null,
    val fingerprints: V6FingerprintBundle? = null,
    val humanDifficulty: HumanDifficultyPrediction? = null,
    val rejections: List<V6Rejection> = emptyList(),
) {
    val accepted: Boolean get() = level != null && rejections.isEmpty()
}

data class V6TechnicalCertificate(
    val identity: GeneratorV6Identity,
    val levelFingerprint: String,
    val causalWitnessCount: Int,
    val completeDecisionStateCount: Int,
    val productionReplayVerified: Boolean,
    val independentSolverVerified: Boolean,
    val failedActionImmutabilityVerified: Boolean,
    val deterministicRegenerationVerified: Boolean,
    val certificatePayloadSha256: String,
    val status: V6CertificationStatus = V6CertificationStatus.V6_TECHNICALLY_CERTIFIED,
)

data class HumanDifficultyPrediction(
    val modelVersion: String,
    val latentDifficulty: Double,
    val bandProbabilities: List<Double>,
    val confidenceInterval: ClosedFloatingPointRange<Double>,
    val calibrationConfidence: Double,
    val outOfDistribution: Boolean,
    val majorContributors: List<Pair<String, Double>>,
    val rejectionReason: String? = null,
) {
    init {
        require(bandProbabilities.size == V6_HUMAN_BAND_COUNT)
    }
}

data class HumanDifficultyCertificate(
    val modelVersion: String,
    val featureSchemaSha256: String,
    val datasetSha256: String,
    val parametersSha256: String,
    val thresholdsSha256: String,
    val validationCatalogSha256: String,
    val validationMetrics: Map<String, Double>,
    val status: V6CertificationStatus,
)

data class CampaignV6Certificate(
    val candidateCatalogSha256: String,
    val technicalCertificateSha256: String,
    val humanModelCertificateSha256: String,
    val productionSampleCertificateSha256: String,
    val promotionManifestSha256: String,
    val status: V6CertificationStatus,
)

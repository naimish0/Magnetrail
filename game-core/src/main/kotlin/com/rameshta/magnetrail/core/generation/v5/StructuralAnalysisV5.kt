package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Analyzer
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Config
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Score
import com.rameshta.magnetrail.core.difficulty.v4.defaultDifficultyV4Seeds
import com.rameshta.magnetrail.core.engine.CollisionTargetType
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.DeterministicRouteTracer
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.MagneticDiagnostics
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.solver.StateKey
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

const val STRUCTURAL_ANALYZER_V5_VERSION = "magnetrail-structural-v5.0"

@Serializable
enum class InteractionNodeTypeV5 { ARROW, MAGNET, WALL }

@Serializable
enum class InteractionTypeV5 {
    COLLISION,
    OCCLUSION,
    MAGNET_CONTROL,
    CANCELLATION,
    POLARITY_DEPENDENCY,
    ROUTE_BLOCK,
    EXPOSURE,
    ORDER_DEPENDENCY,
    STATE_DEPENDENCY,
    REVEAL,
    ALTERNATIVE_PATH,
}

@Serializable
data class InteractionNodeV5(val key: String, val type: InteractionNodeTypeV5)

@Serializable
data class InteractionEdgeV5(
    val source: String,
    val target: String,
    val type: InteractionTypeV5,
)

@Serializable
data class InteractionGraphV5(
    val nodes: List<InteractionNodeV5>,
    val edges: List<InteractionEdgeV5>,
    val totalInteractionEdges: Int,
    val uniqueInteractingObjects: Int,
    val interactionDensity: Double,
    val connectedComponents: Int,
    val largestConnectedComponent: Int,
    val isolatedObjects: Int,
    val averageObjectDegree: Double,
    val maximumObjectDegree: Int,
    val interactionTypeDistribution: Map<String, Int>,
    val fingerprint: String,
)

@Serializable
enum class ObjectRelevanceClassV5 { CRITICAL, HIGH, MEDIUM, LOW, IRRELEVANT }

@Serializable
data class ObjectRelevanceV5(
    val objectKey: String,
    val objectType: InteractionNodeTypeV5,
    val classification: ObjectRelevanceClassV5,
    val score: Double,
    val solvabilityChanged: Boolean,
    val winningFirstActionChange: Double,
    val strategyFamilyChange: Double,
    val dependencyGraphChange: Double,
    val polarityTransitionChange: Double,
    val meaningfulDecisionChange: Double,
    val orderingConstraintChange: Double,
    val lineOfSightChange: Double,
    val cancellationChange: Double,
    val routeStructureChange: Double,
    val analysisComplete: Boolean,
)

@Serializable
data class ObjectRelevanceSummaryV5(
    val objects: List<ObjectRelevanceV5>,
    val averageScore: Double,
    val relevantObjectCount: Int,
    val irrelevantObjectCount: Int,
    val relevantObjectRatio: Double,
    val analysisComplete: Boolean,
    val relevantObjectCountByType: Map<String, Int> = emptyMap(),
    val irrelevantObjectCountByType: Map<String, Int> = emptyMap(),
)

@Serializable
data class StructuralDiagnosticsV5(
    val levelId: String,
    val difficultyBand: StructuralDifficultyBandV5,
    val rows: Int,
    val columns: Int,
    val arrowCount: Int,
    val magnetCount: Int,
    val wallCount: Int,
    val objectDensity: Double,
    val interactionGraph: InteractionGraphV5,
    val magneticRelationshipCount: Int,
    val averageMagneticDistance: Double,
    val maximumMagneticDistance: Int,
    val lineOfSightInteractionCount: Int,
    val arrowVsArrowInteractionCount: Int,
    val magnetCancellationCount: Int,
    val cancellationStateCount: Int,
    val cancellationTransitionCount: Int,
    val cancellationCriticalDecisionCount: Int,
    val polarityDependentDecisionCount: Int,
    val polarityImpactDepth: Int,
    val dependencyEdgeCount: Int,
    val dependencyDepth: Int,
    val orderingConstraintCount: Int,
    val mandatoryOrderingDepth: Int,
    val meaningfulOrderingRate: Double,
    val safeChoiceRatio: Double,
    val meaningfulFailureRate: Double,
    val harmfulDecisionDensity: Double,
    val strategicChoiceDensity: Double,
    val recoveryPressure: Double = 0.0,
    val consequenceDepth: Int,
    val consequenceBreadth: Int,
    val exposureRevealCount: Int,
    val controllerChangeCount: Int,
    val alternativePathCount: Int,
    val objectRelevance: ObjectRelevanceSummaryV5,
    val greedySolveRate: Double,
    val randomSuccessRate: Double,
    val canonicalStrategyCount: Int?,
    val commutationQuotient: Int?,
    val permutationRedundancy: Double?,
    val solverStateCount: Int,
    val searchComplete: Boolean,
    val truncated: Boolean,
    val truncationReasons: List<String>,
    val exactFingerprint: String,
    val symmetryFingerprint: String,
    val interactionFingerprint: String,
    val dependencyFingerprint: String,
    val strategyFingerprint: String,
    val mechanicCombinationFingerprint: String,
    val v4Score: Int?,
    val v4Confidence: Double,
    val spatialDensity: SpatialDensityMetricsV5 = SpatialDensityMetricsV5(),
    val uniqueMagneticRelationshipCount: Int = 0,
    val magneticRelationshipDistances: List<Int> = emptyList(),
    val longRangeMagneticRelationshipCount: Int = 0,
    val meaningfulLineOfSightInteractionCount: Int = 0,
    val meaningfulArrowBlockerRelationshipCount: Int = 0,
    val meaningfulWallOcclusionCount: Int = 0,
    val exposureDepth: Int = 0,
    val persistentConsequenceCount: Int = 0,
    val averagePersistentConsequenceBreadth: Double = 0.0,
    val visibilityChangeCount: Int = 0,
    val commutativeActionPairCount: Int = 0,
    val nonCommutingActionPairCount: Int = 0,
    val viablePairCommutationRatio: Double = 0.0,
    val analyzerVersion: String = STRUCTURAL_ANALYZER_V5_VERSION,
)

data class StructuralAnalysisLimitsV5(
    val maxStates: Int = 100_000,
    val maxActionResolutions: Int = 1_000_000,
    val maxCounterfactualObjects: Int = 128,
    val solverStateCap: Int = 100_000,
)

/**
 * Cheap, engine-derived structure used by Generator V5 while materializing and repairing a
 * solution contract. Unlike authored contract metadata, every edge in this snapshot was observed
 * on a reachable production-engine state.
 */
data class PhysicalSemanticsV5(
    val edges: Set<InteractionEdgeV5>,
    val magneticRelationshipDistances: Map<String, Int>,
    val complete: Boolean,
    val truncationReasons: List<String>,
    val interactionGraph: InteractionGraphV5? = null,
) {
    val longRangeRelationships: Set<String>
        get() = magneticRelationshipDistances.filterValues { it >= 4 }.keys

    val participatingWalls: Set<String>
        get() = edges.asSequence()
            .flatMap { sequenceOf(it.source, it.target) }
            .filter { it.startsWith("wall:") }
            .toSet()
}

/**
 * Offline-only structural analysis. All action outcomes come from [GameEngine]; this class does
 * not duplicate gameplay rules.
 */
class StructuralAnalyzerV5(
    private val engine: GameEngine = DefaultGameEngine(),
    private val tracer: DeterministicRouteTracer = DeterministicRouteTracer(),
    private val magneticDiagnostics: MagneticDiagnostics = MagneticDiagnostics(engine, tracer),
) {
    fun analyzePhysicalSemantics(
        level: LevelDefinition,
        limits: StructuralAnalysisLimitsV5 = StructuralAnalysisLimitsV5(),
    ): PhysicalSemanticsV5 {
        val physical = snapshot(level, limits)
        return PhysicalSemanticsV5(
            edges = physical.edges.toSet(),
            magneticRelationshipDistances = physical.magneticRelationshipDistances.toMap(),
            complete = physical.complete,
            truncationReasons = physical.truncationReasons,
            interactionGraph = buildGraph(level, physical.edges),
        )
    }

    fun analyze(
        level: LevelDefinition,
        band: StructuralDifficultyBandV5,
        limits: StructuralAnalysisLimitsV5 = StructuralAnalysisLimitsV5(),
        difficultyV4: DifficultyV4Score? = null,
    ): StructuralDiagnosticsV5 {
        val snapshot = snapshot(level, limits)
        val v4 = difficultyV4 ?: DifficultyV4Analyzer(
            engine,
            DifficultyV4Config(
                maxExpandedStates = limits.maxStates,
                maxActionResolutions = limits.maxActionResolutions,
                maxCounterfactualStates = limits.maxStates,
                maxCounterfactualActionResolutions = limits.maxActionResolutions,
                maxObjectCounterfactuals = limits.maxCounterfactualObjects,
                randomPolicySeeds = defaultDifficultyV4Seeds(32),
            ),
        ).analyze(level)
        val relevance = objectRelevance(level, snapshot, limits)
        val dependencies = snapshot.edges.filter { it.type in DEPENDENCY_TYPES }
        val dependencyDepth = graphDepth(dependencies)
        val mechanics = snapshot.edges.map { it.type.name }.toSortedSet()
        val truncation = (snapshot.truncationReasons + v4.truncationReasons).distinct().sorted()
        val strategyCount = v4.metrics.strategy.meaningfulStrategyFamilyCount
            ?: v4.metrics.strategy.canonicalStrategyCount
        val uniqueMagneticDistances = snapshot.magneticRelationshipDistances.values.sorted()
        val meaningfulLineOfSightEdges = snapshot.edges.filter { it.type in LOS_TYPES }
        val meaningfulArrowBlockers = snapshot.edges.filter { edge ->
            edge.source.startsWith("arrow:") && edge.target.startsWith("arrow:") &&
                edge.type in setOf(InteractionTypeV5.OCCLUSION, InteractionTypeV5.ROUTE_BLOCK)
        }
        return StructuralDiagnosticsV5(
            levelId = level.id,
            difficultyBand = band,
            rows = level.height,
            columns = level.width,
            arrowCount = level.arrows.size,
            magnetCount = level.magnets.size,
            wallCount = level.walls.size,
            objectDensity = ratio(
                level.arrows.size + level.magnets.size + level.walls.size,
                level.width * level.height,
            ),
            interactionGraph = buildGraph(level, snapshot.edges),
            magneticRelationshipCount = snapshot.magneticDistances.size,
            averageMagneticDistance = snapshot.magneticDistances.averageIntOrZero(),
            maximumMagneticDistance = snapshot.magneticDistances.maxOrNull() ?: 0,
            lineOfSightInteractionCount = snapshot.edges.count {
                it.type == InteractionTypeV5.MAGNET_CONTROL || it.type == InteractionTypeV5.OCCLUSION
            },
            arrowVsArrowInteractionCount = snapshot.edges.count {
                it.source.startsWith("arrow:") && it.target.startsWith("arrow:")
            },
            magnetCancellationCount = snapshot.cancellationPairs.size,
            cancellationStateCount = snapshot.cancellationStateCount,
            cancellationTransitionCount = snapshot.cancellationTransitionCount,
            cancellationCriticalDecisionCount = snapshot.cancellationCriticalDecisionCount,
            polarityDependentDecisionCount = snapshot.polarityDecisionCount,
            polarityImpactDepth = snapshot.polarityImpactDepth,
            dependencyEdgeCount = dependencies.size,
            dependencyDepth = max(dependencyDepth, v4.metrics.ordering.dependencyGraphDepth ?: 0),
            orderingConstraintCount = max(
                snapshot.edges.count { it.type == InteractionTypeV5.ORDER_DEPENDENCY },
                v4.metrics.ordering.mandatoryOrderingPairCount ?: 0,
            ),
            mandatoryOrderingDepth = max(
                graphDepth(snapshot.edges.filter { it.type == InteractionTypeV5.ORDER_DEPENDENCY }),
                v4.metrics.ordering.mandatoryOrderingChainDepth ?: 0,
            ),
            meaningfulOrderingRate = v4.metrics.ordering.mandatoryOrderingRatio ?: 0.0,
            safeChoiceRatio = v4.metrics.safeChoiceRatio,
            meaningfulFailureRate = v4.metrics.meaningfulFailureRate,
            harmfulDecisionDensity = v4.metrics.harmfulDecisionDensity,
            strategicChoiceDensity = ratio(
                v4.metrics.meaningfulSuccessfulChoiceCount,
                v4.metrics.successfulChoiceCount,
            ),
            recoveryPressure = v4.metrics.recovery.normalizedRecoveryPressure,
            consequenceDepth = max(
                snapshot.consequenceDepth,
                v4.metrics.consequencePersistence.maximumDepth ?: 0,
            ),
            consequenceBreadth = max(
                snapshot.consequenceBreadth,
                v4.metrics.consequencePersistence.maximumMeaningfulDecisionsAffected ?: 0,
            ),
            exposureRevealCount = snapshot.edges.count {
                it.type == InteractionTypeV5.EXPOSURE || it.type == InteractionTypeV5.REVEAL
            },
            controllerChangeCount = snapshot.controllerChangeCount,
            alternativePathCount = snapshot.edges.count { it.type == InteractionTypeV5.ALTERNATIVE_PATH },
            objectRelevance = relevance,
            greedySolveRate = greedySolveRate(level),
            randomSuccessRate = v4.metrics.randomPolicy.completionRate,
            canonicalStrategyCount = v4.metrics.strategy.canonicalStrategyCount,
            commutationQuotient = strategyCount,
            permutationRedundancy = v4.metrics.strategy.permutationRedundancy,
            solverStateCount = v4.metrics.searchStateCount,
            searchComplete = snapshot.complete && v4.searchComplete,
            truncated = truncation.isNotEmpty(),
            truncationReasons = truncation,
            exactFingerprint = ContentFingerprint.of(level),
            symmetryFingerprint = ContentFingerprint.symmetryNormalized(level),
            interactionFingerprint = fingerprint(snapshot.edges.map { "${it.source}>${it.target}:${it.type}" }),
            dependencyFingerprint = fingerprint(dependencies.map { "${it.source}>${it.target}:${it.type}" }),
            strategyFingerprint = fingerprint(
                listOf(
                    "strategies=$strategyCount",
                    "ordering=${v4.metrics.ordering.mandatoryOrderingPairCount}",
                    "commuting=${v4.metrics.strategy.commutativeActionPairCount}",
                    "nonCommuting=${v4.metrics.strategy.nonCommutingActionPairCount}",
                ),
            ),
            mechanicCombinationFingerprint = fingerprint(mechanics.toList()),
            v4Score = v4.score,
            v4Confidence = v4.confidence,
            spatialDensity = SpatialDensityAnalyzerV5.analyze(level),
            uniqueMagneticRelationshipCount = uniqueMagneticDistances.size,
            magneticRelationshipDistances = uniqueMagneticDistances,
            longRangeMagneticRelationshipCount = uniqueMagneticDistances.count { it >= 4 },
            meaningfulLineOfSightInteractionCount = meaningfulLineOfSightEdges.size,
            meaningfulArrowBlockerRelationshipCount = meaningfulArrowBlockers.size,
            meaningfulWallOcclusionCount = snapshot.edges.count { edge ->
                edge.source.startsWith("wall:") && edge.type == InteractionTypeV5.OCCLUSION
            },
            exposureDepth = graphDepth(snapshot.edges.filter {
                it.type == InteractionTypeV5.EXPOSURE || it.type == InteractionTypeV5.REVEAL
            }),
            persistentConsequenceCount = snapshot.persistentConsequenceCount,
            averagePersistentConsequenceBreadth = if (snapshot.persistentConsequenceCount == 0) 0.0 else
                round4(snapshot.persistentConsequenceAffectedTotal.toDouble() / snapshot.persistentConsequenceCount),
            visibilityChangeCount = snapshot.visibilityChangeCount,
            commutativeActionPairCount = v4.metrics.strategy.commutativeActionPairCount,
            nonCommutingActionPairCount = v4.metrics.strategy.nonCommutingActionPairCount,
            viablePairCommutationRatio = v4.metrics.strategy.commutationRatio,
        )
    }

    private fun snapshot(level: LevelDefinition, limits: StructuralAnalysisLimitsV5): SnapshotV5 {
        val nodes = linkedMapOf<StateKey, StateNodeV5>()
        val queue = ArrayDeque<BoardState>()
        val edges = linkedSetOf<InteractionEdgeV5>()
        val magneticDistances = mutableListOf<Int>()
        val cancellationPairs = mutableSetOf<String>()
        val reasons = mutableSetOf<String>()
        var resolutions = 0
        var cancellationStates = 0
        var cancellationTransitions = 0
        var cancellationCritical = 0
        var polarityDecisions = 0
        var polarityDepth = 0
        var controllerChanges = 0
        var consequenceDepth = 0
        var consequenceBreadth = 0
        var persistentConsequenceCount = 0
        var persistentConsequenceAffectedTotal = 0
        var visibilityChanges = 0
        val magneticRelationshipDistances = linkedMapOf<String, Int>()

        fun enqueue(state: BoardState) {
            val key = StateKey.from(state)
            if (key in nodes) return
            if (nodes.size >= limits.maxStates) {
                reasons += "STRUCTURAL_STATE_CAP"
                return
            }
            nodes[key] = StateNodeV5(state)
            queue += state
        }
        enqueue(level.initialState())
        while (queue.isNotEmpty()) {
            val state = queue.removeFirst()
            val node = requireNotNull(nodes[StateKey.from(state)])
            var stateCancelled = false
            state.arrows.sortedBy { it.id }.forEach { arrow ->
                if (resolutions >= limits.maxActionResolutions) {
                    reasons += "STRUCTURAL_ACTION_CAP"
                    return@forEach
                }
                resolutions += 1
                val arrowKey = "arrow:${arrow.id}"
                val control = tracer.explainControl(state, arrow)
                control.controllingMagnet?.let { magnet ->
                    edges += InteractionEdgeV5("magnet:${magnet.id}", arrowKey, InteractionTypeV5.MAGNET_CONTROL)
                    val magneticDistance = distance(arrow.position, magnet.position)
                    magneticDistances += magneticDistance
                    magneticRelationshipDistances["${magnet.id}>${arrow.id}"] = magneticDistance
                }
                if (control.cancelledByEqualNearestMagnets) {
                    stateCancelled = true
                    control.equallyNearestVisibleMagnets.forEachIndexed { index, first ->
                        control.equallyNearestVisibleMagnets.drop(index + 1).forEach { second ->
                            val pair = listOf(first.id, second.id).sorted().joinToString("|")
                            cancellationPairs += pair
                            edges += InteractionEdgeV5(
                                "magnet:${first.id}", "magnet:${second.id}", InteractionTypeV5.CANCELLATION,
                            )
                        }
                    }
                }
                val magnetic = magneticDiagnostics.explain(state, arrow, 32)
                magnetic.occludingEntityKeys.forEach { blocker ->
                    edges += InteractionEdgeV5(blocker, arrowKey, InteractionTypeV5.OCCLUSION)
                }
                if (magnetic.counterfactualCapped) reasons += "MAGNETIC_COUNTERFACTUAL_CAP"

                val beforeSignatures = state.arrows.filter { it.id != arrow.id }.associate { future ->
                    future.id to resultSignature(engine.resolve(state, PlayerAction(future.id)))
                }
                val result = engine.resolve(state, PlayerAction(arrow.id))
                result.collisionTarget?.let { target ->
                    val targetKey = when (target.type) {
                        CollisionTargetType.ARROW -> "arrow:${target.entityId}"
                        CollisionTargetType.MAGNET -> "magnet:${target.entityId}"
                        CollisionTargetType.WALL -> "wall:${target.position.row},${target.position.column}"
                    }
                    edges += InteractionEdgeV5(arrowKey, targetKey, InteractionTypeV5.COLLISION)
                    edges += InteractionEdgeV5(targetKey, arrowKey, InteractionTypeV5.ROUTE_BLOCK)
                }
                if (!result.success) {
                    node.transitions += StateTransitionV5(arrow.id, result, null)
                    return@forEach
                }
                enqueue(result.resultingState)
                val childKey = StateKey.from(result.resultingState).takeIf { it in nodes }
                node.transitions += StateTransitionV5(arrow.id, result, childKey)

                var affected = 0
                result.resultingState.arrows.forEach { future ->
                    val afterResult = engine.resolve(result.resultingState, PlayerAction(future.id))
                    val before = beforeSignatures[future.id]
                    val after = resultSignature(afterResult)
                    if (before != after) {
                        affected += 1
                        edges += InteractionEdgeV5(arrowKey, "arrow:${future.id}", InteractionTypeV5.EXPOSURE)
                        edges += InteractionEdgeV5(arrowKey, "arrow:${future.id}", InteractionTypeV5.REVEAL)
                        edges += InteractionEdgeV5(arrowKey, "arrow:${future.id}", InteractionTypeV5.STATE_DEPENDENCY)
                        if (before?.controller != after.controller) {
                            controllerChanges += 1
                            visibilityChanges += 1
                        }
                        if (before?.cancelled != after.cancelled) {
                            cancellationTransitions += 1
                            cancellationCritical += 1
                            visibilityChanges += 1
                        }
                    }
                }
                if (affected > 0) {
                    consequenceDepth = max(consequenceDepth, 1)
                    consequenceBreadth = max(consequenceBreadth, affected)
                    persistentConsequenceCount += 1
                    persistentConsequenceAffectedTotal += affected
                }
                result.polarityChange?.let { change ->
                    val unflipped = result.resultingState.copy(
                        magnets = result.resultingState.magnets.map { magnet ->
                            if (magnet.id == change.magnetId) magnet.copy(polarity = change.from) else magnet
                        },
                    )
                    var impacted = 0
                    result.resultingState.arrows.forEach { future ->
                        val withFlip = resultSignature(engine.resolve(result.resultingState, PlayerAction(future.id)))
                        val withoutFlip = resultSignature(engine.resolve(unflipped, PlayerAction(future.id)))
                        if (withFlip != withoutFlip) {
                            impacted += 1
                            polarityDecisions += 1
                            edges += InteractionEdgeV5(
                                "magnet:${change.magnetId}", "arrow:${future.id}",
                                InteractionTypeV5.POLARITY_DEPENDENCY,
                            )
                            edges += InteractionEdgeV5(
                                arrowKey, "arrow:${future.id}", InteractionTypeV5.STATE_DEPENDENCY,
                            )
                        }
                    }
                    if (impacted > 0) {
                        val persistence = polarityPersistenceDepth(result.resultingState, unflipped)
                        polarityDepth = max(polarityDepth, persistence.first)
                        if (!persistence.second) reasons += "POLARITY_PERSISTENCE_CAP"
                        consequenceBreadth = max(consequenceBreadth, impacted)
                    }
                }
            }
            if (stateCancelled) cancellationStates += 1
        }

        val solvable = mutableMapOf<StateKey, Boolean>()
        fun canSolve(key: StateKey, visiting: MutableSet<StateKey> = mutableSetOf()): Boolean {
            solvable[key]?.let { return it }
            val node = nodes[key] ?: return false
            if (node.state.arrows.isEmpty()) return true.also { solvable[key] = it }
            if (!visiting.add(key)) return false
            val value = node.transitions.any { transition ->
                transition.result.success && transition.childKey?.let { canSolve(it, visiting) } == true
            }
            visiting.remove(key)
            solvable[key] = value
            return value
        }
        nodes.keys.forEach { canSolve(it) }
        nodes.values.forEach { node ->
            val successful = node.transitions.filter { it.result.success && it.childKey != null }
            val viable = successful.filter { transition -> solvable[transition.childKey] == true }
            val fatal = successful.filter { transition -> solvable[transition.childKey] == false }
            if (viable.isNotEmpty() && fatal.isNotEmpty()) {
                viable.forEach { good ->
                    fatal.forEach { bad ->
                        edges += InteractionEdgeV5(
                            "arrow:${good.actionId}", "arrow:${bad.actionId}", InteractionTypeV5.ORDER_DEPENDENCY,
                        )
                    }
                }
            }
            if (viable.size > 1) {
                viable.forEachIndexed { index, first ->
                    viable.drop(index + 1).forEach { second ->
                        if (resultSignature(first.result) != resultSignature(second.result)) {
                            edges += InteractionEdgeV5(
                                "arrow:${first.actionId}", "arrow:${second.actionId}",
                                InteractionTypeV5.ALTERNATIVE_PATH,
                            )
                        }
                    }
                }
            }
        }
        consequenceDepth = max(consequenceDepth, graphDepth(edges.filter { it.type in DEPENDENCY_TYPES }))
        return SnapshotV5(
            edges = edges.toList().sortedWith(compareBy(InteractionEdgeV5::source, InteractionEdgeV5::target, InteractionEdgeV5::type)),
            magneticDistances = magneticDistances,
            magneticRelationshipDistances = magneticRelationshipDistances,
            cancellationPairs = cancellationPairs,
            cancellationStateCount = cancellationStates,
            cancellationTransitionCount = cancellationTransitions,
            cancellationCriticalDecisionCount = cancellationCritical,
            polarityDecisionCount = polarityDecisions,
            polarityImpactDepth = polarityDepth,
            controllerChangeCount = controllerChanges,
            consequenceDepth = consequenceDepth,
            consequenceBreadth = consequenceBreadth,
            persistentConsequenceCount = persistentConsequenceCount,
            persistentConsequenceAffectedTotal = persistentConsequenceAffectedTotal,
            visibilityChangeCount = visibilityChanges,
            complete = reasons.isEmpty(),
            truncationReasons = reasons.toList().sorted(),
            stateCount = nodes.size,
            actionCount = resolutions,
            solvable = solvable[StateKey.from(level.initialState())],
            winningFirstActions = nodes[StateKey.from(level.initialState())]?.transitions.orEmpty()
                .filter { it.result.success && it.childKey?.let { key -> solvable[key] } == true }
                .map { it.actionId }.toSet(),
        )
    }

    private fun objectRelevance(
        level: LevelDefinition,
        baseline: SnapshotV5,
        limits: StructuralAnalysisLimitsV5,
    ): ObjectRelevanceSummaryV5 {
        val graph = buildGraph(level, baseline.edges)
        val degree = graph.edges.flatMap { listOf(it.source, it.target) }.groupingBy { it }.eachCount()
        val maxDegree = degree.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val objects = graph.nodes.take(limits.maxCounterfactualObjects).map { node ->
            val variant = removeObject(level, node.key)
            val variantSnapshot = if (variant.arrows.isEmpty()) null else snapshot(
                variant,
                limits.copy(
                    maxStates = minOf(limits.maxStates, 30_000),
                    maxActionResolutions = minOf(limits.maxActionResolutions, 300_000),
                    maxCounterfactualObjects = 0,
                ),
            )
            val complete = variantSnapshot?.complete ?: true
            val solvabilityChanged = baseline.solvable != (variantSnapshot?.solvable ?: true)
            val firstChange = setDistance(
                baseline.winningFirstActions - node.key.substringAfter(":"),
                variantSnapshot?.winningFirstActions.orEmpty(),
            )
            val strategyChange = normalizedDelta(
                baseline.winningFirstActions.size,
                variantSnapshot?.winningFirstActions?.size ?: 0,
            )
            val dependencyChange = normalizedDelta(
                baseline.edges.count { it.type in DEPENDENCY_TYPES && node.key !in setOf(it.source, it.target) },
                variantSnapshot?.edges?.count { it.type in DEPENDENCY_TYPES } ?: 0,
            )
            val polarityChange = normalizedDelta(
                baseline.polarityDecisionCount,
                variantSnapshot?.polarityDecisionCount ?: 0,
            )
            val decisionChange = normalizedDelta(
                baseline.consequenceBreadth,
                variantSnapshot?.consequenceBreadth ?: 0,
            )
            val orderingChange = normalizedDelta(
                baseline.edges.count { it.type == InteractionTypeV5.ORDER_DEPENDENCY },
                variantSnapshot?.edges?.count { it.type == InteractionTypeV5.ORDER_DEPENDENCY } ?: 0,
            )
            val losChange = edgeSetDistance(baseline, variantSnapshot, LOS_TYPES)
            val cancellationChange = normalizedDelta(
                baseline.cancellationTransitionCount + baseline.cancellationPairs.size,
                (variantSnapshot?.cancellationTransitionCount ?: 0) + (variantSnapshot?.cancellationPairs?.size ?: 0),
            )
            val routeChange = edgeSetDistance(baseline, variantSnapshot, ROUTE_TYPES)
            val interactionParticipation = (degree[node.key] ?: 0).toDouble() / maxDegree
            val score = if (solvabilityChanged) {
                1.0
            } else {
                (
                    firstChange * 0.12 + strategyChange * 0.08 + dependencyChange * 0.18 +
                        polarityChange * 0.12 + decisionChange * 0.12 + orderingChange * 0.12 +
                        losChange * 0.10 + cancellationChange * 0.06 + routeChange * 0.05 +
                        interactionParticipation * 0.05
                    ).coerceIn(0.0, 1.0)
            }
            ObjectRelevanceV5(
                objectKey = node.key,
                objectType = node.type,
                classification = when {
                    score >= 0.65 -> ObjectRelevanceClassV5.CRITICAL
                    score >= 0.35 -> ObjectRelevanceClassV5.HIGH
                    score >= 0.12 -> ObjectRelevanceClassV5.MEDIUM
                    score >= 0.03 -> ObjectRelevanceClassV5.LOW
                    else -> ObjectRelevanceClassV5.IRRELEVANT
                },
                score = round4(score),
                solvabilityChanged = solvabilityChanged,
                winningFirstActionChange = round4(firstChange),
                strategyFamilyChange = round4(strategyChange),
                dependencyGraphChange = round4(dependencyChange),
                polarityTransitionChange = round4(polarityChange),
                meaningfulDecisionChange = round4(decisionChange),
                orderingConstraintChange = round4(orderingChange),
                lineOfSightChange = round4(losChange),
                cancellationChange = round4(cancellationChange),
                routeStructureChange = round4(routeChange),
                analysisComplete = complete,
            )
        }
        val relevant = objects.count { it.classification in setOf(
            ObjectRelevanceClassV5.CRITICAL,
            ObjectRelevanceClassV5.HIGH,
            ObjectRelevanceClassV5.MEDIUM,
        ) }
        return ObjectRelevanceSummaryV5(
            objects = objects,
            averageScore = round4(objects.map { it.score }.averageDoubleOrZero()),
            relevantObjectCount = relevant,
            irrelevantObjectCount = objects.count { it.classification == ObjectRelevanceClassV5.IRRELEVANT },
            relevantObjectRatio = ratio(relevant, objects.size),
            analysisComplete = objects.size == graph.nodes.size && objects.all { it.analysisComplete },
            relevantObjectCountByType = objects.filter { it.classification in setOf(
                ObjectRelevanceClassV5.CRITICAL,
                ObjectRelevanceClassV5.HIGH,
                ObjectRelevanceClassV5.MEDIUM,
            ) }.groupingBy { it.objectType.name }.eachCount().toSortedMap(),
            irrelevantObjectCountByType = objects.filter {
                it.classification == ObjectRelevanceClassV5.IRRELEVANT
            }.groupingBy { it.objectType.name }.eachCount().toSortedMap(),
        )
    }

    private fun buildGraph(level: LevelDefinition, edges: List<InteractionEdgeV5>): InteractionGraphV5 {
        val nodes = buildList {
            level.arrows.forEach { add(InteractionNodeV5("arrow:${it.id}", InteractionNodeTypeV5.ARROW)) }
            level.magnets.forEach { add(InteractionNodeV5("magnet:${it.id}", InteractionNodeTypeV5.MAGNET)) }
            level.walls.forEach { wall ->
                add(InteractionNodeV5("wall:${wall.position.row},${wall.position.column}", InteractionNodeTypeV5.WALL))
            }
        }.sortedBy { it.key }
        val neighborMap = nodes.associate { it.key to mutableSetOf<String>() }
        edges.forEach { edge ->
            neighborMap[edge.source]?.add(edge.target)
            neighborMap[edge.target]?.add(edge.source)
        }
        val seen = mutableSetOf<String>()
        val componentSizes = mutableListOf<Int>()
        nodes.forEach { node ->
            if (!seen.add(node.key)) return@forEach
            var count = 0
            val queue = ArrayDeque<String>().apply { add(node.key) }
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                count += 1
                neighborMap[current].orEmpty().forEach { if (seen.add(it)) queue += it }
            }
            componentSizes += count
        }
        val interacting = neighborMap.count { it.value.isNotEmpty() }
        val meaningfulPairs = edges.map { listOf(it.source, it.target).sorted().joinToString("|") }.toSet().size
        val possiblePairs = nodes.size * (nodes.size - 1) / 2
        val degrees = neighborMap.values.map { it.size }
        return InteractionGraphV5(
            nodes = nodes,
            edges = edges,
            totalInteractionEdges = edges.size,
            uniqueInteractingObjects = interacting,
            interactionDensity = ratio(meaningfulPairs, possiblePairs),
            connectedComponents = componentSizes.size,
            largestConnectedComponent = componentSizes.maxOrNull() ?: 0,
            isolatedObjects = degrees.count { it == 0 },
            averageObjectDegree = round4(degrees.averageIntOrZero()),
            maximumObjectDegree = degrees.maxOrNull() ?: 0,
            interactionTypeDistribution = edges.groupingBy { it.type.name }.eachCount().toSortedMap(),
            fingerprint = fingerprint(edges.map { "${it.source}>${it.target}:${it.type}" }),
        )
    }

    private fun removeObject(level: LevelDefinition, key: String): LevelDefinition = when {
        key.startsWith("arrow:") -> level.copy(
            arrows = level.arrows.filterNot { it.id == key.substringAfter(":") },
            designedSolutions = level.designedSolutions.map { it - key.substringAfter(":") },
            metadata = null,
        )
        key.startsWith("magnet:") -> level.copy(
            magnets = level.magnets.filterNot { it.id == key.substringAfter(":") },
            metadata = null,
        )
        key.startsWith("wall:") -> {
            val position = key.substringAfter(":").split(',').map(String::toInt)
            level.copy(
                walls = level.walls.filterNot { it.position == Position(position[0], position[1]) },
                metadata = null,
            )
        }
        else -> error("Unknown object key $key")
    }

    private fun graphDepth(edges: List<InteractionEdgeV5>): Int {
        if (edges.isEmpty()) return 0
        val outgoing = edges.groupBy { it.source }.mapValues { (_, value) -> value.map { it.target }.distinct() }
        fun visit(node: String, path: Set<String>): Int {
            if (node in path) return 0
            return 1 + (outgoing[node].orEmpty().maxOfOrNull { visit(it, path + node) } ?: -1)
        }
        return outgoing.keys.maxOfOrNull { visit(it, emptySet()) } ?: 0
    }

    private fun greedySolveRate(level: LevelDefinition): Double {
        val authoredOrder = level.arrows.mapIndexed { index, arrow -> arrow.id to index }.toMap()
        val policies: List<(BoardState) -> String?> = listOf(
            { state -> engine.validActions(state).minByOrNull { authoredOrder[it.arrowId] ?: Int.MAX_VALUE }?.arrowId },
            { state -> engine.validActions(state).minByOrNull { action ->
                engine.resolve(state, action).traversedCells.size
            }?.arrowId },
            { state -> engine.validActions(state).maxByOrNull { action ->
                val result = engine.resolve(state, action)
                (if (result.polarityChange != null) 1_000 else 0) + result.traversedCells.size
            }?.arrowId },
        )
        val solved = policies.count { choose ->
            var state = level.initialState()
            while (state.arrows.isNotEmpty()) {
                val action = choose(state) ?: break
                val result = engine.resolve(state, PlayerAction(action))
                if (!result.success) break
                state = result.resultingState
            }
            state.arrows.isEmpty()
        }
        return round4(solved.toDouble() / policies.size)
    }

    /** Longest future depth at which the real flip changes a production-engine outcome. */
    private fun polarityPersistenceDepth(
        flipped: BoardState,
        unflipped: BoardState,
        maximumPairs: Int = 10_000,
    ): Pair<Int, Boolean> {
        data class PairState(val first: BoardState, val second: BoardState, val depth: Int)
        val queue = ArrayDeque<PairState>()
        val seen = mutableSetOf<Pair<StateKey, StateKey>>()
        queue += PairState(flipped, unflipped, 1)
        var maximumImpact = 0
        var expanded = 0
        while (queue.isNotEmpty()) {
            if (expanded >= maximumPairs) return maximumImpact to false
            val current = queue.removeFirst()
            val key = StateKey.from(current.first) to StateKey.from(current.second)
            if (!seen.add(key)) continue
            expanded += 1
            val actionIds = (current.first.arrows.map { it.id } intersect current.second.arrows.map { it.id }.toSet())
                .sorted()
            actionIds.forEach { actionId ->
                val firstResult = engine.resolve(current.first, PlayerAction(actionId))
                val secondResult = engine.resolve(current.second, PlayerAction(actionId))
                if (resultSignature(firstResult) != resultSignature(secondResult)) {
                    maximumImpact = max(maximumImpact, current.depth)
                }
                if (firstResult.success && secondResult.success &&
                    firstResult.resultingState.arrows.isNotEmpty() && secondResult.resultingState.arrows.isNotEmpty()
                ) {
                    queue += PairState(firstResult.resultingState, secondResult.resultingState, current.depth + 1)
                }
            }
        }
        return maximumImpact to true
    }

    private fun resultSignature(result: ResolutionResult): ResultSignatureV5 = ResultSignatureV5(
        success = result.success,
        controller = result.controllingMagnetId,
        effectiveDirection = result.effectiveDirection.name,
        terminal = result.terminalEvent::class.simpleName.orEmpty(),
        cancelled = result.controllingMagnetId == null && result.originalState.magnets.size > 1,
    )

    private fun distance(a: Position, b: Position): Int = abs(a.row - b.row) + abs(a.column - b.column)

    private fun normalizedDelta(first: Int, second: Int): Double =
        abs(first - second).toDouble() / max(1, max(first, second))

    private fun setDistance(first: Set<String>, second: Set<String>): Double {
        val union = first union second
        return if (union.isEmpty()) 0.0 else 1.0 - (first intersect second).size.toDouble() / union.size
    }

    private fun edgeSetDistance(
        baseline: SnapshotV5,
        variant: SnapshotV5?,
        types: Set<InteractionTypeV5>,
    ): Double = setDistance(
        baseline.edges.filter { edge -> edge.type in types }
            .mapTo(mutableSetOf()) { "${it.source}>${it.target}:${it.type}" },
        variant?.edges.orEmpty().filter { it.type in types }
            .mapTo(mutableSetOf()) { "${it.source}>${it.target}:${it.type}" },
    )

    private fun fingerprint(parts: List<String>): String = ContentFingerprint.sha256Hex(parts.sorted().joinToString("|"))

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator <= 0) 0.0 else round4(numerator.toDouble() / denominator)

    private fun Iterable<Int>.averageIntOrZero(): Double = toList().let { if (it.isEmpty()) 0.0 else it.average() }
    private fun Iterable<Double>.averageDoubleOrZero(): Double = toList().let { if (it.isEmpty()) 0.0 else it.average() }
    private fun round4(value: Double): Double = kotlin.math.round(value * 10_000.0) / 10_000.0

    private data class StateNodeV5(
        val state: BoardState,
        val transitions: MutableList<StateTransitionV5> = mutableListOf(),
    )

    private data class StateTransitionV5(
        val actionId: String,
        val result: ResolutionResult,
        val childKey: StateKey?,
    )

    private data class ResultSignatureV5(
        val success: Boolean,
        val controller: String?,
        val effectiveDirection: String,
        val terminal: String,
        val cancelled: Boolean,
    )

    private data class SnapshotV5(
        val edges: List<InteractionEdgeV5>,
        val magneticDistances: List<Int>,
        val magneticRelationshipDistances: Map<String, Int>,
        val cancellationPairs: Set<String>,
        val cancellationStateCount: Int,
        val cancellationTransitionCount: Int,
        val cancellationCriticalDecisionCount: Int,
        val polarityDecisionCount: Int,
        val polarityImpactDepth: Int,
        val controllerChangeCount: Int,
        val consequenceDepth: Int,
        val consequenceBreadth: Int,
        val persistentConsequenceCount: Int,
        val persistentConsequenceAffectedTotal: Int,
        val visibilityChangeCount: Int,
        val complete: Boolean,
        val truncationReasons: List<String>,
        val stateCount: Int,
        val actionCount: Int,
        val solvable: Boolean?,
        val winningFirstActions: Set<String>,
    )

    private companion object {
        val DEPENDENCY_TYPES = setOf(
            InteractionTypeV5.ORDER_DEPENDENCY,
            InteractionTypeV5.STATE_DEPENDENCY,
            InteractionTypeV5.POLARITY_DEPENDENCY,
            InteractionTypeV5.EXPOSURE,
            InteractionTypeV5.REVEAL,
            InteractionTypeV5.ROUTE_BLOCK,
        )
        val LOS_TYPES = setOf(
            InteractionTypeV5.MAGNET_CONTROL,
            InteractionTypeV5.OCCLUSION,
            InteractionTypeV5.CANCELLATION,
        )
        val ROUTE_TYPES = setOf(
            InteractionTypeV5.COLLISION,
            InteractionTypeV5.ROUTE_BLOCK,
            InteractionTypeV5.EXPOSURE,
            InteractionTypeV5.REVEAL,
        )
    }
}

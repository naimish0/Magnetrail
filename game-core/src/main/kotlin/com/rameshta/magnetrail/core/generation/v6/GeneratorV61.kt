package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.generation.SeededRandom
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.core.solver.Solver
import java.security.MessageDigest
import kotlin.math.roundToInt

const val V61_PUBLIC_GENERATOR_SALT = "magnetrail-v6.1-auto-journey-public-salt-1"
const val V61_CAUSAL_FAMILY_COUNT = 24
const val V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT = 1_024
const val V61_HARD_CAPACITY_ROLLOUT_LEVEL = 1_557
const val V61_HIGH_BAND_CAPACITY_EPOCH_START_ATTEMPT = 2_048
const val V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL = 759

internal fun hardCampaignCapacityAttemptV61(levelNumber: Int, attempt: Int): Int =
    if (levelNumber >= V61_HARD_CAPACITY_ROLLOUT_LEVEL) {
        attempt + V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT
    } else {
        attempt
    }

/**
 * Adds spatial capacity only after the original Hard campaign search epoch is exhausted. Position
 * isometries deliberately leave printed directions unchanged: transforming both would merely
 * create a D4 duplicate, while transforming position alone creates a genuinely different board
 * whose complete production DAG, difficulty, occupancy, solver and replay evidence must be
 * reconstructed before admission. Attempts below the epoch boundary are byte-for-byte unchanged.
 */
internal fun applyHardCampaignCapacityEmbeddingV61(
    level: LevelDefinition,
    attempt: Int,
): LevelDefinition {
    if (attempt < V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT) return level
    require(level.width == 6 && level.height == 6)
    val embedding = 1 + Math.floorMod(attempt - V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT, 7)
    fun transform(position: Position): Position {
        val row = position.row
        val column = position.column
        return when (embedding) {
            1 -> Position(row, 7 - column)
            2 -> Position(7 - row, column)
            3 -> Position(7 - row, 7 - column)
            4 -> Position(column, row)
            5 -> Position(column, 7 - row)
            6 -> Position(7 - column, row)
            else -> Position(7 - column, 7 - row)
        }
    }
    return level.copy(
        arrows = level.arrows.map { it.copy(position = transform(it.position)) },
        magnets = level.magnets.map { it.copy(position = transform(it.position)) },
        walls = level.walls.map { it.copy(position = transform(it.position)) },
    )
}

enum class TopologyFamilyV61 {
    SHARED_MAGNET_PARITY_BRAID,
    NESTED_NON_COMMUTING_CHOICES,
    WRONG_NOW_RIGHT_LATER,
    CROSS_CHAIN_CONTROLLER_HANDOFF,
    OCCLUSION_REVEAL_FORK_JOIN,
    CANCELLATION_POLARITY_RESTORATION,
    TWO_CHAIN_INTERLOCK,
    OPENING_TO_ENDGAME_DELAYED_TRAP,
}

data class GeneratorV61Identity(
    val ordinal: Int,
    val band: AutomatedDifficultyBandV61,
    val attempt: Int,
    val topologyFamily: TopologyFamilyV61,
    val familyVariant: Int,
    val seed: Long,
    val generatorIdentity: String = GENERATOR_IDENTITY_V61,
    val schemaVersion: Int = V61_DIFFICULTY_SCHEMA_VERSION,
) {
    init {
        require(ordinal > 0)
        require(attempt >= 0)
        require(familyVariant in 0..2)
    }

    val causalFamilyIdentifier: String
        get() = "${topologyFamily.name.lowercase()}-v${familyVariant + 1}"

    val stableKey: String
        get() = "auto-journey-v1-$ordinal-${band.name.lowercase()}-$attempt-$seed"
}

data class V61GenerationBudgets(
    val maximumAttempts: Int = 64,
    val decisionDagStates: Int = 75_000,
    val decisionDagResolutions: Int = 750_000,
    val solverStates: Int = 75_000,
    val counterfactualChecks: Int = 250_000,
    val canonicalBacktrackingStates: Int = 100_000,
) {
    init {
        require(maximumAttempts > 0)
        require(decisionDagStates > 0 && decisionDagResolutions > 0 && solverStates > 0)
        require(counterfactualChecks > 0 && canonicalBacktrackingStates > 0)
    }
}

data class V61GenerationRequest(
    val levelId: String,
    val playerFacingNumber: Int,
    val ordinal: Int,
    val band: AutomatedDifficultyBandV61,
    val publicSalt: String = V61_PUBLIC_GENERATOR_SALT,
    val causalFamilyIndex: Int? = null,
    val varyCausalFamilyByAttempt: Boolean = false,
    val startingAttempt: Int = 0,
    val knownFingerprints: List<V6FingerprintBundle> = emptyList(),
    val knownFingerprintIndex: V61FingerprintIndex? = null,
    val analysisCache: V61AnalysisCache? = null,
    val forbiddenCausalFamilies: Set<String> = emptySet(),
    val forbiddenStrategyClusters: Set<String> = emptySet(),
    val budgets: V61GenerationBudgets = V61GenerationBudgets(),
) {
    init {
        require(levelId.isNotBlank())
        require(playerFacingNumber > 0 && ordinal > 0 && startingAttempt >= 0)
        require(publicSalt.isNotBlank())
        require(causalFamilyIndex == null || causalFamilyIndex in 0 until V61_CAUSAL_FAMILY_COUNT)
    }
}

data class V61CertifiedCandidate(
    val identity: GeneratorV61Identity,
    val level: LevelDefinition,
    val causalSpec: CausalHypergraphSpec,
    val causalWitnesses: List<CausalWitness>,
    val decisionDag: DecisionDagAnalysisV6,
    val purposefulOccupancy: PurposefulOccupancyV6,
    val fingerprints: V6FingerprintBundle,
    val difficulty: AutomatedDifficultyAssessmentV61,
    val independentSolverVerified: Boolean,
    val failedActionImmutabilityVerified: Boolean,
    val examinedAttempts: Int,
    val synthesisGraphFingerprint: String? = null,
)

data class V61GenerationFailure(
    val request: V61GenerationRequest,
    val examinedAttempts: Int,
    val rejectionCounts: Map<String, Int>,
    val lastCounterexamples: List<String>,
)

sealed interface V61GenerationResult {
    data class Certified(val candidate: V61CertifiedCandidate) : V61GenerationResult
    data class Rejected(val failure: V61GenerationFailure) : V61GenerationResult
}

/**
 * Deterministic counterexample-guided V6.1 synthesis. Logical topology and family are selected
 * before geometry. Every completed geometry is accepted only after full production-engine DAG,
 * independent solver replay, causal-witness, purposeful-object, semantic, and adversarial checks.
 */
class GeneratorV61(
    private val engine: GameEngine = DefaultGameEngine(),
    private val engineGuidedRealizer: EngineGuidedSpatialRealizerV6 = EngineGuidedSpatialRealizerV6(engine),
    private val superHardLogicalSynthesizer: SuperHardLogicalGraphSynthesizerV61 =
        SuperHardLogicalGraphSynthesizerV61(),
    private val expertLogicalSynthesizer: ExpertLogicalGraphSynthesizerV61 = ExpertLogicalGraphSynthesizerV61(),
) {
    fun generate(request: V61GenerationRequest): V61GenerationResult {
        if (request.band == AutomatedDifficultyBandV61.MASTER) {
            return V61GenerationResult.Rejected(
                V61GenerationFailure(
                    request = request,
                    examinedAttempts = 0,
                    rejectionCounts = mapOf("REJECT_MASTER_BAND_OWNER_REMOVED" to 1),
                    lastCounterexamples = listOf(
                        "Master was removed from V6.1 campaign and Auto Journey after bounded synthesis failed " +
                            "the decision-phase, cheap-policy, analysis-cost, and cleanup gates.",
                    ),
                ),
            )
        }
        val rejectionCounts = linkedMapOf<String, Int>()
        val counterexamples = ArrayDeque<String>()
        fun reject(reason: String) {
            rejectionCounts[reason] = rejectionCounts.getOrDefault(reason, 0) + 1
            if (counterexamples.size == 12) counterexamples.removeFirst()
            counterexamples.addLast(reason)
        }

        repeat(request.budgets.maximumAttempts) { attemptOffset ->
            if (Thread.currentThread().isInterrupted) throw InterruptedException("cancelled speculative V6.1 attempt")
            val attempt = request.startingAttempt + attemptOffset
            val identity = identity(request, attempt)
            if (identity.causalFamilyIdentifier in request.forbiddenCausalFamilies) {
                reject("REJECT_CAUSAL_FAMILY_PACING")
                return@repeat
            }
            val level = realizeChallengeSpine(request, identity, counterexamples.toList())
            request.knownFingerprintIndex?.layoutDuplicateReason(level)?.let { duplicate ->
                // Exact and D4 layout checks are content-complete without any semantic analysis.
                // Reject them before allocating a complete DAG; every deeper duplicate gate still
                // runs in its original position for structurally novel candidates.
                reject(duplicate)
                return@repeat
            }
            val dag = request.analysisCache?.decisionDag(
                level,
                request.budgets.decisionDagStates,
                request.budgets.decisionDagResolutions,
            ) {
                CompleteDecisionDagAnalyzerV6(
                    engine,
                    request.budgets.decisionDagStates,
                    request.budgets.decisionDagResolutions,
                ).analyze(level.initialState())
            } ?: CompleteDecisionDagAnalyzerV6(
                engine,
                request.budgets.decisionDagStates,
                request.budgets.decisionDagResolutions,
            ).analyze(level.initialState())
            if (!dag.complete || dag.metrics == null) {
                reject("REJECT_ANALYSIS_TRUNCATED")
                return@repeat
            }
            if (dag.metrics.shortestCompletion == null) {
                reject("REJECT_UNSOLVABLE")
                return@repeat
            }
            if (!failedMovesImmutable(dag)) {
                reject("REJECT_FAILED_ACTION_MUTATION")
                return@repeat
            }
            val profile = occupancyProfile(request.band)
            val occupancy = request.analysisCache?.occupancy(
                level,
                profile,
                request.budgets.counterfactualChecks,
            ) {
                PurposefulOccupancyAnalyzerV6(engine, request.budgets.counterfactualChecks)
                    .analyze(level, dag, profile)
            } ?: PurposefulOccupancyAnalyzerV6(engine, request.budgets.counterfactualChecks)
                .analyze(level, dag, profile)
            if (occupancy.rejectionReasons.isNotEmpty()) {
                reject("REJECT_PURPOSEFUL_OCCUPANCY:${occupancy.rejectionReasons.first()}")
                return@repeat
            }
            val v6Identity = GeneratorV6Identity(
                seed = identity.seed,
                bucket = if (request.band.rank <= 4) request.band.rank else 5,
                grammarFamily = causalGrammar(identity),
                graphInstance = request.ordinal + attempt,
            )
            val extracted = request.analysisCache?.extractedSpec(level, v6Identity, profile) {
                ProductionCausalSpecExtractorV6(engine).extract(v6Identity, profile, level, dag)
            } ?: ProductionCausalSpecExtractorV6(engine).extract(v6Identity, profile, level, dag)
            if (extracted == null) {
                reject("REJECT_CAUSAL_SPEC_EXTRACTION")
                return@repeat
            }
            val causalVerification = request.analysisCache?.causalVerification(level, extracted.spec) {
                ProductionCausalVerifierV6(engine).verify(extracted.spec, extracted.bindings, dag)
            } ?: ProductionCausalVerifierV6(engine).verify(extracted.spec, extracted.bindings, dag)
            if (!causalVerification.complete) {
                reject("REJECT_CAUSAL_WITNESS:${causalVerification.rejections.firstOrNull()?.code}")
                return@repeat
            }
            val synthesisGraph = synthesisGraphFingerprint(request, identity)
            val fingerprintBase = runCatching {
                request.analysisCache?.fingerprint(level, extracted.spec) {
                    SemanticFingerprintBuilderV6(
                        ExactColoredGraphCanonicalizerV6(request.budgets.canonicalBacktrackingStates),
                    ).build(level, extracted.spec, dag, occupancy).getOrThrow()
                } ?: SemanticFingerprintBuilderV6(
                    ExactColoredGraphCanonicalizerV6(request.budgets.canonicalBacktrackingStates),
                ).build(
                    level,
                    extracted.spec,
                    dag,
                    occupancy,
                    if (request.knownFingerprintIndex == null) request.knownFingerprints else emptyList(),
                ).getOrThrow()
            }.getOrElse {
                reject("REJECT_CANONICALIZATION_CAP")
                return@repeat
            }
            val fingerprintWithNearest = request.knownFingerprintIndex?.attachExactNearest(fingerprintBase)
                ?: fingerprintBase
            val fingerprint = fingerprintWithNearest.copy(
                causalFamilyIdentifier = identity.causalFamilyIdentifier,
                synthesisGraphIdentifier = synthesisGraph,
            )
            val duplicate = request.knownFingerprintIndex?.duplicateReason(fingerprint)
                ?: duplicateReason(fingerprint, request.knownFingerprints)
            if (duplicate != null) {
                reject(duplicate)
                return@repeat
            }
            if (fingerprint.strategyBehaviourClusterIdentifier in request.forbiddenStrategyClusters) {
                reject("REJECT_STRATEGY_CLUSTER_PACING")
                return@repeat
            }
            val difficulty = request.analysisCache?.difficulty(level, extracted.spec) {
                CuedAdversarialDifficultyAnalyzerV61().analyze(
                    level, dag, extracted.spec, semanticNoveltyPass = true, purposefulOccupancyPass = true,
                )
            } ?: CuedAdversarialDifficultyAnalyzerV61().analyze(
                level, dag, extracted.spec, semanticNoveltyPass = true, purposefulOccupancyPass = true,
            )
            if (!difficulty.accepted || difficulty.maximumEligibleBand == null ||
                difficulty.maximumEligibleBand.rank < request.band.rank
            ) {
                val cap = difficulty.maximumEligibleBand?.name ?: "NONE"
                reject(
                    "REJECT_DIFFICULTY_CAP:$cap:" +
                        "length=${difficulty.solutionActions}," +
                        "decisions=${difficulty.criticalEpisodes.size}," +
                        "traps=${difficulty.persistentSuccessfulTrapCount}," +
                        "phases=${difficulty.causalPhaseCount}," +
                        "lookahead=${difficulty.criticalEpisodes.maxOfOrNull { it.proof.minimumVisibleLookahead } ?: 0}," +
                        "forced=${difficulty.maximumForcedRun}," +
                        "cheap=${difficulty.policyReport.bestCheapPolicySolveRate}," +
                        "crossChains=${difficulty.crossChainDependencyCount}," +
                        "bands=${difficulty.evidenceBands}," +
                        "dominators=${difficulty.policyReport.dominatingCheapPolicies.joinToString("+")}," +
                        "reasons=${difficulty.rejectionReasons.joinToString("+")}",
                )
                return@repeat
            }
            val solver = request.analysisCache?.solver(level, request.budgets.solverStates) {
                Solver(engine).solve(
                    level.initialState(), solutionLimit = 100_000, maxExploredStates = request.budgets.solverStates,
                )
            } ?: Solver(engine).solve(
                level.initialState(), solutionLimit = 100_000, maxExploredStates = request.budgets.solverStates,
            )
            if (!solver.searchComplete || !solver.solvable || solver.oneCleanSolution == null) {
                reject("REJECT_SOLVER_DISAGREEMENT_OR_TRUNCATION")
                return@repeat
            }
            val replayPassed = request.analysisCache?.replay(
                level,
                solver.oneCleanSolution.map { it.arrowId },
            ) { replaySolutionV61(level, solver.oneCleanSolution.map { it.arrowId }) } ?:
                replaySolutionV61(level, solver.oneCleanSolution.map { it.arrowId })
            if (!replayPassed) {
                reject("REJECT_SOLVER_REPLAY")
                return@repeat
            }
            return V61GenerationResult.Certified(
                V61CertifiedCandidate(
                    identity = identity,
                    level = level.copy(designedSolutions = listOf(solver.oneCleanSolution.map { it.arrowId })),
                    causalSpec = extracted.spec,
                    causalWitnesses = causalVerification.witnesses,
                    decisionDag = dag,
                    purposefulOccupancy = occupancy,
                    fingerprints = fingerprint,
                    difficulty = difficulty,
                    independentSolverVerified = true,
                    failedActionImmutabilityVerified = true,
                    examinedAttempts = attempt + 1,
                    synthesisGraphFingerprint = synthesisGraph.takeUnless { it.startsWith("unavailable:") },
                ),
            )
        }
        return V61GenerationResult.Rejected(
            V61GenerationFailure(
                request,
                request.startingAttempt + request.budgets.maximumAttempts,
                rejectionCounts,
                counterexamples.toList(),
            ),
        )
    }

    fun reproduceIdentity(request: V61GenerationRequest, attempt: Int): GeneratorV61Identity = identity(request, attempt)

    /**
     * Returns the canonical logical graph actually consumed by a graph-first constructor. The
     * numbered Easy, Medium, and Hard campaign paths use scalable graph-first proposals in V6.1,
     * so their synthesis evidence participates in the same exact uniqueness gate as the two high
     * bands. Non-campaign spatial constructors retain the explicit unavailable marker.
     */
    fun synthesisGraphFingerprintForAttempt(
        request: V61GenerationRequest,
        attempt: Int,
    ): String = synthesisGraphFingerprint(request, identity(request, attempt))

    private fun synthesisGraphFingerprint(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
    ): String = when {
        request.varyCausalFamilyByAttempt && request.band in setOf(
            AutomatedDifficultyBandV61.EASY,
            AutomatedDifficultyBandV61.MEDIUM,
        ) -> superHardLogicalSynthesizer.synthesize(
            identity.copy(band = AutomatedDifficultyBandV61.SUPER_HARD),
        ).canonicalGraphFingerprint
        request.varyCausalFamilyByAttempt && request.band == AutomatedDifficultyBandV61.HARD ->
            expertLogicalSynthesizer.synthesize(
                identity.copy(band = AutomatedDifficultyBandV61.EXPERT),
            ).canonicalGraphFingerprint
        request.band == AutomatedDifficultyBandV61.SUPER_HARD ->
            superHardLogicalSynthesizer.synthesize(identity).canonicalGraphFingerprint
        request.band == AutomatedDifficultyBandV61.EXPERT ->
            expertLogicalSynthesizer.synthesize(identity).canonicalGraphFingerprint
        else -> "unavailable:synthesis-graph"
    }

    private fun identity(request: V61GenerationRequest, attempt: Int): GeneratorV61Identity {
        val combinedFamilyIndex = request.causalFamilyIndex?.let { preferred ->
            if (request.varyCausalFamilyByAttempt) preferred + attempt else preferred
        } ?: request.ordinal + attempt
        val normalizedFamilyIndex = Math.floorMod(combinedFamilyIndex, V61_CAUSAL_FAMILY_COUNT)
        val familyIndex = normalizedFamilyIndex % TopologyFamilyV61.entries.size
        val variant = normalizedFamilyIndex / TopologyFamilyV61.entries.size
        val payload = listOf(
            GENERATOR_IDENTITY_V61,
            request.publicSalt,
            request.ordinal,
            request.band.name,
            attempt,
            TopologyFamilyV61.entries[familyIndex].name,
            variant,
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray())
        var seed = 0L
        repeat(8) { index -> seed = (seed shl 8) or (digest[index].toLong() and 0xff) }
        return GeneratorV61Identity(
            request.ordinal,
            request.band,
            attempt,
            TopologyFamilyV61.entries[familyIndex],
            variant,
            seed,
        )
    }

    private fun realizeChallengeSpine(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
        counterexamples: List<String>,
    ): LevelDefinition {
        if (request.band == AutomatedDifficultyBandV61.EASY) {
            if (request.varyCausalFamilyByAttempt) {
                // Compact random 3x3 geometry has finite purposeful capacity and exhausts in the
                // numbered campaign. Use the deterministic graph-first causal family in a
                // distinct 6x7 envelope: its 21–22 witnessed objects occupy 50–52%, inside the
                // unchanged Easy interval. The empty bottom boundary changes production routes
                // and keeps this band structurally distinct from 6x6 campaign Medium boards.
                val planIdentity = identity.copy(band = AutomatedDifficultyBandV61.SUPER_HARD)
                return superHardGraphFirstLevel(
                    request,
                    identity,
                    superHardLogicalSynthesizer.synthesize(planIdentity),
                ).copy(width = 6, height = 7)
            }
            // Every Easy topology needs enough dynamic controllers to survive the seven-level
            // family-spacing gate. A one-magnet/one-wall proposal leaves several grammars with an
            // inert wall, so generation eventually collapses to only four viable family labels.
            // Two witnessed magnets provide the same compact occupancy without decorative filler.
            val easyShape = EngineGuidedShapeV6(width = 3, height = 3, arrows = 3, magnets = 2, walls = 0)
            engineGuidedLevel(
                request = request,
                identity = identity,
                constructionBucket = 1,
                shapeOverride = easyShape,
            )?.let { return it }
        }
        if (request.band == AutomatedDifficultyBandV61.MEDIUM) {
            if (request.varyCausalFamilyByAttempt) {
                // The bounded random spatial search is useful for canonical Medium probes, but
                // repeated campaign uniqueness exhausts some families long before 439 slots.
                // Campaign synthesis therefore freezes a deterministic logical graph first and
                // realizes it with the production-tested high-capacity constructor. Admission is
                // still against the requested Medium occupancy, inferability, difficulty,
                // uniqueness, solver, and replay gates below; this is proposal capacity only.
                val planIdentity = identity.copy(band = AutomatedDifficultyBandV61.SUPER_HARD)
                val graphFirst = superHardGraphFirstLevel(
                    request,
                    identity,
                    superHardLogicalSynthesizer.synthesize(planIdentity),
                )
                // The causal kernels occupy 70–73% of their original 5x6 envelope. Widening the
                // empty boundary column places the same witnessed objects at 58–61%, inside the
                // unchanged Medium occupancy interval; production DAG analysis below observes
                // the longer exit routes and must still certify the resulting board.
                return graphFirst.copy(width = maxOf(6, graphFirst.width))
            }
            // V6 bucket four realizes six-action state-conditioned structures. V6.1 does not
            // inherit that authored bucket: the completed production DAG is independently capped
            // by the conjunctive Medium evidence below. Three dynamic controllers plus one wall
            // keep 4x4 occupancy in range while allowing every causal family to meet the inert
            // object ceiling; this is proposal synthesis only.
            engineGuidedLevel(
                request,
                identity,
                constructionBucket = 4,
                shapeOverride = EngineGuidedShapeV6(width = 4, height = 4, arrows = 6, magnets = 3, walls = 1),
            )?.let { return it }
        }
        if (request.band == AutomatedDifficultyBandV61.HARD) {
            // The old refinement table had fewer semantic graphs than the campaign has Hard
            // slots. Freeze a deterministic logical plan first, then realize it through the same
            // independently replayed production-DAG kernels used by the high-tier capacity proof.
            // Admission below still uses the requested Hard evidence floor and every uniqueness
            // gate; graph-first construction is not a difficulty override.
            if (request.varyCausalFamilyByAttempt) {
                // The Super Hard spatial mapper collapses its large logical plan space to too few
                // exact/policy variants for all numbered Hard slots. The Expert logical mapper
                // has independent controller, cancellation, and handoff braids plus two refinement
                // axes. A 6x6 envelope places every kernel inside Hard's unchanged occupancy range;
                // the resulting production DAG must still satisfy the requested Hard gates.
                val planIdentity = identity.copy(band = AutomatedDifficultyBandV61.EXPERT)
                val graphFirst = expertGraphFirstLevel(
                    request,
                    identity,
                    expertLogicalSynthesizer.synthesize(planIdentity),
                ).copy(width = 6, height = 6)
                return applyHardCampaignCapacityEmbeddingV61(
                    graphFirst,
                    hardCampaignCapacityAttemptV61(request.playerFacingNumber, identity.attempt),
                )
            }
            if (identity.topologyFamily == TopologyFamilyV61.CANCELLATION_POLARITY_RESTORATION) {
                return hardCancellationInterlockLevel(request, identity)
            }
            if (
                identity.topologyFamily in setOf(
                    TopologyFamilyV61.WRONG_NOW_RIGHT_LATER,
                    TopologyFamilyV61.OPENING_TO_ENDGAME_DELAYED_TRAP,
                ) &&
                identity.familyVariant >= 1
            ) {
                return hardDelayedCancellationLevel(request, identity)
            }
            return hardNestedControllerLevel(request, identity)
        }
        if (request.band == AutomatedDifficultyBandV61.SUPER_HARD) {
            return superHardGraphFirstLevel(request, identity, superHardLogicalSynthesizer.synthesize(identity))
        }
        if (request.band == AutomatedDifficultyBandV61.EXPERT) {
            return expertGraphFirstLevel(request, identity, expertLogicalSynthesizer.synthesize(identity))
        }
        val shape = shape(request.band)
        val random = SeededRandom(identity.seed xor counterexamples.joinToString("|").hashCode().toLong())
        val cells = (1..shape.height).flatMap { row ->
            (1..shape.width).map { column -> Position(row, column) }
        }.toMutableList()
        for (index in cells.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = cells[index]
            cells[index] = cells[other]
            cells[other] = value
        }
        val targetOccupied = (shape.width * shape.height * shape.occupancyTarget).roundToInt()
        val magnetCount = shape.magnets
        val wallCount = (targetOccupied - shape.arrows - magnetCount).coerceAtLeast(0)
        val arrowCells = cells.take(shape.arrows)
        val magnetCells = cells.drop(shape.arrows).take(magnetCount)
        val wallCells = cells.drop(shape.arrows + magnetCount).take(wallCount)
        val arrows = arrowCells.mapIndexed { index, position ->
            Arrow(
                id = "v61-a-${index.toString().padStart(2, '0')}",
                position = position,
                printedDirection = cuedDirection(position, shape.width, shape.height, identity, index, random),
            )
        }
        val magnets = magnetCells.mapIndexed { index, position ->
            Magnet(
                id = "v61-m-${index.toString().padStart(2, '0')}",
                position = position,
                polarity = if ((index + identity.familyVariant + identity.attempt) % 2 == 0) Polarity.PULL else Polarity.PUSH,
            )
        }
        return LevelDefinition(
            id = request.levelId,
            number = request.playerFacingNumber,
            title = "${request.band.displayName} Journey ${request.playerFacingNumber}",
            width = shape.width,
            height = shape.height,
            arrows = arrows,
            magnets = magnets,
            walls = wallCells.map(::Wall),
            designedSolutions = emptyList(),
        )
    }

    /**
     * Easy still needs a real state-conditioned relationship, but it does not need decorative
     * capacity. Reuse the bounded V6 MRV/forward-checking/CEGIS realizer with V6.1's stricter
     * occupancy profile. This lets it select a compact 3x3/3x4 geometry containing only objects
     * that receive production-state witnesses instead of forcing three random walls into 4x4.
     * The returned level is only a proposal: the V6.1 pipeline above independently re-expands the
     * complete DAG, verifies the causal relation, checks all semantic archives, grades the easiest
     * policy, and replays the independent solver before it can be certified.
     */
    private fun engineGuidedLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
        constructionBucket: Int,
        shapeOverride: EngineGuidedShapeV6? = null,
    ): LevelDefinition? {
        val v6Identity = GeneratorV6Identity(
            seed = identity.seed,
            bucket = constructionBucket,
            grammarFamily = causalGrammar(identity),
            graphInstance = request.ordinal + identity.attempt,
            refinementIteration = identity.attempt % 3,
        )
        val v6Request = V6GenerationRequest(
            identity = v6Identity,
            stableId = request.levelId,
            number = request.playerFacingNumber,
            title = "${request.band.displayName} Journey ${request.playerFacingNumber}",
            packId = "generator-v6.1",
            knownFingerprints = request.knownFingerprints,
            knownFingerprintIndex = request.knownFingerprintIndex,
            analysisCache = request.analysisCache,
        )
        return engineGuidedRealizer.realize(
            request = v6Request,
            identity = v6Identity,
            profile = occupancyProfile(request.band).copy(bucket = constructionBucket),
            mutationRound = identity.attempt % 3,
            shapeOverride = shapeOverride,
            // Rotate to another causal family instead of spending the whole campaign budget on
            // one topology whose geometry is incompatible with the current uniqueness archive.
            // Keep each topology cheap enough that an exhausted family cannot monopolize an
            // ordered campaign slot. The outer 64-attempt campaign budget still rotates through
            // causal families. Medium's validated constructor succeeds within 128 proposals;
            // Easy keeps 256 because its single-attempt pacing regression identity needs the
            // wider deterministic window. Campaign Hard and higher use graph-first constructors.
            maximumBoardCandidates = if (request.band == AutomatedDifficultyBandV61.EASY) 256 else 128,
            candidateAcceptance = { level, dag, spec ->
                val assessment = CuedAdversarialDifficultyAnalyzerV61().analyze(
                    level = level,
                    decisionDag = dag,
                    causalSpec = spec,
                    semanticNoveltyPass = true,
                    purposefulOccupancyPass = true,
                )
                assessment.accepted &&
                    (assessment.maximumEligibleBand?.rank ?: 0) >= request.band.rank
            },
        ).first?.level
    }

    private fun cuedDirection(
        position: Position,
        width: Int,
        height: Int,
        identity: GeneratorV61Identity,
        index: Int,
        random: SeededRandom,
    ): Direction {
        val outward = buildList {
            if (position.row == 1) add(Direction.NORTH)
            if (position.row == height) add(Direction.SOUTH)
            if (position.column == 1) add(Direction.WEST)
            if (position.column == width) add(Direction.EAST)
        }
        if (outward.isNotEmpty() && (index + identity.familyVariant) % 3 != 0) {
            return outward[(index + identity.attempt) % outward.size]
        }
        return Direction.entries[random.nextInt(Direction.entries.size)]
    }

    /**
     * A materially expanded nested-controller spine. The three added arrows change the reachable
     * policy (rather than padding the old geometry): one creates the third non-commuting decision,
     * one extends its delayed consequence, and one breaks the forced cleanup tail. Deterministic
     * variants change the third interaction's cell and direction; complete production analysis and
     * comparison with the rejected V6 corpus remain mandatory after realization.
     */
    private fun hardNestedControllerLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
    ): LevelDefinition {
        val refinement = hardNestedRefinements[identity.attempt % hardNestedRefinements.size]
        return LevelDefinition(
            id = request.levelId,
            number = request.playerFacingNumber,
            title = "${request.band.displayName} Journey ${request.playerFacingNumber}",
            width = 5,
            height = 5,
            arrows = listOf(
                Arrow("v61-a-00", Position(2, 2), Direction.WEST),
                Arrow("v61-a-01", Position(4, 3), Direction.EAST),
                Arrow("v61-a-02", Position(3, 4), Direction.EAST),
                Arrow("v61-a-03", Position(4, 2), Direction.SOUTH),
                Arrow("v61-a-04", Position(2, 3), Direction.SOUTH),
                Arrow("v61-a-05", Position(1, 4), Direction.EAST),
                Arrow("v61-a-06", Position(4, 4), Direction.WEST),
                Arrow("v61-a-07", Position(2, 1), Direction.EAST),
                Arrow("v61-a-08", Position(3, 3), Direction.EAST),
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", refinement.secondPosition, refinement.secondDirection),
                Arrow("v61-a-11", refinement.thirdPosition, refinement.thirdDirection),
            ),
            magnets = listOf(
                Magnet("v61-m-00", Position(1, 3), Polarity.PULL),
                Magnet("v61-m-01", Position(4, 1), Polarity.PULL),
            ),
            walls = listOf(Wall(Position(1, 1))),
            designedSolutions = emptyList(),
        )
    }

    private data class HardNestedRefinementV61(
        val secondPosition: Position,
        val secondDirection: Direction,
        val thirdPosition: Position,
        val thirdDirection: Direction,
    )

    private val hardNestedRefinements = listOf(
        HardNestedRefinementV61(Position(2, 4), Direction.NORTH, Position(3, 5), Direction.EAST),
        HardNestedRefinementV61(Position(2, 4), Direction.NORTH, Position(1, 2), Direction.EAST),
        HardNestedRefinementV61(Position(1, 2), Direction.NORTH, Position(2, 4), Direction.NORTH),
        HardNestedRefinementV61(Position(1, 2), Direction.EAST, Position(2, 5), Direction.NORTH),
        HardNestedRefinementV61(Position(1, 2), Direction.SOUTH, Position(3, 5), Direction.NORTH),
        HardNestedRefinementV61(Position(1, 2), Direction.WEST, Position(4, 5), Direction.NORTH),
        HardNestedRefinementV61(Position(1, 5), Direction.WEST, Position(3, 5), Direction.EAST),
        HardNestedRefinementV61(Position(2, 4), Direction.EAST, Position(3, 5), Direction.SOUTH),
        HardNestedRefinementV61(Position(2, 5), Direction.NORTH, Position(1, 2), Direction.NORTH),
        HardNestedRefinementV61(Position(2, 5), Direction.EAST, Position(3, 5), Direction.NORTH),
        HardNestedRefinementV61(Position(2, 5), Direction.SOUTH, Position(1, 2), Direction.EAST),
        HardNestedRefinementV61(Position(3, 5), Direction.NORTH, Position(2, 4), Direction.NORTH),
        HardNestedRefinementV61(Position(3, 5), Direction.EAST, Position(1, 5), Direction.WEST),
        HardNestedRefinementV61(Position(3, 5), Direction.SOUTH, Position(4, 5), Direction.EAST),
        HardNestedRefinementV61(Position(4, 5), Direction.NORTH, Position(3, 5), Direction.EAST),
        HardNestedRefinementV61(Position(4, 5), Direction.EAST, Position(1, 2), Direction.SOUTH),
        HardNestedRefinementV61(Position(4, 5), Direction.SOUTH, Position(3, 5), Direction.SOUTH),
        HardNestedRefinementV61(Position(1, 2), Direction.NORTH, Position(5, 2), Direction.EAST),
        HardNestedRefinementV61(Position(1, 2), Direction.EAST, Position(5, 3), Direction.SOUTH),
        HardNestedRefinementV61(Position(1, 2), Direction.SOUTH, Position(5, 4), Direction.WEST),
        HardNestedRefinementV61(Position(1, 2), Direction.WEST, Position(5, 5), Direction.NORTH),
        HardNestedRefinementV61(Position(3, 5), Direction.NORTH, Position(5, 2), Direction.WEST),
        HardNestedRefinementV61(Position(3, 5), Direction.EAST, Position(5, 3), Direction.EAST),
        HardNestedRefinementV61(Position(3, 5), Direction.SOUTH, Position(5, 4), Direction.SOUTH),
    )

    /**
     * The nested Hard spine plus an isolated bottom-row cancellation interlock. Removing either
     * outer blocker exposes one nearest magnet to a12; removing both exposes two equidistant
     * magnets and restores its printed route through production cancellation. The added magnets
     * are separated from the original controller lanes by the occupied row-four/five cells, and
     * the complete DAG must still preserve the original Hard decisions before acceptance.
     */
    private fun hardCancellationInterlockLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
    ): LevelDefinition {
        val base = hardNestedControllerLevel(request, identity)
        return base.copy(
            height = 6,
            arrows = base.arrows + listOf(
                Arrow("v61-a-12", Position(6, 3), Direction.NORTH),
                Arrow("v61-a-13", Position(6, 2), Direction.WEST),
                Arrow("v61-a-14", Position(6, 4), Direction.EAST),
            ),
            magnets = base.magnets + listOf(
                Magnet("v61-m-02", Position(6, 1), Polarity.PULL),
                Magnet("v61-m-03", Position(6, 5), Polarity.PULL),
            ),
        )
    }

    /**
     * A Hard wrong-now/right-later topology whose delayed branch crosses the cancellation lane.
     * It is not a coordinate variant of the twelve-arrow controller spine: both outer blockers
     * must first expose the cancellation state, while the added successful branch changes which
     * polarity is safe when that state is reached. The 21st occupied cell is the Hard occupancy
     * ceiling on 5x6 and is accepted only when the production counterfactual analyzer witnesses it.
     */
    private fun hardDelayedCancellationLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
    ): LevelDefinition {
        val familyOffset = identity.topologyFamily.ordinal * 3 + identity.familyVariant * 5
        val baseAttempt = (7 + familyOffset + identity.attempt) % hardNestedRefinements.size
        val base = hardCancellationInterlockLevel(request, identity.copy(attempt = baseAttempt))
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        val rotation = (familyOffset + identity.attempt * 5) % hardDelayedCancellationRefinements.size
        val refinement = (
            hardDelayedCancellationRefinements.drop(rotation) +
                hardDelayedCancellationRefinements.take(rotation)
            ).first { it.position !in occupied }
        return base.copy(
            arrows = base.arrows + Arrow("v61-a-15", refinement.position, refinement.direction),
        )
    }

    private val hardDelayedCancellationRefinements = listOf(
        SuperHardNestedRefinementV61(Position(5, 5), Direction.WEST),
        SuperHardNestedRefinementV61(Position(5, 4), Direction.NORTH),
        SuperHardNestedRefinementV61(Position(5, 3), Direction.EAST),
        SuperHardNestedRefinementV61(Position(5, 2), Direction.SOUTH),
        SuperHardNestedRefinementV61(Position(5, 1), Direction.EAST),
        SuperHardNestedRefinementV61(Position(4, 5), Direction.NORTH),
        SuperHardNestedRefinementV61(Position(3, 5), Direction.WEST),
        SuperHardNestedRefinementV61(Position(2, 5), Direction.SOUTH),
        SuperHardNestedRefinementV61(Position(1, 5), Direction.WEST),
        SuperHardNestedRefinementV61(Position(1, 2), Direction.NORTH),
        SuperHardNestedRefinementV61(Position(2, 4), Direction.WEST),
    )

    /**
     * Spatial search begins only after [SuperHardLogicalGraphSynthesizerV61] has frozen a logical
     * graph. Its canonical signature chooses the interaction kernel and independent refinements;
     * the complete production DAG later reconstructs every accepted relationship from scratch.
     */
    private fun superHardGraphFirstLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
        plan: SuperHardLogicalPlanV61,
    ): LevelDefinition = when (plan.kernel) {
        SuperHardSpatialKernelV61.CONTROLLER_INTERLOCK -> {
            val base = superHardInterlockLevel(
                request,
                identity.copy(attempt = plan.primaryRefinementIndex % superHardRefinements.size),
            )
            if (plan.directionPhase % 2 == 0) base else base.copy(
                arrows = base.arrows.map { arrow ->
                    if (arrow.id == "v61-a-15") arrow.copy(printedDirection = Direction.SOUTH) else arrow
                },
            )
        }
        SuperHardSpatialKernelV61.NESTED_CANCELLATION ->
            superHardLogicalCancellationLevel(request, identity, plan, branchCount = 1)
        SuperHardSpatialKernelV61.CANCELLATION_HANDOFF ->
            superHardLogicalCancellationLevel(request, identity, plan, branchCount = 2)
    }

    private fun superHardLogicalCancellationLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
        plan: SuperHardLogicalPlanV61,
        branchCount: Int,
    ): LevelDefinition {
        require(branchCount in 1..2)
        val base = hardCancellationInterlockLevel(
            request,
            identity.copy(attempt = plan.primaryRefinementIndex % hardNestedRefinements.size),
        )
        val occupied = (
            base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }
            ).toSet()
        val additions = rotatedSuperHardBranches(plan.secondaryRefinementIndex, plan.directionPhase)
            .filterNot { it.position in occupied }
            .distinctBy { it.position }
            .take(branchCount)
        require(additions.size == branchCount)
        return base.copy(
            arrows = base.arrows + additions.mapIndexed { index, branch ->
                Arrow("v61-a-${15 + index}", branch.position, branch.direction)
            },
        )
    }

    private fun rotatedSuperHardBranches(index: Int, directionPhase: Int): List<SuperHardBranchV61> {
        val directed = superHardBranchPositions.flatMapIndexed { positionIndex, position ->
            Direction.entries.map { direction ->
                SuperHardBranchV61(
                    position,
                    Direction.entries[(direction.ordinal + directionPhase + positionIndex) % Direction.entries.size],
                )
            }
        }
        val rotation = Math.floorMod(index, directed.size)
        return directed.drop(rotation) + directed.take(rotation)
    }

    private data class SuperHardBranchV61(val position: Position, val direction: Direction)

    private val superHardBranchPositions = listOf(
        Position(1, 2), Position(1, 5), Position(2, 4), Position(2, 5), Position(3, 5),
        Position(4, 5), Position(5, 1), Position(5, 2), Position(5, 3), Position(5, 4),
        Position(5, 5), Position(6, 1), Position(6, 2), Position(6, 3), Position(6, 4), Position(6, 5),
    )

    /**
     * A nested-choice Super Hard family built from the verified cancellation interlock rather than
     * the shared 17-action controller template. The extra arrow is selected from an actually free
     * cell and must receive a reachable production witness; changing the underlying Hard
     * refinement changes the safe/trap partial order before semantic admission.
     */
    private fun superHardNestedCancellationLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
    ): LevelDefinition {
        val baseAttempt = (11 + identity.attempt) % hardNestedRefinements.size
        val base = hardCancellationInterlockLevel(request, identity.copy(attempt = baseAttempt))
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        val rotation = identity.attempt % superHardNestedRefinements.size
        val refinement = (superHardNestedRefinements.drop(rotation) + superHardNestedRefinements.take(rotation))
            .first { it.position !in occupied }
        return base.copy(
            arrows = base.arrows + Arrow("v61-a-15", refinement.position, refinement.direction),
        )
    }

    private data class SuperHardNestedRefinementV61(val position: Position, val direction: Direction)

    private val superHardNestedRefinements = listOf(
        SuperHardNestedRefinementV61(Position(1, 5), Direction.NORTH),
        SuperHardNestedRefinementV61(Position(2, 5), Direction.NORTH),
        SuperHardNestedRefinementV61(Position(4, 5), Direction.EAST),
        SuperHardNestedRefinementV61(Position(5, 1), Direction.WEST),
        SuperHardNestedRefinementV61(Position(5, 2), Direction.WEST),
        SuperHardNestedRefinementV61(Position(5, 3), Direction.SOUTH),
        SuperHardNestedRefinementV61(Position(5, 4), Direction.EAST),
        SuperHardNestedRefinementV61(Position(5, 5), Direction.SOUTH),
        SuperHardNestedRefinementV61(Position(1, 2), Direction.SOUTH),
        SuperHardNestedRefinementV61(Position(2, 4), Direction.EAST),
        SuperHardNestedRefinementV61(Position(3, 5), Direction.SOUTH),
    )

    /**
     * A three-phase interlock derived from production-state counterexamples, not from occupied-cell
     * inflation. The opening controller choice changes the middle polarity sequence; the late pair
     * at row five is revealed only after that sequence. The row-six/outer-column refinement arrow
     * is an independently movable branch which keeps the easiest policy's forced runs bounded.
     * Every arrow, both magnets, and both walls still require reachable semantic witnesses before
     * this proposal can leave [generate].
     */
    private fun superHardInterlockLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
    ): LevelDefinition {
        val refinement = superHardRefinements[identity.attempt % superHardRefinements.size]
        return LevelDefinition(
            id = request.levelId,
            number = request.playerFacingNumber,
            title = "${request.band.displayName} Journey ${request.playerFacingNumber}",
            width = 5,
            height = 6,
            arrows = listOf(
                Arrow("v61-a-00", Position(2, 2), Direction.WEST),
                Arrow("v61-a-01", Position(4, 3), Direction.EAST),
                Arrow("v61-a-02", Position(3, 4), Direction.EAST),
                Arrow("v61-a-03", Position(4, 2), Direction.SOUTH),
                Arrow("v61-a-04", Position(2, 3), Direction.SOUTH),
                Arrow("v61-a-05", Position(1, 4), Direction.EAST),
                Arrow("v61-a-06", Position(4, 4), Direction.WEST),
                Arrow("v61-a-07", Position(2, 1), Direction.EAST),
                Arrow("v61-a-08", Position(3, 3), Direction.EAST),
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
                Arrow("v61-a-14", Position(5, 2), Direction.EAST),
                Arrow("v61-a-15", Position(2, 5), Direction.EAST),
                Arrow("v61-a-16", refinement.position, refinement.direction),
            ),
            magnets = listOf(
                Magnet("v61-m-00", Position(1, 3), Polarity.PULL),
                Magnet("v61-m-01", Position(4, 1), Polarity.PULL),
            ),
            walls = listOf(Wall(Position(1, 1)), Wall(Position(1, 5))),
            designedSolutions = emptyList(),
        )
    }

    private data class SuperHardRefinementV61(val position: Position, val direction: Direction)

    private val superHardRefinements = listOf(
        SuperHardRefinementV61(Position(4, 5), Direction.EAST),
        SuperHardRefinementV61(Position(4, 5), Direction.SOUTH),
        SuperHardRefinementV61(Position(5, 4), Direction.EAST),
        SuperHardRefinementV61(Position(5, 4), Direction.SOUTH),
        SuperHardRefinementV61(Position(5, 5), Direction.EAST),
        SuperHardRefinementV61(Position(5, 5), Direction.SOUTH),
        SuperHardRefinementV61(Position(6, 1), Direction.EAST),
        SuperHardRefinementV61(Position(6, 1), Direction.SOUTH),
        SuperHardRefinementV61(Position(6, 1), Direction.WEST),
        SuperHardRefinementV61(Position(6, 2), Direction.EAST),
        SuperHardRefinementV61(Position(6, 2), Direction.SOUTH),
        SuperHardRefinementV61(Position(6, 2), Direction.WEST),
        SuperHardRefinementV61(Position(6, 3), Direction.EAST),
        SuperHardRefinementV61(Position(6, 3), Direction.SOUTH),
        SuperHardRefinementV61(Position(6, 3), Direction.WEST),
        SuperHardRefinementV61(Position(6, 4), Direction.EAST),
        SuperHardRefinementV61(Position(6, 4), Direction.SOUTH),
        SuperHardRefinementV61(Position(6, 4), Direction.WEST),
        SuperHardRefinementV61(Position(6, 5), Direction.EAST),
        SuperHardRefinementV61(Position(6, 5), Direction.SOUTH),
        SuperHardRefinementV61(Position(6, 5), Direction.WEST),
    )

    /**
     * Spatially realizes a pre-geometry Expert logical graph. The logical signature selects a
     * controller/cancellation/handoff kernel and independent refinement coordinates. The kernel is
     * only a search scaffold: every proposed action, trap, phase, controller effect, occupancy
     * witness and semantic signature is reconstructed from the complete production decision DAG
     * before acceptance. Failed graph realizations become the next deterministic CEGIS attempt.
     */
    private fun expertGraphFirstLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
        plan: ExpertLogicalPlanV61,
    ): LevelDefinition = when (plan.kernel) {
        ExpertSpatialKernelV61.CONTROLLER_BRAID -> expertLogicalControllerLevel(request, identity, plan)
        ExpertSpatialKernelV61.CANCELLATION_BRAID -> expertLogicalCancellationLevel(request, identity, plan)
        ExpertSpatialKernelV61.CONTROLLER_HANDOFF -> expertLogicalHandoffLevel(request, identity, plan)
    }

    private fun expertLogicalControllerLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
        plan: ExpertLogicalPlanV61,
    ): LevelDefinition {
        val controllerPosition = Position(6, 4)
        val baseIndex = (0 until superHardRefinements.size).first { offset ->
            val index = (plan.primaryRefinementIndex + offset) % superHardRefinements.size
            superHardRefinements[index].position != controllerPosition
        }.let { (plan.primaryRefinementIndex + it) % superHardRefinements.size }
        val base = superHardInterlockLevel(request, identity.copy(attempt = baseIndex))
        val occupied = (
            base.arrows.map { it.position } + base.magnets.map { it.position } +
                base.walls.map { it.position } + controllerPosition
            ).toSet()
        val branch = rotatedExpertBranches(plan.secondaryRefinementIndex, plan.directionPhase)
            .first { it.position !in occupied }
        return base.copy(
            arrows = base.arrows.map { arrow ->
                if (arrow.id == "v61-a-15") arrow.copy(printedDirection = Direction.SOUTH) else arrow
            } + Arrow("v61-a-17", branch.position, branch.direction),
            magnets = base.magnets + Magnet("v61-m-02", controllerPosition, Polarity.PUSH),
        )
    }

    private fun expertLogicalCancellationLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
        plan: ExpertLogicalPlanV61,
    ): LevelDefinition {
        val fixedPositions = setOf(Position(1, 5), Position(2, 5), Position(5, 5))
        val baseIndex = (0 until hardNestedRefinements.size).first { offset ->
            val index = (plan.primaryRefinementIndex + offset) % hardNestedRefinements.size
            val refinement = hardNestedRefinements[index]
            refinement.secondPosition !in fixedPositions && refinement.thirdPosition !in fixedPositions
        }.let { (plan.primaryRefinementIndex + it) % hardNestedRefinements.size }
        val base = hardCancellationInterlockLevel(request, identity.copy(attempt = baseIndex))
        val occupied = (
            base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position } +
                fixedPositions
            ).toSet()
        val branch = rotatedExpertBranches(plan.secondaryRefinementIndex, plan.directionPhase)
            .first { it.position !in occupied }
        return base.copy(
            arrows = base.arrows + listOf(
                Arrow("v61-a-15", Position(1, 5), Direction.NORTH),
                Arrow("v61-a-16", Position(2, 5), Direction.NORTH),
                Arrow("v61-a-17", branch.position, branch.direction),
            ),
            magnets = base.magnets + Magnet("v61-m-04", Position(5, 5), Polarity.PULL),
        )
    }

    private fun expertLogicalHandoffLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
        plan: ExpertLogicalPlanV61,
    ): LevelDefinition {
        val controllerPosition = Position(5, 5)
        val baseIndex = (0 until hardNestedRefinements.size).first { offset ->
            val index = (plan.primaryRefinementIndex + offset) % hardNestedRefinements.size
            val refinement = hardNestedRefinements[index]
            refinement.secondPosition != controllerPosition && refinement.thirdPosition != controllerPosition
        }.let { (plan.primaryRefinementIndex + it) % hardNestedRefinements.size }
        val base = hardCancellationInterlockLevel(request, identity.copy(attempt = baseIndex))
        val occupied = (
            base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position } +
                controllerPosition
            ).toSet()
        val additions = rotatedExpertBranches(plan.secondaryRefinementIndex, plan.directionPhase)
            .filterNot { it.position in occupied }
            .distinctBy { it.position }
            .take(3)
        require(additions.size == 3)
        return base.copy(
            arrows = base.arrows + additions.mapIndexed { index, branch ->
                Arrow("v61-a-${15 + index}", branch.position, branch.direction)
            },
            magnets = base.magnets + Magnet("v61-m-04", controllerPosition, Polarity.PULL),
        )
    }

    private fun rotatedExpertBranches(index: Int, directionPhase: Int): List<ExpertBranchV61> {
        val directed = expertBranchPositions.flatMapIndexed { positionIndex, position ->
            Direction.entries.map { direction ->
                ExpertBranchV61(
                    position,
                    Direction.entries[(direction.ordinal + directionPhase + positionIndex) % Direction.entries.size],
                )
            }
        }
        val rotation = Math.floorMod(index, directed.size)
        return directed.drop(rotation) + directed.take(rotation)
    }

    private data class ExpertBranchV61(val position: Position, val direction: Direction)

    private val expertBranchPositions = listOf(
        Position(1, 2), Position(1, 5), Position(2, 4), Position(2, 5), Position(3, 5),
        Position(4, 5), Position(5, 1), Position(5, 2), Position(5, 3), Position(5, 4),
        Position(5, 5), Position(6, 1), Position(6, 2), Position(6, 3), Position(6, 4), Position(6, 5),
    )

    /**
     * Expert adds a visible late push controller to the Super Hard interlock. That controller
     * makes two previously safe timings non-commuting; turning a15 south keeps the consequence
     * trace visible and breaks the remaining forced run. a17 is a reachable alternative in the
     * final phase, not filler: removing it at the wrong controller parity changes solvability.
     */
    private fun expertControllerBraidLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
    ): LevelDefinition {
        val branchIdentity = identity.copy(attempt = identity.attempt % 2)
        val base = superHardInterlockLevel(request, branchIdentity)
        return base.copy(
            arrows = base.arrows.map { arrow ->
                if (arrow.id == "v61-a-15") arrow.copy(printedDirection = Direction.SOUTH) else arrow
            } + Arrow(
                "v61-a-17",
                Position(5, 5),
                if ((identity.attempt / 2) % 2 == 0) Direction.EAST else Direction.SOUTH,
            ),
            magnets = base.magnets + Magnet("v61-m-02", Position(6, 4), Polarity.PUSH),
        )
    }

    /** A separate Expert policy topology built around the verified cancellation interlock. */
    private fun expertCancellationBraidLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
    ): LevelDefinition {
        val base = hardCancellationInterlockLevel(request, identity.copy(attempt = 0))
        val refinement = expertCancellationRefinements[identity.attempt % expertCancellationRefinements.size]
        return base.copy(
            arrows = base.arrows + listOf(
                Arrow("v61-a-15", Position(1, 5), Direction.NORTH),
                Arrow("v61-a-16", Position(2, 5), Direction.NORTH),
                Arrow("v61-a-17", refinement.position, refinement.direction),
            ),
            magnets = base.magnets + Magnet("v61-m-04", Position(5, 5), Polarity.PULL),
        )
    }

    /**
     * A separate controller-handoff topology on the bounded 5x6 cancellation board. Unlike the
     * occlusion braid, its underlying Hard core changes with the family variant and CEGIS attempt;
     * that changes reachable controller transitions and the decision policy, not just coordinates.
     */
    private fun expertExpandedCrossHandoffLevel(
        request: V61GenerationRequest,
        identity: GeneratorV61Identity,
    ): LevelDefinition {
        val baseAttempt = (0 until hardNestedRefinements.size).firstNotNullOf { offset ->
            val index = (17 + identity.attempt + offset) % hardNestedRefinements.size
            hardNestedRefinements[index].takeUnless { refinement ->
                refinement.secondPosition == Position(5, 5) || refinement.thirdPosition == Position(5, 5)
            }?.let { index }
        }
        val base = hardCancellationInterlockLevel(request, identity.copy(attempt = baseAttempt))
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        val rotated = expertCrossHandoffRefinements.drop(identity.attempt % expertCrossHandoffRefinements.size) +
            expertCrossHandoffRefinements.take(identity.attempt % expertCrossHandoffRefinements.size)
        val additions = rotated.filterNot { it.position in occupied }.distinctBy { it.position }.take(3)
        require(additions.size == 3)
        return base.copy(
            arrows = base.arrows + additions.mapIndexed { index, addition ->
                Arrow("v61-a-${15 + index}", addition.position, addition.direction)
            },
            magnets = base.magnets + Magnet("v61-m-04", Position(5, 5), Polarity.PULL),
        )
    }

    private data class ExpertCrossHandoffRefinementV61(val position: Position, val direction: Direction)

    private val expertCrossHandoffRefinements = listOf(
        ExpertCrossHandoffRefinementV61(Position(1, 5), Direction.NORTH),
        ExpertCrossHandoffRefinementV61(Position(2, 5), Direction.NORTH),
        ExpertCrossHandoffRefinementV61(Position(4, 5), Direction.EAST),
        ExpertCrossHandoffRefinementV61(Position(5, 1), Direction.WEST),
        ExpertCrossHandoffRefinementV61(Position(5, 2), Direction.WEST),
        ExpertCrossHandoffRefinementV61(Position(5, 3), Direction.SOUTH),
        ExpertCrossHandoffRefinementV61(Position(5, 4), Direction.EAST),
        ExpertCrossHandoffRefinementV61(Position(1, 2), Direction.SOUTH),
    )

    private data class ExpertCancellationRefinementV61(val position: Position, val direction: Direction)

    private val expertCancellationRefinements = buildList {
        Direction.entries.forEach { direction -> add(ExpertCancellationRefinementV61(Position(1, 2), direction)) }
        Direction.entries.forEach { direction -> add(ExpertCancellationRefinementV61(Position(4, 5), direction)) }
        add(ExpertCancellationRefinementV61(Position(5, 1), Direction.WEST))
        (2..4).forEach { column ->
            Direction.entries.forEach { direction ->
                add(ExpertCancellationRefinementV61(Position(5, column), direction))
            }
        }
    }

    private fun occupancyProfile(band: AutomatedDifficultyBandV61): V6Profile {
        val occupancy = when (band) {
            AutomatedDifficultyBandV61.EASY -> 0.45..0.60
            AutomatedDifficultyBandV61.MEDIUM -> 0.50..0.65
            AutomatedDifficultyBandV61.HARD -> 0.55..0.70
            AutomatedDifficultyBandV61.SUPER_HARD -> 0.60..0.75
            AutomatedDifficultyBandV61.EXPERT, AutomatedDifficultyBandV61.MASTER -> 0.65..0.80
        }
        return V6Profile(
            bucket = band.rank.coerceAtMost(5),
            occupancy = occupancy,
            minimumPurposefulOccupiedRatio = if (band.rank >= 4) 0.90 else 0.85,
            meaningfulDecisionRange = 0..100,
            minimumPersistentTraps = 0,
            minimumLookahead = 0,
            maximumHardestWinningShare = null,
            minimumInteractingChains = when (band) {
                AutomatedDifficultyBandV61.MASTER -> 4
                AutomatedDifficultyBandV61.SUPER_HARD, AutomatedDifficultyBandV61.EXPERT -> 3
                AutomatedDifficultyBandV61.HARD -> 2
                else -> 1
            },
            budgets = V6SearchBudgets(
                decisionDagStates = 75_000,
                decisionDagResolutions = 750_000,
                counterfactualChecks = 250_000,
                canonicalBacktrackingStates = 100_000,
            ),
        )
    }

    private fun causalGrammar(identity: GeneratorV61Identity): CausalGrammarFamilyV6 = when (identity.topologyFamily) {
        TopologyFamilyV61.SHARED_MAGNET_PARITY_BRAID -> CausalGrammarFamilyV6.POLARITY_LOCK_RELEASE
        TopologyFamilyV61.NESTED_NON_COMMUTING_CHOICES -> CausalGrammarFamilyV6.FORK_JOIN_COUPLED
        TopologyFamilyV61.WRONG_NOW_RIGHT_LATER -> CausalGrammarFamilyV6.INTERACTING_CHAINS_DELAYED_TRAPS
        TopologyFamilyV61.CROSS_CHAIN_CONTROLLER_HANDOFF -> CausalGrammarFamilyV6.COMPETING_CONTROLLER_HANDOFF
        TopologyFamilyV61.OCCLUSION_REVEAL_FORK_JOIN -> CausalGrammarFamilyV6.OCCLUSION_REVEAL_CHAIN
        TopologyFamilyV61.CANCELLATION_POLARITY_RESTORATION -> CausalGrammarFamilyV6.CANCELLATION_RELEASE
        TopologyFamilyV61.TWO_CHAIN_INTERLOCK -> CausalGrammarFamilyV6.FORK_JOIN_COUPLED
        TopologyFamilyV61.OPENING_TO_ENDGAME_DELAYED_TRAP -> CausalGrammarFamilyV6.INTERACTING_CHAINS_DELAYED_TRAPS
    }

    private fun duplicateReason(
        candidate: V6FingerprintBundle,
        known: List<V6FingerprintBundle>,
    ): String? = known.firstNotNullOfOrNull { existing ->
        when {
            existing.exactLayout == candidate.exactLayout -> "REJECT_EXACT_DUPLICATE"
            existing.d4Layout == candidate.d4Layout -> "REJECT_D4_DUPLICATE"
            existing.arrowLayout == candidate.arrowLayout -> "REJECT_ARROW_LAYOUT_DUPLICATE"
            existing.interactiveLayout == candidate.interactiveLayout -> "REJECT_INTERACTIVE_LAYOUT_DUPLICATE"
            existing.perceptualLayout == candidate.perceptualLayout -> "REJECT_PERCEPTUAL_LAYOUT_DUPLICATE"
            existing.relevancePrunedD4Layout == candidate.relevancePrunedD4Layout -> "REJECT_RELEVANCE_DUPLICATE"
            existing.causalHypergraph == candidate.causalHypergraph -> "REJECT_CAUSAL_DUPLICATE"
            existing.quotientDecisionDag == candidate.quotientDecisionDag -> "REJECT_DECISION_DAG_DUPLICATE"
            existing.solutionPolicy == candidate.solutionPolicy -> "REJECT_SOLUTION_POLICY_DUPLICATE"
            !candidate.synthesisGraphIdentifier.startsWith("unavailable:") &&
                existing.synthesisGraphIdentifier == candidate.synthesisGraphIdentifier ->
                "REJECT_SYNTHESIS_GRAPH_DUPLICATE"
            (candidate.nearestSemanticSimilarity ?: 0.0) > 0.92 -> "REJECT_NEAR_SEMANTIC_DUPLICATE"
            else -> null
        }
    }

    private fun failedMovesImmutable(dag: DecisionDagAnalysisV6): Boolean = dag.nodes.values.all { node ->
        node.transitions.filter { !it.successful }.all { edge ->
            val result = engine.resolve(node.state, PlayerAction(edge.arrowId))
            !result.success && exactStateKeyV6(result.originalState) == exactStateKeyV6(result.resultingState)
        }
    }

    private fun replaySolutionV61(level: LevelDefinition, actionIds: List<String>): Boolean {
        var replay = level.initialState()
        for (arrowId in actionIds) {
            val resolution = engine.resolve(replay, PlayerAction(arrowId))
            if (!resolution.success) return false
            replay = resolution.resultingState
        }
        return replay.arrows.isEmpty()
    }

    private fun shape(band: AutomatedDifficultyBandV61): ShapeV61 = when (band) {
        AutomatedDifficultyBandV61.EASY -> ShapeV61(4, 4, 3, 2, 0.52)
        // Six actions plus two participating magnets exactly meet the 50% presentation floor on
        // 4x4. A larger 5x5 realization would need six filler walls before causal search begins.
        AutomatedDifficultyBandV61.MEDIUM -> ShapeV61(4, 4, 6, 2, 0.50)
        AutomatedDifficultyBandV61.HARD -> ShapeV61(6, 6, 10, 4, 0.63)
        AutomatedDifficultyBandV61.SUPER_HARD -> ShapeV61(7, 7, 14, 5, 0.68)
        AutomatedDifficultyBandV61.EXPERT -> ShapeV61(8, 8, 18, 6, 0.72)
        AutomatedDifficultyBandV61.MASTER -> ShapeV61(8, 8, 22, 7, 0.72)
    }

    private data class ShapeV61(
        val width: Int,
        val height: Int,
        val arrows: Int,
        val magnets: Int,
        val occupancyTarget: Double,
    )

}

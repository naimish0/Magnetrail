package com.rameshta.magnetrail.core.difficulty.v4

import com.rameshta.magnetrail.core.difficulty.DifficultyV3Scorer
import com.rameshta.magnetrail.core.difficulty.PuzzleSearchAnalyzer
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyV4AnalyzerTest {
    @Test
    fun `safe actions are classified separately from meaningful failures`() {
        val result = DifficultyV4Analyzer(AllSuccessfulEngine()).analyze(simpleLevel("safe", "A", "B", "C"))

        assertEquals(0, result.metrics.futureDeadEndChoiceCount)
        assertEquals(1.0, result.metrics.safeChoiceRatio, 0.0)
        assertEquals(0.0, result.metrics.meaningfulFailureRate, 0.0)
    }

    @Test
    fun `harmful successful action is detected`() {
        val result = DifficultyV4Analyzer().analyze(polarityOrderingLevel())

        assertTrue(result.metrics.futureDeadEndChoiceCount > 0)
        assertTrue(result.metrics.harmfulDecisionCount > 0)
        assertTrue(result.metrics.meaningfulFailureRate > 0.0)
    }

    @Test
    fun `consequence persistence reports proven dead end depth`() {
        val persistence = DifficultyV4Analyzer().analyze(polarityOrderingLevel()).metrics.consequencePersistence

        assertTrue(persistence.sampleCount > 0)
        assertNotNull(persistence.minimumDepth)
        assertTrue(requireNotNull(persistence.maximumDepth) >= requireNotNull(persistence.minimumDepth))
    }

    @Test
    fun `mandatory ordering distinguishes one valid order`() {
        val ordering = DifficultyV4Analyzer().analyze(polarityOrderingLevel()).metrics.ordering

        assertEquals(1, ordering.mandatoryOrderingPairCount)
        assertEquals(1.0, ordering.mandatoryOrderingRatio ?: -1.0, 0.0)
        assertEquals(2, ordering.mandatoryOrderingChainDepth)
    }

    @Test
    fun `polarity impact requires changed future actionability`() {
        val polarity = DifficultyV4Analyzer().analyze(polarityOrderingLevel()).metrics.polarity

        assertTrue(polarity.polarityFlipCount > 0)
        assertTrue(polarity.strategicallyImpactfulPolarityFlipCount > 0)
        assertTrue(polarity.actionabilityChangeCount > 0)
    }

    @Test
    fun `commuting actions are detected`() {
        val strategy = DifficultyV4Analyzer(AllSuccessfulEngine())
            .analyze(simpleLevel("commuting", "A", "B", "C")).metrics.strategy

        assertTrue(strategy.commutativeActionPairCount > 0)
        assertEquals(0, strategy.nonCommutingActionPairCount)
        assertEquals(1.0, strategy.commutationRatio, 0.0)
    }

    @Test
    fun `canonical strategy quotient collapses permutations`() {
        val strategy = DifficultyV4Analyzer(AllSuccessfulEngine())
            .analyze(simpleLevel("quotient", "A", "B", "C")).metrics.strategy

        assertEquals(6L, strategy.rawWinningSequenceCount)
        assertEquals(1, strategy.canonicalStrategyCount)
        assertTrue(requireNotNull(strategy.permutationRedundancy) > 0.8)
    }

    @Test
    fun `stable greedy policy records failure`() {
        val greedy = DifficultyV4Analyzer().analyze(polarityOrderingLevel()).metrics.greedyPolicy

        assertFalse(greedy.solved)
        assertEquals(0, greedy.firstDivergenceDepth)
        assertTrue(greedy.recoveryRequired)
    }

    @Test
    fun `random policy is deterministic`() {
        val analyzer = DifficultyV4Analyzer()

        val first = analyzer.analyze(polarityOrderingLevel()).metrics.randomPolicy
        val second = analyzer.analyze(polarityOrderingLevel()).metrics.randomPolicy

        assertEquals(first, second)
        assertEquals(256, first.seedCount)
    }

    @Test
    fun `bad successful choices create recovery pressure`() {
        val recovery = DifficultyV4Analyzer().analyze(polarityOrderingLevel()).metrics.recovery

        assertTrue(recovery.recoverableBadDecisionCount > 0)
        assertTrue(recovery.maximumRecoveryDepth > 0)
        assertTrue(recovery.normalizedRecoveryPressure > 0.0)
    }

    @Test
    fun `forced sequence is not treated as repeated decisions`() {
        val forced = DifficultyV4Analyzer(OnlyLexicographicFirstEngine())
            .analyze(simpleLevel("forced", "A", "B", "C")).metrics.forcedDecision

        assertEquals(3, forced.totalSolutionLength)
        assertEquals(3, forced.forcedSequenceLength)
        assertEquals(3, forced.longestForcedRun)
        assertEquals(0, forced.meaningfulDecisionCount)
    }

    @Test
    fun `wall counterfactual marks strategic relevance`() {
        val objects = DifficultyV4Analyzer().analyze(polarityOrderingLevel()).metrics.objectRelevance

        assertEquals(1, objects.totalWallCount)
        assertEquals(1, objects.relevantWallCount)
        assertEquals(0, objects.irrelevantWallCount)
    }

    @Test
    fun `magnet counterfactual marks strategic relevance`() {
        val objects = DifficultyV4Analyzer().analyze(polarityOrderingLevel()).metrics.objectRelevance

        assertEquals(1, objects.totalMagnetCount)
        assertEquals(1, objects.relevantMagnetCount)
        assertEquals(0, objects.irrelevantMagnetCount)
    }

    @Test
    fun `score penalizes fully safe permutation puzzle`() {
        val safe = DifficultyV4Analyzer(AllSuccessfulEngine()).analyze(simpleLevel("safe-score", "A", "B", "C"))
        val consequential = DifficultyV4Analyzer().analyze(polarityOrderingLevel())

        assertNotNull(safe.score)
        assertNotNull(consequential.score)
        assertTrue(requireNotNull(consequential.score) > requireNotNull(safe.score))
        assertTrue(safe.contributions.negative["safeChoiceRatio"]!! > 0.0)
    }

    @Test
    fun `truncation is visible and prevents a score`() {
        val config = DifficultyV4Config(maxExpandedStates = 1, maxCounterfactualStates = 1)
        val result = DifficultyV4Analyzer(AllSuccessfulEngine(), config)
            .analyze(simpleLevel("truncated", "A", "B"))

        assertTrue(result.searchTruncated)
        assertFalse(result.searchComplete)
        assertNull(result.score)
        assertTrue(result.truncationReasons.any { it.contains("EXPANDED_STATE_CAP") })
    }

    @Test
    fun `complete repeated analysis is structurally identical`() {
        val analyzer = DifficultyV4Analyzer()
        val level = polarityOrderingLevel()

        assertEquals(analyzer.analyze(level), analyzer.analyze(level))
    }

    @Test
    fun `human calibration schema supports parsing multiple raters`() {
        val dataset = DifficultyV4HumanCalibrationDataset(
            groups = mapOf("referenceStrong" to listOf(1)),
            raters = listOf(DifficultyV4HumanRater("owner"), DifficultyV4HumanRater("designer")),
            levels = listOf(
                DifficultyV4HumanLevelRating(
                    levelId = "level-1",
                    levelNumber = 1,
                    group = "referenceStrong",
                    ratings = listOf(
                        DifficultyV4HumanRating("owner", 3, firstMoveObvious = true),
                        DifficultyV4HumanRating("designer", 4, neededUndo = false),
                    ),
                ),
            ),
        )
        val json = Json { encodeDefaults = true; explicitNulls = true }
        val encoded = json.encodeToString(dataset)
        val decoded = json.decodeFromString<DifficultyV4HumanCalibrationDataset>(encoded)

        assertEquals(dataset, decoded)
    }

    @Test
    fun `V3 V4 calibration comparison reports directional alignment`() {
        val dataset = DifficultyV4HumanCalibrationDataset(
            groups = mapOf("control" to listOf(1, 2, 3)),
            levels = (1..3).map { number ->
                DifficultyV4HumanLevelRating(
                    "level-$number",
                    number,
                    "control",
                    listOf(DifficultyV4HumanRating("owner", number * 2)),
                )
            },
        )
        val scores = listOf(
            DifficultyV4CalibrationScoreRow("level-1", 1, 90, 15, 1.0),
            DifficultyV4CalibrationScoreRow("level-2", 2, 50, 45, 1.0),
            DifficultyV4CalibrationScoreRow("level-3", 3, 10, 75, 1.0),
        )

        val report = DifficultyV4Calibrator.compare(dataset, scores)

        assertEquals(-1.0, report.v3.spearman ?: 0.0, 0.0)
        assertEquals(1.0, report.v4.spearman ?: 0.0, 0.0)
        assertEquals("V4_APPEARS_BETTER_ALIGNED", report.alignmentConclusion)
    }

    @Test
    fun `calibration correlation is unavailable for zero variance human ratings`() {
        val dataset = DifficultyV4HumanCalibrationDataset(
            groups = mapOf("control" to listOf(1, 2, 3)),
            levels = (1..3).map { number ->
                DifficultyV4HumanLevelRating(
                    "level-$number",
                    number,
                    "control",
                    listOf(DifficultyV4HumanRating("owner", 2)),
                )
            },
        )
        val scores = (1..3).map { number ->
            DifficultyV4CalibrationScoreRow("level-$number", number, number * 20, number * 10, 1.0)
        }

        val report = DifficultyV4Calibrator.compare(dataset, scores)

        assertNull(report.v3.pearson)
        assertNull(report.v3.spearman)
        assertNull(report.v4.pearson)
        assertNull(report.v4.spearman)
        assertEquals("EVIDENCE_INCONCLUSIVE", report.alignmentConclusion)
    }

    @Test
    fun `human ratings never transfer across a stable ID board revision`() {
        val dataset = DifficultyV4HumanCalibrationDataset(
            groups = mapOf("control" to listOf(1)),
            levels = listOf(
                DifficultyV4HumanLevelRating(
                    levelId = "stable-level",
                    levelNumber = 1,
                    group = "control",
                    ratings = listOf(DifficultyV4HumanRating("owner", 7)),
                    boardFingerprint = "sha256:${"1".repeat(64)}",
                ),
            ),
        )
        val scores = listOf(
            DifficultyV4CalibrationScoreRow(
                levelId = "stable-level",
                levelNumber = 1,
                v3Score = 70,
                v4Score = 80,
                v4Confidence = 1.0,
                boardFingerprint = "sha256:${"2".repeat(64)}",
            ),
        )

        val report = DifficultyV4Calibrator.compare(dataset, scores)

        assertEquals(0, report.ratedObservationCount)
        assertEquals(1, report.excludedStaleBoardRatingCount)
        assertEquals("EVIDENCE_INCONCLUSIVE_AWAITING_HUMAN_CALIBRATION", report.alignmentConclusion)
        assertTrue("STALE BOARD RATINGS EXCLUDED" in report.warnings)
    }

    @Test
    fun `V3 and V4 can be computed independently for the same level`() {
        val level = polarityOrderingLevel()

        val v3 = DifficultyV3Scorer.score(PuzzleSearchAnalyzer().analyze(level))
        val v4 = DifficultyV4Analyzer().analyze(level)

        assertTrue(v3.score in 0..100)
        assertTrue(requireNotNull(v4.score) in 0..100)
        assertTrue(v3.analyzerVersion != v4.analyzerVersion)
    }

    private fun simpleLevel(id: String, vararg ids: String): LevelDefinition = LevelDefinition(
        id = id,
        number = 1,
        title = id,
        width = ids.size.coerceAtLeast(1),
        height = 1,
        arrows = ids.mapIndexed { index, arrowId ->
            Arrow(arrowId, Position(1, index + 1), Direction.NORTH)
        },
        magnets = emptyList(),
        walls = emptyList(),
        designedSolutions = emptyList(),
    )

    private fun polarityOrderingLevel(): LevelDefinition = LevelDefinition(
        id = "polarity-ordering",
        number = 1,
        title = "Polarity ordering",
        width = 4,
        height = 1,
        arrows = listOf(
            Arrow("A", Position(1, 1), Direction.EAST),
            Arrow("B", Position(1, 3), Direction.EAST),
        ),
        magnets = listOf(Magnet("M", Position(1, 2), Polarity.PULL)),
        walls = listOf(Wall(Position(1, 4))),
        designedSolutions = listOf(listOf("B", "A")),
    )

    private class AllSuccessfulEngine : SyntheticEngine() {
        override fun succeeds(state: BoardState, action: PlayerAction): Boolean = true
    }

    private class OnlyLexicographicFirstEngine : SyntheticEngine() {
        override fun succeeds(state: BoardState, action: PlayerAction): Boolean =
            action.arrowId == state.arrows.minOf { it.id }
    }

    private abstract class SyntheticEngine : GameEngine {
        abstract fun succeeds(state: BoardState, action: PlayerAction): Boolean

        override fun resolve(state: BoardState, action: PlayerAction): ResolutionResult {
            val arrow = requireNotNull(state.arrow(action.arrowId))
            val success = succeeds(state, action)
            val resulting = if (success) state.copy(arrows = state.arrows.filterNot { it.id == arrow.id }) else state
            return ResolutionResult(
                success = success,
                originalState = state,
                resultingState = resulting,
                selectedArrowId = arrow.id,
                printedDirection = arrow.printedDirection,
                effectiveDirection = arrow.printedDirection,
                controllingMagnetId = null,
                traversedCells = listOf(arrow.position),
                terminalEvent = TerminalEvent.Exit(arrow.position, arrow.printedDirection),
                collisionTarget = null,
                polarityChange = null,
                isWin = resulting.arrows.isEmpty(),
                isDeadlocked = resulting.arrows.isNotEmpty() && validActions(resulting).isEmpty(),
            )
        }

        override fun validActions(state: BoardState): List<PlayerAction> = state.arrows
            .map { PlayerAction(it.id) }
            .filter { succeeds(state, it) }

        override fun isDeadlocked(state: BoardState): Boolean =
            state.arrows.isNotEmpty() && validActions(state).isEmpty()
    }
}

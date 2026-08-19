package com.rameshta.magnetrail.core.difficulty

import com.rameshta.magnetrail.core.arrow
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.engine.ResolutionResult
import com.rameshta.magnetrail.core.engine.TerminalEvent
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleDifficultyV3Test {
    private val catalog by lazy {
        LevelParser().parseCatalog(checkNotNull(javaClass.getResource("/development/PHASE0_SOURCE_CONTENT_V4.json")).readText())
    }

    @Test
    fun `search graph analysis is deterministic and replays a minimum solution`() {
        val level = catalog.level("campaign-030")
        val first = PuzzleSearchAnalyzer().analyze(level)
        val second = PuzzleSearchAnalyzer().analyze(level)

        assertEquals(first, second)
        assertTrue(first.solvable)
        assertTrue(first.searchComplete)
        assertEquals(first.minimumSolutionLength, first.canonicalSolutionArrowIds.size)
        assertTrue(first.shortestSolutionCount > 0)
        assertTrue(first.solutionFamilyCount > 0)
        assertEquals(1.0, first.confidence, 0.0)
    }

    @Test
    fun `player choices distinguish invalid viable fair and guess dependent outcomes`() {
        val fair = PuzzleSearchAnalyzer().analyze(catalog.level("proto-006"))
        val guessTrap = LevelDefinition(
            id = "guess-trap-fixture",
            number = 1,
            title = "Guess trap",
            width = 3,
            height = 3,
            arrows = listOf(
                arrow("A", 1, 1, Direction.NORTH),
                arrow("B", 1, 2, Direction.NORTH),
                arrow("C", 1, 3, Direction.NORTH),
            ),
            magnets = emptyList(),
            walls = emptyList(),
            designedSolutions = listOf(listOf("B", "C", "A")),
        )
        val guess = PuzzleSearchAnalyzer(engine = GuessTrapEngine()).analyze(guessTrap)

        assertTrue(fair.canonicalChoiceMetrics.strategicallyViableChoices > 0)
        assertTrue(fair.canonicalChoiceMetrics.deceptiveButFairChoices > 0)
        assertEquals(0, fair.canonicalChoiceMetrics.guessDependentChoices)
        assertTrue(guess.canonicalChoiceMetrics.guessDependentChoices > 0)
        assertTrue(guess.canonicalChoiceMetrics.immediatelyInvalidChoices >= 0)
    }

    private class GuessTrapEngine : GameEngine {
        override fun resolve(state: BoardState, action: PlayerAction): ResolutionResult {
            val remaining = state.arrows.map { it.id }.toSet()
            val success = when (remaining) {
                setOf("A", "B", "C") -> action.arrowId in setOf("A", "B")
                setOf("B", "C") -> action.arrowId in setOf("B", "C")
                setOf("A", "C") -> action.arrowId in setOf("A", "C")
                setOf("A") -> action.arrowId == "A"
                else -> false
            }
            val resulting = if (success) {
                state.copy(arrows = state.arrows.filterNot { it.id == action.arrowId })
            } else {
                state
            }
            val arrow = requireNotNull(state.arrow(action.arrowId))
            return ResolutionResult(
                success = success,
                originalState = state,
                resultingState = resulting,
                selectedArrowId = action.arrowId,
                printedDirection = arrow.printedDirection,
                effectiveDirection = arrow.printedDirection,
                controllingMagnetId = null,
                traversedCells = emptyList(),
                terminalEvent = TerminalEvent.Exit(arrow.position, arrow.printedDirection),
                collisionTarget = null,
                polarityChange = null,
                isWin = resulting.arrows.isEmpty(),
                isDeadlocked = !success,
            )
        }

        override fun validActions(state: BoardState): List<PlayerAction> = state.arrows.mapNotNull { arrow ->
            PlayerAction(arrow.id).takeIf { resolve(state, it).success }
        }

        override fun isDeadlocked(state: BoardState): Boolean = state.arrows.isNotEmpty() && validActions(state).isEmpty()
    }

    @Test
    fun `solution length is separate from decision nodes and forced runs`() {
        val metrics = PuzzleSearchAnalyzer().analyze(catalog.level("campaign-100"))

        assertEquals(3, metrics.minimumSolutionLength)
        assertEquals(1, metrics.meaningfulDecisionPoints)
        assertEquals(2, metrics.maximumForcedRunLength)
        assertTrue(metrics.averageDecisionSpacing > 0.0)
        assertTrue(metrics.forcedSequenceLength > metrics.meaningfulDecisionPoints)
    }

    @Test
    fun `bounded solution families and dependency chains are reported independently`() {
        val analyzer = PuzzleSearchAnalyzer()
        val multiple = analyzer.analyze(catalog.level("campaign-030"))
        val dependent = analyzer.analyze(catalog.level("campaign-104"))

        assertEquals(60, multiple.shortestSolutionCount)
        assertEquals(30, multiple.solutionFamilyCount)
        assertEquals(3, dependent.dependencyDepth)
        assertTrue(dependent.deadEndActionCount > 0)
        assertTrue(dependent.averageDeadEndProofDepth > 0.0)
    }

    @Test
    fun `decision rich sparse puzzle outranks a busier mostly forced puzzle`() {
        val analyzer = PuzzleSearchAnalyzer()
        val sparseDecisionRich = DifficultyV3Scorer.score(analyzer.analyze(catalog.level("campaign-030")))
        val busierForced = DifficultyV3Scorer.score(analyzer.analyze(catalog.level("campaign-100")))

        assertTrue(
            sparseDecisionRich.rawMetrics.purposefulSpace.rawOccupancyRatio <
                busierForced.rawMetrics.purposefulSpace.rawOccupancyRatio,
        )
        assertTrue(sparseDecisionRich.rawMetrics.meaningfulDecisionPoints > busierForced.rawMetrics.meaningfulDecisionPoints)
        assertTrue(sparseDecisionRich.score > busierForced.score)
    }

    @Test
    fun `nominal hard score cannot bypass trivial structural floors`() {
        val measured = DifficultyV3Scorer.score(PuzzleSearchAnalyzer().analyze(catalog.level("campaign-100")))
        val nominalSeventy = measured.copy(score = 70, band = DifficultyBandV2.HARD)
        val hardTarget = PuzzleDifficultyTarget(
            id = "hard-fixture",
            minimumScore = 61,
            maximumScore = 75,
            minSolutionLength = 4,
            minMeaningfulDecisions = 2,
            minDependencyDepth = 2,
            minEffectiveBranching = 1.3,
            minNonForcedPortion = 0.35,
            minMechanicRelevance = 0.4,
        )
        val gate = DifficultyV3Gate.evaluate(nominalSeventy, hardTarget)

        assertFalse(gate.accepted)
        assertTrue(DifficultyGateReason.SOLUTION_TOO_SHORT in gate.reasonCodes)
        assertTrue(DifficultyGateReason.INSUFFICIENT_DECISIONS in gate.reasonCodes)
    }

    @Test
    fun `search caps and unsolvable boards remain explicit`() {
        val cappedMetrics = PuzzleSearchAnalyzer(
            config = PuzzleSearchConfig(maxExpandedStates = 1),
        ).analyze(catalog.level("campaign-030"))
        val unsolvable = LevelDefinition(
            id = "unsolvable-v3",
            number = 1,
            title = "Unsolvable",
            width = 3,
            height = 3,
            arrows = listOf(arrow("A", 2, 2, Direction.EAST)),
            magnets = emptyList(),
            walls = listOf(wall(2, 3)),
            designedSolutions = emptyList(),
        )
        val unsolvableMetrics = PuzzleSearchAnalyzer().analyze(unsolvable)

        assertFalse(cappedMetrics.searchComplete)
        assertTrue("EXPANDED_STATE_CAP" in cappedMetrics.truncationReasons)
        assertFalse(DifficultyV3Scorer.score(cappedMetrics).certifiable)
        assertFalse(unsolvableMetrics.solvable)
        assertEquals(0, unsolvableMetrics.minimumSolutionLength)
    }

    @Test
    fun `purposeful space and review priority are independent of human approval`() {
        val metrics = PuzzleSearchAnalyzer().analyze(catalog.level("campaign-081"))
        val priority = HumanReviewPriorityScorer.score(
            HumanReviewPriorityFactors(
                difficultyConfidence = metrics.confidence,
                solverTruncated = !metrics.searchComplete,
                unusualBranchingSeverity = 0.8,
                extremeDifficultySeverity = 0.4,
                qualityMarginSeverity = 0.9,
                novelStructuralPattern = true,
                structuralSimilaritySeverity = 0.7,
                newMechanicInteraction = false,
                unusualSolutionDepthSeverity = 0.6,
            ),
        )

        assertTrue(metrics.purposefulSpace.purposefulEmptyCellCount > 0)
        assertTrue(metrics.purposefulSpace.unusedEmptyCellCount > 0)
        assertTrue(priority.score > 0)
        assertEquals("PENDING", priority.humanReviewStatus)
    }
}

package com.rameshta.magnetrail.core.difficulty

import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.quality.LevelQualityAnalyzer
import com.rameshta.magnetrail.core.quality.LevelQualityStatus
import com.rameshta.magnetrail.core.quality.QualityReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyAnalyzerTest {
    private val catalog by lazy {
        LevelParser().parseCatalog(checkNotNull(javaClass.getResource("/development/PHASE0_SOURCE_CONTENT_V4.json")).readText())
    }

    @Test
    fun `golden mechanics expose pull push fatal cancellation and occlusion dependencies`() {
        val analyzer = DifficultyAnalyzer()
        val pull = analyzer.analyze(catalog.level("proto-003")).metrics
        val push = analyzer.analyze(catalog.level("proto-004")).metrics
        val fatal = analyzer.analyze(catalog.level("proto-006")).metrics
        val occlusion = analyzer.analyze(catalog.level("proto-008")).metrics
        val cancellation = analyzer.analyze(catalog.level("campaign-030")).metrics

        assertEquals(1, pull.pullSolutionActions)
        assertEquals(1, push.pushSolutionActions)
        assertEquals(1.0, fatal.fatalChoiceRatio, 0.0)
        assertEquals(1, fatal.criticalOrderConstraintCount)
        assertEquals(1, occlusion.occlusionDependencyCount)
        assertEquals(1, occlusion.wallDependencyCount)
        assertEquals(1, cancellation.cancellationDependencyCount)
        assertEquals(0, cancellation.solutionDivergenceDepth)
    }

    @Test
    fun `analysis and V2 scoring are deterministic`() {
        val level = catalog.level("campaign-030")
        val first = DifficultyAnalyzer().analyze(level)
        val second = DifficultyAnalyzer().analyze(level)

        assertEquals(first, second)
        assertEquals(37, first.score.score)
        assertEquals(DifficultyBandV2.NORMAL, first.score.band)
        assertEquals(60, first.metrics.solutionCountUpToCap)
    }

    @Test
    fun `bounded solver outcome is explicit and never silently fatal`() {
        val analysis = DifficultyAnalyzer(config = DifficultyConfig(solverStateCap = 1)).analyze(catalog.level("campaign-030"))

        assertFalse(analysis.searchComplete)
        assertTrue(analysis.metrics.stateAnalysisCapped)
        assertTrue("STATE_ANALYSIS_CAPPED" in analysis.score.cappedFlags)
    }

    @Test
    fun `weights validate and score bands include every boundary`() {
        assertThrows(IllegalArgumentException::class.java) {
            DifficultyWeights(wrongOrderRisk = 24)
        }
        assertEquals(DifficultyBandV2.TUTORIAL, DifficultyBandV2.fromScore(15))
        assertEquals(DifficultyBandV2.EASY, DifficultyBandV2.fromScore(16))
        assertEquals(DifficultyBandV2.NORMAL, DifficultyBandV2.fromScore(45))
        assertEquals(DifficultyBandV2.MEDIUM, DifficultyBandV2.fromScore(46))
        assertEquals(DifficultyBandV2.HARD, DifficultyBandV2.fromScore(75))
        assertEquals(DifficultyBandV2.VERY_HARD, DifficultyBandV2.fromScore(76))
        assertEquals(DifficultyBandV2.EXPERT, DifficultyBandV2.fromScore(100))
    }

    @Test
    fun `quality is independent and symmetry duplicates override the numeric score`() {
        val level = catalog.level("proto-001")
        val quality = LevelQualityAnalyzer().analyze(
            level = level,
            difficulty = DifficultyAnalyzer().analyze(level),
            symmetryDuplicateIds = listOf("another-level"),
        )

        assertEquals(LevelQualityStatus.REJECT, quality.qualityStatus)
        assertTrue(QualityReason.SYMMETRY_DUPLICATE in quality.qualityReasons)
        assertTrue(quality.qualityScore < 40)
    }
}

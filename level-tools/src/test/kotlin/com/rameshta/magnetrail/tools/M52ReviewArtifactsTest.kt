package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyAnalyzer
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.quality.LevelQualityAnalyzer
import com.rameshta.magnetrail.core.quality.LevelQualityStatus
import com.rameshta.magnetrail.core.solver.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class M52ReviewArtifactsTest {
    private val phase0Source by lazy { catalog("/development/PHASE0_SOURCE_CONTENT_V4.json") }
    private val review by lazy { catalog("/content/m5_2_review_catalog.json") }

    @Test
    fun `review catalog adds exactly fifty levels without changing protected content`() {
        assertEquals(150, phase0Source.levels.size)
        assertEquals(150, review.levels.size)
        assertEquals(review, phase0Source)
        assertEquals(4, review.contentVersion)
        assertEquals(2, review.generatorVersion)
        assertEquals((1..150).toList(), review.levels.map { it.number })
        assertEquals(150, review.levels.map { it.id }.toSet().size)
        assertEquals(150, review.levels.map(ContentFingerprint::exact).toSet().size)
        assertEquals(150, review.levels.map(ContentFingerprint::symmetryNormalized).toSet().size)

        val protectedMetrics = Json.parseToJsonElement(resource("/content/m5_1_campaign_metrics.json"))
            .jsonObject.getValue("levels").jsonArray
        assertEquals(100, protectedMetrics.size)
        protectedMetrics.forEachIndexed { index, golden ->
            val retained = review.levels[index]
            val fields = golden.jsonObject
            assertEquals(fields.getValue("levelId").jsonPrimitive.content, retained.id)
            assertEquals(fields.getValue("campaignNumber").jsonPrimitive.content.toInt(), retained.number)
            assertEquals(fields.getValue("exactFingerprint").jsonPrimitive.content, ContentFingerprint.exact(retained))
        }

        val expansion = review.levels.drop(100)
        assertEquals((101..150).map { "campaign-${it.toString().padStart(3, '0')}" }, expansion.map { it.id })
        assertTrue(expansion.all { it.width in 6..7 && it.height in 6..7 })
        assertEquals(10, expansion.count { it.metadata?.origin == LevelOrigin.HANDCRAFTED })
        assertEquals(40, expansion.count { it.metadata?.origin == LevelOrigin.GENERATOR_ASSISTED })
    }

    @Test
    fun `all review levels solve and new levels pass independent quality and grading gates`() {
        val engine = DefaultGameEngine()
        val difficultyAnalyzer = DifficultyAnalyzer()
        val qualityAnalyzer = LevelQualityAnalyzer()

        review.levels.forEach { level ->
            val metadata = requireNotNull(level.metadata)
            val solution = Solver().solve(level.initialState(), solutionLimit = 64, maxExploredStates = 50_000)
            assertTrue("${level.id} search incomplete", solution.searchComplete)
            assertTrue("${level.id} is unsolved", solution.solvable)
            assertEquals("${level.id} solution length", metadata.certifiedSolutionLength, solution.shortestDepth)
            assertEquals("${level.id} par", metadata.certifiedSolutionLength, metadata.grading.parActions)
            assertTrue("${level.id} two-star threshold", metadata.grading.twoStarMaxActions >= metadata.grading.parActions)

            var state = level.initialState()
            requireNotNull(solution.oneCleanSolution).forEach { action ->
                val result = engine.resolve(state, action)
                assertTrue("${level.id}/${action.arrowId} replay failed", result.success)
                state = result.resultingState
            }
            assertTrue("${level.id} replay did not clear", state.arrows.isEmpty())

            if (level.number >= 101) {
                val analysis = difficultyAnalyzer.analyze(level)
                assertTrue("${level.id} analyzer replay", analysis.solutionReplayValid)
                assertTrue("${level.id} analyzer search", analysis.searchComplete)
                assertFalse("${level.id} state analysis capped", analysis.metrics.stateAnalysisCapped)
                assertFalse("${level.id} counterfactual analysis capped", analysis.metrics.counterfactualAnalysisCapped)
                assertEquals("${level.id} unknown alternatives", 0, analysis.metrics.unknownAlternativeCount)
                assertEquals(
                    "${level.id} quality",
                    LevelQualityStatus.ACCEPT,
                    qualityAnalyzer.analyze(level, analysis).qualityStatus,
                )
            }
        }
    }

    @Test
    fun `golden expansion levels retain intended diagnostic evidence`() {
        val goldenFingerprints = mapOf(
            "campaign-102" to "sha256:35f345d41ce32c74179ad1603140266a6c8c99fa2a6d62148525205c63fd5f15",
            "campaign-105" to "sha256:0371a2960b8f7ebb29cdefd627dd535aed03e8ec61823c0d974a815d098a77bd",
            "campaign-106" to "sha256:e93b1301d5e557aac538799ed4448ef3cb519fba96ba05b69b48ea84eb562898",
            "campaign-125" to "sha256:0131a2641d716bc74a8442c32bb14c78e8e8c4f176a4d20c332ad97118d8d69a",
            "campaign-150" to "sha256:a9f57511e28b273486b0df32d586be212d64e3d4cf3f996a7006cb23e6694cf4",
        )
        val golden = goldenFingerprints.mapValues { (id, fingerprint) ->
            review.levels.single { it.id == id }.also {
                assertEquals(id, fingerprint, ContentFingerprint.exact(it))
            }
        }
        val analyses = golden.mapValues { DifficultyAnalyzer().analyze(it.value) }

        val crossfield = requireNotNull(golden["campaign-102"])
        val crossfieldMetrics = requireNotNull(analyses["campaign-102"]).metrics
        assertTrue(crossfield.magnets.size >= 2)
        assertTrue(crossfieldMetrics.polarityFlipCount >= 3)
        assertTrue(crossfieldMetrics.controllingMagnetChangeCount >= 1)
        assertTrue(crossfieldMetrics.occlusionDependencyCount >= 1)
        assertTrue(crossfieldMetrics.averageSuccessfulBranching >= 3.0)
        assertTrue(crossfieldMetrics.solutionCountUpToCap > 1)

        assertTrue(requireNotNull(analyses["campaign-105"]).metrics.fatalChoiceRatio >= 0.5)
        assertTrue(requireNotNull(analyses["campaign-125"]).metrics.cancellationDependencyCount >= 1)

        val recovery = requireNotNull(analyses["campaign-106"])
        assertTrue(recovery.score.score < requireNotNull(analyses["campaign-105"]).score.score)
        assertTrue(resource("/content/m5_2_levels_101_150_metrics.csv").lineSequence()
            .single { it.startsWith("campaign-106,") }.contains(",RECOVERY,"))

        val finale = requireNotNull(analyses["campaign-150"])
        assertTrue(finale.score.score >= 61)
        assertTrue(finale.metrics.criticalOrderConstraintCount >= 1)
        assertTrue(finale.metrics.controllingMagnetChangeCount >= 1)
        assertEquals("mastery-set", requireNotNull(golden["campaign-150"]?.metadata).packId)

        golden.forEach { (id, level) ->
            val analysis = requireNotNull(analyses[id])
            assertEquals(LevelQualityStatus.ACCEPT, LevelQualityAnalyzer().analyze(level, analysis).qualityStatus)
            assertEquals(analysis.metrics.cleanSolutionLength, requireNotNull(level.metadata).grading.parActions)
        }
    }

    @Test
    fun `owner approval gate records all fifty explicit approvals`() {
        val approvalRows = resource("/content/m5_2_manual_approvals.csv")
            .lineSequence().filter(String::isNotBlank).drop(1).toList()
        assertEquals(50, approvalRows.size)
        assertTrue(approvalRows.all {
            val columns = it.split(',')
            columns.getOrNull(2) == "APPROVED" && columns.getOrNull(3) == "Project Owner"
        })
        val manualReview = resource("/content/M5_2_MANUAL_REVIEW.md")
        assertTrue(manualReview.contains("Project-owner approval of all 50 levels"))
        assertFalse(manualReview.contains("☐"))
        assertEquals(50, manualReview.lineSequence().count { it.endsWith("| APPROVED |") })
    }

    @Test
    fun `150 level catalog stays within the documented host parse budget`() {
        val source = resource("/content/m5_2_review_catalog.json")
        val samples = List(5) {
            measureTimeMillis { assertEquals(150, LevelParser().parseCatalog(source).levels.size) }
        }
        assertTrue("Catalog parse exceeded 2,000 ms: $samples", samples.max() < 2_000)
    }

    private fun catalog(path: String): LevelCatalog = LevelParser().parseCatalog(resource(path))

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}

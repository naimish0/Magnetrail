package com.rameshta.magnetrail.core.generation

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.DifficultyBand
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.solver.Solver
import com.rameshta.magnetrail.core.generation.v5.CAMPAIGN_CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.v5.GENERATOR_VERSION_V5
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows
import com.rameshta.magnetrail.core.level.LevelValidationException

class ShippedContentTest {
    private val engine = DefaultGameEngine()

    @Test
    fun `all campaign content is unique certified and replayable`() {
        val campaign = load("/Magnetrail_Campaign_Levels_v3.json")
        assertEquals(2_205, campaign.levels.size)
        assertEquals(2_205, campaign.levels.map { it.id }.toSet().size)
        assertEquals((1..2_205).toList(), campaign.levels.map { it.number })
        assertEquals(2_205, campaign.levels.map(ContentFingerprint::of).toSet().size)
        assertEquals(2_205, campaign.levels.map(ContentFingerprint::symmetryNormalized).toSet().size)
        assertEquals(0, campaign.levels.count { it.metadata?.origin == LevelOrigin.HANDCRAFTED })
        assertEquals(2_205, campaign.levels.count { it.metadata?.origin == LevelOrigin.GENERATOR_ASSISTED })
        assertEquals(CAMPAIGN_CONTENT_VERSION, campaign.contentVersion)
        assertEquals(GENERATOR_VERSION_V5, campaign.generatorVersion)
        assertTrue(campaign.levels.take(200).all { it.metadata?.previousContentFingerprint != null })
        assertTrue(campaign.levels.drop(200).all { it.metadata?.previousContentFingerprint == null })
        assertEquals(
            (201..2_205).map { "campaign-${it.toString().padStart(3, '0')}" },
            campaign.levels.drop(200).map { it.id },
        )
        assertEquals(
            mapOf(
                "v5-easy" to 334,
                "v5-medium" to 334,
                "v5-hard" to 333,
                "v5-campaign-v9-super-hard" to 333,
                "v5-campaign-v9-expert" to 333,
                "v5-campaign-v9-master" to 333,
            ),
            campaign.levels.drop(205).groupingBy { it.metadata?.generationProfile }.eachCount(),
        )
        val infinite = load("/content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
        val infiniteExact = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::exact)
        val infiniteSymmetry = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::symmetryNormalized)
        assertTrue(campaign.levels.drop(205).none { ContentFingerprint.exact(it) in infiniteExact })
        assertTrue(campaign.levels.drop(205).none { ContentFingerprint.symmetryNormalized(it) in infiniteSymmetry })
        val contentV8Source = load("/content/v9_expansion/SOURCE_CONTENT_V8.json")
        assertEquals(205, contentV8Source.levels.size)
        assertEquals(contentV8Source.levels, campaign.levels.take(205))
        assertTrue(campaign.levels.all { it.width in 3..8 && it.height in 3..8 })
        assertTrue(campaign.levels.none { it.width == 9 || it.height == 9 })
        assertEquals((1..12).map { "proto-${it.toString().padStart(3, '0')}" }, campaign.levels.take(12).map { it.id })

        campaign.levels.forEach { level ->
            val metadata = requireNotNull(level.metadata)
            val solved = Solver().solve(level.initialState(), solutionLimit = 100_000, maxExploredStates = 200_000)
            assertTrue("${level.id} solver incomplete", solved.searchComplete)
            assertTrue("${level.id} unsolved", solved.solvable)
            assertEquals(metadata.certifiedSolutionLength, solved.shortestDepth)
            assertEquals(metadata.contentFingerprint, ContentFingerprint.of(level))
            var state = level.initialState()
            requireNotNull(solved.oneCleanSolution).forEach { action ->
                val result = engine.resolve(state, action)
                assertTrue("${level.id}/${action.arrowId} failed replay", result.success)
                state = result.resultingState
            }
            assertTrue(state.arrows.isEmpty())
            level.arrows.forEach { arrow ->
                val result = engine.resolve(level.initialState(), PlayerAction(arrow.id))
                if (!result.success) assertEquals(level.initialState(), result.resultingState)
            }
        }
        assertEquals(setOf(DifficultyBand.INTRO, DifficultyBand.DEVELOPING, DifficultyBand.ADVANCED),
            campaign.levels.mapNotNull { it.metadata?.difficultyBand }.toSet())
        assertTrue(campaign.levels.flatMap { it.metadata?.mechanicTags.orEmpty() }.containsAll(
            listOf(
                "MAGNET_CONTROL",
                "POLARITY_DEPENDENCY",
                "WALLS",
                "OCCLUSION",
                "CANCELLATION",
                "ORDER_DEPENDENCY",
                "EXPOSURE_REVEAL",
            ),
        ))
    }

    @Test
    fun `daily fallback bank is solver certified`() {
        val fallbacks = load("/Magnetrail_Daily_Fallbacks_v1.json")
        assertEquals(7, fallbacks.levels.size)
        assertEquals(7, fallbacks.levels.map(ContentFingerprint::of).toSet().size)
        fallbacks.levels.forEach { assertTrue(Solver().solve(it.initialState()).solvable) }
    }

    @Test
    fun `stale content fingerprint fails with a useful error`() {
        val source = checkNotNull(javaClass.getResource("/Magnetrail_Campaign_Levels_v3.json")).readText()
        val fingerprint = Regex("sha256:[0-9a-f]{64}").find(source)?.value ?: error("missing fingerprint")
        val stale = source.replaceFirst(fingerprint, "sha256:${"0".repeat(64)}")

        val error = assertThrows(LevelValidationException::class.java) {
            LevelParser().parseCatalog(stale)
        }
        assertTrue(error.message.orEmpty().contains("content-hash mismatch"))
    }

    private fun load(path: String): LevelCatalog = LevelParser().parseCatalog(
        checkNotNull(javaClass.getResource(path)).readText(),
    )
}

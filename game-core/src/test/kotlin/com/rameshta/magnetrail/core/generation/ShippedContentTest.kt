package com.rameshta.magnetrail.core.generation

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.DifficultyBand
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.solver.Solver
import com.rameshta.magnetrail.core.generation.v5.D2_STAGING_CONTENT_VERSION
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
        assertEquals(200, campaign.levels.size)
        assertEquals(200, campaign.levels.map { it.id }.toSet().size)
        assertEquals((1..200).toList(), campaign.levels.map { it.number })
        assertEquals(200, campaign.levels.map(ContentFingerprint::of).toSet().size)
        assertEquals(200, campaign.levels.map(ContentFingerprint::symmetryNormalized).toSet().size)
        assertEquals(0, campaign.levels.count { it.metadata?.origin == LevelOrigin.HANDCRAFTED })
        assertEquals(200, campaign.levels.count { it.metadata?.origin == LevelOrigin.GENERATOR_ASSISTED })
        assertEquals(D2_STAGING_CONTENT_VERSION, campaign.contentVersion)
        assertEquals(GENERATOR_VERSION_V5, campaign.generatorVersion)
        assertTrue(campaign.levels.all { it.metadata?.previousContentFingerprint != null })
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

package com.rameshta.magnetrail.core.generation

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyAnalyzer
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.prototypeCatalog
import com.rameshta.magnetrail.core.quality.LevelQualityAnalyzer
import com.rameshta.magnetrail.core.quality.LevelQualityStatus
import com.rameshta.magnetrail.core.solver.Solver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationCertificationTest {
    @Test
    fun `same version profile and seed produce identical canonical level`() {
        val request = GenerationRequest(
            "deterministic", 1, "Deterministic", 42L,
            GenerationProfile.DEVELOPING_MEDIUM, "test",
        )
        val generator = LevelGenerator(prototypeCatalog().levels)
        val first = generator.generate(request) as GenerationResult.Generated
        val second = generator.generate(request) as GenerationResult.Generated

        assertEquals(LevelParser().encodeCatalog(catalog(first.level)), LevelParser().encodeCatalog(catalog(second.level)))
        assertEquals(ContentFingerprint.of(first.level), ContentFingerprint.of(second.level))
    }

    @Test
    fun `representative seeds do not collapse to one fingerprint`() {
        val generator = LevelGenerator(prototypeCatalog().levels)
        val fingerprints = (100L..109L).mapNotNull { seed ->
            (generator.generate(
                GenerationRequest("seed-$seed", 1, "Seed $seed", seed, GenerationProfile.DEVELOPING_MEDIUM, "test"),
            ) as? GenerationResult.Generated)?.level?.let(ContentFingerprint::of)
        }
        assertTrue(fingerprints.size >= 8)
        assertTrue(fingerprints.toSet().size >= 5)
        assertNotEquals(fingerprints.first(), fingerprints.last())
    }

    @Test
    fun `M5 2 advanced profile is deterministic versioned and independently acceptable`() {
        val request = GenerationRequest(
            stableId = "m52-profile-golden",
            sequenceNumber = 150,
            title = "M5.2 profile golden",
            seed = 520_002L,
            profile = GenerationProfile.M52_MASTERY,
            packId = "mastery-set",
            contentVersion = M52_CONTENT_VERSION,
            generatorVersion = M52_GENERATOR_VERSION,
        )
        val generator = LevelGenerator(campaignCatalog().levels)
        val first = generator.generate(request) as GenerationResult.Generated
        val second = generator.generate(request) as GenerationResult.Generated

        assertEquals(ContentFingerprint.exact(first.level), ContentFingerprint.exact(second.level))
        assertEquals(first.metrics, second.metrics)
        assertEquals(7, first.level.width)
        assertTrue(first.level.height in 6..7)
        assertEquals(M52_CONTENT_VERSION, first.level.metadata?.contentVersion)
        assertEquals(M52_GENERATOR_VERSION, first.level.metadata?.generatorVersion)
        assertEquals(GenerationProfile.M52_MASTERY.profileId, first.level.metadata?.generationProfile)
        assertTrue(first.metrics.magnetControlledSolutionActions >= GenerationProfile.M52_MASTERY.minMagnetControlledActions)
        assertTrue(first.metrics.polarityFlipCount >= GenerationProfile.M52_MASTERY.minPolarityFlips)
        val analysis = DifficultyAnalyzer().analyze(first.level)
        assertTrue(analysis.score.score in GenerationProfile.M52_MASTERY.minDifficultyScoreV2..
            GenerationProfile.M52_MASTERY.maxDifficultyScoreV2)
        assertEquals(LevelQualityStatus.ACCEPT, LevelQualityAnalyzer().analyze(first.level, analysis).qualityStatus)
    }

    @Test
    fun `solver explored-state cap terminates deterministically`() {
        val campaign = campaignCatalog()
        val state = campaign.levels.first { it.arrows.size >= 5 }.initialState()
        val first = Solver().solve(state, solutionLimit = 32, maxExploredStates = 1)
        val second = Solver().solve(state, solutionLimit = 32, maxExploredStates = 1)

        assertFalse(first.searchComplete)
        assertEquals("explored-state-cap:1", first.terminationReason)
        assertEquals(first, second)
    }

    private fun campaignCatalog() = resourceCatalog("/development/PHASE0_SOURCE_CONTENT_V4.json")

    private fun resourceCatalog(path: String) = LevelParser().parseCatalog(
        checkNotNull(javaClass.getResource(path)).readText(),
    )

    private fun catalog(level: com.rameshta.magnetrail.core.model.LevelDefinition) =
        com.rameshta.magnetrail.core.level.LevelCatalog(
            2, "magnetrail-core-1", "test", listOf(level), CONTENT_VERSION, GENERATOR_VERSION,
        )
}

package com.rameshta.magnetrail.core.generation

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.prototypeCatalog
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
    fun `solver explored-state cap terminates deterministically`() {
        val campaign = campaignCatalog()
        val state = campaign.levels.first { it.arrows.size >= 5 }.initialState()
        val first = Solver().solve(state, solutionLimit = 32, maxExploredStates = 1)
        val second = Solver().solve(state, solutionLimit = 32, maxExploredStates = 1)

        assertFalse(first.searchComplete)
        assertEquals("explored-state-cap:1", first.terminationReason)
        assertEquals(first, second)
    }

    private fun campaignCatalog() = resourceCatalog("/Magnetrail_Campaign_Levels_v3.json")

    private fun resourceCatalog(path: String) = LevelParser().parseCatalog(
        checkNotNull(javaClass.getResource(path)).readText(),
    )

    private fun catalog(level: com.rameshta.magnetrail.core.model.LevelDefinition) =
        com.rameshta.magnetrail.core.level.LevelCatalog(
            2, "magnetrail-core-1", "test", listOf(level), CONTENT_VERSION, GENERATOR_VERSION,
        )
}

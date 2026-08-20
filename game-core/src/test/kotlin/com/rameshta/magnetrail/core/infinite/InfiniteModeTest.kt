package com.rameshta.magnetrail.core.infinite

import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.DifficultyBand
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.GradingThresholds
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelMetadata
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.model.Position
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InfiniteModeTest {
    @Test
    fun `selection is deterministic and avoids recent fingerprints`() {
        val catalog = catalog(listOf(level("easy-a", "v5-d2.1-easy", 1), level("easy-b", "v5-d2.1-easy", 2)))
        val selector = InfiniteCatalogSelector()
        val first = selector.select(catalog, InfiniteDifficulty.RELAXED, InfiniteSelectionState())
        val again = selector.select(catalog, InfiniteDifficulty.RELAXED, InfiniteSelectionState())
        val next = selector.select(
            catalog,
            InfiniteDifficulty.RELAXED,
            InfiniteSelectionState(ordinal = 1, recentFingerprints = listOf(first.level.metadata!!.contentFingerprint)),
        )

        assertEquals(first.identity.puzzleId, again.identity.puzzleId)
        assertFalse(first.level.metadata!!.contentFingerprint == next.level.metadata!!.contentFingerprint)
    }

    @Test
    fun `expert uses the strongest certified fallback when expert content is unavailable`() {
        val catalog = catalog(listOf(level("hard", "v5-d2.1-hard", 3), level("very", "v5-d2.1-very-hard", 4)))
        val decision = InfiniteCatalogSelector().select(catalog, InfiniteDifficulty.EXPERT, InfiniteSelectionState())

        assertTrue(decision.fallbackUsed)
        assertEquals("v5-d2.1-very-hard", decision.selectedProfile)
        assertTrue(InfiniteCatalogSelector().validateCatalog(catalog).isEmpty())
    }

    @Test
    fun `balanced selection follows a deterministic mixed difficulty pattern`() {
        val catalog = catalog(
            listOf(
                level("easy", "v5-d2.1-easy", 11),
                level("medium", "v5-d2.1-medium", 12),
                level("hard", "v5-d2.1-hard", 13),
            ),
        )
        val selector = InfiniteCatalogSelector()
        val profiles = (0..3).map { ordinal ->
            selector.select(catalog, InfiniteDifficulty.BALANCED, InfiniteSelectionState(ordinal)).selectedProfile
        }

        assertEquals(
            listOf("v5-d2.1-medium", "v5-d2.1-easy", "v5-d2.1-hard", "v5-d2.1-medium"),
            profiles,
        )
    }

    @Test
    fun `progressive journey teaches easy medium and hard before mixed play`() {
        val catalog = allBandsCatalog()
        val selector = InfiniteCatalogSelector()

        val selectedProfiles = (0..29).map { ordinal ->
            selector.select(catalog, InfiniteDifficulty.PROGRESSIVE, InfiniteSelectionState(ordinal)).selectedProfile
        }

        assertEquals(List(10) { "v5-d2.1-easy" }, selectedProfiles.take(10))
        assertEquals(List(10) { "v5-d2.1-medium" }, selectedProfiles.drop(10).take(10))
        assertEquals(List(10) { "v5-d2.1-hard" }, selectedProfiles.drop(20).take(10))
    }

    @Test
    fun `first five progressive selections use compact certified tutorial boards`() {
        val resource = checkNotNull(javaClass.getResource("/content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json"))
        val catalog = LevelParser().parseCatalog(resource.readText())
        val selector = InfiniteCatalogSelector()
        val selected = mutableListOf<LevelDefinition>()
        val recent = mutableListOf<String>()

        repeat(5) { ordinal ->
            val decision = selector.select(
                catalog,
                InfiniteDifficulty.PROGRESSIVE,
                InfiniteSelectionState(ordinal, recent),
            )
            selected += decision.level
            recent += requireNotNull(decision.level.metadata).contentFingerprint
        }

        assertTrue(selected.all { it.metadata?.generationProfile?.endsWith("-easy") == true })
        assertEquals(listOf(2, 2, 2, 2, 3), selected.map { it.arrows.size })
        assertTrue(selected.all { it.metadata?.validFirstActionCount == 1 })
        assertTrue(selected.all { it.metadata?.solutionCount == 1 })
        assertEquals(5, selected.map { it.metadata?.contentFingerprint }.distinct().size)
    }

    @Test
    fun `progressive journey enters the requested mixed rhythm after onboarding`() {
        val catalog = allBandsCatalog()
        val selector = InfiniteCatalogSelector()

        val selectedProfiles = (30..38).map { ordinal ->
            selector.select(catalog, InfiniteDifficulty.PROGRESSIVE, InfiniteSelectionState(ordinal)).selectedProfile
        }

        assertEquals(
            listOf(
                "v5-d2.1-hard",
                "v5-d2.1-very-hard",
                "v5-d2.1-easy",
                "v5-d2.1-expert",
                "v5-d2.1-medium",
                "v5-d2.1-easy",
                "v5-d2.1-master",
                "v5-d2.1-hard",
                "v5-d2.1-medium",
            ),
            selectedProfiles,
        )
    }

    @Test
    fun `later progressive blocks are varied but deterministic`() {
        val catalog = allBandsCatalog()
        val selector = InfiniteCatalogSelector()

        val first = (39..47).map { ordinal ->
            selector.select(catalog, InfiniteDifficulty.PROGRESSIVE, InfiniteSelectionState(ordinal)).selectedProfile
        }
        val again = (39..47).map { ordinal ->
            selector.select(catalog, InfiniteDifficulty.PROGRESSIVE, InfiniteSelectionState(ordinal)).selectedProfile
        }

        assertEquals(first, again)
        assertEquals(9, first.size)
        assertTrue(first.toSet().containsAll(setOf("v5-d2.1-easy", "v5-d2.1-expert", "v5-d2.1-master")))
    }

    @Test
    fun `catalog validation accepts certified high-band and rejects campaign entries`() {
        val expert = level("expert", "v5-d2.1-expert", 5)
        val campaign = level("campaign", "v5-d2.1-easy", 6).let {
            it.copy(metadata = it.metadata!!.copy(packId = "campaign"))
        }
        val reasons = InfiniteCatalogSelector().validateCatalog(catalog(listOf(expert, campaign)))

        assertFalse(reasons.any { it.startsWith("uncertified-high-band:") })
        assertTrue(reasons.any { it == "uncertified:campaign" })
    }

    @Test
    fun `master selects certified master content without fallback`() {
        val catalog = catalog(
            listOf(
                level("expert", "v5-d2.1-expert", 21),
                level("master", "v5-d2.1-master", 22),
            ),
        )

        val decision = InfiniteCatalogSelector().select(
            catalog,
            InfiniteDifficulty.MASTER,
            InfiniteSelectionState(),
        )

        assertFalse(decision.fallbackUsed)
        assertEquals("v5-d2.1-master", decision.selectedProfile)
    }

    @Test
    fun `very hard uses nearest certified high band fallback`() {
        val decision = InfiniteCatalogSelector().select(
            catalog(listOf(level("expert", "v5-d2.1-expert", 23))),
            InfiniteDifficulty.VERY_HARD,
            InfiniteSelectionState(),
        )

        assertTrue(decision.fallbackUsed)
        assertEquals("v5-d2.1-expert", decision.selectedProfile)
    }

    @Test
    fun `super hard fallback favors expert over equally near hard`() {
        val decision = InfiniteCatalogSelector().select(
            catalog(
                listOf(
                    level("hard", "v5-d2.1-hard", 24),
                    level("expert", "v5-d2.1-expert", 25),
                ),
            ),
            InfiniteDifficulty.VERY_HARD,
            InfiniteSelectionState(),
        )

        assertTrue(decision.fallbackUsed)
        assertEquals("v5-d2.1-expert", decision.selectedProfile)
    }

    private fun catalog(levels: List<LevelDefinition>) = LevelCatalog(
        schemaVersion = 1,
        ruleVersion = "magnetrail-core-1",
        catalogId = "magnetrail-infinite-v1",
        levels = levels,
        contentVersion = INFINITE_CATALOG_VERSION,
        generatorVersion = 5,
    )

    private fun allBandsCatalog() = catalog(
        listOf(
            level("easy", "v5-d2.1-easy", 31),
            level("medium", "v5-d2.1-medium", 32),
            level("hard", "v5-d2.1-hard", 33),
            level("very-hard", "v5-d2.1-very-hard", 34),
            level("expert", "v5-d2.1-expert", 35),
            level("master", "v5-d2.1-master", 36),
        ),
    )

    private fun level(id: String, profile: String, seed: Long): LevelDefinition {
        val hash = "sha256:" + seed.toString().padStart(64, '0')
        return LevelDefinition(
            id = id,
            number = seed.toInt(),
            title = id,
            width = 2,
            height = 2,
            arrows = listOf(Arrow("a", Position(1, 1), Direction.NORTH)),
            magnets = emptyList(),
            walls = emptyList(),
            designedSolutions = listOf(listOf("a")),
            metadata = LevelMetadata(
                contentVersion = 1,
                origin = LevelOrigin.GENERATOR_ASSISTED,
                generatorVersion = 5,
                generatorSeed = seed,
                generationProfile = profile,
                difficultyBand = DifficultyBand.ADVANCED,
                certifiedSolutionLength = 1,
                solutionCount = 1,
                solutionCountCapped = false,
                validFirstActionCount = 1,
                exploredStateCount = 1,
                grading = GradingThresholds(1, 3),
                packId = "infinite-v1",
                mechanicTags = listOf("MOVEMENT"),
                contentFingerprint = hash,
            ),
        )
    }
}

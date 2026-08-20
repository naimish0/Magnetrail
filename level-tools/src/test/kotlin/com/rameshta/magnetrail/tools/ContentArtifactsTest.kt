package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.economy.EconomySimulation
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.infinite.InfiniteCatalogSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ContentArtifactsTest {
    @Test
    fun `Infinite catalog contains certified content in every enabled band`() {
        val catalog = LevelParser().parseCatalog(resource("/content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json"))
        val profiles = catalog.levels.mapNotNull { it.metadata?.generationProfile }

        assertEquals(624, catalog.levels.size)
        assertTrue(InfiniteCatalogSelector().validateCatalog(catalog).isEmpty())
        assertEquals(12, profiles.count { it.endsWith("expert") })
        assertEquals(12, profiles.count { it.endsWith("master") })
        assertEquals(catalog.levels.size, catalog.levels.map { it.id }.distinct().size)
        assertEquals(
            catalog.levels.size,
            catalog.levels.map { requireNotNull(it.metadata).contentFingerprint }.distinct().size,
        )
    }

    @Test
    fun `checked in report covers every promoted campaign level`() {
        val catalog = LevelParser().parseCatalog(resource("/Magnetrail_Campaign_Levels_v3.json"))
        val report = resource("/M3_CONTENT_REPORT.csv").lineSequence().filter(String::isNotBlank).toList()
        val expansion = resource("/content/m5_2_levels_101_150_metrics.csv")
            .lineSequence().filter(String::isNotBlank).toList()

        assertEquals(101, report.size)
        assertEquals(catalog.levels.take(100).map { it.id }, report.drop(1).map { it.substringBefore(',') })
        assertEquals(51, expansion.size)
        assertEquals(catalog.levels.take(150).drop(100).map { it.id }, expansion.drop(1).map { it.substringBefore(',') })
        assertTrue(report.first().contains("rejected_before_acceptance"))
    }

    @Test
    fun `developer economy simulation remains nonnegative and routes unaffordable hints to ads`() {
        EconomySimulation.representativeCampaign().forEach { result ->
            assertTrue(result.minimumBalance >= 0)
            assertTrue(result.medianBalance >= result.minimumBalance)
            assertTrue(result.unaffordableHintRequests >= 0)
        }
        assertEquals(
            0,
            EconomySimulation.representativeCampaign()
                .single { it.scenario == "periodic-assistance" }
                .unaffordableHintRequests,
        )
        assertTrue(
            EconomySimulation.representativeCampaign()
                .single { it.scenario == "hint-every-level" }
                .unaffordableHintRequests > 0,
        )
    }

    @Test
    fun `M5 1 checked in reports cover the stable campaign and have no hard duplicate finding`() {
        val catalog = LevelParser().parseCatalog(resource("/Magnetrail_Campaign_Levels_v3.json"))
        val metrics = Json.parseToJsonElement(resource("/content/m5_1_campaign_metrics.json")).jsonObject
        val quality = Json.parseToJsonElement(resource("/content/m5_1_campaign_quality.json")).jsonObject
        val metricLevels = metrics.getValue("levels").jsonArray
        val qualityLevels = quality.getValue("levels").jsonArray
        val comparison = resource("/content/m5_1_v1_v2_difficulty_comparison.csv")
            .lineSequence().filter(String::isNotBlank).toList()
        val duplicates = resource("/content/m5_1_duplicate_report.md")

        assertEquals(100, metricLevels.size)
        assertEquals(100, qualityLevels.size)
        assertEquals(101, comparison.size)
        assertEquals(catalog.levels.take(100).map { it.id }, metricLevels.map { it.jsonObject.getValue("levelId").jsonPrimitive.content })
        assertTrue(qualityLevels.none { it.jsonObject.getValue("qualityStatus").jsonPrimitive.content == "REJECT" })
        assertTrue(duplicates.contains("Exact duplicate groups: 0"))
        assertTrue(duplicates.contains("Symmetry-equivalent groups excluding exact-only equality: 0"))
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}

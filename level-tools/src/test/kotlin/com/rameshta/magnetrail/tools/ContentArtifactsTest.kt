package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.economy.EconomySimulation
import com.rameshta.magnetrail.core.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentArtifactsTest {
    @Test
    fun `checked in report covers every promoted campaign level`() {
        val catalog = LevelParser().parseCatalog(resource("/Magnetrail_Campaign_Levels_v3.json"))
        val report = resource("/M3_CONTENT_REPORT.csv").lineSequence().filter(String::isNotBlank).toList()

        assertEquals(catalog.levels.size + 1, report.size)
        assertEquals(catalog.levels.map { it.id }, report.drop(1).map { it.substringBefore(',') })
        assertTrue(report.first().contains("rejected_before_acceptance"))
    }

    @Test
    fun `developer economy simulation reports minimum median and no blocking`() {
        EconomySimulation.representativeCampaign().forEach { result ->
            assertTrue(result.minimumBalance >= 0)
            assertTrue(result.medianBalance >= result.minimumBalance)
            assertEquals(0, result.unaffordableHintRequests)
        }
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}

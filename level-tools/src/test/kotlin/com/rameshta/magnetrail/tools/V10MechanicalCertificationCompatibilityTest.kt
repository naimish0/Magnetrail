package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.generation.v5.CertificationPipelineV5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesCampaignV10
import com.rameshta.magnetrail.core.generation.v5.CertificationResultV5
import com.rameshta.magnetrail.core.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class V10MechanicalCertificationCompatibilityTest {
    @Test
    fun `first remediated V10 board exposes stale analyzer-bound mechanic tags`() {
        val catalog = LevelParser().parseCatalog(
            checkNotNull(javaClass.getResource("/Magnetrail_Campaign_Levels_v3.json")).readText(),
        )
        val level = catalog.levels.single { it.id == "campaign-206" }
        val metadata = requireNotNull(level.metadata)
        val profile = GenerationProfilesCampaignV10.all.single { it.id == metadata.generationProfile }
        val result = CertificationPipelineV5().certify(
            level = level.copy(metadata = null),
            profile = profile,
            seed = requireNotNull(metadata.generatorSeed),
            packId = metadata.packId,
            contentVersion = metadata.contentVersion,
            previousContentFingerprint = metadata.previousContentFingerprint,
        )

        assertTrue(result is CertificationResultV5.Accepted)
        result as CertificationResultV5.Accepted
        val regeneratedMetadata = requireNotNull(result.level.metadata)
        assertNotEquals(metadata.mechanicTags, regeneratedMetadata.mechanicTags)
        assertEquals(
            metadata.copy(mechanicTags = regeneratedMetadata.mechanicTags),
            regeneratedMetadata,
        )
        assertEquals(level.designedSolutions, result.level.designedSolutions)
    }
}

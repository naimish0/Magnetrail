package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesCampaignV10
import org.junit.Assert.assertEquals
import org.junit.Test

class CampaignV10RemediationTest {
    @Test
    fun `V10 allocation remains balanced across six candidate bands`() {
        assertEquals(
            mapOf(
                GenerationProfilesCampaignV10.EASY.id to 334,
                GenerationProfilesCampaignV10.EXPERT.id to 333,
                GenerationProfilesCampaignV10.HARD.id to 333,
                GenerationProfilesCampaignV10.MASTER.id to 333,
                GenerationProfilesCampaignV10.MEDIUM.id to 334,
                GenerationProfilesCampaignV10.SUPER_HARD.id to 333,
            ).toSortedMap(),
            campaignV10ExpectedProfileDistribution(),
        )
    }

    @Test
    fun `arrow targets grow with board size and candidate difficulty`() {
        assertEquals(6, campaignV10TargetArrowCount(GenerationProfilesCampaignV10.EASY.id, 4))
        assertEquals(12, campaignV10TargetArrowCount(GenerationProfilesCampaignV10.EASY.id, 8))
        assertEquals(14, campaignV10TargetArrowCount(GenerationProfilesCampaignV10.SUPER_HARD.id, 8))
        assertEquals(16, campaignV10TargetArrowCount(GenerationProfilesCampaignV10.EXPERT.id, 8))
        assertEquals(18, campaignV10TargetArrowCount(GenerationProfilesCampaignV10.MASTER.id, 8))
    }
}

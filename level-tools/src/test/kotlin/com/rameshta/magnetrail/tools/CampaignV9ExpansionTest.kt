package com.rameshta.magnetrail.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignV9ExpansionTest {
    @Test
    fun `balanced allocation contains 2000 interleaved levels`() {
        val sequence = campaignV9DifficultySequence()

        assertEquals(2_000, sequence.size)
        assertEquals(334, sequence.count { it == "Easy" })
        assertEquals(334, sequence.count { it == "Medium" })
        assertEquals(333, sequence.count { it == "Hard" })
        assertEquals(333, sequence.count { it == "Super Hard" })
        assertEquals(333, sequence.count { it == "Expert" })
        assertEquals(333, sequence.count { it == "Master" })
        assertEquals(
            listOf("Easy", "Medium", "Hard", "Super Hard", "Expert", "Master"),
            sequence.take(6),
        )
        assertTrue(sequence.windowed(6).all { window -> window.distinct().size >= 5 })
    }

    @Test
    fun `profile allocation is explicit and balanced`() {
        assertEquals(
            mapOf(
                "v5-easy" to 334,
                "v5-campaign-v9-expert" to 333,
                "v5-hard" to 333,
                "v5-campaign-v9-master" to 333,
                "v5-medium" to 334,
                "v5-campaign-v9-super-hard" to 333,
            ),
            campaignV9ExpectedProfileDistribution(),
        )
    }
}

package com.rameshta.magnetrail.core.difficulty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1DifficultyTargetsTest {
    @Test
    fun `targets cover exactly 151 through 200 with deliberate recovery slots`() {
        val targets = (151..200).associateWith(Phase1DifficultyTargets::forCampaignNumber)

        assertEquals(50, targets.size)
        assertEquals("phase1-advanced-recall", targets.getValue(151).id)
        assertEquals("phase1-recovery", targets.getValue(154).id)
        assertEquals("phase1-dependency-lattice", targets.getValue(161).id)
        assertEquals("phase1-fair-false-path", targets.getValue(176).id)
        assertEquals("phase1-expert-circuit", targets.getValue(200).id)
        assertTrue(targets.values.all { it.maxGuessDependentRatio == 0.0 })
    }

    @Test(expected = IllegalStateException::class)
    fun `target lookup rejects future phases`() {
        Phase1DifficultyTargets.forCampaignNumber(201)
    }
}

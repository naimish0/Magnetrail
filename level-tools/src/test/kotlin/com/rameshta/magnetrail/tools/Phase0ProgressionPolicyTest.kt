package com.rameshta.magnetrail.tools

import org.junit.Assert.assertTrue
import org.junit.Test

class Phase0ProgressionPolicyTest {
    @Test
    fun `rising medians and upper campaign waves pass`() {
        val scores = buildMap {
            (1..10).forEach { put(it, 10) }
            (11..25).forEach { put(it, 20) }
            (26..40).forEach { put(it, 40) }
            (41..60).forEach { put(it, 55) }
            (61..80).forEach { put(it, 70) }
            (81..100).forEach { put(it, 84) }
            (101..125).forEach { put(it, if (it % 6 == 0) 60 else 76) }
            (126..150).forEach { put(it, if (it % 7 == 0) 62 else 80) }
        }

        assertTrue(Phase0ProgressionPolicy.failures(scores).isEmpty())
    }

    @Test
    fun `flat relabeled campaign is rejected`() {
        val scores = (1..150).associateWith { 70 }

        assertTrue(Phase0ProgressionPolicy.failures(scores).isNotEmpty())
    }

    @Test
    fun `very hard lower quartile must exceed hard median`() {
        val scores = buildMap {
            (1..10).forEach { put(it, 10) }
            (11..25).forEach { put(it, 20) }
            (26..40).forEach { put(it, 40) }
            (41..60).forEach { put(it, 55) }
            (61..80).forEach { put(it, 70) }
            (81..85).forEach { put(it, 68) }
            (86..100).forEach { put(it, 84) }
            (101..125).forEach { put(it, 76) }
            (126..150).forEach { put(it, 80) }
        }

        assertTrue(
            "expected percentile failure",
            "PROGRESSION_VERY_HARD_LOWER_QUARTILE_TOO_LOW" in Phase0ProgressionPolicy.failures(scores),
        )
    }
}

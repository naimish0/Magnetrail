package com.rameshta.magnetrail

import com.rameshta.magnetrail.game.CelebrationIntensity
import com.rameshta.magnetrail.game.completionCelebrationStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionCelebrationTest {
    @Test
    fun `clean three-star solve receives strongest dynamic celebration`() {
        val style = completionCelebrationStyle(
            levelIdentity = "infinite-v5-clean",
            stars = 3,
            actions = 5,
            overloads = 0,
            hintsUsed = 0,
        )

        assertEquals(CelebrationIntensity.EXCELLENT, style.intensity)
        assertEquals(28, style.confettiCount)
        assertTrue(style.emoji.isNotBlank())
        assertTrue(style.message.isNotBlank())
    }

    @Test
    fun `solid and assisted finishes use lighter contextual celebrations`() {
        val recovered = completionCelebrationStyle("level-a", 2, 8, 1, 0)
        val assisted = completionCelebrationStyle("level-b", 2, 8, 0, 1)

        assertEquals(CelebrationIntensity.GOOD, recovered.intensity)
        assertEquals(CelebrationIntensity.GOOD, assisted.intensity)
        assertEquals(16, recovered.confettiCount)
        assertTrue("recovery" in recovered.message.lowercase() || "comeback" in recovered.message.lowercase())
        assertTrue("finish" in assisted.message.lowercase() || "route" in assisted.message.lowercase() ||
            "worked" in assisted.message.lowercase())
    }

    @Test
    fun `rough one-star finish remains calm`() {
        val style = completionCelebrationStyle("level-c", 1, 20, 4, 2)

        assertEquals(CelebrationIntensity.NONE, style.intensity)
        assertEquals(0, style.confettiCount)
        assertTrue(style.emoji.isEmpty())
    }

    @Test
    fun `celebration varies across levels but reproduces for the same result`() {
        val first = completionCelebrationStyle("level-17", 3, 5, 0, 0)
        assertEquals(first, completionCelebrationStyle("level-17", 3, 5, 0, 0))
        val variants = (1..20).map {
            completionCelebrationStyle("level-$it", 3, 5, 0, 0).message
        }.toSet()
        assertTrue(variants.size > 1)
    }
}

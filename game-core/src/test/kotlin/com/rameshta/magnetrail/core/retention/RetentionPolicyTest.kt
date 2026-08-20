package com.rameshta.magnetrail.core.retention

import com.rameshta.magnetrail.core.daily.DailySeed
import com.rameshta.magnetrail.core.daily.StreakPolicy
import com.rameshta.magnetrail.core.daily.StreakState
import com.rameshta.magnetrail.core.economy.EconomyConfig
import com.rameshta.magnetrail.core.economy.EconomySimulation
import com.rameshta.magnetrail.core.economy.RewardPolicy
import com.rameshta.magnetrail.core.grading.GradingPolicy
import com.rameshta.magnetrail.core.model.GradingThresholds
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionPolicyTest {
    private val thresholds = GradingThresholds(4, 6)

    @Test
    fun `star boundaries and hint rule are exact`() {
        assertEquals(3, GradingPolicy.grade(4, 0, 0, thresholds).stars)
        assertEquals(2, GradingPolicy.grade(4, 0, 1, thresholds).stars)
        assertEquals(2, GradingPolicy.grade(6, 2, 0, thresholds).stars)
        assertEquals(1, GradingPolicy.grade(7, 3, 0, thresholds).stars)
    }

    @Test
    fun `campaign rewards are incremental and replay safe`() {
        val first = RewardPolicy.campaignCompletion(150, false, 0, 2)
        assertEquals(160, first.resultingBalance)
        val improved = RewardPolicy.campaignCompletion(first.resultingBalance, true, 2, 3)
        assertEquals(160, improved.resultingBalance)
        val replay = RewardPolicy.campaignCompletion(improved.resultingBalance, true, 3, 3)
        assertEquals(160, replay.resultingBalance)
        assertEquals(0, replay.total)
    }

    @Test
    fun `daily hash has stable golden vectors`() {
        assertEquals(1210663367284340843L, DailySeed.stableHash64("Magnetrail"))
        val identity = DailySeed.identity(LocalDate.of(2026, 8, 19))
        assertEquals("2026-08-19-v1", identity.dailyId)
        assertEquals(5793282614057285746L, identity.seed)
        assertEquals(identity, DailySeed.identity(LocalDate.of(2026, 8, 19)))
    }

    @Test
    fun `streak handles first same next gap and backward dates conservatively`() {
        val day = LocalDate.of(2026, 8, 19)
        val first = StreakPolicy.complete(day, StreakState()).state
        assertEquals(1, first.current)
        val same = StreakPolicy.complete(day, first)
        assertFalse(same.increased)
        assertEquals(first, same.state)
        val next = StreakPolicy.complete(day.plusDays(1), first).state
        assertEquals(2, next.current)
        val backward = StreakPolicy.complete(day.minusDays(2), next)
        assertFalse(backward.increased)
        assertEquals(next, backward.state)
        val gap = StreakPolicy.complete(day.plusDays(4), next).state
        assertEquals(1, gap.current)
        assertEquals(2, gap.best)
    }

    @Test
    fun `economy simulation stays nonnegative without progression blockage`() {
        val results = EconomySimulation.representativeCampaign()
        assertEquals(3, results.size)
        assertTrue(results.all { it.minimumBalance >= 0 })
        assertEquals(0, results.single { it.scenario == "clean-no-hints" }.unaffordableHintRequests)
        assertEquals(0, results.single { it.scenario == "periodic-assistance" }.unaffordableHintRequests)
        assertTrue(results.single { it.scenario == "hint-every-level" }.unaffordableHintRequests > 0)
        assertTrue(results.all { it.finalBalance >= 0 })
    }
}

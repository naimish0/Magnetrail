package com.rameshta.magnetrail

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.GenerationResult
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.daily.DailyChallengeService
import com.rameshta.magnetrail.daily.DailyLoadSource
import com.rameshta.magnetrail.data.DailyCache
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DailyChallengeServiceTest {
    @Test
    fun `same date is deterministic and valid cache is reused`() = runTest {
        val service = service()
        val date = LocalDate.of(2026, 8, 19)
        val first = service.load(date, null)
        val cached = service.load(date, first.cache)

        assertEquals(first.identity, cached.identity)
        assertEquals(ContentFingerprint.of(first.level), ContentFingerprint.of(cached.level))
        assertEquals(DailyLoadSource.CACHE, cached.source)
    }

    @Test
    fun `date change changes identity and corrupt cache is regenerated`() = runTest {
        val service = service()
        val first = service.load(LocalDate.of(2026, 8, 19), null)
        val corrupt = first.cache.copy(contentFingerprint = "sha256:${"0".repeat(64)}")
        val recovered = service.load(LocalDate.of(2026, 8, 19), corrupt)
        val next = service.load(LocalDate.of(2026, 8, 20), first.cache)

        assertNotEquals(DailyLoadSource.CACHE, recovered.source)
        assertEquals(first.level, recovered.level)
        assertNotEquals(first.identity.dailyId, next.identity.dailyId)
    }

    @Test
    fun `bounded generator failure selects deterministic certified fallback`() = runTest {
        val service = service { _, _ -> GenerationResult.Exhausted(48, mapOf("forced-cap" to 48)) }
        val date = LocalDate.of(2026, 8, 21)
        val first = service.load(date, null)
        val second = service.load(date, null)

        assertEquals(DailyLoadSource.BUNDLED_FALLBACK, first.source)
        assertEquals(first.level, second.level)
        assertEquals(first.identity.dailyId, first.level.id)
    }

    private fun service(
        generate: ((List<com.rameshta.magnetrail.core.model.LevelDefinition>, com.rameshta.magnetrail.core.generation.GenerationRequest) -> GenerationResult)? = null,
    ): DailyChallengeService {
        val campaign = catalog("/Magnetrail_Campaign_Levels_v3.json")
        val fallbacks = catalog("/Magnetrail_Daily_Fallbacks_v1.json")
        return if (generate == null) {
            DailyChallengeService(campaign.levels, fallbacks, Dispatchers.Unconfined)
        } else {
            DailyChallengeService(campaign.levels, fallbacks, Dispatchers.Unconfined, generate = generate)
        }
    }

    private fun catalog(path: String) = LevelParser().parseCatalog(
        checkNotNull(javaClass.getResource(path)).readText(),
    )
}

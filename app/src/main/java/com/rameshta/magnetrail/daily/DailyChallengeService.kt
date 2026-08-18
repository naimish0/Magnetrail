package com.rameshta.magnetrail.daily

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.daily.DailyIdentity
import com.rameshta.magnetrail.core.daily.DailySeed
import com.rameshta.magnetrail.core.generation.CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.GENERATOR_VERSION
import com.rameshta.magnetrail.core.generation.GenerationProfile
import com.rameshta.magnetrail.core.generation.GenerationRequest
import com.rameshta.magnetrail.core.generation.GenerationResult
import com.rameshta.magnetrail.core.generation.LevelGenerator
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.data.DailyCache
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

fun interface DateProvider {
    fun currentLocalDate(): LocalDate
}

class SystemDateProvider(
    private val zoneProvider: () -> ZoneId = ZoneId::systemDefault,
) : DateProvider {
    override fun currentLocalDate(): LocalDate = LocalDate.now(zoneProvider())
}

enum class DailyLoadSource {
    CACHE,
    GENERATED,
    BUNDLED_FALLBACK,
}

data class DailyChallenge(
    val identity: DailyIdentity,
    val level: LevelDefinition,
    val source: DailyLoadSource,
    val cache: DailyCache,
    val generationMillis: Long,
)

class DailyChallengeService(
    templates: List<LevelDefinition>,
    private val fallbackCatalog: LevelCatalog,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val parser: LevelParser = LevelParser(),
    private val generate: (List<LevelDefinition>, GenerationRequest) -> GenerationResult = { levels, request ->
        LevelGenerator(levels).generate(request)
    },
) {
    private val templates = templates.toList()

    suspend fun load(date: LocalDate, existingCache: DailyCache?): DailyChallenge = withContext(dispatcher) {
        val started = System.nanoTime()
        val identity = DailySeed.identity(date)
        decodeCache(identity, existingCache)?.let { cached ->
            return@withContext DailyChallenge(
                identity = identity,
                level = cached,
                source = DailyLoadSource.CACHE,
                cache = requireNotNull(existingCache),
                generationMillis = elapsedMillis(started),
            )
        }
        ensureActive()
        val request = GenerationRequest(
                stableId = identity.dailyId,
                sequenceNumber = 1,
                title = "Daily ${identity.localDate}",
                seed = identity.seed,
                profile = GenerationProfile.DAILY_CHALLENGE,
                packId = "daily-challenge",
        )
        val generated = generate(templates, request)
        ensureActive()
        val (level, source) = when (generated) {
            is GenerationResult.Generated -> generated.level to DailyLoadSource.GENERATED
            is GenerationResult.Exhausted -> deterministicFallback(identity) to DailyLoadSource.BUNDLED_FALLBACK
        }
        val catalog = LevelCatalog(
            schemaVersion = 2,
            ruleVersion = "magnetrail-core-1",
            catalogId = "magnetrail-${identity.dailyId}",
            levels = listOf(level),
            contentVersion = CONTENT_VERSION,
            generatorVersion = GENERATOR_VERSION,
        )
        val json = parser.encodeCatalog(catalog)
        val cache = DailyCache(identity.dailyId, ContentFingerprint.of(level), json)
        DailyChallenge(identity, level, source, cache, elapsedMillis(started))
    }

    private fun decodeCache(identity: DailyIdentity, cache: DailyCache?): LevelDefinition? {
        if (cache == null || cache.dailyId != identity.dailyId) return null
        return runCatching {
            val level = parser.parseCatalog(cache.catalogJson).levels.single()
            require(level.id == identity.dailyId)
            require(ContentFingerprint.of(level) == cache.contentFingerprint)
            level
        }.getOrNull()
    }

    private fun deterministicFallback(identity: DailyIdentity): LevelDefinition {
        require(fallbackCatalog.levels.isNotEmpty()) { "Daily fallback bank must not be empty" }
        val index = (identity.seed.ushr(1) % fallbackCatalog.levels.size.toLong()).toInt()
        return fallbackCatalog.levels[index].copy(
            id = identity.dailyId,
            number = 1,
            title = "Daily ${identity.localDate}",
        )
    }

    private fun elapsedMillis(started: Long): Long = (System.nanoTime() - started) / 1_000_000
}

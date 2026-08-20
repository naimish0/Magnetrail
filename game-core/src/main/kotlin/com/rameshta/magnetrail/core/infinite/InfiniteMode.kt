package com.rameshta.magnetrail.core.infinite

import com.rameshta.magnetrail.core.difficulty.v4.DIFFICULTY_V4_ANALYZER_VERSION
import com.rameshta.magnetrail.core.generation.v5.GENERATOR_VERSION_V5
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelValidation
import com.rameshta.magnetrail.core.model.LevelDefinition
import java.security.MessageDigest

const val INFINITE_CATALOG_VERSION = 1
const val INFINITE_SELECTION_VERSION = 3
const val INFINITE_HISTORY_LIMIT = 100
const val INFINITE_RECENT_FINGERPRINT_LIMIT = 16

enum class InfiniteDifficulty(val displayName: String, val explanation: String) {
    PROGRESSIVE(
        "Progressive Journey",
        "Learn with Easy and Medium boards, then enter a varied mix of every certified difficulty.",
    ),
    RELAXED("Easy", "Clear, forgiving relationships with room to explore."),
    BALANCED("Medium", "Mixed dependencies with occasional recovery pressure."),
    CHALLENGING("Hard", "Ordering, polarity, and persistent consequences."),
    VERY_HARD("Super Hard", "Dense consequence chains with limited safe ordering."),
    EXPERT("Expert", "Multi-stage polarity and ordering chains with costly wrong choices."),
    MASTER("Master", "The deepest certified dependency chains and the least room for careless moves."),
}

data class InfinitePuzzleIdentity(
    val generatorVersion: Int,
    val profile: String,
    val seed: Long,
    val contentHash: String,
    val catalogVersion: Int = INFINITE_CATALOG_VERSION,
    val selectionVersion: Int = INFINITE_SELECTION_VERSION,
    val ruleVersion: String = LevelValidation.SUPPORTED_RULE_VERSION,
    val analyzerVersion: String = DIFFICULTY_V4_ANALYZER_VERSION,
) {
    init {
        require(generatorVersion > 0 && profile.isNotBlank() && seed != 0L)
        require(contentHash.matches(Regex("(?:sha256:)?[0-9a-f]{64}")))
    }

    val puzzleId: String
        get() = "infinite-v$generatorVersion-${profile.safeToken()}-$seed-${contentHash.removePrefix("sha256:")}"
}

data class InfiniteSelectionState(
    val ordinal: Int = 0,
    val recentFingerprints: List<String> = emptyList(),
) {
    init {
        require(ordinal >= 0)
    }
}

data class InfiniteSelectionDecision(
    val level: LevelDefinition,
    val identity: InfinitePuzzleIdentity,
    val selectionOrdinal: Int,
    val requestedDifficulty: InfiniteDifficulty,
    val selectedProfile: String,
    val fallbackUsed: Boolean,
    val reason: String,
)

/** Selects only immutable, already-certified catalog entries. It never generates a board. */
class InfiniteCatalogSelector {
    fun select(
        catalog: LevelCatalog,
        difficulty: InfiniteDifficulty,
        state: InfiniteSelectionState,
    ): InfiniteSelectionDecision {
        require(catalog.contentVersion == INFINITE_CATALOG_VERSION)
        val certified = catalog.levels.filter(::isInfiniteCertified)
        require(certified.isNotEmpty()) { "Infinite catalog contains no certified levels" }
        val targetRank = targetRank(difficulty, state.ordinal)
        val exactBand = certified.filter { profileRank(it) == targetRank }
        val recent = state.recentFingerprints.takeLast(INFINITE_RECENT_FINGERPRINT_LIMIT).toSet()
        val desired = exactBand.ifEmpty {
            val minimumDistance = certified.minOf { kotlin.math.abs(profileRank(it) - targetRank) }
            val nearest = certified.filter { kotlin.math.abs(profileRank(it) - targetRank) == minimumDistance }
            // If two bands are equally close, preserve the promised challenge instead of silently
            // making the requested slot easier (notably Super Hard between Hard and Expert).
            val upwardRank = nearest.map(::profileRank).filter { it >= targetRank }.minOrNull()
            upwardRank?.let { rank -> nearest.filter { profileRank(it) == rank } } ?: nearest
        }
        val fresh = desired.filterNot { requireNotNull(it.metadata).contentFingerprint in recent }
        val source = fresh.ifEmpty { desired }
        val selected = source.minWithOrNull(selectionComparator(catalog, difficulty, state.ordinal))
            ?: error("Infinite catalog selection failed")
        val metadata = requireNotNull(selected.metadata)
        val profile = requireNotNull(metadata.generationProfile)
        val identity = identity(selected)
        val fallback = profileRank(selected) != targetRank
        return InfiniteSelectionDecision(
            level = selected,
            identity = identity,
            selectionOrdinal = state.ordinal,
            requestedDifficulty = difficulty,
            selectedProfile = profile,
            fallbackUsed = fallback,
            reason = buildString {
                append("Selected certified ${profile.substringAfterLast('-')} puzzle for ${difficulty.displayName}")
                if (fallback) append(" using the nearest certified fallback band")
                if (fresh.isEmpty()) append(" after the bounded recent-history pool was exhausted")
                append('.')
            },
        )
    }

    fun validateCatalog(catalog: LevelCatalog): List<String> = buildList {
        if (catalog.catalogId != "magnetrail-infinite-v1") add("unexpected-catalog-id")
        if (catalog.contentVersion != INFINITE_CATALOG_VERSION) add("unexpected-content-version")
        if (catalog.generatorVersion != GENERATOR_VERSION_V5) add("unexpected-generator-version")
        if (catalog.levels.isEmpty()) add("empty-catalog")
        catalog.levels.forEach { level ->
            if (!isInfiniteCertified(level)) add("uncertified:${level.id}")
        }
        val ids = catalog.levels.map { it.id }
        if (ids.distinct().size != ids.size) add("duplicate-id")
        val fingerprints = catalog.levels.mapNotNull { it.metadata?.contentFingerprint }
        if (fingerprints.distinct().size != fingerprints.size) add("duplicate-fingerprint")
    }

    fun identity(level: LevelDefinition): InfinitePuzzleIdentity {
        require(isInfiniteCertified(level)) { "Level '${level.id}' is not a certified Infinite entry" }
        val metadata = requireNotNull(level.metadata)
        return InfinitePuzzleIdentity(
            generatorVersion = requireNotNull(metadata.generatorVersion),
            profile = requireNotNull(metadata.generationProfile),
            seed = requireNotNull(metadata.generatorSeed),
            contentHash = metadata.contentFingerprint,
        )
    }

    private fun isInfiniteCertified(level: LevelDefinition): Boolean {
        val metadata = level.metadata ?: return false
        return metadata.packId == "infinite-v1" &&
            metadata.generatorVersion == GENERATOR_VERSION_V5 &&
            metadata.generatorSeed != null &&
            metadata.generationProfile != null &&
            metadata.contentFingerprint.matches(Regex("sha256:[0-9a-f]{64}")) &&
            metadata.certifiedSolutionLength > 0 &&
            level.designedSolutions.isNotEmpty()
    }

    private fun targetRank(difficulty: InfiniteDifficulty, ordinal: Int): Int {
        if (difficulty == InfiniteDifficulty.PROGRESSIVE) return progressiveTargetRank(ordinal)
        val pattern = targetPattern(difficulty)
        return pattern[ordinal % pattern.size]
    }

    /**
     * A deterministic curriculum followed by a varied rhythm. The first mixed block deliberately
     * matches the authored product rhythm; subsequent blocks contain the same balanced ingredients
     * in a seed-stable shuffled order.
     */
    private fun progressiveTargetRank(ordinal: Int): Int = when (ordinal) {
        in 0..9 -> PROFILE_EASY
        in 10..19 -> PROFILE_MEDIUM
        in 20..29 -> PROFILE_HARD
        else -> {
            val mixedOrdinal = ordinal - PROGRESSIVE_CURRICULUM_LENGTH
            val block = mixedOrdinal / PROGRESSIVE_MIX.size
            val index = mixedOrdinal % PROGRESSIVE_MIX.size
            val blockPattern = if (block == 0) {
                PROGRESSIVE_MIX.asList()
            } else {
                PROGRESSIVE_MIX.withIndex()
                    .sortedWith(
                        compareBy<IndexedValue<Int>> {
                            stableHash("progressive-v$INFINITE_SELECTION_VERSION|$block|${it.index}|${it.value}")
                        }.thenBy { it.index },
                    )
                    .map { it.value }
            }
            blockPattern[index]
        }
    }

    private fun targetPattern(difficulty: InfiniteDifficulty): IntArray = when (difficulty) {
        InfiniteDifficulty.PROGRESSIVE -> error("Progressive difficulty uses its curriculum selector")
        InfiniteDifficulty.RELAXED -> intArrayOf(PROFILE_EASY, PROFILE_EASY, PROFILE_MEDIUM, PROFILE_EASY)
        InfiniteDifficulty.BALANCED -> intArrayOf(PROFILE_MEDIUM, PROFILE_EASY, PROFILE_HARD, PROFILE_MEDIUM)
        InfiniteDifficulty.CHALLENGING -> intArrayOf(PROFILE_HARD, PROFILE_MEDIUM, PROFILE_VERY_HARD, PROFILE_HARD)
        InfiniteDifficulty.VERY_HARD -> intArrayOf(PROFILE_VERY_HARD, PROFILE_HARD, PROFILE_EXPERT, PROFILE_VERY_HARD)
        InfiniteDifficulty.EXPERT -> intArrayOf(PROFILE_EXPERT, PROFILE_VERY_HARD, PROFILE_HARD, PROFILE_EXPERT)
        InfiniteDifficulty.MASTER -> intArrayOf(PROFILE_MASTER, PROFILE_EXPERT, PROFILE_MASTER, PROFILE_VERY_HARD)
    }

    private fun profileRank(level: LevelDefinition): Int {
        val profile = level.metadata?.generationProfile.orEmpty()
        return when {
            profile.endsWith("very-hard") -> PROFILE_VERY_HARD
            profile.endsWith("master") -> PROFILE_MASTER
            profile.endsWith("expert") -> PROFILE_EXPERT
            profile.endsWith("hard") -> PROFILE_HARD
            profile.endsWith("medium") -> PROFILE_MEDIUM
            profile.endsWith("easy") -> PROFILE_EASY
            else -> -1
        }
    }

    private fun selectionComparator(
        catalog: LevelCatalog,
        difficulty: InfiniteDifficulty,
        ordinal: Int,
    ): Comparator<LevelDefinition> {
        val stableOrder = compareBy<LevelDefinition> {
            stableHash("${catalog.catalogId}|${catalog.contentVersion}|${difficulty.name}|$ordinal|${it.id}")
        }.thenBy { it.id }
        if (difficulty != InfiniteDifficulty.PROGRESSIVE || ordinal !in 0 until TUTORIAL_LEVEL_COUNT) {
            return stableOrder
        }
        val targetArrows = if (ordinal == TUTORIAL_LEVEL_COUNT - 1) 3 else 2
        return compareBy<LevelDefinition>(
            { kotlin.math.abs(it.arrows.size - targetArrows) },
            { it.metadata?.validFirstActionCount ?: Int.MAX_VALUE },
            { it.metadata?.solutionCount ?: Int.MAX_VALUE },
            { it.magnets.size },
            { it.walls.size },
            { it.metadata?.certifiedSolutionLength ?: Int.MAX_VALUE },
        ).then(stableOrder)
    }

    private fun stableHash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val PROFILE_EASY = 1
        const val PROFILE_MEDIUM = 2
        const val PROFILE_HARD = 3
        const val PROFILE_VERY_HARD = 4
        const val PROFILE_EXPERT = 5
        const val PROFILE_MASTER = 6
        const val PROGRESSIVE_CURRICULUM_LENGTH = 30
        const val TUTORIAL_LEVEL_COUNT = 5
        val PROGRESSIVE_MIX = intArrayOf(
            PROFILE_HARD,
            PROFILE_VERY_HARD,
            PROFILE_EASY,
            PROFILE_EXPERT,
            PROFILE_MEDIUM,
            PROFILE_EASY,
            PROFILE_MASTER,
            PROFILE_HARD,
            PROFILE_MEDIUM,
        )
    }
}

private fun String.safeToken(): String = lowercase().replace(Regex("[^a-z0-9-]"), "-")

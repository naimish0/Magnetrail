package com.rameshta.magnetrail.infinite

import com.rameshta.magnetrail.core.infinite.INFINITE_RECENT_FINGERPRINT_LIMIT
import com.rameshta.magnetrail.core.infinite.InfiniteCatalogSelector
import com.rameshta.magnetrail.core.infinite.InfiniteDifficulty
import com.rameshta.magnetrail.core.infinite.InfiniteSelectionDecision
import com.rameshta.magnetrail.core.infinite.InfiniteSelectionState
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.data.InfiniteProgress

/** Runtime access to an immutable, offline-certified catalog. No board generation occurs here. */
class InfiniteModeService(
    private val catalog: LevelCatalog,
    private val selector: InfiniteCatalogSelector = InfiniteCatalogSelector(),
) {
    val catalogSize: Int get() = catalog.levels.size

    init {
        val errors = selector.validateCatalog(catalog)
        require(errors.isEmpty()) { "Invalid Infinite catalog: ${errors.joinToString()}" }
    }

    /** Exact player-facing band that the Home Play action will resume or select next. */
    fun previewProgressiveDifficulty(progress: InfiniteProgress): String = profileDisplayName(
        resumeOrSelect(InfiniteDifficulty.PROGRESSIVE, progress).selectedProfile,
    )

    fun resumeOrSelect(
        difficulty: InfiniteDifficulty,
        progress: InfiniteProgress,
    ): InfiniteSelectionDecision {
        val resumable = progress.selectedPuzzleId
            ?.takeIf { progress.selectedDifficulty == difficulty.name }
            ?.takeIf { id -> progress.history.none { it.puzzleId == id && it.completed } }
            ?.let(::find)
        if (resumable != null) {
            val identity = selector.identity(resumable)
            return InfiniteSelectionDecision(
                level = resumable,
                identity = identity,
                selectionOrdinal = progress.selectionOrdinal,
                requestedDifficulty = difficulty,
                selectedProfile = identity.profile,
                fallbackUsed = !matchesDifficulty(identity.profile, difficulty),
                reason = "Resumed the same certified Infinite puzzle from local progress.",
            )
        }
        return selectNew(difficulty, progress, advance = progress.selectedPuzzleId != null)
    }

    fun selectNew(
        difficulty: InfiniteDifficulty,
        progress: InfiniteProgress,
        advance: Boolean = true,
    ): InfiniteSelectionDecision {
        val ordinal = (progress.selectionOrdinal + if (advance) 1 else 0).coerceAtLeast(0)
        return selector.select(
            catalog = catalog,
            difficulty = difficulty,
            state = InfiniteSelectionState(
                ordinal = ordinal,
                recentFingerprints = progress.history
                    .sortedBy { it.ordinal }
                    .takeLast(INFINITE_RECENT_FINGERPRINT_LIMIT)
                    .map { it.contentFingerprint },
            ),
        )
    }

    private fun find(puzzleId: String) = catalog.levels.firstOrNull {
        selector.identity(it).puzzleId == puzzleId
    }

    private fun matchesDifficulty(profile: String, difficulty: InfiniteDifficulty): Boolean = when (difficulty) {
        InfiniteDifficulty.PROGRESSIVE -> true
        InfiniteDifficulty.RELAXED -> profile.endsWith("-easy")
        InfiniteDifficulty.BALANCED -> profile.endsWith("-medium")
        InfiniteDifficulty.CHALLENGING -> profile.endsWith("-hard") && !profile.endsWith("-very-hard")
        InfiniteDifficulty.VERY_HARD -> profile.endsWith("-very-hard")
        InfiniteDifficulty.EXPERT -> profile.endsWith("-expert")
        InfiniteDifficulty.MASTER -> profile.endsWith("-master")
    }

    private fun profileDisplayName(profile: String): String = when {
        profile.endsWith("-very-hard") -> InfiniteDifficulty.VERY_HARD.displayName
        profile.endsWith("-master") -> InfiniteDifficulty.MASTER.displayName
        profile.endsWith("-expert") -> InfiniteDifficulty.EXPERT.displayName
        profile.endsWith("-hard") -> InfiniteDifficulty.CHALLENGING.displayName
        profile.endsWith("-medium") -> InfiniteDifficulty.BALANCED.displayName
        profile.endsWith("-easy") -> InfiniteDifficulty.RELAXED.displayName
        else -> "Certified"
    }
}

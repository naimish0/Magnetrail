package com.rameshta.magnetrail.levels

import com.rameshta.magnetrail.core.generation.v5.GENERATOR_VERSION_V5
import com.rameshta.magnetrail.core.generation.v5.LogicalLevelIdentityV5
import com.rameshta.magnetrail.core.generation.v5.TopologyFamilyV5
import com.rameshta.magnetrail.core.model.LevelDefinition

data class LevelSelectionMetadata(
    val number: Int,
    val stableId: String,
    val title: String,
    val packId: String,
    val difficultyLabel: String,
    val logicalIdentity: LogicalLevelIdentityV5? = null,
)

/** Metadata-only index. Reading a range never realizes or solves a puzzle board. */
fun interface LevelMetadataResolver {
    fun metadataAt(zeroBasedIndex: Int): LevelSelectionMetadata
}

class PagedLevelMetadataIndex(
    val totalLevels: Int,
    private val resolver: LevelMetadataResolver,
) {
    init {
        require(totalLevels >= 0)
    }

    fun range(pageIndex: Int, pageSize: Int = LEVEL_RANGE_SIZE): List<LevelSelectionMetadata> {
        val window = LevelRangeNavigator.window(pageIndex, totalLevels, pageSize)
        return (window.startIndex until window.endIndexExclusive).map(resolver::metadataAt)
    }
}

fun authoredLevelMetadataIndex(levels: List<LevelDefinition>): PagedLevelMetadataIndex =
    PagedLevelMetadataIndex(levels.size) { index ->
        val level = levels[index]
        LevelSelectionMetadata(
            number = level.number,
            stableId = level.id,
            title = level.title,
            packId = level.metadata?.packId ?: "field-basics",
            difficultyLabel = level.metadata?.difficultyBand?.name ?: "INTRO",
        )
    }

/** Creates only deterministic identities/titles for the requested range—never puzzle content. */
fun logicalGeneratedMetadataIndex(
    totalLevels: Int,
    profileForLevel: (Int) -> String,
    topologyForLevel: (Int) -> TopologyFamilyV5,
    seedForLevel: (Int) -> Long,
): PagedLevelMetadataIndex = PagedLevelMetadataIndex(totalLevels) { index ->
    val number = index + 1
    val profile = profileForLevel(number)
    val identity = LogicalLevelIdentityV5(
        levelNumber = number,
        profileId = profile,
        generatorVersion = GENERATOR_VERSION_V5,
        seed = seedForLevel(number),
        topologyFamily = topologyForLevel(number),
    )
    LevelSelectionMetadata(
        number = number,
        stableId = identity.stableId,
        title = "Level $number",
        packId = "generated-${(index / LEVEL_RANGE_SIZE) + 1}",
        difficultyLabel = profile,
        logicalIdentity = identity,
    )
}

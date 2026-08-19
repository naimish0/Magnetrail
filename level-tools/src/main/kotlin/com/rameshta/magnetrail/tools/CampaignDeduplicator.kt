package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.CertificationPipeline
import com.rameshta.magnetrail.core.generation.CertificationRequest
import com.rameshta.magnetrail.core.generation.CertificationResult
import com.rameshta.magnetrail.core.generation.GenerationProfile
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.DifficultyBand
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import java.io.File

/** Explicit staging-only repair for hard symmetry duplicates found by the first M5.1 audit. */
internal fun stageSymmetryDeduplication(options: Map<String, String>) {
    val input = File(requireNotNull(options["campaign"]) { "Missing --campaign=..." })
    val output = File(requireNotNull(options["output"]) { "Missing --output=..." })
    val report = File(requireNotNull(options["report"]) { "Missing --report=..." })
    val catalog = LevelParser().parseCatalog(input.readText())
    val pipeline = CertificationPipeline()
    val acceptedFingerprints = mutableSetOf<String>()
    val originalOtherFingerprints = catalog.levels.associate { level ->
        level.id to ContentFingerprint.symmetryNormalized(level)
    }
    val changes = mutableListOf<DeduplicationChange>()
    val tuned = catalog.levels.sortedBy { it.number }.map { original ->
        val originalFingerprint = ContentFingerprint.symmetryNormalized(original)
        if (acceptedFingerprints.add(originalFingerprint)) return@map original

        val forbidden = buildSet {
            addAll(acceptedFingerprints)
            originalOtherFingerprints.filterKeys { it != original.id }.values.forEach(::add)
        }
        val candidates = deterministicWallTunings(original)
        val selected = candidates.firstNotNullOfOrNull { candidate ->
            val request = candidateRequest(original)
            val result = pipeline.certify(candidate.copy(metadata = null), request) as? CertificationResult.Accepted
                ?: return@firstNotNullOfOrNull null
            val symmetryFingerprint = ContentFingerprint.symmetryNormalized(result.level)
            result.takeIf { symmetryFingerprint !in forbidden }
        } ?: error("No bounded wall-only symmetry repair passed certification for ${original.id}")

        val tunedLevel = selected.level
        acceptedFingerprints += ContentFingerprint.symmetryNormalized(tunedLevel)
        changes += DeduplicationChange(
            levelId = original.id,
            number = original.number,
            beforeFingerprint = originalFingerprint,
            afterFingerprint = ContentFingerprint.symmetryNormalized(tunedLevel),
            beforeWalls = original.walls.map { it.position },
            afterWalls = tunedLevel.walls.map { it.position },
        )
        tunedLevel
    }
    check(tuned.size == catalog.levels.size)
    check(tuned.map(ContentFingerprint::symmetryNormalized).toSet().size == tuned.size) {
        "Symmetry deduplication did not produce a unique campaign"
    }
    val staged = LevelCatalog(
        schemaVersion = catalog.schemaVersion,
        ruleVersion = catalog.ruleVersion,
        catalogId = catalog.catalogId,
        levels = tuned,
        contentVersion = catalog.contentVersion,
        generatorVersion = catalog.generatorVersion,
    )
    output.parentFile.mkdirs()
    output.writeText(LevelParser().encodeCatalog(staged))
    report.parentFile.mkdirs()
    report.writeText(buildString {
        appendLine("# M5.1 staged symmetry repairs")
        appendLine()
        appendLine("Generated only after the first M5.1 audit identified hard D4-equivalent duplicates. The earliest campaign member of each group was preserved. Later members received the first deterministic wall-only adjustment that passed the production certification pipeline and remained unique under symmetry.")
        appendLine()
        appendLine("- Stable IDs changed: 0")
        appendLine("- Campaign numbers/order changed: 0")
        appendLine("- Boards tuned: ${changes.size}")
        appendLine()
        appendLine("| # | ID | Walls before | Walls after | Symmetry fingerprint before | Symmetry fingerprint after |")
        appendLine("|---:|---|---|---|---|---|")
        changes.forEach { change ->
            appendLine(
                "| ${change.number} | ${change.levelId} | ${change.beforeWalls.joinToString()} | " +
                    "${change.afterWalls.joinToString()} | `${change.beforeFingerprint}` | `${change.afterFingerprint}` |",
            )
        }
    })
    println("Staged ${changes.size} certified wall-only repairs; shipped campaign was not modified.")
}

private fun deterministicWallTunings(level: LevelDefinition): Sequence<LevelDefinition> = sequence {
    val occupied = (level.arrows.map { it.position } + level.magnets.map { it.position } + level.walls.map { it.position })
        .toSet()
    val empty = buildList {
        for (row in 1..level.height) for (column in 1..level.width) {
            val cell = Position(row, column)
            if (cell !in occupied) add(cell)
        }
    }
    val profile = profileForDeduplication(level)
    if (level.walls.size < profile.maxWalls) {
        empty.forEach { cell -> yield(level.copy(walls = level.walls + Wall(cell))) }
    }
    level.walls.indices.forEach { wallIndex ->
        empty.forEach { cell ->
            yield(level.copy(walls = level.walls.mapIndexed { index, wall ->
                if (index == wallIndex) Wall(cell) else wall
            }))
        }
    }
}

private fun candidateRequest(level: LevelDefinition): CertificationRequest {
    val metadata = requireNotNull(level.metadata) { "${level.id} has no certification metadata" }
    return CertificationRequest(
        profile = profileForDeduplication(level),
        origin = metadata.origin,
        packId = metadata.packId,
        generatorVersion = metadata.generatorVersion,
        generatorSeed = metadata.generatorSeed,
        generationProfile = metadata.generationProfile,
        contentVersion = metadata.contentVersion,
    )
}

private fun profileForDeduplication(level: LevelDefinition): GenerationProfile {
    level.metadata?.generationProfile?.let { profileId ->
        return GenerationProfile.entries.single { it.profileId == profileId }
    }
    return when {
        level.number <= 9 || level.arrows.size == 1 -> GenerationProfile.INTRO_EASY
        level.arrows.size >= 4 || level.metadata?.difficultyBand == DifficultyBand.ADVANCED ->
            GenerationProfile.ADVANCED_HARD
        else -> GenerationProfile.DEVELOPING_MEDIUM
    }
}

private data class DeduplicationChange(
    val levelId: String,
    val number: Int,
    val beforeFingerprint: String,
    val afterFingerprint: String,
    val beforeWalls: List<Position>,
    val afterWalls: List<Position>,
)

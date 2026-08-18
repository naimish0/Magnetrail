package com.rameshta.magnetrail.core.level

import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.core.content.ContentFingerprint

class LevelValidationException(message: String) : IllegalArgumentException(message)

object LevelValidation {
    const val SUPPORTED_SCHEMA_VERSION = 1
    const val M3_SCHEMA_VERSION = 2
    const val SUPPORTED_RULE_VERSION = "magnetrail-core-1"

    internal fun toDomain(catalog: LevelCatalogDto): LevelCatalog {
        validateCatalog(catalog)
        return LevelCatalog(
            schemaVersion = catalog.schemaVersion,
            ruleVersion = catalog.ruleVersion,
            catalogId = catalog.catalogId,
            levels = catalog.levels.map(::toDomain),
            contentVersion = catalog.contentVersion ?: 1,
            generatorVersion = catalog.generatorVersion,
        )
    }

    private fun validateCatalog(catalog: LevelCatalogDto) {
        failUnless(catalog.schemaVersion in setOf(SUPPORTED_SCHEMA_VERSION, M3_SCHEMA_VERSION)) {
            "Unsupported schemaVersion ${catalog.schemaVersion}; expected 1 or 2"
        }
        failUnless(catalog.ruleVersion == SUPPORTED_RULE_VERSION) {
            "Unsupported ruleVersion '${catalog.ruleVersion}'; expected '$SUPPORTED_RULE_VERSION'"
        }
        failUnless(catalog.catalogId.isNotBlank()) { "catalogId must not be blank" }
        failUnless(catalog.levels.isNotEmpty()) { "Catalog '${catalog.catalogId}' must contain at least one level" }
        rejectDuplicates(catalog.levels.map { it.id }, "level ID")
        rejectDuplicates(catalog.levels.map { it.number }, "level number")
        catalog.levels.forEach(::validateLevel)
        if (catalog.schemaVersion == M3_SCHEMA_VERSION) {
            failUnless((catalog.contentVersion ?: 0) > 0) {
                "schemaVersion 2 requires a positive contentVersion"
            }
            failUnless((catalog.generatorVersion ?: 0) > 0) {
                "schemaVersion 2 requires a positive generatorVersion"
            }
            catalog.levels.forEach { level ->
                failUnless(level.metadata != null) { "Level '${level.id}' requires M3 certification metadata" }
            }
        }
    }

    private fun validateLevel(level: LevelDto) {
        val prefix = "Level '${level.id}'"
        failUnless(level.id.isNotBlank()) { "Level ID must not be blank" }
        failUnless(level.number > 0) { "$prefix number must be positive" }
        failUnless(level.title.isNotBlank()) { "$prefix title must not be blank" }
        failUnless(level.width > 0 && level.height > 0) {
            "$prefix dimensions must be positive, but were ${level.width}x${level.height}"
        }
        failUnless(level.arrows.isNotEmpty()) { "$prefix must contain at least one arrow" }

        val entityIds = level.arrows.map { it.id } + level.magnets.map { it.id }
        failUnless(entityIds.all { it.isNotBlank() }) { "$prefix contains a blank entity ID" }
        rejectDuplicates(entityIds, "entity ID", prefix)

        level.arrows.forEach { arrow ->
            validatePosition(prefix, "arrow '${arrow.id}'", arrow.row, arrow.column, level)
            parseDirection(prefix, arrow)
        }
        level.magnets.forEach { magnet ->
            validatePosition(prefix, "magnet '${magnet.id}'", magnet.row, magnet.column, level)
            parsePolarity(prefix, magnet)
        }
        level.walls.forEachIndexed { index, wall ->
            validatePosition(prefix, "wall ${index + 1}", wall.row, wall.column, level)
        }

        val occupiedCells = buildList {
            level.arrows.forEach { add("arrow '${it.id}'" to Position(it.row, it.column)) }
            level.magnets.forEach { add("magnet '${it.id}'" to Position(it.row, it.column)) }
            level.walls.forEachIndexed { index, wall -> add("wall ${index + 1}" to Position(wall.row, wall.column)) }
        }
        occupiedCells.groupBy { it.second }.entries.firstOrNull { it.value.size > 1 }?.let { duplicate ->
            throw LevelValidationException(
                "$prefix has multiple entities at ${duplicate.key}: ${duplicate.value.joinToString { it.first }}",
            )
        }

        failUnless(level.designedSolutions.isNotEmpty()) { "$prefix must define at least one designed solution" }
        val arrowIds = level.arrows.map { it.id }.toSet()
        level.designedSolutions.forEachIndexed { index, solution ->
            failUnless(solution.size == arrowIds.size && solution.toSet() == arrowIds) {
                "$prefix designed solution ${index + 1} must contain every arrow ID exactly once"
            }
        }
    }

    private fun toDomain(level: LevelDto): LevelDefinition {
        val domain = LevelDefinition(
            id = level.id,
            number = level.number,
            title = level.title,
            width = level.width,
            height = level.height,
            arrows = level.arrows.map { arrow ->
                Arrow(
                    id = arrow.id,
                    position = Position(arrow.row, arrow.column),
                    printedDirection = parseDirection("Level '${level.id}'", arrow),
                )
            },
            magnets = level.magnets.map { magnet ->
                Magnet(
                    id = magnet.id,
                    position = Position(magnet.row, magnet.column),
                    polarity = parsePolarity("Level '${level.id}'", magnet),
                )
            },
            walls = level.walls.map { Wall(Position(it.row, it.column)) },
            designedSolutions = level.designedSolutions.map { it.toList() },
            metadata = try {
                level.metadata?.toDomain()
            } catch (error: IllegalArgumentException) {
                throw LevelValidationException("Level '${level.id}' has invalid metadata: ${error.message}")
            },
        )
        domain.metadata?.let { metadata ->
            val actualFingerprint = ContentFingerprint.of(domain)
            failUnless(metadata.contentFingerprint == actualFingerprint) {
                "Level '${level.id}' content-hash mismatch: expected ${metadata.contentFingerprint}, actual $actualFingerprint"
            }
            failUnless(metadata.certifiedSolutionLength == domain.arrows.size) {
                "Level '${level.id}' certifiedSolutionLength must equal its arrow count"
            }
            failUnless(metadata.grading.parActions == metadata.certifiedSolutionLength) {
                "Level '${level.id}' parActions must equal certifiedSolutionLength"
            }
        }
        return domain
    }

    private fun validatePosition(
        prefix: String,
        label: String,
        row: Int,
        column: Int,
        level: LevelDto,
    ) {
        failUnless(row in 1..level.height && column in 1..level.width) {
            "$prefix $label at ($row, $column) is outside the ${level.width}x${level.height} board"
        }
    }

    private fun parseDirection(prefix: String, arrow: ArrowDto): Direction = try {
        Direction.fromCode(arrow.printedDirection)
    } catch (error: IllegalArgumentException) {
        throw LevelValidationException("$prefix arrow '${arrow.id}' has invalid printedDirection '${arrow.printedDirection}'")
    }

    private fun parsePolarity(prefix: String, magnet: MagnetDto): Polarity = try {
        Polarity.fromName(magnet.initialPolarity)
    } catch (error: IllegalArgumentException) {
        throw LevelValidationException("$prefix magnet '${magnet.id}' has invalid initialPolarity '${magnet.initialPolarity}'")
    }

    private fun <T> rejectDuplicates(values: List<T>, label: String, prefix: String? = null) {
        values.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let { duplicate ->
            val context = prefix?.let { "$it has" } ?: "Catalog has"
            throw LevelValidationException("$context duplicate $label '${duplicate.key}'")
        }
    }

    private inline fun failUnless(condition: Boolean, message: () -> String) {
        if (!condition) throw LevelValidationException(message())
    }
}

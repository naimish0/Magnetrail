package com.rameshta.magnetrail.core.content

import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Position
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ContentFingerprint {
    /** Existing orientation-sensitive hash retained for shipped metadata compatibility. */
    fun of(level: LevelDefinition): String = "sha256:${sha256Hex(canonicalBoard(level))}"

    fun exact(level: LevelDefinition): String = of(level)

    /** Smallest full board serialization under every dimension-preserving board symmetry. */
    fun symmetryNormalized(level: LevelDefinition): String =
        "sha256:${sha256Hex(canonicalSymmetryBoard(level))}"

    fun canonicalSymmetryBoard(level: LevelDefinition): String = validSymmetries(level)
        .map { symmetry -> transformedCanonicalBoard(level, symmetry) }
        .min()

    /**
     * Smallest arrow-position silhouette under every valid board symmetry. Directions are
     * intentionally omitted: two boards that only repaint the same reflected arrangement still
     * look like the same setup to a player scanning the campaign.
     */
    fun arrowLayoutSymmetryNormalized(level: LevelDefinition): String =
        "sha256:${sha256Hex(canonicalArrowLayout(level))}"

    fun canonicalArrowLayout(level: LevelDefinition): String = validSymmetries(level).map { symmetry ->
        buildString {
            append("magnetrail-arrow-layout-2|")
                .append(symmetry.outputWidth(level.width, level.height)).append('x')
                .append(symmetry.outputHeight(level.width, level.height))
            append("|arrows=")
            level.arrows.map { symmetry.transform(it.position, level.width, level.height) }
                .sortedWith(compareBy(Position::row, Position::column))
                .forEach { append(position(it)).append(';') }
        }
    }.min()

    /**
     * D4-normalized interactive layout without walls. This prevents wall-only variation from
     * disguising a repeated arrow-and-magnet puzzle skeleton.
     */
    fun interactiveLayoutSymmetryNormalized(level: LevelDefinition): String =
        "sha256:${sha256Hex(canonicalInteractiveLayout(level))}"

    fun canonicalInteractiveLayout(level: LevelDefinition): String = validSymmetries(level).map { symmetry ->
        buildString {
            append("magnetrail-interactive-layout-2|")
                .append(symmetry.outputWidth(level.width, level.height)).append('x')
                .append(symmetry.outputHeight(level.width, level.height))
            append("|arrows=")
            level.arrows.map { arrow ->
                symmetry.transform(arrow.position, level.width, level.height) to
                    symmetry.transform(arrow.printedDirection)
            }.sortedWith(compareBy({ it.first.row }, { it.first.column }, { it.second.name }))
                .forEach { (cell, direction) ->
                    append(position(cell)).append(':').append(direction.code).append(';')
                }
            append("|magnets=")
            level.magnets.map { magnet ->
                symmetry.transform(magnet.position, level.width, level.height) to magnet.polarity
            }.sortedWith(compareBy({ it.first.row }, { it.first.column }, { it.second.name }))
                .forEach { (cell, polarity) ->
                    append(position(cell)).append(':').append(polarity.name).append(';')
                }
        }
    }.min()

    /** Review-only coarse shape key; never used as a hard rejection by itself. */
    fun structuralSimilaritySignature(level: LevelDefinition): String {
        val canonical = validSymmetries(level).map { symmetry ->
            buildString {
                append("magnetrail-similarity-2|")
                    .append(symmetry.outputWidth(level.width, level.height)).append('x')
                    .append(symmetry.outputHeight(level.width, level.height))
                append("|arrows=")
                level.arrows.map { symmetry.transform(it.position, level.width, level.height) }
                    .sortedWith(compareBy(Position::row, Position::column))
                    .forEach { append(position(it)).append(';') }
                append("|magnets=")
                level.magnets.map { symmetry.transform(it.position, level.width, level.height) to it.polarity }
                    .sortedWith(compareBy({ it.first.row }, { it.first.column }, { it.second.name }))
                    .forEach { append(position(it.first)).append(':').append(it.second.name).append(';') }
                append("|wallCount=").append(level.walls.size)
            }
        }.min()
        return "sha256:${sha256Hex(canonical)}"
    }

    /**
     * Coarse D4-normalized visual template. Positions are folded into a 3x3 perceptual grid and
     * retain object type plus arrow-direction counts. This intentionally collides layouts that
     * differ in a few coordinates but present the same large-scale composition to a player.
     */
    fun perceptualTemplateSignature(level: LevelDefinition): String {
        val canonical = validSymmetries(level).map { symmetry ->
            val transformedWidth = symmetry.outputWidth(level.width, level.height)
            val transformedHeight = symmetry.outputHeight(level.width, level.height)
            val arrowCells = level.arrows.groupingBy { arrow ->
                val position = symmetry.transform(arrow.position, level.width, level.height)
                val direction = symmetry.transform(arrow.printedDirection)
                "${coarse(position.row, transformedHeight)},${coarse(position.column, transformedWidth)}:${direction.code}"
            }.eachCount().toSortedMap()
            val magnetCells = level.magnets.groupingBy { magnet ->
                val position = symmetry.transform(magnet.position, level.width, level.height)
                "${coarse(position.row, transformedHeight)},${coarse(position.column, transformedWidth)}:${magnet.polarity.name}"
            }.eachCount().toSortedMap()
            val wallCells = level.walls.groupingBy { wall ->
                val position = symmetry.transform(wall.position, level.width, level.height)
                "${coarse(position.row, transformedHeight)},${coarse(position.column, transformedWidth)}"
            }.eachCount().toSortedMap()
            buildString {
                append("magnetrail-perceptual-template-2|").append(transformedWidth).append('x').append(transformedHeight)
                append("|counts=").append(level.arrows.size).append(',').append(level.magnets.size)
                    .append(',').append(level.walls.size)
                append("|arrows=").append(arrowCells.entries.joinToString(";") { "${it.key}=${it.value}" })
                append("|magnets=").append(magnetCells.entries.joinToString(";") { "${it.key}=${it.value}" })
                append("|walls=").append(wallCells.entries.joinToString(";") { "${it.key}=${it.value}" })
            }
        }.min()
        return "sha256:${sha256Hex(canonical)}"
    }

    /** Maximum arrow-position Jaccard similarity under every valid board symmetry. */
    fun arrowVisualSimilarity(first: LevelDefinition, second: LevelDefinition): Double =
        visualSimilarity(first, second) { level, symmetry ->
            level.arrows.mapTo(hashSetOf()) { arrow ->
                val position = symmetry.transform(arrow.position, level.width, level.height)
                "A:${position.row},${position.column}"
            }
        }

    /** Maximum typed-object-position Jaccard similarity under every valid board symmetry. */
    fun objectVisualSimilarity(first: LevelDefinition, second: LevelDefinition): Double =
        visualSimilarity(first, second) { level, symmetry ->
            buildSet {
                level.arrows.forEach { arrow ->
                    val position = symmetry.transform(arrow.position, level.width, level.height)
                    add("A:${position.row},${position.column}")
                }
                level.magnets.forEach { magnet ->
                    val position = symmetry.transform(magnet.position, level.width, level.height)
                    add("M:${position.row},${position.column}")
                }
                level.walls.forEach { wall ->
                    val position = symmetry.transform(wall.position, level.width, level.height)
                    add("W:${position.row},${position.column}")
                }
            }
        }

    fun canonicalBoard(level: LevelDefinition): String = buildString {
        append("magnetrail-core-1|")
        append(level.width).append('x').append(level.height)
        append("|arrows=")
        level.arrows
            .sortedWith(compareBy({ it.position.row }, { it.position.column }, { it.printedDirection.name }))
            .forEach { arrow ->
                append(position(arrow.position)).append(':').append(arrow.printedDirection.code).append(';')
            }
        append("|magnets=")
        level.magnets
            .sortedWith(compareBy({ it.position.row }, { it.position.column }, { it.polarity.name }))
            .forEach { magnet ->
                append(position(magnet.position)).append(':').append(magnet.polarity.name).append(';')
            }
        append("|walls=")
        level.walls.map { it.position }
            .sortedWith(compareBy(Position::row, Position::column))
            .forEach { wall -> append(position(wall)).append(';') }
    }

    fun sha256Hex(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun position(position: Position): String = "${position.row},${position.column}"

    private fun coarse(value: Int, extent: Int): Int = ((value - 1) * 3 / extent).coerceIn(0, 2)

    private fun visualSimilarity(
        first: LevelDefinition,
        second: LevelDefinition,
        objects: (LevelDefinition, BoardSymmetry) -> Set<String>,
    ): Double {
        val firstObjects = objects(first, BoardSymmetry.IDENTITY)
        val compatible = validSymmetries(second).filter { symmetry ->
            symmetry.outputWidth(second.width, second.height) == first.width &&
                symmetry.outputHeight(second.width, second.height) == first.height
        }
        if (compatible.isEmpty()) return 0.0
        return compatible.maxOf { symmetry ->
            val secondObjects = objects(second, symmetry)
            val union = firstObjects union secondObjects
            if (union.isEmpty()) 1.0 else (firstObjects intersect secondObjects).size.toDouble() / union.size
        }
    }

    private fun validSymmetries(@Suppress("UNUSED_PARAMETER") level: LevelDefinition): List<BoardSymmetry> =
        BoardSymmetry.entries

    private fun transformedCanonicalBoard(level: LevelDefinition, symmetry: BoardSymmetry): String = buildString {
        append("magnetrail-symmetry-2|")
        append(symmetry.outputWidth(level.width, level.height)).append('x')
            .append(symmetry.outputHeight(level.width, level.height))
        append("|arrows=")
        level.arrows.map { arrow ->
            symmetry.transform(arrow.position, level.width, level.height) to symmetry.transform(arrow.printedDirection)
        }.sortedWith(compareBy({ it.first.row }, { it.first.column }, { it.second.name })).forEach { (cell, direction) ->
            append(position(cell)).append(':').append(direction.code).append(';')
        }
        append("|magnets=")
        level.magnets.map { magnet ->
            symmetry.transform(magnet.position, level.width, level.height) to magnet.polarity
        }.sortedWith(compareBy({ it.first.row }, { it.first.column }, { it.second.name })).forEach { (cell, polarity) ->
            append(position(cell)).append(':').append(polarity.name).append(';')
        }
        append("|walls=")
        level.walls.map { symmetry.transform(it.position, level.width, level.height) }
            .sortedWith(compareBy(Position::row, Position::column))
            .forEach { append(position(it)).append(';') }
    }
}

enum class BoardSymmetry {
    IDENTITY,
    ROTATE_90,
    ROTATE_180,
    ROTATE_270,
    REFLECT_HORIZONTAL,
    REFLECT_VERTICAL,
    REFLECT_MAIN_DIAGONAL,
    REFLECT_ANTI_DIAGONAL,
    ;

    fun outputWidth(width: Int, height: Int): Int = when (this) {
        ROTATE_90, ROTATE_270, REFLECT_MAIN_DIAGONAL, REFLECT_ANTI_DIAGONAL -> height
        else -> width
    }

    fun outputHeight(width: Int, height: Int): Int = when (this) {
        ROTATE_90, ROTATE_270, REFLECT_MAIN_DIAGONAL, REFLECT_ANTI_DIAGONAL -> width
        else -> height
    }

    fun transform(position: Position, width: Int, height: Int): Position = when (this) {
        IDENTITY -> position
        ROTATE_90 -> Position(position.column, height + 1 - position.row)
        ROTATE_180 -> Position(height + 1 - position.row, width + 1 - position.column)
        ROTATE_270 -> Position(width + 1 - position.column, position.row)
        REFLECT_HORIZONTAL -> Position(height + 1 - position.row, position.column)
        REFLECT_VERTICAL -> Position(position.row, width + 1 - position.column)
        REFLECT_MAIN_DIAGONAL -> Position(position.column, position.row)
        REFLECT_ANTI_DIAGONAL -> Position(width + 1 - position.column, height + 1 - position.row)
    }

    fun transform(direction: Direction): Direction = when (this) {
        IDENTITY -> direction
        ROTATE_90 -> when (direction) {
            Direction.NORTH -> Direction.EAST
            Direction.EAST -> Direction.SOUTH
            Direction.SOUTH -> Direction.WEST
            Direction.WEST -> Direction.NORTH
        }
        ROTATE_180 -> direction.opposite()
        ROTATE_270 -> when (direction) {
            Direction.NORTH -> Direction.WEST
            Direction.EAST -> Direction.NORTH
            Direction.SOUTH -> Direction.EAST
            Direction.WEST -> Direction.SOUTH
        }
        REFLECT_HORIZONTAL -> when (direction) {
            Direction.NORTH -> Direction.SOUTH
            Direction.SOUTH -> Direction.NORTH
            else -> direction
        }
        REFLECT_VERTICAL -> when (direction) {
            Direction.EAST -> Direction.WEST
            Direction.WEST -> Direction.EAST
            else -> direction
        }
        REFLECT_MAIN_DIAGONAL -> when (direction) {
            Direction.NORTH -> Direction.WEST
            Direction.EAST -> Direction.SOUTH
            Direction.SOUTH -> Direction.EAST
            Direction.WEST -> Direction.NORTH
        }
        REFLECT_ANTI_DIAGONAL -> when (direction) {
            Direction.NORTH -> Direction.EAST
            Direction.EAST -> Direction.NORTH
            Direction.SOUTH -> Direction.WEST
            Direction.WEST -> Direction.SOUTH
        }
    }
}

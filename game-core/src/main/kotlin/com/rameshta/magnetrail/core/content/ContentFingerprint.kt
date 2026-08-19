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

    /** Review-only coarse shape key; never used as a hard rejection by itself. */
    fun structuralSimilaritySignature(level: LevelDefinition): String {
        val canonical = validSymmetries(level).map { symmetry ->
            buildString {
                append("magnetrail-similarity-1|").append(level.width).append('x').append(level.height)
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

    private fun validSymmetries(level: LevelDefinition): List<BoardSymmetry> = if (level.width == level.height) {
        BoardSymmetry.entries
    } else {
        listOf(
            BoardSymmetry.IDENTITY,
            BoardSymmetry.ROTATE_180,
            BoardSymmetry.REFLECT_HORIZONTAL,
            BoardSymmetry.REFLECT_VERTICAL,
        )
    }

    private fun transformedCanonicalBoard(level: LevelDefinition, symmetry: BoardSymmetry): String = buildString {
        append("magnetrail-symmetry-1|")
        append(level.width).append('x').append(level.height)
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

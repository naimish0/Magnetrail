package com.rameshta.magnetrail.core.content

import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ContentFingerprint {
    fun of(level: LevelDefinition): String = "sha256:${sha256Hex(canonicalBoard(level))}"

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
}

package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.BoardSymmetry
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.MagneticDiagnostics
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.generation.SeededRandom
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import kotlin.math.roundToInt

internal data class Phase0CandidateShape(
    val targetId: String,
    val boardSizeRange: IntRange,
    val arrowRange: IntRange,
    val magnetRange: IntRange,
    val wallRange: IntRange,
    val rawEmptyRange: ClosedFloatingPointRange<Double>,
)

/**
 * Deterministic build-time, solution-preserving constructor for Phase 0 staging.
 *
 * The constructor starts with an already certified campaign board, applies a symmetry and
 * coordinate compaction, and replays its known solution. Optional arrows and route-relevant
 * walls are retained only while that production-engine replay remains valid. Full search,
 * Difficulty v3, Quality, and diversity checks remain mandatory in the staging pipeline.
 * This class never promotes or rewrites campaign content.
 */
internal object Phase0CandidateFactory {
    private val engine: GameEngine = DefaultGameEngine()
    private val diagnostics = MagneticDiagnostics(engine)

    fun shape(targetId: String): Phase0CandidateShape = when (targetId) {
        "phase0-tutorial" -> Phase0CandidateShape(targetId, 3..4, 1..2, 0..1, 0..2, 0.55..0.90)
        "phase0-easy" -> Phase0CandidateShape(targetId, 3..4, 2..3, 1..2, 0..3, 0.45..0.75)
        "phase0-planning-intro" -> Phase0CandidateShape(targetId, 4..4, 3..4, 1..2, 0..4, 0.35..0.70)
        "phase0-medium", "phase0-upper-recovery" ->
            Phase0CandidateShape(targetId, 4..4, 4..5, 1..2, 0..4, 0.28..0.62)
        "phase0-hard", "phase0-upper-hard" ->
            Phase0CandidateShape(targetId, 4..4, 4..6, 1..2, 0..4, 0.20..0.56)
        "phase0-very-hard", "phase0-upper-peak" ->
            Phase0CandidateShape(targetId, 4..4, 5..7, 1..2, 0..4, 0.20..0.50)
        else -> error("Unknown Phase 0 target '$targetId'")
    }

    fun create(
        seed: Long,
        targetId: String,
        rankedTemplates: List<LevelDefinition>,
    ): LevelDefinition? {
        require(rankedTemplates.isNotEmpty()) { "Phase 0 candidate construction requires templates" }
        val shape = shape(targetId)
        val random = SeededRandom(seed xor stableSeed(targetId))
        when (Math.floorMod(seed, 10L).toInt()) {
            in 0..5 -> randomPurposefulLattice(seed, targetId, random)?.let { return it }
            in 6..8 -> structuredCompactLattice(seed, targetId, random)?.let { return it }
            else -> compactAlternatingLattice(seed, targetId, random)?.let { return it }
        }
        val templateWindow = rankedTemplates.take(3.coerceAtMost(rankedTemplates.size)).toMutableList()
        shuffle(templateWindow, random)
        templateWindow.forEach { template ->
            createFromTemplate(seed, targetId, shape, template, random)?.let { return it }
        }
        return null
    }

    /**
     * Produces varied compact interaction graphs while keeping every authored entity on a
     * possible magnetic line. Solvability, relevance and cognitive quality are deliberately
     * not assumed here: the production-engine exhaustive analyzer is the acceptance authority.
     */
    private fun randomPurposefulLattice(
        seed: Long,
        targetId: String,
        random: SeededRandom,
    ): LevelDefinition? {
        if (targetId == "phase0-tutorial") return structuredCompactLattice(seed, targetId, random)
        val boardSize = if (
            targetId in setOf("phase0-planning-intro", "phase0-medium", "phase0-upper-recovery") &&
            seed and 1L == 0L
        ) {
            3
        } else {
            4
        }
        val arrowCount = when (targetId) {
            "phase0-easy" -> 2 + random.nextInt(2)
            "phase0-planning-intro" -> 3 + random.nextInt(2)
            "phase0-medium", "phase0-upper-recovery" -> if (boardSize == 3) 4 else 4 + random.nextInt(2)
            "phase0-hard" -> 4 + random.nextInt(2)
            "phase0-upper-hard" -> 5 + random.nextInt(2)
            "phase0-very-hard", "phase0-upper-peak" -> 6 + random.nextInt(2)
            else -> return null
        }
        val magnetCount = when {
            boardSize == 3 -> 1
            targetId == "phase0-easy" -> 1
            else -> 1 + random.nextInt(2)
        }
        val cells = buildList {
            for (row in 1..boardSize) for (column in 1..boardSize) add(Position(row, column))
        }.toMutableList()
        shuffle(cells, random)
        val preferredMagnetCells = cells.filter { position ->
            if (boardSize == 3) true else position.row in 2..3 && position.column in 2..3
        }
        val magnetPositions = preferredMagnetCells.take(magnetCount)
        if (magnetPositions.size != magnetCount) return null
        val availableArrowCells = cells.filter { position ->
            position !in magnetPositions && magnetPositions.any { magnet ->
                position.row == magnet.row || position.column == magnet.column
            }
        }.toMutableList()
        shuffle(availableArrowCells, random)
        if (availableArrowCells.size < arrowCount) return null
        val arrowPositions = availableArrowCells.take(arrowCount)
        val arrows = arrowPositions.mapIndexed { index, position ->
            Arrow(
                id = ('A'.code + index).toChar().toString(),
                position = position,
                printedDirection = Direction.entries[random.nextInt(Direction.entries.size)],
            )
        }
        val magnets = magnetPositions.mapIndexed { index, position ->
            Magnet(
                id = "M${index + 1}",
                position = position,
                polarity = if (random.nextBoolean()) Polarity.PULL else Polarity.PUSH,
            )
        }
        val occupied = (arrowPositions + magnetPositions).toSet()
        val wallCandidates = cells.filter { position ->
            position !in occupied && arrowPositions.any { arrow ->
                arrow.row == position.row || arrow.column == position.column
            }
        }.toMutableList()
        shuffle(wallCandidates, random)
        val desiredWalls = when (targetId) {
            "phase0-easy", "phase0-planning-intro" -> random.nextInt(2)
            else -> random.nextInt(3)
        }
        val base = LevelDefinition(
            id = "phase0-${targetId.removePrefix("phase0-")}-$seed",
            number = 1,
            title = "Phase 0 staged candidate $seed",
            width = boardSize,
            height = boardSize,
            arrows = arrows,
            magnets = magnets,
            walls = wallCandidates.take(desiredWalls).map(::Wall),
            designedSolutions = emptyList(),
            metadata = null,
        )
        return transform(base, BoardSymmetry.entries[random.nextInt(BoardSymmetry.entries.size)])
    }

    /**
     * Enumerates compact authored-role layouts rather than scattering entities. Every arrow is
     * on a magnet arm or an outward boundary route; optional walls occupy the outward cell of a
     * controlled arm. The production search remains responsible for solvability and quality.
     */
    private fun structuredCompactLattice(
        seed: Long,
        targetId: String,
        random: SeededRandom,
    ): LevelDefinition? {
        if (targetId == "phase0-tutorial") {
            val boardSize = 3 + random.nextInt(2)
            val polarity = if (seed and 1L == 0L) Polarity.PULL else Polarity.PUSH
            val useMagnet = Math.floorMod(seed, 3L) != 0L
            val arrowPosition = when {
                !useMagnet -> Position(2, 1)
                polarity == Polarity.PULL -> Position(2, 1)
                else -> Position(2, boardSize - 1)
            }
            val magnetPosition = when (polarity) {
                Polarity.PULL -> Position(2, boardSize)
                Polarity.PUSH -> Position(2, boardSize)
            }
            val base = LevelDefinition(
                id = "phase0-tutorial-$seed",
                number = 1,
                title = "Phase 0 staged candidate $seed",
                width = boardSize,
                height = boardSize,
                arrows = listOf(
                    Arrow("A", arrowPosition, Direction.entries[random.nextInt(Direction.entries.size)]),
                ),
                magnets = if (useMagnet) listOf(Magnet("M1", magnetPosition, polarity)) else emptyList(),
                walls = emptyList(),
                designedSolutions = emptyList(),
                metadata = null,
            )
            return transform(base, BoardSymmetry.entries[random.nextInt(BoardSymmetry.entries.size)])
        }

        val desiredArrows = when (targetId) {
            "phase0-easy" -> 3
            "phase0-planning-intro" -> 3 + random.nextInt(2)
            "phase0-medium", "phase0-upper-recovery" -> 4 + random.nextInt(2)
            "phase0-hard" -> 4 + random.nextInt(2)
            "phase0-upper-hard" -> 5 + random.nextInt(2)
            else -> 6 + random.nextInt(2)
        }
        val magnetCount = if (targetId == "phase0-easy") 1 else 2
        val positions = mutableListOf(
            "A" to Position(1, 2),
            "B" to Position(3, 2),
            "C" to Position(2, 1),
            "D" to Position(2, 3),
            "E" to Position(4, 1),
            "F" to Position(1, 4),
            "G" to Position(4, 3),
            "H" to Position(3, 4),
        )
        shuffle(positions, random)
        val mandatoryIds = when (targetId) {
            "phase0-easy" -> setOf("A", "B", "C")
            else -> setOf("A", "B", "C", "D")
        }
        val selected = (
            positions.filter { it.first in mandatoryIds } + positions.filterNot { it.first in mandatoryIds }
            ).take(desiredArrows)
        val printedDirections = Direction.entries
        val arrows = selected.map { (id, position) ->
            Arrow(id, position, printedDirections[random.nextInt(printedDirections.size)])
        }
        val magnets = buildList {
            add(Magnet("M1", Position(2, 2), if (random.nextBoolean()) Polarity.PULL else Polarity.PUSH))
            if (magnetCount == 2) {
                add(Magnet("M2", Position(4, 4), if (random.nextBoolean()) Polarity.PULL else Polarity.PUSH))
            }
        }
        val selectedIds = selected.mapTo(mutableSetOf()) { it.first }
        val walls = buildList {
            if ("B" in selectedIds || "G" in selectedIds) add(Wall(Position(4, 2)))
            if ("D" in selectedIds || "H" in selectedIds) add(Wall(Position(2, 4)))
        }
        val base = LevelDefinition(
            id = "phase0-${targetId.removePrefix("phase0-")}-$seed",
            number = 1,
            title = "Phase 0 staged candidate $seed",
            width = 4,
            height = 4,
            arrows = arrows,
            magnets = magnets,
            walls = walls,
            designedSolutions = emptyList(),
            metadata = null,
        )
        return transform(base, BoardSymmetry.entries[random.nextInt(BoardSymmetry.entries.size)])
    }

    /**
     * A compact, known-solution family based on visible Pull-required and Push-safe arms.
     * Outward walls are not filler: they make the corresponding arm invalid while its magnet
     * is Push, so the player must reason about alternating polarity. The second magnet pair in
     * high bands creates an additional controller/dependency interaction.
     */
    private fun compactAlternatingLattice(
        seed: Long,
        targetId: String,
        random: SeededRandom,
    ): LevelDefinition? {
        val armCount = when (targetId) {
            "phase0-tutorial" -> 2
            "phase0-easy", "phase0-planning-intro" -> 3
            "phase0-medium", "phase0-upper-recovery" -> 4
            "phase0-hard", "phase0-upper-hard" -> 5
            else -> 6
        }
        val primaryPolarity = when (targetId) {
            "phase0-tutorial", "phase0-upper-hard", "phase0-upper-peak" -> Polarity.PUSH
            else -> Polarity.PULL
        }
        val secondaryPolarity = if (((seed xor stableSeed(targetId)) and 1L) == 0L) {
            Polarity.PUSH
        } else {
            Polarity.PULL
        }
        val arrows = listOf(
            Arrow("A", Position(1, 2), Direction.NORTH),
            Arrow("B", Position(3, 2), Direction.SOUTH),
            Arrow("C", Position(2, 1), Direction.WEST),
            Arrow("D", Position(2, 3), Direction.EAST),
            Arrow("E", Position(4, 1), Direction.WEST),
            Arrow("F", Position(1, 4), Direction.NORTH),
        ).take(armCount)
        val magnets = buildList {
            add(Magnet("M1", Position(2, 2), primaryPolarity))
            if (armCount >= 5) add(Magnet("M2", Position(4, 4), secondaryPolarity))
        }
        val walls = buildList {
            add(Wall(Position(4, 2)))
            if (armCount >= 4) add(Wall(Position(2, 4)))
        }
        val primarySequence = if (primaryPolarity == Polarity.PUSH) {
            listOf("A", "B", "C", "D")
        } else {
            listOf("B", "A", "D", "C")
        }
        val solution = when (armCount) {
            2 -> listOf("A", "B")
            3 -> listOf("B", "A", "C")
            4 -> primarySequence
            5 -> primarySequence + "E"
            else -> primarySequence + if (secondaryPolarity == Polarity.PUSH) listOf("E", "F") else listOf("F", "E")
        }
        var level = LevelDefinition(
            id = "phase0-${targetId.removePrefix("phase0-")}-$seed",
            number = 1,
            title = "Phase 0 staged candidate $seed",
            width = 4,
            height = 4,
            arrows = arrows,
            magnets = magnets,
            walls = walls,
            designedSolutions = listOf(solution),
            metadata = null,
        )
        if (!replays(level, solution)) return null
        val symmetry = BoardSymmetry.entries[random.nextInt(BoardSymmetry.entries.size)]
        level = transform(level, symmetry)
        return level.takeIf { replays(it, solution) }
    }

    private fun createFromTemplate(
        seed: Long,
        targetId: String,
        shape: Phase0CandidateShape,
        template: LevelDefinition,
        random: SeededRandom,
    ): LevelDefinition? {
        val sourceSolution = template.designedSolutions.firstOrNull() ?: return null
        val symmetry = BoardSymmetry.entries[random.nextInt(BoardSymmetry.entries.size)]
        val transformed = pruneIrrelevantWalls(transform(template, symmetry), sourceSolution)
        val viableSizes = shape.boardSizeRange.filter { size -> canCompact(transformed, size) }
        val compacted = viableSizes.asSequence()
            .map { size -> compact(transformed, size) }
            .firstOrNull { candidate -> replays(candidate, sourceSolution) }
            ?: transformed.takeIf { it.width in shape.boardSizeRange && replays(it, sourceSolution) }
            ?: return null

        var candidate = compacted.copy(
            id = "phase0-${targetId.removePrefix("phase0-")}-$seed",
            number = 1,
            title = "Phase 0 staged candidate $seed",
            metadata = null,
            designedSolutions = listOf(sourceSolution),
        )
        if (candidate.arrows.size > shape.arrowRange.last) return null
        val minimumArrowCount = if (targetId == "phase0-tutorial") shape.arrowRange.last else shape.arrowRange.first
        val desiredArrowCount = maxOf(minimumArrowCount, candidate.arrows.size)
            .coerceAtMost(shape.arrowRange.last)
        while (candidate.arrows.size < desiredArrowCount) {
            candidate = addIndependentExitArrow(candidate, random) ?: return null
        }

        val targetOccupied = ((1.0 - midpoint(shape.rawEmptyRange)) * candidate.width * candidate.height)
            .roundToInt()
        val desiredWalls = (targetOccupied - candidate.arrows.size - candidate.magnets.size)
            .coerceIn(shape.wallRange)
        candidate = addRouteRelevantWalls(candidate, desiredWalls, random)
        if (candidate.walls.size !in shape.wallRange) return null
        if (!replays(candidate, candidate.designedSolutions.single())) return null
        return candidate
    }

    private fun transform(level: LevelDefinition, symmetry: BoardSymmetry): LevelDefinition = level.copy(
        arrows = level.arrows.map { arrow ->
            Arrow(
                arrow.id,
                symmetry.transform(arrow.position, level.width, level.height),
                symmetry.transform(arrow.printedDirection),
            )
        },
        magnets = level.magnets.map { magnet ->
            magnet.copy(position = symmetry.transform(magnet.position, level.width, level.height))
        },
        walls = level.walls.map { wall ->
            Wall(symmetry.transform(wall.position, level.width, level.height))
        },
        metadata = null,
    )

    private fun canCompact(level: LevelDefinition, size: Int): Boolean {
        val positions = entityPositions(level)
        val rows = (positions.map { it.row } + listOf(1, level.height)).distinct()
        val columns = (positions.map { it.column } + listOf(1, level.width)).distinct()
        return rows.size <= size && columns.size <= size
    }

    private fun compact(level: LevelDefinition, size: Int): LevelDefinition {
        val positions = entityPositions(level)
        val rowMap = compactAxis((positions.map { it.row } + listOf(1, level.height)).distinct(), size)
        val columnMap = compactAxis((positions.map { it.column } + listOf(1, level.width)).distinct(), size)
        fun map(position: Position) = Position(
            row = requireNotNull(rowMap[position.row]),
            column = requireNotNull(columnMap[position.column]),
        )
        return level.copy(
            width = size,
            height = size,
            arrows = level.arrows.map { it.copy(position = map(it.position)) },
            magnets = level.magnets.map { it.copy(position = map(it.position)) },
            walls = level.walls.map { Wall(map(it.position)) },
            metadata = null,
        )
    }

    private fun compactAxis(source: List<Int>, targetSize: Int): Map<Int, Int> {
        val sorted = source.sorted()
        if (sorted.size == 1) return mapOf(sorted.single() to 1)
        return sorted.mapIndexed { index, value ->
            value to 1 + index * (targetSize - 1) / (sorted.size - 1)
        }.toMap()
    }

    private fun addIndependentExitArrow(level: LevelDefinition, random: SeededRandom): LevelDefinition? {
        val occupied = entityPositions(level).toSet()
        val boundary = buildList {
            for (column in 1..level.width) {
                add(Position(1, column) to Direction.NORTH)
                add(Position(level.height, column) to Direction.SOUTH)
            }
            for (row in 2..<level.height) {
                add(Position(row, 1) to Direction.WEST)
                add(Position(row, level.width) to Direction.EAST)
            }
        }.filter { (position, _) ->
            position !in occupied && level.magnets.none { magnet ->
                position.row == magnet.position.row || position.column == magnet.position.column
            }
        }.toMutableList()
        shuffle(boundary, random)
        val nextId = generateSequence(1) { it + 1 }.map { "P$it" }
            .first { id -> level.arrows.none { it.id == id } }
        boundary.forEach { (position, direction) ->
            val trialSolution = level.designedSolutions.single() + nextId
            val trial = level.copy(
                arrows = level.arrows + Arrow(nextId, position, direction),
                designedSolutions = listOf(trialSolution),
            )
            if (replays(trial, trialSolution)) return trial
        }
        return null
    }

    private fun addRouteRelevantWalls(
        source: LevelDefinition,
        desiredWallCount: Int,
        random: SeededRandom,
    ): LevelDefinition {
        if (source.walls.size >= desiredWallCount) return source
        var candidate = source
        val solution = source.designedSolutions.single()
        val proposed = routeRelevantWallCells(source, solution).toMutableList()
        shuffle(proposed, random)
        proposed.forEach { position ->
            if (candidate.walls.size >= desiredWallCount) return candidate
            if (position in entityPositions(candidate)) return@forEach
            val trial = candidate.copy(walls = candidate.walls + Wall(position))
            if (
                replays(trial, solution) &&
                wallIsRelevant(trial, position, solution) &&
                trial.walls.all { wall -> wallIsRelevant(trial, wall.position, solution) }
            ) {
                candidate = trial
            }
        }
        return candidate
    }

    private fun routeRelevantWallCells(level: LevelDefinition, solution: List<String>): Set<Position> {
        val proposed = linkedSetOf<Position>()
        var state = level.initialState()
        solution.forEach { selectedId ->
            state.arrows.forEach { arrow ->
                val result = engine.resolve(state, PlayerAction(arrow.id))
                if (arrow.id != selectedId) proposed += result.traversedCells
                proposed += diagnostics.relationshipCells(state, arrow)
            }
            val selected = engine.resolve(state, PlayerAction(selectedId))
            if (!selected.success) return emptySet()
            state = selected.resultingState
        }
        proposed.removeAll(entityPositions(level).toSet())
        return proposed.filterTo(linkedSetOf()) { it.row in 1..level.height && it.column in 1..level.width }
    }

    private fun pruneIrrelevantWalls(level: LevelDefinition, solution: List<String>): LevelDefinition {
        var candidate = level
        var changed: Boolean
        do {
            changed = false
            candidate.walls.toList().forEach { wall ->
                if (!wallIsRelevant(candidate, wall.position, solution)) {
                    val trial = candidate.copy(walls = candidate.walls.filterNot { it.position == wall.position })
                    if (replays(trial, solution)) {
                        candidate = trial
                        changed = true
                    }
                }
            }
        } while (changed)
        return candidate
    }

    private fun wallIsRelevant(
        level: LevelDefinition,
        wallPosition: Position,
        solution: List<String>,
    ): Boolean {
        val wallKey = "wall:${wallPosition.row},${wallPosition.column}"
        var state = level.initialState()
        solution.forEach { selectedId ->
            state.arrows.forEach { arrow ->
                val result = engine.resolve(state, PlayerAction(arrow.id))
                if (result.collisionTarget?.position == wallPosition) return true
                if (wallKey in diagnostics.explain(state, arrow, maxCounterfactualChecks = 128).wallEntityKeys) {
                    return true
                }
            }
            val selected = engine.resolve(state, PlayerAction(selectedId))
            if (!selected.success) return false
            state = selected.resultingState
        }
        return false
    }

    private fun replays(level: LevelDefinition, solution: List<String>): Boolean {
        var state = level.initialState()
        val complete = solution.all { arrowId ->
            val result = runCatching { engine.resolve(state, PlayerAction(arrowId)) }.getOrNull()
                ?: return@all false
            if (!result.success) return@all false
            state = result.resultingState
            true
        }
        return complete && state.arrows.isEmpty()
    }

    private fun entityPositions(level: LevelDefinition): List<Position> =
        level.arrows.map { it.position } + level.magnets.map { it.position } + level.walls.map { it.position }

    private fun midpoint(range: ClosedFloatingPointRange<Double>): Double =
        (range.start + range.endInclusive) / 2.0

    private fun <T> shuffle(values: MutableList<T>, random: SeededRandom) {
        for (index in values.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = values[index]
            values[index] = values[other]
            values[other] = value
        }
    }

    private fun stableSeed(value: String): Long {
        var hash = -3750763034362895579L
        value.encodeToByteArray().forEach { byte ->
            hash = hash xor (byte.toLong() and 0xff)
            hash *= 1099511628211L
        }
        return hash
    }
}

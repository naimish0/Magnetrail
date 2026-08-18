package com.rameshta.magnetrail.core.generation

import com.rameshta.magnetrail.core.difficulty.DifficultyMetrics
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.GameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall

data class GenerationRequest(
    val stableId: String,
    val sequenceNumber: Int,
    val title: String,
    val seed: Long,
    val profile: GenerationProfile,
    val packId: String,
    val origin: LevelOrigin = LevelOrigin.GENERATOR_ASSISTED,
)

sealed interface GenerationResult {
    data class Generated(
        val level: LevelDefinition,
        val metrics: DifficultyMetrics,
        val attemptsUsed: Int,
        val rejectedReasons: Map<String, Int>,
    ) : GenerationResult

    data class Exhausted(
        val attemptsUsed: Int,
        val rejectedReasons: Map<String, Int>,
    ) : GenerationResult
}

/**
 * Deterministically mutates known-solvable templates. Each mutation must first
 * replay a transformed known path and is then independently solver-certified.
 */
class LevelGenerator(
    templates: List<LevelDefinition>,
    private val engine: GameEngine = DefaultGameEngine(),
    private val certification: CertificationPipeline = CertificationPipeline(engine),
) {
    private val templates = templates.sortedBy { it.id }

    fun generate(request: GenerationRequest): GenerationResult {
        val eligible = templates.filter { template ->
            template.width == template.height &&
                template.width <= request.profile.maxBoardSize &&
                template.arrows.size in request.profile.minArrows..request.profile.maxArrows &&
                template.magnets.size in request.profile.minMagnets..request.profile.maxMagnets &&
                template.walls.size <= request.profile.maxWalls
        }
        if (eligible.isEmpty()) {
            return GenerationResult.Exhausted(0, mapOf("no-eligible-template" to 1))
        }

        val random = SeededRandom(request.seed xor request.profile.profileId.stableSeedComponent())
        val rejections = linkedMapOf<String, Int>()
        repeat(request.profile.maxAttempts) { attemptIndex ->
            val template = eligible[random.nextInt(eligible.size)]
            val candidate = mutate(template, request, random)
            if (!replaysKnownPath(candidate)) {
                rejections.increment("known-path-invalidated")
                return@repeat
            }
            when (val result = certification.certify(
                candidate,
                CertificationRequest(
                    profile = request.profile,
                    origin = request.origin,
                    packId = request.packId,
                    generatorVersion = GENERATOR_VERSION.takeIf {
                        request.origin == LevelOrigin.GENERATOR_ASSISTED
                    },
                    generatorSeed = request.seed.takeIf {
                        request.origin == LevelOrigin.GENERATOR_ASSISTED
                    },
                    generationProfile = request.profile.profileId.takeIf {
                        request.origin == LevelOrigin.GENERATOR_ASSISTED
                    },
                ),
            )) {
                is CertificationResult.Accepted -> return GenerationResult.Generated(
                    level = result.level,
                    metrics = result.metrics,
                    attemptsUsed = attemptIndex + 1,
                    rejectedReasons = rejections,
                )
                is CertificationResult.Rejected -> result.reasons.forEach { reason -> rejections.increment(reason) }
            }
        }
        return GenerationResult.Exhausted(request.profile.maxAttempts, rejections)
    }

    private fun mutate(
        template: LevelDefinition,
        request: GenerationRequest,
        random: SeededRandom,
    ): LevelDefinition {
        val baseSize = template.width
        val minSize = maxOf(baseSize, request.profile.minBoardSize)
        val boardSize = minSize + random.nextInt(request.profile.maxBoardSize - minSize + 1)
        val transform = random.nextInt(8)
        val rowOffset = random.nextInt(boardSize - baseSize + 1)
        val columnOffset = random.nextInt(boardSize - baseSize + 1)

        fun position(value: Position): Position {
            val transformed = transformPosition(value, baseSize, transform)
            return Position(transformed.row + rowOffset, transformed.column + columnOffset)
        }

        fun direction(value: Position, direction: Direction): Direction {
            val from = transformPosition(value, baseSize, transform)
            val to = transformPosition(value.move(direction), baseSize, transform)
            return Direction.between(from, to)
        }

        val arrows = template.arrows.map { arrow ->
            Arrow(arrow.id, position(arrow.position), direction(arrow.position, arrow.printedDirection))
        }
        val magnets = template.magnets.map { magnet ->
            Magnet(magnet.id, position(magnet.position), magnet.polarity)
        }
        val baseWalls = template.walls.map { Wall(position(it.position)) }
        val occupied = (arrows.map { it.position } + magnets.map { it.position } + baseWalls.map { it.position })
            .toMutableSet()
        val available = buildList {
            for (row in 1..boardSize) for (column in 1..boardSize) {
                Position(row, column).takeIf { it !in occupied }?.let(::add)
            }
        }.toMutableList()
        shuffle(available, random)
        val desiredExtraWalls = random.nextInt((request.profile.maxWalls - baseWalls.size + 1).coerceAtLeast(1))
        val walls = baseWalls.toMutableList()
        available.take(desiredExtraWalls).forEach { position ->
            val trial = LevelDefinition(
                id = request.stableId,
                number = request.sequenceNumber,
                title = request.title,
                width = boardSize,
                height = boardSize,
                arrows = arrows,
                magnets = magnets,
                walls = walls + Wall(position),
                designedSolutions = template.designedSolutions,
            )
            if (replaysKnownPath(trial)) walls += Wall(position)
        }
        return LevelDefinition(
            id = request.stableId,
            number = request.sequenceNumber,
            title = request.title,
            width = boardSize,
            height = boardSize,
            arrows = arrows,
            magnets = magnets,
            walls = walls,
            designedSolutions = template.designedSolutions,
        )
    }

    private fun replaysKnownPath(level: LevelDefinition): Boolean = level.designedSolutions.any { solution ->
        var state = level.initialState()
        solution.all { arrowId ->
            val result = runCatching { engine.resolve(state, PlayerAction(arrowId)) }.getOrNull()
                ?: return@all false
            if (!result.success) return@all false
            state = result.resultingState
            true
        } && state.arrows.isEmpty()
    }

    private fun transformPosition(position: Position, size: Int, transform: Int): Position {
        val mirrored = if (transform >= 4) {
            Position(position.row, size + 1 - position.column)
        } else {
            position
        }
        return when (transform % 4) {
            0 -> mirrored
            1 -> Position(mirrored.column, size + 1 - mirrored.row)
            2 -> Position(size + 1 - mirrored.row, size + 1 - mirrored.column)
            else -> Position(size + 1 - mirrored.column, mirrored.row)
        }
    }

    private fun shuffle(values: MutableList<Position>, random: SeededRandom) {
        for (index in values.lastIndex downTo 1) {
            val other = random.nextInt(index + 1)
            val value = values[index]
            values[index] = values[other]
            values[other] = value
        }
    }

    private fun MutableMap<String, Int>.increment(key: String) {
        this[key] = (this[key] ?: 0) + 1
    }
}

private fun String.stableSeedComponent(): Long {
    var hash = -3750763034362895579L
    encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xff)
        hash *= 1099511628211L
    }
    return hash
}

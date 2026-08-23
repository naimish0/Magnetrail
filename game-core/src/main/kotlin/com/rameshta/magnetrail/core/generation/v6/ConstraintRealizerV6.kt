package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position

data class V6EntityValue(
    val position: Position,
    val printedDirection: Direction? = null,
    val polarity: Polarity? = null,
)

data class V6RealizationVariable(
    val role: LogicalEntityRole,
    val domain: List<V6EntityValue>,
) {
    init {
        require(domain.isNotEmpty())
        require(domain.distinct() == domain)
        require(domain.all { value ->
            when (role.kind) {
                LogicalEntityKindV6.ARROW -> value.printedDirection != null && value.polarity == null
                LogicalEntityKindV6.MAGNET -> value.printedDirection == null && value.polarity != null
                LogicalEntityKindV6.WALL -> value.printedDirection == null && value.polarity == null
            }
        })
    }
}

fun interface V6RealizationConstraint {
    /** False means this partial assignment can never extend to a solution. */
    fun permits(assignment: Map<String, V6EntityValue>): Boolean
}

data class V6RealizationProblem(
    val width: Int,
    val height: Int,
    val variables: List<V6RealizationVariable>,
    val constraints: List<V6RealizationConstraint>,
) {
    init {
        require(width in 1..8 && height in 1..8)
        require(variables.map { it.role.key }.distinct().size == variables.size)
        require(variables.flatMap { it.domain }.all { it.position.row in 1..height && it.position.column in 1..width })
    }
}

sealed interface V6RealizationResult {
    data class Realized(
        val assignment: Map<String, V6EntityValue>,
        val exploredStates: Int,
        val recordedNogoods: Int,
        val elapsedMillis: Long,
    ) : V6RealizationResult

    data class Rejected(
        val reason: V6RejectionCode,
        val exploredStates: Int,
        val recordedNogoods: Int,
        val elapsedMillis: Long,
    ) : V6RealizationResult
}

/** Bounded deterministic CSP search with MRV, forward checking, LCV and recorded nogoods. */
class DeterministicConstraintRealizerV6(
    private val seed: Long,
    private val maxStates: Int,
    private val maxNogoods: Int,
    private val maxMillis: Long,
) {
    init {
        require(maxStates > 0 && maxNogoods > 0 && maxMillis > 0)
    }

    fun realize(problem: V6RealizationProblem): V6RealizationResult {
        val started = System.nanoTime()
        var explored = 0
        var workUnits = 0L
        val nogoods = linkedSetOf<String>()
        var rejection = V6RejectionCode.REALIZATION_ATTEMPT_CAP

        fun wallElapsedMillis(): Long = (System.nanoTime() - started) / 1_000_000
        fun deterministicElapsedMillis(): Long = workUnits / WORK_UNITS_PER_MILLISECOND
        fun compatible(
            assignment: Map<String, V6EntityValue>,
            roleKey: String,
            value: V6EntityValue,
        ): Boolean {
            workUnits += 1
            if (assignment.values.any { it.position == value.position }) return false
            val next = assignment + (roleKey to value)
            return problem.constraints.all { it.permits(next) }
        }

        fun viableDomain(
            variable: V6RealizationVariable,
            assignment: Map<String, V6EntityValue>,
        ): List<V6EntityValue> = variable.domain.filter { compatible(assignment, variable.role.key, it) }

        fun forwardCheck(assignment: Map<String, V6EntityValue>): Boolean = problem.variables
            .filter { it.role.key !in assignment }
            .all { viableDomain(it, assignment).isNotEmpty() }

        fun stateKey(assignment: Map<String, V6EntityValue>): String = assignment.entries.sortedBy { it.key }
            .joinToString("|") { (key, value) ->
                "$key=${value.position.row},${value.position.column}:${value.printedDirection}:${value.polarity}"
            }

        fun search(assignment: LinkedHashMap<String, V6EntityValue>): Map<String, V6EntityValue>? {
            check(wallElapsedMillis() < maxMillis * WATCHDOG_MULTIPLIER) {
                "V6 realizer watchdog exceeded; abort the run without certifying nondeterministic output"
            }
            if (deterministicElapsedMillis() >= maxMillis) {
                rejection = V6RejectionCode.REALIZATION_TIME_CAP
                return null
            }
            if (explored >= maxStates) {
                rejection = V6RejectionCode.REALIZATION_STATE_CAP
                return null
            }
            explored += 1
            if (assignment.size == problem.variables.size) return assignment.toMap()
            val remaining = problem.variables.filter { it.role.key !in assignment }
            val domains = remaining.associateWith { viableDomain(it, assignment) }
            if (domains.values.any { it.isEmpty() }) return null
            val variable = remaining.minWith(
                compareBy<V6RealizationVariable> { requireNotNull(domains[it]).size }
                    .thenBy { stableTie(it.role.key) }
                    .thenBy { it.role.key },
            )
            val values = requireNotNull(domains[variable]).sortedWith(
                compareByDescending<V6EntityValue> { candidate ->
                    val next = assignment + (variable.role.key to candidate)
                    remaining.filter { it != variable }.sumOf { viableDomain(it, next).size }
                }.thenBy { stableTie("${variable.role.key}:${it.position.row},${it.position.column}:${it.printedDirection}:${it.polarity}") }
                    .thenBy { it.position.row }
                    .thenBy { it.position.column }
                    .thenBy { it.printedDirection?.ordinal ?: -1 }
                    .thenBy { it.polarity?.ordinal ?: -1 },
            )
            for (value in values) {
                assignment[variable.role.key] = value
                val key = stateKey(assignment)
                if (key !in nogoods && forwardCheck(assignment)) {
                    search(assignment)?.let { return it }
                }
                assignment.remove(variable.role.key)
                if (nogoods.size >= maxNogoods) {
                    rejection = V6RejectionCode.REALIZATION_NOGOOD_CAP
                    return null
                }
                nogoods += key
            }
            return null
        }

        val assignment = search(linkedMapOf())
        return if (assignment != null) {
            V6RealizationResult.Realized(assignment, explored, nogoods.size, deterministicElapsedMillis())
        } else {
            V6RealizationResult.Rejected(rejection, explored, nogoods.size, deterministicElapsedMillis())
        }
    }

    private fun stableTie(value: String): Long {
        var hash = seed xor -3_750_763_034_362_895_579L
        value.forEach { character ->
            hash = (hash xor character.code.toLong()) * 1_099_511_628_211L
        }
        return hash
    }

    companion object {
        private const val WORK_UNITS_PER_MILLISECOND = 2_000L
        private const val WATCHDOG_MULTIPLIER = 30L
    }
}

object V6Constraints {
    fun aligned(firstRole: String, secondRole: String): V6RealizationConstraint = V6RealizationConstraint { assignment ->
        val first = assignment[firstRole]?.position
        val second = assignment[secondRole]?.position
        first == null || second == null || first.row == second.row || first.column == second.column
    }

    fun minimumDistance(firstRole: String, secondRole: String, minimum: Int): V6RealizationConstraint {
        require(minimum >= 0)
        return V6RealizationConstraint { assignment ->
            val first = assignment[firstRole]?.position
            val second = assignment[secondRole]?.position
            first == null || second == null ||
                kotlin.math.abs(first.row - second.row) + kotlin.math.abs(first.column - second.column) >= minimum
        }
    }

    fun between(blockerRole: String, startRole: String, endRole: String): V6RealizationConstraint =
        V6RealizationConstraint { assignment ->
            val blocker = assignment[blockerRole]?.position
            val start = assignment[startRole]?.position
            val end = assignment[endRole]?.position
            if (blocker == null || start == null || end == null) {
                true
            } else when {
                start.row == end.row -> blocker.row == start.row && blocker.column in
                    (minOf(start.column, end.column) + 1)..<maxOf(start.column, end.column)
                start.column == end.column -> blocker.column == start.column && blocker.row in
                    (minOf(start.row, end.row) + 1)..<maxOf(start.row, end.row)
                else -> false
            }
        }

    fun symmetryBreak(anchorRole: String, width: Int, height: Int): V6RealizationConstraint =
        V6RealizationConstraint { assignment ->
            assignment[anchorRole]?.position?.let { position ->
                position.row < (height + 2) / 2 ||
                    position.row == (height + 2) / 2 && position.column <= (width + 1) / 2
            } ?: true
        }
}

package com.rameshta.magnetrail.core.engine

import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Position

/** Rule-owned explanation used only by build-time analysis and diagnostics. */
data class MagneticControlExplanation(
    val controllingMagnet: Magnet?,
    val equallyNearestVisibleMagnets: List<Magnet>,
    val cancelledByEqualNearestMagnets: Boolean,
)

data class MagneticDependencyExplanation(
    val cancellationUsed: Boolean,
    val occludingEntityKeys: List<String>,
    val wallEntityKeys: List<String>,
    val counterfactualChecksPerformed: Int,
    val counterfactualCapped: Boolean,
)

class MagneticDiagnostics(
    private val engine: GameEngine = DefaultGameEngine(),
    private val tracer: DeterministicRouteTracer = DeterministicRouteTracer(),
) {
    fun explain(
        state: BoardState,
        arrow: Arrow,
        maxCounterfactualChecks: Int = Int.MAX_VALUE,
    ): MagneticDependencyExplanation {
        require(maxCounterfactualChecks >= 0)
        val control = tracer.explainControl(state, arrow)
        val original = engine.resolve(state, PlayerAction(arrow.id)).signature()
        val occluders = mutableListOf<String>()
        val wallDependencies = mutableListOf<String>()
        var checks = 0
        var capped = false

        state.arrows.filter { it.id != arrow.id }.forEach { candidate ->
            if (candidate.position.blocksAlignedMagnet(state, arrow)) {
                if (checks >= maxCounterfactualChecks) {
                    capped = true
                    return@forEach
                }
                checks += 1
                val changed = engine.resolve(
                    state.copy(arrows = state.arrows.filterNot { it.id == candidate.id }),
                    PlayerAction(arrow.id),
                ).signature() != original
                if (changed) occluders += "arrow:${candidate.id}"
            }
        }
        state.walls.forEachIndexed { index, candidate ->
            if (candidate.position.blocksAlignedMagnet(state, arrow)) {
                if (checks >= maxCounterfactualChecks) {
                    capped = true
                    return@forEachIndexed
                }
                checks += 1
                val changed = engine.resolve(
                    state.copy(walls = state.walls.filterIndexed { otherIndex, _ -> otherIndex != index }),
                    PlayerAction(arrow.id),
                ).signature() != original
                if (changed) {
                    val key = "wall:${candidate.position.row},${candidate.position.column}"
                    occluders += key
                    wallDependencies += key
                }
            }
        }
        state.magnets.forEach { candidate ->
            if (candidate.position.blocksAlignedMagnet(state, arrow)) {
                if (checks >= maxCounterfactualChecks) {
                    capped = true
                    return@forEach
                }
                checks += 1
                val changed = engine.resolve(
                    state.copy(magnets = state.magnets.filterNot { it.id == candidate.id }),
                    PlayerAction(arrow.id),
                ).signature() != original
                if (changed) occluders += "magnet:${candidate.id}"
            }
        }
        return MagneticDependencyExplanation(
            cancellationUsed = control.cancelledByEqualNearestMagnets,
            occludingEntityKeys = occluders.sorted(),
            wallEntityKeys = wallDependencies.sorted(),
            counterfactualChecksPerformed = checks,
            counterfactualCapped = capped,
        )
    }

    private fun Position.blocksAlignedMagnet(state: BoardState, arrow: Arrow): Boolean =
        state.magnets.any { magnet ->
            aligned(arrow.position, magnet.position) && between(arrow.position, this, magnet.position)
        }

    private fun aligned(a: Position, b: Position): Boolean = a.row == b.row || a.column == b.column

    private fun between(start: Position, candidate: Position, end: Position): Boolean = when {
        start.row == end.row && candidate.row == start.row -> candidate.column in
            (minOf(start.column, end.column) + 1)..<maxOf(start.column, end.column)
        start.column == end.column && candidate.column == start.column -> candidate.row in
            (minOf(start.row, end.row) + 1)..<maxOf(start.row, end.row)
        else -> false
    }

    private fun ResolutionResult.signature(): ResolutionSignature = ResolutionSignature(
        success = success,
        effectiveDirection = effectiveDirection,
        controllingMagnetId = controllingMagnetId,
        terminalType = terminalEvent::class.simpleName.orEmpty(),
    )

    private data class ResolutionSignature(
        val success: Boolean,
        val effectiveDirection: Direction,
        val controllingMagnetId: String?,
        val terminalType: String,
    )
}

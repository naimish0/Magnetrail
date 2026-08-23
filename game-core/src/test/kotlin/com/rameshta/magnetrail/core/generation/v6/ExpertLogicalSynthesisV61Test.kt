package com.rameshta.magnetrail.core.generation.v6

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpertLogicalSynthesisV61Test {
    private val synthesizer = ExpertLogicalGraphSynthesizerV61()

    @Test
    fun `Expert logical synthesis is deterministic and precedes geometry`() {
        val identity = identity(ordinal = 101, attempt = 7, topology = TopologyFamilyV61.TWO_CHAIN_INTERLOCK)

        val first = synthesizer.synthesize(identity)
        val repeated = synthesizer.synthesize(identity)

        assertEquals(first, repeated)
        assertEquals(18, first.spec.actions.size)
        assertEquals(setOf(0, 1, 2), first.phaseByActionRole.values.toSet())
        assertTrue(first.decisionPairs.size >= 6)
        assertTrue(first.spec.trapContracts.size >= 3)
        assertTrue(first.crossChainDependencies.size >= 2)
        assertEquals(18, topologicalCount(first.spec.solutionPartialOrder))
    }

    @Test
    fun `Expert synthesis creates a substantial non-isomorphic logical archive`() {
        val plans = (0 until 48).map { index ->
            synthesizer.synthesize(
                identity(
                    ordinal = 10_001 + index,
                    attempt = index % 11,
                    topology = TopologyFamilyV61.entries[index % TopologyFamilyV61.entries.size],
                ),
            )
        }

        assertEquals(48, plans.map { it.canonicalGraphFingerprint }.toSet().size)
        assertTrue(plans.map { it.kernel }.toSet().size >= 2)
        assertEquals(48, plans.map(::expertLogicalPlanDigestV61).toSet().size)
    }

    @Test
    fun `graph changes are semantic rather than coordinate or identity changes`() {
        val first = synthesizer.synthesize(
            identity(ordinal = 301, attempt = 2, topology = TopologyFamilyV61.NESTED_NON_COMMUTING_CHOICES),
        )
        val changed = synthesizer.synthesize(
            identity(ordinal = 302, attempt = 3, topology = TopologyFamilyV61.NESTED_NON_COMMUTING_CHOICES),
        )

        assertNotEquals(first.canonicalGraphFingerprint, changed.canonicalGraphFingerprint)
        assertNotEquals(first.spec.solutionPartialOrder.precedence, changed.spec.solutionPartialOrder.precedence)
    }

    private fun identity(
        ordinal: Int,
        attempt: Int,
        topology: TopologyFamilyV61,
    ) = GeneratorV61Identity(
        ordinal = ordinal,
        band = AutomatedDifficultyBandV61.EXPERT,
        attempt = attempt,
        topologyFamily = topology,
        familyVariant = Math.floorMod(ordinal + attempt, 3),
        seed = ordinal.toLong() * 1_000_003L + attempt,
    )

    private fun topologicalCount(order: SolutionPartialOrder): Int {
        val incoming = order.actionRoleKeys.associateWith { 0 }.toMutableMap()
        order.precedence.forEach { (_, after) -> incoming[after] = incoming.getValue(after) + 1 }
        val ready = java.util.PriorityQueue(incoming.filterValues { it == 0 }.keys)
        var visited = 0
        while (ready.isNotEmpty()) {
            val before = ready.remove()
            visited += 1
            order.precedence.filter { it.first == before }.forEach { (_, after) ->
                incoming[after] = incoming.getValue(after) - 1
                if (incoming.getValue(after) == 0) ready += after
            }
        }
        return visited
    }
}

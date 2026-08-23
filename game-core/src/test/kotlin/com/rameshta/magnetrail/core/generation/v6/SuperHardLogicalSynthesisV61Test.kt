package com.rameshta.magnetrail.core.generation.v6

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperHardLogicalSynthesisV61Test {
    private val synthesizer = SuperHardLogicalGraphSynthesizerV61()

    @Test
    fun `Super Hard logical synthesis is deterministic and precedes geometry`() {
        val identity = identity(101, 7, TopologyFamilyV61.TWO_CHAIN_INTERLOCK)

        val first = synthesizer.synthesize(identity)
        val repeated = synthesizer.synthesize(identity)

        assertEquals(first, repeated)
        assertEquals(17, first.spec.actions.size)
        assertEquals(setOf(0, 1, 2), first.phaseByActionRole.values.toSet())
        assertTrue(first.decisionPairs.size >= 4)
        assertTrue(first.spec.trapContracts.size >= 2)
        assertTrue(first.crossChainDependencies.size >= 2)
        assertEquals(17, topologicalCount(first.spec.solutionPartialOrder))
    }

    @Test
    fun `Super Hard synthesis creates a substantial non-isomorphic logical archive`() {
        val plans = (0 until 48).map { index ->
            synthesizer.synthesize(
                identity(
                    20_001 + index,
                    index % 13,
                    TopologyFamilyV61.entries[index % TopologyFamilyV61.entries.size],
                ),
            )
        }

        assertEquals(48, plans.map { it.canonicalGraphFingerprint }.toSet().size)
        assertTrue(plans.map { it.kernel }.toSet().size >= 2)
        assertEquals(48, plans.map(::superHardLogicalPlanDigestV61).toSet().size)
    }

    @Test
    fun `Super Hard graph changes alter dependencies rather than metadata only`() {
        val first = synthesizer.synthesize(
            identity(401, 2, TopologyFamilyV61.SHARED_MAGNET_PARITY_BRAID),
        )
        val changed = synthesizer.synthesize(
            identity(402, 3, TopologyFamilyV61.SHARED_MAGNET_PARITY_BRAID),
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
        band = AutomatedDifficultyBandV61.SUPER_HARD,
        attempt = attempt,
        topologyFamily = topology,
        familyVariant = Math.floorMod(ordinal + attempt, 3),
        seed = ordinal.toLong() * 1_000_033L + attempt,
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

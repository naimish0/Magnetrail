package com.rameshta.magnetrail.core.generation.v5

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignDensityRemediatorV10Test {
    @Test
    fun `remediation adds an ordered arrow cascade and preserves the source solution`() {
        val source = LevelDefinition(
            id = "campaign-206",
            number = 206,
            title = "Source",
            width = 5,
            height = 5,
            arrows = listOf(Arrow("base", Position(3, 3), Direction.NORTH)),
            magnets = listOf(Magnet("magnet", Position(3, 5), Polarity.PULL)),
            walls = emptyList(),
            designedSolutions = listOf(listOf("base")),
        )
        val result = (1L..128L).firstNotNullOfOrNull { seed ->
            CampaignDensityRemediatorV10().remediate(
                source,
                DensityRemediationSpecV10(
                    targetArrowCount = 7,
                    minimumOccupancyRatio = 0.52,
                    topology = DensityTopologyV10.VERTICAL_CASCADES,
                ),
                seed = seed,
            )
        }
        assertNotNull(result)
        requireNotNull(result)

        assertEquals(7, result.level.arrows.size)
        assertTrue(result.auxiliaryDependencyCount >= 2)
        assertTrue(
            (result.level.arrows.size + result.level.magnets.size + result.level.walls.size).toDouble() / 25 >= 0.52,
        )
        var state = result.level.initialState()
        result.level.designedSolutions.single().forEach { id ->
            val resolution = DefaultGameEngine().resolve(state, PlayerAction(id))
            assertTrue("$id should replay", resolution.success)
            state = resolution.resultingState
        }
        assertTrue(state.arrows.isEmpty())
    }

    @Test
    fun `magnet-controlled cascade remains eligible as an interactive dependency`() {
        val source = LevelDefinition(
            id = "campaign-1467",
            number = 1467,
            title = "Magnetic source",
            width = 5,
            height = 5,
            arrows = listOf(
                Arrow("a1", Position(5, 5), Direction.EAST),
                Arrow("a2", Position(4, 4), Direction.EAST),
                Arrow("a3", Position(2, 5), Direction.NORTH),
                Arrow("a4", Position(4, 3), Direction.EAST),
                Arrow("a5", Position(3, 4), Direction.WEST),
                Arrow("a6", Position(5, 4), Direction.WEST),
            ),
            magnets = listOf(
                Magnet("m1", Position(5, 1), Polarity.PUSH),
                Magnet("m2", Position(2, 4), Polarity.PULL),
                Magnet("m3", Position(2, 1), Polarity.PULL),
                Magnet("m4", Position(4, 2), Polarity.PULL),
            ),
            walls = listOf(Wall(Position(1, 3))),
            designedSolutions = listOf(listOf("a1", "a2", "a3", "a4", "a6", "a5")),
        )

        val result = CampaignDensityRemediatorV10().remediate(
            source = source,
            spec = DensityRemediationSpecV10(
                targetArrowCount = 8,
                minimumOccupancyRatio = 0.56,
                topology = DensityTopologyV10.HORIZONTAL_CASCADES,
            ),
            seed = 1_272_203_787L,
        )

        assertNotNull(result)
        assertTrue(requireNotNull(result).auxiliaryDependencyCount >= 1)
    }
}

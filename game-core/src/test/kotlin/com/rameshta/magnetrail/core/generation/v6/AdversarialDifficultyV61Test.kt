package com.rameshta.magnetrail.core.generation.v6

import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.BoardState
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Magnet
import com.rameshta.magnetrail.core.model.Polarity
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdversarialDifficultyV61Test {
    private val engine = DefaultGameEngine()
    private val analyzer = CuedAdversarialDifficultyAnalyzerV61(stochasticRuns = 64)

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic frozen V6 policy caps remain observable`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        catalog.levels.forEach { level ->
            val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
            val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
            println(
                "V61_DIAGNOSTIC ${level.id} actions=${result.solutionActions} decisions=${result.criticalEpisodes.size} " +
                    "lookahead=${result.criticalEpisodes.maxOfOrNull { it.proof.minimumVisibleLookahead } ?: 0} " +
                    "cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                    "cap=${result.maximumEligibleBand?.name} dominators=${result.policyReport.dominatingCheapPolicies}",
            )
            if (level.id == "v6-calibration-030") {
                result.criticalEpisodes.forEach { episode ->
                    println("V61_EPISODE $episode")
                }
                result.policyReport.results.filter { it.solvedRuns > 0 }.forEach { policy ->
                    println("V61_POLICY $policy")
                }
            }
        }
        assertEquals(30, catalog.levels.size)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic expanded nested seed candidates`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val base = catalog.levels.single { it.id == "v6-calibration-030" }
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..4).flatMap { row -> (1..5).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position ->
                Direction.entries.forEach { direction ->
                    val level = base.copy(
                        id = "expanded-nested",
                        width = 5,
                        arrows = base.arrows + Arrow("v61-a-09", position, direction),
                        designedSolutions = emptyList(),
                    )
                    val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                    if (dag.complete && dag.metrics?.shortestCompletion != null) {
                        val result = analyzer.analyze(
                            level,
                            dag,
                            semanticNoveltyPass = true,
                            purposefulOccupancyPass = true,
                        )
                        if (result.criticalEpisodes.size >= 3) {
                            println(
                                "V61_EXPANDED position=$position direction=$direction " +
                                    "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                    "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                    "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                    "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                            )
                        }
                    }
                    examined += 1
                }
            }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic expanded nested two-arrow candidates`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val base = catalog.levels.single { it.id == "v6-calibration-030" }.copy(width = 5)
        val first = Arrow("v61-a-09", Position(3, 2), Direction.SOUTH)
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } +
            base.walls.map { it.position } + first.position).toSet()
        var examined = 0
        (1..4).flatMap { row -> (1..5).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position ->
                Direction.entries.forEach { direction ->
                    val level = base.copy(
                        id = "expanded-nested-two",
                        arrows = base.arrows + first + Arrow("v61-a-10", position, direction),
                        designedSolutions = emptyList(),
                    )
                    val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                    if (dag.complete && dag.metrics?.shortestCompletion != null) {
                        val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                        if (result.criticalEpisodes.size >= 3 && result.policyReport.bestCheapPolicySolveRate <= 0.40) {
                            println(
                                "V61_EXPANDED_TWO position=$position direction=$direction " +
                                    "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                    "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                    "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                    "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                            )
                        }
                    }
                    examined += 1
                }
            }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic expanded nested direction refinements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val base = catalog.levels.single { it.id == "v6-calibration-030" }.copy(
            id = "expanded-nested-refinement",
            width = 5,
        )
        val starting = base.arrows + Arrow("v61-a-09", Position(3, 2), Direction.SOUTH)
        var examined = 0
        starting.indices.forEach { arrowIndex ->
            Direction.entries.filter { it != starting[arrowIndex].printedDirection }.forEach { direction ->
                val arrows = starting.toMutableList().also { values ->
                    values[arrowIndex] = values[arrowIndex].copy(printedDirection = direction)
                }
                val level = base.copy(arrows = arrows, designedSolutions = emptyList())
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 3 && result.forcedCleanupTailRatio <= 0.35 &&
                        result.policyReport.bestCheapPolicySolveRate <= 0.40
                    ) {
                        println(
                            "V61_DIRECTION arrow=${starting[arrowIndex].id} direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            }
        }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic expanded nested position refinements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "expanded-nested-position",
            width = 5,
            arrows = source.arrows + Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
            designedSolutions = emptyList(),
        )
        val entityCount = base.arrows.size + base.magnets.size + base.walls.size
        fun position(index: Int): Position = when {
            index < base.arrows.size -> base.arrows[index].position
            index < base.arrows.size + base.magnets.size -> base.magnets[index - base.arrows.size].position
            else -> base.walls[index - base.arrows.size - base.magnets.size].position
        }
        var examined = 0
        for (first in 0 until entityCount) for (second in first + 1 until entityCount) {
            val firstPosition = position(first)
            val secondPosition = position(second)
            val arrows = base.arrows.mapIndexed { index, arrow -> arrow.copy(position = when (index) {
                first -> secondPosition
                second -> firstPosition
                else -> arrow.position
            }) }
            val magnets = base.magnets.mapIndexed { offset, magnet ->
                val index = base.arrows.size + offset
                magnet.copy(position = when (index) {
                    first -> secondPosition
                    second -> firstPosition
                    else -> magnet.position
                })
            }
            val walls = base.walls.mapIndexed { offset, wall ->
                val index = base.arrows.size + base.magnets.size + offset
                Wall(when (index) {
                    first -> secondPosition
                    second -> firstPosition
                    else -> wall.position
                })
            }
            val level = base.copy(arrows = arrows, magnets = magnets, walls = walls)
            val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
            if (dag.complete && dag.metrics?.shortestCompletion != null) {
                val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                if (result.criticalEpisodes.size >= 3 && result.causalPhaseCount >= 2 &&
                    result.maximumForcedRun <= 4 && result.forcedCleanupTailRatio <= 0.35 &&
                    result.policyReport.bestCheapPolicySolveRate <= 0.40
                ) {
                    println(
                        "V61_POSITION first=$first second=$second decisions=${result.criticalEpisodes.size} " +
                            "phases=${result.causalPhaseCount} traps=${result.persistentSuccessfulTrapCount} " +
                            "forced=${result.maximumForcedRun} cleanup=${result.forcedCleanupTailRatio} " +
                            "cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                            "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                    )
                }
            }
            examined += 1
        }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic expanded nested wall refinements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "expanded-nested-wall",
            width = 5,
            arrows = source.arrows + Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..4).flatMap { row -> (1..5).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position ->
                val level = base.copy(walls = base.walls + Wall(position))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 3 && result.causalPhaseCount >= 2 &&
                        result.maximumForcedRun <= 4 && result.forcedCleanupTailRatio <= 0.35 &&
                        result.policyReport.bestCheapPolicySolveRate <= 0.40
                    ) {
                        println(
                            "V61_WALL position=$position decisions=${result.criticalEpisodes.size} " +
                                "phases=${result.causalPhaseCount} traps=${result.persistentSuccessfulTrapCount} " +
                                "forced=${result.maximumForcedRun} cleanup=${result.forcedCleanupTailRatio} " +
                                "cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }} " +
                                "inferable=${result.inferableCriticalDecisionRatio} " +
                                "invalid=${result.criticalEpisodes.filterNot { it.proof.valid }.map { it.safeActionId + '>' + it.successfulTrapActionId + ':' + it.proof.invalidReasons }}",
                        )
                    }
                }
                examined += 1
            }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic expanded nested magnet refinements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "expanded-nested-magnet",
            width = 5,
            arrows = source.arrows + Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..4).flatMap { row -> (1..5).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position -> Polarity.entries.forEach { polarity ->
                val level = base.copy(magnets = base.magnets + Magnet("v61-m-02", position, polarity))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 3 && result.causalPhaseCount >= 2 &&
                        result.maximumForcedRun <= 4 && result.forcedCleanupTailRatio <= 0.35 &&
                        result.policyReport.bestCheapPolicySolveRate <= 0.40
                    ) {
                        println(
                            "V61_MAGNET position=$position polarity=$polarity decisions=${result.criticalEpisodes.size} " +
                                "phases=${result.causalPhaseCount} traps=${result.persistentSuccessfulTrapCount} " +
                                "forced=${result.maximumForcedRun} cleanup=${result.forcedCleanupTailRatio} " +
                                "cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic expanded nested three-arrow candidates`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val first = Arrow("v61-a-09", Position(3, 2), Direction.SOUTH)
        val secondCandidates = listOf(
            Position(1, 2) to Direction.NORTH,
            Position(1, 2) to Direction.EAST,
            Position(1, 2) to Direction.SOUTH,
            Position(1, 2) to Direction.WEST,
            Position(1, 5) to Direction.WEST,
            Position(2, 4) to Direction.NORTH,
            Position(2, 4) to Direction.EAST,
            Position(2, 5) to Direction.NORTH,
            Position(2, 5) to Direction.EAST,
            Position(2, 5) to Direction.SOUTH,
            Position(3, 5) to Direction.NORTH,
            Position(3, 5) to Direction.EAST,
            Position(3, 5) to Direction.SOUTH,
            Position(4, 5) to Direction.NORTH,
            Position(4, 5) to Direction.EAST,
            Position(4, 5) to Direction.SOUTH,
        )
        var examined = 0
        secondCandidates.forEach { (secondPosition, secondDirection) ->
            val second = Arrow("v61-a-10", secondPosition, secondDirection)
            val base = source.copy(
                id = "expanded-nested-three",
                width = 5,
                height = 5,
                arrows = source.arrows + first + second,
                designedSolutions = emptyList(),
            )
            val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } +
                base.walls.map { it.position }).toSet()
            (1..5).flatMap { row -> (1..5).map { column -> Position(row, column) } }
                .filterNot(occupied::contains)
                .forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-11", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 3 && result.causalPhaseCount >= 2 &&
                        result.persistentSuccessfulTrapCount >= 1 && result.maximumForcedRun <= 4 &&
                        result.forcedCleanupTailRatio <= 0.35 && result.policyReport.bestCheapPolicySolveRate <= 0.40
                    ) {
                        println(
                            "V61_EXPANDED_THREE second=$secondPosition/$secondDirection " +
                                "third=$position/$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            } }
        }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic super hard fourth-decision additions`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "super-hard-fourth-decision",
            width = 5,
            height = 5,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
            ),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..5).flatMap { row -> (1..5).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-12", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 4) {
                        println(
                            "V61_SUPER_ONE position=$position direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                        println(
                            "V61_SUPER_POSITIONS " + result.criticalEpisodes.joinToString { episode ->
                                val remaining = requireNotNull(dag.nodes[episode.stateKey]).state.arrows.size
                                "${level.arrows.size - remaining}:${episode.safeActionId}>${episode.successfulTrapActionId}"
                            },
                        )
                        result.criticalEpisodes.maxByOrNull { episode ->
                            level.arrows.size - requireNotNull(dag.nodes[episode.stateKey]).state.arrows.size
                        }?.let { late ->
                            val node = requireNotNull(dag.nodes[late.stateKey])
                            println("V61_SUPER_LATE arrows=${node.state.arrows.map { it.id }} transitions=${node.transitions}")
                            val safe = node.transitions.single { it.arrowId == late.safeActionId }
                            var cursor = requireNotNull(dag.nodes[safe.childStateKey])
                            val tail = mutableListOf(late.safeActionId)
                            while (cursor.state.arrows.isNotEmpty()) {
                                val edge = cursor.transitions.filter { it.successful && it.futureSolvable == true }
                                    .minByOrNull { it.arrowId } ?: break
                                tail += edge.arrowId
                                cursor = requireNotNull(dag.nodes[edge.childStateKey])
                            }
                            println("V61_SUPER_TAIL $tail")
                        }
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic super hard phase refinements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "super-hard-phase-refinement",
            width = 5,
            height = 5,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
            ),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..5).flatMap { row -> (1..5).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-13", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 4) {
                        println(
                            "V61_SUPER_TWO position=$position direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic super hard late-phase pair refinements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "super-hard-late-phase",
            width = 5,
            height = 5,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
            ),
            designedSolutions = emptyList(),
        )
        val fourthCandidates = listOf(
            Position(1, 2) to Direction.NORTH,
            Position(1, 5) to Direction.SOUTH,
            Position(2, 5) to Direction.NORTH,
            Position(2, 5) to Direction.EAST,
            Position(4, 5) to Direction.NORTH,
            Position(4, 5) to Direction.EAST,
            Position(5, 2) to Direction.SOUTH,
            Position(5, 2) to Direction.WEST,
            Position(5, 4) to Direction.EAST,
            Position(5, 4) to Direction.SOUTH,
            Position(5, 5) to Direction.NORTH,
            Position(5, 5) to Direction.EAST,
        )
        var examined = 0
        fourthCandidates.forEach { (fourthPosition, fourthDirection) ->
            val fourth = Arrow("v61-a-13", fourthPosition, fourthDirection)
            val withFourth = base.copy(arrows = base.arrows + fourth)
            val occupied = (withFourth.arrows.map { it.position } + withFourth.magnets.map { it.position } +
                withFourth.walls.map { it.position }).toSet()
            (1..5).flatMap { row -> (1..5).map { column -> Position(row, column) } }
                .filterNot(occupied::contains)
                .forEach { position -> Direction.entries.forEach { direction ->
                    val level = withFourth.copy(arrows = withFourth.arrows + Arrow("v61-a-14", position, direction))
                    val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                    if (dag.complete && dag.metrics?.shortestCompletion != null) {
                        val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                        if (result.criticalEpisodes.size >= 4 && result.causalPhaseCount >= 3) {
                            println(
                                "V61_SUPER_THREE fourth=$fourthPosition/$fourthDirection fifth=$position/$direction " +
                                    "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                    "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                    "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                    "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                            )
                        }
                    }
                    examined += 1
                } }
        }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic super hard late controller refinements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "super-hard-late-controller",
            width = 5,
            height = 5,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
            ),
            designedSolutions = emptyList(),
        )
        val arrowCandidates = listOf(
            Position(1, 2) to Direction.NORTH,
            Position(1, 5) to Direction.SOUTH,
            Position(2, 5) to Direction.NORTH,
            Position(2, 5) to Direction.EAST,
            Position(4, 5) to Direction.NORTH,
            Position(4, 5) to Direction.EAST,
            Position(5, 2) to Direction.SOUTH,
            Position(5, 2) to Direction.WEST,
            Position(5, 4) to Direction.EAST,
            Position(5, 4) to Direction.SOUTH,
            Position(5, 5) to Direction.NORTH,
            Position(5, 5) to Direction.EAST,
        )
        var examined = 0
        arrowCandidates.forEach { (arrowPosition, arrowDirection) ->
            val withArrow = base.copy(
                arrows = base.arrows + Arrow("v61-a-13", arrowPosition, arrowDirection),
            )
            val occupied = (withArrow.arrows.map { it.position } + withArrow.magnets.map { it.position } +
                withArrow.walls.map { it.position }).toSet()
            (1..5).flatMap { row -> (1..5).map { column -> Position(row, column) } }
                .filterNot(occupied::contains)
                .forEach { position -> Polarity.entries.forEach { polarity ->
                    val level = withArrow.copy(
                        magnets = withArrow.magnets + Magnet("v61-m-02", position, polarity),
                    )
                    val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                    if (dag.complete && dag.metrics?.shortestCompletion != null) {
                        val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                        if (result.solutionActions >= 14 && result.criticalEpisodes.size >= 4 &&
                            result.causalPhaseCount >= 3
                        ) {
                            println(
                                "V61_SUPER_MAGNET arrow=$arrowPosition/$arrowDirection magnet=$position/$polarity " +
                                    "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                    "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                    "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                    "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                            )
                        }
                    }
                    examined += 1
                } }
        }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `wrong-now right-later arrow creates a late Super Hard phase`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val level = source.copy(
            id = "super-hard-wrong-now-right-later",
            width = 5,
            height = 6,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
            ),
            walls = source.walls,
            designedSolutions = emptyList(),
        )
        val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
        assertTrue(dag.truncationReasons.toString(), dag.complete)
        val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)

        println("V61_SUPER_AUTHORED $result")
        assertTrue(result.solutionActions >= 14)
        assertTrue(result.criticalEpisodes.size >= 4)
        assertTrue(result.causalPhaseCount >= 3)
        assertTrue(result.persistentSuccessfulTrapCount >= 2)
        assertTrue(result.maximumForcedRun <= 3)
        assertTrue(result.forcedCleanupTailRatio <= 0.30)
        assertTrue(result.policyReport.bestCheapPolicySolveRate <= 0.20)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic wrong-now right-later wall placements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "super-hard-wall-search",
            width = 5,
            height = 6,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
            ),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..6).flatMap { row -> (1..5).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position ->
                val level = base.copy(walls = base.walls + Wall(position))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.solutionActions >= 14 && result.criticalEpisodes.size >= 4 &&
                        result.causalPhaseCount >= 3
                    ) {
                        println(
                            "V61_SUPER_WALL position=$position decisions=${result.criticalEpisodes.size} " +
                                "phases=${result.causalPhaseCount} traps=${result.persistentSuccessfulTrapCount} " +
                                "forced=${result.maximumForcedRun} cleanup=${result.forcedCleanupTailRatio} " +
                                "cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Super Hard forced-run breaker`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "super-hard-forced-run-breaker",
            width = 5,
            height = 6,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
            ),
            walls = source.walls + Wall(Position(1, 5)),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..6).flatMap { row -> (1..5).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-14", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.solutionActions >= 14 && result.criticalEpisodes.size >= 4 &&
                        result.causalPhaseCount >= 3
                    ) {
                        println(
                            "V61_SUPER_BREAKER position=$position direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Super Hard second forced-run breaker`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "super-hard-second-forced-run-breaker",
            width = 5,
            height = 6,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
                Arrow("v61-a-14", Position(5, 2), Direction.EAST),
            ),
            walls = source.walls + Wall(Position(1, 5)),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..6).flatMap { row -> (1..5).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-15", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.solutionActions >= 14 && result.criticalEpisodes.size >= 4 &&
                        result.causalPhaseCount >= 3
                    ) {
                        println(
                            "V61_SUPER_SECOND_BREAKER position=$position direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }} " +
                                "cap=${result.maximumEligibleBand}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Super Hard forced segment`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val level = source.copy(
            id = "super-hard-forced-segment",
            width = 5,
            height = 6,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
                Arrow("v61-a-14", Position(5, 4), Direction.WEST),
            ),
            walls = source.walls + Wall(Position(1, 5)),
            designedSolutions = emptyList(),
        )
        val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
        val assessment = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
        var node = requireNotNull(dag.nodes[dag.rootStateKey])
        var index = 0
        while (node.state.arrows.isNotEmpty()) {
            val viable = node.transitions.filter { it.successful && it.futureSolvable == true }
            val edge = viable.minWithOrNull(
                compareBy<com.rameshta.magnetrail.core.generation.v6.DecisionTransitionV6> {
                    dag.nodes[it.childStateKey]?.shortestCompletion
                }.thenBy { it.arrowId },
            ) ?: break
            println(
                "V61_SUPER_PATH index=$index action=${edge.arrowId} viable=${viable.map { it.arrowId }} " +
                    "critical=${assessment.criticalEpisodes.filter { it.stateKey == node.stateKey }.map { it.safeActionId + '>' + it.successfulTrapActionId }}",
            )
            node = requireNotNull(dag.nodes[edge.childStateKey])
            index += 1
        }
        assertTrue(index > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Super Hard third forced-run breaker`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "super-hard-third-forced-run-breaker",
            width = 5,
            height = 6,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
                Arrow("v61-a-14", Position(5, 2), Direction.EAST),
                Arrow("v61-a-15", Position(2, 5), Direction.EAST),
            ),
            walls = source.walls + Wall(Position(1, 5)),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..6).flatMap { row -> (1..5).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-16", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.solutionActions >= 14 && result.criticalEpisodes.size >= 4 &&
                        result.causalPhaseCount >= 3
                    ) {
                        println(
                            "V61_SUPER_THIRD_BREAKER position=$position direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    fun `delayed successful losing action receives a visible production trace`() {
        val generated = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-visible-trap-proof",
                playerFacingNumber = 35,
                ordinal = 23,
                band = AutomatedDifficultyBandV61.SUPER_HARD,
                causalFamilyIndex = 15,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )
        assertTrue(generated is V61GenerationResult.Certified)
        generated as V61GenerationResult.Certified

        val episode = generated.candidate.difficulty.criticalEpisodes.first()
        assertTrue(episode.nonCommuting)
        assertTrue(episode.trapActionWinsAtAnotherReachableState)
        assertTrue(episode.delayedConsequenceDepth >= 1)
        assertTrue(episode.proof.valid)
        assertTrue(episode.proof.trapTrace.isNotEmpty())
        assertTrue(episode.proof.explanation.contains("visible cells"))
        assertEquals(1.0, generated.candidate.difficulty.inferableCriticalDecisionRatio, 0.0)
    }

    @Test
    fun `length cannot raise a board above a missing conjunctive evidence gate`() {
        val level = forcedExitBoard(22)
        val dag = syntheticForcedDag(level)

        val result = analyzer.analyze(
            level,
            dag,
            semanticNoveltyPass = true,
            purposefulOccupancyPass = true,
        )

        assertEquals(AutomatedDifficultyBandV61.EXPERT, result.evidenceBands.lengthEligibilityBand)
        assertTrue(result.maximumEligibleBand == null || result.maximumEligibleBand!!.rank < AutomatedDifficultyBandV61.EXPERT.rank)
        assertTrue(result.policyReport.bestCheapPolicySolveRate >= 0.80)
        assertTrue(
            (result.evidenceBands.policyResistanceBand?.rank ?: 0) < AutomatedDifficultyBandV61.EXPERT.rank,
        )
    }

    @Test
    fun `inert structural padding cannot increase the conjunctive difficulty cap`() {
        val original = forcedExitBoard(22)
        val padded = original.copy(walls = listOf(Wall(Position(8, 8))))
        val originalResult = analyzer.analyze(
            original,
            syntheticForcedDag(original),
            semanticNoveltyPass = true,
            purposefulOccupancyPass = true,
        )
        val paddedResult = analyzer.analyze(
            padded,
            syntheticForcedDag(padded),
            semanticNoveltyPass = true,
            purposefulOccupancyPass = true,
        )

        assertEquals(originalResult.maximumEligibleBand, paddedResult.maximumEligibleBand)
        assertEquals(originalResult.evidenceBands, paddedResult.evidenceBands)
        assertTrue((paddedResult.maximumEligibleBand?.rank ?: 0) < AutomatedDifficultyBandV61.EXPERT.rank)
    }

    @Test
    fun `reported guessing is a hard rejection and cannot be offset by metrics`() {
        val level = delayedTrapBoard()
        val dag = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(level.initialState())
        val result = analyzer.analyze(
            level,
            dag,
            semanticNoveltyPass = true,
            purposefulOccupancyPass = true,
            knownGuessRequired = true,
        )

        assertFalse(result.accepted)
        assertFalse(result.guessDependencePass)
        assertTrue("${result.rejectionReasons}", "REJECT_GUESS_REPORTED_REGRESSION" in result.rejectionReasons)
    }

    @Test
    fun `truncated analysis fails closed`() {
        val level = delayedTrapBoard()
        val dag = CompleteDecisionDagAnalyzerV6(engine, 1, 2).analyze(level.initialState())
        val result = analyzer.analyze(
            level,
            dag,
            semanticNoveltyPass = true,
            purposefulOccupancyPass = true,
        )

        assertFalse(result.accepted)
        assertFalse(result.analysisCompletenessPass)
        assertEquals(null, result.maximumEligibleBand)
        assertTrue(result.rejectionReasons.contains("REJECT_ANALYSIS_INCOMPLETE"))
    }

    @Test
    fun `policy ensemble attacks every orientation and exposes cheap solutions`() {
        val level = delayedTrapBoard()
        val dag = CompleteDecisionDagAnalyzerV6(engine, 1_000, 10_000).analyze(level.initialState())
        val report = AdversarialPolicyEnsembleV61(stochasticRuns = 64).evaluate(dag)

        val rowMajor = report.results.filter { it.policy == AdversarialPolicyKindV61.ROW_MAJOR_SCAN }
        assertEquals(8, rowMajor.size)
        assertEquals(8, rowMajor.map { it.orientation }.toSet().size)
        assertNotNull(report.minimumReliableLookahead)
        assertTrue(report.bestCheapPolicySolveRate in 0.0..1.0)
    }

    @Test
    fun `V61 identities and capped rejection are deterministic`() {
        val request = V61GenerationRequest(
            levelId = "auto-journey-v1-1",
            playerFacingNumber = 2206,
            ordinal = 1,
            band = AutomatedDifficultyBandV61.EXPERT,
            budgets = V61GenerationBudgets(
                maximumAttempts = 2,
                decisionDagStates = 1,
                decisionDagResolutions = 1,
                solverStates = 1,
                counterfactualChecks = 1,
                canonicalBacktrackingStates = 1,
            ),
        )
        val generator = GeneratorV61(engine)
        val firstIdentity = generator.reproduceIdentity(request, 0)
        val secondIdentity = generator.reproduceIdentity(request, 0)
        assertEquals(firstIdentity, secondIdentity)
        assertTrue(firstIdentity.causalFamilyIdentifier.isNotBlank())

        val first = generator.generate(request) as V61GenerationResult.Rejected
        val second = generator.generate(request) as V61GenerationResult.Rejected
        assertEquals(first.failure.rejectionCounts, second.failure.rejectionCounts)
        assertEquals(2, first.failure.examinedAttempts)
        assertTrue(first.failure.rejectionCounts.keys.any { it.startsWith("REJECT_ANALYSIS_TRUNCATED") })
    }

    @Test
    fun `campaign identity search rotates families deterministically across attempts`() {
        val request = V61GenerationRequest(
            levelId = "campaign-family-search",
            playerFacingNumber = 17,
            ordinal = 5,
            band = AutomatedDifficultyBandV61.EASY,
            causalFamilyIndex = 9,
            varyCausalFamilyByAttempt = true,
        )
        val generator = GeneratorV61(engine)
        val identities = (0 until V61_CAUSAL_FAMILY_COUNT).map { generator.reproduceIdentity(request, it) }

        assertEquals(V61_CAUSAL_FAMILY_COUNT, identities.map { it.causalFamilyIdentifier }.toSet().size)
        assertEquals(identities, (0 until V61_CAUSAL_FAMILY_COUNT).map { generator.reproduceIdentity(request, it) })
    }

    @Test
    fun `constraint guided Easy realization accepts independent deterministic seeds without filler`() {
        val generator = GeneratorV61(engine)
        val requests = listOf(1, 9, 17).map { ordinal ->
            V61GenerationRequest(
                levelId = "v61-easy-realizer-$ordinal",
                playerFacingNumber = 12 + ordinal,
                ordinal = ordinal,
                band = AutomatedDifficultyBandV61.EASY,
                causalFamilyIndex = 5,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            )
        }
        val first = requests.map(generator::generate)
        val second = requests.map(generator::generate)

        first.zip(second).forEach { (left, right) ->
            assertTrue((left as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), left is V61GenerationResult.Certified)
            assertTrue(right is V61GenerationResult.Certified)
            left as V61GenerationResult.Certified
            right as V61GenerationResult.Certified
            assertEquals(left.candidate.fingerprints.exactLayout, right.candidate.fingerprints.exactLayout)
            assertEquals(1.0, left.candidate.purposefulOccupancy.purposefulOccupiedRatio, 0.0)
            assertEquals(0.0, left.candidate.purposefulOccupancy.inertOccupiedRatio, 0.0)
            assertTrue(left.candidate.level.walls.all { wall ->
                left.candidate.purposefulOccupancy.objectWitnesses[
                    "wall:${wall.position.row},${wall.position.column}",
                ].orEmpty().isNotEmpty()
            })
        }
        assertEquals(
            first.map { (it as V61GenerationResult.Certified).candidate.fingerprints.d4Layout }.size,
            first.map { (it as V61GenerationResult.Certified).candidate.fingerprints.d4Layout }.toSet().size,
        )
    }

    @Test
    fun `interacting-chain Easy realization uses only witnessed controllers`() {
        val request = V61GenerationRequest(
            levelId = "v61-easy-interacting-chain",
            playerFacingNumber = 81,
            ordinal = 69,
            band = AutomatedDifficultyBandV61.EASY,
            causalFamilyIndex = 7,
            budgets = V61GenerationBudgets(maximumAttempts = 64),
        )

        val result = GeneratorV61(engine).generate(request)

        assertTrue(
            (result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(),
            result is V61GenerationResult.Certified,
        )
        result as V61GenerationResult.Certified
        assertEquals(3, result.candidate.level.arrows.size)
        assertEquals(2, result.candidate.level.magnets.size)
        assertTrue(result.candidate.level.walls.isEmpty())
        assertEquals(1.0, result.candidate.purposefulOccupancy.purposefulOccupiedRatio, 0.0)
        assertEquals(1.0, result.candidate.purposefulOccupancy.purposefulMagnetRatio, 0.0)
        assertEquals(0.0, result.candidate.purposefulOccupancy.inertOccupiedRatio, 0.0)
    }

    @Test
    fun `campaign Easy uses graph first capacity while preserving every admission gate`() {
        val result = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "campaign-easy-capacity",
                playerFacingNumber = 331,
                ordinal = 319,
                band = AutomatedDifficultyBandV61.EASY,
                causalFamilyIndex = 0,
                varyCausalFamilyByAttempt = true,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )

        assertTrue(
            (result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(),
            result is V61GenerationResult.Certified,
        )
        result as V61GenerationResult.Certified
        assertTrue(result.candidate.difficulty.accepted)
        assertTrue(result.candidate.difficulty.inferableCriticalDecisionRatio == 1.0)
        assertTrue(result.candidate.difficulty.guessDependencePass)
        assertTrue(result.candidate.independentSolverVerified)
        assertTrue(result.candidate.purposefulOccupancy.occupiedRatio in 0.45..0.62)
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.EASY.rank)
    }

    @Test
    fun `constraint guided Medium realization reaches decision gates without filler`() {
        val request = V61GenerationRequest(
            levelId = "v61-medium-realizer",
            playerFacingNumber = 15,
            ordinal = 3,
            band = AutomatedDifficultyBandV61.MEDIUM,
            causalFamilyIndex = 0,
            budgets = V61GenerationBudgets(maximumAttempts = 64),
        )
        val result = GeneratorV61(engine).generate(request)

        assertTrue((result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), result is V61GenerationResult.Certified)
        result as V61GenerationResult.Certified
        assertEquals(6, result.candidate.level.arrows.size)
        assertEquals(1.0, result.candidate.purposefulOccupancy.purposefulOccupiedRatio, 0.0)
        assertEquals(0.0, result.candidate.purposefulOccupancy.inertOccupiedRatio, 0.0)
        assertTrue(result.candidate.level.walls.all { wall ->
            result.candidate.purposefulOccupancy.objectWitnesses[
                "wall:${wall.position.row},${wall.position.column}",
            ].orEmpty().isNotEmpty()
        })
        assertTrue(result.candidate.difficulty.criticalEpisodes.isNotEmpty())
        assertTrue(result.candidate.difficulty.inferableCriticalDecisionRatio == 1.0)
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.MEDIUM.rank)
    }

    @Test
    fun `campaign Medium uses bounded graph first capacity without changing admission gates`() {
        val result = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "campaign-medium-capacity",
                playerFacingNumber = 92,
                ordinal = 80,
                band = AutomatedDifficultyBandV61.MEDIUM,
                causalFamilyIndex = 11,
                varyCausalFamilyByAttempt = true,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )

        assertTrue(
            (result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(),
            result is V61GenerationResult.Certified,
        )
        result as V61GenerationResult.Certified
        assertTrue(result.candidate.difficulty.accepted)
        assertTrue(result.candidate.difficulty.inferableCriticalDecisionRatio == 1.0)
        assertTrue(result.candidate.difficulty.guessDependencePass)
        assertTrue(result.candidate.independentSolverVerified)
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.MEDIUM.rank)
    }

    @Test
    fun `constraint guided Hard realization requires three visible decisions and resists cheap policies`() {
        val request = V61GenerationRequest(
            levelId = "v61-hard-realizer",
            playerFacingNumber = 22,
            ordinal = 10,
            band = AutomatedDifficultyBandV61.HARD,
            causalFamilyIndex = 7,
            budgets = V61GenerationBudgets(maximumAttempts = 64),
        )
        val result = GeneratorV61(engine).generate(request)

        assertTrue((result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), result is V61GenerationResult.Certified)
        result as V61GenerationResult.Certified
        assertEquals(12, result.candidate.level.arrows.size)
        assertEquals(1.0, result.candidate.purposefulOccupancy.purposefulOccupiedRatio, 0.0)
        assertTrue(result.candidate.difficulty.criticalEpisodes.size >= 3)
        assertTrue(result.candidate.difficulty.inferableCriticalDecisionRatio == 1.0)
        assertTrue(result.candidate.difficulty.policyReport.bestCheapPolicySolveRate <= 0.40)
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.HARD.rank)
    }

    @Test
    fun `campaign Hard has scalable expert graph proposals under unchanged Hard gates`() {
        val result = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "campaign-hard-capacity",
                playerFacingNumber = 613,
                ordinal = 601,
                band = AutomatedDifficultyBandV61.HARD,
                causalFamilyIndex = 2,
                varyCausalFamilyByAttempt = true,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )

        assertTrue(
            (result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(),
            result is V61GenerationResult.Certified,
        )
        result as V61GenerationResult.Certified
        assertTrue(result.candidate.difficulty.accepted)
        assertTrue(result.candidate.difficulty.inferableCriticalDecisionRatio == 1.0)
        assertTrue(result.candidate.difficulty.guessDependencePass)
        assertTrue(result.candidate.independentSolverVerified)
        assertTrue(result.candidate.purposefulOccupancy.occupiedRatio in 0.55..0.72)
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.HARD.rank)
    }

    @Test
    fun `constraint guided Hard cancellation family has a witnessed cancellation transition`() {
        val request = V61GenerationRequest(
            levelId = "v61-hard-cancellation-realizer",
            playerFacingNumber = 41,
            ordinal = 29,
            band = AutomatedDifficultyBandV61.HARD,
            causalFamilyIndex = 21,
            budgets = V61GenerationBudgets(maximumAttempts = 64),
        )
        val result = GeneratorV61(engine).generate(request)

        assertTrue((result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), result is V61GenerationResult.Certified)
        result as V61GenerationResult.Certified
        assertTrue(result.candidate.causalSpec.hyperedges.any { edge ->
            edge.effect == CausalEffectV6.RELEASES_CANCELLATION || edge.effect == CausalEffectV6.CREATES_CANCELLATION
        })
        assertTrue(result.candidate.causalWitnesses.any { witness ->
            witness.claimedEffect == CausalEffectV6.RELEASES_CANCELLATION ||
                witness.claimedEffect == CausalEffectV6.CREATES_CANCELLATION
        })
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.HARD.rank)
    }

    @Test
    fun `later delayed-trap Hard families use witnessed cancellation branches`() {
        val requests = listOf(
            V61GenerationRequest(
                levelId = "v61-hard-wrong-now-v2",
                playerFacingNumber = 85,
                ordinal = 73,
                band = AutomatedDifficultyBandV61.HARD,
                causalFamilyIndex = 10,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
            V61GenerationRequest(
                levelId = "v61-hard-opening-endgame-v3",
                playerFacingNumber = 93,
                ordinal = 81,
                band = AutomatedDifficultyBandV61.HARD,
                causalFamilyIndex = 23,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )

        requests.map { GeneratorV61(engine).generate(it) }.forEach { result ->
            assertTrue(
                (result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(),
                result is V61GenerationResult.Certified,
            )
            result as V61GenerationResult.Certified
            assertEquals(16, result.candidate.level.arrows.size)
            assertEquals(1.0, result.candidate.purposefulOccupancy.purposefulOccupiedRatio, 0.0)
            assertEquals(0.0, result.candidate.purposefulOccupancy.inertOccupiedRatio, 0.0)
            assertTrue(result.candidate.difficulty.criticalEpisodes.size >= 3)
            assertTrue(result.candidate.difficulty.persistentSuccessfulTrapCount >= 1)
            assertTrue(result.candidate.difficulty.inferableCriticalDecisionRatio == 1.0)
            assertTrue(result.candidate.difficulty.policyReport.bestCheapPolicySolveRate <= 0.40)
            assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.HARD.rank)
        }
    }

    @Test
    fun `constraint guided Super Hard realization is multi-phase inferable and policy resistant`() {
        val request = V61GenerationRequest(
            levelId = "v61-super-hard-realizer",
            playerFacingNumber = 35,
            ordinal = 23,
            band = AutomatedDifficultyBandV61.SUPER_HARD,
            causalFamilyIndex = 15,
            budgets = V61GenerationBudgets(maximumAttempts = 64),
        )
        val result = GeneratorV61(engine).generate(request)

        assertTrue((result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), result is V61GenerationResult.Certified)
        result as V61GenerationResult.Certified
        assertTrue(result.candidate.level.arrows.size >= 14)
        assertTrue(result.candidate.synthesisGraphFingerprint?.startsWith("sha256:") == true)
        assertTrue(result.candidate.level.width <= 8 && result.candidate.level.height <= 8)
        assertTrue(result.candidate.purposefulOccupancy.purposefulOccupiedRatio >= 0.90)
        assertTrue(result.candidate.difficulty.criticalEpisodes.size >= 4)
        assertTrue(result.candidate.difficulty.persistentSuccessfulTrapCount >= 2)
        assertTrue(result.candidate.difficulty.causalPhaseCount >= 3)
        assertTrue(result.candidate.difficulty.crossChainDependencyCount >= 2)
        assertEquals(1.0, result.candidate.difficulty.inferableCriticalDecisionRatio, 0.0)
        assertTrue(result.candidate.difficulty.maximumForcedRun <= 3)
        assertTrue(result.candidate.difficulty.forcedCleanupTailRatio <= 0.30)
        assertTrue(result.candidate.difficulty.policyReport.bestCheapPolicySolveRate <= 0.20)
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.SUPER_HARD.rank)
    }

    @Test
    fun `constraint guided Super Hard cancellation family remains witnessed and policy resistant`() {
        val result = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-super-hard-cancellation",
                playerFacingNumber = 55,
                ordinal = 43,
                band = AutomatedDifficultyBandV61.SUPER_HARD,
                causalFamilyIndex = 5,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )

        assertTrue((result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), result is V61GenerationResult.Certified)
        result as V61GenerationResult.Certified
        assertTrue(result.candidate.causalWitnesses.any { witness ->
            witness.claimedEffect == CausalEffectV6.RELEASES_CANCELLATION ||
                witness.claimedEffect == CausalEffectV6.CREATES_CANCELLATION
        })
        assertTrue(result.candidate.difficulty.criticalEpisodes.size >= 4)
        assertTrue(result.candidate.difficulty.maximumForcedRun <= 3)
        assertTrue(result.candidate.difficulty.policyReport.bestCheapPolicySolveRate <= 0.20)
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.SUPER_HARD.rank)
    }

    @Test
    fun `nested Super Hard cancellation spine escapes representative archive clusters`() {
        val generator = GeneratorV61(engine)
        fun certified(levelNumber: Int, ordinal: Int, familyIndex: Int): V61GenerationResult.Certified =
            generator.generate(
                V61GenerationRequest(
                    levelId = "v61-super-archive-$levelNumber",
                    playerFacingNumber = levelNumber,
                    ordinal = ordinal,
                    band = AutomatedDifficultyBandV61.SUPER_HARD,
                    causalFamilyIndex = familyIndex,
                    budgets = V61GenerationBudgets(maximumAttempts = 64),
                ),
            ) as V61GenerationResult.Certified

        val archive = listOf(
            certified(55, 43, 5),
            certified(64, 52, 17),
            certified(70, 58, 12),
            certified(75, 63, 0),
            certified(78, 66, 6),
        ).map { it.candidate.fingerprints }
        val candidate = generator.generate(
            V61GenerationRequest(
                levelId = "v61-super-nested-79",
                playerFacingNumber = 79,
                ordinal = 67,
                band = AutomatedDifficultyBandV61.SUPER_HARD,
                causalFamilyIndex = 9,
                knownFingerprints = archive,
                forbiddenStrategyClusters = archive.mapTo(mutableSetOf()) { it.strategyBehaviourClusterIdentifier },
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )

        assertTrue((candidate as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), candidate is V61GenerationResult.Certified)
        candidate as V61GenerationResult.Certified
        assertTrue(candidate.candidate.level.arrows.size >= 14)
        assertTrue(candidate.candidate.synthesisGraphFingerprint?.startsWith("sha256:") == true)
        assertTrue(candidate.candidate.difficulty.criticalEpisodes.size >= 4)
        assertTrue(candidate.candidate.difficulty.persistentSuccessfulTrapCount >= 2)
        assertTrue(candidate.candidate.difficulty.causalPhaseCount >= 3)
        assertTrue(candidate.candidate.difficulty.crossChainDependencyCount >= 2)
        assertEquals(1.0, candidate.candidate.difficulty.inferableCriticalDecisionRatio, 0.0)
        assertTrue(candidate.candidate.difficulty.policyReport.bestCheapPolicySolveRate <= 0.20)
        assertTrue(candidate.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.SUPER_HARD.rank)
    }

    @Test
    fun `constraint guided Expert realization has six distributed decisions and no shallow dominator`() {
        val request = V61GenerationRequest(
            levelId = "v61-expert-realizer",
            playerFacingNumber = 47,
            ordinal = 35,
            band = AutomatedDifficultyBandV61.EXPERT,
            causalFamilyIndex = 1,
            budgets = V61GenerationBudgets(maximumAttempts = 64),
        )
        val result = GeneratorV61(engine).generate(request)

        assertTrue((result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), result is V61GenerationResult.Certified)
        result as V61GenerationResult.Certified
        assertEquals(18, result.candidate.level.arrows.size)
        assertTrue(result.candidate.purposefulOccupancy.purposefulOccupiedRatio >= 0.90)
        assertTrue(result.candidate.difficulty.criticalEpisodes.size >= 6)
        assertTrue(result.candidate.difficulty.persistentSuccessfulTrapCount >= 3)
        assertTrue(result.candidate.difficulty.causalPhaseCount >= 3)
        assertTrue(result.candidate.difficulty.crossChainDependencyCount >= 2)
        assertEquals(1.0, result.candidate.difficulty.inferableCriticalDecisionRatio, 0.0)
        assertTrue(result.candidate.difficulty.maximumForcedRun <= 3)
        assertTrue(result.candidate.difficulty.forcedCleanupTailRatio <= 0.25)
        assertTrue(result.candidate.difficulty.policyReport.bestCheapPolicySolveRate <= 0.10)
        assertTrue(result.candidate.difficulty.policyReport.results.none { policy ->
            policy.lookaheadDepth in 0..3 && policy.solveRate >= 0.80
        })
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.EXPERT.rank)
    }

    @Test
    fun `constraint guided Expert occlusion family uses a distinct cancellation braid`() {
        val result = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-expert-occlusion-braid",
                playerFacingNumber = 56,
                ordinal = 44,
                band = AutomatedDifficultyBandV61.EXPERT,
                causalFamilyIndex = 20,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )

        assertTrue((result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), result is V61GenerationResult.Certified)
        result as V61GenerationResult.Certified
        assertTrue(result.candidate.causalWitnesses.any { witness ->
            witness.claimedEffect == CausalEffectV6.REVEALS_CONTROLLER ||
                witness.claimedEffect == CausalEffectV6.OCCLUDES_CONTROLLER
        })
        assertTrue(result.candidate.difficulty.criticalEpisodes.size >= 6)
        assertTrue(result.candidate.difficulty.causalPhaseCount >= 3)
        assertTrue(result.candidate.difficulty.maximumForcedRun <= 3)
        assertTrue(result.candidate.difficulty.policyReport.bestCheapPolicySolveRate <= 0.10)
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.EXPERT.rank)
    }

    @Test
    fun `expanded cross handoff is a complete distinct Expert realization`() {
        val result = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-expert-cross-handoff",
                playerFacingNumber = 67,
                ordinal = 55,
                band = AutomatedDifficultyBandV61.EXPERT,
                causalFamilyIndex = 19,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )

        assertTrue((result as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), result is V61GenerationResult.Certified)
        result as V61GenerationResult.Certified
        assertEquals(5, result.candidate.level.width)
        assertEquals(6, result.candidate.level.height)
        assertEquals(18, result.candidate.level.arrows.size)
        assertTrue(result.candidate.decisionDag.complete)
        assertTrue(result.candidate.difficulty.criticalEpisodes.size >= 6)
        assertTrue(result.candidate.difficulty.causalPhaseCount >= 3)
        assertTrue(result.candidate.difficulty.crossChainDependencyCount >= 2)
        assertEquals(1.0, result.candidate.difficulty.inferableCriticalDecisionRatio, 0.0)
        assertTrue(result.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.EXPERT.rank)
    }

    @Test
    fun `opening delayed Expert produces a new semantic policy after cross handoff`() {
        val generator = GeneratorV61(engine)
        val cross = generator.generate(
            V61GenerationRequest(
                levelId = "v61-expert-cross-archive-source",
                playerFacingNumber = 67,
                ordinal = 55,
                band = AutomatedDifficultyBandV61.EXPERT,
                causalFamilyIndex = 19,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        ) as V61GenerationResult.Certified
        val delayed = generator.generate(
            V61GenerationRequest(
                levelId = "v61-expert-delayed-archive-candidate",
                playerFacingNumber = 80,
                ordinal = 68,
                band = AutomatedDifficultyBandV61.EXPERT,
                causalFamilyIndex = 15,
                knownFingerprints = listOf(cross.candidate.fingerprints),
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )

        assertTrue((delayed as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), delayed is V61GenerationResult.Certified)
        delayed as V61GenerationResult.Certified
        assertTrue(delayed.candidate.examinedAttempts > 1)
        assertFalse(delayed.candidate.fingerprints.causalHypergraph == cross.candidate.fingerprints.causalHypergraph)
        assertFalse(delayed.candidate.fingerprints.quotientDecisionDag == cross.candidate.fingerprints.quotientDecisionDag)
        assertFalse(delayed.candidate.fingerprints.solutionPolicy == cross.candidate.fingerprints.solutionPolicy)
        assertTrue(delayed.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.EXPERT.rank)
    }

    @Test
    fun `wrong now Expert avoids the preceding Expert policy and behavior cluster`() {
        val generator = GeneratorV61(engine)
        val preceding = generator.generate(
            V61GenerationRequest(
                levelId = "v61-expert-preceding-source",
                playerFacingNumber = 47,
                ordinal = 35,
                band = AutomatedDifficultyBandV61.EXPERT,
                causalFamilyIndex = 1,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        ) as V61GenerationResult.Certified
        val candidate = generator.generate(
            V61GenerationRequest(
                levelId = "v61-expert-wrong-now-candidate",
                playerFacingNumber = 52,
                ordinal = 40,
                band = AutomatedDifficultyBandV61.EXPERT,
                causalFamilyIndex = 18,
                knownFingerprints = listOf(preceding.candidate.fingerprints),
                forbiddenStrategyClusters = setOf(preceding.candidate.fingerprints.strategyBehaviourClusterIdentifier),
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )

        assertTrue((candidate as? V61GenerationResult.Rejected)?.failure?.rejectionCounts.toString(), candidate is V61GenerationResult.Certified)
        candidate as V61GenerationResult.Certified
        assertFalse(candidate.candidate.fingerprints.solutionPolicy == preceding.candidate.fingerprints.solutionPolicy)
        assertFalse(
            candidate.candidate.fingerprints.strategyBehaviourClusterIdentifier ==
                preceding.candidate.fingerprints.strategyBehaviourClusterIdentifier,
        )
        assertTrue(candidate.candidate.difficulty.maximumEligibleBand!!.rank >= AutomatedDifficultyBandV61.EXPERT.rank)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Expert arrow refinements`() {
        val sourceResult = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-expert-source",
                playerFacingNumber = 47,
                ordinal = 35,
                band = AutomatedDifficultyBandV61.SUPER_HARD,
                causalFamilyIndex = 15,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )
        assertTrue(sourceResult is V61GenerationResult.Certified)
        val base = (sourceResult as V61GenerationResult.Certified).candidate.level.copy(designedSolutions = emptyList())
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..base.height).flatMap { row -> (1..base.width).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-17", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 4) {
                        println(
                            "V61_EXPERT_ARROW position=$position direction=$direction " +
                                "actions=${result.solutionActions} decisions=${result.criticalEpisodes.size} " +
                                "phases=${result.causalPhaseCount} traps=${result.persistentSuccessfulTrapCount} " +
                                "forced=${result.maximumForcedRun} cleanup=${result.forcedCleanupTailRatio} " +
                                "cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Expert magnet refinements`() {
        val sourceResult = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-expert-magnet-source",
                playerFacingNumber = 47,
                ordinal = 35,
                band = AutomatedDifficultyBandV61.SUPER_HARD,
                causalFamilyIndex = 15,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )
        assertTrue(sourceResult is V61GenerationResult.Certified)
        val base = (sourceResult as V61GenerationResult.Certified).candidate.level.copy(designedSolutions = emptyList())
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..base.height).flatMap { row -> (1..base.width).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position -> Polarity.entries.forEach { polarity ->
                val level = base.copy(magnets = base.magnets + Magnet("v61-m-02", position, polarity))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    val cancellation = ProductionCausalSpecExtractorV6(engine).extract(
                        GeneratorV6Identity(1L, 4, CausalGrammarFamilyV6.CANCELLATION_RELEASE, 1),
                        V6Profiles.forBucket(4).copy(minimumInteractingChains = 3),
                        level,
                        dag,
                    ) != null
                    if (result.criticalEpisodes.size >= 4) {
                        println(
                            "V61_EXPERT_MAGNET position=$position polarity=$polarity " +
                                "actions=${result.solutionActions} decisions=${result.criticalEpisodes.size} " +
                                "phases=${result.causalPhaseCount} traps=${result.persistentSuccessfulTrapCount} " +
                                "forced=${result.maximumForcedRun} cleanup=${result.forcedCleanupTailRatio} " +
                                "cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }} " +
                                "cancellation=$cancellation",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Expert magnet and arrow refinements`() {
        val sourceResult = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-expert-pair-source",
                playerFacingNumber = 47,
                ordinal = 35,
                band = AutomatedDifficultyBandV61.SUPER_HARD,
                causalFamilyIndex = 15,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )
        assertTrue(sourceResult is V61GenerationResult.Certified)
        val source = (sourceResult as V61GenerationResult.Certified).candidate.level
        val base = source.copy(
            arrows = source.arrows.map { arrow ->
                if (arrow.id == "v61-a-15") arrow.copy(printedDirection = Direction.SOUTH) else arrow
            },
            magnets = source.magnets + Magnet("v61-m-02", Position(6, 4), Polarity.PUSH),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..base.height).flatMap { row -> (1..base.width).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-17", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 6) {
                        println(
                            "V61_EXPERT_PAIR position=$position direction=$direction " +
                                "actions=${result.solutionActions} decisions=${result.criticalEpisodes.size} " +
                                "phases=${result.causalPhaseCount} traps=${result.persistentSuccessfulTrapCount} " +
                                "forced=${result.maximumForcedRun} cleanup=${result.forcedCleanupTailRatio} " +
                                "cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }} " +
                                "inferable=${result.inferableCriticalDecisionRatio} " +
                                "invalid=${result.criticalEpisodes.filterNot { it.proof.valid }.map { it.safeActionId + '>' + it.successfulTrapActionId + ':' + it.proof.invalidReasons }}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Expert final forced-run breaker`() {
        val sourceResult = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-expert-final-source",
                playerFacingNumber = 47,
                ordinal = 35,
                band = AutomatedDifficultyBandV61.SUPER_HARD,
                causalFamilyIndex = 15,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )
        assertTrue(sourceResult is V61GenerationResult.Certified)
        val source = (sourceResult as V61GenerationResult.Certified).candidate.level
        val base = source.copy(
            arrows = source.arrows + Arrow("v61-a-17", Position(5, 5), Direction.EAST),
            magnets = source.magnets + Magnet("v61-m-02", Position(6, 4), Polarity.PUSH),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..base.height).flatMap { row -> (1..base.width).map { column -> Position(row, column) } }
            .filterNot(occupied::contains)
            .forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-18", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 6) {
                        println(
                            "V61_EXPERT_FINAL position=$position direction=$direction " +
                                "actions=${result.solutionActions} decisions=${result.criticalEpisodes.size} " +
                                "phases=${result.causalPhaseCount} traps=${result.persistentSuccessfulTrapCount} " +
                                "forced=${result.maximumForcedRun} cleanup=${result.forcedCleanupTailRatio} " +
                                "cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Expert branch geometry refinements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val refinements = listOf(
            Position(4, 5) to Direction.EAST,
            Position(4, 5) to Direction.SOUTH,
            Position(5, 4) to Direction.EAST,
            Position(5, 4) to Direction.SOUTH,
            Position(5, 5) to Direction.EAST,
            Position(5, 5) to Direction.SOUTH,
            Position(6, 1) to Direction.EAST,
            Position(6, 1) to Direction.SOUTH,
            Position(6, 1) to Direction.WEST,
            Position(6, 2) to Direction.EAST,
            Position(6, 2) to Direction.SOUTH,
            Position(6, 2) to Direction.WEST,
            Position(6, 3) to Direction.EAST,
            Position(6, 3) to Direction.SOUTH,
            Position(6, 3) to Direction.WEST,
            Position(6, 4) to Direction.EAST,
            Position(6, 4) to Direction.SOUTH,
            Position(6, 4) to Direction.WEST,
            Position(6, 5) to Direction.EAST,
            Position(6, 5) to Direction.SOUTH,
            Position(6, 5) to Direction.WEST,
        )
        var examined = 0
        refinements.filterNot { it.first == Position(6, 4) }.forEachIndexed { index, refinement ->
            val level = source.copy(
                id = "v61-expert-branch-$index",
                width = 5,
                height = 6,
                arrows = source.arrows + listOf(
                    Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                    Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                    Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                    Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                    Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
                    Arrow("v61-a-14", Position(5, 2), Direction.EAST),
                    Arrow("v61-a-15", Position(2, 5), Direction.EAST),
                    Arrow("v61-a-16", refinement.first, refinement.second),
                ),
                magnets = source.magnets + Magnet("v61-m-02", Position(6, 4), Polarity.PUSH),
                walls = source.walls + Wall(Position(1, 5)),
                designedSolutions = emptyList(),
            )
            val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
            if (dag.complete && dag.metrics?.shortestCompletion != null) {
                val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                if (result.criticalEpisodes.size >= 5) {
                    println(
                        "V61_EXPERT_BRANCH refinement=$refinement actions=${result.solutionActions} " +
                            "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                            "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                            "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                            "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                    )
                }
            }
            examined += 1
        }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Expert branch and controller refinements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val refinements = listOf(
            Position(4, 5) to Direction.EAST, Position(4, 5) to Direction.SOUTH,
            Position(5, 4) to Direction.EAST, Position(5, 4) to Direction.SOUTH,
            Position(5, 5) to Direction.EAST, Position(5, 5) to Direction.SOUTH,
            Position(6, 1) to Direction.EAST, Position(6, 1) to Direction.SOUTH, Position(6, 1) to Direction.WEST,
            Position(6, 2) to Direction.EAST, Position(6, 2) to Direction.SOUTH, Position(6, 2) to Direction.WEST,
            Position(6, 3) to Direction.EAST, Position(6, 3) to Direction.SOUTH, Position(6, 3) to Direction.WEST,
            Position(6, 4) to Direction.EAST, Position(6, 4) to Direction.SOUTH, Position(6, 4) to Direction.WEST,
            Position(6, 5) to Direction.EAST, Position(6, 5) to Direction.SOUTH, Position(6, 5) to Direction.WEST,
        )
        var examined = 0
        refinements.forEachIndexed { index, refinement ->
            val branch = source.copy(
                id = "v61-expert-controller-$index",
                width = 5,
                height = 6,
                arrows = source.arrows + listOf(
                    Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                    Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                    Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                    Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                    Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
                    Arrow("v61-a-14", Position(5, 2), Direction.EAST),
                    Arrow("v61-a-15", Position(2, 5), Direction.EAST),
                    Arrow("v61-a-16", refinement.first, refinement.second),
                ),
                walls = source.walls + Wall(Position(1, 5)),
                designedSolutions = emptyList(),
            )
            val occupied = (branch.arrows.map { it.position } + branch.magnets.map { it.position } + branch.walls.map { it.position }).toSet()
            (1..branch.height).flatMap { row -> (1..branch.width).map { column -> Position(row, column) } }
                .filterNot(occupied::contains).forEach { position ->
                Polarity.entries.forEach { polarity ->
                    val level = branch.copy(magnets = branch.magnets + Magnet("v61-m-02", position, polarity))
                    val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                    if (dag.complete && dag.metrics?.shortestCompletion != null) {
                        val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                        val cancellation = ProductionCausalSpecExtractorV6(engine).extract(
                            GeneratorV6Identity(1L, 4, CausalGrammarFamilyV6.CANCELLATION_RELEASE, 1),
                            V6Profiles.forBucket(4).copy(minimumInteractingChains = 3),
                            level,
                            dag,
                        ) != null
                        if (cancellation && result.criticalEpisodes.size >= 4) {
                            println(
                                "V61_SUPER_CANCELLATION branch=$refinement magnet=$position polarity=$polarity " +
                                    "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                    "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                    "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                    "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                            )
                        }
                        if (result.criticalEpisodes.size >= 6) {
                            println(
                                "V61_EXPERT_CONTROLLER branch=$refinement magnet=$position polarity=$polarity " +
                                    "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                    "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                    "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                    "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                            )
                        }
                    }
                    examined += 1
                }
            }
        }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Expert interaction direction refinements`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "v61-expert-direction",
            width = 5,
            height = 6,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
                Arrow("v61-a-14", Position(5, 2), Direction.EAST),
                Arrow("v61-a-15", Position(2, 5), Direction.EAST),
                Arrow("v61-a-16", Position(4, 5), Direction.EAST),
            ),
            magnets = source.magnets + Magnet("v61-m-02", Position(6, 4), Polarity.PUSH),
            walls = source.walls + Wall(Position(1, 5)),
            designedSolutions = emptyList(),
        )
        var examined = 0
        base.arrows.filter { it.id.startsWith("v61-") }.forEach { arrow ->
            Direction.entries.filterNot { it == arrow.printedDirection }.forEach { direction ->
                val level = base.copy(arrows = base.arrows.map { if (it.id == arrow.id) it.copy(printedDirection = direction) else it })
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 6) {
                        println(
                            "V61_EXPERT_DIRECTION arrow=${arrow.id} direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            }
        }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic certified Expert semantic direction variants`() {
        val catalog = LevelParser().parseCatalog(
            requireNotNull(javaClass.getResource("/content/generator_v6/staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json"))
                .readText(),
        )
        val source = catalog.levels.single { it.id == "v6-calibration-030" }
        val base = source.copy(
            id = "v61-expert-semantic-direction",
            width = 5,
            height = 6,
            arrows = source.arrows + listOf(
                Arrow("v61-a-09", Position(3, 2), Direction.SOUTH),
                Arrow("v61-a-10", Position(2, 4), Direction.NORTH),
                Arrow("v61-a-11", Position(3, 5), Direction.EAST),
                Arrow("v61-a-12", Position(5, 3), Direction.NORTH),
                Arrow("v61-a-13", Position(5, 1), Direction.NORTH),
                Arrow("v61-a-14", Position(5, 2), Direction.EAST),
                Arrow("v61-a-15", Position(2, 5), Direction.SOUTH),
                Arrow("v61-a-16", Position(4, 5), Direction.EAST),
                Arrow("v61-a-17", Position(5, 5), Direction.EAST),
            ),
            magnets = source.magnets + Magnet("v61-m-02", Position(6, 4), Polarity.PUSH),
            walls = source.walls + Wall(Position(1, 5)),
            designedSolutions = emptyList(),
        )
        var examined = 0
        base.arrows.filter { it.id.startsWith("v61-") && it.id != "v61-a-15" }.forEach { arrow ->
            Direction.entries.filterNot { it == arrow.printedDirection }.forEach { direction ->
                val level = base.copy(arrows = base.arrows.map { if (it.id == arrow.id) it.copy(printedDirection = direction) else it })
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.accepted &&
                        (result.maximumEligibleBand?.rank ?: 0) >= AutomatedDifficultyBandV61.EXPERT.rank
                    ) {
                        println(
                            "V61_EXPERT_SEMANTIC arrow=${arrow.id} direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            }
        }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic cancellation Expert controller refinements`() {
        val sourceResult = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-cancellation-expert-source",
                playerFacingNumber = 56,
                ordinal = 44,
                band = AutomatedDifficultyBandV61.HARD,
                causalFamilyIndex = 5,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )
        assertTrue(sourceResult is V61GenerationResult.Certified)
        val base = (sourceResult as V61GenerationResult.Certified).candidate.level.copy(designedSolutions = emptyList())
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..base.height).flatMap { row -> (1..base.width).map { column -> Position(row, column) } }
            .filterNot(occupied::contains).forEach { position ->
                Polarity.entries.forEach { polarity ->
                    val level = base.copy(magnets = base.magnets + Magnet("v61-m-04", position, polarity))
                    val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                    if (dag.complete && dag.metrics?.shortestCompletion != null) {
                        val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                        if (result.criticalEpisodes.size >= 5) {
                            println(
                                "V61_CANCEL_EXPERT_MAGNET position=$position polarity=$polarity " +
                                    "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                    "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                    "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                    "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                            )
                        }
                    }
                    examined += 1
                }
            }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic cancellation Expert first branch arrow`() {
        val sourceResult = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-cancellation-expert-arrow-source",
                playerFacingNumber = 56,
                ordinal = 44,
                band = AutomatedDifficultyBandV61.HARD,
                causalFamilyIndex = 5,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )
        assertTrue(sourceResult is V61GenerationResult.Certified)
        val source = (sourceResult as V61GenerationResult.Certified).candidate.level
        val base = source.copy(
            magnets = source.magnets + Magnet("v61-m-04", Position(5, 5), Polarity.PULL),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..base.height).flatMap { row -> (1..base.width).map { column -> Position(row, column) } }
            .filterNot(occupied::contains).forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-15", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 6) {
                        println(
                            "V61_CANCEL_EXPERT_ARROW position=$position direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic cancellation Expert second branch arrow`() {
        val sourceResult = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-cancellation-expert-second-source",
                playerFacingNumber = 56,
                ordinal = 44,
                band = AutomatedDifficultyBandV61.HARD,
                causalFamilyIndex = 5,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )
        assertTrue(sourceResult is V61GenerationResult.Certified)
        val source = (sourceResult as V61GenerationResult.Certified).candidate.level
        val base = source.copy(
            arrows = source.arrows + Arrow("v61-a-15", Position(1, 5), Direction.NORTH),
            magnets = source.magnets + Magnet("v61-m-04", Position(5, 5), Polarity.PULL),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..base.height).flatMap { row -> (1..base.width).map { column -> Position(row, column) } }
            .filterNot(occupied::contains).forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-16", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 6) {
                        println(
                            "V61_CANCEL_EXPERT_SECOND position=$position direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic cancellation Expert final branch arrow`() {
        val sourceResult = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-cancellation-expert-final-source",
                playerFacingNumber = 56,
                ordinal = 44,
                band = AutomatedDifficultyBandV61.HARD,
                causalFamilyIndex = 5,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )
        assertTrue(sourceResult is V61GenerationResult.Certified)
        val source = (sourceResult as V61GenerationResult.Certified).candidate.level
        val base = source.copy(
            arrows = source.arrows + listOf(
                Arrow("v61-a-15", Position(1, 5), Direction.NORTH),
                Arrow("v61-a-16", Position(1, 2), Direction.NORTH),
            ),
            magnets = source.magnets + Magnet("v61-m-04", Position(5, 5), Polarity.PULL),
            designedSolutions = emptyList(),
        )
        val occupied = (base.arrows.map { it.position } + base.magnets.map { it.position } + base.walls.map { it.position }).toSet()
        var examined = 0
        (1..base.height).flatMap { row -> (1..base.width).map { column -> Position(row, column) } }
            .filterNot(occupied::contains).forEach { position -> Direction.entries.forEach { direction ->
                val level = base.copy(arrows = base.arrows + Arrow("v61-a-17", position, direction))
                val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
                if (dag.complete && dag.metrics?.shortestCompletion != null) {
                    val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
                    if (result.criticalEpisodes.size >= 6) {
                        println(
                            "V61_CANCEL_EXPERT_FINAL position=$position direction=$direction " +
                                "decisions=${result.criticalEpisodes.size} phases=${result.causalPhaseCount} " +
                                "traps=${result.persistentSuccessfulTrapCount} forced=${result.maximumForcedRun} " +
                                "cleanup=${result.forcedCleanupTailRatio} cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                                "lookahead=${result.criticalEpisodes.maxOf { it.proof.minimumVisibleLookahead }} " +
                                "inferable=${result.inferableCriticalDecisionRatio}",
                        )
                    }
                }
                examined += 1
            } }
        assertTrue(examined > 0)
    }

    @Test
    @org.junit.Ignore("Exploratory bounded synthesis trace; focused production regression is retained below.")
    fun `diagnostic Master expanded cancellation braid`() {
        val sourceResult = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-master-expanded-source",
                playerFacingNumber = 58,
                ordinal = 46,
                band = AutomatedDifficultyBandV61.HARD,
                causalFamilyIndex = 5,
                budgets = V61GenerationBudgets(maximumAttempts = 64),
            ),
        )
        assertTrue(sourceResult is V61GenerationResult.Certified)
        val source = (sourceResult as V61GenerationResult.Certified).candidate.level
        val level = source.copy(
            id = "v61-master-expanded",
            width = 6,
            arrows = source.arrows + listOf(
                Arrow("v61-a-15", Position(1, 5), Direction.NORTH),
                Arrow("v61-a-16", Position(1, 2), Direction.NORTH),
                Arrow("v61-a-17", Position(2, 5), Direction.NORTH),
                Arrow("v61-a-18", Position(1, 6), Direction.EAST),
                Arrow("v61-a-19", Position(4, 6), Direction.EAST),
                Arrow("v61-a-20", Position(5, 6), Direction.EAST),
                Arrow("v61-a-21", Position(6, 6), Direction.EAST),
            ),
            magnets = source.magnets + Magnet("v61-m-04", Position(5, 5), Polarity.PULL),
            designedSolutions = emptyList(),
        )
        val dag = CompleteDecisionDagAnalyzerV6(engine, 75_000, 750_000).analyze(level.initialState())
        assertTrue(dag.truncationReasons.joinToString(), dag.complete)
        assertNotNull(dag.metrics?.shortestCompletion)
        val result = analyzer.analyze(level, dag, semanticNoveltyPass = true, purposefulOccupancyPass = true)
        println(
            "V61_MASTER_EXPANDED actions=${result.solutionActions} decisions=${result.criticalEpisodes.size} " +
                "phases=${result.causalPhaseCount} traps=${result.persistentSuccessfulTrapCount} " +
                "forced=${result.maximumForcedRun} cleanup=${result.forcedCleanupTailRatio} " +
                "cheap=${result.policyReport.bestCheapPolicySolveRate} " +
                "lookahead=${result.criticalEpisodes.maxOfOrNull { it.proof.minimumVisibleLookahead }} " +
                "inferable=${result.inferableCriticalDecisionRatio} positions=${result.criticalDecisionPositions}",
        )
        assertTrue(result.solutionActions >= 22)
    }

    @Test
    fun `mixed schedule obeys campaign band and family pacing`() {
        val generator = GeneratorV61(engine)
        val levelNumbers = (13..2205).toList()
        val bands = levelNumbers.map(MixedDifficultyScheduleV61::bandForLevel)
        val families = CampaignFamilyAllocatorV61.allocate(bands)
        val slots = levelNumbers.mapIndexed { index, levelNumber ->
            val ordinal = levelNumber - 12
            val band = bands[index]
            val request = V61GenerationRequest(
                "campaign-$levelNumber",
                levelNumber,
                ordinal,
                band,
                causalFamilyIndex = families[index],
            )
            val identity = generator.reproduceIdentity(request, 0)
            CampaignSlotV61(
                levelNumber = levelNumber,
                band = band,
                causalFamilyIdentifier = identity.causalFamilyIdentifier,
                strategyClusterIdentifier = "cluster-$levelNumber",
            )
        }

        val audit = CampaignPacingValidatorV61.validate(slots)
        assertTrue(audit.violations.joinToString("\n"), audit.passed)
        assertEquals(
            mapOf(
                AutomatedDifficultyBandV61.EASY to 2,
                AutomatedDifficultyBandV61.MEDIUM to 4,
                AutomatedDifficultyBandV61.HARD to 6,
                AutomatedDifficultyBandV61.SUPER_HARD to 5,
                AutomatedDifficultyBandV61.EXPERT to 3,
            ),
            MixedDifficultyScheduleV61.expectedTwentyLevelCounts(),
        )
        assertEquals(1325, bands.count {
            it == AutomatedDifficultyBandV61.EASY ||
                it == AutomatedDifficultyBandV61.MEDIUM ||
                it == AutomatedDifficultyBandV61.HARD
        })
        assertEquals(868, bands.count {
            it == AutomatedDifficultyBandV61.SUPER_HARD || it == AutomatedDifficultyBandV61.EXPERT
        })
    }

    @Test
    fun `owner removed Master from campaign and Auto Journey synthesis`() {
        assertFalse(AutomatedDifficultyBandV61.MASTER in AUTOMATED_CAMPAIGN_BANDS_V61)
        assertTrue((13..2205).none { MixedDifficultyScheduleV61.bandForLevel(it) == AutomatedDifficultyBandV61.MASTER })
        assertTrue((1..100).none {
            MixedDifficultyScheduleV61.bandForAutoJourneyOrdinal(it) == AutomatedDifficultyBandV61.MASTER
        })
        val result = GeneratorV61(engine).generate(
            V61GenerationRequest(
                levelId = "v61-removed-master",
                playerFacingNumber = 2206,
                ordinal = 1,
                band = AutomatedDifficultyBandV61.MASTER,
            ),
        )
        assertTrue(result is V61GenerationResult.Rejected)
        result as V61GenerationResult.Rejected
        assertEquals(0, result.failure.examinedAttempts)
        assertEquals(1, result.failure.rejectionCounts["REJECT_MASTER_BAND_OWNER_REMOVED"])
    }

    @Test
    fun `campaign admission rejects a forbidden recent strategy cluster`() {
        val generator = GeneratorV61(engine)
        val request = V61GenerationRequest(
            levelId = "v61-cluster-source",
            playerFacingNumber = 13,
            ordinal = 1,
            band = AutomatedDifficultyBandV61.EASY,
            causalFamilyIndex = 5,
            budgets = V61GenerationBudgets(maximumAttempts = 1),
        )
        val source = generator.generate(request) as V61GenerationResult.Certified
        val repeated = generator.generate(
            request.copy(
                forbiddenStrategyClusters = setOf(source.candidate.fingerprints.strategyBehaviourClusterIdentifier),
            ),
        )

        assertTrue(repeated is V61GenerationResult.Rejected)
        repeated as V61GenerationResult.Rejected
        assertEquals(1, repeated.failure.rejectionCounts["REJECT_STRATEGY_CLUSTER_PACING"])
    }

    private fun delayedTrapBoard() = LevelDefinition(
        id = "v61-delayed-trap",
        number = 1,
        title = "Delayed trap",
        width = 4,
        height = 4,
        arrows = listOf(
            Arrow("A", Position(1, 1), Direction.EAST),
            Arrow("B", Position(3, 3), Direction.NORTH),
            Arrow("C", Position(4, 1), Direction.WEST),
        ),
        magnets = listOf(Magnet("M", Position(1, 3), Polarity.PULL)),
        walls = listOf(Wall(Position(4, 3))),
        designedSolutions = listOf(listOf("B", "A", "C")),
    )

    private fun forcedExitBoard(count: Int): LevelDefinition {
        val positions = (1..8).flatMap { row -> (1..8).map { column -> Position(row, column) } }
        return LevelDefinition(
            id = "long-cheap",
            number = 1,
            title = "Long but cheap",
            width = 8,
            height = 8,
            arrows = positions.take(count).mapIndexed { index, position ->
                val direction = when {
                    position.row == 1 -> Direction.NORTH
                    position.row == 8 -> Direction.SOUTH
                    position.column <= 4 -> Direction.WEST
                    else -> Direction.EAST
                }
                Arrow("A$index", position, direction)
            },
            magnets = emptyList(),
            walls = emptyList(),
            designedSolutions = emptyList(),
        )
    }

    private fun syntheticForcedDag(level: LevelDefinition): DecisionDagAnalysisV6 {
        val nodes = linkedMapOf<String, DecisionNodeV6>()
        val states = (0..level.arrows.size).map { removed ->
            BoardState(
                level.id,
                level.width,
                level.height,
                level.arrows.drop(removed),
                level.magnets,
                level.walls,
            )
        }
        states.forEachIndexed { index, state ->
            val key = exactStateKeyV6(state)
            nodes[key] = DecisionNodeV6(
                stateKey = key,
                state = state,
                solvable = true,
                shortestCompletion = states.lastIndex - index,
                longestCompletion = states.lastIndex - index,
            )
        }
        states.dropLast(1).forEachIndexed { index, state ->
            val node = requireNotNull(nodes[exactStateKeyV6(state)])
            state.arrows.forEachIndexed { arrowIndex, arrow ->
                node.transitions += DecisionTransitionV6(
                    arrowId = arrow.id,
                    successful = arrowIndex == 0,
                    childStateKey = if (arrowIndex == 0) exactStateKeyV6(states[index + 1]) else null,
                    controllerId = null,
                    route = if (arrowIndex == 0) listOf(arrow.position) else emptyList(),
                    terminalEvent = if (arrowIndex == 0) "EXIT" else "COLLISION",
                    polarityChange = null,
                    futureSolvable = if (arrowIndex == 0) true else null,
                )
            }
        }
        val metrics = DecisionDagMetricsV6(
            reachableStateCount = nodes.size,
            successfulTransitionCount = level.arrows.size,
            failedActionAnnotationCount = level.arrows.indices.sum(),
            winningStateCount = nodes.size,
            losingStateCount = 0,
            shortestCompletion = level.arrows.size,
            longestCompletion = level.arrows.size,
            winningSolutionPolicyClasses = 1,
            mandatoryPrecedence = emptySet(),
            transitiveReduction = emptySet(),
            partialOrderWidth = 1,
            forcedRunLengths = listOf(level.arrows.size),
            meaningfulDecisionCount = 0,
            meaningfulNonCommutingDecisionCount = 0,
            successfulLosingBranchCount = 0,
            maximumDelayedDeadlockDepth = 0,
            polarityMemorySpan = 0,
            minimumLookaheadProofDepth = 0,
            hardestWinningChoiceShare = 1.0,
            winningChoiceShares = emptyList(),
            meaningfulDecisionTrace = emptyList(),
            mechanicRhythm = List(level.arrows.size) { "FREE:EXIT:STABLE" },
        )
        return DecisionDagAnalysisV6(
            nodes = nodes,
            rootStateKey = exactStateKeyV6(states.first()),
            complete = true,
            truncationReasons = emptyList(),
            actionResolutionCount = level.arrows.size,
            commutation = emptyList(),
            metrics = metrics,
        )
    }
}

package com.rameshta.magnetrail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.game.AppDestination
import com.rameshta.magnetrail.game.GameAction
import com.rameshta.magnetrail.game.GameMode
import com.rameshta.magnetrail.game.GameViewModel
import com.rameshta.magnetrail.playtest.DataStoreHumanPlaytestRepository
import com.rameshta.magnetrail.playtest.HumanPlaytestAssignment
import com.rameshta.magnetrail.playtest.HumanPlaytestCsv
import com.rameshta.magnetrail.playtest.HumanPlaytestDifficulty
import com.rameshta.magnetrail.playtest.HumanPlaytestGuessResponse
import com.rameshta.magnetrail.playtest.HUMAN_PLAYTEST_FAIRNESS_ANCHORS
import com.rameshta.magnetrail.playtest.HumanPlaytestObservation
import com.rameshta.magnetrail.playtest.HumanPlaytestPlanner
import com.rameshta.magnetrail.playtest.HumanPlaytestRepository
import com.rameshta.magnetrail.playtest.HumanPlaytestSession
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class HumanPlaytestTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `planner creates 30 unique size-stratified V10 assignments across all six bands`() {
        val catalog = campaignCatalog()
        val first = HumanPlaytestPlanner.assignments(catalog, "V10-P01")
        val repeated = HumanPlaytestPlanner.assignments(catalog, "V10-P01")
        val otherParticipant = HumanPlaytestPlanner.assignments(catalog, "V10-P02")

        assertEquals(30, first.size)
        assertEquals(first, repeated)
        assertEquals(first.map { it.levelId }.toSet(), otherParticipant.map { it.levelId }.toSet())
        assertNotEquals(first.map { it.levelId }, otherParticipant.map { it.levelId })
        assertEquals(HumanPlaytestDifficulty.entries.toSet(), first.map { it.expectedDifficulty }.toSet())
        assertEquals(setOf(5), first.groupingBy { it.expectedDifficulty }.eachCount().values.toSet())
        assertEquals(30, first.map { it.levelId }.toSet().size)
        assertTrue(first.all { assignment ->
            catalog.levels.first { it.id == assignment.levelId }.number > 205
        })
        HumanPlaytestDifficulty.entries.forEach { difficulty ->
            val availableSizes = catalog.levels.filter { level ->
                level.metadata?.contentVersion == 10 &&
                    HumanPlaytestDifficulty.fromProfile(level.metadata?.generationProfile) == difficulty
            }.map { it.width to it.height }.toSet()
            val selectedSizes = first.filter { it.expectedDifficulty == difficulty }.map { assignment ->
                catalog.levels.first { it.id == assignment.levelId }.let { it.width to it.height }
            }.toSet()
            assertEquals(availableSizes, selectedSizes)
        }
    }

    @Test
    fun `difficulty parser recognizes every V10 campaign profile`() {
        HumanPlaytestDifficulty.entries.forEach { difficulty ->
            assertEquals(
                difficulty,
                HumanPlaytestDifficulty.fromProfile(difficulty.generationProfile),
            )
        }
    }

    @Test
    fun `repository round trips and clears a resumable session`() = runTest {
        val repository = DataStoreHumanPlaytestRepository(dataStore(this))
        val session = sampleSession()

        repository.save(session)
        assertEquals(session, repository.load())

        repository.clear()
        assertNull(repository.load())
    }

    @Test
    fun `opening V10 playtest clears an incomparable older session`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeHumanPlaytestRepository().apply {
            session = sampleSession().copy(
                schemaVersion = 1,
                studySeed = 9_202_205L,
                campaignContentVersion = 9,
            )
        }
        val viewModel = GameViewModel(
            catalog = campaignCatalog(),
            humanPlaytestCatalog = campaignCatalog(),
            humanPlaytestRepository = repository,
            humanPlaytestEnabled = true,
            debugUnlockAll = true,
        )

        viewModel.onAction(GameAction.OpenHumanPlaytest)
        advanceUntilIdle()

        assertNull(repository.session)
        assertNull(viewModel.uiState.value.humanPlaytest.session)
        assertEquals("PILOT-01", viewModel.uiState.value.humanPlaytest.participantCodeInput)
        assertTrue(viewModel.uiState.value.humanPlaytest.message.orEmpty().contains("fresh blind V10 pilot session"))
    }

    @Test
    fun `CSV contains blind outcome answer key and board density fields`() {
        val csv = HumanPlaytestCsv.encode(sampleSession(withObservation = true))
        val lines = csv.trim().lines()

        assertEquals(2, lines.size)
        assertTrue(lines.first().contains("expected_difficulty"))
        assertTrue(lines.first().contains("total_occupancy_percent"))
        assertTrue(lines.first().contains("successful_wrong_actions"))
        assertTrue(lines.first().contains("fairness_rating"))
        assertTrue(lines.first().contains("guess_response"))
        assertTrue(lines.first().contains("fairness_anchor"))
        assertEquals(lines.first().split(',').size, lines.last().split(',').size)
        assertTrue(lines.last().contains("PT-001"))
        assertTrue(lines.last().contains("Easy,1,Super Hard,4,false"))
        assertTrue(lines.last().contains("43.75"))
        assertTrue(lines.last().contains("Completely unfair — I could only guess"))
    }

    @Test
    fun `fairness scale exposes five explicit accessible anchors`() {
        assertEquals((1..5).toList(), HUMAN_PLAYTEST_FAIRNESS_ANCHORS.keys.toList())
        assertEquals("Completely unfair — I could only guess", HUMAN_PLAYTEST_FAIRNESS_ANCHORS[1])
        assertEquals("Completely fair — outcomes followed visible rules", HUMAN_PLAYTEST_FAIRNESS_ANCHORS[5])
    }

    @Test
    fun `schema four boolean guessing migrates to versioned response`() = runTest {
        val store = dataStore(this)
        val legacy = sampleSession(withObservation = true).copy(
            schemaVersion = 4,
            observations = sampleSession(withObservation = true).observations.map {
                it.copy(guessRequired = true, guessResponse = HumanPlaytestGuessResponse.YES)
            },
        )
        val encoded = Json { encodeDefaults = true }.encodeToString(legacy)
            .replace(Regex(",\"guessResponse\":\"[A-Z]+\""), "")
        store.edit { values ->
            values[stringPreferencesKey("human_playtest_session_json")] = encoded
        }

        val migrated = requireNotNull(DataStoreHumanPlaytestRepository(store).load())

        assertEquals(5, migrated.schemaVersion)
        assertEquals(HumanPlaytestGuessResponse.YES, migrated.observations.single().guessResponse)
    }

    @Test
    fun `V6 planner assigns six per bucket and prevents adjacent causal families`() {
        val source = campaignCatalog()
        val levels = HumanPlaytestDifficulty.entries.filter { it != HumanPlaytestDifficulty.MASTER }.flatMap { difficulty ->
            val template = source.levels.first {
                HumanPlaytestDifficulty.fromProfile(it.metadata?.generationProfile) == difficulty
            }
            (0 until 6).map { index ->
                template.copy(
                    id = "v6-${difficulty.rating}-$index",
                    number = difficulty.rating * 10 + index,
                    metadata = requireNotNull(template.metadata).copy(
                        contentVersion = 12,
                        generatorVersion = 6,
                        generatorSeed = (difficulty.rating * 100 + index).toLong(),
                        generationProfile = "v6-bucket-${difficulty.rating}",
                        mechanicTags = listOf("V6_FAMILY_F${(difficulty.rating + index) % 6}"),
                    ),
                )
            }
        }
        val catalog = LevelCatalog(2, "magnetrail-core-1", "v6-test", levels, 12, 6)
        val assignments = HumanPlaytestPlanner.assignments(catalog, "V6-P01")
        assertEquals(30, assignments.size)
        assertTrue(assignments.none { it.expectedDifficulty == HumanPlaytestDifficulty.MASTER })
        assertEquals(setOf(6), assignments.groupingBy { it.expectedDifficulty.rating }.eachCount().values.toSet())
        assertTrue(assignments.zipWithNext().all { (first, second) -> first.causalFamily != second.causalFamily })
    }

    @Test
    fun `view model records abandonment rating and resumes at next blind board`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeHumanPlaytestRepository()
            var monotonicMillis = 1_000L
            var epochMillis = 10_000L
            val viewModel = GameViewModel(
                catalog = campaignCatalog(),
                humanPlaytestCatalog = campaignCatalog(),
                humanPlaytestRepository = repository,
                humanPlaytestEnabled = true,
                debugUnlockAll = true,
                elapsedMillis = { monotonicMillis },
                wallTimeMillis = { epochMillis },
            )

            viewModel.onAction(GameAction.OpenHumanPlaytest)
            advanceUntilIdle()
            viewModel.onAction(GameAction.UpdateHumanPlaytestParticipant("pilot-03"))
            viewModel.onAction(GameAction.StartHumanPlaytest)
            advanceUntilIdle()

            val session = requireNotNull(viewModel.uiState.value.humanPlaytest.session)
            assertEquals("PILOT-03", session.participantCode)
            assertEquals(30, session.assignments.size)
            assertEquals(AppDestination.HUMAN_PLAYTEST, viewModel.uiState.value.destination)

            viewModel.onAction(GameAction.ResumeHumanPlaytest)
            assertEquals(GameMode.PLAYTEST, viewModel.uiState.value.gameMode)
            assertEquals(AppDestination.GAME, viewModel.uiState.value.destination)
            assertEquals("PT-001", viewModel.uiState.value.humanPlaytestAssignment?.blindId)

            viewModel.onAction(GameAction.Restart)
            monotonicMillis += 5_000L
            epochMillis += 5_000L
            viewModel.onAction(GameAction.AbandonHumanPlaytestBoard)
            viewModel.onAction(GameAction.RateHumanPlaytestBoard(4))
            viewModel.onAction(GameAction.RateHumanPlaytestFairness(4))
            viewModel.onAction(GameAction.SetHumanPlaytestGuessRequired(false))
            viewModel.onAction(GameAction.SetHumanPlaytestRepeatedStrategy(false))
            viewModel.onAction(GameAction.SubmitHumanPlaytestFeedback)
            advanceUntilIdle()

            val saved = requireNotNull(repository.session)
            val observation = saved.observations.single()
            assertFalse(observation.completed)
            assertEquals(HumanPlaytestDifficulty.SUPER_HARD, observation.perceivedDifficulty)
            assertEquals(1, observation.restarts)
            assertEquals(5_000L, observation.durationMillis)
            assertEquals(0, observation.totalActions)
            assertEquals("PT-002", viewModel.uiState.value.humanPlaytestAssignment?.blindId)
            assertEquals(AppDestination.GAME, viewModel.uiState.value.destination)
            assertTrue(viewModel.uiState.value.progress.completedLevelIds.isEmpty())

            val solution = viewModel.uiState.value.currentLevel.designedSolutions.first()
            solution.forEach { arrowId ->
                viewModel.onAction(GameAction.LaunchArrow(arrowId))
                viewModel.onAction(GameAction.AnimationCompleted)
            }
            assertEquals(
                com.rameshta.magnetrail.playtest.HumanPlaytestOutcome.COMPLETED,
                viewModel.uiState.value.humanPlaytestOutcome?.outcome,
            )
            viewModel.onAction(GameAction.RateHumanPlaytestBoard(2))
            viewModel.onAction(GameAction.RateHumanPlaytestFairness(5))
            viewModel.onAction(GameAction.SetHumanPlaytestGuessRequired(false))
            viewModel.onAction(GameAction.SetHumanPlaytestRepeatedStrategy(false))
            viewModel.onAction(GameAction.SubmitHumanPlaytestFeedback)
            advanceUntilIdle()

            val completedObservation = requireNotNull(repository.session).observations.last()
            assertTrue(completedObservation.completed)
            assertEquals(solution.size, completedObservation.finalAttemptActions)
            assertEquals(HumanPlaytestDifficulty.MEDIUM, completedObservation.perceivedDifficulty)
            assertTrue(viewModel.uiState.value.progress.completedLevelIds.isEmpty())
        }

    private fun campaignCatalog(): LevelCatalog = LevelParser().parseCatalog(
        checkNotNull(javaClass.getResource("/Magnetrail_Campaign_Levels_v3.json")).readText(),
    )

    private fun dataStore(scope: TestScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(temporaryFolder.root, "human-playtest-${System.nanoTime()}.preferences_pb") },
    )

    private fun sampleSession(withObservation: Boolean = false): HumanPlaytestSession {
        val assignment = HumanPlaytestAssignment(1, "PT-001", "campaign-206", HumanPlaytestDifficulty.EASY)
        val observation = HumanPlaytestObservation(
            order = 1,
            blindId = "PT-001",
            levelId = "campaign-206",
            expectedDifficulty = HumanPlaytestDifficulty.EASY,
            perceivedDifficulty = HumanPlaytestDifficulty.SUPER_HARD,
            completed = false,
            finalAttemptActions = 7,
            totalActions = 12,
            totalOverloads = 3,
            hintsUsed = 1,
            restarts = 2,
            durationMillis = 42_000L,
            startedAtEpochMillis = 1_000L,
            recordedAtEpochMillis = 43_000L,
            width = 4,
            height = 4,
            arrowCount = 3,
            magnetCount = 2,
            wallCount = 2,
            certifiedSolutionLength = 3,
            contentFingerprint = "sha256:${"a".repeat(64)}",
            fairnessRating = 1,
        )
        return HumanPlaytestSession(
            sessionId = "HPT-TEST",
            participantCode = "PILOT-01",
            studySeed = 10_202_205L,
            campaignContentVersion = 10,
            startedAtEpochMillis = 1_000L,
            assignments = listOf(assignment),
            observations = if (withObservation) listOf(observation) else emptyList(),
        )
    }

    private class FakeHumanPlaytestRepository : HumanPlaytestRepository {
        var session: HumanPlaytestSession? = null

        override suspend fun load(): HumanPlaytestSession? = session

        override suspend fun save(session: HumanPlaytestSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }
    }
}

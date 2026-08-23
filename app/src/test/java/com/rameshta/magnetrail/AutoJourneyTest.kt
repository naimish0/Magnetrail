package com.rameshta.magnetrail

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rameshta.magnetrail.autojourney.AUTO_JOURNEY_FIRST_LEVEL_NUMBER
import com.rameshta.magnetrail.autojourney.AutoJourneyCoordinator
import com.rameshta.magnetrail.autojourney.AutoJourneyFingerprints
import com.rameshta.magnetrail.autojourney.AutoJourneyPreparation
import com.rameshta.magnetrail.autojourney.AutoJourneyRecord
import com.rameshta.magnetrail.autojourney.AutoJourneyRepository
import com.rameshta.magnetrail.autojourney.AutoJourneyState
import com.rameshta.magnetrail.autojourney.DataStoreAutoJourneyRepository
import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.v6.AutomatedDifficultyBandV61
import com.rameshta.magnetrail.core.generation.v6.GENERATOR_IDENTITY_V61
import com.rameshta.magnetrail.core.generation.v6.V61GenerationFailure
import com.rameshta.magnetrail.core.generation.v6.V61GenerationResult
import com.rameshta.magnetrail.core.generation.v6.V6FingerprintBundle
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.Arrow
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Position
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AutoJourneyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `certified board survives repository recreation and completion is idempotent`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            temporaryFolder.newFile("auto.preferences_pb")
        }
        val record = record(1)
        val firstRepository = DataStoreAutoJourneyRepository(store)
        firstRepository.persistCertified(record)

        val recreated = DataStoreAutoJourneyRepository(store)
        assertEquals(record, recreated.load().records.single())
        assertTrue(recreated.markCompleted(record.internalId))
        assertFalse(recreated.markCompleted(record.internalId))
        assertTrue(recreated.load().records.single().completed)
    }

    @Test
    fun `restore returns the identical persisted board without regenerating`() = runTest {
        val repository = FakeAutoJourneyRepository(AutoJourneyState(records = listOf(record(1))))
        var generationCalls = 0
        val coordinator = AutoJourneyCoordinator(
            repository = repository,
            shippedCatalogs = emptyList(),
            generate = { request ->
                generationCalls += 1
                V61GenerationResult.Rejected(V61GenerationFailure(request, 1, mapOf("cap" to 1), listOf("cap")))
            },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            isMainThread = { false },
        )

        val result = coordinator.restoreOrPrepareNext() as AutoJourneyPreparation.Ready

        assertEquals(0, generationCalls)
        assertEquals("auto-journey-v1-1", result.next.internalId)
        assertEquals(AUTO_JOURNEY_FIRST_LEVEL_NUMBER, result.next.playerFacingLevelNumber)
        assertEquals(record(1).canonicalCatalogJson, result.next.canonicalCatalogJson)
    }

    @Test
    fun `bounded failure with no unused certified fallback is recoverable`() = runTest {
        val repository = FakeAutoJourneyRepository()
        val coordinator = AutoJourneyCoordinator(
            repository = repository,
            shippedCatalogs = emptyList(),
            generate = { request ->
                V61GenerationResult.Rejected(
                    V61GenerationFailure(request, 4, mapOf("REJECT_ANALYSIS_TRUNCATED" to 4), listOf("cap")),
                )
            },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            isMainThread = { false },
        )

        val result = coordinator.prepareAhead(3) as AutoJourneyPreparation.Preparing

        assertTrue(result.retryable)
        assertTrue(result.reason.contains("being prepared"))
        assertTrue(repository.state.records.isEmpty())
    }

    @Test
    fun `preparation benchmark verifies certified queue work is off main thread`() = runTest {
        val repository = FakeAutoJourneyRepository(
            AutoJourneyState(records = listOf(record(1), record(2), record(3))),
        )
        val coordinator = AutoJourneyCoordinator(
            repository = repository,
            shippedCatalogs = emptyList(),
            generate = { error("pre-certified queue must not generate") },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            isMainThread = { false },
        )

        val benchmark = coordinator.benchmarkPreparation()

        assertEquals(3, benchmark.preparedCount)
        assertFalse(benchmark.mainThreadBlocked)
        assertFalse(benchmark.thermalMeasurementAvailable)
        assertTrue(benchmark.elapsedMillis >= 0)
    }

    @Test
    fun `generation receives durable recent family and strategy pacing exclusions`() = runTest {
        val completedHistory = (1..19).map { ordinal -> record(ordinal).copy(completed = true) }
        val repository = FakeAutoJourneyRepository(AutoJourneyState(records = completedHistory))
        var capturedRequest: com.rameshta.magnetrail.core.generation.v6.V61GenerationRequest? = null
        val coordinator = AutoJourneyCoordinator(
            repository = repository,
            shippedCatalogs = emptyList(),
            generate = { request ->
                capturedRequest = request
                V61GenerationResult.Rejected(V61GenerationFailure(request, 1, mapOf("cap" to 1), listOf("cap")))
            },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            isMainThread = { false },
        )

        assertTrue(coordinator.prepareAhead(3) is AutoJourneyPreparation.Preparing)
        val request = requireNotNull(capturedRequest)
        assertEquals(completedHistory.takeLast(8).map { it.causalFamily }.toSet(), request.forbiddenCausalFamilies)
        assertEquals(
            completedHistory.takeLast(19).map { it.fingerprints.strategyCluster }.toSet(),
            request.forbiddenStrategyClusters,
        )
    }

    @Test
    fun `shipped fingerprint index is deduplicated and deferred until generation`() = runTest {
        val shippedLevel = level(1)
        val shippedCatalog = LevelCatalog(
            2,
            "magnetrail-core-1",
            "shared-catalog",
            listOf(shippedLevel),
            10,
            5,
        )
        var fingerprintCalls = 0
        val coordinator = AutoJourneyCoordinator(
            repository = FakeAutoJourneyRepository(),
            shippedCatalogs = listOf(shippedCatalog, shippedCatalog),
            generate = { request ->
                V61GenerationResult.Rejected(V61GenerationFailure(request, 1, mapOf("cap" to 1), listOf("cap")))
            },
            dispatcher = UnconfinedTestDispatcher(testScheduler),
            isMainThread = { false },
            fingerprintLevel = { candidate ->
                fingerprintCalls += 1
                fingerprint(candidate)
            },
        )

        assertEquals(0, fingerprintCalls)
        assertTrue(coordinator.prepareAhead(3) is AutoJourneyPreparation.Preparing)
        assertEquals(1, fingerprintCalls)
    }

    private fun record(ordinal: Int): AutoJourneyRecord {
        val level = level(ordinal)
        val json = LevelParser().encodeCatalog(
            LevelCatalog(2, "magnetrail-core-1", "auto-$ordinal", listOf(level), 12, 6),
        )
        val exact = ContentFingerprint.exact(level)
        val initial = AutoJourneyRecord(
            ordinal,
            AUTO_JOURNEY_FIRST_LEVEL_NUMBER + ordinal - 1,
            "auto-journey-v1-$ordinal",
            AutomatedDifficultyBandV61.EASY.name,
            GENERATOR_IDENTITY_V61,
            61L + ordinal,
            0,
            "test-family-$ordinal",
            json,
            "sha256:${sha256(json)}",
            AutoJourneyFingerprints(
                exact,
                ContentFingerprint.symmetryNormalized(level),
                ContentFingerprint.symmetryNormalized(level),
                "causal-$ordinal",
                "dag-$ordinal",
                "policy-$ordinal",
                "trace-$ordinal",
                "rhythm-$ordinal",
                "cluster-$ordinal",
            ),
            "V61_TECHNICALLY_CERTIFIED",
            "",
        )
        return initial.copy(certificationReceiptSha256 = DataStoreAutoJourneyRepository.receipt(initial))
    }

    private fun level(ordinal: Int) = LevelDefinition(
        "auto-journey-v1-$ordinal",
        AUTO_JOURNEY_FIRST_LEVEL_NUMBER + ordinal - 1,
        "Auto Journey",
        3,
        3,
        listOf(Arrow("A", Position(1, 1), Direction.NORTH)),
        emptyList(),
        emptyList(),
        listOf(listOf("A")),
    )

    private fun fingerprint(level: LevelDefinition): V6FingerprintBundle {
        val exact = ContentFingerprint.exact(level)
        return V6FingerprintBundle(
            exact,
            ContentFingerprint.symmetryNormalized(level),
            ContentFingerprint.arrowLayoutSymmetryNormalized(level),
            ContentFingerprint.interactiveLayoutSymmetryNormalized(level),
            ContentFingerprint.perceptualTemplateSignature(level),
            ContentFingerprint.symmetryNormalized(level),
            "unavailable:causal:${level.id}",
            "unavailable:dag:${level.id}",
            "unavailable:policy:${level.id}",
            "unavailable:trace:${level.id}",
            "unavailable:rhythm:${level.id}",
            emptyList(),
        )
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private class FakeAutoJourneyRepository(
        var state: AutoJourneyState = AutoJourneyState(),
    ) : AutoJourneyRepository {
        override suspend fun load(): AutoJourneyState = state

        override suspend fun persistCertified(record: AutoJourneyRecord) {
            state = state.copy(records = state.records + record)
        }

        override suspend fun markCompleted(internalId: String): Boolean {
            val existing = state.records.single { it.internalId == internalId }
            if (existing.completed) return false
            state = state.copy(records = state.records.map { if (it.internalId == internalId) it.copy(completed = true) else it })
            return true
        }
    }
}

package com.rameshta.magnetrail.autojourney

import android.content.Context
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.v6.AutomatedDifficultyBandV61
import com.rameshta.magnetrail.core.generation.v6.GENERATOR_IDENTITY_V61
import com.rameshta.magnetrail.core.generation.v6.GeneratorV61
import com.rameshta.magnetrail.core.generation.v6.MixedDifficultyScheduleV61
import com.rameshta.magnetrail.core.generation.v6.V61GenerationBudgets
import com.rameshta.magnetrail.core.generation.v6.V61GenerationRequest
import com.rameshta.magnetrail.core.generation.v6.V61GenerationResult
import com.rameshta.magnetrail.core.generation.v6.V6FingerprintBundle
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import java.io.IOException
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext

const val AUTO_JOURNEY_SCHEMA_VERSION = 1
const val AUTO_JOURNEY_FIRST_LEVEL_NUMBER = 2206
const val AUTO_JOURNEY_PREFETCH_COUNT = 4

@Serializable
data class AutoJourneyFingerprints(
    val exact: String,
    val d4: String,
    val relevancePrunedD4: String,
    val causal: String,
    val decisionDag: String,
    val solutionPolicy: String,
    val productionTransitionTrace: String,
    val mechanicRhythm: String,
    val strategyCluster: String,
)

@Serializable
data class AutoJourneyRecord(
    val ordinal: Int,
    val playerFacingLevelNumber: Int,
    val internalId: String,
    val difficultyBand: String,
    val generatorIdentity: String,
    val seed: Long,
    val attempt: Int,
    val causalFamily: String,
    val canonicalCatalogJson: String,
    val boardHash: String,
    val fingerprints: AutoJourneyFingerprints,
    val certificationStatus: String,
    val certificationReceiptSha256: String,
    val completed: Boolean = false,
) {
    init {
        require(ordinal > 0)
        require(playerFacingLevelNumber == AUTO_JOURNEY_FIRST_LEVEL_NUMBER + ordinal - 1)
        require(internalId == "auto-journey-v1-$ordinal")
        require(generatorIdentity == GENERATOR_IDENTITY_V61)
        require(boardHash.startsWith("sha256:") && boardHash.length == 71)
    }
}

@Serializable
data class AutoJourneyState(
    val schemaVersion: Int = AUTO_JOURNEY_SCHEMA_VERSION,
    val records: List<AutoJourneyRecord> = emptyList(),
) {
    init {
        require(schemaVersion == AUTO_JOURNEY_SCHEMA_VERSION)
        require(records.map { it.ordinal }.distinct().size == records.size)
        require(records.map { it.internalId }.distinct().size == records.size)
    }

    val nextOrdinal: Int get() = (records.maxOfOrNull { it.ordinal } ?: 0) + 1
}

private val Context.autoJourneyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "magnetrail_auto_journey_v1",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

interface AutoJourneyRepository {
    suspend fun load(): AutoJourneyState
    suspend fun persistCertified(record: AutoJourneyRecord)
    suspend fun markCompleted(internalId: String): Boolean
}

class DataStoreAutoJourneyRepository private constructor(
    private val dataStore: DataStore<Preferences>,
) : AutoJourneyRepository {
    constructor(context: Context) : this(context.applicationContext.autoJourneyDataStore)
    internal constructor(dataStore: DataStore<Preferences>, testMarker: Unit = Unit) : this(dataStore)

    override suspend fun load(): AutoJourneyState {
        val encoded = dataStore.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }.first()[Keys.state] ?: return AutoJourneyState()
        return runCatching { json.decodeFromString<AutoJourneyState>(encoded) }.getOrElse { AutoJourneyState() }
    }

    override suspend fun persistCertified(record: AutoJourneyRecord) {
        require(record.certificationStatus == "V61_TECHNICALLY_CERTIFIED")
        require(record.boardHash == "sha256:${sha256(record.canonicalCatalogJson)}")
        require(record.certificationReceiptSha256 == receipt(record))
        dataStore.edit { stored ->
            val state = stored[Keys.state]?.let { json.decodeFromString<AutoJourneyState>(it) } ?: AutoJourneyState()
            val existing = state.records.singleOrNull { it.ordinal == record.ordinal || it.internalId == record.internalId }
            require(existing == null || existing == record) { "Auto Journey identity cannot be rebound to another board" }
            if (existing == null) {
                require(state.records.none { prior -> prior.fingerprints.collidesWith(record.fingerprints) }) {
                    "Auto Journey semantic collision"
                }
                val orderedHistory = state.records.sortedBy { it.ordinal }
                require(orderedHistory.takeLast(8).none { it.causalFamily == record.causalFamily }) {
                    "Auto Journey causal-family pacing collision"
                }
                require(orderedHistory.takeLast(19).none {
                    it.fingerprints.strategyCluster == record.fingerprints.strategyCluster
                }) { "Auto Journey strategy-cluster pacing collision" }
                stored[Keys.state] = json.encodeToString(state.copy(records = state.records + record))
            }
        }
    }

    override suspend fun markCompleted(internalId: String): Boolean {
        var firstCompletion = false
        dataStore.edit { stored ->
            val state = stored[Keys.state]?.let { json.decodeFromString<AutoJourneyState>(it) } ?: AutoJourneyState()
            require(state.records.any { it.internalId == internalId }) { "Unknown Auto Journey board $internalId" }
            val records = state.records.map { record ->
                if (record.internalId == internalId && !record.completed) {
                    firstCompletion = true
                    record.copy(completed = true)
                } else record
            }
            stored[Keys.state] = json.encodeToString(state.copy(records = records))
        }
        return firstCompletion
    }

    private object Keys {
        val state = stringPreferencesKey("auto_journey_state_json")
    }

    companion object {
        private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

        fun receipt(record: AutoJourneyRecord): String = "sha256:${sha256(
            listOf(
                record.ordinal,
                record.internalId,
                record.difficultyBand,
                record.generatorIdentity,
                record.seed,
                record.attempt,
                record.boardHash,
                record.fingerprints,
                record.certificationStatus,
            ).joinToString("|"),
        )}"
    }
}

sealed interface AutoJourneyPreparation {
    data class Ready(val next: AutoJourneyRecord, val preparedCount: Int) : AutoJourneyPreparation
    data class Preparing(val reason: String, val retryable: Boolean = true) : AutoJourneyPreparation
}

data class AutoJourneyBenchmark(
    val elapsedMillis: Long,
    val heapDeltaBytes: Long,
    val preparedCount: Int,
    val mainThreadBlocked: Boolean,
    val thermalMeasurementAvailable: Boolean = false,
)

class AutoJourneyCoordinator(
    private val repository: AutoJourneyRepository,
    shippedCatalogs: List<LevelCatalog>,
    private val certifiedFallbacks: List<AutoJourneyRecord> = emptyList(),
    generator: GeneratorV61 = GeneratorV61(),
    private val generate: (V61GenerationRequest) -> V61GenerationResult = generator::generate,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val isMainThread: () -> Boolean = { Looper.myLooper() == Looper.getMainLooper() },
    private val fingerprintLevel: (LevelDefinition) -> V6FingerprintBundle = ::layoutBundle,
) {
    private val shippedLevels = buildList {
        val seenCatalogs = Collections.newSetFromMap(IdentityHashMap<LevelCatalog, Boolean>())
        shippedCatalogs.forEach { catalog ->
            if (seenCatalogs.add(catalog)) addAll(catalog.levels)
        }
    }
    private val shippedFingerprints by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        shippedLevels.map(fingerprintLevel)
    }

    suspend fun prepareAhead(count: Int = AUTO_JOURNEY_PREFETCH_COUNT): AutoJourneyPreparation = withContext(dispatcher) {
        require(count in 3..5)
        val initial = repository.load()
        val existingNext = initial.records.filter { !it.completed }.minByOrNull { it.ordinal }
        var state = initial
        while (state.records.count { !it.completed } < count) {
            coroutineContext.ensureActive()
            val ordinal = state.nextOrdinal
            val band = MixedDifficultyScheduleV61.bandForAutoJourneyOrdinal(ordinal)
            val history = state.records.map { it.toBundle() }
            val orderedHistory = state.records.sortedBy { it.ordinal }
            val recentFamilies = orderedHistory.takeLast(8).mapTo(mutableSetOf()) { it.causalFamily }
            val recentClusters = orderedHistory.takeLast(19).mapTo(mutableSetOf()) { it.fingerprints.strategyCluster }
            val request = V61GenerationRequest(
                levelId = "auto-journey-v1-$ordinal",
                playerFacingNumber = AUTO_JOURNEY_FIRST_LEVEL_NUMBER + ordinal - 1,
                ordinal = ordinal,
                band = band,
                knownFingerprints = shippedFingerprints + history,
                forbiddenCausalFamilies = recentFamilies,
                forbiddenStrategyClusters = recentClusters,
                budgets = mobileBudgets(),
            )
            when (val result = generate(request)) {
                is V61GenerationResult.Certified -> {
                    val record = result.candidate.toRecord()
                    repository.persistCertified(record)
                    state = repository.load()
                }
                is V61GenerationResult.Rejected -> {
                    val fallback = certifiedFallbacks.asSequence()
                        .filter { it.difficultyBand == band.name && it.certificationStatus == "V61_TECHNICALLY_CERTIFIED" }
                        .filterNot { candidate ->
                            (shippedFingerprints + history).any { it.collidesWith(candidate.toBundle()) }
                        }
                        .filterNot { it.causalFamily in recentFamilies }
                        .filterNot { it.fingerprints.strategyCluster in recentClusters }
                        .sortedBy { it.internalId }
                        .firstOrNull()
                    if (fallback == null) {
                        return@withContext AutoJourneyPreparation.Preparing(
                            "Strict bounded generation exhausted for ${band.displayName}; next level is being prepared.",
                        )
                    }
                    val rebound = fallback.rebind(ordinal, band)
                    repository.persistCertified(rebound)
                    state = repository.load()
                }
            }
        }
        val next = existingNext ?: state.records.filter { !it.completed }.minByOrNull { it.ordinal }
            ?: return@withContext AutoJourneyPreparation.Preparing("No certified Auto Journey board is available.")
        AutoJourneyPreparation.Ready(next, state.records.count { !it.completed })
    }

    suspend fun restoreOrPrepareNext(): AutoJourneyPreparation = withContext(dispatcher) {
        repository.load().records.filter { !it.completed }.minByOrNull { it.ordinal }?.let {
            return@withContext AutoJourneyPreparation.Ready(it, repository.load().records.count { row -> !row.completed })
        }
        prepareAhead()
    }

    suspend fun markCompleted(internalId: String): Boolean = withContext(dispatcher) {
        repository.markCompleted(internalId)
    }

    suspend fun benchmarkPreparation(): AutoJourneyBenchmark = withContext(dispatcher) {
        val runningOnMainThread = isMainThread()
        val runtime = Runtime.getRuntime()
        val before = runtime.totalMemory() - runtime.freeMemory()
        val started = System.nanoTime()
        val result = prepareAhead(3)
        val elapsed = (System.nanoTime() - started) / 1_000_000
        val after = runtime.totalMemory() - runtime.freeMemory()
        AutoJourneyBenchmark(
            elapsedMillis = elapsed,
            heapDeltaBytes = after - before,
            preparedCount = (result as? AutoJourneyPreparation.Ready)?.preparedCount ?: 0,
            mainThreadBlocked = runningOnMainThread,
        )
    }

    fun parseLevel(record: AutoJourneyRecord): LevelDefinition = LevelParser()
        .parseCatalog(record.canonicalCatalogJson).levels.single().also { level ->
            require(ContentFingerprint.exact(level) == record.fingerprints.exact)
            require("sha256:${sha256(record.canonicalCatalogJson)}" == record.boardHash)
        }

    private fun com.rameshta.magnetrail.core.generation.v6.V61CertifiedCandidate.toRecord(): AutoJourneyRecord {
        val catalogJson = LevelParser().encodeCatalog(
            LevelCatalog(2, "magnetrail-core-1", "auto-journey-v1-${identity.ordinal}", listOf(level), 12, 6),
        )
        val initial = AutoJourneyRecord(
            ordinal = identity.ordinal,
            playerFacingLevelNumber = AUTO_JOURNEY_FIRST_LEVEL_NUMBER + identity.ordinal - 1,
            internalId = "auto-journey-v1-${identity.ordinal}",
            difficultyBand = identity.band.name,
            generatorIdentity = GENERATOR_IDENTITY_V61,
            seed = identity.seed,
            attempt = identity.attempt,
            causalFamily = identity.causalFamilyIdentifier,
            canonicalCatalogJson = catalogJson,
            boardHash = "sha256:${sha256(catalogJson)}",
            fingerprints = AutoJourneyFingerprints(
                fingerprints.exactLayout,
                fingerprints.d4Layout,
                fingerprints.relevancePrunedD4Layout,
                fingerprints.causalHypergraph,
                fingerprints.quotientDecisionDag,
                fingerprints.solutionPolicy,
                fingerprints.productionStateTransitionTrace,
                fingerprints.mechanicRhythm,
                fingerprints.strategyBehaviourClusterIdentifier,
            ),
            certificationStatus = "V61_TECHNICALLY_CERTIFIED",
            certificationReceiptSha256 = "",
        )
        return initial.copy(certificationReceiptSha256 = DataStoreAutoJourneyRepository.receipt(initial))
    }

    private fun AutoJourneyRecord.rebind(ordinal: Int, band: AutomatedDifficultyBandV61): AutoJourneyRecord {
        val level = LevelParser().parseCatalog(canonicalCatalogJson).levels.single().copy(
            id = "auto-journey-v1-$ordinal",
            number = AUTO_JOURNEY_FIRST_LEVEL_NUMBER + ordinal - 1,
        )
        val json = LevelParser().encodeCatalog(LevelCatalog(2, "magnetrail-core-1", "auto-journey-v1-$ordinal", listOf(level), 12, 6))
        val base = copy(
            ordinal = ordinal,
            playerFacingLevelNumber = AUTO_JOURNEY_FIRST_LEVEL_NUMBER + ordinal - 1,
            internalId = "auto-journey-v1-$ordinal",
            difficultyBand = band.name,
            canonicalCatalogJson = json,
            boardHash = "sha256:${sha256(json)}",
            completed = false,
            certificationReceiptSha256 = "",
        )
        return base.copy(certificationReceiptSha256 = DataStoreAutoJourneyRepository.receipt(base))
    }

    private fun mobileBudgets() = V61GenerationBudgets(
        maximumAttempts = 2,
        decisionDagStates = 5_000,
        decisionDagResolutions = 50_000,
        solverStates = 5_000,
        counterfactualChecks = 20_000,
        canonicalBacktrackingStates = 20_000,
    )
}

private fun AutoJourneyRecord.toBundle() = V6FingerprintBundle(
    fingerprints.exact,
    fingerprints.d4,
    fingerprints.exact,
    fingerprints.exact,
    fingerprints.exact,
    fingerprints.relevancePrunedD4,
    fingerprints.causal,
    fingerprints.decisionDag,
    fingerprints.solutionPolicy,
    fingerprints.productionTransitionTrace,
    fingerprints.mechanicRhythm,
    emptyList(),
    productionStateTransitionTrace = fingerprints.productionTransitionTrace,
    causalFamilyIdentifier = causalFamily,
    strategyBehaviourClusterIdentifier = fingerprints.strategyCluster,
)

private fun layoutBundle(level: LevelDefinition) = V6FingerprintBundle(
    ContentFingerprint.exact(level),
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

private fun V6FingerprintBundle.collidesWith(other: V6FingerprintBundle): Boolean =
    exactLayout == other.exactLayout || d4Layout == other.d4Layout ||
        relevancePrunedD4Layout == other.relevancePrunedD4Layout ||
        comparable(causalHypergraph, other.causalHypergraph) ||
        comparable(quotientDecisionDag, other.quotientDecisionDag) ||
        comparable(solutionPolicy, other.solutionPolicy)

private fun AutoJourneyFingerprints.collidesWith(other: AutoJourneyFingerprints): Boolean =
    exact == other.exact || d4 == other.d4 || relevancePrunedD4 == other.relevancePrunedD4 ||
        causal == other.causal || decisionDag == other.decisionDag || solutionPolicy == other.solutionPolicy

private fun comparable(first: String, second: String): Boolean =
    !first.startsWith("unavailable:") && !second.startsWith("unavailable:") && first == second

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

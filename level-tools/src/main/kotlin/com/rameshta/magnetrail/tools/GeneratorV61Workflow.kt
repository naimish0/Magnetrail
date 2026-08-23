package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.generation.v6.AutomatedDifficultyBandV61
import com.rameshta.magnetrail.core.generation.v6.AUTOMATED_CAMPAIGN_BANDS_V61
import com.rameshta.magnetrail.core.generation.v6.CompleteDecisionDagAnalyzerV6
import com.rameshta.magnetrail.core.generation.v6.CuedAdversarialDifficultyAnalyzerV61
import com.rameshta.magnetrail.core.generation.v6.AutomatedDifficultyAssessmentV61
import com.rameshta.magnetrail.core.generation.v6.CampaignFamilyAllocatorV61
import com.rameshta.magnetrail.core.generation.v6.CampaignPacingValidatorV61
import com.rameshta.magnetrail.core.generation.v6.CampaignSlotV61
import com.rameshta.magnetrail.core.generation.v6.GENERATOR_IDENTITY_V61
import com.rameshta.magnetrail.core.generation.v6.GeneratorV61
import com.rameshta.magnetrail.core.generation.v6.MixedDifficultyScheduleV61
import com.rameshta.magnetrail.core.generation.v6.TopologyFamilyV61
import com.rameshta.magnetrail.core.generation.v6.V61CertifiedCandidate
import com.rameshta.magnetrail.core.generation.v6.V61_CAUSAL_FAMILY_COUNT
import com.rameshta.magnetrail.core.generation.v6.V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT
import com.rameshta.magnetrail.core.generation.v6.V61_HARD_CAPACITY_ROLLOUT_LEVEL
import com.rameshta.magnetrail.core.generation.v6.V61_HIGH_BAND_CAPACITY_EPOCH_START_ATTEMPT
import com.rameshta.magnetrail.core.generation.v6.V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL
import com.rameshta.magnetrail.core.generation.v6.V61GenerationBudgets
import com.rameshta.magnetrail.core.generation.v6.V61GenerationRequest
import com.rameshta.magnetrail.core.generation.v6.V61GenerationResult
import com.rameshta.magnetrail.core.generation.v6.V61GenerationFailure
import com.rameshta.magnetrail.core.generation.v6.V61AnalysisCache
import com.rameshta.magnetrail.core.generation.v6.V61FingerprintIndex
import com.rameshta.magnetrail.core.generation.v6.V6FingerprintBundle
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.model.DifficultyBand
import com.rameshta.magnetrail.core.model.GradingThresholds
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelMetadata
import com.rameshta.magnetrail.core.model.LevelOrigin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.lang.management.ManagementFactory
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private data class V61HumanRow(
    val levelId: String,
    val expectedRating: Int,
    val perceivedRating: Int,
    val guessRequired: Boolean,
)

fun analyzeGeneratorV61Regression(options: Map<String, String>) {
    val v6Catalog = LevelParser().parseCatalog(File(options.requiredV61("v6-catalog")).readText())
    val v10Catalog = LevelParser().parseCatalog(File(options.requiredV61("campaign")).readText())
    val rows = parseV61HumanRows(File(options.requiredV61("human-results")))
    val engine = DefaultGameEngine()
    val analyzer = CuedAdversarialDifficultyAnalyzerV61()
    val assessments = rows.associate { row ->
        val level = v6Catalog.levels.single { it.id == row.levelId }
        val dag = CompleteDecisionDagAnalyzerV6(engine).analyze(level.initialState())
        row.levelId to analyzer.analyze(
            level,
            dag,
            semanticNoveltyPass = true,
            purposefulOccupancyPass = true,
            knownGuessRequired = row.guessRequired,
        )
    }
    val formerHigh = rows.filter { it.expectedRating >= AutomatedDifficultyBandV61.SUPER_HARD.rank }
    val retainedHigh = formerHigh.filter { row ->
        (assessments.getValue(row.levelId).maximumEligibleBand?.rank ?: 0) >= row.expectedRating
    }
    val guessRows = rows.filter(V61HumanRow::guessRequired)
    val guessNotRejectedOrCapped = guessRows.filter { row ->
        val result = assessments.getValue(row.levelId)
        result.guessDependencePass && (result.maximumEligibleBand?.rank ?: 0) >= row.expectedRating
    }
    val anchors = listOf("campaign-508", "campaign-1828").associateWith { levelId ->
        val level = v10Catalog.levels.single { it.id == levelId }
        val dag = CompleteDecisionDagAnalyzerV6(engine).analyze(level.initialState())
        analyzer.analyze(
            level,
            dag,
            semanticNoveltyPass = true,
            purposefulOccupancyPass = true,
        )
    }
    val audit = semanticAuditV61(File(options.requiredV61("v6-audit")))
    val exact = v6Catalog.levels.map(ContentFingerprint::exact)
    val d4 = v6Catalog.levels.map(ContentFingerprint::symmetryNormalized)
    val failures = buildList {
        if (rows.size != 30) add("expected 30 V6 observations, found ${rows.size}")
        if (formerHigh.size != 12) add("expected 12 former Super Hard/Expert boards, found ${formerHigh.size}")
        if (retainedHigh.isNotEmpty()) add("former high assignments retained: ${retainedHigh.map { it.levelId }}")
        if (guessRows.size != 23) add("expected 23 guess-required observations, found ${guessRows.size}")
        if (guessNotRejectedOrCapped.isNotEmpty()) add("guess reports escaped V6.1: ${guessNotRejectedOrCapped.map { it.levelId }}")
        if (anchors.values.any { (it.maximumEligibleBand?.rank ?: 0) >= AutomatedDifficultyBandV61.SUPER_HARD.rank }) {
            add("a V10 anchor reached a high tier without the required V6.1 causal evidence")
        }
        if (exact.distinct().size != exact.size) add("V6 exact uniqueness regressed")
        if (d4.distinct().size != d4.size) add("V6 D4 uniqueness regressed")
        if (audit.values.any { !it }) add("V6 semantic audit contains duplicate fingerprints: $audit")
    }
    val output = File(options.requiredV61("output")).also { it.mkdirs() }
    val report = buildJsonObject {
        put("schemaVersion", 1)
        put("status", if (failures.isEmpty()) "V61_HARD_NEGATIVE_REGRESSION_PASS" else "V61_HARD_NEGATIVE_REGRESSION_FAIL")
        put("observationCount", rows.size)
        put("formerHighBoardCount", formerHigh.size)
        put("formerHighAssignmentsRetained", retainedHigh.size)
        put("guessRequiredCount", guessRows.size)
        put("guessRowsEscapingRejectionOrCap", guessNotRejectedOrCapped.size)
        put("exactUnique", exact.distinct().size == exact.size)
        put("d4Unique", d4.distinct().size == d4.size)
        put("semanticUniqueness", buildJsonObject { audit.forEach(::put) })
        put("anchors", buildJsonArray {
            anchors.forEach { (id, result) ->
                add(buildJsonObject {
                    put("levelId", id)
                    put("solutionActions", result.solutionActions)
                    put("maximumEligibleBand", result.maximumEligibleBand?.displayName ?: "NONE")
                    put("bestCheapPolicySolveRate", result.policyReport.bestCheapPolicySolveRate)
                })
            }
        })
        put("hardNegatives", buildJsonArray {
            formerHigh.forEach { row ->
                val result = assessments.getValue(row.levelId)
                add(buildJsonObject {
                    put("levelId", row.levelId)
                    put("formerAssignedRating", row.expectedRating)
                    put("perceivedRating", row.perceivedRating)
                    put("guessRequired", row.guessRequired)
                    put("v61MaximumEligibleBand", result.maximumEligibleBand?.displayName ?: "NONE")
                    put("accepted", result.accepted)
                    put("rejections", buildJsonArray { result.rejectionReasons.forEach { add(JsonPrimitive(it)) } })
                })
            }
        })
        put("failures", buildJsonArray { failures.forEach { add(JsonPrimitive(it)) } })
        put(
            "frozenEvidence",
            "30/30 completed without hints; ratings 17 Easy, 11 Medium, 2 Hard; Spearman 0.498; " +
                "within-one-band 50%; guessing 23/30; repeated strategy 1/30; 11/12 former high boards were Easy/Medium.",
        )
    }
    File(output, "GENERATOR_V61_HARD_NEGATIVE_REGRESSION.json").writeText(
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report) + "\n",
    )
    File(output, "GENERATOR_V61_HARD_NEGATIVE_REGRESSION.md").writeText(
        buildString {
            appendLine("# Generator V6.1 hard-negative regression")
            appendLine()
            appendLine("Status: `${report["status"]?.jsonPrimitive?.content}`")
            appendLine()
            appendLine("- Former V6 Super Hard/Expert assignments retained: ${retainedHigh.size}/12")
            appendLine("- Guess-required rows escaping rejection/cap: ${guessNotRejectedOrCapped.size}/23")
            appendLine("- V6 exact/D4 uniqueness preserved: ${exact.distinct().size == exact.size}/${d4.distinct().size == d4.size}")
            appendLine("- Semantic fingerprint uniqueness: $audit")
            anchors.forEach { (id, result) ->
                appendLine("- $id: ${result.solutionActions} actions; V6.1 cap ${result.maximumEligibleBand?.displayName ?: "NONE"}")
            }
            if (failures.isNotEmpty()) {
                appendLine()
                appendLine("Failures:")
                failures.forEach { appendLine("- $it") }
            }
        },
    )
    check(failures.isEmpty()) { failures.joinToString("; ") }
    println("V6.1 hard-negative regression PASS: ${formerHigh.size} high negatives and ${guessRows.size} guess reports")
}

private fun semanticAuditV61(file: File): Map<String, Boolean> {
    val candidates = Json.parseToJsonElement(file.readText()).jsonObject.getValue("candidates").jsonArray
        .map { it.jsonObject }.filter { it["accepted"]?.jsonPrimitive?.content == "true" }
    return listOf("causal", "decisionDag", "solutionPolicy").associateWith { key ->
        val values = candidates.map { candidate ->
            candidate.getValue("fingerprints").jsonObject.getValue(key).jsonPrimitive.content
        }
        values.distinct().size == values.size
    }
}

private fun parseV61HumanRows(file: File): List<V61HumanRow> {
    val lines = file.readLines().filter(String::isNotBlank)
    val header = parseV61CsvLine(lines.first())
    val index = header.withIndex().associate { it.value to it.index }
    fun List<String>.field(name: String): String = get(requireNotNull(index[name]) { "Missing CSV column $name" })
    return lines.drop(1).map { parseV61CsvLine(it) }.map { values ->
        V61HumanRow(
            levelId = values.field("level_id"),
            expectedRating = values.field("expected_rating").toInt(),
            perceivedRating = values.field("perceived_rating").toInt(),
            guessRequired = values.field("guess_required").toBooleanStrict(),
        )
    }
}

private fun parseV61CsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val character = line[index]
        when {
            character == '"' && quoted && line.getOrNull(index + 1) == '"' -> {
                current.append('"')
                index += 1
            }
            character == '"' -> quoted = !quoted
            character == ',' && !quoted -> {
                result += current.toString()
                current.clear()
            }
            else -> current.append(character)
        }
        index += 1
    }
    result += current.toString()
    return result
}

private fun Map<String, String>.requiredV61(name: String): String =
    requireNotNull(this[name]) { "Missing --$name" }

fun writeGeneratorV61Preflight(options: Map<String, String>) {
    require(options["mandatory-tests-passed"]?.toBooleanStrictOrNull() == true)
    val campaign = File(options.requiredV61("campaign"))
    val relevantPaths = options.requiredV61("relevant-source-paths").split(File.pathSeparator)
        .filter(String::isNotBlank).map(::File)
    val report = buildJsonObject {
        put("schemaVersion", 1)
        put("status", "V61_PREFLIGHT_PASS")
        put("validationPlan", V61_PHASED_VALIDATION_PLAN)
        put("sourceCampaignSha256", sha256V61(campaign))
        put("relevantSourceHash", hashPathsV61(relevantPaths))
        put("relevantFileCount", relevantFilesV61(relevantPaths).size)
        put("mandatoryAutomatedTestsPassed", true)
        put("testCommandResults", options.requiredV61("test-command-summary"))
        put("completedAt", Instant.now().toString())
    }
    writeAtomicallyV61(
        File(options.requiredV61("output")),
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report) + "\n",
    )
    println("V61_PREFLIGHT_PASS: relevantSourceHash=${report.getValue("relevantSourceHash").jsonPrimitive.content}")
}

private const val V61_CONTENT_VERSION = 12
private const val V61_CATALOG_GENERATOR_VERSION = 6
private const val V61_EXPECTED_V10_SHA = "8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9"
internal const val V61_PHASE_ONE_BOARD_COUNT = 1_325
internal const val V61_PHASE_TWO_BOARD_COUNT = 868
private const val V61_FINAL_BOARD_COUNT = 2_205
private const val V61_NON_TUTORIAL_BOARD_COUNT = V61_PHASE_ONE_BOARD_COUNT + V61_PHASE_TWO_BOARD_COUNT
private const val V61_PHASED_VALIDATION_PLAN = "PHASED_V61_1325_868_FINAL_2205"

internal enum class V61CampaignPhase(
    val optionName: String,
    val expectedBoardCount: Int,
    val acceptedStatus: String,
    val rejectedStatus: String,
    val artifactToken: String,
    val catalogId: String,
    val includedBands: Set<AutomatedDifficultyBandV61>,
) {
    PHASE_ONE(
        optionName = "phase-1",
        expectedBoardCount = V61_PHASE_ONE_BOARD_COUNT,
        acceptedStatus = "V61_PHASE_1_CERTIFIED",
        rejectedStatus = "V61_PHASE_1_REJECTED",
        artifactToken = "PHASE1",
        catalogId = "magnetrail-campaign-v12-v6.1-phase-1",
        includedBands = setOf(
            AutomatedDifficultyBandV61.EASY,
            AutomatedDifficultyBandV61.MEDIUM,
            AutomatedDifficultyBandV61.HARD,
        ),
    ),
    PHASE_TWO(
        optionName = "phase-2",
        expectedBoardCount = V61_PHASE_TWO_BOARD_COUNT,
        acceptedStatus = "V61_PHASE_2_CERTIFIED",
        rejectedStatus = "V61_PHASE_2_REJECTED",
        artifactToken = "PHASE2",
        catalogId = "magnetrail-campaign-v12-v6.1-phase-2",
        includedBands = setOf(
            AutomatedDifficultyBandV61.SUPER_HARD,
            AutomatedDifficultyBandV61.EXPERT,
        ),
    ),
    FULL(
        optionName = "full",
        expectedBoardCount = V61_NON_TUTORIAL_BOARD_COUNT,
        acceptedStatus = "AUTOMATED_CAMPAIGN_CERTIFIED",
        rejectedStatus = "AUTOMATED_CAMPAIGN_REJECTED",
        artifactToken = "CAMPAIGN",
        catalogId = "magnetrail-campaign-v12-v6.1",
        includedBands = AUTOMATED_CAMPAIGN_BANDS_V61.toSet(),
    ),
    ;

    companion object {
        fun parse(value: String): V61CampaignPhase = entries.singleOrNull { it.optionName == value }
            ?: error("Unknown V6.1 campaign phase '$value'")
    }
}

internal data class V61ScheduledCampaignSlot(
    val levelNumber: Int,
    val band: AutomatedDifficultyBandV61,
    val familyIndex: Int,
)

internal fun scheduledCampaignSlotsV61(phase: V61CampaignPhase): List<V61ScheduledCampaignSlot> {
    val numbers = (MixedDifficultyScheduleV61.TUTORIAL_END_LEVEL + 1..
        MixedDifficultyScheduleV61.NUMBERED_CAMPAIGN_END_LEVEL).toList()
    val bands = numbers.map(MixedDifficultyScheduleV61::bandForLevel)
    val families = CampaignFamilyAllocatorV61.allocate(bands)
    return numbers.indices.map { index -> V61ScheduledCampaignSlot(numbers[index], bands[index], families[index]) }
        .filter { it.band in phase.includedBands }
        .also { slots ->
            require(slots.size == phase.expectedBoardCount) {
                "${phase.optionName} schedule drift: expected ${phase.expectedBoardCount}, found ${slots.size}"
            }
        }
}

fun benchmarkGeneratorV61AutoJourney(options: Map<String, String>) {
    val output = File(options.requiredV61("output")).also { it.mkdirs() }
    val runtime = Runtime.getRuntime()
    val threadBean = ManagementFactory.getThreadMXBean()
    val rows = AUTOMATED_CAMPAIGN_BANDS_V61.mapIndexed { index, band ->
        System.gc()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()
        val cpuBefore = if (threadBean.isCurrentThreadCpuTimeSupported) threadBean.currentThreadCpuTime else -1L
        val started = System.nanoTime()
        val result = GeneratorV61().generate(
            V61GenerationRequest(
                levelId = "auto-journey-v1-${index + 1}",
                playerFacingNumber = 2206 + index,
                ordinal = index + 1,
                band = band,
                budgets = V61GenerationBudgets(
                    maximumAttempts = 2,
                    decisionDagStates = 5_000,
                    decisionDagResolutions = 50_000,
                    solverStates = 5_000,
                    counterfactualChecks = 20_000,
                    canonicalBacktrackingStates = 20_000,
                ),
            ),
        )
        val wallMillis = (System.nanoTime() - started) / 1_000_000
        val cpuMillis = if (cpuBefore < 0) -1 else (threadBean.currentThreadCpuTime - cpuBefore) / 1_000_000
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        buildJsonObject {
            put("band", band.displayName)
            put("boardSize", when (band) {
                AutomatedDifficultyBandV61.EASY -> "4x4"
                AutomatedDifficultyBandV61.MEDIUM -> "5x5"
                AutomatedDifficultyBandV61.HARD -> "6x6"
                AutomatedDifficultyBandV61.SUPER_HARD -> "7x7"
                AutomatedDifficultyBandV61.EXPERT, AutomatedDifficultyBandV61.MASTER -> "8x8"
            })
            put("wallMillis", wallMillis)
            put("threadCpuMillis", cpuMillis)
            put("heapDeltaBytes", heapAfter - heapBefore)
            when (result) {
                is V61GenerationResult.Certified -> {
                    put("status", "CERTIFIED")
                    put("attempts", result.candidate.examinedAttempts)
                    put("decisionStates", result.candidate.decisionDag.nodes.size)
                }
                is V61GenerationResult.Rejected -> {
                    put("status", "REJECTED")
                    put("attempts", result.failure.examinedAttempts)
                    put("rejections", buildJsonObject {
                        result.failure.rejectionCounts.toSortedMap().forEach { (key, value) -> put(key, value) }
                    })
                }
            }
        }
    }
    val report = buildJsonObject {
        put("schemaVersion", 1)
        put("generatorIdentity", GENERATOR_IDENTITY_V61)
        put("environment", "host JVM; not an Android device")
        put("generationTimestamp", Instant.now().toString())
        put("attemptsPerBand", 2)
        put("runsOffAndroidMainThreadByContract", true)
        put("thermalMeasurementAvailable", false)
        put("uiResponsivenessMeasured", false)
        put("rows", JsonArray(rows))
    }
    File(output, "GENERATOR_V61_AUTO_JOURNEY_HOST_BENCHMARK.json").writeText(
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report) + "\n",
    )
    File(output, "GENERATOR_V61_AUTO_JOURNEY_HOST_BENCHMARK.md").writeText(
        buildString {
            appendLine("# Generator V6.1 Auto Journey host benchmark")
            appendLine()
            appendLine("This is a bounded host-JVM diagnostic, not a device thermal, ANR, or UI benchmark.")
            appendLine()
            rows.forEach { row ->
                appendLine(
                    "- ${row.getValue("band").jsonPrimitive.content} (${row.getValue("boardSize").jsonPrimitive.content}): " +
                        "${row.getValue("status").jsonPrimitive.content}, wall ${row.getValue("wallMillis").jsonPrimitive.content} ms, " +
                        "thread CPU ${row.getValue("threadCpuMillis").jsonPrimitive.content} ms, " +
                        "heap delta ${row.getValue("heapDeltaBytes").jsonPrimitive.content} bytes.",
                )
            }
            appendLine()
            appendLine("Runtime orchestration uses `Dispatchers.Default`; the app test asserts benchmark work is not on the main thread.")
            appendLine("Thermal impact and rendered UI responsiveness require a supported physical/emulated device and were not measured here.")
        },
    )
    println("V6.1 Auto Journey host benchmark wrote ${rows.size} bounded band rows")
}

fun benchmarkGeneratorV61ParallelWorkflow(options: Map<String, String>) {
    val workers = (options["workers"]?.toInt() ?: Runtime.getRuntime().availableProcessors() - 1).coerceIn(2, 24)
    val output = File(options.requiredV61("output")).also(File::mkdirs)
    val seedRequest = V61GenerationRequest(
        levelId = "v61-parallel-benchmark",
        playerFacingNumber = 401,
        ordinal = 389,
        band = AutomatedDifficultyBandV61.HARD,
        causalFamilyIndex = 12,
        varyCausalFamilyByAttempt = true,
        budgets = V61GenerationBudgets(maximumAttempts = 1),
    )
    val archive = (0 until 4).mapNotNull { attempt ->
        when (val result = GeneratorV61().generate(seedRequest.copy(startingAttempt = attempt))) {
            is V61GenerationResult.Certified -> result.candidate.fingerprints
            is V61GenerationResult.Rejected -> null
        }
    }
    val baselineRequest = seedRequest.copy(
        startingAttempt = 0,
        knownFingerprints = archive,
        budgets = V61GenerationBudgets(maximumAttempts = 12),
    )
    val baselineStarted = System.nanoTime()
    val baseline = GeneratorV61().generate(baselineRequest)
    val baselineMillis = (System.nanoTime() - baselineStarted) / 1_000_000

    val executor = Executors.newFixedThreadPool(workers)
    val parallelStarted = System.nanoTime()
    val parallel = try {
        generateParallelV61(
            baseRequest = seedRequest.copy(
                knownFingerprintIndex = V61FingerprintIndex(archive),
                analysisCache = V61AnalysisCache(),
            ),
            maximumAttempts = 12,
            workers = workers,
            executor = executor,
            runRejectionHistogram = linkedMapOf(),
        )
    } finally {
        executor.shutdownNow()
    }
    val parallelMillis = (System.nanoTime() - parallelStarted) / 1_000_000
    val identical = when {
        baseline is V61GenerationResult.Certified && parallel is V61GenerationResult.Certified ->
            baseline.candidate.identity.stableKey == parallel.candidate.identity.stableKey &&
                baseline.candidate.fingerprints.exactLayout == parallel.candidate.fingerprints.exactLayout
        baseline is V61GenerationResult.Rejected && parallel is V61GenerationResult.Rejected -> true
        else -> false
    }
    check(identical) { "Parallel benchmark changed deterministic admission output" }
    val report = buildJsonObject {
        put("schemaVersion", 1)
        put("status", "V61_PARALLEL_WORKFLOW_BENCHMARK_COMPLETE")
        put("workers", workers)
        put("archiveCandidates", archive.size)
        put("maximumAttempts", 12)
        put("sequentialWallMillis", baselineMillis)
        put("parallelWallMillis", parallelMillis)
        put("wallSpeedup", baselineMillis.toDouble() / parallelMillis.coerceAtLeast(1))
        put("identicalAdmissionOutput", identical)
        put("sequentialStatus", baseline::class.simpleName ?: "unknown")
        put("parallelStatus", parallel::class.simpleName ?: "unknown")
    }
    File(output, "GENERATOR_V61_PARALLEL_WORKFLOW_BENCHMARK.json").writeText(
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report) + "\n",
    )
    println(
        "V6.1 parallel benchmark: sequential=${baselineMillis}ms parallel=${parallelMillis}ms " +
            "speedup=${"%.2f".format(baselineMillis.toDouble() / parallelMillis.coerceAtLeast(1))}x workers=$workers",
    )
}

internal data class V61CandidateAuditRow(
    val levelNumber: Int,
    val requestedBand: AutomatedDifficultyBandV61,
    val accepted: Boolean,
    val identity: String?,
    val family: String?,
    val cluster: String?,
    val fingerprints: V6FingerprintBundle?,
    val occupiedRatio: Double?,
    val purposefulRatio: Double?,
    val inertRatio: Double?,
    val stateCount: Int?,
    val analysisCompletenessPass: Boolean?,
    val criticalDecisionCount: Int?,
    val persistentTrapCount: Int?,
    val minimumVisibleLookahead: Int?,
    val inferableCriticalDecisionRatio: Double?,
    val guessDependencePass: Boolean?,
    val bestCheapPolicySolveRate: Double?,
    val maximumEligibleBand: AutomatedDifficultyBandV61?,
    val rejectionCounts: Map<String, Int>,
)

internal data class V61CheckpointContext(
    val sourceCampaignSha256: String,
    val archiveBindingHash: String,
    val relevantSourceHash: String,
    val preflightSha256: String,
)

internal const val V61_HARD_CAPACITY_BASE_RELEVANT_SOURCE_HASH =
    "b3b513dc854d71e2012c37524202f1602fa804bcad4a4e8d1e86e596172b829f"
internal const val V61_HARD_CAPACITY_EPOCH_RELEVANT_SOURCE_HASH =
    "53816806162a1a5b8dc5e6e38ae31e58924ef53c05b009f200f0fe6ec292bc47"
internal const val V61_SYNTHESIS_EVIDENCE_RELEVANT_SOURCE_HASH =
    "a5a54dc459cd5c7b9e29b4ec0bdacc721e2e5fa352936c9dfceaffec3e152aa7"
internal const val V61_HIGH_BAND_CAPACITY_BASE_RELEVANT_SOURCE_HASH =
    "f36c3410547ee8d586fb3c1b139ec8c9f9f7ab7a9ce66a4d38d7c9527ba921a9"
internal const val V61_SUPER_HARD_ONLY_BASE_RELEVANT_SOURCE_HASH =
    "af61c6f5c0b8cc5506bfc02912c7359adebf683a318cbe26463167ecabae6fc6"
internal const val V61_EPOCH_ROTATION_BASE_RELEVANT_SOURCE_HASH =
    "5bdf8b47f6493c1651dfea77a5a24b1fceaee974686520e63faeda4cb6e16141"
internal const val V61_SPECULATIVE_WINDOW_BASE_RELEVANT_SOURCE_HASH =
    "2878c155c29f0a5ebb01854d1574582b49cb33aca7854f24728e998b35d123f5"

fun generateGeneratorV61Campaign(options: Map<String, String>) {
    val sourceFile = File(options.requiredV61("campaign"))
    val sourceSha = sha256V61(sourceFile)
    require(sourceSha == options["expected-production-sha"].orEmpty().ifBlank { V61_EXPECTED_V10_SHA }) {
        "Protected V10 SHA mismatch: expected ${options["expected-production-sha"] ?: V61_EXPECTED_V10_SHA}, found $sourceSha"
    }
    val source = LevelParser().parseCatalog(sourceFile.readText())
    require(source.levels.size == 2205 && source.contentVersion == 10 && source.generatorVersion == 5)
    require(source.levels.take(12).map { it.number } == (1..12).toList()) { "Tutorial range is not exactly 1–12" }
    val output = File(options.requiredV61("output")).also { it.mkdirs() }
    val probe = options["probe"]?.toBooleanStrictOrNull() ?: false
    val phase = if (probe) V61CampaignPhase.FULL else V61CampaignPhase.parse(options["phase"] ?: "full")
    val checkpointContext = if (probe) null else {
        val relevantPaths = options.requiredV61("relevant-source-paths").split(File.pathSeparator)
            .filter(String::isNotBlank).map(::File)
        val relevantSourceHash = hashPathsV61(relevantPaths)
        val preflightFile = File(options.requiredV61("preflight"))
        require(preflightFile.isFile) { "V6.1 hash-bound preflight is missing: ${preflightFile.path}" }
        val preflight = Json.parseToJsonElement(preflightFile.readText()).jsonObject
        require(preflight["status"]?.jsonPrimitive?.content == "V61_PREFLIGHT_PASS")
        require(preflight["mandatoryAutomatedTestsPassed"]?.jsonPrimitive?.content == "true")
        require(preflight["sourceCampaignSha256"]?.jsonPrimitive?.content == sourceSha) {
            "V6.1 preflight source campaign hash is stale"
        }
        require(preflight["relevantSourceHash"]?.jsonPrimitive?.content == relevantSourceHash) {
            "V6.1 preflight relevant-source hash is stale; mandatory gates must run again"
        }
        V61CheckpointContext(
            sourceCampaignSha256 = sourceSha,
            archiveBindingHash = bindingHashV61(options),
            relevantSourceHash = relevantSourceHash,
            preflightSha256 = sha256V61(preflightFile),
        )
    }
    val checkpointDirectory = options["checkpoint"]?.let(::File)?.also(File::mkdirs)
    val maximumAttempts = options["maximum-attempts"]?.toInt() ?: if (probe) 8 else 64
    val workers = (options["workers"]?.toInt() ?: Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 24)
    val scheduledSlots = if (probe) {
        val probeBands = AUTOMATED_CAMPAIGN_BANDS_V61
        val probeFamilies = CampaignFamilyAllocatorV61.allocate(probeBands)
        probeBands.indices.map { index ->
            V61ScheduledCampaignSlot(13 + index, probeBands[index], probeFamilies[index])
        }
    } else {
        scheduledCampaignSlotsV61(phase)
    }
    val requestedNumbers = scheduledSlots.map(V61ScheduledCampaignSlot::levelNumber)
    val bands = scheduledSlots.map(V61ScheduledCampaignSlot::band)
    val known = mutableListOf<V6FingerprintBundle>()
    options["comparison-catalogs"]?.split(File.pathSeparator)?.filter(String::isNotBlank)?.forEach { path ->
        val catalog = LevelParser().parseCatalog(File(path).readText())
        known += catalog.levels.map(::layoutOnlyV61)
    }
    options["v6-audit"]?.let(::File)?.takeIf(File::isFile)?.let { known += auditBundlesV61(it) }
    options["capacity-audit"]?.let(::File)?.takeIf(File::isFile)?.let { known += auditBundlesV61(it) }
    options["capacity-audits"]?.split(File.pathSeparator)?.filter(String::isNotBlank)?.forEach { path ->
        File(path).takeIf(File::isFile)?.let { known += auditBundlesV61(it) }
    }
    options["archive-audits"]?.split(File.pathSeparator)?.filter(String::isNotBlank)?.forEach { path ->
        File(path).takeIf(File::isFile)?.let { known += auditBundlesV61(it) }
    }
    val fingerprintIndex = V61FingerprintIndex(known)
    val analysisCache = V61AnalysisCache()

    val generated = mutableListOf<LevelDefinition>()
    val audits = mutableListOf<V61CandidateAuditRow>()
    val admittedPacingSlots = mutableListOf<CampaignSlotV61>()
    options["pacing-audits"]?.split(File.pathSeparator)?.filter(String::isNotBlank)?.forEach { path ->
        File(path).takeIf(File::isFile)?.let { admittedPacingSlots += pacingSlotsFromAuditV61(it) }
    }
    val perBandFamilyCounts = mutableMapOf<Pair<AutomatedDifficultyBandV61, String>, Int>()
    val perBandClusterCounts = mutableMapOf<Pair<AutomatedDifficultyBandV61, String>, Int>()
    val perBandTargets = bands.groupingBy { it }.eachCount()
    var limitingFailure: String? = null
    val rejectionHistogram = linkedMapOf<String, Int>()
    val generationStarted = System.nanoTime()
    val executor = Executors.newFixedThreadPool(workers)

    val restoredNumbers = linkedSetOf<Int>()
    val checkpointCompatibility = mutableListOf<JsonObject>()
    if (!probe && checkpointDirectory != null) {
        for (scheduled in scheduledSlots) {
            val levelFile = File(checkpointDirectory, checkpointLevelNameV61(scheduled.levelNumber))
            val auditFile = File(checkpointDirectory, checkpointAuditNameV61(scheduled.levelNumber))
            val atomicFile = File(checkpointDirectory, checkpointAtomicNameV61(scheduled.levelNumber))
            if (!atomicFile.isFile && (!levelFile.isFile || !auditFile.isFile)) break
            val restored = runCatching {
                val atomic = atomicFile.takeIf(File::isFile)?.let {
                    Json.parseToJsonElement(it.readText()).jsonObject
                }
                val atomicContextMatches = atomic != null && checkpointMatchesContextV61(
                    atomic,
                    phase,
                    requireNotNull(checkpointContext),
                )
                val catalogText = atomic?.getValue("catalog")?.toString() ?: levelFile.readText()
                val auditJson = atomic?.getValue("audit")?.jsonObject
                    ?: Json.parseToJsonElement(auditFile.readText()).jsonObject
                val level = LevelParser().parseCatalog(catalogText).levels.single()
                var row = candidateAuditRowFromJsonV61(
                    auditJson.getValue("candidates").jsonArray.single().jsonObject,
                )
                val metadata = requireNotNull(level.metadata)
                require(level.number == scheduled.levelNumber && level.id == "campaign-${scheduled.levelNumber}")
                require(row.accepted && row.levelNumber == scheduled.levelNumber && row.requestedBand == scheduled.band)
                require(metadata.contentVersion == V61_CONTENT_VERSION &&
                    metadata.generatorVersion == V61_CATALOG_GENERATOR_VERSION)
                require(row.fingerprints?.exactLayout == ContentFingerprint.exact(level))
                require(row.fingerprints.d4Layout == ContentFingerprint.symmetryNormalized(level))
                require(row.analysisCompletenessPass == true && row.inferableCriticalDecisionRatio == 1.0)
                require(row.guessDependencePass == true &&
                    (row.maximumEligibleBand?.rank ?: 0) >= scheduled.band.rank)
                val acceptedAttempt = atomic?.getValue("acceptedAttempt")?.jsonPrimitive?.content?.toInt()
                    ?: acceptedAttemptFromIdentityV61(requireNotNull(row.identity))
                val requiredStartingAttempt = campaignStartingAttemptV61(
                    phase,
                    scheduled.band,
                    scheduled.levelNumber,
                )
                require(acceptedAttempt >= requiredStartingAttempt) {
                    "checkpoint attempt $acceptedAttempt predates required epoch $requiredStartingAttempt"
                }
                atomic?.get("startingAttempt")?.jsonPrimitive?.content?.toInt()?.let { storedStartingAttempt ->
                    require(storedStartingAttempt == requiredStartingAttempt) {
                        "checkpoint starting epoch $storedStartingAttempt does not match $requiredStartingAttempt"
                    }
                }
                val additiveHardEpochCompatible = atomic != null && checkpointMatchesAdditiveHardEpochV61(
                    atomic,
                    phase,
                    requireNotNull(checkpointContext),
                    scheduled.levelNumber,
                    acceptedAttempt,
                )
                val synthesisEvidenceCompatible = atomic != null && checkpointMatchesSynthesisEvidenceMigrationV61(
                    atomic,
                    phase,
                    requireNotNull(checkpointContext),
                )
                val highBandCapacityCompatible = atomic != null && checkpointMatchesHighBandCapacityMigrationV61(
                    atomic,
                    phase,
                    requireNotNull(checkpointContext),
                    scheduled.levelNumber,
                )
                val superHardOnlyCompatible = atomic != null && checkpointMatchesSuperHardOnlyMigrationV61(
                    atomic,
                    phase,
                    requireNotNull(checkpointContext),
                    scheduled.levelNumber,
                )
                val epochRotationCompatible = atomic != null && checkpointMatchesEpochRotationMigrationV61(
                    atomic,
                    phase,
                    requireNotNull(checkpointContext),
                    scheduled.levelNumber,
                    scheduled.band,
                )
                val speculativeWindowCompatible = atomic != null &&
                    checkpointMatchesSpeculativeWindowMigrationV61(
                        atomic,
                        phase,
                        requireNotNull(checkpointContext),
                    )
                if (synthesisEvidenceCompatible) {
                    val oldFingerprints = requireNotNull(row.fingerprints)
                    require(oldFingerprints.synthesisGraphIdentifier.startsWith("unavailable:"))
                    val synthesisRequest = V61GenerationRequest(
                        levelId = "campaign-${scheduled.levelNumber}",
                        playerFacingNumber = scheduled.levelNumber,
                        ordinal = scheduled.levelNumber - 12,
                        band = scheduled.band,
                        causalFamilyIndex = scheduled.familyIndex,
                        varyCausalFamilyByAttempt = true,
                        budgets = V61GenerationBudgets(maximumAttempts = 1),
                    )
                    val synthesisGraph = GeneratorV61().synthesisGraphFingerprintForAttempt(
                        synthesisRequest,
                        acceptedAttempt,
                    )
                    require(!synthesisGraph.startsWith("unavailable:"))
                    val migratedFingerprints = oldFingerprints.copy(
                        synthesisGraphIdentifier = synthesisGraph,
                    )
                    require(fingerprintIndex.duplicateReason(migratedFingerprints) == null) {
                        "checkpoint logical synthesis graph is not unique"
                    }
                    row = row.copy(fingerprints = migratedFingerprints)
                    writeGeneratorV61Checkpoint(
                        checkpointDirectory,
                        phase,
                        level,
                        row,
                        acceptedAttempt,
                        requireNotNull(checkpointContext),
                    )
                } else if (!atomicContextMatches && !additiveHardEpochCompatible &&
                    !highBandCapacityCompatible && !superHardOnlyCompatible && !epochRotationCompatible &&
                    !speculativeWindowCompatible
                ) {
                    val maximumFamilyCount = maxOf(
                        1,
                        kotlin.math.floor(perBandTargets.getValue(scheduled.band) * 0.05).toInt(),
                    )
                    val maximumClusterCount = maxOf(
                        1,
                        kotlin.math.floor(perBandTargets.getValue(scheduled.band) * 0.02).toInt(),
                    )
                    val forbiddenFamilies = perBandFamilyCounts.filter { (key, count) ->
                        key.first == scheduled.band && count >= maximumFamilyCount
                    }.keys.mapTo(mutableSetOf()) { it.second } + admittedPacingSlots.filter {
                        kotlin.math.abs(it.levelNumber - scheduled.levelNumber) <= 7
                    }.map(CampaignSlotV61::causalFamilyIdentifier)
                    val forbiddenClusters = perBandClusterCounts.filter { (key, count) ->
                        key.first == scheduled.band && count >= maximumClusterCount
                    }.keys.mapTo(mutableSetOf()) { it.second } + admittedPacingSlots.filter {
                        kotlin.math.abs(it.levelNumber - scheduled.levelNumber) <= 19
                    }.map(CampaignSlotV61::strategyClusterIdentifier)
                    val legacyRequest = V61GenerationRequest(
                        levelId = "campaign-${scheduled.levelNumber}",
                        playerFacingNumber = scheduled.levelNumber,
                        ordinal = scheduled.levelNumber - 12,
                        band = scheduled.band,
                        causalFamilyIndex = scheduled.familyIndex,
                        varyCausalFamilyByAttempt = true,
                        startingAttempt = acceptedAttempt,
                        knownFingerprintIndex = fingerprintIndex,
                        analysisCache = analysisCache,
                        forbiddenCausalFamilies = forbiddenFamilies.toSet(),
                        forbiddenStrategyClusters = forbiddenClusters.toSet(),
                        budgets = V61GenerationBudgets(maximumAttempts = 1),
                    )
                    val regenerated = executor.submit(Callable { GeneratorV61().generate(legacyRequest) }).get()
                    require(regenerated is V61GenerationResult.Certified) {
                        "legacy deterministic regeneration rejected"
                    }
                    val candidate = regenerated.candidate
                    require(candidate.identity.stableKey == row.identity)
                    require(ContentFingerprint.exact(candidate.level) == ContentFingerprint.exact(level))
                    require(candidate.level.designedSolutions == level.designedSolutions)
                    require(fingerprintEvidenceEquivalentV61(candidate.fingerprints, requireNotNull(row.fingerprints)))
                    val repeated = executor.submit(Callable { GeneratorV61().generate(legacyRequest) }).get()
                    require(repeated is V61GenerationResult.Certified &&
                        repeated.candidate.fingerprints == candidate.fingerprints)
                    writeGeneratorV61Checkpoint(
                        checkpointDirectory,
                        phase,
                        level,
                        row,
                        acceptedAttempt,
                        requireNotNull(checkpointContext),
                    )
                } else if (additiveHardEpochCompatible || highBandCapacityCompatible ||
                    superHardOnlyCompatible || epochRotationCompatible || speculativeWindowCompatible
                ) {
                    // The only generator change after the named base hash is guarded by
                    // attempt >= 1024. Every checkpoint accepted below that boundary therefore
                    // follows byte-for-byte identical construction code. Rebind its already
                    // complete audit atomically instead of regenerating a long certified prefix.
                    writeGeneratorV61Checkpoint(
                        checkpointDirectory,
                        phase,
                        level,
                        row,
                        acceptedAttempt,
                        requireNotNull(checkpointContext),
                    )
                }
                Triple(
                    level,
                    row,
                    when {
                        atomic == null -> "LEGACY_MIGRATED"
                        atomicContextMatches -> "HASH_BOUND_RESTORED"
                        synthesisEvidenceCompatible -> "SYNTHESIS_EVIDENCE_REBOUND"
                        speculativeWindowCompatible -> "SPECULATIVE_WINDOW_REBOUND"
                        epochRotationCompatible -> "SUPER_HARD_EPOCH_REBOUND"
                        superHardOnlyCompatible -> "SUPER_HARD_ONLY_REBOUND"
                        highBandCapacityCompatible -> "HIGH_BAND_CAPACITY_REBOUND"
                        additiveHardEpochCompatible -> "ADDITIVE_HARD_EPOCH_REBOUND"
                        else -> "HASH_BOUND_REVALIDATED"
                    },
                )
            }.getOrElse { error ->
                checkpointCompatibility += buildJsonObject {
                    put("levelNumber", scheduled.levelNumber)
                    put("status", "INCOMPATIBLE_EXCLUDED")
                    put("reason", error.message ?: error::class.simpleName.orEmpty())
                }
                preserveLegacyCheckpointsV61(checkpointDirectory, scheduledSlots, scheduled.levelNumber)
                println("V6.1 preserved and excluded incompatible checkpoint for level ${scheduled.levelNumber}: ${error.message}")
                break
            }
            val (level, row, restoreStatus) = restored
            val fingerprints = requireNotNull(row.fingerprints)
            generated += level
            audits += row
            known += fingerprints
            fingerprintIndex.add(fingerprints)
            val family = requireNotNull(row.family)
            val cluster = requireNotNull(row.cluster)
            perBandFamilyCounts[scheduled.band to family] =
                perBandFamilyCounts.getOrDefault(scheduled.band to family, 0) + 1
            perBandClusterCounts[scheduled.band to cluster] =
                perBandClusterCounts.getOrDefault(scheduled.band to cluster, 0) + 1
            admittedPacingSlots += CampaignSlotV61(scheduled.levelNumber, scheduled.band, family, cluster)
            restoredNumbers += scheduled.levelNumber
            checkpointCompatibility += buildJsonObject {
                put("levelNumber", scheduled.levelNumber)
                put("status", restoreStatus)
            }
            // Exact regeneration has consumed the evidence needed to rebind this level. Avoid
            // retaining complete restored DAGs while walking a long checkpoint prefix.
            analysisCache.clear()
        }
        if (restoredNumbers.isNotEmpty()) {
            println("V6.1 ${phase.optionName} restored ${restoredNumbers.size}/${scheduledSlots.size} certified checkpoints")
        }
        writeCheckpointCompatibilityV61(checkpointDirectory, checkpointCompatibility, requireNotNull(checkpointContext))
    }
    try {
    scheduledSlots.forEachIndexed { index, scheduled ->
        if (limitingFailure != null && !probe) return@forEachIndexed
        if (scheduled.levelNumber in restoredNumbers) return@forEachIndexed
        val levelNumber = scheduled.levelNumber
        val band = scheduled.band
        val maximumFamilyCount = maxOf(1, kotlin.math.floor(perBandTargets.getValue(band) * 0.05).toInt())
        val maximumClusterCount = maxOf(1, kotlin.math.floor(perBandTargets.getValue(band) * 0.02).toInt())
        val saturatedFamilies = perBandFamilyCounts.filter { (key, count) ->
            key.first == band && count >= maximumFamilyCount
        }.keys.mapTo(mutableSetOf()) { it.second }
        val saturatedClusters = perBandClusterCounts.filter { (key, count) ->
            key.first == band && count >= maximumClusterCount
        }.keys.mapTo(mutableSetOf()) { it.second }
        val neighboringFamilies = admittedPacingSlots.filter {
            it.levelNumber != levelNumber && kotlin.math.abs(it.levelNumber - levelNumber) <= 7
        }.mapTo(mutableSetOf(), CampaignSlotV61::causalFamilyIdentifier)
        val neighboringClusters = admittedPacingSlots.filter {
            it.levelNumber != levelNumber && kotlin.math.abs(it.levelNumber - levelNumber) <= 19
        }.mapTo(mutableSetOf(), CampaignSlotV61::strategyClusterIdentifier)
        val request = V61GenerationRequest(
            levelId = if (probe) "v61-probe-${band.name.lowercase()}" else "campaign-$levelNumber",
            playerFacingNumber = levelNumber,
            ordinal = if (probe) index + 1 else levelNumber - 12,
            band = band,
            causalFamilyIndex = scheduled.familyIndex,
            varyCausalFamilyByAttempt = true,
            startingAttempt = campaignStartingAttemptV61(phase, band, levelNumber),
            knownFingerprintIndex = fingerprintIndex,
            analysisCache = analysisCache,
            forbiddenCausalFamilies = saturatedFamilies + neighboringFamilies,
            forbiddenStrategyClusters = saturatedClusters + neighboringClusters,
            budgets = V61GenerationBudgets(maximumAttempts = 1),
        )
        when (val result = generateParallelV61(
            request,
            maximumAttempts,
            workers,
            executor,
            rejectionHistogram,
        )) {
            is V61GenerationResult.Rejected -> {
                val failure = result.failure
                audits += V61CandidateAuditRow(
                    levelNumber = levelNumber,
                    requestedBand = band,
                    accepted = false,
                    identity = null,
                    family = null,
                    cluster = null,
                    fingerprints = null,
                    occupiedRatio = null,
                    purposefulRatio = null,
                    inertRatio = null,
                    stateCount = null,
                    analysisCompletenessPass = null,
                    criticalDecisionCount = null,
                    persistentTrapCount = null,
                    minimumVisibleLookahead = null,
                    inferableCriticalDecisionRatio = null,
                    guessDependencePass = null,
                    bestCheapPolicySolveRate = null,
                    maximumEligibleBand = null,
                    rejectionCounts = failure.rejectionCounts,
                )
                val familyIndex = scheduled.familyIndex
                val topology = TopologyFamilyV61.entries[familyIndex % TopologyFamilyV61.entries.size]
                limitingFailure = "level=$levelNumber band=${band.displayName} familyIndex=$familyIndex " +
                    "topology=${topology.name} variant=${familyIndex / TopologyFamilyV61.entries.size} " +
                    "attempts=${failure.examinedAttempts} " +
                    "rejections=${failure.rejectionCounts}"
            }
            is V61GenerationResult.Certified -> {
                val candidate = result.candidate
                val repeatRequest = request.copy(
                    startingAttempt = candidate.identity.attempt,
                    budgets = request.budgets.copy(maximumAttempts = 1),
                )
                val repeated = executor.submit(Callable { GeneratorV61().generate(repeatRequest) }).get()
                require(repeated is V61GenerationResult.Certified &&
                    repeated.candidate.fingerprints == candidate.fingerprints
                ) { "Deterministic regeneration failed for ${request.levelId}" }
                val level = candidate.withProductionMetadata(source.levels.getOrNull(levelNumber - 1))
                generated += level
                known += candidate.fingerprints
                fingerprintIndex.add(candidate.fingerprints)
                val auditRow = candidate.auditRow(levelNumber, band)
                audits += auditRow
                val family = candidate.identity.causalFamilyIdentifier
                val cluster = candidate.fingerprints.strategyBehaviourClusterIdentifier
                perBandFamilyCounts[band to family] = perBandFamilyCounts.getOrDefault(band to family, 0) + 1
                perBandClusterCounts[band to cluster] = perBandClusterCounts.getOrDefault(band to cluster, 0) + 1
                admittedPacingSlots += CampaignSlotV61(levelNumber, band, family, cluster)
                if (!probe && checkpointDirectory != null) {
                    writeGeneratorV61Checkpoint(
                        checkpointDirectory,
                        phase,
                        level,
                        auditRow,
                        candidate.identity.attempt,
                        requireNotNull(checkpointContext),
                    )
                    val elapsedSeconds = (System.nanoTime() - generationStarted) / 1_000_000_000.0
                    val generatedThisRun = (generated.size - restoredNumbers.size).coerceAtLeast(1)
                    val ratePerMinute = generatedThisRun * 60.0 / elapsedSeconds.coerceAtLeast(0.001)
                    val etaMinutes = (scheduledSlots.size - generated.size) / ratePerMinute.coerceAtLeast(0.001)
                    println(
                        "V6.1 ${phase.optionName} checkpoint ${generated.size}/${scheduledSlots.size}: ${level.id}; " +
                            "rate=${"%.2f".format(ratePerMinute)}/min eta=${"%.1f".format(etaMinutes)}m " +
                            "topRejections=${rejectionHistogram.entries.sortedByDescending { it.value }.take(4)}",
                    )
                }
                // The deterministic repeat has already hit the accepted content cache and the
                // complete evidence is now atomic on disk. Release it before the next slot.
                analysisCache.clear()
            }
        }
    }
    } finally {
        executor.shutdownNow()
    }

    val levels = when {
        probe -> generated
        phase == V61CampaignPhase.FULL -> source.levels.take(12) + generated
        else -> generated
    }
    val catalog = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = "magnetrail-core-1",
        catalogId = if (probe) "magnetrail-v6.1-probe" else phase.catalogId,
        levels = levels,
        contentVersion = V61_CONTENT_VERSION,
        generatorVersion = V61_CATALOG_GENERATOR_VERSION,
    )
    val artifactToken = if (probe) "PROBE" else phase.artifactToken
    val catalogFile = File(
        output,
        when {
            probe -> "GENERATOR_V61_PROBE_CATALOG.json"
            phase == V61CampaignPhase.FULL -> "GENERATOR_V61_CAMPAIGN_CANDIDATE.json"
            else -> "GENERATOR_V61_${artifactToken}_CATALOG.json"
        },
    )
    catalogFile.writeText(LevelParser().encodeCatalog(catalog) + "\n")
    val auditFile = File(output, "GENERATOR_V61_${artifactToken}_AUDIT.json")
    auditFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), campaignAuditJsonV61(audits)) + "\n")

    val pacing = if (probe || phase != V61CampaignPhase.FULL || generated.size != V61_NON_TUTORIAL_BOARD_COUNT) null else
        CampaignPacingValidatorV61.validate(
        audits.mapNotNull { row ->
            if (!row.accepted) null else CampaignSlotV61(
                row.levelNumber,
                row.requestedBand,
                requireNotNull(row.family),
                requireNotNull(row.cluster),
            )
        },
    )
    val regressionFile = options["regression"]?.let(::File)
    val regressionPass = regressionFile?.readText()?.contains("V61_HARD_NEGATIVE_REGRESSION_PASS") == true
    val mandatoryTestsPassed = probe || checkpointContext != null
    val acceptedAudits = audits.filter(V61CandidateAuditRow::accepted)
    fun duplicateCount(selector: (V6FingerprintBundle) -> String): Int = acceptedAudits
        .mapNotNull(V61CandidateAuditRow::fingerprints)
        .groupingBy(selector)
        .eachCount()
        .values
        .sumOf { (it - 1).coerceAtLeast(0) }
    val exactDuplicates = duplicateCount(V6FingerprintBundle::exactLayout)
    val d4Duplicates = duplicateCount(V6FingerprintBundle::d4Layout)
    val relevanceDuplicates = duplicateCount(V6FingerprintBundle::relevancePrunedD4Layout)
    val causalDuplicates = duplicateCount(V6FingerprintBundle::causalHypergraph)
    val dagDuplicates = duplicateCount(V6FingerprintBundle::quotientDecisionDag)
    val policyDuplicates = duplicateCount(V6FingerprintBundle::solutionPolicy)
    val synthesisDuplicates = duplicateCount(V6FingerprintBundle::synthesisGraphIdentifier)
    val nearSemanticFailures = acceptedAudits.count {
        (it.fingerprints?.nearestSemanticSimilarity ?: 0.0) > 0.92
    }
    val duplicateFree = listOf(
        exactDuplicates, d4Duplicates, relevanceDuplicates, causalDuplicates, dagDuplicates, policyDuplicates,
        synthesisDuplicates, nearSemanticFailures,
    ).all { it == 0 }
    val generatedAllRequested = generated.size == phase.expectedBoardCount && limitingFailure == null
    val complete = !probe && generatedAllRequested && regressionPass && mandatoryTestsPassed && duplicateFree &&
        (phase != V61CampaignPhase.FULL || pacing?.passed == true)
    val status = when {
        probe -> "V61_PROBE_ONLY"
        complete -> phase.acceptedStatus
        else -> phase.rejectedStatus
    }
    val manifestFile = File(output, "GENERATOR_V61_${artifactToken}_MANIFEST.json")
    val manifest = buildJsonObject {
        put("schemaVersion", 1)
        put("validationPlan", V61_PHASED_VALIDATION_PLAN)
        put("phase", if (probe) "probe" else phase.optionName)
        put("generatorIdentity", GENERATOR_IDENTITY_V61)
        put("contentVersion", V61_CONTENT_VERSION)
        put("generatorVersion", V61_CATALOG_GENERATOR_VERSION)
        put("frozenCoreVersion", "magnetrail-core-1")
        put("sourceCampaignSha256", sourceSha)
        put("candidateCampaignSha256", sha256V61(catalogFile))
        put("requestedNonTutorialLevels", requestedNumbers.size)
        put("generatedNonTutorialLevels", generated.size)
        put("candidateCatalogSha256", sha256V61(catalogFile))
        put("candidateAuditSha256", sha256V61(auditFile))
        put("maximumAttemptsPerLevel", maximumAttempts)
        put("workers", workers)
        put("analysisCacheHits", analysisCache.stats().hits)
        put("analysisCacheMisses", analysisCache.stats().misses)
        put("analysisCacheHitRate", analysisCache.stats().hitRate)
        put("mandatoryAutomatedTestsPassed", mandatoryTestsPassed)
    }
    manifestFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), manifest) + "\n")
    val certificateFile = File(
        output,
        if (phase == V61CampaignPhase.FULL && !probe) {
            "GENERATOR_V61_AUTOMATED_CERTIFICATE.json"
        } else {
            "GENERATOR_V61_${artifactToken}_CERTIFICATE.json"
        },
    )
    val perBand = audits.filter(V61CandidateAuditRow::accepted).groupingBy { it.requestedBand.displayName }.eachCount()
    val perFamily = acceptedAudits.groupingBy { requireNotNull(it.family) }.eachCount()
    val perCluster = acceptedAudits.groupingBy { requireNotNull(it.cluster) }.eachCount()
    val certificate = buildJsonObject {
        put("schemaVersion", 1)
        put("validationPlan", V61_PHASED_VALIDATION_PLAN)
        put("phase", if (probe) "probe" else phase.optionName)
        put("certificationType", status)
        put("contentVersion", V61_CONTENT_VERSION)
        put("generatorIdentity", GENERATOR_IDENTITY_V61)
        put("frozenCoreVersion", "magnetrail-core-1")
        put("generationTimestamp", Instant.now().toString())
        put("workingTreeIdentity", options["working-tree-identity"] ?: "unrecorded")
        put("workers", workers)
        put("analysisCacheHits", analysisCache.stats().hits)
        put("analysisCacheMisses", analysisCache.stats().misses)
        put("rejectionHistogram", buildJsonObject {
            rejectionHistogram.toSortedMap().forEach { (reason, count) -> put(reason, count) }
        })
        put("campaignHash", sha256V61(catalogFile))
        put("auditHash", sha256V61(auditFile))
        put("manifestHash", sha256V61(manifestFile))
        put("boardCount", levels.size)
        put("expectedBoardCount", if (probe) requestedNumbers.size else phase.expectedBoardCount)
        put("perBandCounts", buildJsonObject { perBand.toSortedMap().forEach { (key, value) -> put(key, value) } })
        put("perFamilyCounts", buildJsonObject { perFamily.toSortedMap().forEach { (key, value) -> put(key, value) } })
        put("perClusterCounts", buildJsonObject { perCluster.toSortedMap().forEach { (key, value) -> put(key, value) } })
        put("sourceProductionHash", sourceSha)
        put(
            "tutorialsPreserved",
            !probe && phase == V61CampaignPhase.FULL &&
                levels.take(12).map(ContentFingerprint::exact) == source.levels.take(12).map(ContentFingerprint::exact),
        )
        put("allAnalysisComplete", complete && audits.all { it.analysisCompletenessPass == true })
        put("allVisibleProofsComplete", complete && audits.all { it.inferableCriticalDecisionRatio == 1.0 })
        put("allProductionEngineSolvableAndReplayed", complete && acceptedAudits.all { it.stateCount != null })
        put("allDifficultyCapsPassed", complete && acceptedAudits.all {
            (it.maximumEligibleBand?.rank ?: 0) >= it.requestedBand.rank
        })
        put("allPurposefulOccupancyPassed", complete && acceptedAudits.all {
            (it.purposefulRatio ?: 0.0) >= (if (it.requestedBand.rank >= 4) 0.90 else 0.85) &&
                (it.inertRatio ?: 1.0) <= 0.10
        })
        put("requiredGuessingPredictionCount", audits.count { it.guessDependencePass == false })
        put("exactDuplicateCount", exactDuplicates)
        put("d4DuplicateCount", d4Duplicates)
        put("relevancePrunedD4DuplicateCount", relevanceDuplicates)
        put("causalGraphDuplicateCount", causalDuplicates)
        put("decisionDagDuplicateCount", dagDuplicates)
        put("solutionPolicyDuplicateCount", policyDuplicates)
        put("synthesisGraphDuplicateCount", synthesisDuplicates)
        put("nearSemanticFailureCount", nearSemanticFailures)
        put("pacingPassed", pacing?.passed == true)
        put("pacingDeferredToFinalMerge", phase == V61CampaignPhase.PHASE_ONE || phase == V61CampaignPhase.PHASE_TWO)
        put("regressionCorpusPassed", regressionPass)
        put("deterministicRegenerationPassed", complete && audits.all { it.fingerprints != null })
        put("mandatoryAutomatedTestSuitePassed", mandatoryTestsPassed)
        put("testCommandResults", options["test-command-summary"] ?: "not supplied")
        put("promotionSource", catalogFile.path)
        put("promotionDestination", sourceFile.path)
        put("limitingFailure", limitingFailure ?: "")
        put(
            "statement",
            if (complete && phase == V61CampaignPhase.FULL) {
                "This campaign passed automated technical certification and has not been human-validated."
            } else if (complete) {
                "This phase passed automated technical certification; global pacing and 2,205-level certification remain deferred."
            } else {
                "This candidate did not pass automated technical certification and has not been promoted."
            },
        )
    }
    certificateFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), certificate) + "\n")
    File(output, "GENERATOR_V61_${artifactToken}_GENERATION_REPORT.md").writeText(
        buildString {
            appendLine("# Generator V6.1 campaign generation")
            appendLine()
            appendLine("Status: `$status`")
            appendLine()
            appendLine("- Source SHA-256: `$sourceSha`")
            appendLine("- Validation phase: ${if (probe) "probe" else phase.optionName}")
            appendLine("- Generated: ${generated.size}/${requestedNumbers.size} requested boards")
            appendLine("- Per-band accepted: $perBand")
            appendLine("- Regression corpus passed: $regressionPass")
            appendLine("- Global pacing: ${if (phase == V61CampaignPhase.FULL) (pacing?.passed == true) else "deferred to final merge"}")
            if (limitingFailure != null) appendLine("- Limiting gate: `$limitingFailure`")
            appendLine()
            appendLine("Difficulty bands are automated classifications; this output has not been human-validated.")
        },
    )
    println("$status: generated ${generated.size}/${requestedNumbers.size}; ${limitingFailure ?: "all phase gates passed"}")
}

internal fun generateParallelV61(
    baseRequest: V61GenerationRequest,
    maximumAttempts: Int,
    workers: Int,
    executor: ExecutorService,
    runRejectionHistogram: MutableMap<String, Int>,
): V61GenerationResult {
    // A single worker-width batch underutilizes the pool when most attempts fail a cheap pacing
    // gate while one attempt performs a complete DAG. Five worker-widths keep all cores supplied
    // without allowing rejected DAG evidence to grow without bound.
    val speculativeWindow = Math.multiplyExact(workers, 5)
    val slotRejections = linkedMapOf<String, Int>()
    val lastCounterexamples = ArrayDeque<String>()
    val firstAttempt = baseRequest.startingAttempt
    val endExclusive = Math.addExact(firstAttempt, maximumAttempts)
    var nextAttempt = firstAttempt
    while (nextAttempt < endExclusive) {
        val attempts = (nextAttempt until minOf(endExclusive, nextAttempt + speculativeWindow)).toList()
        val futures = attempts.associateWith { attempt ->
            executor.submit(Callable {
                GeneratorV61().generate(
                    baseRequest.copy(
                        startingAttempt = attempt,
                        budgets = baseRequest.budgets.copy(maximumAttempts = 1),
                    ),
                )
            })
        }
        var certified: V61GenerationResult.Certified? = null
        for (attempt in attempts) {
            val result = requireNotNull(futures[attempt]).get()
            if (certified != null) continue
            when (result) {
                is V61GenerationResult.Certified -> {
                    certified = result
                }
                is V61GenerationResult.Rejected -> {
                    result.failure.rejectionCounts.forEach { (reason, count) ->
                        slotRejections[reason] = slotRejections.getOrDefault(reason, 0) + count
                        runRejectionHistogram[reason] = runRejectionHistogram.getOrDefault(reason, 0) + count
                    }
                    result.failure.lastCounterexamples.forEach { counterexample ->
                        if (lastCounterexamples.size == 12) lastCounterexamples.removeFirst()
                        lastCounterexamples.addLast(counterexample)
                    }
                }
            }
        }
        // Drain the immutable speculative window before the caller admits the candidate into its
        // uniqueness index. This prevents background reads racing the next campaign mutation.
        if (certified != null) return certified
        // Every attempt in this ordered batch was rejected, so its heavyweight DAG evidence will
        // never be requested by a later identity. Keep cumulative stats but release the objects.
        baseRequest.analysisCache?.clear()
        nextAttempt += attempts.size
    }
    return V61GenerationResult.Rejected(
        V61GenerationFailure(
            request = baseRequest.copy(
                startingAttempt = firstAttempt,
                budgets = baseRequest.budgets.copy(maximumAttempts = maximumAttempts),
            ),
            examinedAttempts = maximumAttempts,
            rejectionCounts = slotRejections,
            lastCounterexamples = lastCounterexamples.toList(),
        ),
    )
}

private data class PersistedV61AuditRow(
    val raw: JsonObject,
    val levelNumber: Int,
    val requestedBand: AutomatedDifficultyBandV61,
    val family: String,
    val cluster: String,
    val fingerprints: JsonObject,
)

private fun persistedAuditRowsV61(file: File): List<PersistedV61AuditRow> =
    Json.parseToJsonElement(file.readText()).jsonObject.getValue("candidates").jsonArray
        .map { it.jsonObject }
        .filter { it["accepted"]?.jsonPrimitive?.content == "true" }
        .map { row ->
            PersistedV61AuditRow(
                raw = row,
                levelNumber = row.getValue("levelNumber").jsonPrimitive.content.toInt(),
                requestedBand = automatedBandV61(row.getValue("requestedBand").jsonPrimitive.content),
                family = row.getValue("causalFamily").jsonPrimitive.content,
                cluster = row.getValue("strategyCluster").jsonPrimitive.content,
                fingerprints = row.getValue("fingerprints").jsonObject,
            )
        }

private fun pacingSlotsFromAuditV61(file: File): List<CampaignSlotV61> = persistedAuditRowsV61(file).map { row ->
    CampaignSlotV61(row.levelNumber, row.requestedBand, row.family, row.cluster)
}

private fun automatedBandV61(value: String): AutomatedDifficultyBandV61 =
    AutomatedDifficultyBandV61.entries.singleOrNull { it.displayName == value || it.name == value }
        ?: error("Unknown V6.1 automated band '$value'")

fun mergeAndCertifyGeneratorV61Campaign(options: Map<String, String>) {
    val sourceFile = File(options.requiredV61("campaign"))
    val sourceSha = sha256V61(sourceFile)
    val expectedSourceSha = options["expected-production-sha"].orEmpty().ifBlank { V61_EXPECTED_V10_SHA }
    require(sourceSha == expectedSourceSha) {
        "Protected V10 SHA mismatch during phased merge: expected $expectedSourceSha, found $sourceSha"
    }
    val source = LevelParser().parseCatalog(sourceFile.readText())
    require(source.levels.size == V61_FINAL_BOARD_COUNT && source.contentVersion == 10 && source.generatorVersion == 5)
    val workers = (options["workers"]?.toInt() ?: Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 24)
    val relevantSourceHash = hashPathsV61(
        options.requiredV61("relevant-source-paths").split(File.pathSeparator)
            .filter(String::isNotBlank).map(::File),
    )
    val preflightFile = File(options.requiredV61("preflight"))
    val preflight = Json.parseToJsonElement(preflightFile.readText()).jsonObject
    require(preflight["status"]?.jsonPrimitive?.content == "V61_PREFLIGHT_PASS")
    require(preflight["mandatoryAutomatedTestsPassed"]?.jsonPrimitive?.content == "true")
    require(preflight["sourceCampaignSha256"]?.jsonPrimitive?.content == sourceSha)
    require(preflight["relevantSourceHash"]?.jsonPrimitive?.content == relevantSourceHash)

    val phaseOneFiles = V61PhaseFiles(
        catalog = File(options.requiredV61("phase-1-catalog")),
        audit = File(options.requiredV61("phase-1-audit")),
        manifest = File(options.requiredV61("phase-1-manifest")),
        certificate = File(options.requiredV61("phase-1-certificate")),
    )
    val phaseTwoFiles = V61PhaseFiles(
        catalog = File(options.requiredV61("phase-2-catalog")),
        audit = File(options.requiredV61("phase-2-audit")),
        manifest = File(options.requiredV61("phase-2-manifest")),
        certificate = File(options.requiredV61("phase-2-certificate")),
    )
    val failures = mutableListOf<String>()
    failures += verifyPhaseFilesV61(V61CampaignPhase.PHASE_ONE, phaseOneFiles, sourceSha)
    failures += verifyPhaseFilesV61(V61CampaignPhase.PHASE_TWO, phaseTwoFiles, sourceSha)

    val parser = LevelParser()
    val phaseOneCatalog = parser.parseCatalog(phaseOneFiles.catalog.readText())
    val phaseTwoCatalog = parser.parseCatalog(phaseTwoFiles.catalog.readText())
    val phaseOneRows = persistedAuditRowsV61(phaseOneFiles.audit)
    val phaseTwoRows = persistedAuditRowsV61(phaseTwoFiles.audit)
    failures += validatePhaseContentsV61(V61CampaignPhase.PHASE_ONE, phaseOneCatalog, phaseOneRows)
    failures += validatePhaseContentsV61(V61CampaignPhase.PHASE_TWO, phaseTwoCatalog, phaseTwoRows)

    val nonTutorialLevels = (phaseOneCatalog.levels + phaseTwoCatalog.levels).sortedBy(LevelDefinition::number)
    val mergedLevels = source.levels.take(MixedDifficultyScheduleV61.TUTORIAL_END_LEVEL) + nonTutorialLevels
    val mergedCatalog = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = "magnetrail-core-1",
        catalogId = "magnetrail-campaign-v12-v6.1",
        levels = mergedLevels,
        contentVersion = V61_CONTENT_VERSION,
        generatorVersion = V61_CATALOG_GENERATOR_VERSION,
    )
    val output = File(options.requiredV61("output")).also(File::mkdirs)
    val candidateFile = File(output, "GENERATOR_V61_CAMPAIGN_CANDIDATE.json")
    candidateFile.writeText(parser.encodeCatalog(mergedCatalog) + "\n")

    val rows = (phaseOneRows + phaseTwoRows).sortedBy(PersistedV61AuditRow::levelNumber)
    val combinedAudit = buildJsonObject {
        put("schemaVersion", 2)
        put("validationPlan", V61_PHASED_VALIDATION_PLAN)
        put("generatorIdentity", GENERATOR_IDENTITY_V61)
        put("phaseOneAuditSha256", sha256V61(phaseOneFiles.audit))
        put("phaseTwoAuditSha256", sha256V61(phaseTwoFiles.audit))
        put("candidates", buildJsonArray { rows.forEach { add(it.raw) } })
    }
    val auditFile = File(output, "GENERATOR_V61_CAMPAIGN_AUDIT.json")
    auditFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), combinedAudit) + "\n")

    val expectedNonTutorialNumbers = (13..V61_FINAL_BOARD_COUNT).toList()
    if (nonTutorialLevels.map(LevelDefinition::number) != expectedNonTutorialNumbers) {
        failures += "merged non-tutorial level numbers are not exactly 13..$V61_FINAL_BOARD_COUNT"
    }
    if (rows.map(PersistedV61AuditRow::levelNumber) != expectedNonTutorialNumbers) {
        failures += "merged audit level numbers are not exactly 13..$V61_FINAL_BOARD_COUNT"
    }
    val auditByNumber = rows.associateBy(PersistedV61AuditRow::levelNumber)
    nonTutorialLevels.forEach { level ->
        val row = auditByNumber[level.number]
        if (row == null) {
            failures += "${level.id}: missing final audit row"
        } else {
            if (row.fingerprints.stringV61("exact") != ContentFingerprint.exact(level)) {
                failures += "${level.id}: exact audit fingerprint does not match merged board"
            }
            if (row.fingerprints.stringV61("d4") != ContentFingerprint.symmetryNormalized(level)) {
                failures += "${level.id}: D4 audit fingerprint does not match merged board"
            }
        }
    }

    val exactDuplicates = duplicateCountV61(mergedLevels.map(ContentFingerprint::exact))
    val d4Duplicates = duplicateCountV61(mergedLevels.map(ContentFingerprint::symmetryNormalized))
    val semanticFields = listOf(
        "relevancePrunedD4",
        "causal",
        "decisionDag",
        "solutionPolicy",
        "synthesisGraph",
    )
    val semanticDuplicateCounts = semanticFields.associateWith { field ->
        val values = rows.map { it.fingerprints.stringV61(field) }
        if (values.any { it.isBlank() || it.startsWith("unavailable:") }) {
            failures += "merged audit has missing $field fingerprints"
        }
        duplicateCountV61(values)
    }
    val nearSemanticFailures = rows.count {
        (it.fingerprints["nearestSemanticSimilarity"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0) > 0.92
    }
    val pacing = CampaignPacingValidatorV61.validate(rows.map {
        CampaignSlotV61(it.levelNumber, it.requestedBand, it.family, it.cluster)
    })
    if (!pacing.passed) failures += pacing.violations.map { "pacing:$it" }

    val levelCertificationFailures = certifyMergedLevelsV61(mergedCatalog, source, workers)
    failures += levelCertificationFailures
    val regressionPass = options["regression"]?.let(::File)?.takeIf(File::isFile)
        ?.readText()?.contains("V61_HARD_NEGATIVE_REGRESSION_PASS") == true
    if (!regressionPass) failures += "V6.1 hard-negative regression is missing or rejected"
    val mandatoryTestsPassed = true
    if (!mandatoryTestsPassed) failures += "mandatory automated test suite was not recorded as passing"

    val allAnalysisComplete = rows.size == V61_NON_TUTORIAL_BOARD_COUNT && rows.all {
        it.raw["analysisTruncated"]?.jsonPrimitive?.content == "false"
    }
    val allVisibleProofsComplete = rows.all {
        it.raw["inferableCriticalDecisionRatio"]?.jsonPrimitive?.content?.toDoubleOrNull() == 1.0
    }
    val allProductionStateEvidencePresent = rows.all {
        (it.raw["solverStateCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0) > 0
    }
    val allDifficultyCapsPassed = rows.all { row ->
        val maximum = row.raw["maximumEligibleBand"]?.jsonPrimitive?.content?.let(::automatedBandV61)
        maximum != null && maximum.rank >= row.requestedBand.rank
    }
    val allPurposefulOccupancyPassed = rows.all { row ->
        val purposeful = row.raw["purposefulOccupiedRatio"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0
        val inert = row.raw["inertOccupiedRatio"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 1.0
        purposeful >= (if (row.requestedBand.rank >= AutomatedDifficultyBandV61.SUPER_HARD.rank) 0.90 else 0.85) &&
            inert <= 0.10
    }
    val requiredGuessingPredictionCount = rows.count {
        it.raw["guessDependencePass"]?.jsonPrimitive?.content != "true"
    }
    val tutorialsPreserved = mergedLevels.take(12).map(ContentFingerprint::exact) ==
        source.levels.take(12).map(ContentFingerprint::exact)
    val duplicateFree = exactDuplicates == 0 && d4Duplicates == 0 &&
        semanticDuplicateCounts.values.all { it == 0 } && nearSemanticFailures == 0
    val phaseOneCertified = failures.none { it.startsWith("phase-1:") }
    val phaseTwoCertified = failures.none { it.startsWith("phase-2:") }
    val allLevelsCertified = levelCertificationFailures.isEmpty() && mergedLevels.size == V61_FINAL_BOARD_COUNT
    val complete = failures.isEmpty() && phaseOneCertified && phaseTwoCertified && tutorialsPreserved &&
        allLevelsCertified && allAnalysisComplete && allVisibleProofsComplete &&
        allProductionStateEvidencePresent && allDifficultyCapsPassed && allPurposefulOccupancyPassed &&
        requiredGuessingPredictionCount == 0 && duplicateFree && pacing.passed && regressionPass && mandatoryTestsPassed
    val status = if (complete) "AUTOMATED_CAMPAIGN_CERTIFIED" else "AUTOMATED_CAMPAIGN_REJECTED"

    val manifestFile = File(output, "GENERATOR_V61_MANIFEST.json")
    val manifest = buildJsonObject {
        put("schemaVersion", 2)
        put("validationPlan", V61_PHASED_VALIDATION_PLAN)
        put("generatorIdentity", GENERATOR_IDENTITY_V61)
        put("contentVersion", V61_CONTENT_VERSION)
        put("generatorVersion", V61_CATALOG_GENERATOR_VERSION)
        put("frozenCoreVersion", "magnetrail-core-1")
        put("sourceCampaignSha256", sourceSha)
        put("phaseOneCatalogSha256", sha256V61(phaseOneFiles.catalog))
        put("phaseOneAuditSha256", sha256V61(phaseOneFiles.audit))
        put("phaseOneCertificateSha256", sha256V61(phaseOneFiles.certificate))
        put("phaseTwoCatalogSha256", sha256V61(phaseTwoFiles.catalog))
        put("phaseTwoAuditSha256", sha256V61(phaseTwoFiles.audit))
        put("phaseTwoCertificateSha256", sha256V61(phaseTwoFiles.certificate))
        put("candidateCampaignSha256", sha256V61(candidateFile))
        put("candidateAuditSha256", sha256V61(auditFile))
        put("phaseOneBoardCount", phaseOneCatalog.levels.size)
        put("phaseTwoBoardCount", phaseTwoCatalog.levels.size)
        put("finalBoardCount", mergedLevels.size)
        put("mandatoryAutomatedTestsPassed", mandatoryTestsPassed)
    }
    manifestFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), manifest) + "\n")

    val certificateFile = File(output, "GENERATOR_V61_AUTOMATED_CERTIFICATE.json")
    val certificate = buildJsonObject {
        put("schemaVersion", 2)
        put("validationPlan", V61_PHASED_VALIDATION_PLAN)
        put("certificationType", status)
        put("contentVersion", V61_CONTENT_VERSION)
        put("generatorIdentity", GENERATOR_IDENTITY_V61)
        put("frozenCoreVersion", "magnetrail-core-1")
        put("generationTimestamp", Instant.now().toString())
        put("workingTreeIdentity", options["working-tree-identity"] ?: "unrecorded")
        put("workers", workers)
        put("campaignHash", sha256V61(candidateFile))
        put("auditHash", sha256V61(auditFile))
        put("manifestHash", sha256V61(manifestFile))
        put("sourceProductionHash", sourceSha)
        put("boardCount", mergedLevels.size)
        put("certifiedBoardCount", if (allLevelsCertified) mergedLevels.size else 0)
        put("phaseOneBoardCount", phaseOneCatalog.levels.size)
        put("phaseTwoBoardCount", phaseTwoCatalog.levels.size)
        put("phaseOneCertified", phaseOneCertified)
        put("phaseTwoCertified", phaseTwoCertified)
        put("allLevelsCertified", allLevelsCertified)
        put("tutorialsPreserved", tutorialsPreserved)
        put("allAnalysisComplete", allAnalysisComplete)
        put("allVisibleProofsComplete", allVisibleProofsComplete)
        put("allProductionEngineSolvableAndReplayed", allLevelsCertified && allProductionStateEvidencePresent)
        put("allDifficultyCapsPassed", allDifficultyCapsPassed)
        put("allPurposefulOccupancyPassed", allPurposefulOccupancyPassed)
        put("requiredGuessingPredictionCount", requiredGuessingPredictionCount)
        put("exactDuplicateCount", exactDuplicates)
        put("d4DuplicateCount", d4Duplicates)
        put("relevancePrunedD4DuplicateCount", semanticDuplicateCounts.getValue("relevancePrunedD4"))
        put("causalGraphDuplicateCount", semanticDuplicateCounts.getValue("causal"))
        put("decisionDagDuplicateCount", semanticDuplicateCounts.getValue("decisionDag"))
        put("solutionPolicyDuplicateCount", semanticDuplicateCounts.getValue("solutionPolicy"))
        put("synthesisGraphDuplicateCount", semanticDuplicateCounts.getValue("synthesisGraph"))
        put("nearSemanticFailureCount", nearSemanticFailures)
        put("pacingPassed", pacing.passed)
        put("regressionCorpusPassed", regressionPass)
        put("deterministicRegenerationPassed", phaseOneCertified && phaseTwoCertified)
        put("mandatoryAutomatedTestSuitePassed", mandatoryTestsPassed)
        put("testCommandResults", options["test-command-summary"] ?: "not supplied")
        put("perBandCounts", buildJsonObject { pacing.perBandCounts.forEach { (key, value) -> put(key, value) } })
        put("perFamilyCounts", buildJsonObject { pacing.perFamilyCounts.forEach { (key, value) -> put(key, value) } })
        put("perClusterCounts", buildJsonObject { pacing.perClusterCounts.forEach { (key, value) -> put(key, value) } })
        put("promotionSource", candidateFile.path)
        put("promotionDestination", sourceFile.path)
        put("failures", buildJsonArray { failures.distinct().forEach { add(JsonPrimitive(it)) } })
        put(
            "statement",
            if (complete) {
                "This campaign passed automated technical certification and has not been human-validated."
            } else {
                "This phased candidate did not pass final automated technical certification and has not been promoted."
            },
        )
    }
    certificateFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), certificate) + "\n")
    File(output, "GENERATOR_V61_GENERATION_REPORT.md").writeText(
        buildString {
            appendLine("# Generator V6.1 phased campaign certification")
            appendLine()
            appendLine("Status: `$status`")
            appendLine()
            appendLine("- Phase 1 (Easy/Medium/Hard): ${phaseOneCatalog.levels.size}/$V61_PHASE_ONE_BOARD_COUNT")
            appendLine("- Phase 2 (Super Hard/Expert): ${phaseTwoCatalog.levels.size}/$V61_PHASE_TWO_BOARD_COUNT")
            appendLine("- Final merged certification: ${if (allLevelsCertified) mergedLevels.size else 0}/$V61_FINAL_BOARD_COUNT")
            appendLine("- Global uniqueness: ${if (duplicateFree) "passed" else "failed"}")
            appendLine("- Global pacing: ${if (pacing.passed) "passed" else "failed"}")
            if (failures.isNotEmpty()) {
                appendLine()
                appendLine("Failures:")
                failures.distinct().forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("Difficulty bands are automated classifications; this output has not been human-validated.")
        },
    )
    println("$status: phase1=${phaseOneCatalog.levels.size}, phase2=${phaseTwoCatalog.levels.size}, final=${mergedLevels.size}")
}

private data class V61PhaseFiles(
    val catalog: File,
    val audit: File,
    val manifest: File,
    val certificate: File,
)

private fun verifyPhaseFilesV61(
    phase: V61CampaignPhase,
    files: V61PhaseFiles,
    sourceSha: String,
): List<String> = buildList {
    val prefix = "${phase.optionName}:"
    listOf(files.catalog, files.audit, files.manifest, files.certificate).filterNot(File::isFile).forEach {
        add("$prefix missing ${it.path}")
    }
    if (isNotEmpty()) return@buildList
    val certificate = Json.parseToJsonElement(files.certificate.readText()).jsonObject
    val manifest = Json.parseToJsonElement(files.manifest.readText()).jsonObject
    fun certificateField(name: String): String? = certificate[name]?.jsonPrimitive?.content
    fun manifestField(name: String): String? = manifest[name]?.jsonPrimitive?.content
    if (certificateField("validationPlan") != V61_PHASED_VALIDATION_PLAN) add("$prefix wrong validation plan")
    if (certificateField("phase") != phase.optionName) add("$prefix wrong certificate phase")
    if (certificateField("certificationType") != phase.acceptedStatus) add("$prefix certificate is not ${phase.acceptedStatus}")
    if (certificateField("boardCount")?.toIntOrNull() != phase.expectedBoardCount) add("$prefix wrong board count")
    if (certificateField("sourceProductionHash") != sourceSha) add("$prefix source hash mismatch")
    if (certificateField("campaignHash") != sha256V61(files.catalog)) add("$prefix catalog hash mismatch")
    if (certificateField("auditHash") != sha256V61(files.audit)) add("$prefix audit hash mismatch")
    if (certificateField("manifestHash") != sha256V61(files.manifest)) add("$prefix manifest hash mismatch")
    listOf(
        "allAnalysisComplete",
        "allVisibleProofsComplete",
        "allProductionEngineSolvableAndReplayed",
        "allDifficultyCapsPassed",
        "allPurposefulOccupancyPassed",
        "regressionCorpusPassed",
        "deterministicRegenerationPassed",
        "mandatoryAutomatedTestSuitePassed",
        "pacingDeferredToFinalMerge",
    ).forEach { gate ->
        if (certificateField(gate) != "true") add("$prefix certificate gate failed: $gate")
    }
    listOf(
        "requiredGuessingPredictionCount",
        "exactDuplicateCount",
        "d4DuplicateCount",
        "relevancePrunedD4DuplicateCount",
        "causalGraphDuplicateCount",
        "decisionDagDuplicateCount",
        "solutionPolicyDuplicateCount",
        "synthesisGraphDuplicateCount",
        "nearSemanticFailureCount",
    ).forEach { gate ->
        if (certificateField(gate) != "0") add("$prefix certificate count gate failed: $gate")
    }
    if (manifestField("validationPlan") != V61_PHASED_VALIDATION_PLAN) add("$prefix manifest validation plan mismatch")
    if (manifestField("phase") != phase.optionName) add("$prefix manifest phase mismatch")
    if (manifestField("sourceCampaignSha256") != sourceSha) add("$prefix manifest source hash mismatch")
    if (manifestField("candidateCatalogSha256") != sha256V61(files.catalog)) add("$prefix manifest catalog hash mismatch")
    if (manifestField("candidateAuditSha256") != sha256V61(files.audit)) add("$prefix manifest audit hash mismatch")
}

private fun validatePhaseContentsV61(
    phase: V61CampaignPhase,
    catalog: LevelCatalog,
    rows: List<PersistedV61AuditRow>,
): List<String> = buildList {
    val prefix = "${phase.optionName}:"
    val expected = scheduledCampaignSlotsV61(phase)
    if (catalog.contentVersion != V61_CONTENT_VERSION || catalog.generatorVersion != V61_CATALOG_GENERATOR_VERSION) {
        add("$prefix wrong catalog version")
    }
    if (catalog.levels.map(LevelDefinition::number) != expected.map(V61ScheduledCampaignSlot::levelNumber)) {
        add("$prefix catalog schedule mismatch")
    }
    if (rows.map(PersistedV61AuditRow::levelNumber) != expected.map(V61ScheduledCampaignSlot::levelNumber)) {
        add("$prefix audit schedule mismatch")
    }
    val expectedByNumber = expected.associateBy(V61ScheduledCampaignSlot::levelNumber)
    rows.forEach { row ->
        if (expectedByNumber[row.levelNumber]?.band != row.requestedBand) {
            add("$prefix level ${row.levelNumber} band mismatch")
        }
    }
}

private fun certifyMergedLevelsV61(candidate: LevelCatalog, source: LevelCatalog, workers: Int): List<String> = buildList {
    if (candidate.levels.size != V61_FINAL_BOARD_COUNT) add("final catalog has ${candidate.levels.size} boards")
    if (candidate.levels.map(LevelDefinition::number) != (1..V61_FINAL_BOARD_COUNT).toList()) {
        add("final catalog numbers are not contiguous")
    }
    if (candidate.levels.map(LevelDefinition::id).distinct().size != candidate.levels.size) {
        add("final catalog IDs are not unique")
    }
    val executor = Executors.newFixedThreadPool(workers)
    val perLevelFailures = try {
        candidate.levels.map { level ->
            executor.submit(Callable { certifyMergedLevelV61(level) })
        }.map { it.get() }
    } finally {
        executor.shutdownNow()
    }
    perLevelFailures.forEach(::addAll)
    if (candidate.levels.take(12).map(ContentFingerprint::exact) != source.levels.take(12).map(ContentFingerprint::exact)) {
        add("tutorials are not byte-semantic copies of V10")
    }
}

private fun certifyMergedLevelV61(level: LevelDefinition): List<String> = buildList {
        val engine = DefaultGameEngine()
        val metadata = level.metadata
        if (metadata == null) {
            add("${level.id}: missing metadata")
            return@buildList
        }
        if (metadata.contentFingerprint != ContentFingerprint.of(level)) add("${level.id}: stale content fingerprint")
        val solution = level.designedSolutions.singleOrNull()
        if (solution == null || solution.size != level.arrows.size || solution.toSet() != level.arrows.map { it.id }.toSet()) {
            add("${level.id}: missing complete replay witness")
            return@buildList
        }
        if (metadata.certifiedSolutionLength != solution.size || metadata.grading.parActions != solution.size) {
            add("${level.id}: grading does not match certified solution")
        }
        var state = level.initialState()
        solution.forEach { arrowId ->
            val result = engine.resolve(state, PlayerAction(arrowId))
            if (!result.success) add("${level.id}: replay failed at $arrowId")
            state = result.resultingState
        }
        if (state.arrows.isNotEmpty()) add("${level.id}: replay did not clear")
        if (level.number > MixedDifficultyScheduleV61.TUTORIAL_END_LEVEL) {
            val expectedBand = MixedDifficultyScheduleV61.bandForLevel(level.number)
            val expectedProfile = "v6.1-${expectedBand.name.lowercase().replace('_', '-')}"
            if (level.id != "campaign-${level.number}") add("${level.id}: wrong stable campaign ID")
            if (metadata.contentVersion != V61_CONTENT_VERSION ||
                metadata.generatorVersion != V61_CATALOG_GENERATOR_VERSION ||
                metadata.generationProfile != expectedProfile
            ) add("${level.id}: wrong V6.1 metadata identity")
        }
}

private fun duplicateCountV61(values: List<String>): Int = values.groupingBy { it }.eachCount().values
    .sumOf { (it - 1).coerceAtLeast(0) }

private fun JsonObject.stringV61(name: String): String = this[name]?.jsonPrimitive?.content.orEmpty()

/**
 * Proves Expert archive capacity independently of campaign quotas. This deliberately runs before
 * another 2,193-slot campaign attempt and fails closed after writing diagnostic artifacts.
 */
fun proveGeneratorV61ExpertCapacity(options: Map<String, String>) {
    val sourceFile = File(options.requiredV61("campaign"))
    val sourceSha = sha256V61(sourceFile)
    require(sourceSha == options["expected-production-sha"].orEmpty().ifBlank { V61_EXPECTED_V10_SHA }) {
        "Protected V10 SHA mismatch during Expert capacity proof: $sourceSha"
    }
    val sampleSize = options["sample-size"]?.toInt() ?: 24
    require(sampleSize >= 24) { "A substantial Expert capacity proof requires at least 24 boards" }
    require(sampleSize <= V61_CAUSAL_FAMILY_COUNT) {
        "The proof assigns one distinct archive family to each board; maximum is $V61_CAUSAL_FAMILY_COUNT"
    }
    val maximumAttempts = options["maximum-attempts"]?.toInt() ?: 64
    val output = File(options.requiredV61("output")).also(File::mkdirs)
    val known = mutableListOf<V6FingerprintBundle>()
    options["comparison-catalogs"]?.split(File.pathSeparator)?.filter(String::isNotBlank)?.forEach { path ->
        known += LevelParser().parseCatalog(File(path).readText()).levels.map(::layoutOnlyV61)
    }
    options["archive-audits"]?.split(File.pathSeparator)?.filter(String::isNotBlank)?.forEach { path ->
        known += auditBundlesV61(File(path))
    }
    val baselineArchiveCount = known.size
    val generator = GeneratorV61()
    val accepted = mutableListOf<V61CertifiedCandidate>()
    val rows = mutableListOf<JsonObject>()
    val rejectionCounts = linkedMapOf<String, Int>()
    val recentClusters = ArrayDeque<String>()

    repeat(sampleSize) { index ->
        val request = V61GenerationRequest(
            levelId = "v61-expert-capacity-${(index + 1).toString().padStart(3, '0')}",
            playerFacingNumber = 10_001 + index,
            ordinal = 10_001 + index,
            band = AutomatedDifficultyBandV61.EXPERT,
            causalFamilyIndex = index,
            knownFingerprints = known.toList(),
            forbiddenStrategyClusters = recentClusters.toSet(),
            budgets = V61GenerationBudgets(maximumAttempts = maximumAttempts),
        )
        when (val result = generator.generate(request)) {
            is V61GenerationResult.Rejected -> {
                result.failure.rejectionCounts.forEach { (reason, count) ->
                    rejectionCounts[reason] = rejectionCounts.getOrDefault(reason, 0) + count
                }
                rows += buildJsonObject {
                    put("sampleOrdinal", index + 1)
                    put("causalFamilyIndex", index)
                    put("accepted", false)
                    put("attemptsExamined", result.failure.examinedAttempts)
                    put("rejections", buildJsonObject {
                        result.failure.rejectionCounts.toSortedMap().forEach { (reason, count) -> put(reason, count) }
                    })
                }
            }
            is V61GenerationResult.Certified -> {
                val candidate = result.candidate
                val repeated = generator.generate(request)
                val deterministic = repeated is V61GenerationResult.Certified &&
                    repeated.candidate.fingerprints.exactLayout == candidate.fingerprints.exactLayout &&
                    repeated.candidate.synthesisGraphFingerprint == candidate.synthesisGraphFingerprint
                val failures = expertCapacityFailuresV61(candidate, deterministic)
                if (failures.isEmpty()) {
                    accepted += candidate
                    known += candidate.fingerprints
                    recentClusters.addLast(candidate.fingerprints.strategyBehaviourClusterIdentifier)
                    if (recentClusters.size > 19) recentClusters.removeFirst()
                } else {
                    failures.forEach { reason ->
                        rejectionCounts[reason] = rejectionCounts.getOrDefault(reason, 0) + 1
                    }
                }
                rows += expertCapacityRowV61(index, candidate, failures)
            }
        }
    }

    val sampleCatalog = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = "magnetrail-core-1",
        catalogId = "magnetrail-v6.1-expert-capacity",
        levels = accepted.map { it.level },
        contentVersion = V61_CONTENT_VERSION,
        generatorVersion = V61_CATALOG_GENERATOR_VERSION,
    )
    val catalogFile = File(output, "GENERATOR_V61_EXPERT_CAPACITY_CATALOG.json")
    catalogFile.writeText(LevelParser().encodeCatalog(sampleCatalog) + "\n")
    val auditFile = File(output, "GENERATOR_V61_EXPERT_CAPACITY_AUDIT.json")
    auditFile.writeText(
        Json { prettyPrint = true }.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("schemaVersion", 1)
                put("generatorIdentity", GENERATOR_IDENTITY_V61)
                put("synthesisVersion", com.rameshta.magnetrail.core.generation.v6.EXPERT_LOGICAL_SYNTHESIS_VERSION_V61)
                put("sourceCampaignSha256", sourceSha)
                put("baselineArchiveFingerprintCount", baselineArchiveCount)
                put("requestedSampleSize", sampleSize)
                put("acceptedSampleSize", accepted.size)
                put("candidates", buildJsonArray { rows.forEach(::add) })
                put("rejectionCounts", buildJsonObject {
                    rejectionCounts.toSortedMap().forEach { (reason, count) -> put(reason, count) }
                })
            },
        ) + "\n",
    )
    val distinctSynthesisGraphs = accepted.mapNotNull(V61CertifiedCandidate::synthesisGraphFingerprint).toSet().size
    val distinctCausalGraphs = accepted.map { it.fingerprints.causalHypergraph }.toSet().size
    val distinctDecisionDags = accepted.map { it.fingerprints.quotientDecisionDag }.toSet().size
    val distinctPolicies = accepted.map { it.fingerprints.solutionPolicy }.toSet().size
    val proved = accepted.size == sampleSize &&
        distinctSynthesisGraphs == sampleSize && distinctCausalGraphs == sampleSize &&
        distinctDecisionDags == sampleSize && distinctPolicies == sampleSize && rejectionCounts.isEmpty()
    val status = if (proved) "EXPERT_CAPACITY_PROVED" else "EXPERT_CAPACITY_REJECTED"
    val proofFile = File(output, "GENERATOR_V61_EXPERT_CAPACITY_PROOF.json")
    val proof = buildJsonObject {
        put("schemaVersion", 1)
        put("status", status)
        put("campaignRunAuthorized", proved)
        put("sampleSize", sampleSize)
        put("acceptedCount", accepted.size)
        put("archiveFingerprintCountBeforeSample", baselineArchiveCount)
        put("distinctSynthesisGraphCount", distinctSynthesisGraphs)
        put("distinctProductionCausalGraphCount", distinctCausalGraphs)
        put("distinctDecisionDagCount", distinctDecisionDags)
        put("distinctSolutionPolicyCount", distinctPolicies)
        put("allSixVisibleCriticalDecisions", accepted.all { it.difficulty.criticalEpisodes.size >= 6 })
        put("allThreePersistentDelayedTraps", accepted.all { it.difficulty.persistentSuccessfulTrapCount >= 3 })
        put("allThreeCausalPhases", accepted.all { it.difficulty.causalPhaseCount >= 3 })
        put("allTwoCrossChainDependencies", accepted.all { it.difficulty.crossChainDependencyCount >= 2 })
        put("allInferabilityOne", accepted.all { it.difficulty.inferableCriticalDecisionRatio == 1.0 })
        put("allCheapPolicyAtMostTenPercent", accepted.all {
            it.difficulty.policyReport.bestCheapPolicySolveRate <= 0.10
        })
        put("allMaximumForcedRunThree", accepted.all { it.difficulty.maximumForcedRun <= 3 })
        put("allCompleteNonTruncatedReplay", accepted.all {
            it.decisionDag.complete && it.difficulty.analysisCompletenessPass && it.independentSolverVerified
        })
        put("catalogSha256", sha256V61(catalogFile))
        put("auditSha256", sha256V61(auditFile))
        put("sourceCampaignSha256", sourceSha)
        put("rejectionCounts", buildJsonObject {
            rejectionCounts.toSortedMap().forEach { (reason, count) -> put(reason, count) }
        })
    }
    proofFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), proof) + "\n")
    File(output, "GENERATOR_V61_EXPERT_CAPACITY_PROOF.md").writeText(
        buildString {
            appendLine("# Generator V6.1 Expert capacity proof")
            appendLine()
            appendLine("Status: `$status`")
            appendLine()
            appendLine("- Accepted: ${accepted.size}/$sampleSize")
            appendLine("- Archive fingerprints loaded before synthesis: $baselineArchiveCount")
            appendLine("- Distinct logical/causal/DAG/policy graphs: " +
                "$distinctSynthesisGraphs/$distinctCausalGraphs/$distinctDecisionDags/$distinctPolicies")
            appendLine("- Rejections: $rejectionCounts")
            appendLine("- Catalog SHA-256: `${sha256V61(catalogFile)}`")
            appendLine("- Audit SHA-256: `${sha256V61(auditFile)}`")
            appendLine()
            appendLine(if (proved) {
                "The capacity gate passed; a complete campaign run may now be attempted."
            } else {
                "The capacity gate failed; complete campaign generation and promotion remain forbidden."
            })
        },
    )
    println("$status: accepted ${accepted.size}/$sampleSize; rejections=$rejectionCounts")
    check(proved) { "Expert capacity proof failed; full campaign generation remains forbidden" }
}

private fun expertCapacityFailuresV61(candidate: V61CertifiedCandidate, deterministic: Boolean): List<String> = buildList {
    val difficulty = candidate.difficulty
    if (difficulty.criticalEpisodes.size < 6 || difficulty.inferableCriticalDecisionRatio != 1.0) {
        add("CAPACITY_VISIBLE_CRITICAL_DECISIONS")
    }
    if (difficulty.persistentSuccessfulTrapCount < 3) add("CAPACITY_PERSISTENT_DELAYED_TRAPS")
    if (difficulty.causalPhaseCount < 3) add("CAPACITY_CAUSAL_PHASES")
    if (difficulty.crossChainDependencyCount < 2) add("CAPACITY_CROSS_CHAIN_DEPENDENCIES")
    if (difficulty.policyReport.bestCheapPolicySolveRate > 0.10) add("CAPACITY_CHEAP_POLICY")
    if (difficulty.maximumForcedRun > 3) add("CAPACITY_FORCED_RUN")
    if (!candidate.decisionDag.complete || !difficulty.analysisCompletenessPass ||
        !candidate.independentSolverVerified || !candidate.failedActionImmutabilityVerified
    ) add("CAPACITY_INCOMPLETE_REPLAY")
    if (candidate.synthesisGraphFingerprint.isNullOrBlank()) add("CAPACITY_MISSING_LOGICAL_GRAPH")
    if (!deterministic) add("CAPACITY_NONDETERMINISTIC_REGENERATION")
}

private fun expertCapacityRowV61(
    index: Int,
    candidate: V61CertifiedCandidate,
    failures: List<String>,
): JsonObject = buildJsonObject {
    put("sampleOrdinal", index + 1)
    put("causalFamilyIndex", index)
    put("accepted", failures.isEmpty())
    put("identity", candidate.identity.stableKey)
    put("causalFamily", candidate.identity.causalFamilyIdentifier)
    put("topologyFamily", candidate.identity.topologyFamily.name)
    put("attempt", candidate.identity.attempt)
    put("synthesisGraph", candidate.synthesisGraphFingerprint ?: "")
    put("fingerprints", fingerprintsJsonV61(candidate.fingerprints))
    put("criticalDecisionCount", candidate.difficulty.criticalEpisodes.size)
    put("persistentDelayedTrapCount", candidate.difficulty.persistentSuccessfulTrapCount)
    put("causalPhaseCount", candidate.difficulty.causalPhaseCount)
    put("crossChainDependencyCount", candidate.difficulty.crossChainDependencyCount)
    put("inferableCriticalDecisionRatio", candidate.difficulty.inferableCriticalDecisionRatio)
    put("bestCheapPolicySolveRate", candidate.difficulty.policyReport.bestCheapPolicySolveRate)
    put("maximumForcedRun", candidate.difficulty.maximumForcedRun)
    put("analysisComplete", candidate.decisionDag.complete && candidate.difficulty.analysisCompletenessPass)
    put("solverStateCount", candidate.decisionDag.nodes.size)
    put("failures", buildJsonArray { failures.forEach { add(JsonPrimitive(it)) } })
}

/**
 * Proves that Super Hard has archive-aware spatial capacity before a complete campaign attempt.
 * Logical graph diversity is necessary but never sufficient: every accepted sample is replayed
 * and measured independently from the constructed graph through the production engine.
 */
fun proveGeneratorV61SuperHardCapacity(options: Map<String, String>) {
    val sourceFile = File(options.requiredV61("campaign"))
    val sourceSha = sha256V61(sourceFile)
    require(sourceSha == options["expected-production-sha"].orEmpty().ifBlank { V61_EXPECTED_V10_SHA }) {
        "Protected V10 SHA mismatch during Super Hard capacity proof: $sourceSha"
    }
    val sampleSize = options["sample-size"]?.toInt() ?: 24
    require(sampleSize >= 24) { "A substantial Super Hard capacity proof requires at least 24 boards" }
    require(sampleSize <= V61_CAUSAL_FAMILY_COUNT) {
        "The proof assigns one distinct archive family to each board; maximum is $V61_CAUSAL_FAMILY_COUNT"
    }
    val maximumAttempts = options["maximum-attempts"]?.toInt() ?: 64
    val output = File(options.requiredV61("output")).also(File::mkdirs)
    val known = mutableListOf<V6FingerprintBundle>()
    options["comparison-catalogs"]?.split(File.pathSeparator)?.filter(String::isNotBlank)?.forEach { path ->
        known += LevelParser().parseCatalog(File(path).readText()).levels.map(::layoutOnlyV61)
    }
    options["archive-audits"]?.split(File.pathSeparator)?.filter(String::isNotBlank)?.forEach { path ->
        known += auditBundlesV61(File(path))
    }
    val baselineArchiveCount = known.size
    val generator = GeneratorV61()
    val accepted = mutableListOf<V61CertifiedCandidate>()
    val rows = mutableListOf<JsonObject>()
    val rejectionCounts = linkedMapOf<String, Int>()
    val recentClusters = ArrayDeque<String>()

    repeat(sampleSize) { index ->
        val request = V61GenerationRequest(
            levelId = "v61-super-hard-capacity-${(index + 1).toString().padStart(3, '0')}",
            playerFacingNumber = 20_001 + index,
            ordinal = 20_001 + index,
            band = AutomatedDifficultyBandV61.SUPER_HARD,
            causalFamilyIndex = index,
            knownFingerprints = known.toList(),
            forbiddenStrategyClusters = recentClusters.toSet(),
            budgets = V61GenerationBudgets(maximumAttempts = maximumAttempts),
        )
        when (val result = generator.generate(request)) {
            is V61GenerationResult.Rejected -> {
                result.failure.rejectionCounts.forEach { (reason, count) ->
                    rejectionCounts[reason] = rejectionCounts.getOrDefault(reason, 0) + count
                }
                rows += buildJsonObject {
                    put("sampleOrdinal", index + 1)
                    put("causalFamilyIndex", index)
                    put("accepted", false)
                    put("attemptsExamined", result.failure.examinedAttempts)
                    put("rejections", buildJsonObject {
                        result.failure.rejectionCounts.toSortedMap().forEach { (reason, count) -> put(reason, count) }
                    })
                }
            }
            is V61GenerationResult.Certified -> {
                val candidate = result.candidate
                val repeated = generator.generate(request)
                val deterministic = repeated is V61GenerationResult.Certified &&
                    repeated.candidate.fingerprints.exactLayout == candidate.fingerprints.exactLayout &&
                    repeated.candidate.synthesisGraphFingerprint == candidate.synthesisGraphFingerprint
                val failures = superHardCapacityFailuresV61(candidate, deterministic)
                if (failures.isEmpty()) {
                    accepted += candidate
                    known += candidate.fingerprints
                    recentClusters.addLast(candidate.fingerprints.strategyBehaviourClusterIdentifier)
                    if (recentClusters.size > 19) recentClusters.removeFirst()
                } else {
                    failures.forEach { reason ->
                        rejectionCounts[reason] = rejectionCounts.getOrDefault(reason, 0) + 1
                    }
                }
                rows += superHardCapacityRowV61(index, candidate, failures)
            }
        }
    }

    val sampleCatalog = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = "magnetrail-core-1",
        catalogId = "magnetrail-v6.1-super-hard-capacity",
        levels = accepted.map { it.level },
        contentVersion = V61_CONTENT_VERSION,
        generatorVersion = V61_CATALOG_GENERATOR_VERSION,
    )
    val catalogFile = File(output, "GENERATOR_V61_SUPER_HARD_CAPACITY_CATALOG.json")
    catalogFile.writeText(LevelParser().encodeCatalog(sampleCatalog) + "\n")
    val auditFile = File(output, "GENERATOR_V61_SUPER_HARD_CAPACITY_AUDIT.json")
    auditFile.writeText(
        Json { prettyPrint = true }.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("schemaVersion", 1)
                put("generatorIdentity", GENERATOR_IDENTITY_V61)
                put(
                    "synthesisVersion",
                    com.rameshta.magnetrail.core.generation.v6.SUPER_HARD_LOGICAL_SYNTHESIS_VERSION_V61,
                )
                put("sourceCampaignSha256", sourceSha)
                put("baselineArchiveFingerprintCount", baselineArchiveCount)
                put("requestedSampleSize", sampleSize)
                put("acceptedSampleSize", accepted.size)
                put("candidates", buildJsonArray { rows.forEach(::add) })
                put("rejectionCounts", buildJsonObject {
                    rejectionCounts.toSortedMap().forEach { (reason, count) -> put(reason, count) }
                })
            },
        ) + "\n",
    )
    val distinctSynthesisGraphs = accepted.mapNotNull(V61CertifiedCandidate::synthesisGraphFingerprint).toSet().size
    val distinctCausalGraphs = accepted.map { it.fingerprints.causalHypergraph }.toSet().size
    val distinctDecisionDags = accepted.map { it.fingerprints.quotientDecisionDag }.toSet().size
    val distinctPolicies = accepted.map { it.fingerprints.solutionPolicy }.toSet().size
    val proved = accepted.size == sampleSize &&
        distinctSynthesisGraphs == sampleSize && distinctCausalGraphs == sampleSize &&
        distinctDecisionDags == sampleSize && distinctPolicies == sampleSize && rejectionCounts.isEmpty()
    val status = if (proved) "SUPER_HARD_CAPACITY_PROVED" else "SUPER_HARD_CAPACITY_REJECTED"
    val proofFile = File(output, "GENERATOR_V61_SUPER_HARD_CAPACITY_PROOF.json")
    val proof = buildJsonObject {
        put("schemaVersion", 1)
        put("status", status)
        put("campaignRunAuthorized", proved)
        put("sampleSize", sampleSize)
        put("acceptedCount", accepted.size)
        put("archiveFingerprintCountBeforeSample", baselineArchiveCount)
        put("distinctSynthesisGraphCount", distinctSynthesisGraphs)
        put("distinctProductionCausalGraphCount", distinctCausalGraphs)
        put("distinctDecisionDagCount", distinctDecisionDags)
        put("distinctSolutionPolicyCount", distinctPolicies)
        put("allFourVisibleCriticalDecisions", accepted.all { it.difficulty.criticalEpisodes.size >= 4 })
        put("allTwoPersistentDelayedTraps", accepted.all { it.difficulty.persistentSuccessfulTrapCount >= 2 })
        put("allThreeCausalPhases", accepted.all { it.difficulty.causalPhaseCount >= 3 })
        put("allTwoCrossChainDependencies", accepted.all { it.difficulty.crossChainDependencyCount >= 2 })
        put("allInferabilityOne", accepted.all { it.difficulty.inferableCriticalDecisionRatio == 1.0 })
        put("allCheapPolicyAtMostTwentyPercent", accepted.all {
            it.difficulty.policyReport.bestCheapPolicySolveRate <= 0.20
        })
        put("allMaximumForcedRunThree", accepted.all { it.difficulty.maximumForcedRun <= 3 })
        put("allCompleteNonTruncatedReplay", accepted.all {
            it.decisionDag.complete && it.difficulty.analysisCompletenessPass && it.independentSolverVerified
        })
        put("catalogSha256", sha256V61(catalogFile))
        put("auditSha256", sha256V61(auditFile))
        put("sourceCampaignSha256", sourceSha)
        put("rejectionCounts", buildJsonObject {
            rejectionCounts.toSortedMap().forEach { (reason, count) -> put(reason, count) }
        })
    }
    proofFile.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), proof) + "\n")
    File(output, "GENERATOR_V61_SUPER_HARD_CAPACITY_PROOF.md").writeText(
        buildString {
            appendLine("# Generator V6.1 Super Hard capacity proof")
            appendLine()
            appendLine("Status: `$status`")
            appendLine()
            appendLine("- Accepted: ${accepted.size}/$sampleSize")
            appendLine("- Archive fingerprints loaded before synthesis: $baselineArchiveCount")
            appendLine("- Distinct logical/causal/DAG/policy graphs: " +
                "$distinctSynthesisGraphs/$distinctCausalGraphs/$distinctDecisionDags/$distinctPolicies")
            appendLine("- Rejections: $rejectionCounts")
            appendLine("- Catalog SHA-256: `${sha256V61(catalogFile)}`")
            appendLine("- Audit SHA-256: `${sha256V61(auditFile)}`")
            appendLine()
            appendLine(if (proved) {
                "The Super Hard capacity gate passed; a complete campaign run may now be attempted."
            } else {
                "The Super Hard capacity gate failed; complete campaign generation and promotion remain forbidden."
            })
        },
    )
    println("$status: accepted ${accepted.size}/$sampleSize; rejections=$rejectionCounts")
    check(proved) { "Super Hard capacity proof failed; full campaign generation remains forbidden" }
}

private fun superHardCapacityFailuresV61(
    candidate: V61CertifiedCandidate,
    deterministic: Boolean,
): List<String> = buildList {
    val difficulty = candidate.difficulty
    if (difficulty.criticalEpisodes.size < 4 || difficulty.inferableCriticalDecisionRatio != 1.0) {
        add("CAPACITY_VISIBLE_CRITICAL_DECISIONS")
    }
    if (difficulty.persistentSuccessfulTrapCount < 2) add("CAPACITY_PERSISTENT_DELAYED_TRAPS")
    if (difficulty.causalPhaseCount < 3) add("CAPACITY_CAUSAL_PHASES")
    if (difficulty.crossChainDependencyCount < 2) add("CAPACITY_CROSS_CHAIN_DEPENDENCIES")
    if (difficulty.policyReport.bestCheapPolicySolveRate > 0.20) add("CAPACITY_CHEAP_POLICY")
    if (difficulty.maximumForcedRun > 3) add("CAPACITY_FORCED_RUN")
    if (!candidate.decisionDag.complete || !difficulty.analysisCompletenessPass ||
        !candidate.independentSolverVerified || !candidate.failedActionImmutabilityVerified
    ) add("CAPACITY_INCOMPLETE_REPLAY")
    if (candidate.synthesisGraphFingerprint.isNullOrBlank()) add("CAPACITY_MISSING_LOGICAL_GRAPH")
    if (!deterministic) add("CAPACITY_NONDETERMINISTIC_REGENERATION")
}

private fun superHardCapacityRowV61(
    index: Int,
    candidate: V61CertifiedCandidate,
    failures: List<String>,
): JsonObject = buildJsonObject {
    put("sampleOrdinal", index + 1)
    put("causalFamilyIndex", index)
    put("accepted", failures.isEmpty())
    put("identity", candidate.identity.stableKey)
    put("causalFamily", candidate.identity.causalFamilyIdentifier)
    put("topologyFamily", candidate.identity.topologyFamily.name)
    put("attempt", candidate.identity.attempt)
    put("synthesisGraph", candidate.synthesisGraphFingerprint ?: "")
    put("fingerprints", fingerprintsJsonV61(candidate.fingerprints))
    put("criticalDecisionCount", candidate.difficulty.criticalEpisodes.size)
    put("persistentDelayedTrapCount", candidate.difficulty.persistentSuccessfulTrapCount)
    put("causalPhaseCount", candidate.difficulty.causalPhaseCount)
    put("crossChainDependencyCount", candidate.difficulty.crossChainDependencyCount)
    put("inferableCriticalDecisionRatio", candidate.difficulty.inferableCriticalDecisionRatio)
    put("bestCheapPolicySolveRate", candidate.difficulty.policyReport.bestCheapPolicySolveRate)
    put("maximumForcedRun", candidate.difficulty.maximumForcedRun)
    put("analysisComplete", candidate.decisionDag.complete && candidate.difficulty.analysisCompletenessPass)
    put("solverStateCount", candidate.decisionDag.nodes.size)
    put("failures", buildJsonArray { failures.forEach { add(JsonPrimitive(it)) } })
}

fun promoteGeneratorV61Campaign(options: Map<String, String>) {
    val production = File(options.requiredV61("campaign"))
    val candidate = File(options.requiredV61("candidate"))
    val certificate = File(options.requiredV61("certificate"))
    val manifest = File(options.requiredV61("manifest"))
    val rollback = File(options.requiredV61("rollback"))
    require(sha256V61(production) == options.requiredV61("expected-production-sha")) { "Production SHA changed" }
    require(sha256V61(candidate) == options.requiredV61("expected-candidate-sha")) { "Candidate SHA changed" }
    require(sha256V61(certificate) == options.requiredV61("certificate-sha")) { "Certificate SHA changed" }
    require(sha256V61(manifest) == options.requiredV61("manifest-sha")) { "Manifest SHA changed" }
    val certificateJson = Json.parseToJsonElement(certificate.readText()).jsonObject
    require(certificateJson["validationPlan"]?.jsonPrimitive?.content == V61_PHASED_VALIDATION_PLAN)
    require(certificateJson["certificationType"]?.jsonPrimitive?.content == "AUTOMATED_CAMPAIGN_CERTIFIED")
    require(certificateJson["campaignHash"]?.jsonPrimitive?.content == sha256V61(candidate))
    require(certificateJson["manifestHash"]?.jsonPrimitive?.content == sha256V61(manifest))
    require(certificateJson["boardCount"]?.jsonPrimitive?.content == "2205")
    require(certificateJson["certifiedBoardCount"]?.jsonPrimitive?.content == "2205")
    require(certificateJson["phaseOneBoardCount"]?.jsonPrimitive?.content == V61_PHASE_ONE_BOARD_COUNT.toString())
    require(certificateJson["phaseTwoBoardCount"]?.jsonPrimitive?.content == V61_PHASE_TWO_BOARD_COUNT.toString())
    listOf(
        "phaseOneCertified",
        "phaseTwoCertified",
        "allLevelsCertified",
        "tutorialsPreserved",
        "allAnalysisComplete",
        "allVisibleProofsComplete",
        "allProductionEngineSolvableAndReplayed",
        "allDifficultyCapsPassed",
        "allPurposefulOccupancyPassed",
        "pacingPassed",
        "regressionCorpusPassed",
        "deterministicRegenerationPassed",
        "mandatoryAutomatedTestSuitePassed",
    ).forEach { gate ->
        require(certificateJson[gate]?.jsonPrimitive?.content == "true") { "Certificate gate failed: $gate" }
    }
    listOf(
        "requiredGuessingPredictionCount",
        "exactDuplicateCount",
        "d4DuplicateCount",
        "relevancePrunedD4DuplicateCount",
        "causalGraphDuplicateCount",
        "decisionDagDuplicateCount",
        "solutionPolicyDuplicateCount",
        "synthesisGraphDuplicateCount",
        "nearSemanticFailureCount",
    ).forEach { gate ->
        require(certificateJson[gate]?.jsonPrimitive?.content == "0") { "Certificate count gate failed: $gate" }
    }
    require(
        certificateJson["statement"]?.jsonPrimitive?.content ==
            "This campaign passed automated technical certification and has not been human-validated.",
    )
    val parsed = LevelParser().parseCatalog(candidate.readText())
    require(parsed.levels.size == 2205 && parsed.contentVersion == 12 && parsed.generatorVersion == 6)
    if (!rollback.exists()) Files.copy(production.toPath(), rollback.toPath())
    require(sha256V61(rollback) == options.requiredV61("expected-production-sha")) { "Rollback artifact is invalid" }
    val staged = File(production.parentFile, ".${production.name}.v61-promoting")
    Files.copy(candidate.toPath(), staged.toPath(), StandardCopyOption.REPLACE_EXISTING)
    require(sha256V61(staged) == sha256V61(candidate))
    Files.move(staged.toPath(), production.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    require(sha256V61(production) == sha256V61(candidate))
    File(options.requiredV61("receipt")).writeText(
        "status=PROMOTED_V61_CAMPAIGN\npreviousSha256=${sha256V61(rollback)}\nnewSha256=${sha256V61(production)}\n" +
            "certificateSha256=${sha256V61(certificate)}\nmanifestSha256=${sha256V61(manifest)}\n",
    )
    println("PROMOTED_V61_CAMPAIGN ${sha256V61(production)}")
}

private fun V61CertifiedCandidate.withProductionMetadata(previous: LevelDefinition?): LevelDefinition {
    val metrics = requireNotNull(decisionDag.metrics)
    val winningFirst = requireNotNull(decisionDag.nodes[decisionDag.rootStateKey]).transitions.count {
        it.successful && it.futureSolvable == true
    }
    val solutionCount = countWinningPoliciesV61(decisionDag)
    val base = level.copy(metadata = null)
    return base.copy(
        metadata = LevelMetadata(
            contentVersion = V61_CONTENT_VERSION,
            origin = LevelOrigin.GENERATOR_ASSISTED,
            generatorVersion = V61_CATALOG_GENERATOR_VERSION,
            generatorSeed = identity.seed,
            generationProfile = "v6.1-${identity.band.name.lowercase().replace('_', '-')}",
            difficultyBand = when (identity.band) {
                AutomatedDifficultyBandV61.EASY, AutomatedDifficultyBandV61.MEDIUM -> DifficultyBand.INTRO
                AutomatedDifficultyBandV61.HARD -> DifficultyBand.DEVELOPING
                else -> DifficultyBand.ADVANCED
            },
            certifiedSolutionLength = difficulty.solutionActions,
            solutionCount = solutionCount.first,
            solutionCountCapped = solutionCount.second,
            validFirstActionCount = winningFirst,
            exploredStateCount = metrics.reachableStateCount,
            grading = GradingThresholds(difficulty.solutionActions, difficulty.solutionActions + maxOf(2, difficulty.solutionActions / 4)),
            packId = "campaign-v12-v6.1",
            mechanicTags = listOf(
                "V61_GENERATOR_ID=${GENERATOR_IDENTITY_V61}",
                "V61_FAMILY_${identity.causalFamilyIdentifier}",
                "V61_TOPOLOGY_${identity.topologyFamily.name}",
                "V61_CLUSTER_${fingerprints.strategyBehaviourClusterIdentifier}",
                "V61_DIFFICULTY_${identity.band.name}",
                "V61_ATTEMPT_${identity.attempt}",
            ),
            contentFingerprint = ContentFingerprint.of(base),
            previousContentFingerprint = previous?.metadata?.contentFingerprint?.takeIf { it != ContentFingerprint.of(base) },
        ),
    )
}

private fun countWinningPoliciesV61(analysis: com.rameshta.magnetrail.core.generation.v6.DecisionDagAnalysisV6): Pair<Int, Boolean> {
    val cap = 100_000
    val memo = hashMapOf<String, Int>()
    fun count(key: String): Int {
        memo[key]?.let { return it }
        val node = requireNotNull(analysis.nodes[key])
        if (node.state.arrows.isEmpty()) return 1
        var total = 0
        node.transitions.filter { it.successful && it.futureSolvable == true }.forEach { edge ->
            total = (total + count(requireNotNull(edge.childStateKey))).coerceAtMost(cap + 1)
        }
        return total.also { memo[key] = it }
    }
    val total = count(analysis.rootStateKey)
    return total.coerceAtMost(cap) to (total > cap)
}

internal fun V61CertifiedCandidate.auditRow(number: Int, requested: AutomatedDifficultyBandV61) = V61CandidateAuditRow(
    number,
    requested,
    true,
    identity.stableKey,
    identity.causalFamilyIdentifier,
    fingerprints.strategyBehaviourClusterIdentifier,
    fingerprints,
    purposefulOccupancy.occupiedRatio,
    purposefulOccupancy.purposefulOccupiedRatio,
    purposefulOccupancy.inertOccupiedRatio,
    decisionDag.nodes.size,
    difficulty.analysisCompletenessPass,
    difficulty.criticalEpisodes.size,
    difficulty.persistentSuccessfulTrapCount,
    difficulty.criticalEpisodes.maxOfOrNull { it.proof.minimumVisibleLookahead } ?: 0,
    difficulty.inferableCriticalDecisionRatio,
    difficulty.guessDependencePass,
    difficulty.policyReport.bestCheapPolicySolveRate,
    difficulty.maximumEligibleBand,
    emptyMap(),
)

private fun campaignAuditJsonV61(rows: List<V61CandidateAuditRow>): JsonObject = buildJsonObject {
    put("schemaVersion", 1)
    put("generatorIdentity", GENERATOR_IDENTITY_V61)
    put("candidates", buildJsonArray {
        rows.forEach { row ->
            add(buildJsonObject {
                put("levelNumber", row.levelNumber)
                put("requestedBand", row.requestedBand.displayName)
                put("accepted", row.accepted)
                put("identity", row.identity ?: "")
                put("causalFamily", row.family ?: "")
                put("strategyCluster", row.cluster ?: "")
                put("fingerprints", row.fingerprints?.let(::fingerprintsJsonV61) ?: JsonObject(emptyMap()))
                put("occupiedRatio", row.occupiedRatio ?: -1.0)
                put("purposefulOccupiedRatio", row.purposefulRatio ?: -1.0)
                put("inertOccupiedRatio", row.inertRatio ?: -1.0)
                put("solverStateCount", row.stateCount ?: 0)
                put("analysisTruncated", row.analysisCompletenessPass == false)
                put("criticalDecisionCount", row.criticalDecisionCount ?: 0)
                put("persistentTrapCount", row.persistentTrapCount ?: 0)
                put("minimumVisibleLookahead", row.minimumVisibleLookahead ?: 0)
                put("inferableCriticalDecisionRatio", row.inferableCriticalDecisionRatio ?: 0.0)
                put("guessDependencePass", row.guessDependencePass == true)
                put("bestCheapPolicySolveRate", row.bestCheapPolicySolveRate ?: 1.0)
                put("maximumEligibleBand", row.maximumEligibleBand?.displayName ?: "NONE")
                put("rejectionCounts", buildJsonObject { row.rejectionCounts.toSortedMap().forEach { (key, value) -> put(key, value) } })
            })
        }
    })
}

private fun fingerprintsJsonV61(value: V6FingerprintBundle): JsonObject = buildJsonObject {
    put("exact", value.exactLayout)
    put("d4", value.d4Layout)
    put("arrow", value.arrowLayout)
    put("interactive", value.interactiveLayout)
    put("perceptual", value.perceptualLayout)
    put("relevancePrunedD4", value.relevancePrunedD4Layout)
    put("causal", value.causalHypergraph)
    put("decisionDag", value.quotientDecisionDag)
    put("solutionPolicy", value.solutionPolicy)
    put("productionTransitionTrace", value.productionStateTransitionTrace)
    put("meaningfulDecisionTrace", value.meaningfulDecisionTrace)
    put("mechanicRhythm", value.mechanicRhythm)
    put("synthesisGraph", value.synthesisGraphIdentifier)
    put("behaviouralDescriptor", buildJsonArray {
        value.behaviouralDescriptor.forEach { add(JsonPrimitive(it)) }
    })
    put("nearestSemanticFingerprint", value.nearestSemanticFingerprint ?: "")
    put("nearestSemanticSimilarity", value.nearestSemanticSimilarity ?: 0.0)
}

private fun candidateAuditRowFromJsonV61(row: JsonObject): V61CandidateAuditRow {
    val fingerprints = row["fingerprints"]?.jsonObject?.takeIf { it.isNotEmpty() }?.let(::fingerprintBundleFromJsonV61)
    val maximumEligibleBand = row["maximumEligibleBand"]?.jsonPrimitive?.content
        ?.takeUnless { it == "NONE" || it.isBlank() }
        ?.let(::automatedBandV61)
    val rejections = row["rejectionCounts"]?.jsonObject.orEmpty().mapValues { (_, value) ->
        value.jsonPrimitive.content.toInt()
    }
    return V61CandidateAuditRow(
        levelNumber = row.getValue("levelNumber").jsonPrimitive.content.toInt(),
        requestedBand = automatedBandV61(row.getValue("requestedBand").jsonPrimitive.content),
        accepted = row.getValue("accepted").jsonPrimitive.content.toBooleanStrict(),
        identity = row["identity"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank),
        family = row["causalFamily"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank),
        cluster = row["strategyCluster"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank),
        fingerprints = fingerprints,
        occupiedRatio = row["occupiedRatio"]?.jsonPrimitive?.content?.toDoubleOrNull(),
        purposefulRatio = row["purposefulOccupiedRatio"]?.jsonPrimitive?.content?.toDoubleOrNull(),
        inertRatio = row["inertOccupiedRatio"]?.jsonPrimitive?.content?.toDoubleOrNull(),
        stateCount = row["solverStateCount"]?.jsonPrimitive?.content?.toIntOrNull(),
        analysisCompletenessPass = row["analysisTruncated"]?.jsonPrimitive?.content?.let { it == "false" },
        criticalDecisionCount = row["criticalDecisionCount"]?.jsonPrimitive?.content?.toIntOrNull(),
        persistentTrapCount = row["persistentTrapCount"]?.jsonPrimitive?.content?.toIntOrNull(),
        minimumVisibleLookahead = row["minimumVisibleLookahead"]?.jsonPrimitive?.content?.toIntOrNull(),
        inferableCriticalDecisionRatio = row["inferableCriticalDecisionRatio"]?.jsonPrimitive?.content?.toDoubleOrNull(),
        guessDependencePass = row["guessDependencePass"]?.jsonPrimitive?.content?.toBooleanStrictOrNull(),
        bestCheapPolicySolveRate = row["bestCheapPolicySolveRate"]?.jsonPrimitive?.content?.toDoubleOrNull(),
        maximumEligibleBand = maximumEligibleBand,
        rejectionCounts = rejections,
    )
}

private fun fingerprintBundleFromJsonV61(values: JsonObject): V6FingerprintBundle {
    fun field(name: String, fallback: String = "unavailable:$name") =
        values[name]?.jsonPrimitive?.content ?: fallback
    return V6FingerprintBundle(
        exactLayout = field("exact"),
        d4Layout = field("d4"),
        arrowLayout = field("arrow"),
        interactiveLayout = field("interactive"),
        perceptualLayout = field("perceptual"),
        relevancePrunedD4Layout = field("relevancePrunedD4"),
        causalHypergraph = field("causal"),
        quotientDecisionDag = field("decisionDag"),
        solutionPolicy = field("solutionPolicy"),
        meaningfulDecisionTrace = field("meaningfulTrace", field("meaningfulDecisionTrace")),
        mechanicRhythm = field("mechanicRhythm"),
        behaviouralDescriptor = values["behaviouralDescriptor"]?.jsonArray
            ?.map { it.jsonPrimitive.content.toDouble() }.orEmpty(),
        productionStateTransitionTrace = field("productionTransitionTrace"),
        causalFamilyIdentifier = field("causalFamily"),
        strategyBehaviourClusterIdentifier = field("strategyCluster"),
        synthesisGraphIdentifier = field("synthesisGraph"),
        nearestSemanticFingerprint = values["nearestSemanticFingerprint"]?.jsonPrimitive?.content
            ?.takeIf(String::isNotBlank),
        nearestSemanticSimilarity = values["nearestSemanticSimilarity"]?.jsonPrimitive?.content?.toDoubleOrNull(),
    )
}

internal fun writeGeneratorV61Checkpoint(
    directory: File,
    phase: V61CampaignPhase,
    level: LevelDefinition,
    row: V61CandidateAuditRow,
    acceptedAttempt: Int,
    context: V61CheckpointContext,
) {
    val parser = LevelParser()
    val catalog = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = "magnetrail-core-1",
        catalogId = "${phase.catalogId}-checkpoint",
        levels = listOf(level),
        contentVersion = V61_CONTENT_VERSION,
        generatorVersion = V61_CATALOG_GENERATOR_VERSION,
    )
    val catalogJson = Json.parseToJsonElement(parser.encodeCatalog(catalog)).jsonObject
    val auditJson = campaignAuditJsonV61(listOf(row))
    val checkpoint = buildJsonObject {
        put("schemaVersion", 2)
        put("checkpointType", "V61_ATOMIC_CERTIFIED_LEVEL")
        put("phase", phase.optionName)
        put("levelNumber", level.number)
        put("requestedBand", row.requestedBand.displayName)
        put("startingAttempt", campaignStartingAttemptV61(phase, row.requestedBand, row.levelNumber))
        put("acceptedAttempt", acceptedAttempt)
        put("sourceCampaignSha256", context.sourceCampaignSha256)
        put("archiveBindingHash", context.archiveBindingHash)
        put("relevantSourceHash", context.relevantSourceHash)
        put("preflightSha256", context.preflightSha256)
        put("catalog", catalogJson)
        put("audit", auditJson)
    }
    writeAtomicallyV61(
        File(directory, checkpointAtomicNameV61(level.number)),
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), checkpoint) + "\n",
    )
    // Keep the original split files as inspectable compatibility artifacts. Resume uses the
    // single atomic wrapper above, so interruption cannot admit half a checkpoint.
    writeAtomicallyV61(File(directory, checkpointLevelNameV61(level.number)), parser.encodeCatalog(catalog) + "\n")
    writeAtomicallyV61(
        File(directory, checkpointAuditNameV61(level.number)),
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), auditJson) + "\n",
    )
}

private fun writeAtomicallyV61(destination: File, content: String) {
    destination.parentFile?.mkdirs()
    val temporary = File(destination.parentFile, ".${destination.name}.tmp")
    temporary.writeText(content)
    runCatching {
        Files.move(
            temporary.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }.getOrElse {
        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun checkpointLevelNameV61(number: Int): String =
    "campaign-${number.toString().padStart(4, '0')}.json"

private fun checkpointAuditNameV61(number: Int): String =
    "campaign-${number.toString().padStart(4, '0')}-audit.json"

private fun checkpointAtomicNameV61(number: Int): String =
    "campaign-${number.toString().padStart(4, '0')}-checkpoint.json"

internal fun checkpointMatchesContextV61(
    checkpoint: JsonObject,
    phase: V61CampaignPhase,
    context: V61CheckpointContext,
): Boolean = checkpoint["checkpointType"]?.jsonPrimitive?.content == "V61_ATOMIC_CERTIFIED_LEVEL" &&
    checkpoint["phase"]?.jsonPrimitive?.content == phase.optionName &&
    checkpoint["sourceCampaignSha256"]?.jsonPrimitive?.content == context.sourceCampaignSha256 &&
    checkpoint["archiveBindingHash"]?.jsonPrimitive?.content == context.archiveBindingHash &&
    checkpoint["relevantSourceHash"]?.jsonPrimitive?.content == context.relevantSourceHash &&
    checkpoint["preflightSha256"]?.jsonPrimitive?.content == context.preflightSha256

internal fun checkpointMatchesAdditiveHardEpochV61(
    checkpoint: JsonObject,
    phase: V61CampaignPhase,
    context: V61CheckpointContext,
    levelNumber: Int,
    acceptedAttempt: Int,
): Boolean {
    val priorHash = checkpoint["relevantSourceHash"]?.jsonPrimitive?.content
    val proposalPathUnchanged =
        (priorHash == V61_HARD_CAPACITY_BASE_RELEVANT_SOURCE_HASH &&
            acceptedAttempt < V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT) ||
            (priorHash == V61_HARD_CAPACITY_EPOCH_RELEVANT_SOURCE_HASH &&
                levelNumber < V61_HARD_CAPACITY_ROLLOUT_LEVEL)
    return proposalPathUnchanged &&
        checkpoint["checkpointType"]?.jsonPrimitive?.content == "V61_ATOMIC_CERTIFIED_LEVEL" &&
        checkpoint["phase"]?.jsonPrimitive?.content == phase.optionName &&
        checkpoint["sourceCampaignSha256"]?.jsonPrimitive?.content == context.sourceCampaignSha256 &&
        checkpoint["archiveBindingHash"]?.jsonPrimitive?.content == context.archiveBindingHash &&
        checkpoint["preflightSha256"]?.jsonPrimitive?.content?.isNotBlank() == true
}

/**
 * The named revision already certified every stored board and persisted every expensive audit
 * field, but graph-first Easy/Medium/Hard rows omitted their logical synthesis identifier. This
 * migration is deliberately exact-hash-bound: resume recomputes the canonical graph from the
 * accepted identity, performs the full indexed duplicate check, and only then atomically rebinds
 * the checkpoint. Any real collision is preserved and excluded by the ordinary resume path.
 */
internal fun checkpointMatchesSynthesisEvidenceMigrationV61(
    checkpoint: JsonObject,
    phase: V61CampaignPhase,
    context: V61CheckpointContext,
): Boolean = checkpoint["relevantSourceHash"]?.jsonPrimitive?.content ==
    V61_SYNTHESIS_EVIDENCE_RELEVANT_SOURCE_HASH &&
    checkpoint["checkpointType"]?.jsonPrimitive?.content == "V61_ATOMIC_CERTIFIED_LEVEL" &&
    checkpoint["phase"]?.jsonPrimitive?.content == phase.optionName &&
    checkpoint["sourceCampaignSha256"]?.jsonPrimitive?.content == context.sourceCampaignSha256 &&
    checkpoint["archiveBindingHash"]?.jsonPrimitive?.content == context.archiveBindingHash &&
    checkpoint["preflightSha256"]?.jsonPrimitive?.content?.isNotBlank() == true

/**
 * The high-band capacity rollout is campaign-number guarded. Phase 1 never enters either affected
 * requested band, and every Phase 2 checkpoint before the rollout number retains the exact prior
 * proposal path. Archive equality is mandatory so changed capacity evidence cannot be rebound.
 */
internal fun checkpointMatchesHighBandCapacityMigrationV61(
    checkpoint: JsonObject,
    phase: V61CampaignPhase,
    context: V61CheckpointContext,
    levelNumber: Int,
): Boolean {
    val proposalPathUnchanged = phase == V61CampaignPhase.PHASE_ONE ||
        (phase == V61CampaignPhase.PHASE_TWO && levelNumber < V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL)
    return proposalPathUnchanged &&
        checkpoint["relevantSourceHash"]?.jsonPrimitive?.content ==
            V61_HIGH_BAND_CAPACITY_BASE_RELEVANT_SOURCE_HASH &&
        checkpoint["checkpointType"]?.jsonPrimitive?.content == "V61_ATOMIC_CERTIFIED_LEVEL" &&
        checkpoint["phase"]?.jsonPrimitive?.content == phase.optionName &&
        checkpoint["sourceCampaignSha256"]?.jsonPrimitive?.content == context.sourceCampaignSha256 &&
        checkpoint["archiveBindingHash"]?.jsonPrimitive?.content == context.archiveBindingHash &&
        checkpoint["preflightSha256"]?.jsonPrimitive?.content?.isNotBlank() == true
}

/** The follow-up only removes the rollout from Expert; Phase 1 and campaign 759 are unaffected. */
internal fun checkpointMatchesSuperHardOnlyMigrationV61(
    checkpoint: JsonObject,
    phase: V61CampaignPhase,
    context: V61CheckpointContext,
    levelNumber: Int,
): Boolean {
    val proposalPathUnchanged = phase == V61CampaignPhase.PHASE_ONE ||
        (phase == V61CampaignPhase.PHASE_TWO && levelNumber <= V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL)
    return proposalPathUnchanged &&
        checkpoint["relevantSourceHash"]?.jsonPrimitive?.content ==
            V61_SUPER_HARD_ONLY_BASE_RELEVANT_SOURCE_HASH &&
        checkpoint["checkpointType"]?.jsonPrimitive?.content == "V61_ATOMIC_CERTIFIED_LEVEL" &&
        checkpoint["phase"]?.jsonPrimitive?.content == phase.optionName &&
        checkpoint["sourceCampaignSha256"]?.jsonPrimitive?.content == context.sourceCampaignSha256 &&
        checkpoint["archiveBindingHash"]?.jsonPrimitive?.content == context.archiveBindingHash &&
        checkpoint["preflightSha256"]?.jsonPrimitive?.content?.isNotBlank() == true
}

/** The final revision adds Expert to the existing Super Hard epoch at campaign 759. */
internal fun checkpointMatchesEpochRotationMigrationV61(
    checkpoint: JsonObject,
    phase: V61CampaignPhase,
    context: V61CheckpointContext,
    levelNumber: Int,
    band: AutomatedDifficultyBandV61,
): Boolean {
    val proposalPathUnchanged = phase == V61CampaignPhase.PHASE_ONE ||
        (phase == V61CampaignPhase.PHASE_TWO &&
            (levelNumber < V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL ||
                band == AutomatedDifficultyBandV61.SUPER_HARD))
    return proposalPathUnchanged &&
        checkpoint["relevantSourceHash"]?.jsonPrimitive?.content ==
            V61_EPOCH_ROTATION_BASE_RELEVANT_SOURCE_HASH &&
        checkpoint["checkpointType"]?.jsonPrimitive?.content == "V61_ATOMIC_CERTIFIED_LEVEL" &&
        checkpoint["phase"]?.jsonPrimitive?.content == phase.optionName &&
        checkpoint["sourceCampaignSha256"]?.jsonPrimitive?.content == context.sourceCampaignSha256 &&
        checkpoint["archiveBindingHash"]?.jsonPrimitive?.content == context.archiveBindingHash &&
        checkpoint["preflightSha256"]?.jsonPrimitive?.content?.isNotBlank() == true
}

/** Scheduler windowing changes execution overlap only; deterministic proposal order is identical. */
internal fun checkpointMatchesSpeculativeWindowMigrationV61(
    checkpoint: JsonObject,
    phase: V61CampaignPhase,
    context: V61CheckpointContext,
): Boolean = checkpoint["relevantSourceHash"]?.jsonPrimitive?.content ==
    V61_SPECULATIVE_WINDOW_BASE_RELEVANT_SOURCE_HASH &&
    checkpoint["checkpointType"]?.jsonPrimitive?.content == "V61_ATOMIC_CERTIFIED_LEVEL" &&
    checkpoint["phase"]?.jsonPrimitive?.content == phase.optionName &&
    checkpoint["sourceCampaignSha256"]?.jsonPrimitive?.content == context.sourceCampaignSha256 &&
    checkpoint["archiveBindingHash"]?.jsonPrimitive?.content == context.archiveBindingHash &&
    checkpoint["preflightSha256"]?.jsonPrimitive?.content?.isNotBlank() == true

internal fun campaignStartingAttemptV61(
    phase: V61CampaignPhase,
    band: AutomatedDifficultyBandV61,
    levelNumber: Int,
): Int = if (
    phase == V61CampaignPhase.PHASE_TWO &&
    (band == AutomatedDifficultyBandV61.SUPER_HARD || band == AutomatedDifficultyBandV61.EXPERT) &&
    levelNumber >= V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL
) {
    V61_HIGH_BAND_CAPACITY_EPOCH_START_ATTEMPT
} else {
    0
}

private fun acceptedAttemptFromIdentityV61(identity: String): Int {
    val match = Regex("^auto-journey-v1-\\d+-[a-z_]+-(\\d+)-(-?\\d+)$").matchEntire(identity)
        ?: error("checkpoint identity does not expose a deterministic attempt: $identity")
    return match.groupValues[1].toInt()
}

private fun fingerprintEvidenceEquivalentV61(first: V6FingerprintBundle, second: V6FingerprintBundle): Boolean =
    first.exactLayout == second.exactLayout &&
        first.d4Layout == second.d4Layout &&
        first.relevancePrunedD4Layout == second.relevancePrunedD4Layout &&
        first.causalHypergraph == second.causalHypergraph &&
        first.quotientDecisionDag == second.quotientDecisionDag &&
        first.solutionPolicy == second.solutionPolicy &&
        first.productionStateTransitionTrace == second.productionStateTransitionTrace &&
        first.meaningfulDecisionTrace == second.meaningfulDecisionTrace &&
        first.mechanicRhythm == second.mechanicRhythm &&
        first.synthesisGraphIdentifier == second.synthesisGraphIdentifier &&
        first.behaviouralDescriptor == second.behaviouralDescriptor

private fun preserveLegacyCheckpointsV61(
    directory: File,
    scheduledSlots: List<V61ScheduledCampaignSlot>,
    firstExcludedLevel: Int,
) {
    val preserved = File(directory, "incompatible-legacy").also(File::mkdirs)
    scheduledSlots.asSequence().filter { it.levelNumber >= firstExcludedLevel }.forEach { slot ->
        listOf(
            checkpointLevelNameV61(slot.levelNumber),
            checkpointAuditNameV61(slot.levelNumber),
            checkpointAtomicNameV61(slot.levelNumber),
        ).forEach { name ->
            val source = File(directory, name)
            if (source.isFile) {
                Files.copy(source.toPath(), File(preserved, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}

private fun writeCheckpointCompatibilityV61(
    directory: File,
    rows: List<JsonObject>,
    context: V61CheckpointContext,
) {
    val report = buildJsonObject {
        put("schemaVersion", 1)
        put("sourceCampaignSha256", context.sourceCampaignSha256)
        put("archiveBindingHash", context.archiveBindingHash)
        put("relevantSourceHash", context.relevantSourceHash)
        put("preflightSha256", context.preflightSha256)
        put("validatedCount", rows.count { it["status"]?.jsonPrimitive?.content != "INCOMPATIBLE_EXCLUDED" })
        put("incompatibleCount", rows.count { it["status"]?.jsonPrimitive?.content == "INCOMPATIBLE_EXCLUDED" })
        put("rows", JsonArray(rows))
    }
    writeAtomicallyV61(
        File(directory, "CHECKPOINT_COMPATIBILITY.json"),
        Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), report) + "\n",
    )
}

private fun layoutOnlyV61(level: LevelDefinition) = V6FingerprintBundle(
    exactLayout = ContentFingerprint.exact(level),
    d4Layout = ContentFingerprint.symmetryNormalized(level),
    arrowLayout = ContentFingerprint.arrowLayoutSymmetryNormalized(level),
    interactiveLayout = ContentFingerprint.interactiveLayoutSymmetryNormalized(level),
    perceptualLayout = ContentFingerprint.perceptualTemplateSignature(level),
    relevancePrunedD4Layout = ContentFingerprint.symmetryNormalized(level),
    causalHypergraph = "unavailable:causal:${level.id}",
    quotientDecisionDag = "unavailable:dag:${level.id}",
    solutionPolicy = "unavailable:policy:${level.id}",
    meaningfulDecisionTrace = "unavailable:trace:${level.id}",
    mechanicRhythm = "unavailable:rhythm:${level.id}",
    behaviouralDescriptor = emptyList(),
)

private fun auditBundlesV61(file: File): List<V6FingerprintBundle> {
    val candidates = Json.parseToJsonElement(file.readText()).jsonObject.getValue("candidates").jsonArray
    return candidates.map { it.jsonObject }.filter { it["accepted"]?.jsonPrimitive?.content == "true" }.map { candidate ->
        val values = candidate.getValue("fingerprints").jsonObject
        fun field(name: String, fallback: String = "unavailable:$name") =
            values[name]?.jsonPrimitive?.content ?: fallback
        V6FingerprintBundle(
            field("exact"), field("d4"), field("arrow"), field("interactive"), field("perceptual"),
            field("relevancePrunedD4"), field("causal"), field("decisionDag"), field("solutionPolicy"),
            field("meaningfulTrace", field("meaningfulDecisionTrace")), field("mechanicRhythm"),
            values["behaviouralDescriptor"]?.jsonArray?.map { it.jsonPrimitive.content.toDouble() } ?: emptyList(),
            productionStateTransitionTrace = field("productionTransitionTrace"),
            causalFamilyIdentifier = candidate["causalFamily"]?.jsonPrimitive?.content ?: "unavailable:causal-family",
            strategyBehaviourClusterIdentifier = candidate["strategyCluster"]?.jsonPrimitive?.content
                ?: "unavailable:strategy-cluster",
            synthesisGraphIdentifier = field("synthesisGraph"),
        )
    }
}

private fun sha256V61(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun relevantFilesV61(paths: List<File>): List<File> = paths.flatMap { path ->
    when {
        path.isFile -> listOf(path)
        path.isDirectory -> path.walkTopDown().filter(File::isFile)
            .filterNot { file -> file.path.contains("${File.separator}build${File.separator}") }
            .filterNot { file -> file.path.contains("${File.separator}.gradle${File.separator}") }
            .toList()
        else -> emptyList()
    }
}.distinctBy { it.absoluteFile.normalize().path }.sortedBy { it.absoluteFile.normalize().path }

private fun hashPathsV61(paths: List<File>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    relevantFilesV61(paths).forEach { file ->
        val path = file.absoluteFile.normalize().path.toByteArray()
        digest.update(path.size.toString().toByteArray())
        digest.update(0)
        digest.update(path)
        digest.update(0)
        digest.update(file.readBytes())
        digest.update(0)
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun bindingHashV61(options: Map<String, String>): String {
    val keys = listOf(
        "comparison-catalogs", "v6-audit", "capacity-audit", "capacity-audits",
        "archive-audits", "pacing-audits", "regression",
    )
    val digest = MessageDigest.getInstance("SHA-256")
    keys.forEach { key ->
        val paths = options[key]?.split(File.pathSeparator).orEmpty().filter(String::isNotBlank)
        digest.update(key.toByteArray())
        digest.update(0)
        paths.forEach { path ->
            val file = File(path)
            digest.update(file.absoluteFile.normalize().path.toByteArray())
            digest.update(0)
            if (file.isFile) digest.update(file.readBytes()) else digest.update("MISSING".toByteArray())
            digest.update(0)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

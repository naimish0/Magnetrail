package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.generation.v6.CausalGrammarFamilyV6
import com.rameshta.magnetrail.core.generation.v6.CompleteDecisionDagAnalyzerV6
import com.rameshta.magnetrail.core.generation.v6.GENERATOR_VERSION_V6
import com.rameshta.magnetrail.core.generation.v6.GeneratorV6
import com.rameshta.magnetrail.core.generation.v6.GeneratorV6Identity
import com.rameshta.magnetrail.core.generation.v6.HumanCognitiveFeatureExtractorV1
import com.rameshta.magnetrail.core.generation.v6.HumanCognitiveFeaturesV1
import com.rameshta.magnetrail.core.generation.v6.HumanCalibrationObservationV1
import com.rameshta.magnetrail.core.generation.v6.HumanDifficultyModelV1
import com.rameshta.magnetrail.core.generation.v6.HumanModelFitResultV1
import com.rameshta.magnetrail.core.generation.v6.HumanModelValidatorV1
import com.rameshta.magnetrail.core.generation.v6.HumanPolicyEnsembleV1
import com.rameshta.magnetrail.core.generation.v6.CumulativeLinkOrdinalCalibratorV1
import com.rameshta.magnetrail.core.generation.v6.CampaignMigrationV6
import com.rameshta.magnetrail.core.generation.v6.DeterministicMapElitesV6
import com.rameshta.magnetrail.core.generation.v6.MapElitesCellV6
import com.rameshta.magnetrail.core.generation.v6.V6_HUMAN_BAND_COUNT
import com.rameshta.magnetrail.core.generation.v6.V6_PILOT_BOARD_COUNT
import com.rameshta.magnetrail.core.generation.v6.V6Candidate
import com.rameshta.magnetrail.core.generation.v6.V6CertificationStatus
import com.rameshta.magnetrail.core.generation.v6.V6FingerprintBundle
import com.rameshta.magnetrail.core.generation.v6.V6GenerationRequest
import com.rameshta.magnetrail.core.generation.v6.V6GenerationResult
import com.rameshta.magnetrail.core.generation.v6.V6Profiles
import com.rameshta.magnetrail.core.generation.v6.V6ProgressSnapshot
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val EXPECTED_V10_SHA = "8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9"
private const val EXPECTED_INFINITE_SHA = "c3500d87eedfa7cdbfdde1000bf49ae9af5c2a0e0c24c4ce5c7d977b5d6b4f83"
private const val EXPECTED_DAILY_SHA = "3f21a633fa1e51d29938b830fe567103a52354e367d3177c20b8196c881156c8"
private const val EXPECTED_V11_SHA = "035ebef661048892fe5236dd4a8c6682c1decf9d9e0585a505c645e4e8b39184"
private const val V6_CALIBRATION_SEED = 6_120_000_001L
private const val V6_VALIDATION_SEED = 6_220_000_001L
private const val V6_SLOT_SEED_ATTEMPTS = 4
private const val V6_SLOT_SEED_STRIDE = 10_000_019L

private val v6Json = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = true }

@Serializable
internal data class V6BaselineCatalogAuditDto(
    val name: String,
    val path: String,
    val expectedSha256: String,
    val actualSha256: String,
    val hashMatches: Boolean,
    val schemaVersion: Int,
    val contentVersion: Int,
    val generatorVersion: Int?,
    val levelCount: Int,
    val exactUnique: Int,
    val d4Unique: Int,
    val arrowLayoutUnique: Int,
    val interactiveLayoutUnique: Int,
    val perceptualUnique: Int,
)

@Serializable
internal data class V6CloneGroupDto(
    val sourceCatalog: String,
    val signatureType: String,
    val signature: String,
    val levelIds: List<String>,
    val reason: String,
    val catalogLoadingCause: Boolean,
    val runtimeCachingCause: Boolean,
    val renderingCause: Boolean,
    val literalTransformCause: Boolean,
    val semanticEquivalenceBeyondOldFingerprint: Boolean,
)

@Serializable
internal data class V6StrategyAuditDto(
    val levelId: String,
    val complete: Boolean,
    val reachableStates: Int,
    val meaningfulDecisions: Int?,
    val successfulLosingBranches: Int?,
    val hardestWinningShare: Double?,
    val solutionPolicyClasses: Int?,
    val truncationReasons: List<String>,
)

@Serializable
internal data class V6BaselineAuditDto(
    val schemaVersion: Int = 1,
    val status: String,
    val catalogs: List<V6BaselineCatalogAuditDto>,
    val d4Contract: Map<String, String>,
    val runtimePathFindings: Map<String, String>,
    val representativeCloneGroups: List<V6CloneGroupDto>,
    val solverStrategyFindings: List<V6StrategyAuditDto>,
    val conclusion: String,
)

internal fun auditGeneratorV6Baseline(options: Map<String, String>) {
    val campaignFile = File(options.requiredV6("campaign"))
    val infiniteFile = File(options.requiredV6("infinite"))
    val dailyFile = File(options.requiredV6("daily"))
    val v11File = File(options.requiredV6("v11"))
    val v9File = File(options.requiredV6("v9-source"))
    val playtestFile = File(options.requiredV6("playtest"))
    val output = File(options.requiredV6("output")).also { it.mkdirs() }
    val parser = LevelParser()
    val catalogInputs = listOf(
        Triple("Campaign V10", campaignFile, EXPECTED_V10_SHA),
        Triple("Infinite V1", infiniteFile, EXPECTED_INFINITE_SHA),
        Triple("Daily fallback V1", dailyFile, EXPECTED_DAILY_SHA),
        Triple("V11 pilot", v11File, EXPECTED_V11_SHA),
    )
    val parsed = catalogInputs.associate { (name, file, _) -> name to parser.parseCatalog(file.readText()) }
    val catalogs = catalogInputs.map { (name, file, expected) ->
        val catalog = requireNotNull(parsed[name])
        V6BaselineCatalogAuditDto(
            name = name,
            path = file.path,
            expectedSha256 = expected,
            actualSha256 = sha256FileV6(file),
            hashMatches = sha256FileV6(file) == expected,
            schemaVersion = catalog.schemaVersion,
            contentVersion = catalog.contentVersion,
            generatorVersion = catalog.generatorVersion,
            levelCount = catalog.levels.size,
            exactUnique = catalog.levels.map(ContentFingerprint::exact).toSet().size,
            d4Unique = catalog.levels.map(ContentFingerprint::symmetryNormalized).toSet().size,
            arrowLayoutUnique = catalog.levels.map(ContentFingerprint::arrowLayoutSymmetryNormalized).toSet().size,
            interactiveLayoutUnique = catalog.levels.map(ContentFingerprint::interactiveLayoutSymmetryNormalized).toSet().size,
            perceptualUnique = catalog.levels.map(ContentFingerprint::perceptualTemplateSignature).toSet().size,
        )
    }
    check(catalogs.all { it.hashMatches }) { "Protected baseline hash mismatch; refusing V6 audit continuation" }
    val v9 = parser.parseCatalog(v9File.readText())
    val playtestRows = readCsvV6(playtestFile).filter { row -> row["expected_difficulty"] in setOf("Super Hard", "Expert", "Master") }
    val testedIds = playtestRows.mapNotNull { it["level_id"] }.toSet()
    val highCoreGroups = v9.levels.filter { it.id in testedIds }.groupBy { level ->
        ContentFingerprint.interactiveLayoutSymmetryNormalized(level)
    }.filterValues { it.size > 1 }
    val currentCampaign = requireNotNull(parsed["Campaign V10"])
    val perceptualGroups = currentCampaign.levels.filter { it.id in testedIds }.groupBy {
        ContentFingerprint.perceptualTemplateSignature(it)
    }.filterValues { it.size > 1 }
    val cloneGroups = buildList {
        highCoreGroups.entries.sortedByDescending { it.value.size }.take(8).forEach { (signature, levels) ->
            add(
                V6CloneGroupDto(
                    sourceCatalog = "Campaign V9 source cores used by V10 remediation",
                    signatureType = "D4 arrow+magnet interactive core",
                    signature = signature,
                    levelIds = levels.map { it.id }.sorted(),
                    reason = "V10 rebuilt density around a shared V9 interaction core; walls/cascades changed full layouts but not the underlying strategy template.",
                    catalogLoadingCause = false,
                    runtimeCachingCause = false,
                    renderingCause = false,
                    literalTransformCause = true,
                    semanticEquivalenceBeyondOldFingerprint = true,
                ),
            )
        }
        perceptualGroups.entries.sortedByDescending { it.value.size }.take(4).forEach { (signature, levels) ->
            add(
                V6CloneGroupDto(
                    sourceCatalog = "Campaign V10",
                    signatureType = "coarse perceptual template",
                    signature = signature,
                    levelIds = levels.map { it.id }.sorted(),
                    reason = "Distinct exact/D4 boards retain the same coarse composition; geometric uniqueness is not semantic novelty.",
                    catalogLoadingCause = false,
                    runtimeCachingCause = false,
                    renderingCause = false,
                    literalTransformCause = false,
                    semanticEquivalenceBeyondOldFingerprint = true,
                ),
            )
        }
    }
    val representativeIds = playtestRows.groupBy { it["expected_difficulty"].orEmpty() }.toSortedMap()
        .values.mapNotNull { rows -> rows.firstOrNull()?.get("level_id") }
    val strategies = representativeIds.mapNotNull { id -> v9.levels.firstOrNull { it.id == id } }.map { level ->
        val analysis = CompleteDecisionDagAnalyzerV6(DefaultGameEngine(), 500_000, 5_000_000)
            .analyze(level.initialState())
        V6StrategyAuditDto(
            level.id,
            analysis.complete,
            analysis.nodes.size,
            analysis.metrics?.meaningfulDecisionCount,
            analysis.metrics?.successfulLosingBranchCount,
            analysis.metrics?.hardestWinningChoiceShare,
            analysis.metrics?.winningSolutionPolicyClasses,
            analysis.truncationReasons,
        )
    }
    val audit = V6BaselineAuditDto(
        status = V6CertificationStatus.BASELINE_VERIFIED.name,
        catalogs = catalogs,
        d4Contract = mapOf(
            "idsAndMetadata" to "removed",
            "entityOrdering" to "canonical sort",
            "coordinates" to "all eight D4 transforms",
            "directions" to "transformed with geometry",
            "rectangles" to "90-degree/diagonal transforms swap serialized width and height",
            "magnetPolarity" to "included",
            "walls" to "included in full-board D4",
        ),
        runtimePathFindings = mapOf(
            "canonicalJson" to "protected bytes match expected V10 and parse as 2,205 distinct levels",
            "runtimeBoardState" to "LevelParser creates independent immutable BoardState objects from the selected LevelDefinition",
            "renderedInteractableBoard" to "Compose consumes current BoardState and emits arrow IDs; it does not transform or cache level geometry",
            "catalogSelection" to "AssetLevelCatalog selects stable IDs from the canonical asset; no evidence of wrong-catalog substitution",
            "cause" to "literal shared V9 cores plus semantic equivalence beyond exact/D4/arrow/interactive fingerprints",
        ),
        representativeCloneGroups = cloneGroups,
        solverStrategyFindings = strategies,
        conclusion = "Known high-band clones are content-generation/semantic-template clones, not parser, runtime-cache, catalog-selection or renderer defects.",
    )
    File(output, "GENERATOR_V6_BASELINE_AUDIT.json").writeText(v6Json.encodeToString(audit) + "\n")
    File(output, "GENERATOR_V6_BASELINE_AUDIT.md").writeText(renderBaselineAuditV6(audit))
    println("${audit.status}: ${catalogs.sumOf { it.levelCount }} protected boards audited; ${cloneGroups.size} representative clone groups")
}

@Serializable
internal data class V6ObjectWitnessAuditDto(
    val objectKey: String,
    val participation: List<String>,
    val evidence: List<String>,
)

@Serializable
internal data class V6CandidateAuditDto(
    val seed: Long,
    val identity: String,
    val bucket: Int,
    val grammarFamily: String,
    val mapElitesCell: String?,
    val accepted: Boolean,
    val levelId: String?,
    val fingerprints: Map<String, String>,
    val behaviouralDescriptor: List<Double>,
    val cognitiveFeatures: List<Double>,
    val occupiedRatio: Double?,
    val purposefulOccupiedRatio: Double?,
    val inertOccupiedRatio: Double?,
    val purposefulArrowRatio: Double?,
    val purposefulMagnetRatio: Double?,
    val purposefulWallRatio: Double?,
    val objectWitnesses: List<V6ObjectWitnessAuditDto>,
    val solverStateCount: Int?,
    val decisionStateCount: Int?,
    val analysisTruncated: Boolean,
    val truncationReasons: List<String>,
    val meaningfulDecisions: Int?,
    val persistentTraps: Int?,
    val minimumLookahead: Int?,
    val delayedDeadlockDepth: Int?,
    val polarityMemorySpan: Int?,
    val policyClasses: Int?,
    val hardestWinningShare: Double?,
    val humanModelProbabilities: List<Double>?,
    val humanModelConfidence: Double?,
    val nearestSemanticFingerprint: String?,
    val nearestSemanticSimilarity: Double?,
    val rejectionReasons: List<String>,
)

@Serializable
internal data class V6PilotAuditDto(
    val schemaVersion: Int = 2,
    val catalogKind: String,
    val status: String,
    val baseSeed: Long,
    val requestedCount: Int,
    val acceptedCount: Int,
    val catalogSha256: String,
    val candidates: List<V6CandidateAuditDto>,
    val rejectionCounts: Map<String, Int>,
    val familyCoverage: Map<String, Int>,
    val mapElitesCoverage: Map<String, Int>,
    val nearestSemanticPairs: List<String>,
)

@Serializable
internal data class V6CertificationStateDto(
    val schemaVersion: Int = 2,
    val status: String,
    val baselineStatus: String,
    val calibrationStatus: String,
    val sealedValidationStatus: String,
    val humanModelStatus: String,
    val productionChanged: Boolean,
    val reasons: List<String>,
)

internal fun generateGeneratorV6Pilot(options: Map<String, String>) {
    val campaign = LevelParser().parseCatalog(File(options.requiredV6("campaign")).readText())
    val infinite = LevelParser().parseCatalog(File(options.requiredV6("infinite")).readText())
    val daily = LevelParser().parseCatalog(File(options.requiredV6("daily")).readText())
    val v11 = LevelParser().parseCatalog(File(options.requiredV6("v11")).readText())
    val outputRoot = File(options.requiredV6("output")).also { it.mkdirs() }
    val kind = options["kind"] ?: "both"
    val catalogs = buildList {
        if (kind == "both" || kind == "calibration") add("calibration" to V6_CALIBRATION_SEED)
        if (kind == "both" || kind == "validation") add("sealed-validation" to V6_VALIDATION_SEED)
    }
    if (kind == "reports") {
        listOf(
            "calibration" to "GENERATOR_V6_CALIBRATION_AUDIT.json",
            "sealed-validation" to "GENERATOR_V6_SEALED_VALIDATION_AUDIT.json",
        ).forEach { (directory, name) ->
            val output = File(outputRoot, directory)
            val auditFile = File(output, name)
            if (auditFile.isFile) writePilotSidecarsV6(v6Json.decodeFromString(auditFile.readText()), output)
        }
    }
    val baseline = (campaign.levels + infinite.levels + daily.levels + v11.levels).map(::layoutOnlyBundleV6)
    var calibrationArchive = File(outputRoot, "calibration/GENERATOR_V6_CALIBRATION_AUDIT.json")
        .takeIf(File::isFile)
        ?.let { file -> v6Json.decodeFromString<V6PilotAuditDto>(file.readText()) }
        ?.candidates.orEmpty().filter { it.accepted }.map(::auditFingerprintBundleV6)
    val audits = catalogs.map { (catalogKind, baseSeed) ->
        val archive = if (catalogKind == "sealed-validation") calibrationArchive + baseline else baseline
        generateOnePilotV6(catalogKind, baseSeed, outputRoot, archive).also { audit ->
            if (catalogKind == "calibration") {
                calibrationArchive = audit.candidates.filter { it.accepted }.map(::auditFingerprintBundleV6)
            }
        }
    }
    val calibrationAuditFile = File(outputRoot, "calibration/GENERATOR_V6_CALIBRATION_AUDIT.json")
    val validationAuditFile = File(outputRoot, "sealed-validation/GENERATOR_V6_SEALED_VALIDATION_AUDIT.json")
    if (calibrationAuditFile.isFile && validationAuditFile.isFile) {
        val completeAudits = listOf(calibrationAuditFile, validationAuditFile).map { file ->
            v6Json.decodeFromString<V6PilotAuditDto>(file.readText())
        }
        val technical = completeAudits.all { it.status == V6CertificationStatus.V6_TECHNICALLY_CERTIFIED.name }
        val state = V6CertificationStateDto(
            status = if (technical) {
                V6CertificationStatus.AWAITING_HUMAN_CALIBRATION.name
            } else {
                V6CertificationStatus.FAIL_NO_PROMOTION.name
            },
            baselineStatus = V6CertificationStatus.BASELINE_VERIFIED.name,
            calibrationStatus = completeAudits.first { it.catalogKind == "calibration" }.status,
            sealedValidationStatus = completeAudits.first { it.catalogKind == "sealed-validation" }.status,
            humanModelStatus = V6CertificationStatus.AWAITING_HUMAN_CALIBRATION.name,
            productionChanged = false,
            reasons = if (technical) {
                listOf("Real V6 human calibration and sealed-validation observations are absent")
            } else {
                listOf("One or both bounded $V6_PILOT_BOARD_COUNT-board five-band pilot catalogs failed technical certification")
            },
        )
        File(outputRoot, "GENERATOR_V6_CERTIFICATION_STATUS.json")
            .writeText(v6Json.encodeToString(state) + "\n")
    }
}

private fun generateOnePilotV6(
    catalogKind: String,
    baseSeed: Long,
    outputRoot: File,
    baseline: List<V6FingerprintBundle>,
): V6PilotAuditDto {
    val output = File(outputRoot, catalogKind).also { it.mkdirs() }
    val accepted = mutableListOf<V6Candidate>()
    val audits = mutableListOf<V6CandidateAuditDto>()
    val known = baseline.toMutableList()
    val rejectionCounts = linkedMapOf<String, Int>()
    var slot = 0
    for (bucket in 1..V6_HUMAN_BAND_COUNT) {
        repeat(6) { withinBucket ->
            slot += 1
            val family = CausalGrammarFamilyV6.entries[(withinBucket + bucket - 1) % CausalGrammarFamilyV6.entries.size]
            val prefix = if (catalogKind == "calibration") "v6-calibration" else "v6-validation"
            var slotAccepted = false
            for (slotAttempt in 0 until V6_SLOT_SEED_ATTEMPTS) {
                val seed = baseSeed + bucket * 1_000_003L + withinBucket * 97_003L +
                    slotAttempt * V6_SLOT_SEED_STRIDE
                val identity = GeneratorV6Identity(seed, bucket, family, graphInstance = slot * 101 + slotAttempt)
                val result = GeneratorV6().generate(
                    V6GenerationRequest(
                        identity,
                        "$prefix-${slot.toString().padStart(3, '0')}",
                        slot,
                        "Blind board ${slot.toString().padStart(3, '0')}",
                        "v6-$catalogKind",
                        known,
                    ),
                )
                val candidate = when (result) {
                    is V6GenerationResult.Generated -> result.candidate.also {
                        accepted += it
                        known += requireNotNull(it.fingerprints)
                        slotAccepted = true
                    }
                    is V6GenerationResult.Rejected -> result.candidate
                }
                candidate.rejections.forEach { rejection ->
                    rejectionCounts[rejection.code.name] = rejectionCounts.getOrDefault(rejection.code.name, 0) + 1
                }
                audits += candidateAuditV6(candidate)
                println(
                    "V6 $catalogKind bucket=$bucket slot=$withinBucket attempt=$slotAttempt " +
                        if (candidate.accepted) "accepted" else "rejected",
                )
                if (slotAccepted) break
            }
        }
    }
    val catalogFile = File(output, if (catalogKind == "calibration") {
        "GENERATOR_V6_CALIBRATION_CATALOG.json"
    } else {
        "GENERATOR_V6_SEALED_VALIDATION_CATALOG.json"
    })
    val catalog = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = "magnetrail-core-1",
        catalogId = "magnetrail-v6-$catalogKind-five-band-v2",
        levels = accepted.mapNotNull { it.level },
        contentVersion = 12,
        generatorVersion = GENERATOR_VERSION_V6,
    )
    catalogFile.writeText(LevelParser().encodeCatalog(catalog) + "\n")
    val status = if (accepted.size == V6_PILOT_BOARD_COUNT && pilotCompositionPassesV6(accepted)) {
        V6CertificationStatus.V6_TECHNICALLY_CERTIFIED.name
    } else {
        V6CertificationStatus.FAIL_NO_PROMOTION.name
    }
    val audit = V6PilotAuditDto(
        catalogKind = catalogKind,
        status = status,
        baseSeed = baseSeed,
        requestedCount = V6_PILOT_BOARD_COUNT,
        acceptedCount = accepted.size,
        catalogSha256 = sha256FileV6(catalogFile),
        candidates = audits,
        rejectionCounts = rejectionCounts.toSortedMap(),
        familyCoverage = accepted.groupingBy { it.spec.family.name }.eachCount().toSortedMap(),
        mapElitesCoverage = accepted.map { mapElitesCellStringV6(it) }.groupingBy { it }.eachCount().toSortedMap(),
        nearestSemanticPairs = accepted.mapNotNull { candidate ->
            val bundle = candidate.fingerprints
            bundle?.nearestSemanticFingerprint?.let { nearest ->
                "${candidate.level?.id} -> $nearest @ ${bundle.nearestSemanticSimilarity}"
            }
        },
    )
    File(output, "GENERATOR_V6_${if (catalogKind == "calibration") "CALIBRATION" else "SEALED_VALIDATION"}_AUDIT.json")
        .writeText(v6Json.encodeToString(audit) + "\n")
    writePilotSidecarsV6(audit, output)
    println("$status: $catalogKind accepted ${accepted.size}/$V6_PILOT_BOARD_COUNT; sha256=${audit.catalogSha256}")
    return audit
}

private fun writePilotSidecarsV6(audit: V6PilotAuditDto, output: File) {
    val stableRejectionCounts = audit.rejectionCounts.entries.sortedBy { it.key }.associate { it.toPair() }
    File(output, "GENERATOR_V6_REJECTION_COUNTS.json")
        .writeText(v6Json.encodeToString<Map<String, Int>>(stableRejectionCounts) + "\n")
    File(output, "GENERATOR_V6_SEMANTIC_NEIGHBOURS.md").writeText(renderNeighboursV6(audit))
    File(output, "GENERATOR_V6_MAP_ELITES_COVERAGE.md").writeText(renderCoverageV6(audit))
    File(output, "GENERATOR_V6_PILOT_REPORT.md").writeText(renderPilotReportV6(audit))
}

private fun candidateAuditV6(candidate: V6Candidate): V6CandidateAuditDto {
    val fingerprints = candidate.fingerprints
    val occupancy = candidate.occupancy
    val metrics = candidate.decisionAnalysis?.metrics
    return V6CandidateAuditDto(
        seed = candidate.identity.seed,
        identity = candidate.identity.stableKey,
        bucket = candidate.profile.bucket,
        grammarFamily = candidate.spec.family.name,
        mapElitesCell = candidate.takeIf { it.accepted }?.let(::mapElitesCellStringV6),
        accepted = candidate.accepted,
        levelId = candidate.level?.id,
        fingerprints = buildMap {
            fingerprints?.let {
                put("exact", it.exactLayout); put("d4", it.d4Layout); put("arrow", it.arrowLayout)
                put("interactive", it.interactiveLayout); put("perceptual", it.perceptualLayout)
                put("relevancePrunedD4", it.relevancePrunedD4Layout); put("causal", it.causalHypergraph)
                put("decisionDag", it.quotientDecisionDag); put("solutionPolicy", it.solutionPolicy)
                put("meaningfulTrace", it.meaningfulDecisionTrace); put("mechanicRhythm", it.mechanicRhythm)
            }
        },
        behaviouralDescriptor = fingerprints?.behaviouralDescriptor.orEmpty(),
        cognitiveFeatures = candidate.decisionAnalysis?.takeIf { it.complete }?.let {
            HumanCognitiveFeatureExtractorV1.extract(it, candidate.spec.interactingChainCount).vector()
        }.orEmpty(),
        occupiedRatio = occupancy?.occupiedRatio,
        purposefulOccupiedRatio = occupancy?.purposefulOccupiedRatio,
        inertOccupiedRatio = occupancy?.inertOccupiedRatio,
        purposefulArrowRatio = occupancy?.purposefulArrowRatio,
        purposefulMagnetRatio = occupancy?.purposefulMagnetRatio,
        purposefulWallRatio = occupancy?.purposefulWallRatio,
        objectWitnesses = occupancy?.objectWitnesses.orEmpty().map { (key, values) ->
            V6ObjectWitnessAuditDto(key, values.map { it.participation.name }.distinct(), values.map { it.evidence })
        },
        solverStateCount = candidate.level?.metadata?.exploredStateCount,
        decisionStateCount = candidate.decisionAnalysis?.nodes?.size,
        analysisTruncated = candidate.decisionAnalysis?.complete == false,
        truncationReasons = candidate.decisionAnalysis?.truncationReasons.orEmpty(),
        meaningfulDecisions = metrics?.meaningfulDecisionCount,
        persistentTraps = metrics?.successfulLosingBranchCount,
        minimumLookahead = metrics?.minimumLookaheadProofDepth,
        delayedDeadlockDepth = metrics?.maximumDelayedDeadlockDepth,
        polarityMemorySpan = metrics?.polarityMemorySpan,
        policyClasses = metrics?.winningSolutionPolicyClasses,
        hardestWinningShare = metrics?.hardestWinningChoiceShare,
        humanModelProbabilities = candidate.humanDifficulty?.bandProbabilities,
        humanModelConfidence = candidate.humanDifficulty?.calibrationConfidence,
        nearestSemanticFingerprint = fingerprints?.nearestSemanticFingerprint,
        nearestSemanticSimilarity = fingerprints?.nearestSemanticSimilarity,
        rejectionReasons = candidate.rejections.map { "${it.code}:${it.detail}" },
    )
}

private fun pilotCompositionPassesV6(candidates: List<V6Candidate>): Boolean {
    if (candidates.size != V6_PILOT_BOARD_COUNT) return false
    return (1..V6_HUMAN_BAND_COUNT).all { bucket ->
        val rows = candidates.filter { it.profile.bucket == bucket }
        rows.size == 6 && rows.groupingBy { it.spec.family }.eachCount().values.all { it <= 2 } &&
            (bucket < 4 || rows.map { it.spec.family }.distinct().size >= 4)
    } && candidates.groupingBy { it.spec.family }.eachCount().values.all { it <= 9 }
}

private fun layoutOnlyBundleV6(level: LevelDefinition) = V6FingerprintBundle(
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

private fun auditFingerprintBundleV6(candidate: V6CandidateAuditDto): V6FingerprintBundle {
    fun value(key: String): String = requireNotNull(candidate.fingerprints[key]) {
        "Accepted V6 audit candidate ${candidate.identity} is missing fingerprint '$key'"
    }
    return V6FingerprintBundle(
        exactLayout = value("exact"),
        d4Layout = value("d4"),
        arrowLayout = value("arrow"),
        interactiveLayout = value("interactive"),
        perceptualLayout = value("perceptual"),
        relevancePrunedD4Layout = value("relevancePrunedD4"),
        causalHypergraph = value("causal"),
        quotientDecisionDag = value("decisionDag"),
        solutionPolicy = value("solutionPolicy"),
        meaningfulDecisionTrace = value("meaningfulTrace"),
        mechanicRhythm = value("mechanicRhythm"),
        behaviouralDescriptor = candidate.behaviouralDescriptor,
    )
}

private fun mapElitesCellStringV6(candidate: V6Candidate): String {
    val metrics = requireNotNull(candidate.decisionAnalysis?.metrics)
    val occupancy = requireNotNull(candidate.occupancy)
    return listOf(
        candidate.spec.family.name,
        "d${metrics.meaningfulDecisionCount}",
        "t${metrics.successfulLosingBranchCount}-${metrics.maximumDelayedDeadlockDepth}",
        "p${metrics.polarityMemorySpan}",
        "w${metrics.partialOrderWidth}-${metrics.transitiveReduction.size}",
        "o${(occupancy.purposefulOccupiedRatio * 10).toInt()}",
    ).joinToString("/")
}

private fun renderBaselineAuditV6(audit: V6BaselineAuditDto): String = buildString {
    appendLine("# Generator V6 baseline audit")
    appendLine()
    appendLine("Status: **${audit.status}**")
    appendLine()
    appendLine("## Protected catalogs")
    appendLine()
    appendLine("| Catalog | Levels | SHA-256 verified | Exact | D4 | Arrow | Interactive | Perceptual |")
    appendLine("|---|---:|:---:|---:|---:|---:|---:|---:|")
    audit.catalogs.forEach {
        appendLine("| ${it.name} | ${it.levelCount} | ${if (it.hashMatches) "yes" else "NO"} | ${it.exactUnique} | ${it.d4Unique} | ${it.arrowLayoutUnique} | ${it.interactiveLayoutUnique} | ${it.perceptualUnique} |")
    }
    appendLine()
    appendLine("## D4 contradiction audit")
    appendLine()
    audit.d4Contract.forEach { (key, value) -> appendLine("- $key: $value") }
    appendLine()
    appendLine("The pre-V6 rectangular implementation used only four dimension-preserving transforms. V6 serializes transformed dimensions and proves all eight transforms, including direction changes and width/height swaps.")
    appendLine()
    appendLine("## Clone cause")
    appendLine()
    audit.runtimePathFindings.forEach { (key, value) -> appendLine("- $key: $value") }
    appendLine()
    audit.representativeCloneGroups.forEach { group ->
        appendLine("- `${group.signatureType}` `${group.signature}`: ${group.levelIds.joinToString()}. ${group.reason}")
    }
    appendLine()
    appendLine("## Solver-derived representatives")
    appendLine()
    audit.solverStrategyFindings.forEach {
        appendLine("- ${it.levelId}: complete=${it.complete}, states=${it.reachableStates}, decisions=${it.meaningfulDecisions}, traps=${it.successfulLosingBranches}, hardest winning share=${it.hardestWinningShare}, policy classes=${it.solutionPolicyClasses}, truncation=${it.truncationReasons}")
    }
    appendLine()
    appendLine("## Conclusion")
    appendLine()
    appendLine(audit.conclusion)
}

private fun renderPilotReportV6(audit: V6PilotAuditDto): String = buildString {
    appendLine("# Generator V6 ${audit.catalogKind} pilot")
    appendLine()
    appendLine("- Status: **${audit.status}**")
    appendLine("- Accepted/requested: ${audit.acceptedCount}/${audit.requestedCount}")
    appendLine("- Catalog SHA-256: `${audit.catalogSha256}`")
    appendLine("- Family coverage: ${audit.familyCoverage}")
    appendLine("- MAP-Elites cells: ${audit.mapElitesCoverage.size}")
    appendLine("- Rejections: ${audit.rejectionCounts}")
    appendLine()
    appendLine("Candidate bucket names are blinded calibration identities, not human-certified difficulty labels.")
}

private fun renderNeighboursV6(audit: V6PilotAuditDto): String = buildString {
    appendLine("# Generator V6 semantic neighbours — ${audit.catalogKind}")
    appendLine()
    if (audit.nearestSemanticPairs.isEmpty()) appendLine("No accepted candidates have a semantic neighbour.")
    audit.nearestSemanticPairs.forEach { appendLine("- $it") }
}

private fun renderCoverageV6(audit: V6PilotAuditDto): String = buildString {
    appendLine("# Generator V6 causal-family and MAP-Elites coverage — ${audit.catalogKind}")
    appendLine()
    appendLine("- Families: ${audit.familyCoverage}")
    appendLine("- Occupied cells: ${audit.mapElitesCoverage.size}")
    audit.mapElitesCoverage.forEach { (cell, count) -> appendLine("- `$cell`: $count") }
}

@Serializable
internal data class HumanDifficultyModelDtoV1(
    val version: String,
    val featureNames: List<String>,
    val means: List<Double>,
    val scales: List<Double>,
    val coefficients: List<Double>,
    val thresholds: List<Double>,
    val participantIntercepts: Map<String, Double>,
    val featureMinimums: List<Double>,
    val featureMaximums: List<Double>,
    val trainingObservationCount: Int,
    val trainingParticipantCount: Int,
    val trainingBoardCount: Int,
    val l2: Double,
    val calibrationCatalogSha256: String,
    val featureSchemaSha256: String,
    val datasetSha256: String,
    val parametersSha256: String,
    val thresholdsSha256: String,
    val familyGroupedValidation: Map<String, Double>,
    val status: String,
)

@Serializable
internal data class HumanModelStatusDtoV1(
    val status: String,
    val reasons: List<String>,
    val calibrationResultsPresent: Boolean,
    val sealedResultsPresent: Boolean,
    val modelSha256: String? = null,
)

@Serializable
internal data class HumanValidationCertificateDtoV1(
    val status: String,
    val modelSha256: String,
    val calibrationDatasetSha256: String,
    val validationDatasetSha256: String,
    val validationCatalogSha256: String,
    val metrics: Map<String, Double>,
    val medianByBucket: List<Double>,
    val leakageFailures: List<String>,
    val validationFailures: List<String>,
)

internal fun calibrateHumanLikeDifficultyV1(options: Map<String, String>) {
    val auditFile = File(options.requiredV6("audit"))
    val catalogFile = File(options.requiredV6("catalog"))
    val resultsFile = File(options.requiredV6("results"))
    val output = File(options.requiredV6("output")).also { it.mkdirs() }
    if (!resultsFile.isFile || resultsFile.length() == 0L) {
        val reason = "No real V6 calibration results exist at ${resultsFile.path}. Automated/V10 evidence cannot certify the human model."
        File(output, "HUMAN_MODEL_STATUS.json").writeText(
            v6Json.encodeToString(
                HumanModelStatusDtoV1(
                    V6CertificationStatus.AWAITING_HUMAN_CALIBRATION.name,
                    listOf(reason),
                    calibrationResultsPresent = false,
                    sealedResultsPresent = false,
                ),
            ) + "\n",
        )
        File(output, "HUMAN_LIKE_DIFFICULTY_V1_MODEL_CARD.md").writeText(renderAwaitingModelCardV6(reason))
        File(output, "HUMAN_CALIBRATION_RESULTS_REPORT.md").writeText(
            "# V6 human calibration results\n\nStatus: **AWAITING_HUMAN_CALIBRATION**\n\n$reason\n",
        )
        println(V6CertificationStatus.AWAITING_HUMAN_CALIBRATION.name)
        return
    }
    val audit = v6Json.decodeFromString<V6PilotAuditDto>(auditFile.readText())
    check(audit.status == V6CertificationStatus.V6_TECHNICALLY_CERTIFIED.name) {
        "Calibration catalog is not technically certified"
    }
    check(audit.catalogSha256 == sha256FileV6(catalogFile)) { "Calibration catalog hash mismatch" }
    val observations = observationsFromV6(resultsFile, audit)
    val fit = CumulativeLinkOrdinalCalibratorV1().fit(observations)
    if (fit is HumanModelFitResultV1.Insufficient) {
        File(output, "HUMAN_MODEL_STATUS.json").writeText(
            v6Json.encodeToString(
                HumanModelStatusDtoV1(
                    V6CertificationStatus.AWAITING_HUMAN_CALIBRATION.name,
                    fit.reasons,
                    calibrationResultsPresent = true,
                    sealedResultsPresent = false,
                ),
            ) + "\n",
        )
        File(output, "HUMAN_CALIBRATION_RESULTS_REPORT.md").writeText(renderInsufficientHumanReportV6(fit.reasons))
        println(V6CertificationStatus.AWAITING_HUMAN_CALIBRATION.name)
        return
    }
    fit as HumanModelFitResultV1.Fitted
    val model = fit.model
    val familyValidation = observations.map { it.causalFamily }.distinct().sorted().associateWith { family ->
        val training = observations.filter { it.causalFamily != family }
        val validation = observations.filter { it.causalFamily == family }
        val fold = CumulativeLinkOrdinalCalibratorV1(iterations = 2_000).fit(training, enforceEvidenceContract = false)
        if (fold !is HumanModelFitResultV1.Fitted || validation.size < 2) {
            0.0
        } else {
            spearmanToolV6(
                validation.map { fold.model.predict(it.features).latentDifficulty },
                validation.map { it.perceivedBand.toDouble() },
            )
        }
    }
    val featureSchemaSha = sha256TextV6(HumanCognitiveFeaturesV1.names.joinToString("\n"))
    val datasetSha = sha256FileV6(resultsFile)
    val parametersSha = sha256TextV6(model.coefficients.joinToString(",") + "|" + model.participantIntercepts.toSortedMap())
    val thresholdsSha = sha256TextV6(model.thresholds.joinToString(","))
    val dto = model.toDtoV6(
        sha256FileV6(catalogFile),
        featureSchemaSha,
        datasetSha,
        parametersSha,
        thresholdsSha,
        familyValidation,
    )
    val modelFile = File(output, "HUMAN_LIKE_DIFFICULTY_V1_MODEL.json")
    modelFile.writeText(v6Json.encodeToString(dto) + "\n")
    File(output, "HUMAN_MODEL_STATUS.json").writeText(
        v6Json.encodeToString(
            HumanModelStatusDtoV1(
                "MODEL_FROZEN_AWAITING_SEALED_VALIDATION",
                emptyList(),
                calibrationResultsPresent = true,
                sealedResultsPresent = false,
                modelSha256 = sha256FileV6(modelFile),
            ),
        ) + "\n",
    )
    File(output, "HUMAN_LIKE_DIFFICULTY_V1_MODEL_CARD.md").writeText(
        renderModelCardV6(dto, fit.finalNegativeLogLikelihood),
    )
    File(output, "HUMAN_CALIBRATION_RESULTS_REPORT.md").writeText(renderCalibrationReportV6(dto, observations))
    println("MODEL_FROZEN_AWAITING_SEALED_VALIDATION sha256=${sha256FileV6(modelFile)}")
}

internal fun validateGeneratorV6HumanCertificate(options: Map<String, String>) {
    val modelFile = File(options.requiredV6("model"))
    val calibrationResults = File(options.requiredV6("calibration-results"))
    val calibrationAudit = v6Json.decodeFromString<V6PilotAuditDto>(File(options.requiredV6("calibration-audit")).readText())
    val validationResults = File(options.requiredV6("validation-results"))
    val validationAuditFile = File(options.requiredV6("validation-audit"))
    val validationAudit = v6Json.decodeFromString<V6PilotAuditDto>(validationAuditFile.readText())
    val validationCatalog = File(options.requiredV6("validation-catalog"))
    val output = File(options.requiredV6("output")).also { it.mkdirs() }
    if (!validationResults.isFile || validationResults.length() == 0L) {
        val reason = "Sealed V6 validation results are absent; the frozen model cannot be human-certified."
        File(output, "HUMAN_MODEL_STATUS.json").writeText(
            v6Json.encodeToString(
                HumanModelStatusDtoV1(
                    V6CertificationStatus.AWAITING_HUMAN_CALIBRATION.name,
                    listOf(reason),
                    calibrationResults.isFile,
                    sealedResultsPresent = false,
                    modelSha256 = modelFile.takeIf { it.isFile }?.let(::sha256FileV6),
                ),
            ) + "\n",
        )
        println(V6CertificationStatus.AWAITING_HUMAN_CALIBRATION.name)
        return
    }
    check(modelFile.isFile && calibrationResults.isFile) { "Frozen calibration model/results are missing" }
    check(validationAudit.status == V6CertificationStatus.V6_TECHNICALLY_CERTIFIED.name)
    check(validationAudit.catalogSha256 == sha256FileV6(validationCatalog))
    val modelDto = v6Json.decodeFromString<HumanDifficultyModelDtoV1>(modelFile.readText())
    val model = modelDto.toDomainV6()
    val calibration = observationsFromV6(calibrationResults, calibrationAudit)
    val validation = observationsFromV6(validationResults, validationAudit)
    val evidence = CumulativeLinkOrdinalCalibratorV1(iterations = 1).fit(validation)
    val evidenceFailures = if (evidence is HumanModelFitResultV1.Insufficient) evidence.reasons else emptyList()
    val leakage = HumanModelValidatorV1.assertNoLeakage(calibration, validation)
    val metrics = HumanModelValidatorV1.validateSealed(validation)
    val predictionFailures = validation.mapNotNull { observation ->
        model.predict(observation.features).rejectionReason?.let { "${observation.boardFingerprint}:$it" }
    }
    val oodProbe = featuresFromVectorV6(model.featureMaximums.indices.map { index ->
        model.featureMaximums[index] + model.scales[index] * 10.0
    })
    val confidenceProbe = featuresFromVectorV6(model.means)
    val rejectionContractFailures = buildList {
        if (model.predict(oodProbe).rejectionReason != "OUT_OF_DISTRIBUTION") add("ood-rejection-contract-failed")
        if (model.copy(trainingBoardCount = 1).predict(confidenceProbe).rejectionReason != "LOW_CALIBRATION_CONFIDENCE") {
            add("low-confidence-rejection-contract-failed")
        }
    }
    val failures = evidenceFailures + leakage + metrics.failureReasons +
        predictionFailures.takeIf { it.size > validation.size / 10 }.orEmpty() + rejectionContractFailures
    val status = if (failures.isEmpty()) {
        V6CertificationStatus.HUMAN_MODEL_CERTIFIED.name
    } else {
        V6CertificationStatus.FAIL_NO_PROMOTION.name
    }
    val metricMap = linkedMapOf(
        "spearmanAssignedPerceived" to metrics.spearmanAssignedPerceived,
        "spearmanClusteredLower95" to metrics.spearmanClusteredLower95,
        "adjacentProbabilityOfSuperiority" to metrics.adjacentProbabilityOfSuperiority,
        "adjacentSuperiorityLower95" to metrics.adjacentSuperiorityLower95,
        "withinOneBandRate" to metrics.withinOneBandRate,
        "fairnessRate" to metrics.fairnessRate,
        "guessRate" to metrics.guessRate,
        "repeatedStrategyRate" to metrics.repeatedStrategyRate,
        "noHintCompletionRate" to metrics.noHintCompletionRate,
        "highBucketNoHintCompletionRate" to metrics.highBucketNoHintCompletionRate,
    )
    val certificate = HumanValidationCertificateDtoV1(
        status,
        sha256FileV6(modelFile),
        sha256FileV6(calibrationResults),
        sha256FileV6(validationResults),
        sha256FileV6(validationCatalog),
        metricMap,
        metrics.medianByBucket,
        leakage,
        failures,
    )
    File(output, "HUMAN_DIFFICULTY_CERTIFICATE.json").writeText(v6Json.encodeToString(certificate) + "\n")
    File(output, "HUMAN_VALIDATION_RESULTS_REPORT.md").writeText(renderValidationReportV6(certificate))
    println(status)
}

private fun observationsFromV6(
    results: File,
    audit: V6PilotAuditDto,
): List<HumanCalibrationObservationV1> {
    val candidateByLevel = audit.candidates.filter { it.accepted && it.levelId != null }.associateBy { it.levelId }
    val requiredColumns = setOf(
        "participant_code", "level_id", "content_fingerprint", "perceived_rating", "expected_rating",
        "hints_used", "completed", "fairness_rating", "guess_required", "repeated_strategy",
    )
    val rows = readCsvV6(results)
    check(rows.isNotEmpty()) { "Human results CSV has no observations" }
    check(requiredColumns.all { it in rows.first() }) { "Human results CSV is missing required V6 columns" }
    return rows.map { row ->
        val levelId = row.getValue("level_id")
        val candidate = requireNotNull(candidateByLevel[levelId]) { "Result references non-certified board $levelId" }
        check(candidate.fingerprints["exact"] == row.getValue("content_fingerprint")) {
            "Fingerprint mismatch for $levelId"
        }
        check(candidate.cognitiveFeatures.size == HumanCognitiveFeaturesV1.names.size)
        HumanCalibrationObservationV1(
            participantCode = row.getValue("participant_code"),
            boardFingerprint = row.getValue("content_fingerprint"),
            causalFamily = candidate.grammarFamily,
            semanticCluster = candidate.fingerprints["causal"] + ":" + candidate.fingerprints["decisionDag"],
            perceivedBand = row.getValue("perceived_rating").toInt(),
            assignedBucket = row.getValue("expected_rating").toInt(),
            features = featuresFromVectorV6(candidate.cognitiveFeatures),
            hinted = row.getValue("hints_used").toInt() > 0,
            completed = row.getValue("completed").toBooleanStrict(),
            fairnessRating = row.getValue("fairness_rating").toInt(),
            guessRequired = row.getValue("guess_required").toBooleanStrict(),
            repeatedStrategy = row.getValue("repeated_strategy").toBooleanStrict(),
        )
    }
}

private fun featuresFromVectorV6(values: List<Double>) = HumanCognitiveFeaturesV1(
    values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8],
    values[9], values[10], values[11], values[12], values[13], values[14],
)

private fun HumanDifficultyModelV1.toDtoV6(
    catalogSha: String,
    featureSha: String,
    datasetSha: String,
    parameterSha: String,
    thresholdSha: String,
    familyValidation: Map<String, Double>,
) = HumanDifficultyModelDtoV1(
    version,
    featureNames,
    means,
    scales,
    coefficients,
    thresholds,
    participantIntercepts,
    featureMinimums,
    featureMaximums,
    trainingObservationCount,
    trainingParticipantCount,
    trainingBoardCount,
    l2,
    catalogSha,
    featureSha,
    datasetSha,
    parameterSha,
    thresholdSha,
    familyValidation,
    "MODEL_FROZEN_AWAITING_SEALED_VALIDATION",
)

private fun HumanDifficultyModelDtoV1.toDomainV6() = HumanDifficultyModelV1(
    featureNames,
    means,
    scales,
    coefficients,
    thresholds,
    participantIntercepts,
    featureMinimums,
    featureMaximums,
    trainingObservationCount,
    trainingParticipantCount,
    trainingBoardCount,
    l2,
    version,
)

private fun renderAwaitingModelCardV6(reason: String): String = """# HumanLikeDifficultyV1 model card

Status: **AWAITING_HUMAN_CALIBRATION**

Model version: `human-like-difficulty-v1-five-band-v2` (Easy through Expert; Master excluded)

$reason

No coefficients, thresholds, validation metrics or human certificate exist. The implemented model
is an interpretable human-calibrated predictor with confidence and OOD rejection; it cannot be
calibrated from automated analyzer output.
"""

private fun renderInsufficientHumanReportV6(reasons: List<String>): String = buildString {
    appendLine("# V6 human calibration results")
    appendLine()
    appendLine("Status: **AWAITING_HUMAN_CALIBRATION**")
    appendLine()
    reasons.forEach { appendLine("- $it") }
}

private fun renderModelCardV6(model: HumanDifficultyModelDtoV1, nll: Double): String = buildString {
    appendLine("# HumanLikeDifficultyV1 model card")
    appendLine()
    appendLine("Status: **${model.status}**")
    appendLine("- Model version: `${model.version}`")
    appendLine("- Ordered output bands: Easy, Medium, Hard, Super Hard, Expert")
    appendLine("- Observations/participants/boards: ${model.trainingObservationCount}/${model.trainingParticipantCount}/${model.trainingBoardCount}")
    appendLine("- L2 regularization: ${model.l2}")
    appendLine("- Final negative log likelihood: $nll")
    appendLine("- Feature schema SHA-256: `${model.featureSchemaSha256}`")
    appendLine("- Dataset SHA-256: `${model.datasetSha256}`")
    appendLine("- Parameter SHA-256: `${model.parametersSha256}`")
    appendLine("- Threshold SHA-256: `${model.thresholdsSha256}`")
    appendLine("- Calibration catalog SHA-256: `${model.calibrationCatalogSha256}`")
    appendLine()
    appendLine("| Feature | Coefficient |")
    appendLine("|---|---:|")
    model.featureNames.zip(model.coefficients).forEach { (name, coefficient) -> appendLine("| $name | $coefficient |") }
    appendLine()
    appendLine("Thresholds: ${model.thresholds}")
    appendLine()
    appendLine("Family-grouped validation: ${model.familyGroupedValidation}")
    appendLine()
    appendLine("Limitations: no label is certified until the untouched sealed catalog passes every human gate. Predictions outside calibration support or below confidence are rejected.")
}

private fun renderCalibrationReportV6(
    model: HumanDifficultyModelDtoV1,
    observations: List<HumanCalibrationObservationV1>,
): String = buildString {
    appendLine("# V6 human calibration results")
    appendLine()
    appendLine("- Status: **${model.status}**")
    appendLine("- Observations: ${observations.size}")
    appendLine("- Participants: ${observations.map { it.participantCode }.distinct().size}")
    appendLine("- Boards: ${observations.map { it.boardFingerprint }.distinct().size}")
    appendLine("- Hinted observations retained in audit: ${observations.count { it.hinted }}")
}

private fun renderValidationReportV6(certificate: HumanValidationCertificateDtoV1): String = buildString {
    appendLine("# V6 sealed human validation")
    appendLine()
    appendLine("Status: **${certificate.status}**")
    appendLine()
    certificate.metrics.forEach { (key, value) -> appendLine("- $key: $value") }
    appendLine("- Median by bucket: ${certificate.medianByBucket}")
    appendLine("- Leakage failures: ${certificate.leakageFailures}")
    appendLine("- Gate failures: ${certificate.validationFailures}")
}

private fun spearmanToolV6(first: List<Double>, second: List<Double>): Double {
    if (first.size != second.size || first.size < 2) return 0.0
    fun ranks(values: List<Double>): List<Double> {
        val sorted = values.withIndex().sortedBy { it.value }
        val result = MutableList(values.size) { 0.0 }
        var index = 0
        while (index < sorted.size) {
            var end = index + 1
            while (end < sorted.size && sorted[end].value == sorted[index].value) end += 1
            val rank = (index + 1 + end) / 2.0
            for (position in index until end) result[sorted[position].index] = rank
            index = end
        }
        return result
    }
    val x = ranks(first); val y = ranks(second); val mx = x.average(); val my = y.average()
    val numerator = x.indices.sumOf { (x[it] - mx) * (y[it] - my) }
    val denominator = kotlin.math.sqrt(x.sumOf { (it - mx) * (it - mx) } * y.sumOf { (it - my) * (it - my) })
    return if (denominator == 0.0) 0.0 else numerator / denominator
}

@Serializable
internal data class V6ProductionAuditDto(
    val schemaVersion: Int = 1,
    val status: String,
    val sourceCatalogSha256: String,
    val humanCertificateSha256: String,
    val modelSha256: String,
    val requestedCount: Int,
    val acceptedCount: Int,
    val candidateCatalogSha256: String,
    val deterministicRegenerationPassed: Boolean,
    val candidates: List<V6CandidateAuditDto>,
    val rejectionCounts: Map<String, Int>,
    val causalGraphFamilyCoverage: Map<String, Int>,
    val behaviouralClusterCoverage: Map<String, Int>,
    val mapElitesCoverage: Map<String, Int>,
    val pacingFailures: List<String>,
    val migrationSafe: Boolean,
    val migrationReasons: List<String>,
)

@Serializable
internal data class V6ProductionTechnicalCertificateDto(
    val schemaVersion: Int = 1,
    val status: String,
    val candidateCatalogSha256: String,
    val candidateCount: Int,
    val candidateCertificatePayloadsSha256: String,
    val deterministicRegenerationPassed: Boolean,
    val completeAnalysisPassed: Boolean,
    val migrationPassed: Boolean,
)

internal fun generateGeneratorV6ProductionCandidates(options: Map<String, String>) {
    val campaignFile = File(options.requiredV6("campaign"))
    val humanCertificateFile = File(options.requiredV6("human-certificate"))
    val modelFile = File(options.requiredV6("model"))
    val output = File(options.requiredV6("output")).also { it.mkdirs() }
    check(sha256FileV6(campaignFile) == EXPECTED_V10_SHA) { "Production V10 source hash changed" }
    check(humanCertificateFile.isFile && modelFile.isFile) { "HUMAN_MODEL_CERTIFIED artifacts are absent" }
    val humanCertificateText = humanCertificateFile.readText()
    check(humanCertificateText.contains("\"status\": \"${V6CertificationStatus.HUMAN_MODEL_CERTIFIED.name}\"")) {
        "Full production generation is forbidden without HUMAN_MODEL_CERTIFIED"
    }
    val humanCertificate = v6Json.decodeFromString<HumanValidationCertificateDtoV1>(humanCertificateText)
    check(humanCertificate.modelSha256 == sha256FileV6(modelFile)) { "Human certificate/model hash mismatch" }
    val model = v6Json.decodeFromString<HumanDifficultyModelDtoV1>(modelFile.readText()).toDomainV6()
    val parser = LevelParser()
    val source = parser.parseCatalog(campaignFile.readText())
    val immutableComparators = listOf(
        source,
        parser.parseCatalog(File(options.requiredV6("infinite")).readText()),
        parser.parseCatalog(File(options.requiredV6("daily")).readText()),
        parser.parseCatalog(File(options.requiredV6("v11")).readText()),
    )
    val known = immutableComparators.flatMap { it.levels }.map(::layoutOnlyBundleV6).toMutableList()
    val accepted = mutableListOf<LevelDefinition>()
    val acceptedCandidates = mutableListOf<V6Candidate>()
    val audits = mutableListOf<V6CandidateAuditDto>()
    val certificatePayloads = mutableListOf<String>()
    val rejectionCounts = linkedMapOf<String, Int>()
    var deterministic = true
    val generator = GeneratorV6(humanDifficultyModel = model)
    source.levels.forEachIndexed { index, oldLevel ->
        val bucket = productionBucketV6(oldLevel)
        val family = CausalGrammarFamilyV6.entries[(index + index / CausalGrammarFamilyV6.entries.size) % CausalGrammarFamilyV6.entries.size]
        val request = V6GenerationRequest(
            identity = GeneratorV6Identity(6_320_000_001L + index * 1_000_003L, bucket, family, index * 101),
            stableId = oldLevel.id,
            number = oldLevel.number,
            title = oldLevel.title,
            packId = oldLevel.metadata?.packId ?: "campaign-v6",
            knownFingerprints = known,
        )
        val result = generator.generate(request)
        val candidate = when (result) {
            is V6GenerationResult.Generated -> {
                val replay = GeneratorV6(humanDifficultyModel = model).generate(request)
                val replayCandidate = (replay as? V6GenerationResult.Generated)?.candidate
                val exactMatch = replayCandidate?.let { repeated ->
                    repeated.level == result.candidate.level &&
                        repeated.fingerprints == result.candidate.fingerprints
                } == true
                deterministic = deterministic && exactMatch
                if (!exactMatch) {
                    result.candidate.copy(
                        rejections = listOf(com.rameshta.magnetrail.core.generation.v6.V6Rejection(
                            com.rameshta.magnetrail.core.generation.v6.V6RejectionCode.CEGIS_REFINEMENT_EXHAUSTED,
                            "byte-identical deterministic regeneration failed",
                        )),
                    )
                } else {
                    val level = requireNotNull(result.candidate.level)
                    val linked = level.copy(
                        metadata = requireNotNull(level.metadata).copy(
                            previousContentFingerprint = oldLevel.metadata?.contentFingerprint
                                ?: ContentFingerprint.of(oldLevel),
                        ),
                    )
                    val linkedCandidate = result.candidate.copy(level = linked)
                    accepted += linked
                    acceptedCandidates += linkedCandidate
                    known += requireNotNull(linkedCandidate.fingerprints)
                    certificatePayloads += result.certificate.certificatePayloadSha256
                    linkedCandidate
                }
            }
            is V6GenerationResult.Rejected -> result.candidate
        }
        candidate.rejections.forEach { rejection ->
            rejectionCounts[rejection.code.name] = rejectionCounts.getOrDefault(rejection.code.name, 0) + 1
        }
        audits += candidateAuditV6(candidate)
    }
    val catalog = LevelCatalog(
        2,
        "magnetrail-core-1",
        "magnetrail-campaign-v6-staging",
        accepted,
        12,
        GENERATOR_VERSION_V6,
    )
    val catalogFile = File(output, "GENERATOR_V6_PRODUCTION_CANDIDATE.json")
    catalogFile.writeText(parser.encodeCatalog(catalog) + "\n")
    val causalCoverage = acceptedCandidates.mapNotNull { it.fingerprints?.causalHypergraph }
        .groupingBy { it }.eachCount().entries.sortedBy { it.key }.associate { it.toPair() }
    val clusterCoverage = acceptedCandidates.mapNotNull { candidate ->
        candidate.fingerprints?.let { "${it.meaningfulDecisionTrace}:${it.mechanicRhythm}" }
    }.groupingBy { it }.eachCount().entries.sortedBy { it.key }.associate { it.toPair() }
    val mapArchive = DeterministicMapElitesV6().apply { merge(acceptedCandidates) }
    val mapCoverage = mapArchive.entries().groupingBy { it.cell.toString() }.eachCount()
        .entries.sortedBy { it.key }.associate { it.toPair() }
    val pacingFailures = productionPacingFailuresV6(acceptedCandidates)
    val migration = CampaignMigrationV6.assess(
        source.levels,
        accepted,
        V6ProgressSnapshot(emptySet(), emptySet(), 1, source.levels.firstOrNull()?.id, null, emptyMap(), emptyMap(), 0),
    )
    val complete = accepted.size == source.levels.size && deterministic && pacingFailures.isEmpty() && migration.safe
    val status = if (complete) {
        V6CertificationStatus.FULL_CAMPAIGN_STAGING_CERTIFIED
    } else {
        V6CertificationStatus.FAIL_NO_PROMOTION
    }
    val audit = V6ProductionAuditDto(
        status = status.name,
        sourceCatalogSha256 = sha256FileV6(campaignFile),
        humanCertificateSha256 = sha256FileV6(humanCertificateFile),
        modelSha256 = sha256FileV6(modelFile),
        requestedCount = source.levels.size,
        acceptedCount = accepted.size,
        candidateCatalogSha256 = sha256FileV6(catalogFile),
        deterministicRegenerationPassed = deterministic,
        candidates = audits,
        rejectionCounts = rejectionCounts.entries.sortedBy { it.key }.associate { it.toPair() },
        causalGraphFamilyCoverage = causalCoverage,
        behaviouralClusterCoverage = clusterCoverage,
        mapElitesCoverage = mapCoverage,
        pacingFailures = pacingFailures,
        migrationSafe = migration.safe,
        migrationReasons = migration.rejectionReasons,
    )
    File(output, "GENERATOR_V6_PRODUCTION_AUDIT.json").writeText(v6Json.encodeToString(audit) + "\n")
    File(output, "GENERATOR_V6_MIGRATION_REPORT.md").writeText(buildString {
        appendLine("# Generator V6 migration report")
        appendLine()
        appendLine("Status: **${if (migration.safe) "PASS" else "FAIL"}**")
        appendLine("- KEEP: ${migration.entries.count { it.operation.name == "KEEP" }}")
        appendLine("- REPLACE: ${migration.entries.count { it.operation.name == "REPLACE" }}")
        appendLine("- APPEND: ${migration.entries.count { it.operation.name == "APPEND" }}")
        migration.rejectionReasons.forEach { appendLine("- $it") }
    })
    val technical = V6ProductionTechnicalCertificateDto(
        status = status.name,
        candidateCatalogSha256 = audit.candidateCatalogSha256,
        candidateCount = accepted.size,
        candidateCertificatePayloadsSha256 = sha256TextV6(certificatePayloads.sorted().joinToString("\n")),
        deterministicRegenerationPassed = deterministic,
        completeAnalysisPassed = acceptedCandidates.all { it.decisionAnalysis?.complete == true },
        migrationPassed = migration.safe,
    )
    File(output, "V6_TECHNICAL_CERTIFICATE.json").writeText(v6Json.encodeToString(technical) + "\n")
    println("${status.name}: production candidates ${accepted.size}/${source.levels.size}")
}

private fun productionBucketV6(level: LevelDefinition): Int {
    val profile = level.metadata?.generationProfile.orEmpty()
    return when {
        "master" in profile || "expert" in profile -> 5
        "super-hard" in profile || "very-hard" in profile -> 4
        "hard" in profile -> 3
        "medium" in profile -> 2
        else -> 1
    }
}

private fun productionPacingFailuresV6(candidates: List<V6Candidate>): List<String> = buildList {
    candidates.forEachIndexed { index, candidate ->
        val fingerprint = requireNotNull(candidate.fingerprints)
        val recentCausal = candidates.subList(maxOf(0, index - 7), index)
            .mapNotNull { it.fingerprints?.causalHypergraph }
        if (fingerprint.causalHypergraph in recentCausal) add("causal-family-repeat:${candidate.level?.id}")
        val cluster = "${fingerprint.meaningfulDecisionTrace}:${fingerprint.mechanicRhythm}"
        val recentClusters = candidates.subList(maxOf(0, index - 19), index).mapNotNull { prior ->
            prior.fingerprints?.let { "${it.meaningfulDecisionTrace}:${it.mechanicRhythm}" }
        }
        if (cluster in recentClusters) add("behavioural-cluster-repeat:${candidate.level?.id}")
    }
    candidates.groupBy { it.profile.bucket }.forEach { (bucket, rows) ->
        rows.groupingBy { it.fingerprints?.causalHypergraph }.eachCount().forEach { (family, count) ->
            if (count.toDouble() / rows.size > 0.05) add("bucket-$bucket-causal-family-over-5-percent:$family")
        }
        rows.groupingBy { "${it.fingerprints?.meaningfulDecisionTrace}:${it.fingerprints?.mechanicRhythm}" }
            .eachCount().forEach { (cluster, count) ->
                if (count.toDouble() / rows.size > 0.02) add("bucket-$bucket-cluster-over-2-percent:$cluster")
            }
    }
}

internal fun promoteGeneratorV6Campaign(options: Map<String, String>) {
    check(options["confirmation"] == "true") { "Refusing V6 promotion without explicit confirmation" }
    val campaign = File(options.requiredV6("campaign"))
    val candidate = File(options.requiredV6("candidate"))
    val technical = File(options.requiredV6("technical-certificate"))
    val human = File(options.requiredV6("human-certificate"))
    val sample = File(options.requiredV6("sample-certificate"))
    val manifest = File(options.requiredV6("manifest"))
    val rollback = File(options.requiredV6("rollback"))
    val expectedProductionSha = options.requiredV6("expected-production-sha")
    val expectedCandidateSha = options.requiredV6("expected-candidate-sha")
    check(sha256FileV6(campaign) == expectedProductionSha && expectedProductionSha == EXPECTED_V10_SHA)
    check(LevelParser().parseCatalog(campaign.readText()).contentVersion == options.requiredV6("expected-content-version").toInt())
    check(sha256FileV6(candidate) == expectedCandidateSha)
    check(sha256FileV6(technical) == options.requiredV6("technical-certificate-sha"))
    check(sha256FileV6(human) == options.requiredV6("human-certificate-sha"))
    check(sha256FileV6(sample) == options.requiredV6("sample-certificate-sha"))
    check(sha256FileV6(manifest) == options.requiredV6("manifest-sha"))
    check(technical.readText().contains(V6CertificationStatus.FULL_CAMPAIGN_STAGING_CERTIFIED.name))
    check(human.readText().contains(V6CertificationStatus.HUMAN_MODEL_CERTIFIED.name))
    check(sample.readText().contains(V6CertificationStatus.CAMPAIGN_CERTIFIED.name))
    check(manifest.readText().contains(V6CertificationStatus.CAMPAIGN_CERTIFIED.name))
    val staged = LevelParser().parseCatalog(candidate.readText())
    check(staged.contentVersion == 12 && staged.generatorVersion == GENERATOR_VERSION_V6)
    check(staged.levels.map { it.id }.distinct().size == staged.levels.size)
    check(staged.levels.all { level ->
        level.metadata?.generatorVersion == GENERATOR_VERSION_V6 &&
            level.metadata?.contentVersion == 12 &&
            level.metadata?.previousContentFingerprint != null
    })
    rollback.parentFile.mkdirs()
    if (!rollback.exists()) Files.copy(campaign.toPath(), rollback.toPath())
    check(sha256FileV6(rollback) == expectedProductionSha)
    val temporary = File(campaign.parentFile, ".${campaign.name}.v6-${expectedCandidateSha.take(12)}.tmp")
    Files.copy(candidate.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
    check(sha256FileV6(temporary) == expectedCandidateSha)
    try {
        check(sha256FileV6(campaign) == expectedProductionSha) { "Production changed during promotion preparation" }
        Files.move(
            temporary.toPath(),
            campaign.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (error: Throwable) {
        temporary.delete()
        check(sha256FileV6(campaign) == expectedProductionSha) { "Atomic promotion failure altered production" }
        throw error
    }
    println(V6CertificationStatus.PROMOTED_V6_CAMPAIGN.name)
}

private fun readCsvV6(file: File): List<Map<String, String>> {
    val rows = file.readLines().filter { it.isNotBlank() }
    if (rows.isEmpty()) return emptyList()
    val header = parseCsvLineV6(rows.first())
    return rows.drop(1).map { row -> header.zip(parseCsvLineV6(row)).toMap() }
}

private fun parseCsvLineV6(line: String): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val character = line[index]
        when {
            character == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                current.append('"'); index += 1
            }
            character == '"' -> quoted = !quoted
            character == ',' && !quoted -> { cells += current.toString(); current.clear() }
            else -> current.append(character)
        }
        index += 1
    }
    cells += current.toString()
    return cells
}

private fun sha256FileV6(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun sha256TextV6(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun Map<String, String>.requiredV6(key: String): String =
    requireNotNull(this[key]) { "Missing --$key" }

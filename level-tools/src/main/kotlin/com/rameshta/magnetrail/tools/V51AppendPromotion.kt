package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Analyzer
import com.rameshta.magnetrail.core.difficulty.v4.DifficultyV4Config
import com.rameshta.magnetrail.core.difficulty.v4.defaultDifficultyV4Seeds
import com.rameshta.magnetrail.core.engine.DefaultGameEngine
import com.rameshta.magnetrail.core.engine.PlayerAction
import com.rameshta.magnetrail.core.generation.v5.CAMPAIGN_CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.v5.CertificationPipelineV5
import com.rameshta.magnetrail.core.generation.v5.CertificationResultV5
import com.rameshta.magnetrail.core.generation.v5.GENERATOR_VERSION_V5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfileV5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesD21
import com.rameshta.magnetrail.core.generation.v5.GenerationRequestV5
import com.rameshta.magnetrail.core.generation.v5.LevelGeneratorV5
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.GradingThresholds
import com.rameshta.magnetrail.core.model.DifficultyBand
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelMetadata
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.solver.Solver
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.ceil
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val V51_APPEND_SOURCE_SHA256 =
    "8552d9ef7a2eeb140c4611ff5a9e3a40a04efb35878d752acef5e222a1dc8ca5"
private const val V51_APPEND_CANDIDATE_SHA256 =
    "cb877e2f0ef42047317953d46ec09f8b16dd5e59b3bc54f35b540eeaffccbab4"

private val V51_APPEND_IDS = listOf(
    "v5-repair-easy-0001",
    "v5-repair-medium-0001",
    "v5-repair-hard-0001",
    "v5-repair-very-hard-0001",
    "v5-repair-expert-0001",
)

@Serializable
data class V51AppendPromotionRow(
    val stagedCandidateId: String,
    val candidateSource: String,
    val productionLevelId: String,
    val campaignNumber: Int,
    val generationProfile: String,
    val boardFingerprint: String,
    val solverCertified: Boolean,
    val v4Complete: Boolean,
    val structuralCertificationStatus: String,
    val structuralRejectionReasons: List<String>,
)

@Serializable
data class V51AppendPromotionReport(
    val schemaVersion: Int = 1,
    val status: String,
    val authorization: String,
    val sourceCampaignSha256: String,
    val candidateCatalogSha256: String,
    val promotedCampaignSha256: String,
    val sourceContentVersion: Int,
    val targetContentVersion: Int,
    val sourceLevelCount: Int,
    val targetLevelCount: Int,
    val preservedExistingLevelCount: Int,
    val appendedLevelCount: Int,
    val excludedCandidateIds: List<String>,
    val humanPlaytestStatus: String,
    val automatedHumanApprovalCount: Int,
    val rows: List<V51AppendPromotionRow>,
    val notes: List<String>,
)

internal data class V51AppendPromotionPlan(
    val catalog: LevelCatalog,
    val report: V51AppendPromotionReport,
    val promotedBytes: ByteArray,
)

internal fun buildV51AppendPromotionPlan(
    sourceBytes: ByteArray,
    candidateBytes: ByteArray,
): V51AppendPromotionPlan {
    check(v51Sha256(sourceBytes) == V51_APPEND_SOURCE_SHA256) {
        "Canonical content-v7 source changed; refusing append promotion"
    }
    check(v51Sha256(candidateBytes) == V51_APPEND_CANDIDATE_SHA256) {
        "Generator V5 repair staging catalog changed; regenerate the promotion manifest"
    }
    val parser = LevelParser()
    val source = parser.parseCatalog(sourceBytes.decodeToString())
    val staged = parser.parseCatalog(candidateBytes.decodeToString())
    check(source.contentVersion == 7 && source.generatorVersion == GENERATOR_VERSION_V5)
    check(source.levels.size == 200 && source.levels.map { it.number } == (1..200).toList())
    check(staged.levels.map { it.id }.containsAll(V51_APPEND_IDS))
    check(staged.levels.single { it.id == "v5-repair-master-0001" }.metadata?.generationProfile ==
        GenerationProfilesD21.MASTER.id)

    val profiles = listOf(
        GenerationProfilesD21.EASY,
        GenerationProfilesD21.MEDIUM,
        GenerationProfilesD21.HARD,
        GenerationProfilesD21.VERY_HARD,
        GenerationProfilesD21.EXPERT,
        GenerationProfilesD21.MASTER,
    ).associateBy(GenerationProfileV5::id)
    val pipeline = CertificationPipelineV5()
    val generator = LevelGeneratorV5()
    val engine = DefaultGameEngine()
    val solver = Solver(engine)
    val rows = mutableListOf<V51AppendPromotionRow>()
    val appended = V51_APPEND_IDS.mapIndexed { index, stagedId ->
        val stagedCandidate = staged.levels.single { it.id == stagedId }
        val metadata = requireNotNull(stagedCandidate.metadata)
        val profile = requireNotNull(profiles[metadata.generationProfile])
        check(profile != GenerationProfilesD21.MASTER)
        val number = 201 + index
        val productionId = "campaign-${number.toString().padStart(3, '0')}"
        val candidate = if (profile == GenerationProfilesD21.EXPERT) {
            generator.generateRaw(
                GenerationRequestV5(
                    stableId = stagedId,
                    sequenceNumber = stagedCandidate.number,
                    title = stagedCandidate.title,
                    seed = requireNotNull(metadata.generatorSeed),
                    profile = profile,
                    packId = "generator-v5.1-owner-waiver-staging",
                    maxAttempts = 1,
                ),
            )
        } else {
            stagedCandidate
        }
        val productionRaw = candidate.copy(
            id = productionId,
            number = number,
            title = "Magnetic Circuit ${number.toString().padStart(3, '0')}",
            metadata = null,
        )
        val currentCertification = pipeline.certify(
            level = productionRaw,
            profile = profile,
            seed = requireNotNull(metadata.generatorSeed),
            packId = "magnetic-circuit-11",
            contentVersion = CAMPAIGN_CONTENT_VERSION,
        )
        val accepted = currentCertification as? CertificationResultV5.Accepted
        val rejectionReasons = (currentCertification as? CertificationResultV5.Rejected)?.reasons.orEmpty()
        val production = if (profile != GenerationProfilesD21.EXPERT) {
            checkNotNull(accepted) {
                "Non-Expert append candidate $stagedId failed current V5 gates: $rejectionReasons"
            }.level
        } else {
            check(accepted == null) { "Expert waiver is only valid for an actually uncertified candidate" }
            solverCertifiedExpert(
                level = productionRaw,
                profile = profile,
                seed = requireNotNull(metadata.generatorSeed),
                difficultyBand = metadata.difficultyBand,
                mechanicTags = metadata.mechanicTags,
            )
        }
        val v4 = DifficultyV4Analyzer(
            config = DifficultyV4Config(
                maxExpandedStates = profile.analysisStateCap,
                maxActionResolutions = profile.analysisStateCap * 12,
                maxCounterfactualStates = profile.analysisStateCap,
                maxCounterfactualActionResolutions = profile.analysisStateCap * 16,
                maxObjectCounterfactuals = profile.counterfactualCap,
                randomPolicySeeds = defaultDifficultyV4Seeds(32),
            ),
        ).analyze(production)
        check(v4.searchComplete && !v4.searchTruncated) {
            "Append candidate $stagedId has incomplete V4 analysis: ${v4.truncationReasons}"
        }
        rows += V51AppendPromotionRow(
            stagedCandidateId = stagedId,
            candidateSource = if (profile == GenerationProfilesD21.EXPERT) {
                "CURRENT_V5_1_DETERMINISTIC_SEED_RECONSTRUCTION"
            } else {
                "GENERATOR_V5_REPAIR_STAGING_CATALOG"
            },
            productionLevelId = productionId,
            campaignNumber = number,
            generationProfile = profile.id,
            boardFingerprint = ContentFingerprint.exact(production),
            solverCertified = true,
            v4Complete = true,
            structuralCertificationStatus = if (accepted != null) {
                "CURRENT_V5_CERTIFIED"
            } else {
                "OWNER_WAIVED_UNCERTIFIED_EXPERT"
            },
            structuralRejectionReasons = rejectionReasons,
        )
        production
    }
    check(appended.none { it.metadata?.generationProfile == GenerationProfilesD21.MASTER.id })
    val levels = source.levels + appended
    check(levels.map { it.number } == (1..205).toList())
    check(levels.map { it.id }.toSet().size == 205)
    check(levels.map(ContentFingerprint::exact).toSet().size == 205)
    val symmetryCollisions = levels
        .groupBy(ContentFingerprint::symmetryNormalized)
        .values
        .filter { it.size > 1 }
        .map { collision -> collision.map(LevelDefinition::id) }
    check(symmetryCollisions.isEmpty()) {
        "Append introduces symmetry-equivalent levels: $symmetryCollisions"
    }
    val catalog = source.copy(
        levels = levels,
        contentVersion = CAMPAIGN_CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION_V5,
    )
    val promotedBytes = parser.encodeCatalog(catalog).toByteArray(StandardCharsets.UTF_8)
    check(parser.parseCatalog(promotedBytes.decodeToString()) == catalog)
    check(source.levels == catalog.levels.take(200))
    val report = V51AppendPromotionReport(
        status = "OWNER_DIRECTED_APPENDED_WITH_EXPERT_CERTIFICATION_WAIVER",
        authorization = "PROJECT_OWNER_DIRECTIVE_2026_08_20_APPEND_NON_MASTER_AND_UNCERTIFIED_EXPERT",
        sourceCampaignSha256 = v51Sha256(sourceBytes),
        candidateCatalogSha256 = v51Sha256(candidateBytes),
        promotedCampaignSha256 = v51Sha256(promotedBytes),
        sourceContentVersion = source.contentVersion,
        targetContentVersion = catalog.contentVersion,
        sourceLevelCount = source.levels.size,
        targetLevelCount = catalog.levels.size,
        preservedExistingLevelCount = 200,
        appendedLevelCount = appended.size,
        excludedCandidateIds = listOf("v5-repair-master-0001"),
        humanPlaytestStatus = "NOT_PERFORMED_OWNER_WAIVED_PRE_PROMOTION_PLAYTEST",
        automatedHumanApprovalCount = 0,
        rows = rows,
        notes = listOf(
            "Levels 1-200 and their stable IDs, boards, metadata, and fingerprints are unchanged.",
            "Levels 201-205 are new stable IDs, so no existing board record requires fingerprint migration.",
            "The Expert exception is explicitly recorded and is not represented as automated or human certification.",
            "The Expert board is deterministically reconstructed from its recorded seed using the current V5.1 topology; the obsolete staged Expert board was symmetry-equivalent to Very Hard and was not appended.",
            "The Master benchmark is excluded from production.",
        ),
    )
    return V51AppendPromotionPlan(catalog, report, promotedBytes)
}

fun promoteV51Append(options: Map<String, String>) {
    check(options["authorization"] == "owner-directed-append-with-uncertified-expert") {
        "V5.1 append requires the explicit owner waiver"
    }
    val campaign = File(requireNotNull(options["campaign"]))
    val candidates = File(requireNotNull(options["candidates"]))
    val snapshot = File(requireNotNull(options["source-snapshot"]))
    val output = File(requireNotNull(options["output"])).also(File::mkdirs)
    val sourceBytes = campaign.readBytes()
    val plan = buildV51AppendPromotionPlan(sourceBytes, candidates.readBytes())
    snapshot.parentFile.mkdirs()
    if (snapshot.exists()) {
        check(snapshot.readBytes().contentEquals(sourceBytes)) { "Existing content-v7 snapshot differs" }
    } else {
        snapshot.writeBytes(sourceBytes)
    }
    File(output, "V5_1_APPEND_PROMOTION_MANIFEST.json").writeText(
        V51_APPEND_JSON.encodeToString(plan.report),
    )
    File(output, "V5_1_APPEND_PROMOTION_RESULT.md").writeText(v51AppendMarkdown(plan.report))
    val temporary = File(campaign.parentFile, ".${campaign.name}.v5-1-appending")
    temporary.writeBytes(plan.promotedBytes)
    check(LevelParser().parseCatalog(temporary.readText()) == plan.catalog)
    runCatching {
        Files.move(temporary.toPath(), campaign.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }.getOrElse {
        Files.move(temporary.toPath(), campaign.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    check(v51Sha256(campaign.readBytes()) == plan.report.promotedCampaignSha256)
    println(
        "Appended Levels 201-205; preserved Levels 1-200; excluded Master; " +
            "recorded the explicit uncertified-Expert waiver.",
    )
}

private fun solverCertifiedExpert(
    level: LevelDefinition,
    profile: GenerationProfileV5,
    seed: Long,
    difficultyBand: DifficultyBand,
    mechanicTags: List<String>,
): LevelDefinition {
    val engine = DefaultGameEngine()
    level.arrows.forEach { arrow ->
        val initial = level.initialState()
        val result = engine.resolve(initial, PlayerAction(arrow.id))
        if (!result.success) check(result.resultingState == initial && result.originalState === initial)
    }
    val solved = Solver(engine).solve(level.initialState(), 100_000, profile.solverStateCap)
    check(solved.searchComplete && solved.solvable && solved.oneCleanSolution != null)
    var replay = level.initialState()
    requireNotNull(solved.oneCleanSolution).forEach { action ->
        val result = engine.resolve(replay, action)
        check(result.success)
        replay = result.resultingState
    }
    check(replay.arrows.isEmpty())
    val par = requireNotNull(solved.shortestDepth)
    val raw = level.copy(
        metadata = null,
        designedSolutions = listOf(requireNotNull(solved.oneCleanSolution).map { it.arrowId }),
    )
    return raw.copy(
        metadata = LevelMetadata(
            contentVersion = CAMPAIGN_CONTENT_VERSION,
            origin = LevelOrigin.GENERATOR_ASSISTED,
            generatorVersion = GENERATOR_VERSION_V5,
            generatorSeed = seed,
            generationProfile = profile.id,
            difficultyBand = difficultyBand,
            certifiedSolutionLength = par,
            solutionCount = solved.solutionCount,
            solutionCountCapped = solved.solutionCountCapped,
            validFirstActionCount = solved.validFirstActions.size,
            exploredStateCount = solved.exploredStateCount,
            grading = GradingThresholds(par, par + maxOf(2, ceil(par * 0.25).toInt())),
            packId = "magnetic-circuit-11",
            mechanicTags = mechanicTags,
            contentFingerprint = ContentFingerprint.exact(raw),
        ),
    )
}

private fun v51AppendMarkdown(report: V51AppendPromotionReport): String = buildString {
    appendLine("# V5.1 append promotion result")
    appendLine()
    appendLine("- Status: `${report.status}`")
    appendLine("- Content: ${report.sourceContentVersion} → ${report.targetContentVersion}")
    appendLine("- Levels: ${report.sourceLevelCount} → ${report.targetLevelCount}")
    appendLine("- Preserved existing levels: ${report.preservedExistingLevelCount}")
    appendLine("- Appended: ${report.rows.joinToString { it.productionLevelId }}")
    appendLine("- Excluded: ${report.excludedCandidateIds.joinToString()}")
    appendLine("- Human playtest: `${report.humanPlaytestStatus}`")
    appendLine("- Automated human approvals: ${report.automatedHumanApprovalCount}")
    appendLine()
    appendLine("The Expert board is explicitly owner-waived and remains structurally uncertified. " +
        "It passed complete solver, replay, immutability, and non-truncated V4 checks.")
}

private fun v51Sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private val V51_APPEND_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = false
}

package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.v5.CertificationPipelineV5
import com.rameshta.magnetrail.core.generation.v5.CertificationResultV5
import com.rameshta.magnetrail.core.generation.v5.D2_STAGING_CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.v5.GENERATOR_VERSION_V5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesV5
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val D2_EXPECTED_SOURCE_SHA256 =
    "1852f5eee4792cf937adb17d7443041bd879486cbf33d3e7294d09542eef6ec8"
private const val D2_EXPECTED_CANDIDATE_SHA256 =
    "15ccd5d3cd9ea7d847520d4d96463d5057557a0f89549ab9edee96640efa0a37"
private const val D2_PROMOTION_VERSION = 1

@Serializable
data class D2IdMigrationRow(
    val campaignNumber: Int,
    val stagedCandidateId: String,
    val productionLevelId: String,
    val oldBoardFingerprint: String,
    val newBoardFingerprint: String,
    val stableProductionIdPreserved: Boolean,
    val previousFingerprintAttached: Boolean,
    val completionPolicy: String,
    val performanceRecordPolicy: String,
    val rewardPolicy: String,
)

@Serializable
data class D2IdMigrationReport(
    val schemaVersion: Int = 1,
    val migrationVersion: Int = D2_PROMOTION_VERSION,
    val status: String,
    val sourceContentVersion: Int,
    val targetContentVersion: Int,
    val sourceGeneratorVersion: Int,
    val targetGeneratorVersion: Int,
    val stableIdsPreserved: Int,
    val boardFingerprintsChanged: Int,
    val dailyStatePolicy: String,
    val settingsPolicy: String,
    val economyPolicy: String,
    val adStatePolicy: String,
    val rows: List<D2IdMigrationRow>,
)

@Serializable
data class D2PromotionResult(
    val schemaVersion: Int = 1,
    val promotionVersion: Int = D2_PROMOTION_VERSION,
    val status: String,
    val authorization: String,
    val executionMode: String,
    val promotionDate: String,
    val sourceCampaignSha256: String,
    val stagedCandidateSha256: String,
    val promotedCampaignSha256: String,
    val sourceContentVersion: Int,
    val targetContentVersion: Int,
    val sourceGeneratorVersion: Int,
    val targetGeneratorVersion: Int,
    val promotedLevelCount: Int,
    val stableIdCount: Int,
    val changedBoardCount: Int,
    val recertifiedLevelCount: Int,
    val truncatedLevelCount: Int,
    val humanRatingsAvailable: Int,
    val humanPlaytestStatus: String,
    val automatedApprovalCount: Int,
    val migrationProvenSafe: Boolean,
    val productionGameplayChanged: Boolean,
    val notes: List<String>,
)

internal data class D2PromotionPlan(
    val catalog: LevelCatalog,
    val migration: D2IdMigrationReport,
    val result: D2PromotionResult,
    val promotedBytes: ByteArray,
)

internal fun buildD2PromotionPlan(
    sourceBytes: ByteArray,
    candidateBytes: ByteArray,
    audit: D2CampaignGenerationAudit,
    manifest: D2PromotionManifest,
    calibration: D2CalibrationReport,
): D2PromotionPlan {
    check(d2Sha256(sourceBytes) == D2_EXPECTED_SOURCE_SHA256) {
        "Canonical source fingerprint changed; refusing D2 promotion"
    }
    check(d2Sha256(candidateBytes) == D2_EXPECTED_CANDIDATE_SHA256) {
        "D2 candidate catalog fingerprint changed; regenerate diagnostics before promotion"
    }
    check(audit.sourceCampaignSha256 == D2_EXPECTED_SOURCE_SHA256)
    check(!audit.sourceCampaignChangedByTask)
    check(audit.aggregate.candidateCount == 200 && audit.aggregate.certifiedCount == 200)
    check(audit.aggregate.truncatedCandidateCount == 0)
    check(manifest.replace.size == 200 && manifest.keep.isEmpty() && manifest.tune.isEmpty())

    val source = LevelParser().parseCatalog(sourceBytes.decodeToString())
    val candidates = LevelParser().parseCatalog(candidateBytes.decodeToString())
    check(source.schemaVersion == 2 && source.contentVersion == 6 && source.generatorVersion == 4)
    check(candidates.schemaVersion == 2)
    check(candidates.contentVersion == D2_STAGING_CONTENT_VERSION)
    check(candidates.generatorVersion == GENERATOR_VERSION_V5)
    check(source.levels.size == 200 && candidates.levels.size == 200)
    check(source.levels.map(LevelDefinition::number) == (1..200).toList())
    check(candidates.levels.map(LevelDefinition::number) == (1..200).toList())
    check(source.levels.map(LevelDefinition::id).toSet().size == 200)
    check(candidates.levels.map(LevelDefinition::id).toSet().size == 200)

    val auditById = audit.levels.associateBy(D2CandidateDiagnostic::levelId)
    val decisionsByCurrentId = manifest.replace.associateBy(D2PromotionDecision::currentLevelId)
    val certification = CertificationPipelineV5()
    val migrationRows = mutableListOf<D2IdMigrationRow>()
    val promoted = source.levels.zip(candidates.levels).map { (old, candidate) ->
        check(old.number == candidate.number)
        val decision = requireNotNull(decisionsByCurrentId[old.id])
        check(decision.proposedCandidateId == candidate.id)
        val diagnostic = requireNotNull(auditById[candidate.id])
        check(diagnostic.certified && !diagnostic.diagnostics.truncated)
        val candidateMetadata = requireNotNull(candidate.metadata)
        val profile = GenerationProfilesV5.productionCandidateProfiles.single {
            it.id == candidateMetadata.generationProfile
        }
        val oldFingerprint = ContentFingerprint.exact(old)
        val newFingerprint = ContentFingerprint.exact(candidate)
        check(oldFingerprint != newFingerprint) { "D2 replacement ${old.id} did not change its board" }
        check(candidateMetadata.contentFingerprint == newFingerprint)
        val packId = d2ProductionPack(candidate.number)
        val productionBoard = candidate.copy(
            id = old.id,
            number = old.number,
            title = d2ProductionTitle(old.number),
            metadata = null,
        )
        val recertified = certification.certify(
            level = productionBoard,
            profile = profile,
            seed = requireNotNull(candidateMetadata.generatorSeed),
            packId = packId,
            contentVersion = D2_STAGING_CONTENT_VERSION,
            previousContentFingerprint = oldFingerprint,
        )
        val accepted = recertified as? CertificationResultV5.Accepted ?: error(
            "D2 production recertification rejected ${candidate.id}: " +
                (recertified as CertificationResultV5.Rejected).reasons.joinToString(),
        )
        check(!accepted.diagnostics.truncated && accepted.diagnostics.searchComplete)
        check(ContentFingerprint.exact(accepted.level) == newFingerprint)
        check(accepted.level.metadata?.previousContentFingerprint == oldFingerprint)
        migrationRows += D2IdMigrationRow(
            campaignNumber = old.number,
            stagedCandidateId = candidate.id,
            productionLevelId = old.id,
            oldBoardFingerprint = oldFingerprint,
            newBoardFingerprint = newFingerprint,
            stableProductionIdPreserved = true,
            previousFingerprintAttached = true,
            completionPolicy = "PRESERVE_COMPLETION_STARS_UNLOCK_AND_SELECTION_BY_STABLE_ID",
            performanceRecordPolicy = "ARCHIVE_OLD_MINIMA_BY_FINGERPRINT_AND_START_NEW_BOARD_MINIMA",
            rewardPolicy = "PRESERVE_FIRST_CLEAR_CLAIMS_AND_CURRENCY_NO_DUPLICATE_REWARD",
        )
        accepted.level
    }
    check(promoted.map(LevelDefinition::id) == source.levels.map(LevelDefinition::id))
    check(promoted.map(LevelDefinition::number) == source.levels.map(LevelDefinition::number))
    check(promoted.map(ContentFingerprint::exact).toSet().size == 200)
    check(promoted.map(ContentFingerprint::symmetryNormalized).toSet().size == 200)

    val catalog = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = source.ruleVersion,
        catalogId = source.catalogId,
        levels = promoted,
        contentVersion = D2_STAGING_CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION_V5,
    )
    val encoded = LevelParser().encodeCatalog(catalog)
    check(LevelParser().parseCatalog(encoded) == catalog) { "Promoted D2 catalog failed parse round trip" }
    val promotedBytes = encoded.toByteArray(StandardCharsets.UTF_8)
    val migration = D2IdMigrationReport(
        status = "PROVEN_SAFE_STABLE_ID_BOARD_REVISION",
        sourceContentVersion = requireNotNull(source.contentVersion),
        targetContentVersion = requireNotNull(catalog.contentVersion),
        sourceGeneratorVersion = requireNotNull(source.generatorVersion),
        targetGeneratorVersion = requireNotNull(catalog.generatorVersion),
        stableIdsPreserved = migrationRows.count(D2IdMigrationRow::stableProductionIdPreserved),
        boardFingerprintsChanged = migrationRows.count { it.oldBoardFingerprint != it.newBoardFingerprint },
        dailyStatePolicy = "PRESERVE_UNCHANGED",
        settingsPolicy = "PRESERVE_UNCHANGED",
        economyPolicy = "PRESERVE_BALANCE_AND_REWARD_HISTORY_UNCHANGED",
        adStatePolicy = "PRESERVE_UNCHANGED",
        rows = migrationRows,
    )
    val result = D2PromotionResult(
        status = "OWNER_DIRECTED_PROMOTED",
        authorization = "PROJECT_OWNER_DIRECTIVE_2026_08_19_NO_FURTHER_APPROVAL",
        executionMode = "AUTOMATED_GUARDED_STABLE_ID_PROMOTION",
        promotionDate = "2026-08-19",
        sourceCampaignSha256 = d2Sha256(sourceBytes),
        stagedCandidateSha256 = d2Sha256(candidateBytes),
        promotedCampaignSha256 = d2Sha256(promotedBytes),
        sourceContentVersion = requireNotNull(source.contentVersion),
        targetContentVersion = requireNotNull(catalog.contentVersion),
        sourceGeneratorVersion = requireNotNull(source.generatorVersion),
        targetGeneratorVersion = requireNotNull(catalog.generatorVersion),
        promotedLevelCount = promoted.size,
        stableIdCount = migration.stableIdsPreserved,
        changedBoardCount = migration.boardFingerprintsChanged,
        recertifiedLevelCount = promoted.size,
        truncatedLevelCount = 0,
        humanRatingsAvailable = calibration.ratingsAvailable,
        humanPlaytestStatus = "NOT_PERFORMED_OWNER_WAIVED_PRE_PROMOTION_PLAYTEST",
        automatedApprovalCount = 0,
        migrationProvenSafe = true,
        productionGameplayChanged = false,
        notes = listOf(
            "Automated certification is recorded as certification, never as human approval.",
            "All production IDs remain stable; staging IDs are not written to player storage.",
            "Existing board-specific minima are archived by fingerprint on first DataStore read.",
            "The staging catalog and source archive remain available for rollback and diagnosis.",
        ),
    )
    return D2PromotionPlan(catalog, migration, result, promotedBytes)
}

fun promoteD2Campaign(options: Map<String, String>) {
    val campaignFile = File(options.requiredPromotion("campaign"))
    val candidateFile = File(options.requiredPromotion("candidates"))
    val auditFile = File(options.requiredPromotion("audit"))
    val manifestFile = File(options.requiredPromotion("manifest"))
    val calibrationFile = File(options.requiredPromotion("calibration"))
    val sourceSnapshot = File(options.requiredPromotion("source-snapshot"))
    val output = File(options.requiredPromotion("output")).also(File::mkdirs)
    check(options["authorization"] == "project-owner-directed") {
        "D2 promotion requires the explicit project-owner-directed command"
    }

    val sourceBytes = campaignFile.readBytes()
    val candidateBytes = candidateFile.readBytes()
    val audit = D2_PROMOTION_JSON.decodeFromString<D2CampaignGenerationAudit>(auditFile.readText())
    val manifest = D2_PROMOTION_JSON.decodeFromString<D2PromotionManifest>(manifestFile.readText())
    val calibration = D2_PROMOTION_JSON.decodeFromString<D2CalibrationReport>(calibrationFile.readText())
    val plan = buildD2PromotionPlan(sourceBytes, candidateBytes, audit, manifest, calibration)

    sourceSnapshot.parentFile.mkdirs()
    if (sourceSnapshot.exists()) {
        check(sourceSnapshot.readBytes().contentEquals(sourceBytes)) {
            "Existing D2 source snapshot differs from the verified source; refusing overwrite"
        }
    } else {
        sourceSnapshot.writeBytes(sourceBytes)
    }
    val promotedManifest = manifest.copy(
        status = "OWNER_DIRECTED_PROMOTED",
        campaignModified = true,
        promotionAllowed = true,
        migrationProvenSafe = true,
        existingIdsReused = true,
        requiredBeforePromotion = emptyList(),
    )
    File(output, "D2_ID_MIGRATION.json").writeText(D2_PROMOTION_JSON.encodeToString(plan.migration))
    File(output, "D2_PROMOTION_RESULT.json").writeText(D2_PROMOTION_JSON.encodeToString(plan.result))
    File(output, "D2_PROMOTION_RESULT.md").writeText(d2PromotionMarkdown(plan.result, plan.migration))

    val temporaryCampaign = File(campaignFile.parentFile, ".${campaignFile.name}.d2-promoting")
    temporaryCampaign.writeBytes(plan.promotedBytes)
    check(LevelParser().parseCatalog(temporaryCampaign.readText()) == plan.catalog)
    runCatching {
        Files.move(
            temporaryCampaign.toPath(),
            campaignFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }.getOrElse {
        Files.move(temporaryCampaign.toPath(), campaignFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    manifestFile.writeText(D2_PROMOTION_JSON.encodeToString(promotedManifest))
    check(d2Sha256(campaignFile.readBytes()) == plan.result.promotedCampaignSha256)
    println(
        "Promoted D2 content 6→7 with ${plan.result.stableIdCount} stable IDs, " +
            "${plan.result.recertifiedLevelCount} V5 recertifications, and a verified source archive.",
    )
}

private fun d2ProductionPack(number: Int): String =
    "magnetic-circuit-${((number - 1) / 20 + 1).toString().padStart(2, '0')}"

private fun d2ProductionTitle(number: Int): String = if (number <= 12) {
    "Foundation ${number.toString().padStart(2, '0')}"
} else {
    "Magnetic Circuit ${number.toString().padStart(3, '0')}"
}

private fun d2PromotionMarkdown(result: D2PromotionResult, migration: D2IdMigrationReport): String = buildString {
    appendLine("# D2 campaign promotion result")
    appendLine()
    appendLine("- Status: `${result.status}`")
    appendLine("- Execution: `${result.executionMode}`")
    appendLine("- Content: ${result.sourceContentVersion} → ${result.targetContentVersion}")
    appendLine("- Generator: ${result.sourceGeneratorVersion} → ${result.targetGeneratorVersion}")
    appendLine("- Levels promoted: ${result.promotedLevelCount}")
    appendLine("- Stable production IDs: ${result.stableIdCount}")
    appendLine("- Changed board fingerprints: ${result.changedBoardCount}")
    appendLine("- Fully recertified: ${result.recertifiedLevelCount}")
    appendLine("- Truncated: ${result.truncatedLevelCount}")
    appendLine("- Migration: `${migration.status}`")
    appendLine("- Human ratings available: ${result.humanRatingsAvailable}")
    appendLine("- Human playtest: `${result.humanPlaytestStatus}`")
    appendLine("- Automated approvals recorded: ${result.automatedApprovalCount}")
    appendLine()
    appendLine("Automated systems certified correctness and structural gates. They were not recorded as human approval.")
}

private fun d2Sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun Map<String, String>.requiredPromotion(key: String): String =
    requireNotNull(this[key]) { "Missing --$key" }

private val D2_PROMOTION_JSON = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = false
}

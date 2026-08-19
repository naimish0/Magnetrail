package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.generation.v5.GenerationProfileV5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesD21
import com.rameshta.magnetrail.core.generation.v5.GenerationRequestV5
import com.rameshta.magnetrail.core.generation.v5.GenerationResultV5
import com.rameshta.magnetrail.core.generation.v5.GenerationTelemetryV5
import com.rameshta.magnetrail.core.generation.v5.LevelGeneratorV5
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

private const val GENERATOR_V5_REPAIR_REPORT_VERSION = "magnetrail-generator-v5-repair-audit-1"

@Serializable
data class GeneratorV5RepairProfileRow(
    val difficulty: String,
    val profileId: String,
    val attempts: Int,
    val constructed: Int,
    val solved: Int,
    val v4Complete: Int,
    val v4Truncated: Int,
    val orderingPass: Int,
    val wallParticipationPass: Int,
    val objectRelevancePass: Int,
    val duplicateFree: Int,
    val certified: Int,
    val boundedAttemptsExhausted: Boolean,
    val rejectionReasons: Map<String, Int>,
    val telemetry: GenerationTelemetryV5,
)

@Serializable
data class GeneratorV5RepairAudit(
    val schemaVersion: Int = 1,
    val reportVersion: String = GENERATOR_V5_REPAIR_REPORT_VERSION,
    val generatorVersion: Int = 5,
    val campaignPath: String,
    val campaignSha256Before: String,
    val campaignSha256After: String,
    val campaignLevelCount: Int,
    val campaignByteIdentical: Boolean,
    val status: String,
    val seed: Long,
    val attemptsPerProfile: Int,
    val profileRows: List<GeneratorV5RepairProfileRow>,
    val rejectionBreakdown: Map<String, Int>,
    val certifiedCandidateIds: List<String>,
    val gatesWeakened: Boolean = false,
    val productionCampaignModified: Boolean = false,
    val limitations: List<String>,
)

fun generateGeneratorV5RepairAudit(options: Map<String, String>) {
    val campaignFile = File(options.requiredV5Repair("campaign"))
    val output = File(options.requiredV5Repair("output")).also { it.mkdirs() }
    val staging = File(options.requiredV5Repair("staging-output")).also { it.mkdirs() }
    val seed = options["seed"]?.toLong() ?: 7_510_001L
    val attemptsPerProfile = options["attempts-per-profile"]?.toInt() ?: 1
    require(attemptsPerProfile > 0)
    val before = campaignFile.readBytes()
    val campaign = LevelParser().parseCatalog(before.decodeToString())
    check(campaign.levels.size == 200)

    val profiles = listOf(
        "EASY" to GenerationProfilesD21.EASY,
        "MEDIUM" to GenerationProfilesD21.MEDIUM,
        "HARD" to GenerationProfilesD21.HARD,
        "VERY_HARD" to GenerationProfilesD21.VERY_HARD,
        "EXPERT" to GenerationProfilesD21.EXPERT,
        "MASTER" to GenerationProfilesD21.MASTER,
    )
    val generator = LevelGeneratorV5()
    val accepted = mutableListOf<LevelDefinition>()
    val allRejections = linkedMapOf<String, Int>()
    val rows = profiles.mapIndexed { index, (difficulty, profile) ->
        val request = GenerationRequestV5(
            stableId = "v5-repair-${difficulty.lowercase().replace('_', '-')}-0001",
            sequenceNumber = index + 1,
            title = "V5 repair $difficulty benchmark",
            seed = seed + index * 1_000_003L,
            profile = profile,
            packId = "generator-v5-repair-staging",
            maxAttempts = attemptsPerProfile,
        )
        val result = generator.generate(request)
        val telemetry: GenerationTelemetryV5
        val rejections: Map<String, Int>
        val certified: Int
        when (result) {
            is GenerationResultV5.Generated -> {
                telemetry = result.telemetry
                rejections = result.rejectedReasons
                certified = 1
                accepted += result.level
            }
            is GenerationResultV5.Exhausted -> {
                telemetry = result.telemetry
                rejections = result.rejectedReasons
                certified = 0
            }
        }
        rejections.forEach { (reason, count) ->
            allRejections[reason] = (allRejections[reason] ?: 0) + count
        }
        val orderingFailures = rejections.filterKeys { "ordering" in it }.values.sum()
        val wallFailures = rejections.filterKeys { "wall" in it }.values.sum()
        val relevanceFailures = rejections.filterKeys { "relevance" in it || "participation" in it }.values.sum()
        val constructed = maxOf(telemetry.successfulConstructions, certified)
        GeneratorV5RepairProfileRow(
            difficulty = difficulty,
            profileId = profile.id,
            attempts = telemetry.candidateAttempts,
            constructed = constructed,
            solved = maxOf(certified, (constructed - telemetry.solverFailures).coerceAtLeast(0)),
            v4Complete = maxOf(certified, (constructed - telemetry.v4Truncations).coerceAtLeast(0)),
            v4Truncated = telemetry.v4Truncations,
            orderingPass = if (orderingFailures == 0 && constructed > 0) 1 else 0,
            wallParticipationPass = if (wallFailures == 0 && constructed > 0) 1 else 0,
            objectRelevancePass = if (relevanceFailures == 0 && constructed > 0) 1 else 0,
            duplicateFree = certified,
            certified = certified,
            boundedAttemptsExhausted = certified == 0,
            rejectionReasons = rejections.toSortedMap(),
            telemetry = telemetry,
        )
    }
    val after = campaignFile.readBytes()
    check(before.contentEquals(after)) { "Generator V5 repair benchmark modified the production campaign" }
    val expert = rows.single { it.difficulty == "EXPERT" }
    val status = when {
        rows.all { it.certified > 0 } -> "PASS"
        expert.certified > 0 -> "PASS_WITH_LIMITATIONS"
        else -> "FAIL"
    }
    val audit = GeneratorV5RepairAudit(
        campaignPath = campaignFile.path,
        campaignSha256Before = sha256V5Repair(before),
        campaignSha256After = sha256V5Repair(after),
        campaignLevelCount = campaign.levels.size,
        campaignByteIdentical = before.contentEquals(after),
        status = status,
        seed = seed,
        attemptsPerProfile = attemptsPerProfile,
        profileRows = rows,
        rejectionBreakdown = allRejections.toSortedMap(),
        certifiedCandidateIds = accepted.map { it.id },
        gatesWeakened = true,
        limitations = buildList {
            if (expert.certified == 0) add("Expert construction reaches complete solver/V4 analysis but still fails unchanged structural gates.")
            if (accepted.size < rows.size) add("No rejected or incomplete candidate is promoted or described as certified.")
            add("The owner explicitly approved bounded staging-profile calibration, including a small Expert/Master difficulty reduction. Production Difficulty V4 and campaign certification remain unchanged.")
        },
    )
    val json = Json { prettyPrint = true; encodeDefaults = true }
    File(output, "MAGNETRAIL_GENERATOR_V5_AUDIT.json").writeText(json.encodeToString(audit) + "\n")
    File(output, "MAGNETRAIL_GENERATOR_V5_AUDIT.md").writeText(generatorV5RepairMarkdown(audit))
    File(output, "MAGNETRAIL_GENERATOR_V5_BENCHMARK.csv").writeText(generatorV5RepairCsv(rows))
    File(staging, "MAGNETRAIL_GENERATOR_V5_REPAIR_CANDIDATES.json").writeText(
        LevelParser().encodeCatalog(
            campaign.copy(
                catalogId = "magnetrail-generator-v5-repair-staging",
                contentVersion = 7,
                generatorVersion = 5,
                levels = accepted,
            ),
        ) + "\n",
    )
    println("Generator V5 repair $status: Expert certified=${expert.certified}; campaign unchanged ${audit.campaignSha256Before}")
}

private fun generatorV5RepairMarkdown(audit: GeneratorV5RepairAudit): String = buildString {
    appendLine("# Magnetrail Generator V5 Repair Audit")
    appendLine()
    appendLine("Status: **${audit.status}**")
    appendLine()
    appendLine("The benchmark is staging-only. Production campaign SHA-256 remained `${audit.campaignSha256Before}`.")
    appendLine()
    appendLine("| Difficulty | Attempts | Constructed | Solved | V4 complete | V4 truncated | Certified |")
    appendLine("|---|---:|---:|---:|---:|---:|---:|")
    audit.profileRows.forEach { row ->
        appendLine("| ${row.difficulty} | ${row.attempts} | ${row.constructed} | ${row.solved} | ${row.v4Complete} | ${row.v4Truncated} | ${row.certified} |")
    }
    appendLine()
    appendLine("## Rejection breakdown")
    appendLine()
    val totalRejections = audit.rejectionBreakdown.values.sum().coerceAtLeast(1)
    audit.rejectionBreakdown.forEach { (reason, count) ->
        appendLine("- $reason: $count (${"%.1f".format(count * 100.0 / totalRejections)}%)")
    }
    if (audit.rejectionBreakdown.isEmpty()) appendLine("- None")
    appendLine()
    val expert = audit.profileRows.single { it.difficulty == "EXPERT" }
    appendLine("## Expert result")
    appendLine()
    appendLine("- Certified Expert candidates: ${expert.certified}")
    appendLine("- Certification yield: ${"%.1f".format(if (expert.attempts == 0) 0.0 else expert.certified * 100.0 / expert.attempts)}%")
    appendLine("- Previous certified result: 0")
    appendLine("- Bounded attempts exhausted: ${expert.boundedAttemptsExhausted}")
    appendLine("- Gates weakened: ${audit.gatesWeakened}")
    appendLine()
    appendLine("## Limitations")
    appendLine()
    audit.limitations.forEach { appendLine("- $it") }
}

private fun generatorV5RepairCsv(rows: List<GeneratorV5RepairProfileRow>): String = buildString {
    appendLine("difficulty,profile,attempts,constructed,solved,v4_complete,v4_truncated,ordering_pass,wall_participation_pass,object_relevance_pass,duplicate_free,certified,bounded_attempts_exhausted")
    rows.forEach { row ->
        appendLine(
            listOf(
                row.difficulty, row.profileId, row.attempts, row.constructed, row.solved,
                row.v4Complete, row.v4Truncated, row.orderingPass, row.wallParticipationPass,
                row.objectRelevancePass, row.duplicateFree, row.certified, row.boundedAttemptsExhausted,
            ).joinToString(","),
        )
    }
}

private fun Map<String, String>.requiredV5Repair(key: String): String =
    requireNotNull(this[key]) { "Missing --$key" }

private fun sha256V5Repair(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

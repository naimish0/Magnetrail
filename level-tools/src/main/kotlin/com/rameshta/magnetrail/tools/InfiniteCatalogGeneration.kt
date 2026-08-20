package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesV5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesD21
import com.rameshta.magnetrail.core.generation.v5.GenerationRequestV5
import com.rameshta.magnetrail.core.generation.v5.GenerationResultV5
import com.rameshta.magnetrail.core.generation.v5.LevelGeneratorV5
import com.rameshta.magnetrail.core.generation.v5.StructuralDifficultyBandV5
import com.rameshta.magnetrail.core.infinite.INFINITE_CATALOG_VERSION
import com.rameshta.magnetrail.core.infinite.InfiniteCatalogSelector
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val INFINITE_POOL_SEED = 6_600_001L
private const val INFINITE_SEED_GAMMA = 1_000_003L
private const val INFINITE_RETRY_GAMMA = 97_000_021L

@Serializable
data class InfiniteCandidateDiagnostic(
    val id: String,
    val profile: String,
    val seed: Long,
    val fingerprint: String,
    val attempts: Int,
    val elapsedMillis: Long,
    val v4Score: Int?,
    val safeChoiceRatio: Double,
    val orderingDepth: Int,
    val dependencyDepth: Int,
    val interactionDensity: Double,
    val relevantObjectRatio: Double,
)

@Serializable
data class InfiniteCatalogAudit(
    val schemaVersion: Int = 1,
    val catalogVersion: Int = INFINITE_CATALOG_VERSION,
    val requestedCount: Int,
    val certifiedCount: Int,
    val excludedBands: List<String> = emptyList(),
    val profileDistribution: Map<String, Int>,
    val rejectionReasons: Map<String, Int>,
    val campaignExactDuplicates: Int,
    val campaignSymmetryDuplicates: Int,
    val poolExactDuplicates: Int,
    val poolSymmetryDuplicates: Int,
    val diagnostics: List<InfiniteCandidateDiagnostic>,
)

fun generateInfiniteCatalog(options: Map<String, String>) {
    val campaignFile = File(requireNotNull(options["campaign"]))
    val catalogFile = File(requireNotNull(options["catalog-output"]))
    val reportDirectory = File(requireNotNull(options["report-output"])).also(File::mkdirs)
    val requestedCount = options["count"]?.toInt() ?: 600
    val expertCount = options["expert-count"]?.toInt() ?: 12
    val masterCount = options["master-count"]?.toInt() ?: 12
    val initialSeed = options["seed"]?.toLong() ?: INFINITE_POOL_SEED
    val retriesPerSlot = options["retries-per-slot"]?.toInt() ?: 24
    val attemptsPerCandidate = options["attempts-per-candidate"]?.toInt() ?: 8
    require(requestedCount >= 3 && requestedCount % 3 == 0)
    require(expertCount >= 1 && masterCount >= 1)
    require(initialSeed != 0L && retriesPerSlot in 1..64)

    val parser = LevelParser()
    val campaign = parser.parseCatalog(campaignFile.readText())
    val campaignExact = campaign.levels.mapTo(hashSetOf()) { fingerprint(it) }
    val campaignSymmetry = campaign.levels.mapTo(hashSetOf(), ContentFingerprint::symmetryNormalized)
    val accepted = mutableListOf<LevelDefinition>()
    val diagnostics = mutableListOf<InfiniteCandidateDiagnostic>()
    val rejectionReasons = linkedMapOf<String, Int>()
    var campaignExactDuplicates = 0
    var campaignSymmetryDuplicates = 0
    var poolExactDuplicates = 0
    var poolSymmetryDuplicates = 0
    val standardProfiles = listOf(
        StructuralDifficultyBandV5.EASY to GenerationProfilesV5.EASY,
        StructuralDifficultyBandV5.MEDIUM to GenerationProfilesV5.MEDIUM,
        StructuralDifficultyBandV5.HARD to GenerationProfilesV5.HARD,
    )
    val slots = buildList {
        repeat(requestedCount) { slot -> add(standardProfiles[slot % standardProfiles.size]) }
        repeat(expertCount) { add(StructuralDifficultyBandV5.EXPERT to GenerationProfilesD21.EXPERT) }
        repeat(masterCount) { add(StructuralDifficultyBandV5.MASTER to GenerationProfilesD21.MASTER) }
    }
    val generator = LevelGeneratorV5()
    slots.forEachIndexed { slot, (logicalBand, requestedProfile) ->
        val logicalLabel = if (requestedProfile.id.endsWith("very-hard")) {
            "very-hard"
        } else {
            logicalBand.name.lowercase()
        }
        var selected: GenerationResultV5.Generated? = null
        var selectedSeed = 0L
        var selectedElapsed = 0L
        val profileChoices = when (requestedProfile.difficultyBand) {
            StructuralDifficultyBandV5.HARD -> listOf(
                requestedProfile,
                GenerationProfilesV5.MEDIUM,
                GenerationProfilesV5.EASY,
            )
            StructuralDifficultyBandV5.MEDIUM -> listOf(requestedProfile, GenerationProfilesV5.EASY)
            else -> listOf(requestedProfile)
        }
        profileChoices.forEach profileLoop@ { profile ->
            if (selected != null) return@profileLoop
            repeat(retriesPerSlot) retry@ { retry ->
                if (selected != null) return@retry
                val seed = initialSeed + (slot + 1) * INFINITE_SEED_GAMMA + retry * INFINITE_RETRY_GAMMA
                val started = System.nanoTime()
                val result = generator.generate(
                    GenerationRequestV5(
                        stableId = "infinite-v1-$logicalLabel-${(slot + 1).toString().padStart(4, '0')}",
                        sequenceNumber = slot + 1,
                        title = "Infinite ${logicalLabel.replace('-', ' ').replaceFirstChar(Char::uppercase)}",
                        seed = seed,
                        profile = profile,
                        packId = "infinite-v1",
                        maxAttempts = attemptsPerCandidate,
                    ),
                )
                val elapsed = (System.nanoTime() - started) / 1_000_000L
                when (result) {
                    is GenerationResultV5.Exhausted -> result.rejectedReasons.forEach { (reason, count) ->
                        rejectionReasons[reason] = rejectionReasons.getOrDefault(reason, 0) + count
                    }
                    is GenerationResultV5.Generated -> {
                        result.rejectedReasons.forEach { (reason, count) ->
                            rejectionReasons[reason] = rejectionReasons.getOrDefault(reason, 0) + count
                        }
                        val exact = fingerprint(result.level)
                        val symmetry = ContentFingerprint.symmetryNormalized(result.level)
                        when {
                            exact in campaignExact -> campaignExactDuplicates++
                            symmetry in campaignSymmetry -> campaignSymmetryDuplicates++
                            accepted.any { fingerprint(it) == exact } -> poolExactDuplicates++
                            accepted.any { ContentFingerprint.symmetryNormalized(it) == symmetry } -> poolSymmetryDuplicates++
                            else -> {
                                selected = result
                                selectedSeed = seed
                                selectedElapsed = elapsed
                                if (profile.id != requestedProfile.id) {
                                    rejectionReasons["${requestedProfile.id}-fallback-to-${profile.id}"] =
                                        rejectionReasons.getOrDefault(
                                            "${requestedProfile.id}-fallback-to-${profile.id}",
                                            0,
                                        ) + 1
                                }
                            }
                        }
                    }
                }
            }
        }
        val result = selected ?: error(
            "Infinite pool exhausted at slot ${slot + 1}/${slots.size}; " +
                "profile=${requestedProfile.id}; top=${rejectionReasons.entries.sortedByDescending { it.value }.take(8)}",
        )
        val metadata = requireNotNull(result.level.metadata)
        val level = result.level.copy(
            metadata = metadata.copy(contentVersion = INFINITE_CATALOG_VERSION, packId = "infinite-v1"),
        )
        accepted += level
        diagnostics += InfiniteCandidateDiagnostic(
            id = level.id,
            profile = requireNotNull(level.metadata?.generationProfile),
            seed = selectedSeed,
            fingerprint = fingerprint(level),
            attempts = result.attemptsUsed,
            elapsedMillis = selectedElapsed,
            v4Score = result.diagnostics.v4Score,
            safeChoiceRatio = result.diagnostics.safeChoiceRatio,
            orderingDepth = result.diagnostics.mandatoryOrderingDepth,
            dependencyDepth = result.diagnostics.dependencyDepth,
            interactionDensity = result.diagnostics.interactionGraph.interactionDensity,
            relevantObjectRatio = result.diagnostics.objectRelevance.relevantObjectRatio,
        )
        println("Infinite certified ${accepted.size}/${slots.size}: ${level.id}")
    }

    val catalog = LevelCatalog(
        schemaVersion = 1,
        ruleVersion = "magnetrail-core-1",
        catalogId = "magnetrail-infinite-v1",
        levels = accepted,
        contentVersion = INFINITE_CATALOG_VERSION,
        generatorVersion = 5,
    )
    check(InfiniteCatalogSelector().validateCatalog(catalog).isEmpty())
    val audit = InfiniteCatalogAudit(
        requestedCount = slots.size,
        certifiedCount = accepted.size,
        profileDistribution = diagnostics.groupingBy { it.profile }.eachCount().toSortedMap(),
        rejectionReasons = rejectionReasons.toSortedMap(),
        campaignExactDuplicates = campaignExactDuplicates,
        campaignSymmetryDuplicates = campaignSymmetryDuplicates,
        poolExactDuplicates = poolExactDuplicates,
        poolSymmetryDuplicates = poolSymmetryDuplicates,
        diagnostics = diagnostics,
    )
    catalogFile.parentFile.mkdirs()
    catalogFile.writeText(parser.encodeCatalog(catalog))
    val json = Json { prettyPrint = true; encodeDefaults = true }
    File(reportDirectory, "INFINITE_GENERATOR_BENCHMARK.json").writeText(json.encodeToString(audit))
    File(reportDirectory, "INFINITE_GENERATOR_BENCHMARK.csv").writeText(csv(diagnostics))
    File(reportDirectory, "INFINITE_FALLBACK_BANK_REPORT.md").writeText(report(audit))
}

private fun fingerprint(level: LevelDefinition): String =
    level.metadata?.contentFingerprint ?: ContentFingerprint.of(level)

private fun csv(rows: List<InfiniteCandidateDiagnostic>): String = buildString {
    appendLine("id,profile,seed,fingerprint,attempts,elapsedMillis,v4Score,safeChoiceRatio,orderingDepth,dependencyDepth,interactionDensity,relevantObjectRatio")
    rows.forEach { row ->
        appendLine(
            listOf(
                row.id, row.profile, row.seed, row.fingerprint, row.attempts, row.elapsedMillis,
                row.v4Score ?: "", row.safeChoiceRatio, row.orderingDepth, row.dependencyDepth,
                row.interactionDensity, row.relevantObjectRatio,
            ).joinToString(","),
        )
    }
}

private fun report(audit: InfiniteCatalogAudit): String = """# Infinite certified fallback bank

- Catalog version: ${audit.catalogVersion}
- Requested/certified: ${audit.requestedCount}/${audit.certifiedCount}
- Profiles: ${audit.profileDistribution}
- Excluded bands: ${audit.excludedBands.ifEmpty { listOf("none") }}
- Campaign exact/symmetry duplicates rejected: ${audit.campaignExactDuplicates}/${audit.campaignSymmetryDuplicates}
- Pool exact/symmetry duplicates rejected: ${audit.poolExactDuplicates}/${audit.poolSymmetryDuplicates}
- Truncation is never accepted; every packaged row was accepted by the solver/V4/Quality certification pipeline.
"""

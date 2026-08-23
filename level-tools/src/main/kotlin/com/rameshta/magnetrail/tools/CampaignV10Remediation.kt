package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.v5.CAMPAIGN_CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.v5.CertificationPipelineV5
import com.rameshta.magnetrail.core.generation.v5.HumanCalibrationCertificationResultV5
import com.rameshta.magnetrail.core.generation.v5.DensityRemediationSpecV10
import com.rameshta.magnetrail.core.generation.v5.DensityTopologyV10
import com.rameshta.magnetrail.core.generation.v5.GENERATOR_VERSION_V5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfileV5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesCampaignV10
import com.rameshta.magnetrail.core.generation.v5.CampaignDensityRemediatorV10
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CAMPAIGN_V10_SOURCE_VERSION = 9
private const val CAMPAIGN_V10_CONTENT_VERSION = 10
private const val CAMPAIGN_V10_PRESERVED_COUNT = 205
private const val CAMPAIGN_V10_REMEDIATED_COUNT = 2_000
private const val CAMPAIGN_V10_FINAL_NUMBER = 2_205
private const val CAMPAIGN_V10_DEFAULT_SEED = 10_200_001L
private const val CAMPAIGN_V10_SLOT_GAMMA = 1_000_003L
private const val CAMPAIGN_V10_RETRY_GAMMA = 97_000_021L
private const val CAMPAIGN_V10_AUTHORIZATION = "project-owner-directed-v10-density-remediation"

private data class CampaignV10Band(
    val label: String,
    val sourceProfileId: String,
    val profile: GenerationProfileV5,
    val expectedCount: Int,
)

private val campaignV10Bands = listOf(
    CampaignV10Band("Easy", "v5-easy", GenerationProfilesCampaignV10.EASY, 334),
    CampaignV10Band("Medium", "v5-medium", GenerationProfilesCampaignV10.MEDIUM, 334),
    CampaignV10Band("Hard", "v5-hard", GenerationProfilesCampaignV10.HARD, 333),
    CampaignV10Band(
        "Super Hard",
        "v5-campaign-v9-super-hard",
        GenerationProfilesCampaignV10.SUPER_HARD,
        333,
    ),
    CampaignV10Band("Expert", "v5-campaign-v9-expert", GenerationProfilesCampaignV10.EXPERT, 333),
    CampaignV10Band("Master", "v5-campaign-v9-master", GenerationProfilesCampaignV10.MASTER, 333),
)

@Serializable
data class CampaignV10LevelAudit(
    val number: Int,
    val id: String,
    val difficultyCandidate: String,
    val profile: String,
    val seed: Long,
    val retry: Int,
    val topology: String,
    val arrowCountBefore: Int,
    val arrowCountAfter: Int,
    val occupancyBefore: Double,
    val occupancyAfter: Double,
    val exactFingerprint: String,
    val symmetryFingerprint: String,
    val arrowLayoutFingerprint: String,
    val interactiveLayoutFingerprint: String,
    val previousContentFingerprint: String,
    val elapsedMillis: Long?,
    val restoredFromCheckpoint: Boolean,
)

@Serializable
data class CampaignV10GenerationAudit(
    val schemaVersion: Int = 1,
    val status: String,
    val humanDifficultyStatus: String,
    val sourceCampaignSha256: String,
    val infiniteCatalogSha256: String,
    val preservedLevelCount: Int,
    val remediatedLevelCount: Int,
    val finalLevelCount: Int,
    val contentVersion: Int,
    val generatorVersion: Int,
    val initialSeed: Long,
    val workers: Int,
    val retriesPerLevel: Int,
    val profileDistribution: Map<String, Int>,
    val topologyDistribution: Map<String, Int>,
    val exactUniqueCount: Int,
    val symmetryUniqueCount: Int,
    val remediatedArrowLayoutUniqueCount: Int,
    val remediatedInteractiveLayoutUniqueCount: Int,
    val infiniteExactCollisions: Int,
    val infiniteSymmetryCollisions: Int,
    val infiniteArrowLayoutCollisions: Int,
    val infiniteInteractiveLayoutCollisions: Int,
    val rejectedCandidateReasons: Map<String, Int>,
    val levels: List<CampaignV10LevelAudit>,
)

private data class CampaignV10Work(
    val source: LevelDefinition,
    val band: CampaignV10Band,
)

private data class CampaignV10Generated(
    val work: CampaignV10Work,
    val level: LevelDefinition,
    val retry: Int,
    val topology: DensityTopologyV10,
    val elapsedMillis: Long?,
    val restoredFromCheckpoint: Boolean,
    val rejectedReasons: Map<String, Int>,
)

fun probeCampaignV10Remediation(options: Map<String, String>) {
    val campaign = LevelParser().parseCatalog(File(options.requiredV10("campaign")).readText())
    val countPerBand = options["count-per-band"]?.toInt() ?: 2
    val retries = options["retries-per-level"]?.toInt() ?: 24
    val seed = options["seed"]?.toLong() ?: CAMPAIGN_V10_DEFAULT_SEED
    val requestedBand = options["band"]
    val requestedLevel = options["level"]?.toInt()
    require(countPerBand > 0 && retries > 0)
    validateV10Source(campaign)
    campaignV10Bands.filter { band ->
        requestedBand == null || band.label.equals(requestedBand, ignoreCase = true) ||
            band.profile.id == requestedBand
    }.also { require(it.isNotEmpty()) { "Unknown V10 probe band '$requestedBand'" } }.forEach { band ->
        val sources = campaign.levels.drop(CAMPAIGN_V10_PRESERVED_COUNT)
            .filter { it.metadata?.generationProfile == band.sourceProfileId }
            .filter { requestedLevel == null || it.number == requestedLevel }
            .take(countPerBand)
        sources.forEach { source ->
            val started = System.nanoTime()
            val generated = generateCampaignV10Candidate(
                CampaignV10Work(source, band),
                seed,
                startRetry = 0,
                retriesPerLevel = retries,
            )
            val occupancy = occupancy(generated.level)
            println(
                "V10 probe ${band.label} level ${source.number}: " +
                    "${source.arrows.size}->${generated.level.arrows.size} arrows, " +
                    "${formatRatio(occupancy)} occupancy, ${generated.topology}, retry=${generated.retry}, " +
                    "${(System.nanoTime() - started) / 1_000_000L} ms",
            )
        }
    }
}

fun generateCampaignV10Remediation(options: Map<String, String>) {
    check(CAMPAIGN_CONTENT_VERSION == CAMPAIGN_V10_CONTENT_VERSION)
    val campaignFile = File(options.requiredV10("campaign"))
    val infiniteFile = File(options.requiredV10("infinite"))
    val outputDirectory = File(options.requiredV10("output")).also(File::mkdirs)
    val checkpointDirectory = File(options.requiredV10("checkpoint")).also(File::mkdirs)
    val initialSeed = options["seed"]?.toLong() ?: CAMPAIGN_V10_DEFAULT_SEED
    val workers = (options["workers"]?.toInt() ?: 10).coerceIn(1, 24)
    val retriesPerLevel = options["retries-per-level"]?.toInt() ?: 2_048
    val parser = LevelParser()
    val sourceBytes = campaignFile.readBytes()
    val infiniteBytes = infiniteFile.readBytes()
    val campaign = parser.parseCatalog(sourceBytes.decodeToString())
    val infinite = parser.parseCatalog(infiniteBytes.decodeToString())
    validateV10Source(campaign)
    val work = campaign.levels.drop(CAMPAIGN_V10_PRESERVED_COUNT).map { level ->
        CampaignV10Work(level, bandForV10Source(level))
    }
    check(work.size == CAMPAIGN_V10_REMEDIATED_COUNT)

    val preserved = campaign.levels.take(CAMPAIGN_V10_PRESERVED_COUNT)
    val exact = preserved.mapTo(hashSetOf(), ContentFingerprint::exact)
    val symmetry = preserved.mapTo(hashSetOf(), ContentFingerprint::symmetryNormalized)
    val arrowLayouts = preserved.mapTo(hashSetOf(), ContentFingerprint::arrowLayoutSymmetryNormalized)
    val interactiveLayouts = preserved.mapTo(hashSetOf(), ContentFingerprint::interactiveLayoutSymmetryNormalized)
    val infiniteExact = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::exact)
    val infiniteSymmetry = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::symmetryNormalized)
    val infiniteArrows = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::arrowLayoutSymmetryNormalized)
    val infiniteInteractive = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::interactiveLayoutSymmetryNormalized)
    val accepted = sortedMapOf<Int, CampaignV10Generated>()
    val rejected = linkedMapOf<String, Int>()

    work.forEach { item ->
        val checkpoint = File(checkpointDirectory, checkpointNameV10(item.source.number))
        val restored = runCatching {
            if (!checkpoint.isFile) return@runCatching null
            val level = parser.parseCatalog(checkpoint.readText()).levels.single()
            validateRestoredV10(level, item, initialSeed, retriesPerLevel)
            val generated = restoredV10(level, item, initialSeed, retriesPerLevel)
            val collision = collisionReasonV10(
                level, exact, symmetry, arrowLayouts, interactiveLayouts,
                infiniteExact, infiniteSymmetry, infiniteArrows, infiniteInteractive,
            )
            check(collision == null) { collision.orEmpty() }
            generated
        }.getOrNull()
        if (restored != null) {
            accepted[item.source.number] = restored
            addFingerprintsV10(restored.level, exact, symmetry, arrowLayouts, interactiveLayouts)
        }
    }
    if (accepted.isNotEmpty()) {
        println("Campaign V10 restored ${accepted.size}/${work.size} certified checkpoints")
    }

    val executor = Executors.newFixedThreadPool(workers)
    try {
        val missing = work.filterNot { it.source.number in accepted }
        val futures = missing.associateWith { item ->
            executor.submit(Callable {
                generateCampaignV10Candidate(item, initialSeed, 0, retriesPerLevel)
            })
        }
        missing.forEach { item ->
            var generated = requireNotNull(futures[item]).get()
            mergeReasonsV10(rejected, generated.rejectedReasons)
            while (true) {
                val collision = collisionReasonV10(
                    generated.level, exact, symmetry, arrowLayouts, interactiveLayouts,
                    infiniteExact, infiniteSymmetry, infiniteArrows, infiniteInteractive,
                )
                if (collision == null) {
                    accepted[item.source.number] = generated
                    addFingerprintsV10(generated.level, exact, symmetry, arrowLayouts, interactiveLayouts)
                    writeCheckpointV10(parser, checkpointDirectory, generated.level)
                    println(
                        "Campaign V10 certified ${accepted.size}/${work.size}: ${generated.level.id} " +
                            "${item.band.label}, ${generated.level.arrows.size} arrows, " +
                            "${formatRatio(occupancy(generated.level))} occupied",
                    )
                    break
                }
                rejected[collision] = rejected.getOrDefault(collision, 0) + 1
                generated = generateCampaignV10Candidate(
                    item,
                    initialSeed,
                    generated.retry + 1,
                    retriesPerLevel,
                )
                mergeReasonsV10(rejected, generated.rejectedReasons)
            }
        }
    } finally {
        executor.shutdownNow()
    }

    val generated = work.map { requireNotNull(accepted[it.source.number]) }
    val remediated = generated.map(CampaignV10Generated::level)
    val finalLevels = preserved + remediated
    check(finalLevels.map(LevelDefinition::number) == (1..CAMPAIGN_V10_FINAL_NUMBER).toList())
    check(finalLevels.map(LevelDefinition::id).toSet().size == CAMPAIGN_V10_FINAL_NUMBER)
    check(finalLevels.map(ContentFingerprint::exact).toSet().size == CAMPAIGN_V10_FINAL_NUMBER)
    check(finalLevels.map(ContentFingerprint::symmetryNormalized).toSet().size == CAMPAIGN_V10_FINAL_NUMBER)
    check(remediated.map(ContentFingerprint::arrowLayoutSymmetryNormalized).toSet().size == remediated.size)
    check(remediated.map(ContentFingerprint::interactiveLayoutSymmetryNormalized).toSet().size == remediated.size)
    check(remediated.none { ContentFingerprint.exact(it) in infiniteExact })
    check(remediated.none { ContentFingerprint.symmetryNormalized(it) in infiniteSymmetry })
    check(remediated.none { ContentFingerprint.arrowLayoutSymmetryNormalized(it) in infiniteArrows })
    check(remediated.none { ContentFingerprint.interactiveLayoutSymmetryNormalized(it) in infiniteInteractive })
    val distribution = remediated.groupingBy { requireNotNull(it.metadata?.generationProfile) }.eachCount().toSortedMap()
    check(distribution == campaignV10Bands.associate { it.profile.id to it.expectedCount }.toSortedMap())

    val expanded = campaign.copy(
        levels = finalLevels,
        contentVersion = CAMPAIGN_V10_CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION_V5,
    )
    val levelAudits = generated.map { result ->
        val old = result.work.source
        val level = result.level
        CampaignV10LevelAudit(
            number = level.number,
            id = level.id,
            difficultyCandidate = result.work.band.label,
            profile = requireNotNull(level.metadata?.generationProfile),
            seed = requireNotNull(level.metadata?.generatorSeed),
            retry = result.retry,
            topology = result.topology.name,
            arrowCountBefore = old.arrows.size,
            arrowCountAfter = level.arrows.size,
            occupancyBefore = occupancy(old),
            occupancyAfter = occupancy(level),
            exactFingerprint = ContentFingerprint.exact(level),
            symmetryFingerprint = ContentFingerprint.symmetryNormalized(level),
            arrowLayoutFingerprint = ContentFingerprint.arrowLayoutSymmetryNormalized(level),
            interactiveLayoutFingerprint = ContentFingerprint.interactiveLayoutSymmetryNormalized(level),
            previousContentFingerprint = requireNotNull(level.metadata?.previousContentFingerprint),
            elapsedMillis = result.elapsedMillis,
            restoredFromCheckpoint = result.restoredFromCheckpoint,
        )
    }
    val audit = CampaignV10GenerationAudit(
        status = "MECHANICALLY_CERTIFIED_STAGING",
        humanDifficultyStatus = "PENDING_BLINDED_PLAYTEST",
        sourceCampaignSha256 = sha256V10(sourceBytes),
        infiniteCatalogSha256 = sha256V10(infiniteBytes),
        preservedLevelCount = preserved.size,
        remediatedLevelCount = remediated.size,
        finalLevelCount = finalLevels.size,
        contentVersion = CAMPAIGN_V10_CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION_V5,
        initialSeed = initialSeed,
        workers = workers,
        retriesPerLevel = retriesPerLevel,
        profileDistribution = distribution,
        topologyDistribution = generated.groupingBy { it.topology.name }.eachCount().toSortedMap(),
        exactUniqueCount = finalLevels.map(ContentFingerprint::exact).toSet().size,
        symmetryUniqueCount = finalLevels.map(ContentFingerprint::symmetryNormalized).toSet().size,
        remediatedArrowLayoutUniqueCount = remediated.map(ContentFingerprint::arrowLayoutSymmetryNormalized)
            .toSet().size,
        remediatedInteractiveLayoutUniqueCount = remediated
            .map(ContentFingerprint::interactiveLayoutSymmetryNormalized).toSet().size,
        infiniteExactCollisions = 0,
        infiniteSymmetryCollisions = 0,
        infiniteArrowLayoutCollisions = 0,
        infiniteInteractiveLayoutCollisions = 0,
        rejectedCandidateReasons = rejected.toSortedMap(),
        levels = levelAudits,
    )
    val json = Json { prettyPrint = true; encodeDefaults = true }
    writeAtomicallyV10(
        File(outputDirectory, "Magnetrail_Campaign_Levels_v10.json"),
        parser.encodeCatalog(expanded) + "\n",
    )
    writeAtomicallyV10(
        File(outputDirectory, "CAMPAIGN_V10_GENERATION_AUDIT.json"),
        json.encodeToString(audit) + "\n",
    )
    writeAtomicallyV10(
        File(outputDirectory, "CAMPAIGN_V10_GENERATION_REPORT.md"),
        campaignV10Markdown(audit),
    )
    println("Campaign V10 staging complete: ${expanded.levels.size} certified levels")
}

fun promoteCampaignV10Remediation(options: Map<String, String>) {
    check(options.requiredV10("authorization") == CAMPAIGN_V10_AUTHORIZATION) {
        "Campaign V10 promotion is not authorized"
    }
    val campaignFile = File(options.requiredV10("campaign"))
    val stagedFile = File(options.requiredV10("staged-campaign"))
    val stagedAuditFile = File(options.requiredV10("staged-audit"))
    val stagedReportFile = File(options.requiredV10("staged-report"))
    val infiniteFile = File(options.requiredV10("infinite"))
    val sourceSnapshot = File(options.requiredV10("source-snapshot"))
    val publishedAudit = File(options.requiredV10("published-audit"))
    val publishedReport = File(options.requiredV10("published-report"))
    val resultFile = File(options.requiredV10("result"))
    val parser = LevelParser()
    val canonicalBytes = campaignFile.readBytes()
    val canonical = parser.parseCatalog(canonicalBytes.decodeToString())
    val staged = parser.parseCatalog(stagedFile.readText())
    val infinite = parser.parseCatalog(infiniteFile.readText())
    val alreadyPromoted = canonical.contentVersion == CAMPAIGN_V10_CONTENT_VERSION
    val sourceBytes = if (alreadyPromoted) {
        check(sourceSnapshot.isFile) { "Campaign V10 source snapshot is missing" }
        sourceSnapshot.readBytes()
    } else {
        canonicalBytes
    }
    val source = parser.parseCatalog(sourceBytes.decodeToString())
    validateV10Source(source)
    check(staged.contentVersion == CAMPAIGN_V10_CONTENT_VERSION)
    check(staged.levels.size == CAMPAIGN_V10_FINAL_NUMBER)
    if (alreadyPromoted) check(canonical == staged) { "Canonical Campaign V10 differs from staging" }
    check(staged.levels.take(CAMPAIGN_V10_PRESERVED_COUNT) == source.levels.take(CAMPAIGN_V10_PRESERVED_COUNT))
    val remediated = staged.levels.drop(CAMPAIGN_V10_PRESERVED_COUNT)
    check(remediated.map(ContentFingerprint::arrowLayoutSymmetryNormalized).toSet().size == remediated.size)
    check(remediated.map(ContentFingerprint::interactiveLayoutSymmetryNormalized).toSet().size == remediated.size)
    val infiniteExact = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::exact)
    val infiniteSymmetry = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::symmetryNormalized)
    val infiniteArrows = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::arrowLayoutSymmetryNormalized)
    val infiniteInteractive = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::interactiveLayoutSymmetryNormalized)
    check(remediated.none { ContentFingerprint.exact(it) in infiniteExact })
    check(remediated.none { ContentFingerprint.symmetryNormalized(it) in infiniteSymmetry })
    check(remediated.none { ContentFingerprint.arrowLayoutSymmetryNormalized(it) in infiniteArrows })
    check(remediated.none { ContentFingerprint.interactiveLayoutSymmetryNormalized(it) in infiniteInteractive })
    remediated.zip(source.levels.drop(CAMPAIGN_V10_PRESERVED_COUNT)).forEach { (new, old) ->
        check(new.metadata?.previousContentFingerprint == ContentFingerprint.exact(old))
    }

    sourceSnapshot.parentFile.mkdirs()
    if (!sourceSnapshot.exists()) writeAtomicallyV10(sourceSnapshot, sourceBytes.decodeToString())
    if (!alreadyPromoted) writeAtomicallyV10(campaignFile, parser.encodeCatalog(staged) + "\n")
    writeAtomicallyV10(publishedAudit, stagedAuditFile.readText())
    writeAtomicallyV10(publishedReport, stagedReportFile.readText())
    writeAtomicallyV10(
        resultFile,
        """# Campaign V10 density-remediation promotion

- Status: **PROMOTED — PROJECT OWNER DIRECTED, DIFFICULTY APPROVAL PENDING**
- Catalog: 2,205 levels (Levels 1–205 preserved; Levels 206–2,205 remediated)
- Content version: 9 → 10
- Difficulty labels: **candidate labels pending a new blinded human playtest**
- Remediated arrow silhouettes: 2,000/2,000 unique under rotation/reflection
- Remediated interactive layouts: 2,000/2,000 unique under rotation/reflection
- Infinite exact, symmetry, arrow-layout, and interactive-layout collisions: 0
- Progress migration: each changed level records its V9 fingerprint
- Source SHA-256: `${sha256V10(sourceBytes)}`
- Promoted SHA-256: `${sha256V10(campaignFile.readBytes())}`
""",
    )
    println(if (alreadyPromoted) "Verified Campaign V10 promotion" else "Promoted Campaign V10")
}

internal fun campaignV10TargetArrowCount(profileId: String, size: Int): Int = when (profileId) {
    GenerationProfilesCampaignV10.EASY.id -> when (size) { 4 -> 6; 5 -> 7; 8 -> 12; else -> error("size") }
    GenerationProfilesCampaignV10.MEDIUM.id -> when (size) { 5 -> 8; 6 -> 10; 8 -> 14; else -> error("size") }
    GenerationProfilesCampaignV10.HARD.id -> when (size) { 5 -> 10; 6 -> 12; 7 -> 14; 8 -> 16; else -> error("size") }
    GenerationProfilesCampaignV10.SUPER_HARD.id -> 14
    GenerationProfilesCampaignV10.EXPERT.id -> 16
    GenerationProfilesCampaignV10.MASTER.id -> 18
    else -> error("Unsupported V10 profile $profileId")
}

internal fun campaignV10ExpectedProfileDistribution(): Map<String, Int> =
    campaignV10Bands.associate { it.profile.id to it.expectedCount }.toSortedMap()

private fun generateCampaignV10Candidate(
    work: CampaignV10Work,
    initialSeed: Long,
    startRetry: Int,
    retriesPerLevel: Int,
): CampaignV10Generated {
    val rejected = linkedMapOf<String, Int>()
    var elapsed = 0L
    for (retry in startRetry until retriesPerLevel) {
        val seed = seedForV10(work.source.number, retry, initialSeed)
        val topology = topologyForV10(work.source.number, retry)
        val started = System.nanoTime()
        val remediated = CampaignDensityRemediatorV10().remediate(
            source = work.source,
            spec = DensityRemediationSpecV10(
                targetArrowCount = campaignV10TargetArrowCount(work.band.profile.id, work.source.width),
                minimumOccupancyRatio = work.band.profile.objectDensityRange.minimum,
                topology = topology,
            ),
            seed = seed,
        )
        if (remediated == null) {
            rejected["density-construction-failed"] = rejected.getOrDefault("density-construction-failed", 0) + 1
            elapsed += (System.nanoTime() - started) / 1_000_000L
            continue
        }
        when (
            val certification = CertificationPipelineV5().certifyForHumanCalibration(
                level = remediated.level,
                profile = work.band.profile,
                seed = seed,
                packId = requireNotNull(work.source.metadata).packId,
                contentVersion = CAMPAIGN_V10_CONTENT_VERSION,
                previousContentFingerprint = ContentFingerprint.exact(work.source),
            )
        ) {
            is HumanCalibrationCertificationResultV5.Rejected -> certification.reasons.forEach { reason ->
                rejected[reason] = rejected.getOrDefault(reason, 0) + 1
            }
            is HumanCalibrationCertificationResultV5.Accepted -> {
                elapsed += (System.nanoTime() - started) / 1_000_000L
                return CampaignV10Generated(
                    work = work,
                    level = certification.level,
                    retry = retry,
                    topology = topology,
                    elapsedMillis = elapsed,
                    restoredFromCheckpoint = false,
                    rejectedReasons = rejected,
                )
            }
        }
        elapsed += (System.nanoTime() - started) / 1_000_000L
    }
    error(
        "Campaign V10 exhausted ${work.band.label} level ${work.source.number}; " +
            "retries=$startRetry..${retriesPerLevel - 1}; " +
            "top=${rejected.entries.sortedByDescending { it.value }.take(10)}",
    )
}

private fun validateV10Source(catalog: LevelCatalog) {
    check(catalog.contentVersion == CAMPAIGN_V10_SOURCE_VERSION)
    check(catalog.levels.size == CAMPAIGN_V10_FINAL_NUMBER)
    check(catalog.levels.map(LevelDefinition::number) == (1..CAMPAIGN_V10_FINAL_NUMBER).toList())
    val distribution = catalog.levels.drop(CAMPAIGN_V10_PRESERVED_COUNT)
        .groupingBy { requireNotNull(it.metadata?.generationProfile) }.eachCount().toSortedMap()
    check(distribution == campaignV10Bands.associate { it.sourceProfileId to it.expectedCount }.toSortedMap()) {
        "Unexpected Campaign V9 source distribution: $distribution"
    }
}

private fun bandForV10Source(level: LevelDefinition): CampaignV10Band = requireNotNull(
    campaignV10Bands.firstOrNull { it.sourceProfileId == level.metadata?.generationProfile },
) { "Unsupported V9 profile on level ${level.number}: ${level.metadata?.generationProfile}" }

private fun validateRestoredV10(
    level: LevelDefinition,
    work: CampaignV10Work,
    initialSeed: Long,
    retriesPerLevel: Int,
) {
    check(level.id == work.source.id && level.number == work.source.number)
    check(level.metadata?.contentVersion == CAMPAIGN_V10_CONTENT_VERSION)
    check(level.metadata?.generationProfile == work.band.profile.id)
    check(level.metadata?.previousContentFingerprint == ContentFingerprint.exact(work.source))
    check(level.arrows.size == campaignV10TargetArrowCount(work.band.profile.id, level.width))
    check(occupancy(level) >= work.band.profile.objectDensityRange.minimum)
    retryForV10(level, work.source.number, initialSeed, retriesPerLevel)
}

private fun restoredV10(
    level: LevelDefinition,
    work: CampaignV10Work,
    initialSeed: Long,
    retriesPerLevel: Int,
): CampaignV10Generated {
    val retry = retryForV10(level, work.source.number, initialSeed, retriesPerLevel)
    return CampaignV10Generated(
        work = work,
        level = level,
        retry = retry,
        topology = topologyForV10(work.source.number, retry),
        elapsedMillis = null,
        restoredFromCheckpoint = true,
        rejectedReasons = emptyMap(),
    )
}

private fun retryForV10(level: LevelDefinition, number: Int, initialSeed: Long, limit: Int): Int {
    val metadataSeed = requireNotNull(level.metadata?.generatorSeed)
    return (0 until limit).firstOrNull { seedForV10(number, it, initialSeed) == metadataSeed }
        ?: error("Unexpected V10 generator seed $metadataSeed on level $number")
}

private fun collisionReasonV10(
    level: LevelDefinition,
    exact: Set<String>,
    symmetry: Set<String>,
    arrows: Set<String>,
    interactive: Set<String>,
    infiniteExact: Set<String>,
    infiniteSymmetry: Set<String>,
    infiniteArrows: Set<String>,
    infiniteInteractive: Set<String>,
): String? = when {
    ContentFingerprint.exact(level) in infiniteExact -> "infinite-exact-duplicate"
    ContentFingerprint.symmetryNormalized(level) in infiniteSymmetry -> "infinite-symmetry-duplicate"
    ContentFingerprint.arrowLayoutSymmetryNormalized(level) in infiniteArrows -> "infinite-arrow-layout-duplicate"
    ContentFingerprint.interactiveLayoutSymmetryNormalized(level) in infiniteInteractive ->
        "infinite-interactive-layout-duplicate"
    ContentFingerprint.exact(level) in exact -> "campaign-exact-duplicate"
    ContentFingerprint.symmetryNormalized(level) in symmetry -> "campaign-symmetry-duplicate"
    ContentFingerprint.arrowLayoutSymmetryNormalized(level) in arrows -> "campaign-arrow-layout-duplicate"
    ContentFingerprint.interactiveLayoutSymmetryNormalized(level) in interactive ->
        "campaign-interactive-layout-duplicate"
    else -> null
}

private fun addFingerprintsV10(
    level: LevelDefinition,
    exact: MutableSet<String>,
    symmetry: MutableSet<String>,
    arrows: MutableSet<String>,
    interactive: MutableSet<String>,
) {
    exact += ContentFingerprint.exact(level)
    symmetry += ContentFingerprint.symmetryNormalized(level)
    arrows += ContentFingerprint.arrowLayoutSymmetryNormalized(level)
    interactive += ContentFingerprint.interactiveLayoutSymmetryNormalized(level)
}

private fun writeCheckpointV10(parser: LevelParser, directory: File, level: LevelDefinition) {
    val catalog = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = "magnetrail-core-1",
        catalogId = "magnetrail-campaign-v10-checkpoint",
        levels = listOf(level),
        contentVersion = CAMPAIGN_V10_CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION_V5,
    )
    writeAtomicallyV10(
        File(directory, checkpointNameV10(level.number)),
        parser.encodeCatalog(catalog) + "\n",
    )
}

private fun campaignV10Markdown(audit: CampaignV10GenerationAudit): String {
    val arrowCountBefore = audit.levels.sumOf(CampaignV10LevelAudit::arrowCountBefore)
    val arrowCountAfter = audit.levels.sumOf(CampaignV10LevelAudit::arrowCountAfter)
    val arrowIncreasePercent = (arrowCountAfter - arrowCountBefore) * 100.0 / arrowCountBefore
    val meanOccupancyBefore = audit.levels.map(CampaignV10LevelAudit::occupancyBefore).average()
    val meanOccupancyAfter = audit.levels.map(CampaignV10LevelAudit::occupancyAfter).average()
    val occupancyMinimum = audit.levels.minOf(CampaignV10LevelAudit::occupancyAfter)
    val occupancyMaximum = audit.levels.maxOf(CampaignV10LevelAudit::occupancyAfter)
    return """# Campaign V10 density-remediation report

- Status: **${audit.status}**
- Human difficulty status: **${audit.humanDifficultyStatus}**
- Preserved/remediated/final levels: ${audit.preservedLevelCount}/${audit.remediatedLevelCount}/${audit.finalLevelCount}
- Remediated arrow count: $arrowCountBefore → $arrowCountAfter (${formatDecimalV10(arrowIncreasePercent)}% increase; every remediated board increased)
- Mean occupied cells: ${formatRatio(meanOccupancyBefore)} → ${formatRatio(meanOccupancyAfter)} (V10 range ${formatRatio(occupancyMinimum)}–${formatRatio(occupancyMaximum)})
- Profile distribution: ${audit.profileDistribution}
- Cascade topology distribution: ${audit.topologyDistribution}
- Full-board exact uniqueness: ${audit.exactUniqueCount}/${audit.finalLevelCount}
- Full-board rotation/reflection uniqueness: ${audit.symmetryUniqueCount}/${audit.finalLevelCount}
- Remediated arrow-silhouette uniqueness: ${audit.remediatedArrowLayoutUniqueCount}/${audit.remediatedLevelCount}
- Remediated interactive-layout uniqueness: ${audit.remediatedInteractiveLayoutUniqueCount}/${audit.remediatedLevelCount}
- Infinite exact/symmetry/arrow/interactive collisions: ${audit.infiniteExactCollisions}/${audit.infiniteSymmetryCollisions}/${audit.infiniteArrowLayoutCollisions}/${audit.infiniteInteractiveLayoutCollisions}
- Content/generator version: ${audit.contentVersion}/${audit.generatorVersion}
- Workers/retries: ${audit.workers}/${audit.retriesPerLevel}
- Seed: ${audit.initialSeed}

Levels 206–2,205 were rebuilt from their V9 certified puzzle cores. Each received a deterministic,
magnet-aware ordered arrow cascade, enough solution-preserving wall structure to satisfy its
density floor, a fresh production-engine solution witness and replay certification, and a V9
fingerprint migration link. Automated difficulty metrics are diagnostic until human approval;
wall-only and rotation/reflection variants are hard-rejected.

The six labels are candidates, not approved difficulty claims. A new blinded human playtest is
required because the submitted V9 study rejected the previous calibration.
"""
}

private fun seedForV10(number: Int, retry: Int, initialSeed: Long): Long =
    initialSeed + (number - CAMPAIGN_V10_PRESERVED_COUNT) * CAMPAIGN_V10_SLOT_GAMMA +
        retry * CAMPAIGN_V10_RETRY_GAMMA

private fun topologyForV10(number: Int, retry: Int): DensityTopologyV10 =
    DensityTopologyV10.entries[(number + retry) % DensityTopologyV10.entries.size]

private fun occupancy(level: LevelDefinition): Double =
    (level.arrows.size + level.magnets.size + level.walls.size).toDouble() / (level.width * level.height)

private fun formatRatio(value: Double): String = "%.1f%%".format(value * 100.0)

private fun formatDecimalV10(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun checkpointNameV10(number: Int): String = "campaign-${number.toString().padStart(4, '0')}.json"

private fun mergeReasonsV10(target: MutableMap<String, Int>, source: Map<String, Int>) {
    source.forEach { (reason, count) -> target[reason] = target.getOrDefault(reason, 0) + count }
}

private fun writeAtomicallyV10(file: File, content: String) {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, ".${file.name}.tmp")
    temporary.writeText(content)
    runCatching {
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }.getOrElse {
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun sha256V10(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun Map<String, String>.requiredV10(key: String): String = requireNotNull(this[key]) { "Missing --$key" }

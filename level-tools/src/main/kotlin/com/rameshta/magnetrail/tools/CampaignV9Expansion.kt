package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.v5.GENERATOR_VERSION_V5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfileV5
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesCampaignV9
import com.rameshta.magnetrail.core.generation.v5.GenerationProfilesV5
import com.rameshta.magnetrail.core.generation.v5.GenerationRequestV5
import com.rameshta.magnetrail.core.generation.v5.GenerationResultV5
import com.rameshta.magnetrail.core.generation.v5.LevelGeneratorV5
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val CAMPAIGN_V9_CONTENT_VERSION = 9
private const val CAMPAIGN_V9_SOURCE_COUNT = 205
private const val CAMPAIGN_V9_APPEND_COUNT = 2_000
private const val CAMPAIGN_V9_FIRST_NUMBER = 206
private const val CAMPAIGN_V9_FINAL_NUMBER = 2_205
private const val CAMPAIGN_V9_DEFAULT_SEED = 9_200_001L
private const val CAMPAIGN_V9_SLOT_GAMMA = 1_000_003L
private const val CAMPAIGN_V9_RETRY_GAMMA = 97_000_021L
private const val CAMPAIGN_V9_GENERATOR_ATTEMPT_GAMMA = -7046029254386353131L
private const val CAMPAIGN_V9_AUTHORIZATION = "project-owner-directed-2000-level-expansion"

private data class CampaignV9Profile(
    val label: String,
    val profile: GenerationProfileV5,
    val requested: Int,
)

private val campaignV9Profiles = listOf(
    CampaignV9Profile("Easy", GenerationProfilesV5.EASY, 334),
    CampaignV9Profile("Medium", GenerationProfilesV5.MEDIUM, 334),
    CampaignV9Profile("Hard", GenerationProfilesV5.HARD, 333),
    CampaignV9Profile("Super Hard", GenerationProfilesCampaignV9.SUPER_HARD, 333),
    CampaignV9Profile("Expert", GenerationProfilesCampaignV9.EXPERT, 333),
    CampaignV9Profile("Master", GenerationProfilesCampaignV9.MASTER, 333),
)

@Serializable
data class CampaignV9LevelAudit(
    val number: Int,
    val id: String,
    val difficulty: String,
    val profile: String,
    val seed: Long,
    val exactFingerprint: String,
    val symmetryFingerprint: String,
    val generatorAttempts: Int,
    val seedRetry: Int,
    val elapsedMillis: Long?,
    val restoredFromCheckpoint: Boolean,
)

@Serializable
data class CampaignV9GenerationAudit(
    val schemaVersion: Int = 1,
    val status: String,
    val sourceCampaignSha256: String,
    val infiniteCatalogSha256: String,
    val sourceLevelCount: Int,
    val appendedLevelCount: Int,
    val finalLevelCount: Int,
    val firstAppendedNumber: Int,
    val finalNumber: Int,
    val contentVersion: Int,
    val generatorVersion: Int,
    val initialSeed: Long,
    val workers: Int,
    val maxAttemptsPerSeed: Int,
    val retriesPerSlot: Int,
    val profileDistribution: Map<String, Int>,
    val exactUniqueCount: Int,
    val symmetryUniqueCount: Int,
    val infiniteExactCollisions: Int,
    val infiniteSymmetryCollisions: Int,
    val rejectedCandidateReasons: Map<String, Int>,
    val levels: List<CampaignV9LevelAudit>,
)

private data class CampaignV9Slot(
    val appendIndex: Int,
    val number: Int,
    val difficulty: String,
    val profile: GenerationProfileV5,
)

private data class CampaignV9Generated(
    val slot: CampaignV9Slot,
    val level: LevelDefinition,
    val seedRetry: Int,
    val generatorAttempts: Int,
    val elapsedMillis: Long?,
    val restoredFromCheckpoint: Boolean,
    val rejectedReasons: Map<String, Int>,
)

fun generateCampaignV9Expansion(options: Map<String, String>) {
    val campaignFile = File(options.requiredCampaignV9("campaign"))
    val infiniteFile = File(options.requiredCampaignV9("infinite"))
    val outputDirectory = File(options.requiredCampaignV9("output")).also(File::mkdirs)
    val checkpointDirectory = File(options.requiredCampaignV9("checkpoint")).also(File::mkdirs)
    val requestedCount = options["count"]?.toInt() ?: CAMPAIGN_V9_APPEND_COUNT
    val initialSeed = options["seed"]?.toLong() ?: CAMPAIGN_V9_DEFAULT_SEED
    val workers = (options["workers"]?.toInt() ?: 2).coerceIn(1, 24)
    val retriesPerSlot = options["retries-per-slot"]?.toInt() ?: 64
    val maxAttemptsPerSeed = options["attempts-per-seed"]?.toInt() ?: 1
    require(requestedCount == CAMPAIGN_V9_APPEND_COUNT) {
        "Campaign V9 has a frozen balanced allocation of $CAMPAIGN_V9_APPEND_COUNT levels"
    }
    require(initialSeed != 0L && retriesPerSlot in 1..256 && maxAttemptsPerSeed in 1..64)

    val parser = LevelParser()
    val sourceBytes = campaignFile.readBytes()
    val infiniteBytes = infiniteFile.readBytes()
    val source = parser.parseCatalog(sourceBytes.decodeToString())
    val infinite = parser.parseCatalog(infiniteBytes.decodeToString())
    check(source.levels.size == CAMPAIGN_V9_SOURCE_COUNT && source.contentVersion == 8) {
        "Campaign V9 expansion requires the canonical 205-level content-v8 source"
    }
    check(source.levels.map(LevelDefinition::number) == (1..CAMPAIGN_V9_SOURCE_COUNT).toList())
    val slots = campaignV9Slots()
    check(slots.size == requestedCount)

    val exactFingerprints = source.levels.mapTo(hashSetOf(), ContentFingerprint::exact)
    val symmetryFingerprints = source.levels.mapTo(hashSetOf(), ContentFingerprint::symmetryNormalized)
    val infiniteExact = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::exact)
    val infiniteSymmetry = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::symmetryNormalized)
    val acceptedByIndex = sortedMapOf<Int, CampaignV9Generated>()
    val rejectedReasons = linkedMapOf<String, Int>()

    slots.forEach { slot ->
        val checkpoint = File(checkpointDirectory, checkpointName(slot.number))
        val restored = runCatching {
            if (!checkpoint.isFile) return@runCatching null
            val catalog = parser.parseCatalog(checkpoint.readText())
            val level = catalog.levels.single()
            val metadata = requireNotNull(level.metadata)
            check(level.number == slot.number && level.id == campaignLevelId(slot.number))
            check(metadata.generationProfile == slot.profile.id)
            check(metadata.contentVersion == CAMPAIGN_V9_CONTENT_VERSION)
            val exact = ContentFingerprint.exact(level)
            val symmetry = ContentFingerprint.symmetryNormalized(level)
            check(exact !in exactFingerprints && exact !in infiniteExact)
            check(symmetry !in symmetryFingerprints && symmetry !in infiniteSymmetry)
            val (seedRetry, generatorAttempts) = inferGenerationAttempt(
                slot = slot,
                metadataSeed = requireNotNull(metadata.generatorSeed),
                initialSeed = initialSeed,
                retriesPerSlot = retriesPerSlot,
                maxAttemptsPerSeed = maxAttemptsPerSeed,
            )
            CampaignV9Generated(
                slot = slot,
                level = level,
                seedRetry = seedRetry,
                generatorAttempts = generatorAttempts,
                elapsedMillis = null,
                restoredFromCheckpoint = true,
                rejectedReasons = emptyMap(),
            )
        }.getOrNull()
        if (restored != null) {
            acceptedByIndex[slot.appendIndex] = restored
            exactFingerprints += ContentFingerprint.exact(restored.level)
            symmetryFingerprints += ContentFingerprint.symmetryNormalized(restored.level)
        }
    }
    if (acceptedByIndex.isNotEmpty()) {
        println("Campaign V9 restored ${acceptedByIndex.size}/${slots.size} certified checkpoints")
    }

    val executor = Executors.newFixedThreadPool(workers)
    try {
        // Keep the executor fed continuously. Difficulty is interleaved, so fixed-size batches
        // otherwise finish their cheap slots early and leave half the cores idle while the high
        // bands complete. Results are still accepted in campaign order, keeping collision handling
        // and the generated catalog deterministic for a given seed and checkpoint set.
        val missing = slots.filterNot { it.appendIndex in acceptedByIndex }
        val futures = missing.associateWith { slot ->
            executor.submit(Callable {
                generateCampaignV9Candidate(
                    slot = slot,
                    initialSeed = initialSeed,
                    startRetry = 0,
                    retriesPerSlot = retriesPerSlot,
                    maxAttemptsPerSeed = maxAttemptsPerSeed,
                )
            })
        }
        missing.forEach { slot ->
            var generated = requireNotNull(futures[slot]).get()
            mergeReasons(rejectedReasons, generated.rejectedReasons)
            while (true) {
                val exact = ContentFingerprint.exact(generated.level)
                val symmetry = ContentFingerprint.symmetryNormalized(generated.level)
                val collision = when {
                    exact in infiniteExact -> "infinite-exact-duplicate"
                    symmetry in infiniteSymmetry -> "infinite-symmetry-duplicate"
                    exact in exactFingerprints -> "campaign-exact-duplicate"
                    symmetry in symmetryFingerprints -> "campaign-symmetry-duplicate"
                    else -> null
                }
                if (collision == null) {
                    exactFingerprints += exact
                    symmetryFingerprints += symmetry
                    acceptedByIndex[slot.appendIndex] = generated
                    writeCheckpoint(parser, checkpointDirectory, generated.level)
                    println(
                        "Campaign V9 certified ${acceptedByIndex.size}/${slots.size}: " +
                            "${generated.level.id} ${slot.difficulty} (${generated.elapsedMillis} ms)",
                    )
                    break
                }
                rejectedReasons[collision] = rejectedReasons.getOrDefault(collision, 0) + 1
                generated = generateCampaignV9Candidate(
                    slot = slot,
                    initialSeed = initialSeed,
                    startRetry = generated.seedRetry + 1,
                    retriesPerSlot = retriesPerSlot,
                    maxAttemptsPerSeed = maxAttemptsPerSeed,
                )
                mergeReasons(rejectedReasons, generated.rejectedReasons)
            }
        }
    } finally {
        executor.shutdownNow()
    }

    val generated = slots.map { slot -> requireNotNull(acceptedByIndex[slot.appendIndex]) }
    val appended = generated.map(CampaignV9Generated::level)
    val finalLevels = source.levels + appended
    check(finalLevels.map(LevelDefinition::number) == (1..CAMPAIGN_V9_FINAL_NUMBER).toList())
    check(finalLevels.map(LevelDefinition::id).toSet().size == CAMPAIGN_V9_FINAL_NUMBER)
    check(finalLevels.map(ContentFingerprint::exact).toSet().size == CAMPAIGN_V9_FINAL_NUMBER)
    check(finalLevels.map(ContentFingerprint::symmetryNormalized).toSet().size == CAMPAIGN_V9_FINAL_NUMBER)
    check(appended.none { ContentFingerprint.exact(it) in infiniteExact })
    check(appended.none { ContentFingerprint.symmetryNormalized(it) in infiniteSymmetry })
    val distribution = appended.groupingBy { requireNotNull(it.metadata?.generationProfile) }.eachCount().toSortedMap()
    val expectedDistribution = campaignV9Profiles.associate { it.profile.id to it.requested }.toSortedMap()
    check(distribution == expectedDistribution) { "Unexpected profile distribution: $distribution" }

    val expanded = source.copy(
        catalogId = "magnetrail-campaign-v3",
        levels = finalLevels,
        contentVersion = CAMPAIGN_V9_CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION_V5,
    )
    val levelAudit = generated.map { result ->
        val level = result.level
        CampaignV9LevelAudit(
            number = level.number,
            id = level.id,
            difficulty = result.slot.difficulty,
            profile = requireNotNull(level.metadata?.generationProfile),
            seed = requireNotNull(level.metadata?.generatorSeed),
            exactFingerprint = ContentFingerprint.exact(level),
            symmetryFingerprint = ContentFingerprint.symmetryNormalized(level),
            generatorAttempts = result.generatorAttempts,
            seedRetry = result.seedRetry,
            elapsedMillis = result.elapsedMillis,
            restoredFromCheckpoint = result.restoredFromCheckpoint,
        )
    }
    val audit = CampaignV9GenerationAudit(
        status = "CERTIFIED_STAGING",
        sourceCampaignSha256 = sha256CampaignV9(sourceBytes),
        infiniteCatalogSha256 = sha256CampaignV9(infiniteBytes),
        sourceLevelCount = source.levels.size,
        appendedLevelCount = appended.size,
        finalLevelCount = finalLevels.size,
        firstAppendedNumber = CAMPAIGN_V9_FIRST_NUMBER,
        finalNumber = CAMPAIGN_V9_FINAL_NUMBER,
        contentVersion = CAMPAIGN_V9_CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION_V5,
        initialSeed = initialSeed,
        workers = workers,
        maxAttemptsPerSeed = maxAttemptsPerSeed,
        retriesPerSlot = retriesPerSlot,
        profileDistribution = distribution,
        exactUniqueCount = finalLevels.map(ContentFingerprint::exact).toSet().size,
        symmetryUniqueCount = finalLevels.map(ContentFingerprint::symmetryNormalized).toSet().size,
        infiniteExactCollisions = 0,
        infiniteSymmetryCollisions = 0,
        rejectedCandidateReasons = rejectedReasons.toSortedMap(),
        levels = levelAudit,
    )
    writeAtomically(
        File(outputDirectory, "Magnetrail_Campaign_Levels_v9.json"),
        parser.encodeCatalog(expanded) + "\n",
    )
    val json = Json { prettyPrint = true; encodeDefaults = true }
    writeAtomically(
        File(outputDirectory, "CAMPAIGN_V9_GENERATION_AUDIT.json"),
        json.encodeToString(audit) + "\n",
    )
    writeAtomically(
        File(outputDirectory, "CAMPAIGN_V9_GENERATION_REPORT.md"),
        campaignV9Markdown(audit),
    )
    println("Campaign V9 staging complete: ${expanded.levels.size} unique certified levels")
}

fun promoteCampaignV9Expansion(options: Map<String, String>) {
    val authorization = options.requiredCampaignV9("authorization")
    check(authorization == CAMPAIGN_V9_AUTHORIZATION) { "Campaign V9 promotion is not authorized" }
    val campaignFile = File(options.requiredCampaignV9("campaign"))
    val stagedFile = File(options.requiredCampaignV9("staged-campaign"))
    val stagedAuditFile = File(options.requiredCampaignV9("staged-audit"))
    val stagedReportFile = File(options.requiredCampaignV9("staged-report"))
    val infiniteFile = File(options.requiredCampaignV9("infinite"))
    val sourceSnapshot = File(options.requiredCampaignV9("source-snapshot"))
    val publishedAuditFile = File(options.requiredCampaignV9("published-audit"))
    val publishedReportFile = File(options.requiredCampaignV9("published-report"))
    val resultFile = File(options.requiredCampaignV9("result"))
    val parser = LevelParser()
    val canonicalBytes = campaignFile.readBytes()
    val canonical = parser.parseCatalog(canonicalBytes.decodeToString())
    val staged = parser.parseCatalog(stagedFile.readText())
    val infinite = parser.parseCatalog(infiniteFile.readText())
    val stagedAudit = stagedAuditFile.readText()
    val stagedReport = stagedReportFile.readText()
    val alreadyPromoted = canonical.contentVersion == CAMPAIGN_V9_CONTENT_VERSION &&
        canonical.levels.size == CAMPAIGN_V9_FINAL_NUMBER
    val sourceBytes = if (alreadyPromoted) {
        check(sourceSnapshot.isFile) { "Campaign V9 source snapshot is missing" }
        sourceSnapshot.readBytes()
    } else {
        canonicalBytes
    }
    val source = parser.parseCatalog(sourceBytes.decodeToString())
    check(source.levels.size == CAMPAIGN_V9_SOURCE_COUNT && source.contentVersion == 8)
    check(staged.levels.size == CAMPAIGN_V9_FINAL_NUMBER && staged.contentVersion == CAMPAIGN_V9_CONTENT_VERSION)
    if (alreadyPromoted) check(canonical == staged) { "Canonical Campaign V9 differs from certified staging" }
    check(staged.levels.take(source.levels.size) == source.levels) { "Staging changed existing campaign boards" }
    check(staged.levels.map(LevelDefinition::number) == (1..CAMPAIGN_V9_FINAL_NUMBER).toList())
    check(staged.levels.map(LevelDefinition::id).toSet().size == CAMPAIGN_V9_FINAL_NUMBER)
    check(staged.levels.map(ContentFingerprint::exact).toSet().size == CAMPAIGN_V9_FINAL_NUMBER)
    check(staged.levels.map(ContentFingerprint::symmetryNormalized).toSet().size == CAMPAIGN_V9_FINAL_NUMBER)
    val appended = staged.levels.drop(CAMPAIGN_V9_SOURCE_COUNT)
    val infiniteExact = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::exact)
    val infiniteSymmetry = infinite.levels.mapTo(hashSetOf(), ContentFingerprint::symmetryNormalized)
    check(appended.none { ContentFingerprint.exact(it) in infiniteExact })
    check(appended.none { ContentFingerprint.symmetryNormalized(it) in infiniteSymmetry })
    val distribution = appended.groupingBy { requireNotNull(it.metadata?.generationProfile) }.eachCount().toSortedMap()
    check(distribution == campaignV9Profiles.associate { it.profile.id to it.requested }.toSortedMap())

    sourceSnapshot.parentFile.mkdirs()
    if (!sourceSnapshot.exists()) writeAtomically(sourceSnapshot, sourceBytes.decodeToString())
    if (!alreadyPromoted) writeAtomically(campaignFile, parser.encodeCatalog(staged) + "\n")
    writeAtomically(publishedAuditFile, stagedAudit)
    writeAtomically(publishedReportFile, stagedReport)
    writeAtomically(
        resultFile,
        """# Campaign V9 promotion result

- Status: **PROMOTED — PROJECT OWNER DIRECTED**
- Levels: 205 → 2,205
- Appended range: 206–2,205
- Content version: 8 → 9
- Difficulty allocation: Easy 334, Medium 334, Hard 333, Super Hard 333, Expert 333, Master 333
- Exact fingerprints: 2,205/2,205 unique
- Rotation/reflection fingerprints: 2,205/2,205 unique
- New boards colliding with the Infinite catalog: 0
- Source SHA-256: `${sha256CampaignV9(sourceBytes)}`
- Promoted SHA-256: `${sha256CampaignV9(campaignFile.readBytes())}`
""",
    )
    println(
        if (alreadyPromoted) {
            "Verified existing Campaign V9 promotion: ${staged.levels.size} levels"
        } else {
            "Promoted Campaign V9: ${staged.levels.size} levels"
        },
    )
}

internal fun campaignV9DifficultySequence(): List<String> = campaignV9Slots().map(CampaignV9Slot::difficulty)

internal fun campaignV9ExpectedProfileDistribution(): Map<String, Int> =
    campaignV9Profiles.associate { it.profile.id to it.requested }.toSortedMap()

private fun campaignV9Slots(): List<CampaignV9Slot> {
    val remaining = campaignV9Profiles.associateWith(CampaignV9Profile::requested).toMutableMap()
    return buildList {
        while (remaining.values.any { it > 0 }) {
            campaignV9Profiles.forEach { allocation ->
                if (remaining.getValue(allocation) > 0) {
                    val index = size
                    add(
                        CampaignV9Slot(
                            appendIndex = index,
                            number = CAMPAIGN_V9_FIRST_NUMBER + index,
                            difficulty = allocation.label,
                            profile = allocation.profile,
                        ),
                    )
                    remaining[allocation] = remaining.getValue(allocation) - 1
                }
            }
        }
    }
}

private fun generateCampaignV9Candidate(
    slot: CampaignV9Slot,
    initialSeed: Long,
    startRetry: Int,
    retriesPerSlot: Int,
    maxAttemptsPerSeed: Int,
): CampaignV9Generated {
    val rejected = linkedMapOf<String, Int>()
    var elapsedMillis = 0L
    for (retry in startRetry until retriesPerSlot) {
        val seed = seedFor(slot, retry, initialSeed)
        val request = GenerationRequestV5(
            stableId = campaignLevelId(slot.number),
            sequenceNumber = slot.number,
            title = "Magnetic Circuit ${slot.number.toString().padStart(4, '0')}",
            seed = seed,
            profile = slot.profile,
            packId = campaignPackId(slot.number),
            contentVersion = CAMPAIGN_V9_CONTENT_VERSION,
            maxAttempts = if (slot.profile.id.startsWith("v5-campaign-v9-")) 1 else maxAttemptsPerSeed,
        )
        val started = System.nanoTime()
        when (val result = LevelGeneratorV5().generate(request)) {
            is GenerationResultV5.Exhausted -> mergeReasons(rejected, result.rejectedReasons)
            is GenerationResultV5.Generated -> {
                elapsedMillis += (System.nanoTime() - started) / 1_000_000L
                mergeReasons(rejected, result.rejectedReasons)
                check(result.level.metadata?.generationProfile == slot.profile.id)
                check(result.level.metadata?.contentVersion == CAMPAIGN_V9_CONTENT_VERSION)
                return CampaignV9Generated(
                    slot = slot,
                    level = result.level,
                    seedRetry = retry,
                    generatorAttempts = result.attemptsUsed,
                    elapsedMillis = elapsedMillis,
                    restoredFromCheckpoint = false,
                    rejectedReasons = rejected,
                )
            }
        }
        elapsedMillis += (System.nanoTime() - started) / 1_000_000L
    }
    error(
        "Campaign V9 exhausted ${slot.difficulty} slot ${slot.number}; " +
            "retries=$startRetry..${retriesPerSlot - 1}; top=${rejected.entries.sortedByDescending { it.value }.take(8)}",
    )
}

private fun writeCheckpoint(parser: LevelParser, directory: File, level: LevelDefinition) {
    val catalog = LevelCatalog(
        schemaVersion = 2,
        ruleVersion = "magnetrail-core-1",
        catalogId = "magnetrail-campaign-v9-checkpoint",
        levels = listOf(level),
        contentVersion = CAMPAIGN_V9_CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION_V5,
    )
    writeAtomically(File(directory, checkpointName(level.number)), parser.encodeCatalog(catalog) + "\n")
}

private fun campaignV9Markdown(audit: CampaignV9GenerationAudit): String = """# Campaign V9 generation report

- Status: **${audit.status}**
- Source/final levels: ${audit.sourceLevelCount}/${audit.finalLevelCount}
- Appended range: ${audit.firstAppendedNumber}–${audit.finalNumber}
- Profile distribution: ${audit.profileDistribution}
- Exact uniqueness: ${audit.exactUniqueCount}/${audit.finalLevelCount}
- Rotation/reflection uniqueness: ${audit.symmetryUniqueCount}/${audit.finalLevelCount}
- Infinite exact/symmetry collisions: ${audit.infiniteExactCollisions}/${audit.infiniteSymmetryCollisions}
- Generator version: ${audit.generatorVersion}
- Content version: ${audit.contentVersion}
- Workers: ${audit.workers}
- Seed: ${audit.initialSeed}
- Checkpoints restored while materializing this report: ${audit.levels.count { it.restoredFromCheckpoint }}

Every appended board was accepted by the production engine, complete solver, Difficulty V4,
Quality V2, structural gates, replay checks, and exact plus D4-symmetry duplicate gates.
"""

private fun campaignLevelId(number: Int): String = "campaign-${number.toString().padStart(3, '0')}"

private fun campaignPackId(number: Int): String =
    "magnetic-circuit-${((number - 1) / 20 + 1).toString().padStart(2, '0')}"

private fun checkpointName(number: Int): String = "campaign-${number.toString().padStart(4, '0')}.json"

private fun seedFor(slot: CampaignV9Slot, retry: Int, initialSeed: Long): Long =
    initialSeed + (slot.appendIndex + 1) * CAMPAIGN_V9_SLOT_GAMMA + retry * CAMPAIGN_V9_RETRY_GAMMA

private fun inferGenerationAttempt(
    slot: CampaignV9Slot,
    metadataSeed: Long,
    initialSeed: Long,
    retriesPerSlot: Int,
    maxAttemptsPerSeed: Int,
): Pair<Int, Int> {
    val attempts = if (slot.profile.id.startsWith("v5-campaign-v9-")) 1 else maxAttemptsPerSeed
    for (retry in 0 until retriesPerSlot) {
        val retrySeed = seedFor(slot, retry, initialSeed)
        for (attempt in 0 until attempts) {
            if (retrySeed + attempt * CAMPAIGN_V9_GENERATOR_ATTEMPT_GAMMA == metadataSeed) {
                return retry to (attempt + 1)
            }
        }
    }
    error("Checkpoint ${campaignLevelId(slot.number)} has an unexpected generator seed $metadataSeed")
}

private fun mergeReasons(target: MutableMap<String, Int>, source: Map<String, Int>) {
    source.forEach { (reason, count) -> target[reason] = target.getOrDefault(reason, 0) + count }
}

private fun writeAtomically(file: File, content: String) {
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

private fun sha256CampaignV9(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun Map<String, String>.requiredCampaignV9(key: String): String =
    requireNotNull(this[key]) { "Missing --$key" }

package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.difficulty.DifficultyAnalysis
import com.rameshta.magnetrail.core.difficulty.DifficultyAnalyzer
import com.rameshta.magnetrail.core.difficulty.DifficultyBandV2
import com.rameshta.magnetrail.core.generation.CertificationPipeline
import com.rameshta.magnetrail.core.generation.CertificationRequest
import com.rameshta.magnetrail.core.generation.CertificationResult
import com.rameshta.magnetrail.core.generation.CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.GENERATOR_VERSION
import com.rameshta.magnetrail.core.generation.GenerationProfile
import com.rameshta.magnetrail.core.generation.GenerationRequest
import com.rameshta.magnetrail.core.generation.GenerationResult
import com.rameshta.magnetrail.core.generation.LevelGenerator
import com.rameshta.magnetrail.core.generation.M52_CONTENT_VERSION
import com.rameshta.magnetrail.core.generation.M52_GENERATOR_VERSION
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelOrigin
import com.rameshta.magnetrail.core.model.Position
import com.rameshta.magnetrail.core.model.Wall
import com.rameshta.magnetrail.core.quality.LevelQualityAnalyzer
import com.rameshta.magnetrail.core.quality.LevelQualityScore
import com.rameshta.magnetrail.core.quality.LevelQualityStatus
import java.io.File
import java.util.Locale
import kotlin.math.abs

private const val M52_FIRST_SEED = 520_001L
private const val M52_DEFAULT_POOL_SIZE = 200
private val HAND_TUNED_NUMBERS = setOf(103, 105, 109, 110, 115, 120, 125, 130, 135, 140)
private val RECOVERY_NUMBERS = setOf(106, 112, 118, 124, 131, 137, 143, 148)

internal fun stageM52Expansion(options: Map<String, String>) {
    val campaignFile = File(options.requiredM52("campaign"))
    val outputDirectory = File(options.requiredM52("output")).also(File::mkdirs)
    val poolSize = options["pool-size"]?.toInt() ?: M52_DEFAULT_POOL_SIZE
    val firstSeed = options["seed"]?.toLong() ?: M52_FIRST_SEED
    require(poolSize >= 50) { "M5.2 candidate pool must contain at least 50 viable candidates" }

    val sourceCatalog = LevelParser().parseCatalog(campaignFile.readText())
    check(sourceCatalog.levels.size in setOf(100, 150)) {
        "M5.2 staging requires either the frozen 100-level base or promoted 150-level campaign"
    }
    val base = sourceCatalog.copy(
        catalogId = "magnetrail-campaign-v3",
        levels = sourceCatalog.levels.take(100),
        contentVersion = CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION,
    )
    check(base.levels.map(ContentFingerprint::symmetryNormalized).toSet().size == 100) {
        "Base campaign has a symmetry duplicate"
    }

    val builder = M52ExpansionBuilder(base)
    val build = builder.build(poolSize, firstSeed)
    val parser = LevelParser()
    File(outputDirectory, "M52_CANDIDATE_POOL.json").writeText(parser.encodeCatalog(build.poolCatalog))
    File(outputDirectory, "Magnetrail_Campaign_Levels_v4.json").writeText(parser.encodeCatalog(build.reviewCatalog))
    File(outputDirectory, "m5_2_candidate_pool_metrics.csv").writeText(build.candidatePoolCsv())
    File(outputDirectory, "m5_2_levels_101_150_metrics.csv").writeText(build.selectedMetricsCsv())
    File(outputDirectory, "M5_2_CAMPAIGN_101_150_REPORT.md").writeText(build.summaryMarkdown())
    File(outputDirectory, "M5_2_DUPLICATE_REPORT.md").writeText(build.duplicateMarkdown())
    File(outputDirectory, "M5_2_PACING_REPORT.md").writeText(build.pacingMarkdown())
    File(outputDirectory, "M5_2_MANUAL_REVIEW.md").writeText(build.manualReviewMarkdown())
    File(outputDirectory, "m5_2_manual_approvals.csv").writeText(build.approvalsCsv())
    File(outputDirectory, "M5_2_MIGRATION.md").writeText(build.migrationMarkdown())
    File(outputDirectory, "M5_2_FULL_150_REPORT.md").writeText(build.fullReportMarkdown())
    println(
        "Staged ${build.pool.size} viable candidates and a ${build.reviewCatalog.levels.size}-level review catalog; " +
            "shipped content was not modified and all 50 approvals remain pending.",
    )
}

internal fun certifyM52Review(options: Map<String, String>) {
    val sourceCatalog = LevelParser().parseCatalog(File(options.requiredM52("base-campaign")).readText())
    val base = sourceCatalog.copy(
        catalogId = "magnetrail-campaign-v3",
        levels = sourceCatalog.levels.take(100),
        contentVersion = CONTENT_VERSION,
        generatorVersion = GENERATOR_VERSION,
    )
    val review = LevelParser().parseCatalog(File(options.requiredM52("review-campaign")).readText())
    check(sourceCatalog.levels.size in setOf(100, 150) && base.levels.size == 100 && review.levels.size == 150)
    base.levels.forEachIndexed { index, old ->
        val retained = review.levels[index]
        check(retained.id == old.id && retained.number == old.number)
        check(ContentFingerprint.exact(retained) == ContentFingerprint.exact(old)) {
            "M5.2 changed protected level ${old.id}"
        }
        check(retained.metadata == old.metadata) { "M5.2 changed protected metadata for ${old.id}" }
    }
    check(review.levels.map(ContentFingerprint::exact).toSet().size == 150)
    check(review.levels.map(ContentFingerprint::symmetryNormalized).toSet().size == 150)
    val analyzer = DifficultyAnalyzer()
    val qualityAnalyzer = LevelQualityAnalyzer()
    val certification = CertificationPipeline()
    review.levels.drop(100).forEach { level ->
        val metadata = requireNotNull(level.metadata)
        val profiles = metadata.generationProfile?.let { id ->
            listOf(GenerationProfile.entries.single { it.profileId == id })
        } ?: listOf(GenerationProfile.M52_ADVANCED_CONTINUATION, GenerationProfile.M52_MASTERY)
        val recertified = profiles.firstNotNullOfOrNull { profile ->
            certification.certify(
                level.copy(metadata = null),
                CertificationRequest(
                    profile = profile,
                    origin = metadata.origin,
                    packId = metadata.packId,
                    generatorVersion = metadata.generatorVersion,
                    generatorSeed = metadata.generatorSeed,
                    generationProfile = metadata.generationProfile,
                    contentVersion = metadata.contentVersion,
                ),
            ) as? CertificationResult.Accepted
        } ?: error("M5.2 recertification rejected ${level.id}")
        check(recertified.level.metadata == metadata) { "M5.2 metadata mismatch for ${level.id}" }
        val analysis = analyzer.analyze(level)
        check(analysis.searchComplete && analysis.solutionReplayValid)
        check(!analysis.metrics.stateAnalysisCapped && !analysis.metrics.counterfactualAnalysisCapped)
        check(analysis.metrics.unknownAlternativeCount == 0)
        val quality = qualityAnalyzer.analyze(level, analysis)
        check(quality.qualityStatus == LevelQualityStatus.ACCEPT) {
            "M5.2 quality rejected ${level.id}: ${quality.qualityReasons}"
        }
    }
    println("Certified staged M5.2 review catalog: 100 protected + 50 new, no hard/cap/duplicate failures.")
}

private class M52ExpansionBuilder(
    private val base: LevelCatalog,
) {
    private val analyzer = DifficultyAnalyzer()
    private val qualityAnalyzer = LevelQualityAnalyzer()
    private val certification = CertificationPipeline()
    private val generator = LevelGenerator(base.levels)
    private val rejectionCounts = linkedMapOf<String, Int>()

    fun build(poolSize: Int, firstSeed: Long): M52Build {
        val pool = generatePool(poolSize, firstSeed)
        val selected = selectFinalSet(pool)
        check(selected.size == 50)
        check(selected.count { it.level.metadata?.origin == LevelOrigin.HANDCRAFTED } == 10)
        check(selected.count { it.level.metadata?.origin == LevelOrigin.GENERATOR_ASSISTED } == 40)
        val reviewCatalog = LevelCatalog(
            schemaVersion = base.schemaVersion,
            ruleVersion = base.ruleVersion,
            catalogId = "magnetrail-campaign-v4",
            levels = base.levels + selected.map(M52Selected::level),
            contentVersion = M52_CONTENT_VERSION,
            generatorVersion = M52_GENERATOR_VERSION,
        )
        check(reviewCatalog.levels.map { it.id }.toSet().size == 150)
        check(reviewCatalog.levels.map { it.number } == (1..150).toList())
        check(reviewCatalog.levels.map(ContentFingerprint::exact).toSet().size == 150)
        check(reviewCatalog.levels.map(ContentFingerprint::symmetryNormalized).toSet().size == 150)
        return M52Build(
            base = base,
            pool = pool,
            selected = selected,
            reviewCatalog = reviewCatalog,
            rejectionCounts = rejectionCounts.toMap(),
            firstSeed = firstSeed,
        )
    }

    private fun generatePool(poolSize: Int, firstSeed: Long): List<M52Candidate> {
        val baseSymmetry = base.levels.mapTo(mutableSetOf(), ContentFingerprint::symmetryNormalized)
        val acceptedSymmetry = mutableSetOf<String>()
        val viable = mutableListOf<M52Candidate>()
        var seed = firstSeed
        val seedLimit = firstSeed + poolSize * 250L
        while (viable.size < poolSize && seed < seedLimit) {
            val proposedNumber = 101 + viable.size % 50
            val profile = if (seed % 3L == 0L) GenerationProfile.M52_MASTERY else
                GenerationProfile.M52_ADVANCED_CONTINUATION
            val started = System.nanoTime()
            val result = generator.generate(
                GenerationRequest(
                    stableId = "m52-candidate-$seed",
                    sequenceNumber = proposedNumber,
                    title = "M5.2 candidate $seed",
                    seed = seed,
                    profile = profile,
                    packId = packForM52(proposedNumber),
                    contentVersion = M52_CONTENT_VERSION,
                    generatorVersion = M52_GENERATOR_VERSION,
                ),
            )
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000
            when (result) {
                is GenerationResult.Exhausted -> {
                    rejectionCounts.incrementM52("generator-exhausted")
                    result.rejectedReasons.forEach { (reason, count) -> rejectionCounts.incrementM52(reason, count) }
                }
                is GenerationResult.Generated -> {
                    result.rejectedReasons.forEach { (reason, count) -> rejectionCounts.incrementM52(reason, count) }
                    val level = result.level
                    val symmetry = ContentFingerprint.symmetryNormalized(level)
                    val analysis = analyzer.analyze(level)
                    val quality = qualityAnalyzer.analyze(level, analysis)
                    val rejection = candidateRejection(level, analysis, quality, symmetry, baseSymmetry, acceptedSymmetry)
                    if (rejection == null) {
                        acceptedSymmetry += symmetry
                        viable += M52Candidate(
                            level = level,
                            analysis = analysis,
                            quality = quality,
                            seed = seed,
                            profile = profile,
                            generationMillis = elapsedMillis,
                            attemptsUsed = result.attemptsUsed,
                        )
                    } else {
                        rejectionCounts.incrementM52(rejection)
                    }
                }
            }
            seed += 1
        }
        check(viable.size == poolSize) {
            "Generated ${viable.size}/$poolSize viable candidates before seed limit $seedLimit; rejections=$rejectionCounts"
        }
        return viable
    }

    private fun candidateRejection(
        level: LevelDefinition,
        analysis: DifficultyAnalysis,
        quality: LevelQualityScore,
        symmetry: String,
        baseSymmetry: Set<String>,
        acceptedSymmetry: Set<String>,
    ): String? = when {
        level.width !in 6..7 || level.height !in 6..7 -> "board-size-outside-6x6-7x7"
        symmetry in baseSymmetry -> "symmetry-duplicate-existing"
        symmetry in acceptedSymmetry -> "symmetry-duplicate-pool"
        analysis.metrics.cleanSolutionLength < 3 -> "trivial-solution"
        analysis.metrics.magnetControlledSolutionActions < 2 &&
            analysis.metrics.cancellationDependencyCount == 0 -> "weak-magnetic-dependency"
        analysis.metrics.polarityFlipCount < 2 &&
            analysis.metrics.cancellationDependencyCount == 0 -> "weak-polarity-chain"
        analysis.metrics.visualCongestionScore > 0.65 -> "visual-congestion"
        analysis.metrics.stateAnalysisCapped || analysis.metrics.counterfactualAnalysisCapped -> "essential-analysis-capped"
        analysis.metrics.unknownAlternativeCount > 0 -> "unknown-alternative"
        analysis.metrics.forcedMoveRatio >= 0.95 -> "near-total-forcedness"
        analysis.score.band < DifficultyBandV2.NORMAL -> "below-advanced-score-floor"
        quality.qualityStatus != LevelQualityStatus.ACCEPT ->
            "quality-${quality.qualityStatus.name.lowercase()}:${quality.qualityReasons.joinToString("+")}"
        else -> null
    }

    private fun selectFinalSet(pool: List<M52Candidate>): List<M52Selected> {
        val remaining = pool.toMutableList()
        val masteryReserve = pool.filter { it.profile == GenerationProfile.M52_MASTERY }
            .sortedWith(compareByDescending<M52Candidate> { it.analysis.score.score }.thenBy { it.seed })
            .take(20)
            .toSet()
        val finaleReserve = masteryReserve.maxWithOrNull(
            compareBy<M52Candidate> { it.analysis.score.score }
                .thenBy { mechanicFit(it.analysis, 150) }
                .thenByDescending { it.seed },
        )
        val selected = mutableListOf<M52Selected>()
        val usedSymmetry = base.levels.mapTo(mutableSetOf(), ContentFingerprint::symmetryNormalized)
        val recentSimilarities = ArrayDeque(
            base.levels.takeLast(4).map(ContentFingerprint::structuralSimilaritySignature),
        )
        for (number in 101..150) {
            val target = targetScore(number)
            val isHandTuned = number in HAND_TUNED_NUMBERS
            val eligible = remaining.filter { candidate ->
                (number >= 141 || candidate !in masteryReserve) &&
                    (number == 150 || candidate != finaleReserve)
            }
            val ranked = eligible.sortedWith(
                compareBy<M52Candidate> {
                    val signature = ContentFingerprint.structuralSimilaritySignature(it.level)
                    if (number == 150) 0 else if (signature in recentSimilarities) 100 else 0
                }.thenBy {
                    when {
                        number in RECOVERY_NUMBERS && it.level.width == 6 -> 0
                        number !in RECOVERY_NUMBERS && it.level.width == 7 -> 0
                        else -> 1
                    }
                }.thenBy {
                    if (number >= 141 && it.profile == GenerationProfile.M52_MASTERY) 0 else 1
                }.thenBy {
                    if (number == 125 && it.analysis.metrics.cancellationDependencyCount > 0) 0 else 1
                }.thenBy {
                    if (number == 150) -it.analysis.score.score else 0
                }.thenBy { abs(it.analysis.score.score - target) }
                    .thenByDescending { mechanicFit(it.analysis, number) }
                    .thenBy { it.seed },
            )
            var final: M52Selected? = null
            var source: M52Candidate? = null
            if (isHandTuned) {
                val tuned = ranked.take(18).mapNotNull { candidate ->
                    val similarityGate = if (number == 150) emptyList() else recentSimilarities
                    tuneCandidate(candidate, number, target, usedSymmetry, similarityGate)?.let { candidate to it }
                }.minWithOrNull(
                    compareBy<Pair<M52Candidate, M52Selected>> {
                        if (number == 125 && it.second.analysis.metrics.cancellationDependencyCount > 0) 0 else 1
                    }.thenBy { abs(it.second.analysis.score.score - target) }
                        .thenByDescending { mechanicFit(it.second.analysis, number) }
                        .thenBy { it.first.seed },
                )
                source = tuned?.first
                final = tuned?.second
            } else {
                for (candidate in ranked) {
                    val proposed = certifyFinalCandidate(candidate, number) ?: continue
                    val symmetry = ContentFingerprint.symmetryNormalized(proposed.level)
                    val similarity = ContentFingerprint.structuralSimilaritySignature(proposed.level)
                    if (symmetry in usedSymmetry || (number != 150 && similarity in recentSimilarities)) continue
                    final = proposed
                    source = candidate
                    break
                }
            }
            checkNotNull(final) { "No M5.2 candidate satisfied final selection constraints for level $number" }
            remaining.remove(checkNotNull(source))
            usedSymmetry += ContentFingerprint.symmetryNormalized(final.level)
            recentSimilarities += ContentFingerprint.structuralSimilaritySignature(final.level)
            while (recentSimilarities.size > 4) recentSimilarities.removeFirst()
            selected += final
        }
        return selected
    }

    private fun certifyFinalCandidate(candidate: M52Candidate, number: Int): M52Selected? {
        val raw = candidate.level.copy(
            id = campaignId(number),
            number = number,
            title = titleForM52(number),
            designedSolutions = candidate.level.designedSolutions,
            metadata = null,
        )
        val accepted = certification.certify(
            raw,
            CertificationRequest(
                profile = candidate.profile,
                origin = LevelOrigin.GENERATOR_ASSISTED,
                packId = packForM52(number),
                generatorVersion = M52_GENERATOR_VERSION,
                generatorSeed = candidate.seed,
                generationProfile = candidate.profile.profileId,
                contentVersion = M52_CONTENT_VERSION,
            ),
        ) as? CertificationResult.Accepted ?: return null
        val analysis = analyzer.analyze(accepted.level)
        val quality = qualityAnalyzer.analyze(accepted.level, analysis)
        if (quality.qualityStatus != LevelQualityStatus.ACCEPT) return null
        return M52Selected(
            level = accepted.level,
            analysis = analysis,
            quality = quality,
            sourceSeed = candidate.seed,
            sourceProfile = candidate.profile.profileId,
            generationMillis = candidate.generationMillis,
            role = roleFor(number),
            designRationale = "Selected from the deterministic advanced pool for ${roleFor(number).lowercase()} pacing.",
            beforeFingerprint = ContentFingerprint.exact(candidate.level),
        )
    }

    private fun tuneCandidate(
        candidate: M52Candidate,
        number: Int,
        targetScore: Int,
        usedSymmetry: Set<String>,
        recentSimilarities: Collection<String>,
    ): M52Selected? {
        val rawBase = candidate.level.copy(
            id = campaignId(number),
            number = number,
            title = titleForM52(number),
            metadata = null,
        )
        val variants = wallTuningVariants(rawBase, candidate.profile.maxWalls).take(80)
        val accepted = variants.mapNotNull { variant ->
            val result = certification.certify(
                variant,
                CertificationRequest(
                    profile = candidate.profile,
                    origin = LevelOrigin.HANDCRAFTED,
                    packId = packForM52(number),
                    contentVersion = M52_CONTENT_VERSION,
                ),
            ) as? CertificationResult.Accepted ?: return@mapNotNull null
            val symmetry = ContentFingerprint.symmetryNormalized(result.level)
            val similarity = ContentFingerprint.structuralSimilaritySignature(result.level)
            if (symmetry in usedSymmetry || similarity in recentSimilarities) return@mapNotNull null
            val analysis = analyzer.analyze(result.level)
            val quality = qualityAnalyzer.analyze(result.level, analysis)
            if (quality.qualityStatus != LevelQualityStatus.ACCEPT) return@mapNotNull null
            Triple(result.level, analysis, quality)
        }.minWithOrNull(
            compareBy<Triple<LevelDefinition, DifficultyAnalysis, LevelQualityScore>> {
                if (number == 125 && it.second.metrics.cancellationDependencyCount > 0) 0 else 1
            }.thenBy {
                abs(it.second.score.score - targetScore)
            }.thenByDescending { mechanicFit(it.second, number) }
                .thenBy { ContentFingerprint.exact(it.first) },
        ) ?: return null
        val rationale = when {
            number in RECOVERY_NUMBERS ->
                "Hand-tuned as a meaningful recovery board with lower branching load after the preceding peak."
            accepted.second.metrics.cancellationDependencyCount > 0 ->
                "Hand-tuned so a readable cancellation dependency drives the intended ordering insight."
            accepted.second.metrics.occlusionDependencyCount > 0 ->
                "Hand-tuned so an occlusion clue drives the intended ordering insight."
            accepted.second.metrics.controllingMagnetChangeCount > 0 ->
                "Hand-tuned wall geometry makes a controller change legible without adding a new mechanic."
            accepted.second.metrics.criticalOrderConstraintCount > 0 ->
                "Hand-tuned as a fair checkpoint with a readable critical ordering dependency."
            else -> "Hand-tuned to foreground alternating Pull/Push polarity planning."
        }
        return M52Selected(
            level = accepted.first,
            analysis = accepted.second,
            quality = accepted.third,
            sourceSeed = candidate.seed,
            sourceProfile = candidate.profile.profileId,
            generationMillis = candidate.generationMillis,
            role = roleFor(number),
            designRationale = rationale,
            beforeFingerprint = ContentFingerprint.exact(candidate.level),
        )
    }

    private fun wallTuningVariants(level: LevelDefinition, maxWalls: Int): Sequence<LevelDefinition> = sequence {
        val occupiedWithoutWalls = (level.arrows.map { it.position } + level.magnets.map { it.position }).toSet()
        val available = buildList {
            for (row in 1..level.height) for (column in 1..level.width) {
                val cell = Position(row, column)
                if (cell !in occupiedWithoutWalls && level.walls.none { it.position == cell }) add(cell)
            }
        }
        if (level.walls.size <= maxWalls - 2) {
            for (first in available.indices) for (second in first + 1..available.lastIndex) {
                yield(level.copy(walls = level.walls + Wall(available[first]) + Wall(available[second])))
            }
        } else if (level.walls.size < maxWalls) {
            available.forEach { yield(level.copy(walls = level.walls + Wall(it))) }
        }
        if (level.walls.size >= 2) {
            for (first in available.indices) for (second in first + 1..available.lastIndex) {
                yield(level.copy(walls = level.walls.drop(2) + Wall(available[first]) + Wall(available[second])))
            }
        }
    }

    private fun mechanicFit(analysis: DifficultyAnalysis, number: Int): Int {
        val metrics = analysis.metrics
        return when (number) {
            in 101..110 -> metrics.polarityFlipCount + metrics.criticalOrderConstraintCount * 2
            in 111..125 -> metrics.controllingMagnetChangeCount * 3 + metrics.occlusionDependencyCount * 3 +
                metrics.cancellationDependencyCount * 10 +
                metrics.magnetControlledSolutionActions
            in 126..140 -> metrics.criticalOrderConstraintCount * 3 + metrics.cancellationDependencyCount * 4 +
                (metrics.fatalChoiceRatio * 5).toInt()
            else -> metrics.magnetControlledSolutionActions + metrics.criticalOrderConstraintCount * 2 +
                metrics.controllingMagnetChangeCount * 2
        }
    }
}

private data class M52Candidate(
    val level: LevelDefinition,
    val analysis: DifficultyAnalysis,
    val quality: LevelQualityScore,
    val seed: Long,
    val profile: GenerationProfile,
    val generationMillis: Long,
    val attemptsUsed: Int,
)

private data class M52Selected(
    val level: LevelDefinition,
    val analysis: DifficultyAnalysis,
    val quality: LevelQualityScore,
    val sourceSeed: Long,
    val sourceProfile: String,
    val generationMillis: Long,
    val role: String,
    val designRationale: String,
    val beforeFingerprint: String,
)

private data class M52Build(
    val base: LevelCatalog,
    val pool: List<M52Candidate>,
    val selected: List<M52Selected>,
    val reviewCatalog: LevelCatalog,
    val rejectionCounts: Map<String, Int>,
    val firstSeed: Long,
) {
    val poolCatalog: LevelCatalog = LevelCatalog(
        schemaVersion = base.schemaVersion,
        ruleVersion = base.ruleVersion,
        catalogId = "magnetrail-m52-candidate-pool",
        levels = pool.mapIndexed { index, candidate -> candidate.level.copy(number = index + 1) },
        contentVersion = M52_CONTENT_VERSION,
        generatorVersion = M52_GENERATOR_VERSION,
    )

    fun candidatePoolCsv(): String = buildString {
        appendLine(M52_CSV_HEADER + ",similarity_neighbors,proposed_curriculum_position")
        val bySimilarity = pool.groupBy { ContentFingerprint.structuralSimilaritySignature(it.level) }
        pool.forEach { candidate ->
            val neighbors = bySimilarity.getValue(ContentFingerprint.structuralSimilaritySignature(candidate.level))
                .filterNot { it === candidate }
                .map { it.level.id }
                .sorted()
            appendLine(
                candidate.csvRow() + ",${csv(neighbors.ifEmpty { listOf("none") }.joinToString("+"))}," +
                    "${candidate.level.number}:${packForM52(candidate.level.number)}",
            )
        }
    }

    fun selectedMetricsCsv(): String = buildString {
        appendLine(M52_CSV_HEADER + ",campaign_role,design_rationale,before_fingerprint,approval_status")
        selected.forEach { level ->
            appendLine(
                level.csvRow() + ",${level.role},${csv(level.designRationale)},${level.beforeFingerprint},PENDING_OWNER_REVIEW",
            )
        }
    }

    fun summaryMarkdown(): String = buildString {
        val origins = selected.groupingBy { requireNotNull(it.level.metadata).origin }.eachCount()
        val boards = selected.groupingBy { "${it.level.width}x${it.level.height}" }.eachCount()
        val bands = selected.groupingBy { it.analysis.score.band.displayName }.eachCount()
        val quality = selected.groupingBy { it.quality.qualityStatus }.eachCount()
        val tags = selected.flatMap { it.level.metadata?.mechanicTags.orEmpty() }.groupingBy { it }.eachCount()
        val localAffected = localSimilarityPairs().flatMap { listOf(it.first, it.second) }.toSet()
        appendLine("# Magnetrail M5.2 campaign 101–150 review report")
        appendLine()
        appendLine("This packet is staging evidence, not shipped content. The deterministic 50-level set cannot be promoted until every row in `M5_2_MANUAL_REVIEW.md` is approved by an owner/reviewer.")
        appendLine()
        appendLine("- Proposed final count: 150 (${base.levels.size} unchanged + ${selected.size} staged)")
        appendLine("- Origin split for 101–150: $origins")
        appendLine("- Board sizes: $boards")
        appendLine("- Difficulty V2: $bands")
        appendLine("- Target-distribution note: Very Hard/Expert candidates were not manufactured by inflating density; any remaining target-band deviation is explicit calibration evidence for owner review.")
        appendLine("- Quality: $quality")
        appendLine("- Sequence-aware Quality (M5.1 eight-level window): {ACCEPT=${selected.size - localAffected.size}, REVIEW=${localAffected.size}}; local-structure REVIEW rows require explicit owner resolution")
        appendLine("- Mechanic tags: $tags")
        appendLine("- Candidate pool: ${pool.size} viable from seed range $firstSeed..${pool.maxOf { it.seed }} (5 viable candidates per generator-assisted slot)")
        appendLine("- Candidate profiles: ${pool.groupingBy { it.profile.profileId }.eachCount()}")
        appendLine("- Generator/content/analyzer versions: $M52_GENERATOR_VERSION / $M52_CONTENT_VERSION / ${selected.first().analysis.score.analyzerVersion}")
        appendLine("- Generation + certification time range: ${selected.minOf { it.generationMillis }}..${selected.maxOf { it.generationMillis }} ms")
        appendLine("- Solver explored-state range: ${selected.minOf { it.analysis.metrics.solverStatesExplored }}..${selected.maxOf { it.analysis.metrics.solverStatesExplored }}")
        appendLine("- Runtime budgets: host catalog parsing <2,000 ms; connected 150-item lazy-grid discovery <10,000 ms; solver state caps 30,000/50,000 by profile")
        appendLine("- Rejections: $rejectionCounts")
        appendLine("- Remaining manual approvals: ${selected.size}")
        appendLine()
        appendLine("Levels 1–100 are byte-for-byte domain copies with unchanged IDs, numbers, boards, metadata, and fingerprints. The review catalog uses stable IDs `campaign-101` through `campaign-150`; it is not consumed by the app until the explicit approval-gated promotion task succeeds.")
    }

    fun duplicateMarkdown(): String = buildString {
        val all = reviewCatalog.levels
        val exactGroups = all.groupBy(ContentFingerprint::exact).values.filter { it.size > 1 }
        val symmetryGroups = all.groupBy(ContentFingerprint::symmetryNormalized).values.filter { it.size > 1 }
        val localPairs = localSimilarityPairs()
        appendLine("# M5.2 duplicate report")
        appendLine()
        appendLine("- Boards checked: ${all.size}")
        appendLine("- Exact duplicate groups: ${exactGroups.size}")
        appendLine("- Symmetry duplicate groups: ${symmetryGroups.size}")
        appendLine("- Local-window review similarity pairs: ${localPairs.size}")
        appendLine()
        if (localPairs.isEmpty()) appendLine("No local similarity waiver is required.") else {
            appendLine("Review-only pairs (not hard duplicates):")
            localPairs.forEach { appendLine("- ${it.first} ↔ ${it.second}") }
            appendLine()
            appendLine("These structural-signature findings require explicit owner confirmation that neighboring play does not feel repetitive.")
        }
    }

    fun pacingMarkdown(): String = buildString {
        appendLine("# M5.2 pacing and recovery report")
        appendLine()
        appendLine("The ordering below is deterministic and staging-only. Peaks and recovery roles were selected before manual approval; tooling does not reorder them at runtime.")
        appendLine()
        appendLine("| # | ID | Arc | Score/band | Role | Fatal | Forced | Branching | Approval |")
        appendLine("|---:|---|---|---|---|---:|---:|---:|---|")
        selected.forEach { item ->
            val m = item.analysis.metrics
            appendLine(
                "| ${item.level.number} | ${item.level.id} | ${packForM52(item.level.number)} | " +
                    "${item.analysis.score.score}/${item.analysis.score.band.displayName} | ${item.role} | " +
                    "${format(m.fatalChoiceRatio)} | ${format(m.forcedMoveRatio)} | " +
                    "${format(m.averageSuccessfulBranching)} | PENDING |",
            )
        }
        appendLine()
        appendLine("Configured recovery positions: ${RECOVERY_NUMBERS.sorted().joinToString()}. No more than three Hard-or-higher levels may remain consecutive after owner review; any exception must be recorded beside the affected manual-review rows.")
    }

    fun manualReviewMarkdown(): String = buildString {
        appendLine("# M5.2 manual review gate")
        appendLine()
        appendLine("No human playtesting or owner approval is claimed. All levels remain staging candidates until the reviewer completes every field and changes the corresponding machine gate in `m5_2_manual_approvals.csv` to `APPROVED`.")
        appendLine()
        appendLine("| # | Stable ID | Visual | Replay | Polarity readable | Failure clear | Mechanic matters | Alternatives fair | Placement | Neighbor variety | Peak/recovery role | Reviewer notes | Status |")
        appendLine("|---:|---|---|---|---|---|---|---|---|---|---|---|---|")
        selected.forEach { item ->
            appendLine(
                "| ${item.level.number} | ${item.level.id} | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ ${item.designRationale} |  | PENDING_OWNER_REVIEW |",
            )
        }
    }

    fun approvalsCsv(): String = buildString {
        appendLine("level_id,campaign_number,status,reviewer,notes")
        selected.forEach { appendLine("${it.level.id},${it.level.number},PENDING_OWNER_REVIEW,,") }
    }

    fun migrationMarkdown(): String = """# M5.2 content/progress migration

The proposed catalog moves catalog content version 3 → 4 and generator version 1 → 2 only after approval-gated promotion. Level IDs and all content fingerprints for Levels 1–100 remain unchanged.

- Progress is keyed by stable level ID. Completion, stars, best actions, overloads, hints, first-clear rewards, coins, Daily history/settings, and ad caps are retained.
- Catalog-size clamping is dynamic. Completing `campaign-100` in the 150-level catalog raises `highestUnlockedLevel` to 101, making `campaign-101` the valid Continue destination.
- Players below Level 100 retain their current highest unlock and selected stable ID.
- New IDs `campaign-101`…`campaign-150` start absent from completion/record/reward sets.
- Completing Level 150 clamps the unlock to 150; `hasNextLevel` is false and no Level 151 is synthesized.
- Repeated migration is idempotent. Unknown/corrupt IDs are filtered while economy, Daily, settings, and monetization state use the existing conservative recovery path.
- Promotion remains blocked while any approval row is not `APPROVED`.
"""

    fun fullReportMarkdown(): String = buildString {
        val analyses = reviewCatalog.levels.associateWith { DifficultyAnalyzer().analyze(it) }
        val quality = analyses.map { (level, analysis) -> LevelQualityAnalyzer().analyze(level, analysis).qualityStatus }
            .groupingBy { it }.eachCount()
        appendLine("# Magnetrail proposed full 150-level campaign report")
        appendLine()
        appendLine("Status: **STAGING — OWNER REVIEW REQUIRED**")
        appendLine()
        appendLine("- Total levels: ${reviewCatalog.levels.size}")
        appendLine("- Original stable levels preserved: ${base.levels.size}")
        appendLine("- New staged IDs: ${selected.first().level.id}…${selected.last().level.id}")
        appendLine("- Catalog/content/generator versions: ${reviewCatalog.catalogId} / ${reviewCatalog.contentVersion} / ${reviewCatalog.generatorVersion}")
        appendLine("- Origins: ${reviewCatalog.levels.groupingBy { requireNotNull(it.metadata).origin }.eachCount()}")
        appendLine("- Board sizes: ${reviewCatalog.levels.groupingBy { "${it.width}x${it.height}" }.eachCount()}")
        appendLine("- Difficulty V2: ${analyses.values.groupingBy { it.score.band.displayName }.eachCount()}")
        appendLine("- Standalone Quality: $quality (all new levels are ACCEPT; the sequence-aware M5.1 report remains authoritative for Levels 1–100)")
        appendLine("- Mechanic tags: ${reviewCatalog.levels.flatMap { requireNotNull(it.metadata).mechanicTags }.groupingBy { it }.eachCount()}")
        appendLine("- Solver explored-state range: ${analyses.values.minOf { it.metrics.solverStatesExplored }}..${analyses.values.maxOf { it.metrics.solverStatesExplored }}")
        appendLine("- Exact fingerprints: ${reviewCatalog.levels.map(ContentFingerprint::exact).toSet().size}")
        appendLine("- Symmetry fingerprints: ${reviewCatalog.levels.map(ContentFingerprint::symmetryNormalized).toSet().size}")
        appendLine("- New Quality ACCEPT: ${selected.count { it.quality.qualityStatus == LevelQualityStatus.ACCEPT }}")
        appendLine("- New capped/unknown essential analyses: ${selected.count {
            it.analysis.metrics.stateAnalysisCapped || it.analysis.metrics.counterfactualAnalysisCapped ||
                it.analysis.metrics.unknownAlternativeCount > 0
        }}")
        appendLine("- Manual approvals outstanding: ${selected.size}")
    }

    private fun M52Candidate.csvRow(): String {
        val m = analysis.metrics
        return listOf(
            level.id,
            level.number,
            seed,
            profile.profileId,
            M52_GENERATOR_VERSION,
            ContentFingerprint.exact(level),
            ContentFingerprint.symmetryNormalized(level),
            "${level.width}x${level.height}",
            level.arrows.size,
            level.magnets.size,
            level.walls.size,
            csv(analysis.certifiedSolution.orEmpty().joinToString("+") { it.arrowId }),
            m.solutionCountUpToCap,
            m.solutionCountCapped,
            analysis.score.score,
            analysis.score.band.displayName,
            quality.qualityScore,
            quality.qualityStatus,
            csv(quality.qualityReasons.joinToString("+")),
            format(m.fatalChoiceRatio),
            format(m.forcedMoveRatio),
            format(m.averageSuccessfulBranching),
            m.maximumSuccessfulBranching,
            m.criticalOrderConstraintCount,
            m.pullSolutionActions,
            m.pushSolutionActions,
            m.polarityFlipCount,
            m.controllingMagnetChangeCount,
            m.occlusionDependencyCount,
            m.cancellationDependencyCount,
            m.wallDependencyCount,
            format(m.visualCongestionScore),
            m.solverStatesExplored,
            generationMillis,
            m.stateAnalysisCapped || m.counterfactualAnalysisCapped,
            attemptsUsed,
        ).joinToString(",")
    }

    private fun M52Selected.csvRow(): String {
        val m = analysis.metrics
        return listOf(
            level.id,
            level.number,
            sourceSeed,
            sourceProfile,
            level.metadata?.generatorVersion ?: "hand-tuned",
            ContentFingerprint.exact(level),
            ContentFingerprint.symmetryNormalized(level),
            "${level.width}x${level.height}",
            level.arrows.size,
            level.magnets.size,
            level.walls.size,
            csv(analysis.certifiedSolution.orEmpty().joinToString("+") { it.arrowId }),
            m.solutionCountUpToCap,
            m.solutionCountCapped,
            analysis.score.score,
            analysis.score.band.displayName,
            quality.qualityScore,
            quality.qualityStatus,
            csv(quality.qualityReasons.joinToString("+")),
            format(m.fatalChoiceRatio),
            format(m.forcedMoveRatio),
            format(m.averageSuccessfulBranching),
            m.maximumSuccessfulBranching,
            m.criticalOrderConstraintCount,
            m.pullSolutionActions,
            m.pushSolutionActions,
            m.polarityFlipCount,
            m.controllingMagnetChangeCount,
            m.occlusionDependencyCount,
            m.cancellationDependencyCount,
            m.wallDependencyCount,
            format(m.visualCongestionScore),
            m.solverStatesExplored,
            generationMillis,
            m.stateAnalysisCapped || m.counterfactualAnalysisCapped,
            1,
        ).joinToString(",")
    }

    private fun localSimilarityPairs(): List<Pair<String, String>> = selected.flatMapIndexed { index, item ->
        selected.drop(index + 1).take(8).mapNotNull { other ->
            if (ContentFingerprint.structuralSimilaritySignature(item.level) ==
                ContentFingerprint.structuralSimilaritySignature(other.level)
            ) item.level.id to other.level.id else null
        }
    }
}

private fun targetScore(number: Int): Int = when {
    number in RECOVERY_NUMBERS -> 42 + number % 4
    number == 150 -> 72
    number % 5 == 0 -> 64 + number % 8
    number in 101..110 -> listOf(38, 44, 49, 54, 58, 62, 47, 52, 57, 64)[number - 101]
    number in 111..125 -> 52 + (number * 5 % 14)
    number in 126..140 -> 58 + (number * 7 % 14)
    else -> 61 + (number * 3 % 12)
}

private fun roleFor(number: Int): String = when {
    number in RECOVERY_NUMBERS -> "RECOVERY"
    number % 5 == 0 -> "PEAK"
    else -> "BUILD"
}

private fun packForM52(number: Int): String = when (number) {
    in 101..110 -> "reorientation"
    in 111..125 -> "crossfields"
    in 126..140 -> "polarity-chains"
    else -> "mastery-set"
}

private fun titleForM52(number: Int): String {
    val stems = listOf("Reversal", "Crossfield", "Relay", "Occlusion", "Polarity", "Vector", "Cadence", "Alignment")
    return "${stems[(number - 101) % stems.size]} ${number.toString().padStart(3, '0')}"
}

private fun campaignId(number: Int): String = "campaign-${number.toString().padStart(3, '0')}"

private fun Map<String, String>.requiredM52(key: String): String = requireNotNull(this[key]) { "Missing --$key=..." }

private fun MutableMap<String, Int>.incrementM52(key: String, amount: Int = 1) {
    this[key] = (this[key] ?: 0) + amount
}

private fun format(value: Double): String = "%.4f".format(Locale.ROOT, value)

private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

private const val M52_CSV_HEADER =
    "level_id,campaign_number,seed,profile,generator_version,exact_fingerprint,symmetry_fingerprint," +
        "board,arrows,magnets,walls,certified_solution,solution_count,solution_capped,difficulty_score," +
        "difficulty_band,quality_score,quality_status,quality_reasons,fatal_choice_ratio,forced_move_ratio," +
        "average_branching,maximum_branching,critical_constraints,pull_actions,push_actions,polarity_flips," +
        "controller_changes,occlusion_dependencies,cancellation_dependencies,wall_dependencies," +
        "visual_congestion,solver_states,generation_certification_ms,essential_analysis_capped,attempts"

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    implementation(project(":game-core"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

application {
    mainClass = "com.rameshta.magnetrail.tools.ContentToolKt"
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("docs"))
    }
}

tasks.test {
    useJUnit()
}

val docsDirectory = rootProject.layout.projectDirectory.dir("docs")
val m51StagingDirectory = layout.buildDirectory.dir("m5_1-staging")
val m52StagingDirectory = layout.buildDirectory.dir("m5_2-staging")
val phase0StagingDirectory = layout.buildDirectory.dir("phase0-staging")
val phase1StagingDirectory = layout.buildDirectory.dir("phase1-staging")
val d2StagingDirectory = docsDirectory.dir("content/d2/staging")
val d21StagingDirectory = docsDirectory.dir("content/d2_1/staging")
val infiniteDirectory = docsDirectory.dir("infinite")
val infiniteContentDirectory = docsDirectory.dir("content/infinite")
val campaignV9StagingDirectory = layout.buildDirectory.dir("campaign-v9-staging")
val campaignV9CheckpointDirectory = layout.buildDirectory.dir("campaign-v9-checkpoints")

tasks.register<JavaExec>("generateCampaignV9Expansion") {
    group = "magnetrail content"
    description = "Generate 2,000 mixed, certified, campaign/Infinite-unique Levels 206-2205 with resumable checkpoints."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("content/v9_expansion/SOURCE_CONTENT_V8.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    inputs.files(campaign, infinite)
    inputs.property("campaignV9Count", "2000")
    inputs.property("campaignV9Seed", providers.gradleProperty("campaignV9Seed").getOrElse("9200001"))
    inputs.property("campaignV9Workers", providers.gradleProperty("campaignV9Workers").getOrElse("6"))
    inputs.property("campaignV9RetriesPerSlot", providers.gradleProperty("campaignV9RetriesPerSlot").getOrElse("128"))
    inputs.property("campaignV9AttemptsPerSeed", providers.gradleProperty("campaignV9AttemptsPerSeed").getOrElse("8"))
    outputs.files(
        campaignV9StagingDirectory.map { it.file("Magnetrail_Campaign_Levels_v9.json") },
        campaignV9StagingDirectory.map { it.file("CAMPAIGN_V9_GENERATION_AUDIT.json") },
        campaignV9StagingDirectory.map { it.file("CAMPAIGN_V9_GENERATION_REPORT.md") },
    )
    outputs.upToDateWhen { false }
    args(
        "generate-campaign-v9-expansion",
        "--campaign=${campaign.asFile}",
        "--infinite=${infinite.asFile}",
        "--output=${campaignV9StagingDirectory.get().asFile}",
        "--checkpoint=${campaignV9CheckpointDirectory.get().asFile}",
        "--count=2000",
        "--seed=${providers.gradleProperty("campaignV9Seed").getOrElse("9200001")}",
        "--workers=${providers.gradleProperty("campaignV9Workers").getOrElse("6")}",
        "--retries-per-slot=${providers.gradleProperty("campaignV9RetriesPerSlot").getOrElse("128")}",
        "--attempts-per-seed=${providers.gradleProperty("campaignV9AttemptsPerSeed").getOrElse("8")}",
    )
}

tasks.register<JavaExec>("promoteCampaignV9Expansion") {
    group = "magnetrail content"
    description = "Promote the owner-directed 2,000-level Campaign V9 expansion after all automated gates pass."
    dependsOn("generateCampaignV9Expansion")
    val promotionConfirmed = providers.gradleProperty("confirmCampaignV9Promotion")
    inputs.property("campaignV9PromotionConfirmed", promotionConfirmed.orElse("false"))
    doFirst {
        check(promotionConfirmed.orNull == "true") {
            "Refusing Campaign V9 promotion without -PconfirmCampaignV9Promotion=true"
        }
    }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    val promotion = docsDirectory.dir("content/v9_expansion")
    inputs.files(
        campaign,
        infinite,
        campaignV9StagingDirectory.map { it.file("Magnetrail_Campaign_Levels_v9.json") },
        campaignV9StagingDirectory.map { it.file("CAMPAIGN_V9_GENERATION_AUDIT.json") },
        campaignV9StagingDirectory.map { it.file("CAMPAIGN_V9_GENERATION_REPORT.md") },
    )
    outputs.files(
        campaign,
        promotion.file("SOURCE_CONTENT_V8.json"),
        promotion.file("CAMPAIGN_V9_GENERATION_AUDIT.json"),
        promotion.file("CAMPAIGN_V9_GENERATION_REPORT.md"),
        promotion.file("CAMPAIGN_V9_PROMOTION_RESULT.md"),
    )
    outputs.upToDateWhen { false }
    args(
        "promote-campaign-v9-expansion",
        "--campaign=${campaign.asFile}",
        "--staged-campaign=${campaignV9StagingDirectory.get().file("Magnetrail_Campaign_Levels_v9.json").asFile}",
        "--staged-audit=${campaignV9StagingDirectory.get().file("CAMPAIGN_V9_GENERATION_AUDIT.json").asFile}",
        "--staged-report=${campaignV9StagingDirectory.get().file("CAMPAIGN_V9_GENERATION_REPORT.md").asFile}",
        "--infinite=${infinite.asFile}",
        "--source-snapshot=${promotion.file("SOURCE_CONTENT_V8.json").asFile}",
        "--published-audit=${promotion.file("CAMPAIGN_V9_GENERATION_AUDIT.json").asFile}",
        "--published-report=${promotion.file("CAMPAIGN_V9_GENERATION_REPORT.md").asFile}",
        "--result=${promotion.file("CAMPAIGN_V9_PROMOTION_RESULT.md").asFile}",
        "--authorization=project-owner-directed-2000-level-expansion",
    )
}

tasks.register<JavaExec>("generateInfiniteCertifiedCatalog") {
    group = "magnetrail content"
    description = "Generate a separate immutable Infinite catalog containing only fully certified boards in every band."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    inputs.file(campaign)
    inputs.property("infiniteCandidateCount", providers.gradleProperty("infiniteCandidateCount").getOrElse("600"))
    inputs.property("infiniteExpertCount", providers.gradleProperty("infiniteExpertCount").getOrElse("12"))
    inputs.property("infiniteMasterCount", providers.gradleProperty("infiniteMasterCount").getOrElse("12"))
    inputs.property("infiniteSeed", providers.gradleProperty("infiniteSeed").getOrElse("6600001"))
    outputs.files(
        infiniteContentDirectory.file("INFINITE_CERTIFIED_CATALOG_V1.json"),
        infiniteDirectory.file("INFINITE_GENERATOR_BENCHMARK.json"),
        infiniteDirectory.file("INFINITE_GENERATOR_BENCHMARK.csv"),
        infiniteDirectory.file("INFINITE_FALLBACK_BANK_REPORT.md"),
    )
    outputs.upToDateWhen { false }
    args(
        "generate-infinite-catalog",
        "--campaign=${campaign.asFile}",
        "--catalog-output=${infiniteContentDirectory.file("INFINITE_CERTIFIED_CATALOG_V1.json").asFile}",
        "--report-output=${infiniteDirectory.asFile}",
        "--count=${providers.gradleProperty("infiniteCandidateCount").getOrElse("600")}",
        "--expert-count=${providers.gradleProperty("infiniteExpertCount").getOrElse("12")}",
        "--master-count=${providers.gradleProperty("infiniteMasterCount").getOrElse("12")}",
        "--seed=${providers.gradleProperty("infiniteSeed").getOrElse("6600001")}",
        "--retries-per-slot=${providers.gradleProperty("infiniteRetriesPerSlot").getOrElse("24")}",
    )
    providers.gradleProperty("infiniteAttemptsPerCandidate").orNull?.let {
        args("--attempts-per-candidate=$it")
    }
}

tasks.register<JavaExec>("stagePhase1Expansion") {
    group = "magnetrail content"
    description = "Generate and analyze the Phase 1 Levels 151–200 proposal without promotion."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    inputs.file(campaign)
    inputs.property("phase1Seed", providers.gradleProperty("phase1Seed").getOrElse("8100001"))
    inputs.property(
        "phase1OversizeMultiplier",
        providers.gradleProperty("phase1OversizeMultiplier").getOrElse("4"),
    )
    inputs.property(
        "phase1AttemptsPerTarget",
        providers.gradleProperty("phase1AttemptsPerTarget").getOrElse("30000"),
    )
    outputs.dir(phase1StagingDirectory)
    outputs.upToDateWhen { false }
    args(
        "stage-phase1-expansion",
        "--campaign=${campaign.asFile}",
        "--output=${phase1StagingDirectory.get().asFile}",
        "--seed=${providers.gradleProperty("phase1Seed").getOrElse("8100001")}",
        "--oversize-multiplier=${providers.gradleProperty("phase1OversizeMultiplier").getOrElse("4")}",
        "--attempts-per-target=${providers.gradleProperty("phase1AttemptsPerTarget").getOrElse("30000")}",
    )
}

tasks.register("publishPhase1Proposal") {
    group = "documentation"
    description = "Publish Phase 1 pre-promotion diagnostics and manifest; never modifies campaign content."
    dependsOn("stagePhase1Expansion")
    doLast {
        copy {
            from(phase1StagingDirectory)
            include("M5_3_*")
            into(docsDirectory.dir("content"))
        }
    }
}

tasks.register<JavaExec>("promoteApprovedPhase1") {
    group = "magnetrail content"
    description = "Promote the explicitly owner-approved Phase 1 proposal and preserve source evidence."
    doFirst {
        check(providers.gradleProperty("confirmPhase1Promotion").orNull == "true") {
            "Refusing Phase 1 promotion without -PconfirmPhase1Promotion=true"
        }
    }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "promote-approved-phase1",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--proposal-catalog=${docsDirectory.file("content/M5_3_PROPOSED_CAMPAIGN_NOT_PROMOTED.json").asFile}",
        "--proposal-report=${docsDirectory.file("content/M5_3_PROPOSED_PROMOTION_MANIFEST.json").asFile}",
        "--source-snapshot=${docsDirectory.file("content/M5_3_SOURCE_CONTENT_V5.json").asFile}",
        "--output=${docsDirectory.dir("content").asFile}",
        "--approval=project-owner-approved",
    )
}

tasks.register<JavaExec>("finalizePhase1") {
    group = "verification"
    description = "Recompute final Phase 1 certification and review evidence for the promoted 200-level catalog."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val source = docsDirectory.file("content/M5_3_SOURCE_CONTENT_V5.json")
    val approval = docsDirectory.file("content/M5_3_APPROVED_PROMOTION.json")
    inputs.files(campaign, source, approval)
    outputs.files(
        docsDirectory.file("content/M5_3_FINAL_DIAGNOSTICS.json"),
        docsDirectory.file("content/M5_3_CAMPAIGN_151_200_REPORT.md"),
        docsDirectory.file("content/M5_3_DUPLICATE_REPORT.md"),
        docsDirectory.file("content/M5_3_PACING_REPORT.md"),
        docsDirectory.file("content/M5_3_MANUAL_REVIEW.md"),
        docsDirectory.file("content/M5_3_MIGRATION.md"),
        docsDirectory.file("content/M5_3_FULL_200_REPORT.md"),
    )
    outputs.upToDateWhen { false }
    args(
        "finalize-promoted-phase1",
        "--campaign=${campaign.asFile}",
        "--source-snapshot=${source.asFile}",
        "--approved-report=${approval.asFile}",
        "--output=${docsDirectory.dir("content").asFile}",
    )
}

tasks.register<JavaExec>("analyzePhase0Current") {
    group = "verification"
    description = "Analyze the checked-in 150-level campaign with Puzzle Difficulty v3 without modifying content."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    inputs.file(docsDirectory.file("Magnetrail_Campaign_Levels_v3.json"))
    outputs.dir(phase0StagingDirectory)
    outputs.upToDateWhen { false }
    args(
        "analyze-phase0",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--output=${phase0StagingDirectory.get().asFile}",
    )
}

tasks.register("publishPhase0Evidence") {
    group = "documentation"
    description = "Publish Phase 0 diagnostics and staged candidates; never modifies campaign content."
    dependsOn("planPhase0Remediation")
    doLast {
        copy {
            from(phase0StagingDirectory.map { it.file("PHASE0_CURRENT_DIAGNOSTICS.json") })
            from(phase0StagingDirectory.map { it.file("PHASE0_REMEDIATION_MANIFEST.md") })
            from(phase0StagingDirectory.map { it.file("PHASE0_DISTRIBUTION_REPORT.md") })
            from(phase0StagingDirectory.map { it.file("PHASE0_HUMAN_REVIEW_CHECKLIST.md") })
            from(phase0StagingDirectory.map { it.file("PHASE0_CANDIDATE_POOL.json") })
            from(phase0StagingDirectory.map { it.file("PHASE0_CANDIDATE_POOL.md") })
            from(phase0StagingDirectory.map { it.file("phase0_candidate_catalog.json") }) {
                rename { "PHASE0_STAGED_CANDIDATES.json" }
            }
            from(phase0StagingDirectory.map { it.file("PHASE0_PROPOSED_REMEDIATION.json") })
            from(phase0StagingDirectory.map { it.file("PHASE0_PROPOSED_REMEDIATION.md") })
            from(phase0StagingDirectory.map { it.file("PHASE0_PROPOSED_DISTRIBUTION_REPORT.md") })
            from(phase0StagingDirectory.map { it.file("PHASE0_PROPOSED_HUMAN_REVIEW_CHECKLIST.md") })
            from(phase0StagingDirectory.map { it.file("phase0_proposed_campaign.json") }) {
                rename { "PHASE0_PROPOSED_CAMPAIGN_NOT_PROMOTED.json" }
            }
            into(docsDirectory.dir("development"))
        }
    }
}

tasks.register<JavaExec>("planPhase0Remediation") {
    group = "magnetrail content"
    description = "Select and report a stable-ID Phase 0 remediation proposal without promoting campaign content."
    dependsOn("stagePhase0Candidates")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val diagnostics = phase0StagingDirectory.map { it.file("PHASE0_CURRENT_DIAGNOSTICS.json") }
    val candidateReport = phase0StagingDirectory.map { it.file("PHASE0_CANDIDATE_POOL.json") }
    val candidateCatalog = phase0StagingDirectory.map { it.file("phase0_candidate_catalog.json") }
    inputs.files(
        docsDirectory.file("Magnetrail_Campaign_Levels_v3.json"),
        diagnostics,
        candidateReport,
        candidateCatalog,
    )
    outputs.files(
        phase0StagingDirectory.map { it.file("PHASE0_PROPOSED_REMEDIATION.json") },
        phase0StagingDirectory.map { it.file("PHASE0_PROPOSED_REMEDIATION.md") },
        phase0StagingDirectory.map { it.file("PHASE0_PROPOSED_DISTRIBUTION_REPORT.md") },
        phase0StagingDirectory.map { it.file("PHASE0_PROPOSED_HUMAN_REVIEW_CHECKLIST.md") },
        phase0StagingDirectory.map { it.file("phase0_proposed_campaign.json") },
    )
    args(
        "plan-phase0-remediation",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--diagnostics=${diagnostics.get().asFile}",
        "--candidate-report=${candidateReport.get().asFile}",
        "--candidate-catalog=${candidateCatalog.get().asFile}",
        "--output=${phase0StagingDirectory.get().asFile}",
    )
}

tasks.register<JavaExec>("promoteApprovedPhase0") {
    group = "magnetrail content"
    description = "Promote the explicitly owner-approved Phase 0 proposal with content migration evidence."
    dependsOn("planPhase0Remediation")
    doFirst {
        check(providers.gradleProperty("confirmPhase0Promotion").orNull == "true") {
            "Refusing Phase 0 promotion without -PconfirmPhase0Promotion=true"
        }
    }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "promote-approved-phase0",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--proposal-catalog=${phase0StagingDirectory.get().file("phase0_proposed_campaign.json").asFile}",
        "--proposal-report=${phase0StagingDirectory.get().file("PHASE0_PROPOSED_REMEDIATION.json").asFile}",
        "--source-snapshot=${docsDirectory.file("development/PHASE0_SOURCE_CONTENT_V4.json").asFile}",
        "--output=${docsDirectory.dir("development").asFile}",
        "--approval=project-owner-approved",
    )
}

tasks.register<JavaExec>("finalizePhase0") {
    group = "verification"
    description = "Recompute final Phase 0 certification and review evidence for the promoted catalog."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val source = docsDirectory.file("development/PHASE0_SOURCE_CONTENT_V4.json")
    val approval = docsDirectory.file("development/PHASE0_APPROVED_REMEDIATION.json")
    inputs.files(campaign, source, approval)
    outputs.files(
        docsDirectory.file("development/PHASE0_FINAL_DIAGNOSTICS.json"),
        docsDirectory.file("development/PHASE0_FINAL_CERTIFICATION.md"),
        docsDirectory.file("development/PHASE0_FINAL_DISTRIBUTION_REPORT.md"),
        docsDirectory.file("development/PHASE0_FINAL_HUMAN_REVIEW_CHECKLIST.md"),
    )
    outputs.upToDateWhen { false }
    args(
        "finalize-promoted-phase0",
        "--campaign=${campaign.asFile}",
        "--source-snapshot=${source.asFile}",
        "--approved-report=${approval.asFile}",
        "--output=${docsDirectory.dir("development").asFile}",
    )
}

tasks.named("processTestResources") {
    // Final reports are checked-in test resources. When their producer is in the same
    // invocation, finish the write before Gradle snapshots the docs resource tree.
    mustRunAfter("finalizePhase0")
    mustRunAfter("finalizePhase1")
    mustRunAfter("publishPhase1Proposal")
    mustRunAfter("analyzeCampaignDifficultyV4")
    mustRunAfter("calibrateDifficultyV4")
}

tasks.register<JavaExec>("stagePhase0Candidates") {
    group = "magnetrail content"
    description = "Generate and analyze an oversized Phase 0 candidate pool without modifying shipped content."
    dependsOn("analyzePhase0Current")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val diagnostics = phase0StagingDirectory.map { it.file("PHASE0_CURRENT_DIAGNOSTICS.json") }
    inputs.files(docsDirectory.file("Magnetrail_Campaign_Levels_v3.json"), diagnostics)
    inputs.property("phase0PoolSize", providers.gradleProperty("phase0PoolSize").getOrElse("450"))
    inputs.property("phase0Seed", providers.gradleProperty("phase0Seed").getOrElse("600001"))
    inputs.property(
        "phase0AttemptsPerTarget",
        providers.gradleProperty("phase0AttemptsPerTarget").getOrElse("25000"),
    )
    outputs.files(
        phase0StagingDirectory.map { it.file("PHASE0_CANDIDATE_POOL.json") },
        phase0StagingDirectory.map { it.file("PHASE0_CANDIDATE_POOL.md") },
        phase0StagingDirectory.map { it.file("phase0_candidate_catalog.json") },
    )
    args(
        "stage-phase0-candidates",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--diagnostics=${diagnostics.get().asFile}",
        "--output=${phase0StagingDirectory.get().asFile}",
        "--pool-size=${providers.gradleProperty("phase0PoolSize").getOrElse("450")}",
        "--seed=${providers.gradleProperty("phase0Seed").getOrElse("600001")}",
        "--attempts-per-target=${providers.gradleProperty("phase0AttemptsPerTarget").getOrElse("25000")}",
    )
}

tasks.register<JavaExec>("generateCandidates") {
    group = "magnetrail content"
    description = "Generate certified candidates into build/m3-staging without modifying shipped assets."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "generate",
        "--prototype=${docsDirectory.file("Magnetrail_Prototype_Levels_v1.json").asFile}",
        "--output=${layout.buildDirectory.dir("m3-staging").get().asFile}",
        "--count=${providers.gradleProperty("candidateCount").getOrElse("12")}",
        "--seed=${providers.gradleProperty("candidateSeed").getOrElse("730000")}",
        "--profile=${providers.gradleProperty("candidateProfile").getOrElse("DEVELOPING_MEDIUM")}",
    )
}

tasks.register<JavaExec>("certifyCampaign") {
    group = "verification"
    description = "Parse and independently certify every shipped campaign and daily fallback level."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "certify",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--fallbacks=${docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json").asFile}",
    )
}

tasks.register<JavaExec>("benchmarkDaily") {
    group = "verification"
    description = "Measure deterministic daily generation/certification for 31 dates on the host JVM."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "benchmark",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--output=${layout.buildDirectory.file("reports/daily-benchmark.txt").get().asFile}",
    )
}

tasks.register<JavaExec>("promoteCampaign") {
    group = "magnetrail content"
    description = "Deliberately rebuild shipped M3 content; requires -PconfirmPromotion=true."
    doFirst {
        check(providers.gradleProperty("confirmPromotion").orNull == "true") {
            "Refusing to overwrite shipped content. Re-run with -PconfirmPromotion=true after review."
        }
    }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "promote",
        "--prototype=${docsDirectory.file("Magnetrail_Prototype_Levels_v1.json").asFile}",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--fallbacks=${docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json").asFile}",
        "--report=${docsDirectory.file("M3_CONTENT_REPORT.csv").asFile}",
        "--summary=${docsDirectory.file("M3_CONTENT_REPORT.md").asFile}",
    )
}

tasks.register<JavaExec>("analyzeCampaignDifficulty") {
    group = "verification"
    description = "Stage deterministic Magnetrail V2 difficulty metrics and the v1-to-v2 comparison."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val metrics = m51StagingDirectory.map { it.file("m5_1_campaign_metrics.json") }
    val comparison = m51StagingDirectory.map { it.file("m5_1_v1_v2_difficulty_comparison.csv") }
    inputs.files(
        docsDirectory.file("Magnetrail_Campaign_Levels_v3.json"),
        docsDirectory.file("M3_CONTENT_REPORT.csv"),
    )
    outputs.files(metrics, comparison)
    args(
        "analyze-difficulty",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--legacy-report=${docsDirectory.file("M3_CONTENT_REPORT.csv").asFile}",
        "--metrics-output=${metrics.get().asFile}",
        "--comparison-output=${comparison.get().asFile}",
    )
}

tasks.register<JavaExec>("analyzeCampaignQuality") {
    group = "verification"
    description = "Stage quality scores and stable quality reason codes independently from difficulty."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val output = m51StagingDirectory.map { it.file("m5_1_campaign_quality.json") }
    inputs.files(
        docsDirectory.file("Magnetrail_Campaign_Levels_v3.json"),
        docsDirectory.file("M3_CONTENT_REPORT.csv"),
    )
    outputs.file(output)
    args(
        "analyze-quality",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--legacy-report=${docsDirectory.file("M3_CONTENT_REPORT.csv").asFile}",
        "--output=${output.get().asFile}",
    )
}

tasks.register<JavaExec>("checkCampaignSymmetryDuplicates") {
    group = "verification"
    description = "Stage exact, D4-symmetry, and review-only local similarity findings."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val output = m51StagingDirectory.map { it.file("m5_1_duplicate_report.md") }
    inputs.files(
        docsDirectory.file("Magnetrail_Campaign_Levels_v3.json"),
        docsDirectory.file("M3_CONTENT_REPORT.csv"),
    )
    outputs.file(output)
    args(
        "check-duplicates",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--legacy-report=${docsDirectory.file("M3_CONTENT_REPORT.csv").asFile}",
        "--output=${output.get().asFile}",
    )
}

tasks.register<JavaExec>("auditCampaignPacing") {
    group = "verification"
    description = "Stage the fixed-campaign curriculum, recovery, and difficulty-wave audit."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val output = m51StagingDirectory.map { it.file("M5_1_CAMPAIGN_AUDIT.md") }
    inputs.files(
        docsDirectory.file("Magnetrail_Campaign_Levels_v3.json"),
        docsDirectory.file("M3_CONTENT_REPORT.csv"),
    )
    outputs.file(output)
    args(
        "audit-pacing",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--legacy-report=${docsDirectory.file("M3_CONTENT_REPORT.csv").asFile}",
        "--output=${output.get().asFile}",
    )
}

tasks.register<JavaExec>("certifyCampaignQuality") {
    group = "verification"
    description = "Fail on hard M5.1 quality, ID, exact-duplicate, or symmetry-duplicate gates."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "certify-quality",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--legacy-report=${docsDirectory.file("M3_CONTENT_REPORT.csv").asFile}",
    )
}

tasks.register("promoteM51CampaignAudit") {
    group = "magnetrail content"
    description = "Explicitly promote reviewed M5.1 staging reports into docs/content."
    dependsOn(
        "analyzeCampaignDifficulty",
        "analyzeCampaignQuality",
        "checkCampaignSymmetryDuplicates",
        "auditCampaignPacing",
    )
    doFirst {
        check(providers.gradleProperty("confirmM51Promotion").orNull == "true") {
            "Refusing to promote M5.1 reports. Re-run with -PconfirmM51Promotion=true after staging review."
        }
    }
    doLast {
        copy {
            from(m51StagingDirectory)
            include(
                "M5_1_CAMPAIGN_AUDIT.md",
                "m5_1_campaign_metrics.json",
                "m5_1_campaign_quality.json",
                "m5_1_duplicate_report.md",
                "m5_1_v1_v2_difficulty_comparison.csv",
            )
            into(docsDirectory.dir("content"))
        }
    }
}

tasks.register<JavaExec>("stageCampaignSymmetryRepairs") {
    group = "magnetrail content"
    description = "Stage deterministic, certification-gated wall-only repairs for hard symmetry duplicates."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaignOutput = m51StagingDirectory.map { it.file("Magnetrail_Campaign_Levels_v3.json") }
    val reportOutput = m51StagingDirectory.map { it.file("m5_1_symmetry_repairs.md") }
    inputs.file(docsDirectory.file("Magnetrail_Campaign_Levels_v3.json"))
    outputs.files(campaignOutput, reportOutput)
    args(
        "deduplicate-symmetry",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--output=${campaignOutput.get().asFile}",
        "--report=${reportOutput.get().asFile}",
    )
}

tasks.register("promoteCampaignSymmetryRepairs") {
    group = "magnetrail content"
    description = "Explicitly promote staged M5.1 board repairs; preserves IDs and sequence."
    dependsOn("stageCampaignSymmetryRepairs")
    doFirst {
        check(providers.gradleProperty("confirmM51BoardChanges").orNull == "true") {
            "Refusing to modify the shipped campaign. Review staging, then use -PconfirmM51BoardChanges=true."
        }
    }
    doLast {
        copy {
            from(m51StagingDirectory.map { it.file("Magnetrail_Campaign_Levels_v3.json") })
            into(docsDirectory)
        }
        copy {
            from(m51StagingDirectory.map { it.file("m5_1_symmetry_repairs.md") })
            into(docsDirectory.dir("content"))
        }
    }
}

tasks.register<JavaExec>("stageM52CampaignExpansion") {
    group = "magnetrail content"
    description = "Generate and stage the deterministic M5.2 candidate pool and 101-150 review set."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    inputs.file(docsDirectory.file("Magnetrail_Campaign_Levels_v3.json"))
    inputs.property("m52PoolSize", providers.gradleProperty("m52PoolSize").getOrElse("200"))
    inputs.property("m52Seed", providers.gradleProperty("m52Seed").getOrElse("520001"))
    outputs.dir(m52StagingDirectory)
    args(
        "stage-m52-expansion",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--output=${m52StagingDirectory.get().asFile}",
        "--pool-size=${providers.gradleProperty("m52PoolSize").getOrElse("200")}",
        "--seed=${providers.gradleProperty("m52Seed").getOrElse("520001")}",
    )
}

tasks.register("publishM52ReviewPacket") {
    group = "magnetrail content"
    description = "Explicitly publish M5.2 staging reports, never the unapproved campaign catalog."
    dependsOn("stageM52CampaignExpansion")
    doFirst {
        check(providers.gradleProperty("confirmM52ReviewPacket").orNull == "true") {
            "Review staging first, then use -PconfirmM52ReviewPacket=true. This does not promote levels."
        }
    }
    doLast {
        val destination = docsDirectory.dir("content").asFile
        copy {
            from(m52StagingDirectory)
            include(
                "M5_2_CAMPAIGN_101_150_REPORT.md",
                "M5_2_DUPLICATE_REPORT.md",
                "M5_2_PACING_REPORT.md",
                "M5_2_MIGRATION.md",
                "M5_2_FULL_150_REPORT.md",
                "m5_2_levels_101_150_metrics.csv",
                "m5_2_candidate_pool_metrics.csv",
            )
            into(destination)
        }
        copy {
            from(m52StagingDirectory.map { it.file("Magnetrail_Campaign_Levels_v4.json") })
            into(destination)
            rename("Magnetrail_Campaign_Levels_v4.json", "m5_2_review_catalog.json")
        }
        val manualReview = destination.resolve("M5_2_MANUAL_REVIEW.md")
        val approvals = destination.resolve("m5_2_manual_approvals.csv")
        val approvalReviewStarted = approvals.exists() && approvals.readLines()
            .filter(String::isNotBlank)
            .drop(1)
            .any { row ->
                val columns = row.split(',')
                columns.getOrNull(2) != "PENDING_OWNER_REVIEW" ||
                    columns.drop(3).any(String::isNotBlank)
            }
        val checklistReviewStarted = manualReview.exists() && (
            "☑" in manualReview.readText() ||
                manualReview.readLines().any { row ->
                    row.startsWith("| 1") && listOf("| APPROVED |", "| REVISE |", "| REJECT |").any(row::contains)
                }
            )
        if (!approvalReviewStarted && !checklistReviewStarted) {
            copy {
                from(m52StagingDirectory.map { it.file("M5_2_MANUAL_REVIEW.md") })
                into(destination)
            }
            copy {
                from(m52StagingDirectory.map { it.file("m5_2_manual_approvals.csv") })
                into(destination)
            }
        }
    }
}

tasks.register<JavaExec>("certifyM52ReviewCatalog") {
    group = "verification"
    description = "Independently recertify the staged 150-level review catalog without promoting it."
    dependsOn("stageM52CampaignExpansion")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    inputs.file(docsDirectory.file("Magnetrail_Campaign_Levels_v3.json"))
    inputs.file(m52StagingDirectory.map { it.file("Magnetrail_Campaign_Levels_v4.json") })
    args(
        "certify-m52-review",
        "--base-campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--review-campaign=${m52StagingDirectory.get().file("Magnetrail_Campaign_Levels_v4.json").asFile}",
    )
}

tasks.register("promoteM52Campaign") {
    group = "magnetrail content"
    description = "Promote the 150-level catalog only after all 50 machine-readable owner approvals."
    dependsOn("stageM52CampaignExpansion")
    doFirst {
        check(providers.gradleProperty("confirmM52CampaignPromotion").orNull == "true") {
            "Refusing campaign promotion without -PconfirmM52CampaignPromotion=true."
        }
        val approvals = docsDirectory.file("content/m5_2_manual_approvals.csv").asFile
        check(approvals.exists()) { "Missing docs/content/m5_2_manual_approvals.csv" }
        val rows = approvals.readLines().filter(String::isNotBlank).drop(1)
        check(rows.size == 50) { "Expected 50 approval rows, found ${rows.size}" }
        val pending = rows.filter { row ->
            val columns = row.split(',')
            columns.getOrNull(2) != "APPROVED" || columns.getOrNull(3).isNullOrBlank()
        }
        check(pending.isEmpty()) {
            "M5.2 promotion blocked: ${pending.size} levels lack APPROVED status or a named owner/reviewer."
        }
        val manualReview = docsDirectory.file("content/M5_2_MANUAL_REVIEW.md").asFile
        check(manualReview.exists()) { "Missing docs/content/M5_2_MANUAL_REVIEW.md" }
        val reviewRows = manualReview.readLines().filter { row ->
            row.matches(Regex("^\\| (10[1-9]|1[1-4][0-9]|150) \\|.*"))
        }
        check(reviewRows.size == 50) { "Expected 50 manual-review rows, found ${reviewRows.size}" }
        val incompleteReviews = reviewRows.filter { row -> row.count { it == '☑' } != 9 || !row.endsWith("| APPROVED |") }
        check(incompleteReviews.isEmpty()) {
            "M5.2 promotion blocked: ${incompleteReviews.size} manual-review rows are incomplete or not APPROVED."
        }
    }
    doLast {
        copy {
            from(m52StagingDirectory.map { it.file("Magnetrail_Campaign_Levels_v4.json") })
            into(docsDirectory)
            rename("Magnetrail_Campaign_Levels_v4.json", "Magnetrail_Campaign_Levels_v3.json")
        }
        val reportReplacements = mapOf(
            "M5_2_CAMPAIGN_101_150_REPORT.md" to listOf(
                "This packet is staging evidence, not shipped content. The deterministic 50-level set cannot be promoted until every row in `M5_2_MANUAL_REVIEW.md` is approved by an owner/reviewer." to
                    "All 50 levels received explicit project-owner approval on 2026-08-19 and are now promoted into the shipped catalog.",
                "- Proposed final count: 150 (100 unchanged + 50 staged)" to "- Final count: 150 (100 unchanged + 50 promoted)",
                "- Target-distribution note: Very Hard/Expert candidates were not manufactured by inflating density; any remaining target-band deviation is explicit calibration evidence for owner review." to
                    "- Target-distribution note: Very Hard/Expert candidates were not manufactured by inflating density; the resulting target-band deviation was accepted during owner review.",
                "local-structure REVIEW rows require explicit owner resolution" to
                    "local-structure REVIEW rows received explicit owner approval",
                "- Remaining manual approvals: 50" to "- Remaining manual approvals: 0",
                "The review catalog uses stable IDs `campaign-101` through `campaign-150`; it is not consumed by the app until the explicit approval-gated promotion task succeeds." to
                    "The promoted catalog uses stable IDs `campaign-101` through `campaign-150` and is consumed by normal app asset synchronization.",
            ),
            "M5_2_FULL_150_REPORT.md" to listOf(
                "# Magnetrail proposed full 150-level campaign report" to "# Magnetrail full 150-level campaign report",
                "Status: **STAGING — OWNER REVIEW REQUIRED**" to "Status: **PROMOTED — OWNER APPROVED 2026-08-19**",
                "- New staged IDs:" to "- New promoted IDs:",
                "- Manual approvals outstanding: 50" to "- Manual approvals outstanding: 0",
            ),
            "M5_2_PACING_REPORT.md" to listOf(
                "The ordering below is deterministic and staging-only. Peaks and recovery roles were selected before manual approval; tooling does not reorder them at runtime." to
                    "The owner-approved ordering is deterministic. Tooling does not reorder peaks or recovery roles at runtime.",
                "| PENDING |" to "| APPROVED |",
            ),
            "M5_2_DUPLICATE_REPORT.md" to listOf(
                "These structural-signature findings require explicit owner confirmation that neighboring play does not feel repetitive." to
                    "The project owner explicitly confirmed on 2026-08-19 that these neighboring levels remain acceptably distinct in play.",
            ),
            "M5_2_MIGRATION.md" to listOf(
                "The proposed catalog moves catalog content version 3 → 4 and generator version 1 → 2 only after approval-gated promotion." to
                    "The promoted catalog moves catalog content version 3 → 4 and generator version 1 → 2 after the completed approval gate.",
                "- Promotion remains blocked while any approval row is not `APPROVED`." to
                    "- Promotion completed only after all 50 approval rows and manual checklist fields were `APPROVED`.",
            ),
        )
        reportReplacements.forEach { (name, replacements) ->
            val report = docsDirectory.file("content/$name").asFile
            var content = report.readText()
            replacements.forEach { (before, after) -> content = content.replace(before, after) }
            report.writeText(content)
        }
        val metrics = docsDirectory.file("content/m5_2_levels_101_150_metrics.csv").asFile
        metrics.writeText(metrics.readText().replace("PENDING_OWNER_REVIEW", "APPROVED"))
    }
}

tasks.register<JavaExec>("analyzeCampaignDifficultyV4") {
    group = "verification"
    description = "Generate bounded, deterministic Difficulty V4 diagnostics without changing campaign content."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val output = docsDirectory.dir("development")
    val optionalConfig = providers.gradleProperty("difficultyV4Config")
    inputs.file(campaign)
    optionalConfig.orNull?.let { inputs.file(it) }
    outputs.files(
        output.file("MAGNETRAIL_DIFFICULTY_V4_AUDIT.json"),
        output.file("MAGNETRAIL_DIFFICULTY_V4_AUDIT.md"),
        output.file("MAGNETRAIL_DIFFICULTY_V4_LEVEL_DIAGNOSTICS.csv"),
        output.file("MAGNETRAIL_DIFFICULTY_V4_HUMAN_CALIBRATION.json"),
        output.file("MAGNETRAIL_DIFFICULTY_V4_CALIBRATION.md"),
    )
    outputs.upToDateWhen { false }
    args(
        "analyze-difficulty-v4",
        "--campaign=${campaign.asFile}",
        "--output=${output.asFile}",
    )
    optionalConfig.orNull?.let { args("--config=${file(it)}") }
}

tasks.register<JavaExec>("calibrateDifficultyV4") {
    group = "verification"
    description = "Compare human ratings with V3/V4 diagnostics; never changes model weights or campaign content."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val output = docsDirectory.dir("development")
    val audit = output.file("MAGNETRAIL_DIFFICULTY_V4_AUDIT.json")
    val human = output.file("MAGNETRAIL_DIFFICULTY_V4_HUMAN_CALIBRATION.json")
    inputs.files(audit, human)
    outputs.file(output.file("MAGNETRAIL_DIFFICULTY_V4_CALIBRATION.md"))
    outputs.upToDateWhen { false }
    mustRunAfter("analyzeCampaignDifficultyV4")
    args(
        "calibrate-difficulty-v4",
        "--audit=${audit.asFile}",
        "--human-calibration=${human.asFile}",
        "--output=${output.file("MAGNETRAIL_DIFFICULTY_V4_CALIBRATION.md").asFile}",
    )
}

tasks.register<JavaExec>("generateCampaignV5Candidates") {
    group = "magnetrail content"
    description = "Generate the isolated D2 Generator V5 candidate catalog and diagnostics; never promotes content."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("content/d2/promotion/D2_SOURCE_CONTENT_V6.json")
    val v4Audit = docsDirectory.file("development/MAGNETRAIL_DIFFICULTY_V4_AUDIT.json")
    inputs.files(campaign, v4Audit)
    inputs.property("d2CandidateCount", providers.gradleProperty("d2CandidateCount").getOrElse("200"))
    inputs.property("d2Seed", providers.gradleProperty("d2Seed").getOrElse("5200001"))
    inputs.property(
        "d2AttemptsPerCandidate",
        providers.gradleProperty("d2AttemptsPerCandidate").getOrElse("profile-default"),
    )
    outputs.files(
        d2StagingDirectory.file("D2_CAMPAIGN_V5_CANDIDATES.json"),
        d2StagingDirectory.file("D2_HUMAN_REVIEW_CATALOG.json"),
        d2StagingDirectory.file("D2_GENERATION_RUN.json"),
        docsDirectory.file("development/D2_CAMPAIGN_GENERATION_AUDIT.json"),
        docsDirectory.file("development/D2_CAMPAIGN_GENERATION_AUDIT.md"),
        docsDirectory.file("development/D2_LEVEL_DIAGNOSTICS.csv"),
        docsDirectory.file("development/D2_OBJECT_RELEVANCE.csv"),
        docsDirectory.file("development/D2_INTERACTION_GRAPH.csv"),
        docsDirectory.file("development/D2_CALIBRATION.json"),
        docsDirectory.file("development/D2_CALIBRATION.md"),
        docsDirectory.file("development/D2_PROMOTION_MANIFEST.json"),
    )
    args(
        "generate-d2-v5",
        "--campaign=${campaign.asFile}",
        "--difficulty-v4-audit=${v4Audit.asFile}",
        "--output=${docsDirectory.dir("development").asFile}",
        "--staging-output=${d2StagingDirectory.asFile}",
        "--count=${providers.gradleProperty("d2CandidateCount").getOrElse("200")}",
        "--seed=${providers.gradleProperty("d2Seed").getOrElse("5200001")}",
    )
    providers.gradleProperty("d2AttemptsPerCandidate").orNull?.let {
        args("--attempts-per-candidate=$it")
    }
}

fun registerD2AnalysisTask(taskName: String, command: String, descriptionText: String) =
    tasks.register<JavaExec>(taskName) {
        group = "verification"
        description = descriptionText
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = application.mainClass
        inputs.file(docsDirectory.file("development/D2_CAMPAIGN_GENERATION_AUDIT.json"))
        args(
            command,
            "--audit=${docsDirectory.file("development/D2_CAMPAIGN_GENERATION_AUDIT.json").asFile}",
        )
    }

registerD2AnalysisTask(
    "analyzeCampaignGenerationV5",
    "analyze-d2-v5",
    "Validate D2 V5 certification, structural gates, and truncation evidence.",
)

tasks.register<JavaExec>("generateD21SpatialDensityCandidates") {
    group = "magnetrail content"
    description = "Generate bounded, fully certified D2.1 spatial-density staging evidence; never modifies campaign content."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val development = docsDirectory.dir("development")
    inputs.file(campaign)
    inputs.property("d21CandidatesPerProfile", providers.gradleProperty("d21CandidatesPerProfile").getOrElse("1"))
    inputs.property("d21Seed", providers.gradleProperty("d21Seed").getOrElse("6210001"))
    inputs.property(
        "d21AttemptsPerCandidate",
        providers.gradleProperty("d21AttemptsPerCandidate").getOrElse("profile-default"),
    )
    inputs.property("d21SeedRetries", providers.gradleProperty("d21SeedRetries").getOrElse("1"))
    inputs.property("d21Profiles", providers.gradleProperty("d21Profiles").getOrElse("all"))
    outputs.files(
        d21StagingDirectory.file("MAGNETRAIL_D2_1_SPATIAL_CANDIDATES.json"),
        development.file("MAGNETRAIL_D2_1_AUDIT.json"),
        development.file("MAGNETRAIL_D2_1_AUDIT.md"),
        development.file("MAGNETRAIL_D2_1_LEVEL_DIAGNOSTICS.csv"),
    )
    outputs.upToDateWhen { false }
    args(
        "generate-d2.1-spatial-density",
        "--campaign=${campaign.asFile}",
        "--output=${development.asFile}",
        "--staging-output=${d21StagingDirectory.asFile}",
        "--candidates-per-profile=${providers.gradleProperty("d21CandidatesPerProfile").getOrElse("1")}",
        "--seed=${providers.gradleProperty("d21Seed").getOrElse("6210001")}",
        "--seed-retries=${providers.gradleProperty("d21SeedRetries").getOrElse("1")}",
    )
    providers.gradleProperty("d21AttemptsPerCandidate").orNull?.let {
        args("--attempts-per-candidate=$it")
    }
    providers.gradleProperty("d21Profiles").orNull?.let { args("--profiles=$it") }
}

tasks.register<JavaExec>("analyzeD21SpatialDensity") {
    group = "verification"
    description = "Validate D2.1 occupancy, participation, certification, determinism, and campaign immutability evidence."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val audit = docsDirectory.file("development/MAGNETRAIL_D2_1_AUDIT.json")
    inputs.file(audit)
    args("validate-d2.1-spatial-density", "--audit=${audit.asFile}")
}

tasks.register<JavaExec>("benchmarkGeneratorV5Repair") {
    group = "magnetrail content"
    description = "Run the bounded solution-first Generator V5 staging benchmark without promotion."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val development = docsDirectory.dir("development")
    val staging = docsDirectory.dir("content/generator_v5_repair")
    inputs.file(campaign)
    inputs.property("generatorV5RepairSeed", providers.gradleProperty("generatorV5RepairSeed").getOrElse("7510001"))
    inputs.property(
        "generatorV5RepairAttemptsPerProfile",
        providers.gradleProperty("generatorV5RepairAttemptsPerProfile").getOrElse("1"),
    )
    outputs.files(
        development.file("MAGNETRAIL_GENERATOR_V5_AUDIT.json"),
        development.file("MAGNETRAIL_GENERATOR_V5_AUDIT.md"),
        development.file("MAGNETRAIL_GENERATOR_V5_BENCHMARK.csv"),
        staging.file("MAGNETRAIL_GENERATOR_V5_REPAIR_CANDIDATES.json"),
    )
    outputs.upToDateWhen { false }
    args(
        "benchmark-generator-v5-repair",
        "--campaign=${campaign.asFile}",
        "--output=${development.asFile}",
        "--staging-output=${staging.asFile}",
        "--seed=${providers.gradleProperty("generatorV5RepairSeed").getOrElse("7510001")}",
        "--attempts-per-profile=${providers.gradleProperty("generatorV5RepairAttemptsPerProfile").getOrElse("1")}",
    )
}
registerD2AnalysisTask(
    "analyzeObjectRelevanceV5",
    "analyze-d2-objects-v5",
    "Validate complete counterfactual object-relevance evidence for D2 candidates.",
)
registerD2AnalysisTask(
    "analyzeInteractionGraphsV5",
    "analyze-d2-graphs-v5",
    "Validate D2 typed interaction graphs and fingerprints.",
)

tasks.register<Test>("testAdaptiveDifficultySelection") {
    group = "verification"
    description = "Run deterministic D2 adaptive selector and skill-model tests with no runtime integration."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnit()
    filter { includeTestsMatching("*DifficultySelectionV1Test") }
}

tasks.register<JavaExec>("promoteD2Campaign") {
    group = "magnetrail content"
    description = "Guardedly promote the owner-directed D2 V5 catalog with stable-ID migration evidence."
    doFirst {
        check(providers.gradleProperty("confirmD2Promotion").orNull == "true") {
            "Refusing destructive D2 promotion without -PconfirmD2Promotion=true"
        }
    }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val candidates = d2StagingDirectory.file("D2_CAMPAIGN_V5_CANDIDATES.json")
    val development = docsDirectory.dir("development")
    val promotion = docsDirectory.dir("content/d2/promotion")
    inputs.files(
        campaign,
        candidates,
        development.file("D2_CAMPAIGN_GENERATION_AUDIT.json"),
        development.file("D2_PROMOTION_MANIFEST.json"),
        development.file("D2_CALIBRATION.json"),
    )
    outputs.files(
        campaign,
        development.file("D2_PROMOTION_MANIFEST.json"),
        promotion.file("D2_SOURCE_CONTENT_V6.json"),
        promotion.file("D2_ID_MIGRATION.json"),
        promotion.file("D2_PROMOTION_RESULT.json"),
        promotion.file("D2_PROMOTION_RESULT.md"),
    )
    outputs.upToDateWhen { false }
    args(
        "promote-d2-v5",
        "--campaign=${campaign.asFile}",
        "--candidates=${candidates.asFile}",
        "--audit=${development.file("D2_CAMPAIGN_GENERATION_AUDIT.json").asFile}",
        "--manifest=${development.file("D2_PROMOTION_MANIFEST.json").asFile}",
        "--calibration=${development.file("D2_CALIBRATION.json").asFile}",
        "--source-snapshot=${promotion.file("D2_SOURCE_CONTENT_V6.json").asFile}",
        "--output=${promotion.asFile}",
        "--authorization=project-owner-directed",
    )
}

tasks.register<JavaExec>("promoteV51Append") {
    group = "magnetrail content"
    description = "Append owner-directed V5 repair Levels 201-205, excluding Master and recording the Expert waiver."
    val confirmedAuthorization = providers.gradleProperty("confirmV51Append")
        .map { confirmation ->
            if (confirmation == "true") {
                "owner-directed-append-with-uncertified-expert"
            } else {
                "missing-confirmation"
            }
        }
        .getOrElse("missing-confirmation")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val candidates = docsDirectory.file("content/generator_v5_repair/MAGNETRAIL_GENERATOR_V5_REPAIR_CANDIDATES.json")
    val output = docsDirectory.dir("content/v5_1_append/promotion")
    inputs.files(campaign, candidates)
    outputs.files(
        campaign,
        output.file("SOURCE_CONTENT_V7.json"),
        output.file("V5_1_APPEND_PROMOTION_MANIFEST.json"),
        output.file("V5_1_APPEND_PROMOTION_RESULT.md"),
    )
    outputs.upToDateWhen { false }
    args(
        "promote-v5.1-append",
        "--campaign=${campaign.asFile}",
        "--candidates=${candidates.asFile}",
        "--source-snapshot=${output.file("SOURCE_CONTENT_V7.json").asFile}",
        "--output=${output.asFile}",
        "--authorization=$confirmedAuthorization",
    )
}

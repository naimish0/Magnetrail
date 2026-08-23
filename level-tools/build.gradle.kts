import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.MessageDigest

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
        resources.exclude("content/combined_v10_v11/staging/**")
        resources.exclude("content/generator_v6_1/staging/**")
        resources.exclude("content/generator_v6_1/benchmark/**")
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
val campaignV10StagingDirectory = layout.buildDirectory.dir("campaign-v10-staging")
val campaignV10CheckpointDirectory = layout.buildDirectory.dir("campaign-v10-checkpoints")
val campaignV10SourceSnapshot = docsDirectory.file("content/v10_density_remediation/SOURCE_CONTENT_V9.json")
val campaignV10SourceFile = providers.provider {
    campaignV10SourceSnapshot.asFile.takeIf { it.isFile }
        ?: docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile
}
val generatorV6Directory = docsDirectory.dir("content/generator_v6")
val generatorV61Directory = docsDirectory.dir("content/generator_v6_1")
val generatorV61RelevantSourcePaths = listOf(
    rootProject.file("game-core/src"),
    rootProject.file("game-core/build.gradle.kts"),
    rootProject.file("level-tools/src"),
    rootProject.file("level-tools/build.gradle.kts"),
    rootProject.file("app/src"),
    rootProject.file("app/build.gradle.kts"),
    rootProject.file("gradle/libs.versions.toml"),
)
val generatorV61RelevantSourcePathArgument =
    generatorV61RelevantSourcePaths.joinToString(File.pathSeparator, transform = File::getPath)

tasks.register<JavaExec>("analyzeGeneratorV61Regression") {
    group = "verification"
    description = "Freeze the failed V6 human evidence and verify V6.1 caps plus semantic uniqueness."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val v6Catalog = generatorV6Directory.file("staging/calibration/GENERATOR_V6_CALIBRATION_CATALOG.json")
    val v6Audit = generatorV6Directory.file("staging/calibration/GENERATOR_V6_CALIBRATION_AUDIT.json")
    val human = docsDirectory.file("magnetrail-playtest-naimish-b054cc41.csv")
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    inputs.files(v6Catalog, v6Audit, human, campaign)
    outputs.dir(generatorV61Directory.dir("regression"))
    args(
        "analyze-generator-v6.1-regression",
        "--v6-catalog=${v6Catalog.asFile}",
        "--v6-audit=${v6Audit.asFile}",
        "--human-results=${human.asFile}",
        "--campaign=${campaign.asFile}",
        "--output=${generatorV61Directory.dir("regression").asFile}",
    )
}

tasks.named("processTestResources") {
    mustRunAfter("analyzeGeneratorV61Regression")
}

tasks.register<JavaExec>("probeGeneratorV61") {
    group = "verification"
    description = "Probe one strictly gated V6.1 board per automated band; writes staging diagnostics only."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    val daily = docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json")
    val v11 = docsDirectory.file("content/v11_pilot/V11_PILOT_CATALOG.json")
    val audit = generatorV6Directory.file("staging/calibration/GENERATOR_V6_CALIBRATION_AUDIT.json")
    val expertCapacityAudit = generatorV61Directory.file("capacity/expert/GENERATOR_V61_EXPERT_CAPACITY_AUDIT.json")
    val superHardCapacityAudit = generatorV61Directory.file("capacity/super_hard/GENERATOR_V61_SUPER_HARD_CAPACITY_AUDIT.json")
    val regression = generatorV61Directory.file("regression/GENERATOR_V61_HARD_NEGATIVE_REGRESSION.json")
    val output = generatorV61Directory.dir("staging/probe")
    dependsOn("analyzeGeneratorV61Regression")
    inputs.files(campaign, infinite, daily, v11, audit, expertCapacityAudit, superHardCapacityAudit, regression)
    outputs.dir(output)
    outputs.upToDateWhen { false }
    args(
        "generate-generator-v6.1-campaign",
        "--campaign=${campaign.asFile}",
        "--expected-production-sha=8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9",
        "--comparison-catalogs=${listOf(campaign, infinite, daily, v11).joinToString(File.pathSeparator) { it.asFile.path }}",
        "--v6-audit=${audit.asFile}",
        "--capacity-audits=${listOf(expertCapacityAudit, superHardCapacityAudit).joinToString(File.pathSeparator) { it.asFile.path }}",
        "--regression=${regression.asFile}",
        "--output=${output.asFile}",
        "--probe=true",
        "--maximum-attempts=${providers.gradleProperty("v61ProbeAttempts").getOrElse("8")}",
    )
}

tasks.register<JavaExec>("proveGeneratorV61ExpertCapacity") {
    group = "verification"
    description = "Prove 24 archive-aware, non-isomorphic Expert boards before any full campaign run."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    val daily = docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json")
    val v11 = docsDirectory.file("content/v11_pilot/V11_PILOT_CATALOG.json")
    val rejectedV6Audit = generatorV6Directory.file("staging/calibration/GENERATOR_V6_CALIBRATION_AUDIT.json")
    val superHardCapacityAudit = generatorV61Directory.file("capacity/super_hard/GENERATOR_V61_SUPER_HARD_CAPACITY_AUDIT.json")
    val precedingV61Audit = generatorV61Directory.file("staging/production/GENERATOR_V61_CAMPAIGN_AUDIT.json")
    val output = generatorV61Directory.dir("capacity/expert")
    dependsOn(":game-core:test")
    inputs.files(campaign, infinite, daily, v11, rejectedV6Audit, superHardCapacityAudit, precedingV61Audit)
    outputs.dir(output)
    outputs.upToDateWhen { false }
    args(
        "prove-generator-v6.1-expert-capacity",
        "--campaign=${campaign.asFile}",
        "--expected-production-sha=8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9",
        "--comparison-catalogs=${listOf(campaign, infinite, daily, v11).joinToString(File.pathSeparator) { it.asFile.path }}",
        "--archive-audits=${listOf(rejectedV6Audit, superHardCapacityAudit, precedingV61Audit).joinToString(File.pathSeparator) { it.asFile.path }}",
        "--sample-size=${providers.gradleProperty("v61ExpertCapacitySize").getOrElse("24")}",
        "--maximum-attempts=${providers.gradleProperty("v61ExpertCapacityAttempts").getOrElse("64")}",
        "--output=${output.asFile}",
    )
}

tasks.register("verifyGeneratorV61ExpertCapacity") {
    group = "verification"
    description = "Verify the hash-bound Expert capacity proof and reject stale synthesis evidence."
    val capacity = generatorV61Directory.dir("capacity/expert")
    val proof = capacity.file("GENERATOR_V61_EXPERT_CAPACITY_PROOF.json")
    val catalog = capacity.file("GENERATOR_V61_EXPERT_CAPACITY_CATALOG.json")
    val audit = capacity.file("GENERATOR_V61_EXPERT_CAPACITY_AUDIT.json")
    val synthesisSources = fileTree(rootProject.file("game-core/src/main/kotlin/com/rameshta/magnetrail/core/generation/v6")) {
        include("**/*.kt")
    }
    inputs.files(proof, catalog, audit, synthesisSources)
    doLast {
        check(proof.asFile.isFile && catalog.asFile.isFile && audit.asFile.isFile) {
            "Expert capacity artifacts are missing; run proveGeneratorV61ExpertCapacity"
        }
        val proofText = proof.asFile.readText()
        check(proofText.contains("\"status\": \"EXPERT_CAPACITY_PROVED\""))
        check(proofText.contains("\"campaignRunAuthorized\": true"))
        check(proofText.contains("\"sampleSize\": 24") && proofText.contains("\"acceptedCount\": 24"))
        val digest = MessageDigest.getInstance("SHA-256")
        fun sha256(file: File): String = digest.digest(file.readBytes()).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
        check(proofText.contains("\"catalogSha256\": \"${sha256(catalog.asFile)}\""))
        check(proofText.contains("\"auditSha256\": \"${sha256(audit.asFile)}\""))
        val newestSynthesisSource = synthesisSources.files.maxOfOrNull(File::lastModified) ?: 0L
        check(proof.asFile.lastModified() >= newestSynthesisSource) {
            "Expert synthesis source changed after the capacity proof; re-run proveGeneratorV61ExpertCapacity"
        }
    }
}

tasks.register<JavaExec>("proveGeneratorV61SuperHardCapacity") {
    group = "verification"
    description = "Prove 24 archive-aware, non-isomorphic Super Hard boards before any full campaign run."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    val daily = docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json")
    val v11 = docsDirectory.file("content/v11_pilot/V11_PILOT_CATALOG.json")
    val rejectedV6Audit = generatorV6Directory.file("staging/calibration/GENERATOR_V6_CALIBRATION_AUDIT.json")
    val precedingV61Audit = generatorV61Directory.file("staging/production/GENERATOR_V61_CAMPAIGN_AUDIT.json")
    val output = generatorV61Directory.dir("capacity/super_hard")
    dependsOn(":game-core:test")
    inputs.files(campaign, infinite, daily, v11, rejectedV6Audit, precedingV61Audit)
    outputs.dir(output)
    outputs.upToDateWhen { false }
    args(
        "prove-generator-v6.1-super-hard-capacity",
        "--campaign=${campaign.asFile}",
        "--expected-production-sha=8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9",
        "--comparison-catalogs=${listOf(campaign, infinite, daily, v11).joinToString(File.pathSeparator) { it.asFile.path }}",
        "--archive-audits=${listOf(rejectedV6Audit, precedingV61Audit).joinToString(File.pathSeparator) { it.asFile.path }}",
        "--sample-size=${providers.gradleProperty("v61SuperHardCapacitySize").getOrElse("24")}",
        "--maximum-attempts=${providers.gradleProperty("v61SuperHardCapacityAttempts").getOrElse("64")}",
        "--output=${output.asFile}",
    )
}

tasks.register("verifyGeneratorV61SuperHardCapacity") {
    group = "verification"
    description = "Verify the hash-bound Super Hard capacity proof and reject stale synthesis evidence."
    val capacity = generatorV61Directory.dir("capacity/super_hard")
    val proof = capacity.file("GENERATOR_V61_SUPER_HARD_CAPACITY_PROOF.json")
    val catalog = capacity.file("GENERATOR_V61_SUPER_HARD_CAPACITY_CATALOG.json")
    val audit = capacity.file("GENERATOR_V61_SUPER_HARD_CAPACITY_AUDIT.json")
    val synthesisSources = fileTree(rootProject.file("game-core/src/main/kotlin/com/rameshta/magnetrail/core/generation/v6")) {
        include("**/*.kt")
    }
    inputs.files(proof, catalog, audit, synthesisSources)
    doLast {
        check(proof.asFile.isFile && catalog.asFile.isFile && audit.asFile.isFile) {
            "Super Hard capacity artifacts are missing; run proveGeneratorV61SuperHardCapacity"
        }
        val proofText = proof.asFile.readText()
        check(proofText.contains("\"status\": \"SUPER_HARD_CAPACITY_PROVED\""))
        check(proofText.contains("\"campaignRunAuthorized\": true"))
        check(proofText.contains("\"sampleSize\": 24") && proofText.contains("\"acceptedCount\": 24"))
        val digest = MessageDigest.getInstance("SHA-256")
        fun sha256(file: File): String = digest.digest(file.readBytes()).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
        check(proofText.contains("\"catalogSha256\": \"${sha256(catalog.asFile)}\""))
        check(proofText.contains("\"auditSha256\": \"${sha256(audit.asFile)}\""))
        val newestSynthesisSource = synthesisSources.files.maxOfOrNull(File::lastModified) ?: 0L
        check(proof.asFile.lastModified() >= newestSynthesisSource) {
            "Super Hard synthesis source changed after the capacity proof; re-run proveGeneratorV61SuperHardCapacity"
        }
    }
}

tasks.register<JavaExec>("benchmarkGeneratorV61AutoJourney") {
    group = "verification"
    description = "Measure bounded V6.1 mobile-budget attempts on the host; never mutates production."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val output = generatorV61Directory.dir("benchmark")
    outputs.dir(output)
    outputs.upToDateWhen { false }
    args(
        "benchmark-generator-v6.1-auto-journey",
        "--output=${output.asFile}",
    )
}

tasks.register<JavaExec>("benchmarkGeneratorV61ParallelWorkflow") {
    group = "verification"
    description = "Benchmark deterministic indexed multi-core admission against the former sequential scan."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val output = generatorV61Directory.dir("benchmark/parallel-workflow")
    outputs.dir(output)
    outputs.upToDateWhen { false }
    args(
        "benchmark-generator-v6.1-parallel-workflow",
        "--workers=${providers.gradleProperty("v61Workers").getOrElse((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(2).toString())}",
        "--output=${output.asFile}",
    )
}

tasks.register<JavaExec>("prepareGeneratorV61Preflight") {
    group = "verification"
    description = "Run mandatory gates once per relevant V6.1 revision and persist their hash-bound result."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val output = layout.buildDirectory.file("v61-preflight/GENERATOR_V61_PREFLIGHT.json")
    dependsOn(
        "analyzeGeneratorV61Regression",
        ":game-core:test",
        ":level-tools:test",
        ":app:testDebugUnitTest",
        ":app:lintRelease",
        ":app:compileReleaseKotlin",
        ":app:compileDebugAndroidTestKotlin",
        ":app:verifyGeneratorV6ReleaseExclusion",
        ":app:verifyReleaseManifest",
    )
    inputs.files(generatorV61RelevantSourcePaths)
    inputs.file(campaign)
    outputs.file(output)
    args(
        "write-generator-v6.1-preflight",
        "--campaign=${campaign.asFile}",
        "--relevant-source-paths=$generatorV61RelevantSourcePathArgument",
        "--output=${output.get().asFile}",
        "--mandatory-tests-passed=true",
        "--test-command-summary=game-core:test;level-tools:test;app:testDebugUnitTest;app:lintRelease;" +
            "app:compileReleaseKotlin;app:compileDebugAndroidTestKotlin;app:verifyGeneratorV6ReleaseExclusion;" +
            "app:verifyReleaseManifest",
    )
}

tasks.register<JavaExec>("generateGeneratorV61Phase1Candidates") {
    group = "magnetrail content"
    description = "Generate and certify the 1,325 Easy/Medium/Hard V6.1 phase; never mutates production."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    val daily = docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json")
    val v11 = docsDirectory.file("content/v11_pilot/V11_PILOT_CATALOG.json")
    val audit = generatorV6Directory.file("staging/calibration/GENERATOR_V6_CALIBRATION_AUDIT.json")
    val regression = generatorV61Directory.file("regression/GENERATOR_V61_HARD_NEGATIVE_REGRESSION.json")
    val output = generatorV61Directory.dir("staging/phase-1")
    val checkpoint = layout.buildDirectory.dir("generator-v6_1/checkpoints/phase-1")
    val preflight = layout.buildDirectory.file("v61-preflight/GENERATOR_V61_PREFLIGHT.json")
    val precedingV61Audit = generatorV61Directory.file("staging/production/GENERATOR_V61_CAMPAIGN_AUDIT.json")
    dependsOn(
        "prepareGeneratorV61Preflight",
    )
    inputs.files(campaign, infinite, daily, v11, audit, regression, precedingV61Audit, preflight)
    outputs.files(
        output.file("GENERATOR_V61_PHASE1_CATALOG.json"),
        output.file("GENERATOR_V61_PHASE1_AUDIT.json"),
        output.file("GENERATOR_V61_PHASE1_MANIFEST.json"),
        output.file("GENERATOR_V61_PHASE1_CERTIFICATE.json"),
    )
    args(
        "generate-generator-v6.1-campaign",
        "--campaign=${campaign.asFile}",
        "--expected-production-sha=8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9",
        "--comparison-catalogs=${listOf(campaign, infinite, daily, v11).joinToString(File.pathSeparator) { it.asFile.path }}",
        "--v6-audit=${audit.asFile}",
        "--archive-audits=${precedingV61Audit.asFile}",
        "--regression=${regression.asFile}",
        "--output=${output.asFile}",
        "--checkpoint=${checkpoint.get().asFile}",
        "--preflight=${preflight.get().asFile}",
        "--relevant-source-paths=$generatorV61RelevantSourcePathArgument",
        "--probe=false",
        "--phase=phase-1",
        "--maximum-attempts=${providers.gradleProperty("v61Phase1Attempts").orElse(providers.gradleProperty("v61CampaignAttempts")).getOrElse("64")}",
        "--workers=${providers.gradleProperty("v61Workers").getOrElse((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1).toString())}",
        "--working-tree-identity=${providers.gradleProperty("v61WorkingTreeIdentity").getOrElse("local-dirty-worktree")}",
        "--mandatory-tests-passed=true",
        "--test-command-summary=game-core:test;level-tools:test;app:testDebugUnitTest;app:lintRelease;" +
            "app:compileReleaseKotlin;app:compileDebugAndroidTestKotlin;app:verifyGeneratorV6ReleaseExclusion;" +
            "app:verifyReleaseManifest",
    )
}

tasks.register("certifyGeneratorV61Phase1Candidates") {
    group = "verification"
    description = "Fail unless all 1,325 Easy/Medium/Hard phase boards are certified."
    dependsOn("generateGeneratorV61Phase1Candidates")
    val staging = generatorV61Directory.dir("staging/phase-1")
    val candidate = staging.file("GENERATOR_V61_PHASE1_CATALOG.json")
    val certificate = staging.file("GENERATOR_V61_PHASE1_CERTIFICATE.json")
    inputs.files(candidate, certificate)
    doLast {
        check(candidate.asFile.isFile && certificate.asFile.isFile)
        val text = certificate.asFile.readText()
        check(text.contains("\"certificationType\": \"V61_PHASE_1_CERTIFIED\""))
        check(text.contains("\"boardCount\": 1325"))
        check(text.contains("\"pacingDeferredToFinalMerge\": true"))
        listOf(
            "allAnalysisComplete", "allVisibleProofsComplete", "allProductionEngineSolvableAndReplayed",
            "allDifficultyCapsPassed", "allPurposefulOccupancyPassed", "regressionCorpusPassed",
            "deterministicRegenerationPassed", "mandatoryAutomatedTestSuitePassed",
        ).forEach { gate -> check(text.contains("\"$gate\": true")) { "Phase 1 gate failed: $gate" } }
        listOf(
            "requiredGuessingPredictionCount", "exactDuplicateCount", "d4DuplicateCount",
            "relevancePrunedD4DuplicateCount", "causalGraphDuplicateCount", "decisionDagDuplicateCount",
            "solutionPolicyDuplicateCount", "synthesisGraphDuplicateCount", "nearSemanticFailureCount",
        ).forEach { gate -> check(text.contains("\"$gate\": 0")) { "Phase 1 count gate failed: $gate" } }
    }
}

tasks.register<JavaExec>("generateGeneratorV61Phase2Candidates") {
    group = "magnetrail content"
    description = "Generate and certify the 868 Super Hard/Expert V6.1 phase; never mutates production."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    val daily = docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json")
    val v11 = docsDirectory.file("content/v11_pilot/V11_PILOT_CATALOG.json")
    val audit = generatorV6Directory.file("staging/calibration/GENERATOR_V6_CALIBRATION_AUDIT.json")
    val expertCapacityAudit = generatorV61Directory.file("capacity/expert/GENERATOR_V61_EXPERT_CAPACITY_AUDIT.json")
    val superHardCapacityAudit = generatorV61Directory.file("capacity/super_hard/GENERATOR_V61_SUPER_HARD_CAPACITY_AUDIT.json")
    val regression = generatorV61Directory.file("regression/GENERATOR_V61_HARD_NEGATIVE_REGRESSION.json")
    val phaseOne = generatorV61Directory.dir("staging/phase-1")
    val phaseOneCatalog = phaseOne.file("GENERATOR_V61_PHASE1_CATALOG.json")
    val phaseOneAudit = phaseOne.file("GENERATOR_V61_PHASE1_AUDIT.json")
    val output = generatorV61Directory.dir("staging/phase-2")
    val checkpoint = layout.buildDirectory.dir("generator-v6_1/checkpoints/phase-2")
    val preflight = layout.buildDirectory.file("v61-preflight/GENERATOR_V61_PREFLIGHT.json")
    val precedingV61Audit = generatorV61Directory.file("staging/production/GENERATOR_V61_CAMPAIGN_AUDIT.json")
    dependsOn(
        "certifyGeneratorV61Phase1Candidates",
        "verifyGeneratorV61ExpertCapacity",
        "verifyGeneratorV61SuperHardCapacity",
        "prepareGeneratorV61Preflight",
    )
    inputs.files(
        campaign, infinite, daily, v11, audit, expertCapacityAudit, superHardCapacityAudit,
        regression, phaseOneCatalog, phaseOneAudit, precedingV61Audit, preflight,
    )
    outputs.files(
        output.file("GENERATOR_V61_PHASE2_CATALOG.json"),
        output.file("GENERATOR_V61_PHASE2_AUDIT.json"),
        output.file("GENERATOR_V61_PHASE2_MANIFEST.json"),
        output.file("GENERATOR_V61_PHASE2_CERTIFICATE.json"),
    )
    args(
        "generate-generator-v6.1-campaign",
        "--campaign=${campaign.asFile}",
        "--expected-production-sha=8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9",
        "--comparison-catalogs=${listOf(campaign, infinite, daily, v11, phaseOneCatalog).joinToString(File.pathSeparator) { it.asFile.path }}",
        "--v6-audit=${audit.asFile}",
        "--capacity-audits=${listOf(expertCapacityAudit, superHardCapacityAudit).joinToString(File.pathSeparator) { it.asFile.path }}",
        "--archive-audits=${listOf(phaseOneAudit, precedingV61Audit).joinToString(File.pathSeparator) { it.asFile.path }}",
        "--pacing-audits=${phaseOneAudit.asFile}",
        "--regression=${regression.asFile}",
        "--output=${output.asFile}",
        "--checkpoint=${checkpoint.get().asFile}",
        "--preflight=${preflight.get().asFile}",
        "--relevant-source-paths=$generatorV61RelevantSourcePathArgument",
        "--probe=false",
        "--phase=phase-2",
        "--maximum-attempts=${providers.gradleProperty("v61Phase2Attempts").orElse(providers.gradleProperty("v61CampaignAttempts")).getOrElse("64")}",
        "--workers=${providers.gradleProperty("v61Workers").getOrElse((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1).toString())}",
        "--working-tree-identity=${providers.gradleProperty("v61WorkingTreeIdentity").getOrElse("local-dirty-worktree")}",
        "--mandatory-tests-passed=true",
        "--test-command-summary=game-core:test;level-tools:test;app:testDebugUnitTest;app:lintRelease;" +
            "app:compileReleaseKotlin;app:compileDebugAndroidTestKotlin;app:verifyGeneratorV6ReleaseExclusion;" +
            "app:verifyReleaseManifest",
    )
}

tasks.register("certifyGeneratorV61Phase2Candidates") {
    group = "verification"
    description = "Fail unless all 868 Super Hard/Expert phase boards are certified."
    dependsOn("generateGeneratorV61Phase2Candidates")
    val staging = generatorV61Directory.dir("staging/phase-2")
    val candidate = staging.file("GENERATOR_V61_PHASE2_CATALOG.json")
    val certificate = staging.file("GENERATOR_V61_PHASE2_CERTIFICATE.json")
    inputs.files(candidate, certificate)
    doLast {
        check(candidate.asFile.isFile && certificate.asFile.isFile)
        val text = certificate.asFile.readText()
        check(text.contains("\"certificationType\": \"V61_PHASE_2_CERTIFIED\""))
        check(text.contains("\"boardCount\": 868"))
        check(text.contains("\"pacingDeferredToFinalMerge\": true"))
        listOf(
            "allAnalysisComplete", "allVisibleProofsComplete", "allProductionEngineSolvableAndReplayed",
            "allDifficultyCapsPassed", "allPurposefulOccupancyPassed", "regressionCorpusPassed",
            "deterministicRegenerationPassed", "mandatoryAutomatedTestSuitePassed",
        ).forEach { gate -> check(text.contains("\"$gate\": true")) { "Phase 2 gate failed: $gate" } }
        listOf(
            "requiredGuessingPredictionCount", "exactDuplicateCount", "d4DuplicateCount",
            "relevancePrunedD4DuplicateCount", "causalGraphDuplicateCount", "decisionDagDuplicateCount",
            "solutionPolicyDuplicateCount", "synthesisGraphDuplicateCount", "nearSemanticFailureCount",
        ).forEach { gate -> check(text.contains("\"$gate\": 0")) { "Phase 2 count gate failed: $gate" } }
    }
}

tasks.register<JavaExec>("mergeAndCertifyGeneratorV61ProductionCandidates") {
    group = "verification"
    description = "Merge both certified phases, then run global uniqueness, pacing, and 2,205-board replay certification."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    dependsOn("certifyGeneratorV61Phase1Candidates", "certifyGeneratorV61Phase2Candidates")
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val regression = generatorV61Directory.file("regression/GENERATOR_V61_HARD_NEGATIVE_REGRESSION.json")
    val phaseOne = generatorV61Directory.dir("staging/phase-1")
    val phaseTwo = generatorV61Directory.dir("staging/phase-2")
    val output = generatorV61Directory.dir("staging/production")
    val preflight = layout.buildDirectory.file("v61-preflight/GENERATOR_V61_PREFLIGHT.json")
    inputs.files(
        campaign, regression, preflight,
        phaseOne.file("GENERATOR_V61_PHASE1_CATALOG.json"),
        phaseOne.file("GENERATOR_V61_PHASE1_AUDIT.json"),
        phaseOne.file("GENERATOR_V61_PHASE1_MANIFEST.json"),
        phaseOne.file("GENERATOR_V61_PHASE1_CERTIFICATE.json"),
        phaseTwo.file("GENERATOR_V61_PHASE2_CATALOG.json"),
        phaseTwo.file("GENERATOR_V61_PHASE2_AUDIT.json"),
        phaseTwo.file("GENERATOR_V61_PHASE2_MANIFEST.json"),
        phaseTwo.file("GENERATOR_V61_PHASE2_CERTIFICATE.json"),
    )
    outputs.files(
        output.file("GENERATOR_V61_CAMPAIGN_CANDIDATE.json"),
        output.file("GENERATOR_V61_CAMPAIGN_AUDIT.json"),
        output.file("GENERATOR_V61_MANIFEST.json"),
        output.file("GENERATOR_V61_AUTOMATED_CERTIFICATE.json"),
    )
    args(
        "merge-certify-generator-v6.1-campaign",
        "--campaign=${campaign.asFile}",
        "--expected-production-sha=8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9",
        "--phase-1-catalog=${phaseOne.file("GENERATOR_V61_PHASE1_CATALOG.json").asFile}",
        "--phase-1-audit=${phaseOne.file("GENERATOR_V61_PHASE1_AUDIT.json").asFile}",
        "--phase-1-manifest=${phaseOne.file("GENERATOR_V61_PHASE1_MANIFEST.json").asFile}",
        "--phase-1-certificate=${phaseOne.file("GENERATOR_V61_PHASE1_CERTIFICATE.json").asFile}",
        "--phase-2-catalog=${phaseTwo.file("GENERATOR_V61_PHASE2_CATALOG.json").asFile}",
        "--phase-2-audit=${phaseTwo.file("GENERATOR_V61_PHASE2_AUDIT.json").asFile}",
        "--phase-2-manifest=${phaseTwo.file("GENERATOR_V61_PHASE2_MANIFEST.json").asFile}",
        "--phase-2-certificate=${phaseTwo.file("GENERATOR_V61_PHASE2_CERTIFICATE.json").asFile}",
        "--regression=${regression.asFile}",
        "--output=${output.asFile}",
        "--preflight=${preflight.get().asFile}",
        "--relevant-source-paths=$generatorV61RelevantSourcePathArgument",
        "--workers=${providers.gradleProperty("v61Workers").getOrElse((Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1).toString())}",
        "--working-tree-identity=${providers.gradleProperty("v61WorkingTreeIdentity").getOrElse("local-dirty-worktree")}",
        "--mandatory-tests-passed=true",
        "--test-command-summary=game-core:test;level-tools:test;app:testDebugUnitTest;app:lintRelease;" +
            "app:compileReleaseKotlin;app:compileDebugAndroidTestKotlin;app:verifyGeneratorV6ReleaseExclusion;" +
            "app:verifyReleaseManifest",
    )
}

tasks.register("generateGeneratorV61ProductionCandidates") {
    group = "magnetrail content"
    description = "Run V6.1 Phase 1, Phase 2, and the final merged campaign certification."
    dependsOn("mergeAndCertifyGeneratorV61ProductionCandidates")
}

tasks.register("certifyGeneratorV61ProductionCandidates") {
    group = "verification"
    description = "Fail unless both phases and the merged 2,205-board campaign are certified."
    dependsOn("generateGeneratorV61ProductionCandidates")
    val staging = generatorV61Directory.dir("staging/production")
    val candidate = staging.file("GENERATOR_V61_CAMPAIGN_CANDIDATE.json")
    val certificate = staging.file("GENERATOR_V61_AUTOMATED_CERTIFICATE.json")
    inputs.files(candidate, certificate)
    doLast {
        check(candidate.asFile.isFile && certificate.asFile.isFile)
        val text = certificate.asFile.readText()
        check(text.contains("\"validationPlan\": \"PHASED_V61_1325_868_FINAL_2205\""))
        check(text.contains("\"certificationType\": \"AUTOMATED_CAMPAIGN_CERTIFIED\"")) {
            "V6.1 phased campaign is not AUTOMATED_CAMPAIGN_CERTIFIED"
        }
        check(text.contains("\"phaseOneBoardCount\": 1325"))
        check(text.contains("\"phaseTwoBoardCount\": 868"))
        check(text.contains("\"boardCount\": 2205"))
        check(text.contains("\"certifiedBoardCount\": 2205"))
    }
}

tasks.register<JavaExec>("promoteGeneratorV61Campaign") {
    group = "magnetrail content"
    description = "Atomically promote only a hash-bound AUTOMATED_CAMPAIGN_CERTIFIED V6.1 candidate."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val staging = generatorV61Directory.dir("staging/production")
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val rollback = docsDirectory.file("content/generator_v6_1/rollback/Magnetrail_Campaign_Levels_V10_SOURCE.json")
    val receipt = generatorV61Directory.file("promotion/GENERATOR_V61_PROMOTION_RECEIPT.txt")
    dependsOn("certifyGeneratorV61ProductionCandidates")
    doFirst {
        check(providers.gradleProperty("v61ExpectedCandidateSha").isPresent)
        check(providers.gradleProperty("v61CertificateSha").isPresent)
        check(providers.gradleProperty("v61ManifestSha").isPresent)
    }
    args(
        "promote-generator-v6.1-campaign",
        "--campaign=${campaign.asFile}",
        "--candidate=${staging.file("GENERATOR_V61_CAMPAIGN_CANDIDATE.json").asFile}",
        "--certificate=${staging.file("GENERATOR_V61_AUTOMATED_CERTIFICATE.json").asFile}",
        "--manifest=${staging.file("GENERATOR_V61_MANIFEST.json").asFile}",
        "--rollback=${rollback.asFile}",
        "--receipt=${receipt.asFile}",
        "--expected-production-sha=8ad274b5dcf39f69db006c87bc0861e9b7037516755f79d8560206b4f2577de9",
        "--expected-candidate-sha=${providers.gradleProperty("v61ExpectedCandidateSha").getOrElse("missing")}",
        "--certificate-sha=${providers.gradleProperty("v61CertificateSha").getOrElse("missing")}",
        "--manifest-sha=${providers.gradleProperty("v61ManifestSha").getOrElse("missing")}",
    )
}

tasks.register<JavaExec>("auditGeneratorV6Baseline") {
    group = "verification"
    description = "Audit protected V10/Infinite/Daily/V11 baselines and representative semantic clone families."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    val daily = docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json")
    val v11 = docsDirectory.file("content/v11_pilot/V11_PILOT_CATALOG.json")
    val v9 = docsDirectory.file("content/v10_density_remediation/SOURCE_CONTENT_V9.json")
    val playtest = docsDirectory.file("magnetrail-playtest-pet-52c263fb.csv")
    inputs.files(campaign, infinite, daily, v11, v9, playtest)
    outputs.files(
        docsDirectory.file("GENERATOR_V6_BASELINE_AUDIT.json"),
        docsDirectory.file("GENERATOR_V6_BASELINE_AUDIT.md"),
    )
    outputs.upToDateWhen { false }
    args(
        "audit-generator-v6-baseline",
        "--campaign=${campaign.asFile}",
        "--infinite=${infinite.asFile}",
        "--daily=${daily.asFile}",
        "--v11=${v11.asFile}",
        "--v9-source=${v9.asFile}",
        "--playtest=${playtest.asFile}",
        "--output=${docsDirectory.asFile}",
    )
}

tasks.register<JavaExec>("probeGeneratorV6") {
    group = "verification"
    description = "Run the bounded V6 calibration generator; writes staging diagnostics only."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    val daily = docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json")
    val v11 = docsDirectory.file("content/v11_pilot/V11_PILOT_CATALOG.json")
    inputs.files(campaign, infinite, daily, v11)
    outputs.dir(generatorV6Directory.dir("staging/probe"))
    outputs.upToDateWhen { false }
    args(
        "generate-generator-v6-pilot",
        "--campaign=${campaign.asFile}",
        "--infinite=${infinite.asFile}",
        "--daily=${daily.asFile}",
        "--v11=${v11.asFile}",
        "--output=${generatorV6Directory.dir("staging/probe").asFile}",
        "--kind=calibration",
    )
}

tasks.register<JavaExec>("generateGeneratorV6Pilot") {
    group = "magnetrail content"
    description = "Generate isolated 30-board five-band calibration and sealed V6 catalogs without promotion."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    val daily = docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json")
    val v11 = docsDirectory.file("content/v11_pilot/V11_PILOT_CATALOG.json")
    val staging = generatorV6Directory.dir("staging")
    val pilotKind = providers.gradleProperty("v6PilotKind").getOrElse("both")
    inputs.files(campaign, infinite, daily, v11)
    inputs.property("generatorV6", 6)
    inputs.property("v6PilotKind", pilotKind)
    outputs.dir(staging)
    outputs.upToDateWhen { false }
    args(
        "generate-generator-v6-pilot",
        "--campaign=${campaign.asFile}",
        "--infinite=${infinite.asFile}",
        "--daily=${daily.asFile}",
        "--v11=${v11.asFile}",
        "--output=${staging.asFile}",
        "--kind=$pilotKind",
    )
}

tasks.register("analyzeGeneratorV6Pilot") {
    group = "verification"
    description = "Generate and retain complete V6 per-candidate, rejection, neighbour and MAP-Elites reports."
    dependsOn("generateGeneratorV6Pilot")
}

tasks.register("certifyGeneratorV6Pilot") {
    group = "verification"
    description = "Require complete technically certified V6 calibration and sealed catalogs."
    dependsOn("generateGeneratorV6Pilot")
    inputs.files(
        generatorV6Directory.file("staging/calibration/GENERATOR_V6_CALIBRATION_AUDIT.json"),
        generatorV6Directory.file("staging/sealed-validation/GENERATOR_V6_SEALED_VALIDATION_AUDIT.json"),
    )
    doLast {
        inputs.files.files.sortedBy { it.path }.forEach { audit ->
            check(audit.isFile && audit.readText().contains("\"status\": \"V6_TECHNICALLY_CERTIFIED\"")) {
                "V6 pilot is not technically certified: ${audit.path}"
            }
        }
    }
}

tasks.register<JavaExec>("calibrateHumanLikeDifficultyV1") {
    group = "verification"
    description = "Fit HumanLikeDifficultyV1 only from sufficient real V6 calibration results."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val staging = generatorV6Directory.dir("staging/calibration")
    val human = generatorV6Directory.dir("human")
    args(
        "calibrate-human-like-difficulty-v1",
        "--audit=${staging.file("GENERATOR_V6_CALIBRATION_AUDIT.json").asFile}",
        "--catalog=${staging.file("GENERATOR_V6_CALIBRATION_CATALOG.json").asFile}",
        "--results=${human.file("calibration_results.csv").asFile}",
        "--output=${human.asFile}",
    )
}

tasks.register<JavaExec>("validateGeneratorV6HumanCertificate") {
    group = "verification"
    description = "Validate the frozen HumanLikeDifficultyV1 certificate against sealed V6 results."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val calibration = generatorV6Directory.dir("staging/calibration")
    val validation = generatorV6Directory.dir("staging/sealed-validation")
    val human = generatorV6Directory.dir("human")
    args(
        "validate-generator-v6-human-certificate",
        "--model=${human.file("HUMAN_LIKE_DIFFICULTY_V1_MODEL.json").asFile}",
        "--calibration-results=${human.file("calibration_results.csv").asFile}",
        "--calibration-audit=${calibration.file("GENERATOR_V6_CALIBRATION_AUDIT.json").asFile}",
        "--validation-results=${human.file("sealed_validation_results.csv").asFile}",
        "--validation-audit=${validation.file("GENERATOR_V6_SEALED_VALIDATION_AUDIT.json").asFile}",
        "--validation-catalog=${validation.file("GENERATOR_V6_SEALED_VALIDATION_CATALOG.json").asFile}",
        "--output=${human.asFile}",
    )
}

tasks.register<JavaExec>("generateGeneratorV6ProductionCandidates") {
    group = "magnetrail content"
    description = "Generate full V6 campaign staging only after a certified human model exists."
    dependsOn("validateGeneratorV6HumanCertificate")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val human = generatorV6Directory.dir("human")
    val production = generatorV6Directory.dir("production")
    args(
        "generate-generator-v6-production-candidates",
        "--campaign=${docsDirectory.file("Magnetrail_Campaign_Levels_v3.json").asFile}",
        "--infinite=${docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json").asFile}",
        "--daily=${docsDirectory.file("Magnetrail_Daily_Fallbacks_v1.json").asFile}",
        "--v11=${docsDirectory.file("content/v11_pilot/V11_PILOT_CATALOG.json").asFile}",
        "--human-certificate=${human.file("HUMAN_DIFFICULTY_CERTIFICATE.json").asFile}",
        "--model=${human.file("HUMAN_LIKE_DIFFICULTY_V1_MODEL.json").asFile}",
        "--output=${production.asFile}",
    )
}

tasks.register("certifyGeneratorV6ProductionCandidates") {
    group = "verification"
    description = "Certify the frozen full V6 campaign and final human production sample."
    dependsOn("generateGeneratorV6ProductionCandidates")
}

tasks.register("prepareGeneratorV6Promotion") {
    group = "magnetrail content"
    description = "Prepare the hash-bound V6 promotion manifest without mutating production."
    dependsOn("certifyGeneratorV6ProductionCandidates")
}

tasks.register<JavaExec>("promoteGeneratorV6Campaign") {
    group = "magnetrail content"
    description = "Atomically promote only an exact hash-bound CAMPAIGN_CERTIFIED V6 campaign."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val promotion = generatorV6Directory.dir("promotion")
    val candidate = generatorV6Directory.file("production/GENERATOR_V6_PRODUCTION_CANDIDATE.json")
    val technical = generatorV6Directory.file("production/V6_TECHNICAL_CERTIFICATE.json")
    val human = generatorV6Directory.file("human/HUMAN_DIFFICULTY_CERTIFICATE.json")
    val sample = generatorV6Directory.file("production/V6_PRODUCTION_SAMPLE_CERTIFICATE.json")
    val manifest = promotion.file("V6_PROMOTION_MANIFEST.json")
    outputs.upToDateWhen { false }
    args(
        "promote-generator-v6-campaign",
        "--campaign=${campaign.asFile}",
        "--candidate=${candidate.asFile}",
        "--technical-certificate=${technical.asFile}",
        "--human-certificate=${human.asFile}",
        "--sample-certificate=${sample.asFile}",
        "--manifest=${manifest.asFile}",
        "--rollback=${promotion.file("SOURCE_CAMPAIGN_V10_ROLLBACK.json").asFile}",
        "--confirmation=${providers.gradleProperty("confirmGeneratorV6Promotion").getOrElse("false")}",
        "--expected-production-sha=${providers.gradleProperty("expectedGeneratorV6ProductionSha").getOrElse("")}",
        "--expected-content-version=${providers.gradleProperty("expectedGeneratorV6ContentVersion").getOrElse("")}",
        "--expected-candidate-sha=${providers.gradleProperty("expectedGeneratorV6CandidateSha").getOrElse("")}",
        "--technical-certificate-sha=${providers.gradleProperty("expectedGeneratorV6TechnicalCertificateSha").getOrElse("")}",
        "--human-certificate-sha=${providers.gradleProperty("expectedGeneratorV6HumanCertificateSha").getOrElse("")}",
        "--sample-certificate-sha=${providers.gradleProperty("expectedGeneratorV6SampleCertificateSha").getOrElse("")}",
        "--manifest-sha=${providers.gradleProperty("expectedGeneratorV6ManifestSha").getOrElse("")}",
    )
}

tasks.register<JavaExec>("stageCertifiedV11Merge") {
    group = "magnetrail content"
    description = "Fail-closed staging merge of V10 with only pre-certified, evidence-complete V11 boards."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val v10 = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val v10Audit = docsDirectory.file("content/v10_density_remediation/CAMPAIGN_V10_GENERATION_AUDIT.json")
    val v10Promotion = docsDirectory.file("content/v10_density_remediation/CAMPAIGN_V10_PROMOTION_RESULT.md")
    val v10Waiver = docsDirectory.file("content/v10_density_remediation/LEGACY_V10_OWNER_WAIVER.json")
    val v10HumanCsv = docsDirectory.file("magnetrail-playtest-pet-52c263fb.csv")
    val v10Rejection = docsDirectory.file("content/v11_pilot/V10_HUMAN_PLAYTEST_REJECTION.md")
    val v11 = docsDirectory.file("content/v11_pilot/V11_PILOT_CATALOG.json")
    val v11Audit = docsDirectory.file("content/v11_pilot/V11_PILOT_AUDIT.json")
    val v11Certificate = docsDirectory.file("content/v11_pilot/V11_PILOT_CERTIFICATE.json")
    val output = docsDirectory.dir("content/combined_v10_v11/staging")
    inputs.files(v10, v10Audit, v10Promotion, v10Waiver, v10HumanCsv, v10Rejection, v11, v11Audit)
    outputs.files(
        output.file("COMBINED_V10_CERTIFIED_V11_CATALOG.json"),
        output.file("COMBINED_V10_V11_MERGE_MANIFEST.json"),
        output.file("COMBINED_V10_V11_CERTIFICATE.json"),
    )
    outputs.upToDateWhen { false }
    args(
        "stage-certified-v11-merge",
        "--v10=${v10.asFile}",
        "--v10-audit=${v10Audit.asFile}",
        "--v10-promotion=${v10Promotion.asFile}",
        "--v10-waiver=${v10Waiver.asFile}",
        "--v10-human-csv=${v10HumanCsv.asFile}",
        "--v10-rejection=${v10Rejection.asFile}",
        "--v11=${v11.asFile}",
        "--v11-audit=${v11Audit.asFile}",
        "--v11-certificate=${v11Certificate.asFile}",
        "--output=${output.asFile}",
    )
}

tasks.register<JavaExec>("probeCampaignV10Remediation") {
    group = "verification"
    description = "Probe every V10 density-remediation band through production certification."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    args(
        "probe-campaign-v10-remediation",
        "--campaign=${campaignV10SourceFile.get()}",
        "--count-per-band=${providers.gradleProperty("campaignV10ProbePerBand").getOrElse("2")}",
        "--retries-per-level=${providers.gradleProperty("campaignV10RetriesPerLevel").getOrElse("24")}",
        "--seed=${providers.gradleProperty("campaignV10Seed").getOrElse("10200001")}",
    )
    providers.gradleProperty("campaignV10ProbeBand").orNull?.let { args("--band=$it") }
    providers.gradleProperty("campaignV10ProbeLevel").orNull?.let { args("--level=$it") }
}

tasks.register<JavaExec>("generateCampaignV10Remediation") {
    group = "magnetrail content"
    description = "Rebuild Levels 206-2205 with dense arrows and component-level uniqueness gates."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = campaignV10SourceFile
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    inputs.files(campaign, infinite)
    inputs.property("campaignV10Seed", providers.gradleProperty("campaignV10Seed").getOrElse("10200001"))
    inputs.property("campaignV10Workers", providers.gradleProperty("campaignV10Workers").getOrElse("10"))
    inputs.property(
        "campaignV10RetriesPerLevel",
        providers.gradleProperty("campaignV10RetriesPerLevel").getOrElse("2048"),
    )
    outputs.files(
        campaignV10StagingDirectory.map { it.file("Magnetrail_Campaign_Levels_v10.json") },
        campaignV10StagingDirectory.map { it.file("CAMPAIGN_V10_GENERATION_AUDIT.json") },
        campaignV10StagingDirectory.map { it.file("CAMPAIGN_V10_GENERATION_REPORT.md") },
    )
    outputs.upToDateWhen { false }
    args(
        "generate-campaign-v10-remediation",
        "--campaign=${campaign.get()}",
        "--infinite=${infinite.asFile}",
        "--output=${campaignV10StagingDirectory.get().asFile}",
        "--checkpoint=${campaignV10CheckpointDirectory.get().asFile}",
        "--seed=${providers.gradleProperty("campaignV10Seed").getOrElse("10200001")}",
        "--workers=${providers.gradleProperty("campaignV10Workers").getOrElse("10")}",
        "--retries-per-level=${providers.gradleProperty("campaignV10RetriesPerLevel").getOrElse("2048")}",
    )
}

tasks.register<JavaExec>("promoteCampaignV10Remediation") {
    group = "magnetrail content"
    description = "Promote certified V10 density remediation while retaining V9 migration evidence."
    dependsOn("generateCampaignV10Remediation")
    val confirmed = providers.gradleProperty("confirmCampaignV10Promotion")
    inputs.property("campaignV10PromotionConfirmed", confirmed.orElse("false"))
    doFirst {
        check(confirmed.orNull == "true") {
            "Refusing Campaign V10 promotion without -PconfirmCampaignV10Promotion=true"
        }
    }
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = application.mainClass
    val campaign = docsDirectory.file("Magnetrail_Campaign_Levels_v3.json")
    val infinite = docsDirectory.file("content/infinite/INFINITE_CERTIFIED_CATALOG_V1.json")
    val promotion = docsDirectory.dir("content/v10_density_remediation")
    args(
        "promote-campaign-v10-remediation",
        "--campaign=${campaign.asFile}",
        "--staged-campaign=${campaignV10StagingDirectory.get().file("Magnetrail_Campaign_Levels_v10.json").asFile}",
        "--staged-audit=${campaignV10StagingDirectory.get().file("CAMPAIGN_V10_GENERATION_AUDIT.json").asFile}",
        "--staged-report=${campaignV10StagingDirectory.get().file("CAMPAIGN_V10_GENERATION_REPORT.md").asFile}",
        "--infinite=${infinite.asFile}",
        "--source-snapshot=${promotion.file("SOURCE_CONTENT_V9.json").asFile}",
        "--published-audit=${promotion.file("CAMPAIGN_V10_GENERATION_AUDIT.json").asFile}",
        "--published-report=${promotion.file("CAMPAIGN_V10_GENERATION_REPORT.md").asFile}",
        "--result=${promotion.file("CAMPAIGN_V10_PROMOTION_RESULT.md").asFile}",
        "--authorization=project-owner-directed-v10-density-remediation",
    )
}

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

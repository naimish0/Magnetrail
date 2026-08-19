import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
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
    testImplementation(libs.kotlinx.serialization.json)
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

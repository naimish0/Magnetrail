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

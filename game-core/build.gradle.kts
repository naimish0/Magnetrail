import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

sourceSets {
    test {
        resources.srcDir(rootProject.file("docs"))
        // Campaign certification writes atomically into docs staging. Those mutable outputs are
        // never test fixtures and must not invalidate the hash-bound mandatory preflight.
        resources.exclude("content/combined_v10_v11/staging/**")
        resources.exclude("content/generator_v6_1/staging/**")
        resources.exclude("content/generator_v6_1/benchmark/**")
    }
}

tasks.test {
    useJUnit()
}

tasks.named("processTestResources") {
    // D1 reports live under docs, which is also a test-resource source. Preserve ordering
    // only when diagnostic writers and tests are explicitly requested together.
    mustRunAfter(":level-tools:analyzeCampaignDifficultyV4")
    mustRunAfter(":level-tools:calibrateDifficultyV4")
    mustRunAfter(":level-tools:analyzeGeneratorV61Regression")
}

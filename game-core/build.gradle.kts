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
        resources.srcDir(rootProject.layout.buildDirectory.dir("generated/magnetrailTestResources"))
    }
}

tasks.test {
    useJUnit()
}

tasks.named("processTestResources") {
    dependsOn(rootProject.tasks.named("syncDocsTestResources"))
    // D1 reports live under docs, which supplies the generated test-resource snapshot. Preserve
    // ordering only when diagnostic writers and tests are explicitly requested together.
    mustRunAfter(":level-tools:analyzeCampaignDifficultyV4")
    mustRunAfter(":level-tools:calibrateDifficultyV4")
    mustRunAfter(":level-tools:analyzeGeneratorV61Regression")
}

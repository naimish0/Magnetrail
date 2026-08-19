// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

tasks.register("certifyCampaignContent") {
    group = "verification"
    description = "Certify the checked-in Magnetrail M3 campaign and daily fallback bank."
    dependsOn(":level-tools:certifyCampaign", ":level-tools:certifyCampaignQuality")
}

tasks.register("analyzeCampaignDifficulty") {
    group = "verification"
    description = "Stage Magnetrail M5.1 V2 difficulty evidence."
    dependsOn(":level-tools:analyzeCampaignDifficulty")
}

tasks.register("analyzeCampaignQuality") {
    group = "verification"
    description = "Stage Magnetrail M5.1 level-quality evidence."
    dependsOn(":level-tools:analyzeCampaignQuality")
}

tasks.register("checkCampaignSymmetryDuplicates") {
    group = "verification"
    description = "Stage Magnetrail M5.1 symmetry duplicate findings."
    dependsOn(":level-tools:checkCampaignSymmetryDuplicates")
}

tasks.register("auditCampaignPacing") {
    group = "verification"
    description = "Stage Magnetrail M5.1 fixed-sequence pacing audit."
    dependsOn(":level-tools:auditCampaignPacing")
}

tasks.register("promoteM51CampaignAudit") {
    group = "magnetrail content"
    description = "Explicitly promote reviewed M5.1 reports with -PconfirmM51Promotion=true."
    dependsOn(":level-tools:promoteM51CampaignAudit")
}

tasks.register("generateLevelCandidates") {
    group = "magnetrail content"
    description = "Generate candidates to level-tools/build/m3-staging."
    dependsOn(":level-tools:generateCandidates")
}

tasks.register("benchmarkDailyChallenge") {
    group = "verification"
    description = "Benchmark 31 deterministic daily candidates on the host JVM."
    dependsOn(":level-tools:benchmarkDaily")
}

tasks.register("promoteCampaignContent") {
    group = "magnetrail content"
    description = "Explicitly rebuild checked-in M3 content after review."
    dependsOn(":level-tools:promoteCampaign")
}

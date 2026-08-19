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

tasks.register("stageM52CampaignExpansion") {
    group = "magnetrail content"
    description = "Stage the deterministic M5.2 candidate pool and 150-level review catalog."
    dependsOn(":level-tools:stageM52CampaignExpansion")
}

tasks.register("publishM52ReviewPacket") {
    group = "magnetrail content"
    description = "Publish M5.2 review evidence without shipping unapproved levels."
    dependsOn(":level-tools:publishM52ReviewPacket")
}

tasks.register("certifyM52ReviewCatalog") {
    group = "verification"
    description = "Recertify the staged M5.2 150-level review catalog."
    dependsOn(":level-tools:certifyM52ReviewCatalog")
}

tasks.register("promoteM52Campaign") {
    group = "magnetrail content"
    description = "Approval-gated promotion of the reviewed 150-level M5.2 catalog."
    dependsOn(":level-tools:promoteM52Campaign")
}

tasks.register("analyzePhase0Current") {
    group = "verification"
    description = "Stage Phase 0 Puzzle Difficulty v3 diagnostics for the current 150-level catalog."
    dependsOn(":level-tools:analyzePhase0Current")
}

tasks.register("stagePhase0Candidates") {
    group = "magnetrail content"
    description = "Stage the Phase 0 candidate pool and diagnostics; never modifies the checked-in campaign."
    dependsOn(":level-tools:stagePhase0Candidates")
}

tasks.register("publishPhase0Evidence") {
    group = "documentation"
    description = "Publish Phase 0 diagnostics/staging evidence without modifying campaign content."
    dependsOn(":level-tools:publishPhase0Evidence")
}

tasks.register("planPhase0Remediation") {
    group = "magnetrail content"
    description = "Create the Phase 0 remediation proposal without modifying checked-in campaign content."
    dependsOn(":level-tools:planPhase0Remediation")
}

tasks.register("promoteApprovedPhase0") {
    group = "magnetrail content"
    description = "Promote the explicitly owner-approved Phase 0 catalog."
    dependsOn(":level-tools:promoteApprovedPhase0")
}

tasks.register("finalizePhase0") {
    group = "verification"
    description = "Recompute final Phase 0 diagnostics and certification for the promoted catalog."
    dependsOn(":level-tools:finalizePhase0")
}

tasks.register("stagePhase1Expansion") {
    group = "magnetrail content"
    description = "Stage and analyze Phase 1 Levels 151–200 candidates without promotion."
    dependsOn(":level-tools:stagePhase1Expansion")
}

tasks.register("publishPhase1Proposal") {
    group = "documentation"
    description = "Publish the Phase 1 pre-promotion evidence and owner-review manifest."
    dependsOn(":level-tools:publishPhase1Proposal")
}

tasks.register("promoteApprovedPhase1") {
    group = "magnetrail content"
    description = "Promote the explicitly owner-approved Phase 1 catalog."
    dependsOn(":level-tools:promoteApprovedPhase1")
}

tasks.register("finalizePhase1") {
    group = "verification"
    description = "Recompute final Phase 1 diagnostics and certification for the promoted catalog."
    dependsOn(":level-tools:finalizePhase1")
}

tasks.register("analyzeCampaignDifficultyV4") {
    group = "verification"
    description = "Generate isolated, deterministic Difficulty V4 campaign diagnostics."
    dependsOn(":level-tools:analyzeCampaignDifficultyV4")
}

tasks.register("calibrateDifficultyV4") {
    group = "verification"
    description = "Compare entered human ratings with Difficulty V3 and provisional V4."
    dependsOn(":level-tools:calibrateDifficultyV4")
}

tasks.register("generateCampaignV5Candidates") {
    group = "magnetrail content"
    description = "Generate isolated D2 Generator V5 candidates and review evidence without promotion."
    dependsOn(":level-tools:generateCampaignV5Candidates")
}

tasks.register("analyzeCampaignGenerationV5") {
    group = "verification"
    description = "Validate D2 V5 generation and complete certification evidence."
    dependsOn(":level-tools:analyzeCampaignGenerationV5")
}

tasks.register("analyzeObjectRelevanceV5") {
    group = "verification"
    description = "Validate D2 counterfactual object-relevance diagnostics."
    dependsOn(":level-tools:analyzeObjectRelevanceV5")
}

tasks.register("analyzeInteractionGraphsV5") {
    group = "verification"
    description = "Validate D2 typed interaction graphs and fingerprints."
    dependsOn(":level-tools:analyzeInteractionGraphsV5")
}

tasks.register("testAdaptiveDifficultySelection") {
    group = "verification"
    description = "Run the deterministic offline DifficultySelectionV1 tests."
    dependsOn(":level-tools:testAdaptiveDifficultySelection")
}

tasks.register("generateD21SpatialDensityCandidates") {
    group = "magnetrail content"
    description = "Generate isolated D2.1 dense, meaningful staging candidates and diagnostics."
    dependsOn(":level-tools:generateD21SpatialDensityCandidates")
}

tasks.register("analyzeD21SpatialDensity") {
    group = "verification"
    description = "Validate D2.1 spatial-density staging evidence and campaign immutability."
    dependsOn(":level-tools:analyzeD21SpatialDensity")
}

tasks.register("benchmarkGeneratorV5Repair") {
    group = "magnetrail content"
    description = "Run the bounded solution-first Generator V5 repair benchmark in staging."
    dependsOn(":level-tools:benchmarkGeneratorV5Repair")
}

tasks.register("promoteD2Campaign") {
    group = "magnetrail content"
    description = "Promote the owner-directed D2 V5 catalog with guarded stable-ID migration."
    dependsOn(":level-tools:promoteD2Campaign")
}

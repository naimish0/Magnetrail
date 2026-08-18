package com.rameshta.magnetrail.core.model

enum class LevelOrigin {
    HANDCRAFTED,
    GENERATOR_ASSISTED,
}

enum class DifficultyBand {
    INTRO,
    DEVELOPING,
    ADVANCED,
}

data class GradingThresholds(
    val parActions: Int,
    val twoStarMaxActions: Int,
) {
    init {
        require(parActions > 0) { "parActions must be positive" }
        require(twoStarMaxActions >= parActions) {
            "twoStarMaxActions must be at least parActions"
        }
    }
}

/**
 * Immutable certification metadata shipped beside a level. It is descriptive;
 * [BoardState] and the production engine remain the gameplay authority.
 */
data class LevelMetadata(
    val contentVersion: Int,
    val origin: LevelOrigin,
    val generatorVersion: Int? = null,
    val generatorSeed: Long? = null,
    val generationProfile: String? = null,
    val difficultyBand: DifficultyBand,
    val certifiedSolutionLength: Int,
    val solutionCount: Int,
    val solutionCountCapped: Boolean,
    val validFirstActionCount: Int,
    val exploredStateCount: Int,
    val grading: GradingThresholds,
    val packId: String,
    val mechanicTags: List<String>,
    val contentFingerprint: String,
) {
    init {
        require(contentVersion > 0) { "contentVersion must be positive" }
        require(certifiedSolutionLength > 0) { "certifiedSolutionLength must be positive" }
        require(solutionCount > 0) { "solutionCount must be positive" }
        require(validFirstActionCount > 0) { "validFirstActionCount must be positive" }
        require(exploredStateCount > 0) { "exploredStateCount must be positive" }
        require(packId.isNotBlank()) { "packId must not be blank" }
        require(mechanicTags.isNotEmpty()) { "mechanicTags must not be empty" }
        require(contentFingerprint.startsWith("sha256:") && contentFingerprint.length == 71) {
            "contentFingerprint must be a sha256 fingerprint"
        }
        if (origin == LevelOrigin.GENERATOR_ASSISTED) {
            require(generatorVersion != null && generatorVersion > 0) {
                "Generator-assisted levels require generatorVersion"
            }
            require(generatorSeed != null) { "Generator-assisted levels require generatorSeed" }
            require(!generationProfile.isNullOrBlank()) {
                "Generator-assisted levels require generationProfile"
            }
        }
    }
}

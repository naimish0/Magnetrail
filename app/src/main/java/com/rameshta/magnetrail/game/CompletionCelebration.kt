package com.rameshta.magnetrail.game

internal enum class CelebrationIntensity {
    NONE,
    GOOD,
    EXCELLENT,
}

internal data class CompletionCelebrationStyle(
    val intensity: CelebrationIntensity,
    val emoji: String,
    val message: String,
    val confettiCount: Int,
    val variant: Int,
) {
    val celebratesStrongPlay: Boolean get() = intensity != CelebrationIntensity.NONE
}

internal fun completionCelebrationStyle(
    levelIdentity: String,
    stars: Int,
    actions: Int,
    overloads: Int,
    hintsUsed: Int,
): CompletionCelebrationStyle {
    val intensity = when {
        stars == 3 && overloads == 0 && hintsUsed == 0 -> CelebrationIntensity.EXCELLENT
        stars >= 2 -> CelebrationIntensity.GOOD
        else -> CelebrationIntensity.NONE
    }
    if (intensity == CelebrationIntensity.NONE) {
        return CompletionCelebrationStyle(intensity, "", "", 0, 0)
    }

    val excellent = listOf(
        "🧠✨" to "Brilliant sequence!",
        "🎯🔥" to "Perfect read!",
        "⚡🏆" to "Clean solve!",
        "🥳💫" to "Sharp thinking!",
        "🙌⭐" to "Beautifully played!",
    )
    val good = when {
        hintsUsed > 0 -> listOf(
            "💡✨" to "Great finish!",
            "🧲👏" to "Nicely worked out!",
            "🎉💡" to "You found the route!",
        )
        overloads > 0 -> listOf(
            "👏🧲" to "Nice recovery!",
            "🎉🎯" to "Strong comeback!",
            "🙌⭐" to "Well recovered!",
        )
        else -> listOf(
            "🎉⭐" to "Nice sequence!",
            "👏✨" to "Great thinking!",
            "🧲🎯" to "Field mastered!",
            "🙌💫" to "Smooth solve!",
        )
    }
    val choices = if (intensity == CelebrationIntensity.EXCELLENT) excellent else good
    val key = "$levelIdentity|$stars|$actions|$overloads|$hintsUsed"
    val variant = key.fold(17) { hash, char -> hash * 31 + char.code }
        .and(Int.MAX_VALUE) % choices.size
    val (emoji, message) = choices[variant]
    return CompletionCelebrationStyle(
        intensity = intensity,
        emoji = emoji,
        message = message,
        confettiCount = if (intensity == CelebrationIntensity.EXCELLENT) 28 else 16,
        variant = variant,
    )
}

package com.rameshta.magnetrail.game

data class MotionPolicy(
    val reduced: Boolean,
    val millisPerCell: Int,
    val maxRouteMillis: Int,
    val impactMillis: Long,
    val rewindMillis: Int,
    val polarityFlipMillis: Long,
    val completionDelayMillis: Long,
    val animateFieldPulse: Boolean,
    val showLongTrails: Boolean,
    val showCelebrationParticles: Boolean,
) {
    companion object {
        val Normal = MotionPolicy(
            reduced = false,
            millisPerCell = 90,
            maxRouteMillis = 720,
            impactMillis = 160,
            rewindMillis = 210,
            polarityFlipMillis = 260,
            completionDelayMillis = 520,
            animateFieldPulse = true,
            showLongTrails = true,
            showCelebrationParticles = true,
        )

        val Reduced = MotionPolicy(
            reduced = true,
            millisPerCell = 24,
            maxRouteMillis = 90,
            impactMillis = 60,
            rewindMillis = 70,
            polarityFlipMillis = 0,
            completionDelayMillis = 80,
            animateFieldPulse = false,
            showLongTrails = false,
            showCelebrationParticles = false,
        )

        fun from(reducedMotion: Boolean): MotionPolicy = if (reducedMotion) Reduced else Normal
    }
}

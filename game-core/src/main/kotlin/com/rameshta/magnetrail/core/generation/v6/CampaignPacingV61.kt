package com.rameshta.magnetrail.core.generation.v6

data class CampaignSlotV61(
    val levelNumber: Int,
    val band: AutomatedDifficultyBandV61,
    val causalFamilyIdentifier: String,
    val strategyClusterIdentifier: String,
)

data class CampaignPacingAuditV61(
    val passed: Boolean,
    val violations: List<String>,
    val perBandCounts: Map<String, Int>,
    val perFamilyCounts: Map<String, Int>,
    val perClusterCounts: Map<String, Int>,
)

object MixedDifficultyScheduleV61 {
    const val TUTORIAL_END_LEVEL = 12
    const val NUMBERED_CAMPAIGN_END_LEVEL = 2205

    private val repeating = listOf(
        AutomatedDifficultyBandV61.EASY,
        AutomatedDifficultyBandV61.MEDIUM,
        AutomatedDifficultyBandV61.HARD,
        AutomatedDifficultyBandV61.SUPER_HARD,
        AutomatedDifficultyBandV61.HARD,
        AutomatedDifficultyBandV61.MEDIUM,
        AutomatedDifficultyBandV61.EXPERT,
        AutomatedDifficultyBandV61.HARD,
        AutomatedDifficultyBandV61.HARD,
        AutomatedDifficultyBandV61.SUPER_HARD,
        AutomatedDifficultyBandV61.EASY,
        AutomatedDifficultyBandV61.MEDIUM,
        AutomatedDifficultyBandV61.HARD,
        AutomatedDifficultyBandV61.EXPERT,
        AutomatedDifficultyBandV61.SUPER_HARD,
        AutomatedDifficultyBandV61.MEDIUM,
        AutomatedDifficultyBandV61.HARD,
        AutomatedDifficultyBandV61.SUPER_HARD,
        AutomatedDifficultyBandV61.SUPER_HARD,
        AutomatedDifficultyBandV61.EXPERT,
    )

    fun bandForLevel(levelNumber: Int): AutomatedDifficultyBandV61 {
        require(levelNumber > TUTORIAL_END_LEVEL)
        if (levelNumber <= 20) {
            return if (levelNumber % 3 == 0) AutomatedDifficultyBandV61.MEDIUM else AutomatedDifficultyBandV61.EASY
        }
        if (levelNumber <= 32) {
            return listOf(
                AutomatedDifficultyBandV61.MEDIUM,
                AutomatedDifficultyBandV61.HARD,
                AutomatedDifficultyBandV61.EASY,
                AutomatedDifficultyBandV61.MEDIUM,
                AutomatedDifficultyBandV61.HARD,
                AutomatedDifficultyBandV61.MEDIUM,
            )[(levelNumber - 21) % 6]
        }
        if (levelNumber <= 44) {
            return listOf(
                AutomatedDifficultyBandV61.HARD,
                AutomatedDifficultyBandV61.MEDIUM,
                AutomatedDifficultyBandV61.SUPER_HARD,
                AutomatedDifficultyBandV61.HARD,
            )[(levelNumber - 33) % 4]
        }
        if (levelNumber <= 54) {
            return listOf(
                AutomatedDifficultyBandV61.HARD,
                AutomatedDifficultyBandV61.SUPER_HARD,
                AutomatedDifficultyBandV61.EXPERT,
                AutomatedDifficultyBandV61.MEDIUM,
                AutomatedDifficultyBandV61.HARD,
            )[(levelNumber - 45) % 5]
        }
        if (levelNumber <= 60) {
            return listOf(
                AutomatedDifficultyBandV61.SUPER_HARD,
                AutomatedDifficultyBandV61.EXPERT,
                AutomatedDifficultyBandV61.HARD,
                AutomatedDifficultyBandV61.SUPER_HARD,
                AutomatedDifficultyBandV61.MEDIUM,
                AutomatedDifficultyBandV61.SUPER_HARD,
            )[levelNumber - 55]
        }
        return repeating[Math.floorMod(levelNumber - 61, repeating.size)]
    }

    fun bandForAutoJourneyOrdinal(ordinal: Int): AutomatedDifficultyBandV61 {
        require(ordinal > 0)
        return repeating[Math.floorMod(ordinal - 1, repeating.size)]
    }

    fun expectedTwentyLevelCounts(): Map<AutomatedDifficultyBandV61, Int> =
        AUTOMATED_CAMPAIGN_BANDS_V61.associateWith { band -> repeating.count { it == band } }
}

object CampaignFamilyAllocatorV61 {
    const val FAMILY_COUNT = 24

    /** Stable greedy balancing by tier, subject to the eight-position exclusion window. */
    fun allocate(bands: List<AutomatedDifficultyBandV61>): List<Int> {
        val recent = ArrayDeque<Int>()
        val perBand = AutomatedDifficultyBandV61.entries.associateWith { IntArray(FAMILY_COUNT) }
        val total = IntArray(FAMILY_COUNT)
        return bands.mapIndexed { ordinal, band ->
            val preferred = Math.floorMod(ordinal * 7 + band.rank * 5, FAMILY_COUNT)
            val candidates = (0 until FAMILY_COUNT).filterNot(recent::contains)
            val selected = candidates.minWith(
                compareBy<Int> { perBand.getValue(band)[it] }
                    .thenBy { total[it] }
                    .thenBy { Math.floorMod(it - preferred, FAMILY_COUNT) }
                    .thenBy { it },
            )
            perBand.getValue(band)[selected] += 1
            total[selected] += 1
            recent.addLast(selected)
            if (recent.size > 8) recent.removeFirst()
            selected
        }
    }
}

object CampaignPacingValidatorV61 {
    fun validate(slots: List<CampaignSlotV61>): CampaignPacingAuditV61 {
        val sorted = slots.sortedBy { it.levelNumber }
        val violations = linkedSetOf<String>()
        sorted.zipWithNext().forEach { (left, right) ->
            if (right.levelNumber != left.levelNumber + 1) violations += "non-contiguous:${left.levelNumber}:${right.levelNumber}"
            if (left.band == right.band && sorted.getOrNull(sorted.indexOf(left) - 1)?.band == left.band) {
                violations += "three-same-band:${left.levelNumber - 1}-${right.levelNumber}"
            }
            if (left.band == AutomatedDifficultyBandV61.MASTER && right.band == AutomatedDifficultyBandV61.MASTER) {
                violations += "consecutive-master:${left.levelNumber}"
            }
            if (left.band == AutomatedDifficultyBandV61.MASTER && right.band.rank >= AutomatedDifficultyBandV61.EXPERT.rank) {
                violations += "missing-post-master-recovery:${left.levelNumber}"
            }
        }
        sorted.windowed(20).filter { it.first().levelNumber >= 61 }.forEach { window ->
            val actual = window.groupingBy { it.band }.eachCount()
            MixedDifficultyScheduleV61.expectedTwentyLevelCounts().forEach { (band, expected) ->
                if (kotlin.math.abs((actual[band] ?: 0) - expected) > 1) {
                    violations += "twenty-window-band:${window.first().levelNumber}:${band.name}"
                }
            }
        }
        sorted.windowed(5).filter { it.first().levelNumber >= 61 }.forEach { window ->
            if (window.none { it.band.rank >= AutomatedDifficultyBandV61.SUPER_HARD.rank }) {
                violations += "five-window-no-high:${window.first().levelNumber}"
            }
        }
        sorted.forEachIndexed { index, slot ->
            sorted.subList(maxOf(0, index - 7), index).forEach { previous ->
                if (previous.causalFamilyIdentifier == slot.causalFamilyIdentifier) {
                    violations += "family-spacing:${previous.levelNumber}:${slot.levelNumber}"
                }
            }
            sorted.subList(maxOf(0, index - 19), index).forEach { previous ->
                if (previous.strategyClusterIdentifier == slot.strategyClusterIdentifier) {
                    violations += "cluster-spacing:${previous.levelNumber}:${slot.levelNumber}"
                }
            }
        }
        sorted.groupBy { it.band }.forEach { (band, rows) ->
            rows.groupingBy { it.causalFamilyIdentifier }.eachCount().forEach { (family, count) ->
                if (count.toDouble() / rows.size > 0.05) violations += "family-tier-cap:${band.name}:$family:$count"
            }
            rows.groupingBy { it.strategyClusterIdentifier }.eachCount().forEach { (cluster, count) ->
                if (count.toDouble() / rows.size > 0.02) violations += "cluster-tier-cap:${band.name}:$cluster:$count"
            }
        }
        return CampaignPacingAuditV61(
            passed = violations.isEmpty(),
            violations = violations.toList(),
            perBandCounts = sorted.groupingBy { it.band.displayName }.eachCount().toSortedMap(),
            perFamilyCounts = sorted.groupingBy { it.causalFamilyIdentifier }.eachCount().toSortedMap(),
            perClusterCounts = sorted.groupingBy { it.strategyClusterIdentifier }.eachCount().toSortedMap(),
        )
    }
}

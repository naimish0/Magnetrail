package com.rameshta.magnetrail.core.generation

/** SplitMix64 with a frozen implementation; all generator randomness enters through this seed. */
class SeededRandom(seed: Long) {
    private var state: Long = seed

    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        var value = state
        value = (value xor (value ushr 30)) * MIX_1
        value = (value xor (value ushr 27)) * MIX_2
        return value xor (value ushr 31)
    }

    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        return (nextLong().ushr(1) % bound.toLong()).toInt()
    }

    fun nextBoolean(): Boolean = nextLong() and 1L == 0L

    companion object {
        private const val GOLDEN_GAMMA = -7046029254386353131L
        private const val MIX_1 = -4658895280553007687L
        private const val MIX_2 = -7723592293110705685L
    }
}

package com.rameshta.magnetrail.core.daily

import java.time.LocalDate

data class StreakState(
    val current: Int = 0,
    val best: Int = 0,
    val lastTrustedCompletionDate: LocalDate? = null,
)

data class StreakUpdate(
    val state: StreakState,
    val increased: Boolean,
)

object StreakPolicy {
    fun complete(date: LocalDate, previous: StreakState): StreakUpdate {
        require(previous.current >= 0 && previous.best >= previous.current) { "Invalid streak state" }
        val last = previous.lastTrustedCompletionDate
        if (last != null && !date.isAfter(last)) {
            return StreakUpdate(previous, increased = false)
        }
        val nextCurrent = if (last != null && date == last.plusDays(1)) previous.current + 1 else 1
        return StreakUpdate(
            state = StreakState(
                current = nextCurrent,
                best = maxOf(previous.best, nextCurrent),
                lastTrustedCompletionDate = date,
            ),
            increased = true,
        )
    }
}

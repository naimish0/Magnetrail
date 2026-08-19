package com.rameshta.magnetrail.ads

import android.os.SystemClock
import java.time.LocalDate

interface AdClock {
    fun wallTimeMillis(): Long
    fun elapsedRealtimeMillis(): Long
    fun localDate(): LocalDate
}

object SystemAdClock : AdClock {
    override fun wallTimeMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
    override fun localDate(): LocalDate = LocalDate.now()
}

class ForegroundAdClock : AdClock {
    private var accumulatedMillis = 0L
    private var foregroundStartedAt: Long? = null

    @Synchronized
    fun setForeground(foreground: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (foreground && foregroundStartedAt == null) {
            foregroundStartedAt = now
        } else if (!foreground) {
            foregroundStartedAt?.let { accumulatedMillis += (now - it).coerceAtLeast(0L) }
            foregroundStartedAt = null
        }
    }

    override fun wallTimeMillis(): Long = System.currentTimeMillis()

    @Synchronized
    override fun elapsedRealtimeMillis(): Long = accumulatedMillis +
        (foregroundStartedAt?.let { (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L) } ?: 0L)

    override fun localDate(): LocalDate = LocalDate.now()
}

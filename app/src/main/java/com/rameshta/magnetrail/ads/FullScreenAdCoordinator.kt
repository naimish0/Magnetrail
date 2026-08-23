package com.rameshta.magnetrail.ads

import java.util.concurrent.atomic.AtomicReference

enum class FullScreenOwner { CONSENT, REWARDED, INTERSTITIAL, APP_OPEN }

class FullScreenAdCoordinator(private val clock: AdClock) {
    private val owner = AtomicReference<FullScreenOwner?>(null)

    @Volatile var lastDismissedElapsedMillis: Long? = null
        private set
    @Volatile var lastRewardedElapsedMillis: Long? = null
        private set

    fun tryAcquire(candidate: FullScreenOwner): Boolean = owner.compareAndSet(null, candidate)

    fun release(candidate: FullScreenOwner, completed: Boolean) {
        if (!owner.compareAndSet(candidate, null) || !completed) return
        val now = clock.elapsedRealtimeMillis()
        lastDismissedElapsedMillis = now
        if (candidate == FullScreenOwner.REWARDED) lastRewardedElapsedMillis = now
    }

    fun isIdle(): Boolean = owner.get() == null
    fun currentOwner(): FullScreenOwner? = owner.get()
}

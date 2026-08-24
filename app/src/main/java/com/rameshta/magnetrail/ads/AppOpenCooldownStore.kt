package com.rameshta.magnetrail.ads

import android.content.Context
import androidx.core.content.edit

interface AppOpenCooldownStore {
    fun lastShownWallTimeMillis(): Long?
    fun recordShown(wallTimeMillis: Long)
}

class SharedPreferencesAppOpenCooldownStore(context: Context) : AppOpenCooldownStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun lastShownWallTimeMillis(): Long? = if (preferences.contains(KEY_LAST_SHOWN)) {
        preferences.getLong(KEY_LAST_SHOWN, 0L).takeIf { it >= 0L }
    } else {
        null
    }

    override fun recordShown(wallTimeMillis: Long) {
        require(wallTimeMillis >= 0L)
        val existing = lastShownWallTimeMillis() ?: 0L
        preferences.edit { putLong(KEY_LAST_SHOWN, maxOf(existing, wallTimeMillis)) }
    }

    private companion object {
        const val PREFERENCES_NAME = "magnetrail_app_open_ads"
        const val KEY_LAST_SHOWN = "last_shown_wall_time_millis"
    }
}

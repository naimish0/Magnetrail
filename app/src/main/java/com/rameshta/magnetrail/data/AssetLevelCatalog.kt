package com.rameshta.magnetrail.data

import android.content.Context
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser

class AssetLevelCatalog(
    context: Context,
    private val parser: LevelParser = LevelParser(),
) {
    private val assets = context.applicationContext.assets

    fun load(): LevelCatalog = load(CAMPAIGN_ASSET_PATH)

    fun loadDailyFallbacks(): LevelCatalog = load(DAILY_FALLBACK_ASSET_PATH)

    private fun load(path: String): LevelCatalog = try {
        val source = assets.open(path).bufferedReader().use { it.readText() }
        parser.parseCatalog(source)
    } catch (error: Exception) {
        throw IllegalStateException(
            "Unable to load or validate canonical level asset '$path'",
            error,
        )
    }

    companion object {
        const val CAMPAIGN_ASSET_PATH = "levels/magnetrail_campaign_levels_v3.json"
        const val DAILY_FALLBACK_ASSET_PATH = "levels/magnetrail_daily_fallbacks_v1.json"
    }
}

package com.rameshta.magnetrail.data

import android.content.Context
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser

class AssetLevelCatalog(
    context: Context,
    private val parser: LevelParser = LevelParser(),
) {
    private val assets = context.applicationContext.assets
    private val loadedCatalogs = mutableMapOf<String, LevelCatalog>()

    fun load(): LevelCatalog = load(CAMPAIGN_ASSET_PATH)

    fun loadDailyFallbacks(): LevelCatalog = load(DAILY_FALLBACK_ASSET_PATH)

    fun loadInfiniteCatalog(): LevelCatalog = load(INFINITE_ASSET_PATH)

    fun loadHumanPlaytestCatalog(selection: String): LevelCatalog = when (selection) {
        "v10" -> load(CAMPAIGN_ASSET_PATH).also { catalog ->
            require(
                catalog.generatorVersion == 5 &&
                    catalog.contentVersion == 10 &&
                    catalog.levels.size == V10_CAMPAIGN_SIZE,
            ) { "The V10 human-playtest source is not the expected promoted 2,205-board campaign." }
        }
        "v6" -> load(V6_HUMAN_PLAYTEST_ASSET_PATH).also { catalog ->
            require(
                catalog.generatorVersion == 6 &&
                    catalog.contentVersion == 12 &&
                    catalog.levels.size == V6_PILOT_SIZE,
            ) { "The V6 human-playtest catalog is not the expected sealed 30-board study." }
        }
        else -> error("Unsupported human-playtest catalog '$selection'")
    }

    @Synchronized
    private fun load(path: String): LevelCatalog = loadedCatalogs.getOrPut(path) {
        try {
            assets.open(path).buffered().use(parser::parseCatalog)
        } catch (error: Exception) {
            throw IllegalStateException(
                "Unable to load or validate canonical level asset '$path'",
                error,
            )
        }
    }

    companion object {
        const val CAMPAIGN_ASSET_PATH = "levels/magnetrail_campaign_levels_v3.json"
        const val DAILY_FALLBACK_ASSET_PATH = "levels/magnetrail_daily_fallbacks_v1.json"
        const val INFINITE_ASSET_PATH = "levels/magnetrail_infinite_catalog_v1.json"
        const val V6_HUMAN_PLAYTEST_ASSET_PATH = "levels/magnetrail_v6_calibration.json"
        const val V10_CAMPAIGN_SIZE = 2_205
        const val V6_PILOT_SIZE = 30
    }
}

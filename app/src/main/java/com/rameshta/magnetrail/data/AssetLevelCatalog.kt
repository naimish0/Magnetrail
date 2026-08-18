package com.rameshta.magnetrail.data

import android.content.Context
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser

class AssetLevelCatalog(
    context: Context,
    private val parser: LevelParser = LevelParser(),
) {
    private val assets = context.applicationContext.assets

    fun load(): LevelCatalog = try {
        val source = assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        parser.parseCatalog(source)
    } catch (error: Exception) {
        throw IllegalStateException(
            "Unable to load or validate canonical level asset '$ASSET_PATH'",
            error,
        )
    }

    companion object {
        const val ASSET_PATH = "levels/magnetrail_prototype_levels_v1.json"
    }
}

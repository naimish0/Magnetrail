package com.rameshta.magnetrail

import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser

fun prototypeCatalog(): LevelCatalog {
    val resource = checkNotNull(object {}.javaClass.getResource("/Magnetrail_Prototype_Levels_v1.json"))
    return LevelParser().parseCatalog(resource.readText())
}

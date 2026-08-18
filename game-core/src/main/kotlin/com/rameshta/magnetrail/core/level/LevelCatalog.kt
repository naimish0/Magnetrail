package com.rameshta.magnetrail.core.level

import com.rameshta.magnetrail.core.model.LevelDefinition

data class LevelCatalog(
    val schemaVersion: Int,
    val ruleVersion: String,
    val catalogId: String,
    val levels: List<LevelDefinition>,
) {
    fun level(id: String): LevelDefinition = requireNotNull(levels.firstOrNull { it.id == id }) {
        "Level '$id' does not exist in catalog '$catalogId'"
    }
}

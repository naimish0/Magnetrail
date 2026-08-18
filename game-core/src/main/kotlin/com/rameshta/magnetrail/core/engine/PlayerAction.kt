package com.rameshta.magnetrail.core.engine

data class PlayerAction(
    val arrowId: String,
) {
    init {
        require(arrowId.isNotBlank()) { "arrowId must not be blank" }
    }
}

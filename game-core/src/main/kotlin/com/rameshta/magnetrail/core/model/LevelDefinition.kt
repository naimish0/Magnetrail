package com.rameshta.magnetrail.core.model

data class LevelDefinition(
    val id: String,
    val number: Int,
    val title: String,
    val width: Int,
    val height: Int,
    val arrows: List<Arrow>,
    val magnets: List<Magnet>,
    val walls: List<Wall>,
    val designedSolutions: List<List<String>>,
) {
    fun initialState(): BoardState = BoardState(
        levelId = id,
        width = width,
        height = height,
        arrows = arrows.toList(),
        magnets = magnets.toList(),
        walls = walls.toList(),
    )
}

package com.rameshta.magnetrail.core.model

data class BoardState(
    val levelId: String,
    val width: Int,
    val height: Int,
    val arrows: List<Arrow>,
    val magnets: List<Magnet>,
    val walls: List<Wall>,
) {
    init {
        require(levelId.isNotBlank()) { "Board levelId must not be blank" }
        require(width > 0 && height > 0) { "Board dimensions must be positive, but were ${width}x$height" }

        val entities = buildList {
            arrows.forEach { add("arrow '${it.id}'" to it.position) }
            magnets.forEach { add("magnet '${it.id}'" to it.position) }
            walls.forEachIndexed { index, wall -> add("wall ${index + 1}" to wall.position) }
        }
        entities.forEach { (label, position) ->
            require(contains(position)) { "$label at $position is outside the ${width}x$height board" }
        }

        val duplicatePosition = entities.groupBy { it.second }.entries.firstOrNull { it.value.size > 1 }
        require(duplicatePosition == null) {
            "Multiple entities occupy ${duplicatePosition?.key}: ${duplicatePosition?.value?.joinToString { it.first }}"
        }

        val identifiers = arrows.map { it.id } + magnets.map { it.id }
        require(identifiers.all { it.isNotBlank() }) { "Arrow and magnet IDs must not be blank" }
        val duplicateId = identifiers.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }
        require(duplicateId == null) { "Duplicate entity ID '${duplicateId?.key}'" }
    }

    fun contains(position: Position): Boolean =
        position.row in 1..height && position.column in 1..width

    fun arrow(id: String): Arrow? = arrows.firstOrNull { it.id == id }

    fun magnet(id: String): Magnet? = magnets.firstOrNull { it.id == id }
}

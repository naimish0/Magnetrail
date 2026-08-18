package com.rameshta.magnetrail.core.level

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class LevelParsingException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

class LevelParser(
    private val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    },
) {
    fun parseCatalog(source: String): LevelCatalog {
        val dto = try {
            json.decodeFromString<LevelCatalogDto>(source)
        } catch (error: SerializationException) {
            throw LevelParsingException("Level catalog is not valid JSON: ${error.message}", error)
        }
        return LevelValidation.toDomain(dto)
    }
}

@Serializable
internal data class LevelCatalogDto(
    val schemaVersion: Int,
    val ruleVersion: String,
    val catalogId: String,
    val levels: List<LevelDto>,
)

@Serializable
internal data class LevelDto(
    val id: String,
    val number: Int,
    val title: String,
    val width: Int,
    val height: Int,
    val arrows: List<ArrowDto>,
    val magnets: List<MagnetDto>,
    val walls: List<WallDto>,
    val designedSolutions: List<List<String>>,
)

@Serializable
internal data class ArrowDto(
    val id: String,
    val row: Int,
    val column: Int,
    val printedDirection: String,
)

@Serializable
internal data class MagnetDto(
    val id: String,
    val row: Int,
    val column: Int,
    val initialPolarity: String,
)

@Serializable
internal data class WallDto(
    val row: Int,
    val column: Int,
)

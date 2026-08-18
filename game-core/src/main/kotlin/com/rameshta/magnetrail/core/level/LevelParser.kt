package com.rameshta.magnetrail.core.level

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import com.rameshta.magnetrail.core.model.DifficultyBand
import com.rameshta.magnetrail.core.model.GradingThresholds
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.LevelMetadata
import com.rameshta.magnetrail.core.model.LevelOrigin

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

    fun encodeCatalog(catalog: LevelCatalog): String = Json(json) { prettyPrint = true }
        .encodeToString(LevelCatalogDto.serializer(), catalog.toDto())
}

@Serializable
internal data class LevelCatalogDto(
    val schemaVersion: Int,
    val ruleVersion: String,
    val catalogId: String,
    val levels: List<LevelDto>,
    val contentVersion: Int? = null,
    val generatorVersion: Int? = null,
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
    val metadata: LevelMetadataDto? = null,
)

@Serializable
internal data class LevelMetadataDto(
    val contentVersion: Int,
    val origin: String,
    val generatorVersion: Int? = null,
    val generatorSeed: Long? = null,
    val generationProfile: String? = null,
    val difficultyBand: String,
    val certifiedSolutionLength: Int,
    val solutionCount: Int,
    val solutionCountCapped: Boolean,
    val validFirstActionCount: Int,
    val exploredStateCount: Int,
    val parActions: Int,
    val twoStarMaxActions: Int,
    val packId: String,
    val mechanicTags: List<String>,
    val contentFingerprint: String,
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

private fun LevelCatalog.toDto(): LevelCatalogDto = LevelCatalogDto(
    schemaVersion = schemaVersion,
    ruleVersion = ruleVersion,
    catalogId = catalogId,
    levels = levels.map(LevelDefinition::toDto),
    contentVersion = contentVersion,
    generatorVersion = generatorVersion,
)

private fun LevelDefinition.toDto(): LevelDto = LevelDto(
    id = id,
    number = number,
    title = title,
    width = width,
    height = height,
    arrows = arrows.map { ArrowDto(it.id, it.position.row, it.position.column, it.printedDirection.code) },
    magnets = magnets.map { MagnetDto(it.id, it.position.row, it.position.column, it.polarity.name) },
    walls = walls.map { WallDto(it.position.row, it.position.column) },
    designedSolutions = designedSolutions,
    metadata = metadata?.toDto(),
)

private fun LevelMetadata.toDto(): LevelMetadataDto = LevelMetadataDto(
    contentVersion = contentVersion,
    origin = origin.name,
    generatorVersion = generatorVersion,
    generatorSeed = generatorSeed,
    generationProfile = generationProfile,
    difficultyBand = difficultyBand.name,
    certifiedSolutionLength = certifiedSolutionLength,
    solutionCount = solutionCount,
    solutionCountCapped = solutionCountCapped,
    validFirstActionCount = validFirstActionCount,
    exploredStateCount = exploredStateCount,
    parActions = grading.parActions,
    twoStarMaxActions = grading.twoStarMaxActions,
    packId = packId,
    mechanicTags = mechanicTags,
    contentFingerprint = contentFingerprint,
)

internal fun LevelMetadataDto.toDomain(): LevelMetadata = LevelMetadata(
    contentVersion = contentVersion,
    origin = LevelOrigin.valueOf(origin),
    generatorVersion = generatorVersion,
    generatorSeed = generatorSeed,
    generationProfile = generationProfile,
    difficultyBand = DifficultyBand.valueOf(difficultyBand),
    certifiedSolutionLength = certifiedSolutionLength,
    solutionCount = solutionCount,
    solutionCountCapped = solutionCountCapped,
    validFirstActionCount = validFirstActionCount,
    exploredStateCount = exploredStateCount,
    grading = GradingThresholds(parActions, twoStarMaxActions),
    packId = packId,
    mechanicTags = mechanicTags,
    contentFingerprint = contentFingerprint,
)

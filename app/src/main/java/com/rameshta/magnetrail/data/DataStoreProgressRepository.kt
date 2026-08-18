package com.rameshta.magnetrail.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rameshta.magnetrail.core.level.LevelCatalog
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.magnetrailDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "magnetrail_player_v1",
)

class DataStoreProgressRepository private constructor(
    private val dataStore: DataStore<Preferences>,
    private val catalog: LevelCatalog,
    private val defaultReducedMotion: Boolean,
) : ProgressRepository {
    constructor(
        context: Context,
        catalog: LevelCatalog,
        defaultReducedMotion: Boolean,
    ) : this(
        dataStore = context.applicationContext.magnetrailDataStore,
        catalog = catalog,
        defaultReducedMotion = defaultReducedMotion,
    )

    internal constructor(
        dataStore: DataStore<Preferences>,
        catalog: LevelCatalog,
        defaultReducedMotion: Boolean,
        testMarker: Unit = Unit,
    ) : this(dataStore, catalog, defaultReducedMotion)

    override val preferences: Flow<PlayerPreferences> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map(::decode)
        .distinctUntilChanged()

    override suspend fun updateSetting(key: SettingKey, enabled: Boolean) {
        dataStore.edit { stored ->
            ensureCurrentSchema(stored)
            stored[key.preferenceKey] = enabled
        }
    }

    override suspend fun selectLevel(levelId: String) {
        val selectedIndex = catalog.levels.indexOfFirst { it.id == levelId }
        if (selectedIndex < 0) return
        dataStore.edit { stored ->
            ensureCurrentSchema(stored)
            val highestUnlocked = (stored[Keys.highestUnlockedLevel] ?: 1)
                .coerceIn(1, catalog.levels.size)
            if (selectedIndex < highestUnlocked) {
                stored[Keys.lastSelectedLevelId] = levelId
            }
        }
    }

    override suspend fun recordCompletion(levelId: String, moves: Int) {
        val completedIndex = catalog.levels.indexOfFirst { it.id == levelId }
        if (completedIndex < 0) return
        dataStore.edit { stored ->
            ensureCurrentSchema(stored)
            val currentHighest = (stored[Keys.highestUnlockedLevel] ?: 1)
                .coerceIn(1, catalog.levels.size)
            if (completedIndex >= currentHighest) return@edit
            val completed = stored[Keys.completedLevelIds].orEmpty()
                .filterTo(mutableSetOf()) { id -> catalog.levels.any { it.id == id } }
            completed += levelId
            stored[Keys.completedLevelIds] = completed
            stored[Keys.highestUnlockedLevel] = maxOf(
                currentHighest,
                (completedIndex + 2).coerceAtMost(catalog.levels.size),
            )
            if (moves > 0) {
                val bestMoves = decodeBestMoves(stored[Keys.bestMoves]).toMutableMap()
                bestMoves[levelId] = minOf(bestMoves[levelId] ?: Int.MAX_VALUE, moves)
                stored[Keys.bestMoves] = encodeBestMoves(bestMoves)
            }
        }
    }

    private fun decode(stored: Preferences): PlayerPreferences {
        val default = defaultPreferences()
        val schemaVersion = stored[Keys.schemaVersion] ?: PLAYER_PREFERENCES_SCHEMA_VERSION
        if (schemaVersion != PLAYER_PREFERENCES_SCHEMA_VERSION) return default

        val validIds = catalog.levels.mapTo(linkedSetOf()) { it.id }
        val completedIds = stored[Keys.completedLevelIds].orEmpty().filterTo(linkedSetOf()) {
            it in validIds
        }
        val completedUnlock = completedIds.maxOfOrNull { id ->
            val index = catalog.levels.indexOfFirst { it.id == id }
            (index + 2).coerceAtMost(catalog.levels.size)
        } ?: 1
        val highestUnlocked = maxOf(
            (stored[Keys.highestUnlockedLevel] ?: 1).coerceIn(1, catalog.levels.size),
            completedUnlock,
        )
        val requestedLevelId = stored[Keys.lastSelectedLevelId]
        val requestedIndex = catalog.levels.indexOfFirst { it.id == requestedLevelId }
        val lastSelected = if (requestedIndex in 0 until highestUnlocked) {
            catalog.levels[requestedIndex].id
        } else {
            catalog.levels[highestUnlocked - 1].id
        }
        val bestMoves = decodeBestMoves(stored[Keys.bestMoves])
            .filter { (id, moves) -> id in validIds && moves > 0 }

        return PlayerPreferences(
            settings = PlayerSettings(
                soundEnabled = stored[Keys.soundEnabled] ?: true,
                hapticsEnabled = stored[Keys.hapticsEnabled] ?: true,
                reducedMotion = stored[Keys.reducedMotion] ?: defaultReducedMotion,
                highContrastFields = stored[Keys.highContrastFields] ?: false,
                pathPreviewAssistance = stored[Keys.pathPreviewAssistance] ?: false,
            ),
            progress = PlayerProgress(
                highestUnlockedLevel = highestUnlocked,
                completedLevelIds = completedIds,
                lastSelectedLevelId = lastSelected,
                bestMovesByLevel = bestMoves,
            ),
        )
    }

    private fun defaultPreferences(): PlayerPreferences = PlayerPreferences(
        settings = PlayerSettings(reducedMotion = defaultReducedMotion),
        progress = PlayerProgress(lastSelectedLevelId = catalog.levels.first().id),
    )

    private fun ensureCurrentSchema(stored: androidx.datastore.preferences.core.MutablePreferences) {
        if (stored[Keys.schemaVersion] != null &&
            stored[Keys.schemaVersion] != PLAYER_PREFERENCES_SCHEMA_VERSION
        ) {
            stored.clear()
        }
        stored[Keys.schemaVersion] = PLAYER_PREFERENCES_SCHEMA_VERSION
    }

    private object Keys {
        val schemaVersion = intPreferencesKey("schema_version")
        val soundEnabled = booleanPreferencesKey("sound_enabled")
        val hapticsEnabled = booleanPreferencesKey("haptics_enabled")
        val reducedMotion = booleanPreferencesKey("reduced_motion")
        val highContrastFields = booleanPreferencesKey("high_contrast_fields")
        val pathPreviewAssistance = booleanPreferencesKey("path_preview_assistance")
        val highestUnlockedLevel = intPreferencesKey("highest_unlocked_level")
        val completedLevelIds = stringSetPreferencesKey("completed_level_ids")
        val lastSelectedLevelId = stringPreferencesKey("last_selected_level_id")
        val bestMoves = stringSetPreferencesKey("best_moves")
    }

    private val SettingKey.preferenceKey: Preferences.Key<Boolean>
        get() = when (this) {
            SettingKey.SOUND -> Keys.soundEnabled
            SettingKey.HAPTICS -> Keys.hapticsEnabled
            SettingKey.REDUCED_MOTION -> Keys.reducedMotion
            SettingKey.HIGH_CONTRAST_FIELDS -> Keys.highContrastFields
            SettingKey.PATH_PREVIEW_ASSISTANCE -> Keys.pathPreviewAssistance
        }

    private fun decodeBestMoves(values: Set<String>?): Map<String, Int> = values.orEmpty()
        .mapNotNull { encoded ->
            val separator = encoded.lastIndexOf(':')
            if (separator <= 0) return@mapNotNull null
            val moves = encoded.substring(separator + 1).toIntOrNull() ?: return@mapNotNull null
            encoded.substring(0, separator) to moves
        }
        .toMap()

    private fun encodeBestMoves(values: Map<String, Int>): Set<String> = values
        .mapTo(linkedSetOf()) { (id, moves) -> "$id:$moves" }
}

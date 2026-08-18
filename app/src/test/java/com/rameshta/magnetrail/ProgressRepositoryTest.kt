package com.rameshta.magnetrail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.rameshta.magnetrail.data.DataStoreProgressRepository
import com.rameshta.magnetrail.data.PLAYER_PREFERENCES_SCHEMA_VERSION
import com.rameshta.magnetrail.data.SettingKey
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `settings defaults honor system motion and round trip`() = runTest {
        val repository = repository(dataStore(this), defaultReducedMotion = true)

        val defaults = repository.preferences.first()
        assertTrue(defaults.settings.soundEnabled)
        assertTrue(defaults.settings.hapticsEnabled)
        assertTrue(defaults.settings.reducedMotion)
        assertFalse(defaults.settings.highContrastFields)
        assertFalse(defaults.settings.pathPreviewAssistance)

        repository.updateSetting(SettingKey.SOUND, false)
        repository.updateSetting(SettingKey.HAPTICS, false)
        repository.updateSetting(SettingKey.REDUCED_MOTION, false)
        repository.updateSetting(SettingKey.HIGH_CONTRAST_FIELDS, true)
        repository.updateSetting(SettingKey.PATH_PREVIEW_ASSISTANCE, true)

        val restored = repository.preferences.first()
        assertFalse(restored.settings.soundEnabled)
        assertFalse(restored.settings.hapticsEnabled)
        assertFalse(restored.settings.reducedMotion)
        assertTrue(restored.settings.highContrastFields)
        assertTrue(restored.settings.pathPreviewAssistance)
        assertEquals(PLAYER_PREFERENCES_SCHEMA_VERSION, restored.schemaVersion)
    }

    @Test
    fun `completion unlocks next level replay keeps best and level twelve stops at boundary`() = runTest {
        val repository = repository(dataStore(this))

        repository.recordCompletion("proto-001", moves = 1)
        repository.recordCompletion("proto-001", moves = 3)
        var progress = repository.preferences.first().progress
        assertEquals(2, progress.highestUnlockedLevel)
        assertEquals(setOf("proto-001"), progress.completedLevelIds)
        assertEquals(1, progress.bestMovesByLevel["proto-001"])

        repository.selectLevel("proto-002")
        progress = repository.preferences.first().progress
        assertEquals("proto-002", progress.lastSelectedLevelId)

        prototypeCatalog().levels.drop(1).forEach { level ->
            repository.recordCompletion(level.id, moves = level.arrows.size)
        }
        progress = repository.preferences.first().progress
        assertEquals(12, progress.highestUnlockedLevel)
        assertTrue("proto-012" in progress.completedLevelIds)
        assertEquals(12, prototypeCatalog().levels.size)
    }

    @Test
    fun `corrupt and future values are safely clamped or reset`() = runTest {
        val store = dataStore(this)
        store.edit { preferences ->
            preferences[intPreferencesKey("schema_version")] = PLAYER_PREFERENCES_SCHEMA_VERSION
            preferences[intPreferencesKey("highest_unlocked_level")] = 999
            preferences[stringSetPreferencesKey("completed_level_ids")] =
                setOf("proto-012", "missing")
            preferences[stringPreferencesKey("last_selected_level_id")] = "missing"
            preferences[stringSetPreferencesKey("best_moves")] =
                setOf("proto-001:-3", "proto-002:2", "broken")
        }
        var restored = repository(store).preferences.first()
        assertEquals(12, restored.progress.highestUnlockedLevel)
        assertEquals(setOf("proto-012"), restored.progress.completedLevelIds)
        assertEquals("proto-012", restored.progress.lastSelectedLevelId)
        assertEquals(mapOf("proto-002" to 2), restored.progress.bestMovesByLevel)

        store.edit { preferences ->
            preferences.clear()
            preferences[intPreferencesKey("schema_version")] = 999
            preferences[booleanPreferencesKey("sound_enabled")] = false
            preferences[intPreferencesKey("highest_unlocked_level")] = 12
        }
        restored = repository(store).preferences.first()
        assertEquals(PLAYER_PREFERENCES_SCHEMA_VERSION, restored.schemaVersion)
        assertTrue(restored.settings.soundEnabled)
        assertEquals(1, restored.progress.highestUnlockedLevel)
        assertEquals("proto-001", restored.progress.lastSelectedLevelId)
    }

    private fun dataStore(scope: TestScope): DataStore<Preferences> {
        val target = File(temporaryFolder.newFolder(), "player.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { target }
    }

    private fun repository(
        dataStore: DataStore<Preferences>,
        defaultReducedMotion: Boolean = false,
    ) = DataStoreProgressRepository(
        dataStore = dataStore,
        catalog = prototypeCatalog(),
        defaultReducedMotion = defaultReducedMotion,
        testMarker = Unit,
    )
}

package com.rameshta.magnetrail.playtest

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.humanPlaytestDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "magnetrail_human_playtest_v1",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

interface HumanPlaytestRepository {
    suspend fun load(): HumanPlaytestSession?
    suspend fun save(session: HumanPlaytestSession)
    suspend fun clear()
}

class DataStoreHumanPlaytestRepository private constructor(
    private val dataStore: DataStore<Preferences>,
) : HumanPlaytestRepository {
    constructor(context: Context) : this(context.applicationContext.humanPlaytestDataStore)

    internal constructor(dataStore: DataStore<Preferences>, testMarker: Unit = Unit) : this(dataStore)

    override suspend fun load(): HumanPlaytestSession? {
        val encoded = dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }
            .first()[Keys.session]
            ?: return null
        return runCatching { json.decodeFromString<HumanPlaytestSession>(encoded) }.getOrNull()
            ?.let(::migrate)
    }

    override suspend fun save(session: HumanPlaytestSession) {
        require(session.schemaVersion == HUMAN_PLAYTEST_SCHEMA_VERSION)
        dataStore.edit { stored -> stored[Keys.session] = json.encodeToString(session) }
    }

    override suspend fun clear() {
        dataStore.edit { stored -> stored.remove(Keys.session) }
    }

    private object Keys {
        val session = stringPreferencesKey("human_playtest_session_json")
    }

    companion object {
        private val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        private fun migrate(session: HumanPlaytestSession): HumanPlaytestSession = when {
            session.schemaVersion >= HUMAN_PLAYTEST_SCHEMA_VERSION -> session
            session.schemaVersion == 4 -> session.copy(
                schemaVersion = HUMAN_PLAYTEST_SCHEMA_VERSION,
                observations = session.observations.map { observation ->
                    observation.copy(
                        guessResponse = if (observation.guessRequired) {
                            HumanPlaytestGuessResponse.YES
                        } else {
                            HumanPlaytestGuessResponse.NO
                        },
                    )
                },
            )
            else -> session
        }
    }
}

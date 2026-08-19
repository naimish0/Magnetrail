package com.rameshta.magnetrail

import androidx.datastore.core.CorruptionException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rameshta.magnetrail.data.DataStoreProgressRepository
import com.rameshta.magnetrail.data.PLAYER_PREFERENCES_SCHEMA_VERSION
import com.rameshta.magnetrail.data.playerDataStoreCorruptionHandler
import com.rameshta.magnetrail.privacy.ExternalUrlPolicy
import com.rameshta.magnetrail.release.ProductionReleaseConfiguration
import com.rameshta.magnetrail.release.ProductionReleaseConfigurationValidator
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class M5ReleaseReadinessTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `Google sample IDs and incomplete production inputs are rejected`() {
        val problems = ProductionReleaseConfigurationValidator.problems(
            ProductionReleaseConfiguration(
                adMobAppId = "ca-app-pub-3940256099942544~3347511713",
                rewardedAdUnitId = "ca-app-pub-3940256099942544/5224354917",
                interstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712",
                privacyPolicyUrl = "http://example.test/privacy",
                targetAudience = "unspecified",
                liveAdsEnabled = false,
                firebaseConfigured = false,
                uploadSigningConfigured = false,
            ),
        )

        assertEquals(8, problems.size)
    }

    @Test
    fun `complete non-sample production-shaped inputs pass the pure validator`() {
        val problems = ProductionReleaseConfigurationValidator.problems(
            ProductionReleaseConfiguration(
                adMobAppId = "ca-app-pub-1234567890123456~1234567890",
                rewardedAdUnitId = "ca-app-pub-1234567890123456/1234567890",
                interstitialAdUnitId = "ca-app-pub-1234567890123456/0987654321",
                privacyPolicyUrl = "https://example.test/magnetrail/privacy",
                targetAudience = "general",
                liveAdsEnabled = true,
                firebaseConfigured = true,
                uploadSigningConfigured = true,
            ),
        )

        assertTrue(problems.isEmpty())
    }

    @Test
    fun `external policy URL accepts only safe HTTPS hosts`() {
        assertTrue(ExternalUrlPolicy.isSafeHttpsUrl("https://example.test/privacy"))
        assertFalse(ExternalUrlPolicy.isSafeHttpsUrl("http://example.test/privacy"))
        assertFalse(ExternalUrlPolicy.isSafeHttpsUrl("https://user@example.test/privacy"))
        assertFalse(ExternalUrlPolicy.isSafeHttpsUrl("javascript:alert(1)"))
        assertFalse(ExternalUrlPolicy.isSafeHttpsUrl(""))
    }

    @Test
    fun `M4 schema migrates to M5 without losing progress`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            File(temporaryFolder.newFolder(), "m4.preferences_pb")
        }
        store.edit { values ->
            values[intPreferencesKey("schema_version")] = 4
            values[intPreferencesKey("coin_balance")] = 321
            values[stringPreferencesKey("last_selected_level_id")] = "proto-001"
        }

        val migrated = DataStoreProgressRepository(
            dataStore = store,
            catalog = prototypeCatalog(),
            defaultReducedMotion = false,
            testMarker = Unit,
        ).preferences.first()

        assertEquals(PLAYER_PREFERENCES_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(321, migrated.progress.coinBalance)
        assertEquals("proto-001", migrated.progress.lastSelectedLevelId)
    }

    @Test
    fun `player DataStore corruption handler recovers to empty preferences`() = runTest {
        assertEquals(
            emptyPreferences(),
            playerDataStoreCorruptionHandler.handleCorruption(CorruptionException("test corruption")),
        )
    }

    @Test
    fun `release variant never embeds Google sample ad IDs`() {
        if (BuildConfig.DEBUG) return
        assertFalse("3940256099942544" in BuildConfig.ADMOB_APP_ID)
        assertFalse("3940256099942544" in BuildConfig.REWARDED_AD_UNIT_ID)
        assertFalse("3940256099942544" in BuildConfig.INTERSTITIAL_AD_UNIT_ID)
    }

    @Test
    fun `en-US store listing stays within current Play character limits`() {
        val listing = checkNotNull(javaClass.getResource("/release/store-listing/en-US.md"))
            .readText()
        fun field(heading: String): String = listing
            .substringAfter("\n## $heading\n\n", missingDelimiterValue = "")
            .substringBefore("\n\n## ")
            .trim()

        val appName = field("App name")
        val shortDescription = field("Short description")
        val fullDescription = field("Full description")

        assertEquals("Magnetrail", appName)
        assertTrue("App name is ${appName.length} characters", appName.length <= 30)
        assertTrue(
            "Short description is ${shortDescription.length} characters",
            shortDescription.length <= 80,
        )
        assertTrue(
            "Full description is ${fullDescription.length} characters",
            fullDescription.length <= 4_000,
        )
    }
}

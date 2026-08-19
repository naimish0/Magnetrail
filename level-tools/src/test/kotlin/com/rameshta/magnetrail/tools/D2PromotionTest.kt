package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.level.LevelParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class D2PromotionTest {
    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `promoted campaign has complete stable ID and fingerprint migration evidence`() {
        val source = LevelParser().parseCatalog(resource("/content/d2/promotion/D2_SOURCE_CONTENT_V6.json"))
        val staged = LevelParser().parseCatalog(resource("/content/d2/staging/D2_CAMPAIGN_V5_CANDIDATES.json"))
        val promoted = LevelParser().parseCatalog(resource("/Magnetrail_Campaign_Levels_v3.json"))
        val migration = json.decodeFromString<D2IdMigrationReport>(
            resource("/content/d2/promotion/D2_ID_MIGRATION.json"),
        )
        val result = json.decodeFromString<D2PromotionResult>(
            resource("/content/d2/promotion/D2_PROMOTION_RESULT.json"),
        )

        assertEquals(7, promoted.contentVersion)
        assertEquals(5, promoted.generatorVersion)
        assertEquals(source.levels.map { it.id }, promoted.levels.map { it.id })
        assertEquals(staged.levels.map(ContentFingerprint::exact), promoted.levels.map(ContentFingerprint::exact))
        assertEquals(200, migration.stableIdsPreserved)
        assertEquals(200, migration.boardFingerprintsChanged)
        assertEquals(200, migration.rows.size)
        assertTrue(migration.rows.all { it.stableProductionIdPreserved && it.previousFingerprintAttached })
        assertEquals("OWNER_DIRECTED_PROMOTED", result.status)
        assertEquals(200, result.recertifiedLevelCount)
        assertEquals(0, result.truncatedLevelCount)
        assertEquals(0, result.humanRatingsAvailable)
        assertEquals(0, result.automatedApprovalCount)
        assertTrue(result.migrationProvenSafe)
        assertFalse(result.productionGameplayChanged)
    }

    private fun resource(path: String): String = checkNotNull(javaClass.getResource(path)).readText()
}

package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeneratorV6PromotionTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `promotion reuses an interrupted rollback artifact and atomically replaces only the target`() {
        val source = File(requireNotNull(javaClass.getResource("/Magnetrail_Campaign_Levels_v3.json")).toURI())
        val campaign = temporaryFolder.newFile("campaign.json").apply { writeBytes(source.readBytes()) }
        val rollback = temporaryFolder.newFile("rollback.json").apply { writeBytes(source.readBytes()) }
        val parser = LevelParser()
        val oldCatalog = parser.parseCatalog(source.readText())
        val old = oldCatalog.levels.first()
        val changedRaw = old.copy(
            arrows = old.arrows.mapIndexed { index, arrow ->
                if (index == 0) arrow.copy(printedDirection = arrow.printedDirection.opposite()) else arrow
            },
            metadata = null,
        )
        val changed = changedRaw.copy(
            metadata = requireNotNull(old.metadata).copy(
                contentVersion = 12,
                generatorVersion = 6,
                generatorSeed = 6_000_001L,
                generationProfile = "v6-bucket-1",
                contentFingerprint = ContentFingerprint.of(changedRaw),
                previousContentFingerprint = requireNotNull(old.metadata).contentFingerprint,
            ),
        )
        val candidate = temporaryFolder.newFile("candidate.json").apply {
            writeText(parser.encodeCatalog(LevelCatalog(2, "magnetrail-core-1", "v6-test", listOf(changed), 12, 6)) + "\n")
        }
        val technical = statusFile("technical.json", "FULL_CAMPAIGN_STAGING_CERTIFIED")
        val human = statusFile("human.json", "HUMAN_MODEL_CERTIFIED")
        val sample = statusFile("sample.json", "CAMPAIGN_CERTIFIED")
        val manifest = statusFile("manifest.json", "CAMPAIGN_CERTIFIED")
        val sourceSha = sha(source)
        promoteGeneratorV6Campaign(
            mapOf(
                "confirmation" to "true",
                "campaign" to campaign.path,
                "candidate" to candidate.path,
                "technical-certificate" to technical.path,
                "human-certificate" to human.path,
                "sample-certificate" to sample.path,
                "manifest" to manifest.path,
                "rollback" to rollback.path,
                "expected-production-sha" to sourceSha,
                "expected-content-version" to "10",
                "expected-candidate-sha" to sha(candidate),
                "technical-certificate-sha" to sha(technical),
                "human-certificate-sha" to sha(human),
                "sample-certificate-sha" to sha(sample),
                "manifest-sha" to sha(manifest),
            ),
        )
        assertEquals(sha(candidate), sha(campaign))
        assertEquals(sourceSha, sha(rollback))
        assertTrue(parser.parseCatalog(campaign.readText()).generatorVersion == 6)
    }

    @Test
    fun `V61 rejected certificate cannot create rollback receipt or mutate production`() {
        val source = File(requireNotNull(javaClass.getResource("/Magnetrail_Campaign_Levels_v3.json")).toURI())
        val campaign = temporaryFolder.newFile("v61-production.json").apply { writeBytes(source.readBytes()) }
        val candidate = temporaryFolder.newFile("v61-candidate.json").apply { writeBytes(source.readBytes()) }
        val manifest = temporaryFolder.newFile("v61-manifest.json").apply { writeText("{}\n") }
        val certificate = temporaryFolder.newFile("v61-certificate.json").apply {
            writeText(
                """{"certificationType":"AUTOMATED_CAMPAIGN_REJECTED","campaignHash":"${sha(candidate)}","manifestHash":"${sha(manifest)}","boardCount":2205}""" + "\n",
            )
        }
        val rollback = File(temporaryFolder.root, "v61-rollback.json")
        val receipt = File(temporaryFolder.root, "v61-receipt.txt")
        val before = sha(campaign)

        assertThrows(IllegalArgumentException::class.java) {
            promoteGeneratorV61Campaign(
                mapOf(
                    "campaign" to campaign.path,
                    "candidate" to candidate.path,
                    "certificate" to certificate.path,
                    "manifest" to manifest.path,
                    "rollback" to rollback.path,
                    "receipt" to receipt.path,
                    "expected-production-sha" to before,
                    "expected-candidate-sha" to sha(candidate),
                    "certificate-sha" to sha(certificate),
                    "manifest-sha" to sha(manifest),
                ),
            )
        }

        assertEquals(before, sha(campaign))
        assertFalse(rollback.exists())
        assertFalse(receipt.exists())
    }

    private fun statusFile(name: String, status: String) = temporaryFolder.newFile(name).apply {
        writeText("{\"status\":\"$status\"}\n")
    }

    private fun sha(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

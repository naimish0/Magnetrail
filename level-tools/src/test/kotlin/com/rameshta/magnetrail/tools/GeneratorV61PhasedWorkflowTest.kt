package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.content.ContentFingerprint
import com.rameshta.magnetrail.core.generation.v6.AutomatedDifficultyBandV61
import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.level.LevelParser
import com.rameshta.magnetrail.core.model.LevelDefinition
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GeneratorV61PhasedWorkflowTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `phases partition the complete non-tutorial schedule with frozen counts`() {
        val phaseOne = scheduledCampaignSlotsV61(V61CampaignPhase.PHASE_ONE)
        val phaseTwo = scheduledCampaignSlotsV61(V61CampaignPhase.PHASE_TWO)

        assertEquals(V61_PHASE_ONE_BOARD_COUNT, phaseOne.size)
        assertEquals(V61_PHASE_TWO_BOARD_COUNT, phaseTwo.size)
        assertEquals(
            setOf(
                AutomatedDifficultyBandV61.EASY,
                AutomatedDifficultyBandV61.MEDIUM,
                AutomatedDifficultyBandV61.HARD,
            ),
            phaseOne.map { it.band }.toSet(),
        )
        assertEquals(
            setOf(AutomatedDifficultyBandV61.SUPER_HARD, AutomatedDifficultyBandV61.EXPERT),
            phaseTwo.map { it.band }.toSet(),
        )
        assertTrue(phaseOne.map { it.levelNumber }.toSet().intersect(phaseTwo.map { it.levelNumber }.toSet()).isEmpty())
        assertEquals((13..2205).toList(), (phaseOne + phaseTwo).sortedBy { it.levelNumber }.map { it.levelNumber })
    }

    @Test
    fun `phase family allocation is inherited from the unsplit schedule`() {
        val full = scheduledCampaignSlotsV61(V61CampaignPhase.FULL).associateBy { it.levelNumber }

        (scheduledCampaignSlotsV61(V61CampaignPhase.PHASE_ONE) +
            scheduledCampaignSlotsV61(V61CampaignPhase.PHASE_TWO)).forEach { phased ->
            assertEquals(full.getValue(phased.levelNumber).familyIndex, phased.familyIndex)
        }
    }

    @Test
    fun `final merge binds both phases and certifies all 2205 boards`() {
        val parser = LevelParser()
        val sourceFile = File(requireNotNull(javaClass.getResource("/Magnetrail_Campaign_Levels_v3.json")).toURI())
        val source = parser.parseCatalog(sourceFile.readText())
        val workingSource = temporaryFolder.newFile("source.json").apply { writeBytes(sourceFile.readBytes()) }
        val phaseOne = writePassingPhase(V61CampaignPhase.PHASE_ONE, source, parser, sha(workingSource))
        val phaseTwo = writePassingPhase(V61CampaignPhase.PHASE_TWO, source, parser, sha(workingSource))
        val regression = temporaryFolder.newFile("regression.json").apply {
            writeText("{\"status\":\"V61_HARD_NEGATIVE_REGRESSION_PASS\"}\n")
        }
        val preflight = temporaryFolder.newFile("preflight.json")
        writeGeneratorV61Preflight(
            mapOf(
                "campaign" to workingSource.path,
                "relevant-source-paths" to workingSource.path,
                "output" to preflight.path,
                "mandatory-tests-passed" to "true",
                "test-command-summary" to "synthetic integration fixture",
            ),
        )
        val output = temporaryFolder.newFolder("final")

        mergeAndCertifyGeneratorV61Campaign(
            mapOf(
                "campaign" to workingSource.path,
                "expected-production-sha" to sha(workingSource),
                "phase-1-catalog" to phaseOne.catalog.path,
                "phase-1-audit" to phaseOne.audit.path,
                "phase-1-manifest" to phaseOne.manifest.path,
                "phase-1-certificate" to phaseOne.certificate.path,
                "phase-2-catalog" to phaseTwo.catalog.path,
                "phase-2-audit" to phaseTwo.audit.path,
                "phase-2-manifest" to phaseTwo.manifest.path,
                "phase-2-certificate" to phaseTwo.certificate.path,
                "regression" to regression.path,
                "preflight" to preflight.path,
                "relevant-source-paths" to workingSource.path,
                "output" to output.path,
                "mandatory-tests-passed" to "true",
            ),
        )

        val certificate = Json.parseToJsonElement(
            File(output, "GENERATOR_V61_AUTOMATED_CERTIFICATE.json").readText(),
        ).jsonObject
        assertEquals(
            certificate.getValue("failures").toString(),
            "AUTOMATED_CAMPAIGN_CERTIFIED",
            certificate.getValue("certificationType").jsonPrimitive.content,
        )
        assertEquals("2205", certificate.getValue("certifiedBoardCount").jsonPrimitive.content)
        assertEquals("true", certificate.getValue("phaseOneCertified").jsonPrimitive.content)
        assertEquals("true", certificate.getValue("phaseTwoCertified").jsonPrimitive.content)
    }

    private fun writePassingPhase(
        phase: V61CampaignPhase,
        source: LevelCatalog,
        parser: LevelParser,
        sourceSha: String,
    ): TestPhaseFiles {
        val directory = temporaryFolder.newFolder(phase.optionName)
        val slots = scheduledCampaignSlotsV61(phase)
        val sourceByNumber = source.levels.associateBy(LevelDefinition::number)
        val levels = slots.map { slot ->
            val level = sourceByNumber.getValue(slot.levelNumber)
            level.copy(
                id = "campaign-${slot.levelNumber}",
                metadata = requireNotNull(level.metadata).copy(
                    contentVersion = 12,
                    generatorVersion = 6,
                    generationProfile = "v6.1-${slot.band.name.lowercase().replace('_', '-')}",
                ),
            )
        }
        val catalog = File(directory, "catalog.json").apply {
            writeText(
                parser.encodeCatalog(
                    LevelCatalog(2, "magnetrail-core-1", phase.catalogId, levels, 12, 6),
                ) + "\n",
            )
        }
        val audit = File(directory, "audit.json").apply {
            val rows = slots.zip(levels).map { (slot, level) ->
                buildJsonObject {
                    put("levelNumber", slot.levelNumber)
                    put("requestedBand", slot.band.displayName)
                    put("accepted", true)
                    put("causalFamily", "family-${slot.familyIndex}")
                    put("strategyCluster", "cluster-${slot.levelNumber}")
                    put("fingerprints", buildJsonObject {
                        put("exact", ContentFingerprint.exact(level))
                        put("d4", ContentFingerprint.symmetryNormalized(level))
                        put("relevancePrunedD4", "relevance-${slot.levelNumber}")
                        put("causal", "causal-${slot.levelNumber}")
                        put("decisionDag", "dag-${slot.levelNumber}")
                        put("solutionPolicy", "policy-${slot.levelNumber}")
                        put("synthesisGraph", "synthesis-${slot.levelNumber}")
                        put("nearestSemanticSimilarity", 0.0)
                    })
                    put("purposefulOccupiedRatio", 1.0)
                    put("inertOccupiedRatio", 0.0)
                    put("solverStateCount", 1)
                    put("analysisTruncated", false)
                    put("inferableCriticalDecisionRatio", 1.0)
                    put("guessDependencePass", true)
                    put("maximumEligibleBand", slot.band.displayName)
                }
            }
            val json = buildJsonObject {
                put("schemaVersion", 1)
                put("candidates", buildJsonArray { rows.forEach(::add) })
            }
            writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json) + "\n")
        }
        val manifest = File(directory, "manifest.json").apply {
            val json = buildJsonObject {
                put("validationPlan", "PHASED_V61_1325_868_FINAL_2205")
                put("phase", phase.optionName)
                put("sourceCampaignSha256", sourceSha)
                put("candidateCatalogSha256", sha(catalog))
                put("candidateAuditSha256", sha(audit))
            }
            writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json) + "\n")
        }
        val certificate = File(directory, "certificate.json").apply {
            val json = buildJsonObject {
                put("validationPlan", "PHASED_V61_1325_868_FINAL_2205")
                put("phase", phase.optionName)
                put("certificationType", phase.acceptedStatus)
                put("boardCount", phase.expectedBoardCount)
                put("sourceProductionHash", sourceSha)
                put("campaignHash", sha(catalog))
                put("auditHash", sha(audit))
                put("manifestHash", sha(manifest))
                listOf(
                    "allAnalysisComplete",
                    "allVisibleProofsComplete",
                    "allProductionEngineSolvableAndReplayed",
                    "allDifficultyCapsPassed",
                    "allPurposefulOccupancyPassed",
                    "regressionCorpusPassed",
                    "deterministicRegenerationPassed",
                    "mandatoryAutomatedTestSuitePassed",
                    "pacingDeferredToFinalMerge",
                ).forEach { put(it, true) }
                listOf(
                    "requiredGuessingPredictionCount",
                    "exactDuplicateCount",
                    "d4DuplicateCount",
                    "relevancePrunedD4DuplicateCount",
                    "causalGraphDuplicateCount",
                    "decisionDagDuplicateCount",
                    "solutionPolicyDuplicateCount",
                    "synthesisGraphDuplicateCount",
                    "nearSemanticFailureCount",
                ).forEach { put(it, 0) }
            }
            writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), json) + "\n")
        }
        return TestPhaseFiles(catalog, audit, manifest, certificate)
    }

    private fun sha(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class TestPhaseFiles(
        val catalog: File,
        val audit: File,
        val manifest: File,
        val certificate: File,
    )
}

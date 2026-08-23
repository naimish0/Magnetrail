package com.rameshta.magnetrail.tools

import com.rameshta.magnetrail.core.generation.v6.AutomatedDifficultyBandV61
import com.rameshta.magnetrail.core.generation.v6.GeneratorV61
import com.rameshta.magnetrail.core.generation.v6.V61AnalysisCache
import com.rameshta.magnetrail.core.generation.v6.V61FingerprintIndex
import com.rameshta.magnetrail.core.generation.v6.V61GenerationBudgets
import com.rameshta.magnetrail.core.generation.v6.V61GenerationRequest
import com.rameshta.magnetrail.core.generation.v6.V61GenerationResult
import com.rameshta.magnetrail.core.generation.v6.V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT
import com.rameshta.magnetrail.core.generation.v6.V61_HARD_CAPACITY_ROLLOUT_LEVEL
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratorV61ParallelWorkflowTest {
    @Test
    fun `high-band rollout migration preserves only unaffected phase prefixes`() {
        val context = V61CheckpointContext("source", "archive", "new-revision", "new-preflight")
        fun checkpoint(phase: V61CampaignPhase) = buildJsonObject {
            put("checkpointType", "V61_ATOMIC_CERTIFIED_LEVEL")
            put("phase", phase.optionName)
            put("sourceCampaignSha256", context.sourceCampaignSha256)
            put("archiveBindingHash", context.archiveBindingHash)
            put("relevantSourceHash", V61_HIGH_BAND_CAPACITY_BASE_RELEVANT_SOURCE_HASH)
            put("preflightSha256", "old-preflight")
        }

        assertTrue(
            checkpointMatchesHighBandCapacityMigrationV61(
                checkpoint(V61CampaignPhase.PHASE_ONE),
                V61CampaignPhase.PHASE_ONE,
                context,
                2_205,
            ),
        )
        assertTrue(
            checkpointMatchesHighBandCapacityMigrationV61(
                checkpoint(V61CampaignPhase.PHASE_TWO),
                V61CampaignPhase.PHASE_TWO,
                context,
                com.rameshta.magnetrail.core.generation.v6.V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL - 1,
            ),
        )
        assertFalse(
            checkpointMatchesHighBandCapacityMigrationV61(
                checkpoint(V61CampaignPhase.PHASE_TWO),
                V61CampaignPhase.PHASE_TWO,
                context,
                com.rameshta.magnetrail.core.generation.v6.V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL,
            ),
        )
        assertFalse(
            checkpointMatchesHighBandCapacityMigrationV61(
                checkpoint(V61CampaignPhase.PHASE_TWO),
                V61CampaignPhase.PHASE_TWO,
                context.copy(archiveBindingHash = "changed-archive"),
                700,
            ),
        )

        val narrowedCheckpoint = buildJsonObject {
            put("checkpointType", "V61_ATOMIC_CERTIFIED_LEVEL")
            put("phase", V61CampaignPhase.PHASE_TWO.optionName)
            put("sourceCampaignSha256", context.sourceCampaignSha256)
            put("archiveBindingHash", context.archiveBindingHash)
            put("relevantSourceHash", V61_SUPER_HARD_ONLY_BASE_RELEVANT_SOURCE_HASH)
            put("preflightSha256", "old-preflight")
        }
        assertTrue(
            checkpointMatchesSuperHardOnlyMigrationV61(
                narrowedCheckpoint,
                V61CampaignPhase.PHASE_TWO,
                context,
                com.rameshta.magnetrail.core.generation.v6.V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL,
            ),
        )
        assertFalse(
            checkpointMatchesSuperHardOnlyMigrationV61(
                narrowedCheckpoint,
                V61CampaignPhase.PHASE_TWO,
                context,
                com.rameshta.magnetrail.core.generation.v6.V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL + 1,
            ),
        )

        val epochCheckpoint = buildJsonObject {
            put("checkpointType", "V61_ATOMIC_CERTIFIED_LEVEL")
            put("phase", V61CampaignPhase.PHASE_TWO.optionName)
            put("sourceCampaignSha256", context.sourceCampaignSha256)
            put("archiveBindingHash", context.archiveBindingHash)
            put("relevantSourceHash", V61_EPOCH_ROTATION_BASE_RELEVANT_SOURCE_HASH)
            put("preflightSha256", "old-preflight")
        }
        assertTrue(
            checkpointMatchesEpochRotationMigrationV61(
                epochCheckpoint,
                V61CampaignPhase.PHASE_TWO,
                context,
                com.rameshta.magnetrail.core.generation.v6.V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL - 1,
                AutomatedDifficultyBandV61.EXPERT,
            ),
        )
        assertFalse(
            checkpointMatchesEpochRotationMigrationV61(
                epochCheckpoint,
                V61CampaignPhase.PHASE_TWO,
                context,
                com.rameshta.magnetrail.core.generation.v6.V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL,
                AutomatedDifficultyBandV61.EXPERT,
            ),
        )
        assertTrue(
            checkpointMatchesEpochRotationMigrationV61(
                epochCheckpoint,
                V61CampaignPhase.PHASE_TWO,
                context,
                com.rameshta.magnetrail.core.generation.v6.V61_HIGH_BAND_CAPACITY_ROLLOUT_LEVEL,
                AutomatedDifficultyBandV61.SUPER_HARD,
            ),
        )
    }

    @Test
    fun `synthesis evidence migration is exact hash and archive bound`() {
        val context = V61CheckpointContext("source", "archive", "new-revision", "new-preflight")
        val checkpoint = buildJsonObject {
            put("checkpointType", "V61_ATOMIC_CERTIFIED_LEVEL")
            put("phase", V61CampaignPhase.PHASE_ONE.optionName)
            put("sourceCampaignSha256", context.sourceCampaignSha256)
            put("archiveBindingHash", context.archiveBindingHash)
            put("relevantSourceHash", V61_SYNTHESIS_EVIDENCE_RELEVANT_SOURCE_HASH)
            put("preflightSha256", "old-preflight")
        }

        assertTrue(
            checkpointMatchesSynthesisEvidenceMigrationV61(
                checkpoint,
                V61CampaignPhase.PHASE_ONE,
                context,
            ),
        )
        assertFalse(
            checkpointMatchesSynthesisEvidenceMigrationV61(
                checkpoint,
                V61CampaignPhase.PHASE_TWO,
                context,
            ),
        )
        assertFalse(
            checkpointMatchesSynthesisEvidenceMigrationV61(
                checkpoint,
                V61CampaignPhase.PHASE_ONE,
                context.copy(archiveBindingHash = "changed-archive"),
            ),
        )
    }

    @Test
    fun `speculative window migration is exact hash phase and archive bound`() {
        val context = V61CheckpointContext("source", "archive", "new-revision", "new-preflight")
        fun checkpoint(hash: String, archive: String = context.archiveBindingHash) = buildJsonObject {
            put("checkpointType", "V61_ATOMIC_CERTIFIED_LEVEL")
            put("phase", V61CampaignPhase.PHASE_TWO.optionName)
            put("sourceCampaignSha256", context.sourceCampaignSha256)
            put("archiveBindingHash", archive)
            put("relevantSourceHash", hash)
            put("preflightSha256", "old-preflight")
        }

        assertTrue(
            checkpointMatchesSpeculativeWindowMigrationV61(
                checkpoint(V61_SPECULATIVE_WINDOW_BASE_RELEVANT_SOURCE_HASH),
                V61CampaignPhase.PHASE_TWO,
                context,
            ),
        )
        assertFalse(
            checkpointMatchesSpeculativeWindowMigrationV61(
                checkpoint("different"),
                V61CampaignPhase.PHASE_TWO,
                context,
            ),
        )
        assertFalse(
            checkpointMatchesSpeculativeWindowMigrationV61(
                checkpoint(V61_SPECULATIVE_WINDOW_BASE_RELEVANT_SOURCE_HASH, archive = "changed"),
                V61CampaignPhase.PHASE_TWO,
                context,
            ),
        )
    }

    @Test
    fun `additive hard epoch rebinds only exact base checkpoints below boundary`() {
        val context = V61CheckpointContext("source", "archive", "new-revision", "new-preflight")
        val checkpoint = buildJsonObject {
            put("checkpointType", "V61_ATOMIC_CERTIFIED_LEVEL")
            put("phase", V61CampaignPhase.PHASE_ONE.optionName)
            put("sourceCampaignSha256", context.sourceCampaignSha256)
            put("archiveBindingHash", context.archiveBindingHash)
            put("relevantSourceHash", V61_HARD_CAPACITY_BASE_RELEVANT_SOURCE_HASH)
            put("preflightSha256", "old-preflight")
        }

        assertTrue(
            checkpointMatchesAdditiveHardEpochV61(
                checkpoint,
                V61CampaignPhase.PHASE_ONE,
                context,
                13,
                V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT - 1,
            ),
        )
        assertFalse(
            checkpointMatchesAdditiveHardEpochV61(
                checkpoint,
                V61CampaignPhase.PHASE_ONE,
                context,
                13,
                V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT,
            ),
        )
        assertFalse(
            checkpointMatchesAdditiveHardEpochV61(
                checkpoint,
                V61CampaignPhase.PHASE_ONE,
                context.copy(archiveBindingHash = "changed-archive"),
                13,
                0,
            ),
        )
        val epochCheckpoint = buildJsonObject {
            put("checkpointType", "V61_ATOMIC_CERTIFIED_LEVEL")
            put("phase", V61CampaignPhase.PHASE_ONE.optionName)
            put("sourceCampaignSha256", context.sourceCampaignSha256)
            put("archiveBindingHash", context.archiveBindingHash)
            put("relevantSourceHash", V61_HARD_CAPACITY_EPOCH_RELEVANT_SOURCE_HASH)
            put("preflightSha256", "epoch-preflight")
        }
        assertTrue(
            checkpointMatchesAdditiveHardEpochV61(
                epochCheckpoint,
                V61CampaignPhase.PHASE_ONE,
                context,
                V61_HARD_CAPACITY_ROLLOUT_LEVEL - 1,
                V61_HARD_CAPACITY_EMBEDDING_START_ATTEMPT + 21,
            ),
        )
        assertFalse(
            checkpointMatchesAdditiveHardEpochV61(
                epochCheckpoint,
                V61CampaignPhase.PHASE_ONE,
                context,
                V61_HARD_CAPACITY_ROLLOUT_LEVEL,
                0,
            ),
        )
    }

    @Test
    fun `ordered speculative admission is identical across worker counts`() {
        val sequential = generate(workers = 1)
        val parallel = generate(workers = 4)

        assertEquals(sequential.identity.stableKey, parallel.identity.stableKey)
        assertEquals(sequential.level, parallel.level)
        assertEquals(sequential.fingerprints, parallel.fingerprints)
        assertEquals(sequential.difficulty, parallel.difficulty)
    }

    @Test
    fun `ordered speculative admission honors a bounded nonzero epoch`() {
        val sequential = generate(workers = 1, startingAttempt = 24)
        val parallel = generate(workers = 4, startingAttempt = 24)

        assertTrue(sequential.identity.attempt >= 24)
        assertEquals(sequential.identity.stableKey, parallel.identity.stableKey)
        assertEquals(sequential.fingerprints, parallel.fingerprints)
    }

    @Test
    fun `atomic checkpoint survives split companion interruption and invalidates changed context`() {
        val candidate = generate(workers = 2)
        val directory = Files.createTempDirectory("v61-checkpoint-test").toFile()
        val context = V61CheckpointContext("source-a", "archive-a", "revision-a", "preflight-a")
        try {
            writeGeneratorV61Checkpoint(
                directory,
                V61CampaignPhase.PHASE_ONE,
                candidate.level,
                candidate.auditRow(13, AutomatedDifficultyBandV61.EASY),
                candidate.identity.attempt,
                context,
            )
            val atomic = directory.listFiles().single { it.name.endsWith("-checkpoint.json") }
            val splitAudit = directory.listFiles().single { it.name.endsWith("-audit.json") }
            assertTrue(splitAudit.delete())

            val checkpoint = Json.parseToJsonElement(atomic.readText()).jsonObject
            assertTrue(checkpoint.getValue("catalog").jsonObject.isNotEmpty())
            assertTrue(checkpoint.getValue("audit").jsonObject.isNotEmpty())
            assertEquals(0, checkpoint.getValue("startingAttempt").jsonPrimitive.content.toInt())
            assertTrue(checkpointMatchesContextV61(checkpoint, V61CampaignPhase.PHASE_ONE, context))
            assertFalse(
                checkpointMatchesContextV61(
                    checkpoint,
                    V61CampaignPhase.PHASE_ONE,
                    context.copy(relevantSourceHash = "revision-b"),
                ),
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun generate(workers: Int, startingAttempt: Int = 0) = Executors.newFixedThreadPool(workers).let { executor ->
        try {
            val result = generateParallelV61(
                baseRequest = V61GenerationRequest(
                    levelId = "parallel-admission-test",
                    playerFacingNumber = 13,
                    ordinal = 1,
                    band = AutomatedDifficultyBandV61.EASY,
                    causalFamilyIndex = 5,
                    varyCausalFamilyByAttempt = true,
                    startingAttempt = startingAttempt,
                    knownFingerprintIndex = V61FingerprintIndex(),
                    analysisCache = V61AnalysisCache(),
                    budgets = V61GenerationBudgets(maximumAttempts = 1),
                ),
                maximumAttempts = 16,
                workers = workers,
                executor = executor,
                runRejectionHistogram = linkedMapOf(),
            )
            assertTrue((result as? V61GenerationResult.Rejected)?.failure.toString(), result is V61GenerationResult.Certified)
            (result as V61GenerationResult.Certified).candidate
        } finally {
            executor.shutdownNow()
        }
    }
}

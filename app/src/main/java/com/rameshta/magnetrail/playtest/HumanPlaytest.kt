package com.rameshta.magnetrail.playtest

import com.rameshta.magnetrail.core.level.LevelCatalog
import com.rameshta.magnetrail.core.model.LevelDefinition
import java.util.Locale
import java.util.Random
import java.security.MessageDigest
import kotlinx.serialization.Serializable

const val HUMAN_PLAYTEST_SCHEMA_VERSION = 5
const val HUMAN_PLAYTEST_SAMPLE_PER_DIFFICULTY = 10
const val HUMAN_PLAYTEST_V6_SAMPLE_PER_DIFFICULTY = 6
const val HUMAN_PLAYTEST_V10_SAMPLE_PER_DIFFICULTY = 5
const val HUMAN_PLAYTEST_V10_TARGET_SIZE = 30
const val HUMAN_PLAYTEST_STUDY_SEED = 11_600_030L

@Serializable
enum class HumanPlaytestDifficulty(
    val rating: Int,
    val displayName: String,
    val generationProfile: String,
) {
    EASY(1, "Easy", "v5-campaign-v10-easy-dense"),
    MEDIUM(2, "Medium", "v5-campaign-v10-medium-dense"),
    HARD(3, "Hard", "v5-campaign-v10-hard-dense"),
    SUPER_HARD(4, "Super Hard", "v5-campaign-v10-super-hard-dense"),
    EXPERT(5, "Expert", "v5-campaign-v10-expert-dense"),
    MASTER(6, "Master", "v5-campaign-v10-master-dense");

    companion object {
        fun fromProfile(profile: String?): HumanPlaytestDifficulty? = entries.firstOrNull {
            it.generationProfile == profile || profile == "v6-bucket-${it.rating}"
        }

        fun fromRating(rating: Int): HumanPlaytestDifficulty = entries.first { it.rating == rating }
    }
}

@Serializable
data class HumanPlaytestAssignment(
    val order: Int,
    val blindId: String,
    val levelId: String,
    val expectedDifficulty: HumanPlaytestDifficulty,
    val causalFamily: String? = null,
)

@Serializable
data class HumanPlaytestObservation(
    val order: Int,
    val blindId: String,
    val levelId: String,
    val expectedDifficulty: HumanPlaytestDifficulty,
    val perceivedDifficulty: HumanPlaytestDifficulty,
    val completed: Boolean,
    val finalAttemptActions: Int,
    val totalActions: Int,
    val totalOverloads: Int,
    val hintsUsed: Int,
    val restarts: Int,
    val durationMillis: Long,
    val startedAtEpochMillis: Long,
    val recordedAtEpochMillis: Long,
    val width: Int,
    val height: Int,
    val arrowCount: Int,
    val magnetCount: Int,
    val wallCount: Int,
    val certifiedSolutionLength: Int?,
    val contentFingerprint: String?,
    val catalogFingerprint: String = "",
    val causalFingerprint: String? = null,
    val decisionDagFingerprint: String? = null,
    val solutionPolicyFingerprint: String? = null,
    val semanticCluster: String? = null,
    val failedActions: Int = totalOverloads,
    val successfulWrongActions: Int = 0,
    val deadlocks: Int = 0,
    val fairnessRating: Int = 0,
    val guessRequired: Boolean = false,
    val guessResponse: HumanPlaytestGuessResponse = if (guessRequired) {
        HumanPlaytestGuessResponse.YES
    } else {
        HumanPlaytestGuessResponse.NO
    },
    val guessDecisionStateFingerprint: String? = null,
    val repeatedStrategy: Boolean = false,
    val comment: String = "",
)

@Serializable
data class HumanPlaytestSession(
    val schemaVersion: Int = HUMAN_PLAYTEST_SCHEMA_VERSION,
    val sessionId: String,
    val participantCode: String,
    val studySeed: Long,
    val campaignContentVersion: Int,
    val startedAtEpochMillis: Long,
    val assignments: List<HumanPlaytestAssignment>,
    val observations: List<HumanPlaytestObservation> = emptyList(),
) {
    val completedCount: Int get() = observations.size
    val isComplete: Boolean get() = completedCount == assignments.size
    val nextAssignment: HumanPlaytestAssignment?
        get() {
            val completedOrders = observations.mapTo(hashSetOf()) { it.order }
            return assignments.firstOrNull { it.order !in completedOrders }
        }
}

data class HumanPlaytestUiState(
    val loading: Boolean = false,
    val participantCodeInput: String = "",
    val session: HumanPlaytestSession? = null,
    val message: String? = null,
)

enum class HumanPlaytestOutcome {
    COMPLETED,
    ABANDONED,
}

@Serializable
enum class HumanPlaytestGuessResponse(val displayName: String) {
    NO("No"),
    UNSURE("Unsure"),
    YES("Yes"),
}

val HUMAN_PLAYTEST_FAIRNESS_ANCHORS: Map<Int, String> = linkedMapOf(
    1 to "Completely unfair — I could only guess",
    2 to "Mostly unfair — important outcomes were unclear",
    3 to "Mixed — some choices were predictable",
    4 to "Mostly fair — visible reasoning usually worked",
    5 to "Completely fair — outcomes followed visible rules",
)

data class HumanPlaytestOutcomeDraft(
    val outcome: HumanPlaytestOutcome,
    val finalAttemptActions: Int,
    val totalActions: Int,
    val totalOverloads: Int,
    val hintsUsed: Int,
    val restarts: Int,
    val durationMillis: Long,
    val startedAtEpochMillis: Long,
    val successfulWrongActions: Int = 0,
    val deadlocks: Int = 0,
)

data class HumanPlaytestFeedbackDraft(
    val perceivedRating: Int? = null,
    val fairnessRating: Int? = null,
    val guessResponse: HumanPlaytestGuessResponse? = null,
    val repeatedStrategy: Boolean? = null,
    val comment: String = "",
) {
    val complete: Boolean
        get() = perceivedRating != null && fairnessRating != null &&
            guessResponse != null && repeatedStrategy != null
}

data class HumanPlaytestExport(
    val fileName: String,
    val content: String,
)

object HumanPlaytestPlanner {
    fun normalizeParticipantCode(raw: String): String {
        val normalized = raw.trim().uppercase(Locale.US)
        require(normalized.matches(Regex("[A-Z0-9_-]{2,24}"))) {
            "Use a 2–24 character anonymous code containing letters, numbers, _ or -."
        }
        return normalized
    }

    fun assignments(
        catalog: LevelCatalog,
        participantCode: String,
        samplesPerDifficulty: Int = HUMAN_PLAYTEST_SAMPLE_PER_DIFFICULTY,
        studySeed: Long = HUMAN_PLAYTEST_STUDY_SEED,
    ): List<HumanPlaytestAssignment> {
        require(samplesPerDifficulty > 0)
        val participant = normalizeParticipantCode(participantCode)
        val studyDifficulties = if (catalog.generatorVersion == 6) {
            HumanPlaytestDifficulty.entries.filter { it != HumanPlaytestDifficulty.MASTER }
        } else {
            HumanPlaytestDifficulty.entries
        }
        val selectedByDifficulty = studyDifficulties.associateWith { difficulty ->
            val candidates = catalog.levels.filter { level ->
                level.metadata?.contentVersion == catalog.contentVersion &&
                    HumanPlaytestDifficulty.fromProfile(level.metadata?.generationProfile) == difficulty
            }.sortedBy(LevelDefinition::id).toMutableList()
            val required = when {
                catalog.generatorVersion == 6 -> HUMAN_PLAYTEST_V6_SAMPLE_PER_DIFFICULTY
                catalog.contentVersion == 10 -> HUMAN_PLAYTEST_V10_SAMPLE_PER_DIFFICULTY
                difficulty == HumanPlaytestDifficulty.MASTER -> candidates.size.coerceAtMost(samplesPerDifficulty)
                else -> samplesPerDifficulty
            }
            require(required > 0 && candidates.size >= required) {
                "${difficulty.displayName} needs ${when {
                    catalog.generatorVersion == 6 -> HUMAN_PLAYTEST_V6_SAMPLE_PER_DIFFICULTY
                    catalog.contentVersion == 10 -> HUMAN_PLAYTEST_V10_SAMPLE_PER_DIFFICULTY
                    difficulty == HumanPlaytestDifficulty.MASTER -> "at least 1"
                    else -> samplesPerDifficulty
                }} " +
                    "V${catalog.contentVersion} pilot boards; found ${candidates.size}."
            }
            val selectionRandom = Random(studySeed xor (difficulty.rating * SEED_STRIDE))
            val selected = if (catalog.contentVersion == 10) {
                sizeStratifiedSelection(candidates, required, selectionRandom)
            } else {
                candidates.stableShuffle(selectionRandom)
                candidates.take(required).toMutableList()
            }
            selected.also {
                selected.stableShuffle(Random(studySeed xor stableHash(participant) xor difficulty.rating.toLong()))
            }
        }

        val ordered = buildList {
            repeat(selectedByDifficulty.values.maxOf { it.size }) { round ->
                val difficulties = studyDifficulties
                    .filter { selectedByDifficulty.getValue(it).size > round }
                    .toMutableList()
                difficulties.stableShuffle(Random(studySeed xor stableHash(participant) xor (round * SEED_STRIDE)))
                difficulties.forEach { difficulty ->
                    add(difficulty to selectedByDifficulty.getValue(difficulty)[round])
                }
            }
        }
        val adjacencySafe = avoidAdjacentFamilies(ordered)
        return adjacencySafe.mapIndexed { index, (difficulty, level) ->
            HumanPlaytestAssignment(
                order = index + 1,
                blindId = "PT-${(index + 1).toString().padStart(3, '0')}",
                levelId = level.id,
                expectedDifficulty = difficulty,
                causalFamily = level.v6Tag("V6_FAMILY_")?.removePrefix("V6_FAMILY_"),
            )
        }.also { assignments ->
            if (catalog.contentVersion == 10) check(assignments.size == HUMAN_PLAYTEST_V10_TARGET_SIZE)
        }
    }

    fun catalogFingerprint(catalog: LevelCatalog): String {
        val payload = catalog.levels.sortedBy(LevelDefinition::id).joinToString("\n") { level ->
            "${level.id}:${level.metadata?.contentFingerprint.orEmpty()}"
        }
        return "sha256:" + MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun avoidAdjacentFamilies(
        source: List<Pair<HumanPlaytestDifficulty, LevelDefinition>>,
    ): List<Pair<HumanPlaytestDifficulty, LevelDefinition>> {
        val remaining = source.toMutableList()
        return buildList {
            while (remaining.isNotEmpty()) {
                val previous = lastOrNull()?.second?.v6Tag("V6_FAMILY_")
                val index = remaining.indexOfFirst { it.second.v6Tag("V6_FAMILY_") != previous }
                    .takeIf { it >= 0 } ?: 0
                add(remaining.removeAt(index))
            }
        }
    }

    private fun LevelDefinition.v6Tag(prefix: String): String? =
        metadata?.mechanicTags?.firstOrNull { it.startsWith(prefix) }

    private fun sizeStratifiedSelection(
        candidates: List<LevelDefinition>,
        required: Int,
        random: Random,
    ): MutableList<LevelDefinition> {
        val pools = candidates
            .groupBy { it.width to it.height }
            .toSortedMap(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .values
            .map { levels -> levels.toMutableList().also { it.stableShuffle(random) } }
        require(required >= pools.size) {
            "A $required-board sample cannot cover all ${pools.size} available board sizes."
        }
        val selected = pools.mapTo(mutableListOf()) { it.removeAt(0) }
        val remaining = pools.flatten().toMutableList().also { it.stableShuffle(random) }
        selected += remaining.take(required - selected.size)
        check(selected.size == required)
        return selected
    }

    private fun stableHash(value: String): Long {
        var hash = -3_750_763_034_362_895_579L
        value.forEach { character ->
            hash = hash xor character.code.toLong()
            hash *= 1_099_511_628_211L
        }
        return hash
    }

    private fun <T> MutableList<T>.stableShuffle(random: Random) {
        for (index in lastIndex downTo 1) {
            val target = random.nextInt(index + 1)
            val value = this[index]
            this[index] = this[target]
            this[target] = value
        }
    }

    private const val SEED_STRIDE = 1_000_003L

}

object HumanPlaytestCsv {
    private val columns = listOf(
        "schema_version",
        "session_id",
        "participant_code",
        "campaign_content_version",
        "study_seed",
        "blind_board",
        "board_order",
        "level_id",
        "expected_difficulty",
        "expected_rating",
        "perceived_difficulty",
        "perceived_rating",
        "completed",
        "final_attempt_actions",
        "total_actions",
        "total_overloads",
        "failed_actions",
        "successful_wrong_actions",
        "hints_used",
        "restarts",
        "deadlocks",
        "duration_ms",
        "started_at_epoch_ms",
        "recorded_at_epoch_ms",
        "width",
        "height",
        "arrows",
        "magnets",
        "walls",
        "total_occupancy_percent",
        "certified_solution_length",
        "content_fingerprint",
        "catalog_fingerprint",
        "causal_fingerprint",
        "decision_dag_fingerprint",
        "solution_policy_fingerprint",
        "semantic_cluster",
        "fairness_rating",
        "guess_required",
        "repeated_strategy",
        "comment",
        "guess_response",
        "fairness_anchor",
        "guess_decision_state_fingerprint",
    )

    fun encode(session: HumanPlaytestSession): String = buildString {
        appendLine(columns.joinToString(","))
        session.observations.sortedBy { it.order }.forEach { observation ->
            val area = observation.width * observation.height
            val occupancy = if (area == 0) 0.0 else {
                100.0 * (observation.arrowCount + observation.magnetCount + observation.wallCount) / area
            }
            appendLine(
                listOf(
                    session.schemaVersion,
                    session.sessionId,
                    session.participantCode,
                    session.campaignContentVersion,
                    session.studySeed,
                    observation.blindId,
                    observation.order,
                    observation.levelId,
                    observation.expectedDifficulty.displayName,
                    observation.expectedDifficulty.rating,
                    observation.perceivedDifficulty.displayName,
                    observation.perceivedDifficulty.rating,
                    observation.completed,
                    observation.finalAttemptActions,
                    observation.totalActions,
                    observation.totalOverloads,
                    observation.failedActions,
                    observation.successfulWrongActions,
                    observation.hintsUsed,
                    observation.restarts,
                    observation.deadlocks,
                    observation.durationMillis,
                    observation.startedAtEpochMillis,
                    observation.recordedAtEpochMillis,
                    observation.width,
                    observation.height,
                    observation.arrowCount,
                    observation.magnetCount,
                    observation.wallCount,
                    String.format(Locale.US, "%.2f", occupancy),
                    observation.certifiedSolutionLength ?: "",
                    observation.contentFingerprint.orEmpty(),
                    observation.catalogFingerprint,
                    observation.causalFingerprint.orEmpty(),
                    observation.decisionDagFingerprint.orEmpty(),
                    observation.solutionPolicyFingerprint.orEmpty(),
                    observation.semanticCluster.orEmpty(),
                    observation.fairnessRating,
                    observation.guessRequired,
                    observation.repeatedStrategy,
                    observation.comment,
                    observation.guessResponse.name,
                    HUMAN_PLAYTEST_FAIRNESS_ANCHORS[observation.fairnessRating].orEmpty(),
                    observation.guessDecisionStateFingerprint.orEmpty(),
                ).joinToString(",") { csvCell(it.toString()) },
            )
        }
    }

    private fun csvCell(value: String): String = if (
        value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    ) {
        "\"${value.replace("\"", "\"\"")}\""
    } else {
        value
    }
}

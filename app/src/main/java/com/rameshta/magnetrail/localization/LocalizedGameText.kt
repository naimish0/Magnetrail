package com.rameshta.magnetrail.localization

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import com.rameshta.magnetrail.R
import com.rameshta.magnetrail.core.infinite.InfiniteDifficulty
import com.rameshta.magnetrail.core.model.Direction
import com.rameshta.magnetrail.core.model.LevelDefinition
import com.rameshta.magnetrail.core.model.Polarity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun InfiniteDifficulty.localizedName(): String = stringResource(
    when (this) {
        InfiniteDifficulty.PROGRESSIVE -> R.string.difficulty_progressive
        InfiniteDifficulty.RELAXED -> R.string.difficulty_easy
        InfiniteDifficulty.BALANCED -> R.string.difficulty_medium
        InfiniteDifficulty.CHALLENGING -> R.string.difficulty_hard
        InfiniteDifficulty.VERY_HARD -> R.string.difficulty_super_hard
        InfiniteDifficulty.EXPERT -> R.string.difficulty_expert
        InfiniteDifficulty.MASTER -> R.string.difficulty_master
    },
)

@Composable
fun InfiniteDifficulty.localizedExplanation(): String = stringResource(
    when (this) {
        InfiniteDifficulty.PROGRESSIVE -> R.string.difficulty_progressive_detail
        InfiniteDifficulty.RELAXED -> R.string.difficulty_easy_detail
        InfiniteDifficulty.BALANCED -> R.string.difficulty_medium_detail
        InfiniteDifficulty.CHALLENGING -> R.string.difficulty_hard_detail
        InfiniteDifficulty.VERY_HARD -> R.string.difficulty_super_hard_detail
        InfiniteDifficulty.EXPERT -> R.string.difficulty_expert_detail
        InfiniteDifficulty.MASTER -> R.string.difficulty_master_detail
    },
)

@Composable
fun localizedDifficultyName(value: String): String {
    val resource = when (value.trim().lowercase().replace('_', ' ')) {
        "progressive", "progressive journey" -> R.string.difficulty_progressive
        "easy", "relaxed", "intro" -> R.string.difficulty_easy
        "medium", "balanced" -> R.string.difficulty_medium
        "hard", "challenging" -> R.string.difficulty_hard
        "very hard", "super hard" -> R.string.difficulty_super_hard
        "expert" -> R.string.difficulty_expert
        "master" -> R.string.difficulty_master
        else -> null
    }
    return resource?.let { stringResource(it) } ?: value
}

@Composable
fun localizedLevelTitle(level: LevelDefinition): String {
    val suffix = level.title.substringAfterLast(' ', missingDelimiterValue = "")
    return when {
        level.title.startsWith("Foundation ") -> stringResource(R.string.level_title_foundation, suffix)
        level.title.startsWith("Magnetic Circuit ") ->
            stringResource(R.string.level_title_magnetic_circuit, suffix)
        level.title.startsWith("Level ") -> stringResource(R.string.level_title_generic, level.number)
        else -> level.title
    }
}

@Composable
fun localizedPackName(packId: String): String {
    val number = packId.substringAfterLast('-').toIntOrNull()?.toString()?.padStart(2, '0')
    return if (packId.startsWith("magnetic-circuit") && number != null) {
        stringResource(R.string.level_title_magnetic_circuit, number)
    } else {
        packId.replace('-', ' ').replaceFirstChar { it.uppercase() }
    }
}

@Composable
fun localizedDateLabel(isoDate: String?): String {
    if (isoDate.isNullOrBlank()) return ""
    val locale = LocalConfiguration.current.locales[0]
    return runCatching {
        LocalDate.parse(isoDate).format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale),
        )
    }.getOrDefault(isoDate)
}

@Composable
fun Direction.localizedName(): String = stringResource(
    when (this) {
        Direction.NORTH -> R.string.direction_north
        Direction.SOUTH -> R.string.direction_south
        Direction.EAST -> R.string.direction_east
        Direction.WEST -> R.string.direction_west
    },
)

@Composable
fun Polarity.localizedName(): String = stringResource(
    if (this == Polarity.PULL) R.string.polarity_pull else R.string.polarity_push,
)

@Composable
fun localizedRuntimeMessage(message: String): String {
    exactRuntimeMessages[message]?.let { return stringResource(it) }
    Regex("Hint: Try arrow (.+)").matchEntire(message)?.let {
        return stringResource(R.string.runtime_hint_arrow, it.groupValues[1])
    }
    Regex("A hint costs (\\d+) coins\\. Balance: (\\d+)\\.").matchEntire(message)?.let {
        return stringResource(R.string.runtime_hint_cost, it.groupValues[1].toInt(), it.groupValues[2].toInt())
    }
    Regex("Strict bounded generation exhausted for (.+); next level is being prepared\\.")
        .matchEntire(message)
        ?.let {
            return stringResource(
                R.string.runtime_generation_exhausted,
                localizedDifficultyName(it.groupValues[1]),
            )
        }
    return message
}

@Composable
fun localizedCelebrationMessage(message: String): String = stringResource(
    when (message) {
        "Brilliant sequence!" -> R.string.celebration_brilliant_sequence
        "Perfect read!" -> R.string.celebration_perfect_read
        "Clean solve!" -> R.string.celebration_clean_solve
        "Sharp thinking!" -> R.string.celebration_sharp_thinking
        "Beautifully played!" -> R.string.celebration_beautifully_played
        "Great finish!" -> R.string.celebration_great_finish
        "Nicely worked out!" -> R.string.celebration_nicely_worked_out
        "You found the route!" -> R.string.celebration_found_route
        "Nice recovery!" -> R.string.celebration_nice_recovery
        "Strong comeback!" -> R.string.celebration_strong_comeback
        "Well recovered!" -> R.string.celebration_well_recovered
        "Nice sequence!" -> R.string.celebration_nice_sequence
        "Great thinking!" -> R.string.celebration_great_thinking
        "Field mastered!" -> R.string.celebration_field_mastered
        else -> R.string.celebration_smooth_solve
    },
)

private val exactRuntimeMessages: Map<String, Int> = mapOf(
    "Finding a clean move" to R.string.runtime_finding_clean_move,
    "Preparing hint" to R.string.runtime_preparing_hint,
    "No earned ad hint is available." to R.string.runtime_no_ad_hint,
    "No hint is available. Restart to keep exploring." to R.string.runtime_no_hint,
    "Daily Challenge is unavailable in this build." to R.string.runtime_daily_unavailable,
    "Daily Challenge could not be prepared." to R.string.runtime_daily_error,
    "Next level is being prepared. Please retry." to R.string.runtime_next_level_retry,
    "Preparing the next certified level…" to R.string.runtime_preparing_next_level,
    "Your earned hint is ready." to R.string.runtime_hint_ready,
    "Watch an ad to reveal one safe move." to R.string.runtime_watch_ad_hint,
    "No ad available right now" to R.string.runtime_no_ad,
    "Watch an ad to skip this level and receive 10 coins." to R.string.runtime_watch_ad_skip,
    "Ad is loading" to R.string.runtime_ad_loading,
    "No ad reward was earned" to R.string.runtime_no_ad_reward,
    "This skip reward was already applied" to R.string.runtime_skip_already_applied,
    "Finish the ad to skip this level" to R.string.runtime_finish_ad_skip,
    "No certified Auto Journey board is available." to R.string.runtime_no_auto_board,
)

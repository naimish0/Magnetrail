package com.rameshta.magnetrail.analytics

sealed interface AnalyticsEvent {
    val name: String
    val parameters: Map<String, Any>

    data class LevelStart(
        val levelId: String,
        val pack: String,
        val difficulty: String,
        val origin: String,
    ) : AnalyticsEvent {
        override val name = "level_start"
        override val parameters = mapOf("level_id" to levelId, "pack" to pack, "difficulty" to difficulty, "origin" to origin)
    }

    data class LevelComplete(
        val levelId: String,
        val stars: Int,
        val actions: Int,
        val overloads: Int,
        val hints: Int,
        val durationBucket: String,
    ) : AnalyticsEvent {
        override val name = "level_complete"
        override val parameters = mapOf(
            "level_id" to levelId,
            "stars" to stars,
            "actions" to actions,
            "overloads" to overloads,
            "hints" to hints,
            "duration_bucket" to durationBucket,
        )
    }

    data class LevelRestart(val levelId: String, val attemptBucket: String) : AnalyticsEvent {
        override val name = "level_restart"
        override val parameters = mapOf("level_id" to levelId, "attempt_bucket" to attemptBucket)
    }

    data class LevelDeadlock(val levelId: String, val actionsBucket: String) : AnalyticsEvent {
        override val name = "level_deadlock"
        override val parameters = mapOf("level_id" to levelId, "actions_bucket" to actionsBucket)
    }

    data class HintCoinSpend(val balanceBucket: String) : AnalyticsEvent {
        override val name = "hint_coin_spend"
        override val parameters = mapOf("balance_bucket" to balanceBucket)
    }

    data class HintShown(val source: String) : AnalyticsEvent {
        override val name = "hint_shown"
        override val parameters = mapOf("source" to source)
    }

    data class DailyStart(val difficulty: String) : AnalyticsEvent {
        override val name = "daily_start"
        override val parameters = mapOf("difficulty" to difficulty)
    }

    data class DailyComplete(val difficulty: String, val stars: Int, val streakBucket: String) : AnalyticsEvent {
        override val name = "daily_complete"
        override val parameters = mapOf("difficulty" to difficulty, "stars" to stars, "streak_bucket" to streakBucket)
    }

    data class InfiniteStart(val difficulty: String, val fallback: Boolean) : AnalyticsEvent {
        override val name = "infinite_start"
        override val parameters = mapOf("difficulty" to difficulty, "fallback" to fallback)
    }

    data class InfiniteComplete(
        val difficulty: String,
        val actionsBucket: String,
        val overloadsBucket: String,
        val hintsBucket: String,
    ) : AnalyticsEvent {
        override val name = "infinite_complete"
        override val parameters = mapOf(
            "difficulty" to difficulty,
            "actions_bucket" to actionsBucket,
            "overloads_bucket" to overloadsBucket,
            "hints_bucket" to hintsBucket,
        )
    }

    data class RewardedOffer(val outcome: String) : AnalyticsEvent {
        override val name = "rewarded_offer"
        override val parameters = mapOf("outcome" to outcome)
    }

    data class RewardedLoadResult(val result: String) : AnalyticsEvent {
        override val name = "rewarded_load_result"
        override val parameters = mapOf("result" to result)
    }

    data object RewardedShow : AnalyticsEvent {
        override val name = "rewarded_show"
        override val parameters = emptyMap<String, Any>()
    }

    data object RewardedEarned : AnalyticsEvent {
        override val name = "rewarded_earned"
        override val parameters = emptyMap<String, Any>()
    }

    data class RewardedDismiss(val earned: Boolean) : AnalyticsEvent {
        override val name = "rewarded_dismiss"
        override val parameters = mapOf("earned" to earned)
    }

    data class RewardedSkip(val outcome: String, val mode: String) : AnalyticsEvent {
        override val name = "rewarded_skip"
        override val parameters = mapOf("outcome" to outcome, "mode" to mode)
    }

    data class InterstitialEligible(val reason: String, val outcome: String) : AnalyticsEvent {
        override val name = "interstitial_eligible"
        override val parameters = mapOf("reason" to reason, "outcome" to outcome)
    }

    data object InterstitialShow : AnalyticsEvent {
        override val name = "interstitial_show"
        override val parameters = emptyMap<String, Any>()
    }

    data object InterstitialDismiss : AnalyticsEvent {
        override val name = "interstitial_dismiss"
        override val parameters = emptyMap<String, Any>()
    }

    data class AdShowFailure(val format: String, val category: String) : AnalyticsEvent {
        override val name = "ad_show_failure"
        override val parameters = mapOf("format" to format, "category" to category)
    }

    data class ConsentFlowResult(val state: String) : AnalyticsEvent {
        override val name = "consent_flow_result"
        override val parameters = mapOf("state" to state)
    }

    data object PrivacyOptionsOpen : AnalyticsEvent {
        override val name = "privacy_options_open"
        override val parameters = emptyMap<String, Any>()
    }

    data class DiagnosticsSettingChanged(val enabled: Boolean) : AnalyticsEvent {
        override val name = "diagnostics_setting_changed"
        override val parameters = mapOf("enabled" to enabled)
    }
}

object AnalyticsBuckets {
    fun count(value: Int): String = when {
        value <= 0 -> "0"
        value <= 2 -> "1_2"
        value <= 5 -> "3_5"
        value <= 10 -> "6_10"
        else -> "11_plus"
    }

    fun duration(seconds: Long): String = when {
        seconds < 30 -> "under_30s"
        seconds < 60 -> "30_59s"
        seconds < 180 -> "1_2m"
        seconds < 600 -> "3_9m"
        else -> "10m_plus"
    }
}

object AnalyticsPrivacyGuard {
    private val forbiddenKeys = setOf(
        "name", "email", "phone", "location", "contacts", "free_text", "advertising_id",
        "firebase_installation_id", "consent_string", "date_of_birth", "user_id", "daily_seed",
        "local_date", "board_state",
    )

    fun requireSafe(event: AnalyticsEvent) {
        require(event.name.matches(Regex("[a-z][a-z0-9_]{0,39}"))) { "Invalid analytics event name" }
        require(event.parameters.keys.none { it in forbiddenKeys }) { "Forbidden analytics parameter" }
        require(event.parameters.values.none { it is String && it.length > 100 }) { "Analytics value is too long" }
    }
}

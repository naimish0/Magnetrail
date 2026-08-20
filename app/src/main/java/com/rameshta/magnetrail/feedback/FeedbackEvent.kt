package com.rameshta.magnetrail.feedback

import com.rameshta.magnetrail.data.PlayerSettings

enum class FeedbackEvent {
    SELECT,
    ARROW_TRAVEL,
    ARROW_EXIT,
    PULL_CAPTURE,
    PUSH_EXIT,
    POLARITY_FLIP,
    COLLISION,
    INVALID_MOVE,
    RESTART,
    BOARD_COMPLETION,
}

enum class SoundCue {
    SELECT,
    TRAVEL,
    EXIT,
    PULL_CAPTURE,
    PUSH_EXIT,
    POLARITY_FLIP,
    IMPACT,
    UNDO_RESTART,
    COMPLETION,
}

enum class HapticCue {
    TICK,
    CONFIRM,
    IMPACT,
    COMPLETION,
}

data class FeedbackCommands(
    val sound: SoundCue?,
    val haptic: HapticCue?,
)

object FeedbackPolicy {
    fun commandsFor(event: FeedbackEvent, settings: PlayerSettings): FeedbackCommands {
        val sound = when (event) {
            FeedbackEvent.SELECT -> SoundCue.SELECT
            FeedbackEvent.ARROW_TRAVEL -> SoundCue.TRAVEL
            FeedbackEvent.ARROW_EXIT -> SoundCue.EXIT
            FeedbackEvent.PULL_CAPTURE -> SoundCue.PULL_CAPTURE
            FeedbackEvent.PUSH_EXIT -> SoundCue.PUSH_EXIT
            FeedbackEvent.POLARITY_FLIP -> SoundCue.POLARITY_FLIP
            FeedbackEvent.COLLISION, FeedbackEvent.INVALID_MOVE -> SoundCue.IMPACT
            FeedbackEvent.RESTART -> SoundCue.UNDO_RESTART
            FeedbackEvent.BOARD_COMPLETION -> SoundCue.COMPLETION
        }.takeIf { settings.soundEnabled }
        val haptic = when (event) {
            FeedbackEvent.SELECT, FeedbackEvent.POLARITY_FLIP -> HapticCue.TICK
            FeedbackEvent.ARROW_TRAVEL -> null
            FeedbackEvent.ARROW_EXIT,
            FeedbackEvent.PULL_CAPTURE,
            FeedbackEvent.PUSH_EXIT,
            FeedbackEvent.RESTART,
            -> HapticCue.CONFIRM
            FeedbackEvent.COLLISION, FeedbackEvent.INVALID_MOVE -> HapticCue.IMPACT
            FeedbackEvent.BOARD_COMPLETION -> HapticCue.COMPLETION
        }.takeIf { settings.hapticsEnabled }
        return FeedbackCommands(sound = sound, haptic = haptic)
    }
}

package com.rameshta.magnetrail.feedback

import com.rameshta.magnetrail.data.PlayerSettings

class FeedbackController(
    private val soundController: SoundController,
    private val hapticController: HapticController,
    private val enabled: Boolean = true,
) : AutoCloseable {
    fun handle(event: FeedbackEvent, settings: PlayerSettings) {
        if (!enabled) return
        val commands = FeedbackPolicy.commandsFor(event, settings)
        commands.sound?.let(soundController::play)
        commands.haptic?.let(hapticController::perform)
    }

    override fun close() {
        soundController.close()
    }
}

fun interface HapticController {
    fun perform(cue: HapticCue)
}

interface SoundController : AutoCloseable {
    fun play(cue: SoundCue)

    override fun close()
}

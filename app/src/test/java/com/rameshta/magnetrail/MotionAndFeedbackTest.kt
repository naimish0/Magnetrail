package com.rameshta.magnetrail

import com.rameshta.magnetrail.data.PlayerSettings
import com.rameshta.magnetrail.feedback.FeedbackController
import com.rameshta.magnetrail.feedback.FeedbackEvent
import com.rameshta.magnetrail.feedback.FeedbackPolicy
import com.rameshta.magnetrail.feedback.HapticController
import com.rameshta.magnetrail.feedback.HapticCue
import com.rameshta.magnetrail.feedback.SoundController
import com.rameshta.magnetrail.feedback.SoundCue
import com.rameshta.magnetrail.game.MotionPolicy
import com.rameshta.magnetrail.game.GameAction
import com.rameshta.magnetrail.game.GameViewModel
import com.rameshta.magnetrail.data.SettingKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionAndFeedbackTest {
    @Test
    fun `motion policy selects normal and reduced behavior centrally`() {
        val normal = MotionPolicy.from(reducedMotion = false)
        val reduced = MotionPolicy.from(reducedMotion = true)

        assertFalse(normal.reduced)
        assertTrue(normal.animateFieldPulse)
        assertTrue(normal.showCelebrationParticles)
        assertTrue(reduced.reduced)
        assertFalse(reduced.animateFieldPulse)
        assertFalse(reduced.showLongTrails)
        assertTrue(reduced.maxRouteMillis < normal.maxRouteMillis)
    }

    @Test
    fun `reduced motion setting cannot change an engine result`() {
        val normal = GameViewModel(prototypeCatalog())
        val reduced = GameViewModel(prototypeCatalog())
        reduced.onAction(GameAction.UpdateSetting(SettingKey.REDUCED_MOTION, true))

        normal.onAction(GameAction.LaunchArrow("A"))
        reduced.onAction(GameAction.LaunchArrow("A"))
        val normalResult = normal.uiState.value.inFlightResult
        val reducedResult = reduced.uiState.value.inFlightResult
        normal.onAction(GameAction.AnimationCompleted)
        reduced.onAction(GameAction.AnimationCompleted)

        assertEquals(normalResult, reducedResult)
        assertEquals(normal.uiState.value.boardState, reduced.uiState.value.boardState)
    }

    @Test
    fun `semantic event maps to each enabled gateway exactly once`() {
        val sound = RecordingSoundController()
        val haptics = RecordingHapticController()
        val controller = FeedbackController(sound, haptics)

        controller.handle(FeedbackEvent.PULL_CAPTURE, PlayerSettings())

        assertEquals(listOf(SoundCue.PULL_CAPTURE), sound.cues)
        assertEquals(listOf(HapticCue.CONFIRM), haptics.cues)
    }

    @Test
    fun `disabled sound and haptics suppress output`() {
        val commands = FeedbackPolicy.commandsFor(
            FeedbackEvent.BOARD_COMPLETION,
            PlayerSettings(soundEnabled = false, hapticsEnabled = false),
        )
        assertNull(commands.sound)
        assertNull(commands.haptic)

        val sound = RecordingSoundController()
        val haptics = RecordingHapticController()
        FeedbackController(sound, haptics).handle(
            FeedbackEvent.COLLISION,
            PlayerSettings(soundEnabled = false, hapticsEnabled = false),
        )
        assertTrue(sound.cues.isEmpty())
        assertTrue(haptics.cues.isEmpty())
    }

    private class RecordingSoundController : SoundController {
        val cues = mutableListOf<SoundCue>()
        override fun play(cue: SoundCue) { cues += cue }
        override fun close() = Unit
    }

    private class RecordingHapticController : HapticController {
        val cues = mutableListOf<HapticCue>()
        override fun perform(cue: HapticCue) { cues += cue }
    }
}

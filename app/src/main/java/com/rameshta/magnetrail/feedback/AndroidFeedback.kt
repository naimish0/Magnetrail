package com.rameshta.magnetrail.feedback

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

class ViewHapticController(private val view: View) : HapticController {
    override fun perform(cue: HapticCue) {
        val constant = when (cue) {
            HapticCue.TICK -> HapticFeedbackConstants.CLOCK_TICK
            HapticCue.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            }
            HapticCue.IMPACT -> if (Build.VERSION.SDK_INT >= 30) {
                HapticFeedbackConstants.REJECT
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
            HapticCue.COMPLETION -> HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}

/** Original, locally defined PCM cues. No downloaded or third-party audio assets are used. */
class SynthSoundController : SoundController {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    @Volatile
    private var closed = false
    @Volatile
    private var currentTrack: AudioTrack? = null

    override fun play(cue: SoundCue) {
        if (closed) return
        val spec = cue.spec
        executor.execute {
            if (closed) return@execute
            releaseCurrent()
            val samples = synthesize(spec)
            val track = AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
                samples.size * Short.SIZE_BYTES,
                AudioTrack.MODE_STATIC,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
            track.write(samples, 0, samples.size)
            currentTrack = track
            track.play()
            runCatching {
                executor.schedule(
                    { if (currentTrack === track) releaseCurrent() else track.release() },
                    spec.durationMillis + 40L,
                    TimeUnit.MILLISECONDS,
                )
            }.onFailure { releaseCurrent() }
        }
    }

    override fun close() {
        closed = true
        executor.execute(::releaseCurrent)
        executor.shutdown()
    }

    private fun releaseCurrent() {
        currentTrack?.let { track ->
            runCatching { track.stop() }
            track.release()
        }
        currentTrack = null
    }

    private fun synthesize(spec: ToneSpec): ShortArray {
        val frameCount = (SAMPLE_RATE * spec.durationMillis / 1_000).coerceAtLeast(1).toInt()
        return ShortArray(frameCount) { index ->
            val progress = index.toDouble() / frameCount
            val envelope = sin(PI * progress).coerceAtLeast(0.0) * spec.gain
            val frequency = spec.startHz + (spec.endHz - spec.startHz) * progress
            val harmonic = if (spec.harmonicHz == null) 0.0 else {
                sin(2.0 * PI * spec.harmonicHz * index / SAMPLE_RATE) * 0.34
            }
            val sample = (sin(2.0 * PI * frequency * index / SAMPLE_RATE) + harmonic) * envelope
            (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private data class ToneSpec(
        val startHz: Double,
        val endHz: Double,
        val durationMillis: Long,
        val gain: Double,
        val harmonicHz: Double? = null,
    )

    private val SoundCue.spec: ToneSpec
        get() = when (this) {
            SoundCue.SELECT -> ToneSpec(460.0, 520.0, 38, 0.10)
            SoundCue.TRAVEL -> ToneSpec(520.0, 610.0, 72, 0.08)
            SoundCue.EXIT -> ToneSpec(610.0, 880.0, 105, 0.12)
            SoundCue.PULL_CAPTURE -> ToneSpec(520.0, 260.0, 125, 0.14)
            SoundCue.PUSH_EXIT -> ToneSpec(430.0, 920.0, 130, 0.14)
            SoundCue.POLARITY_FLIP -> ToneSpec(390.0, 680.0, 150, 0.12, harmonicHz = 780.0)
            SoundCue.IMPACT -> ToneSpec(150.0, 95.0, 105, 0.18)
            SoundCue.UNDO_RESTART -> ToneSpec(410.0, 330.0, 85, 0.10)
            SoundCue.COMPLETION -> ToneSpec(520.0, 780.0, 230, 0.15, harmonicHz = 1_040.0)
        }

    private companion object {
        const val SAMPLE_RATE = 22_050
    }
}

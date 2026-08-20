package com.rameshta.magnetrail.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rameshta.magnetrail.ui.theme.MagnetrailMuted
import com.rameshta.magnetrail.ui.theme.MagnetrailPull
import com.rameshta.magnetrail.ui.theme.MagnetrailPush

@Composable
internal fun TutorialCoachCard(
    lesson: TutorialLesson,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.semantics {
            contentDescription = "Tutorial ${lesson.number} of $TUTORIAL_LEVEL_COUNT. ${lesson.title}. " +
                "${lesson.message} ${lesson.prompt}"
        },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TutorialGlyph(lesson.animation, reducedMotion)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "LEARN ${lesson.number} OF $TUTORIAL_LEVEL_COUNT · ${lesson.title.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MagnetrailPull,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    lesson.message,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MagnetrailMuted,
                )
                Text(
                    lesson.prompt,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TutorialGlyph(kind: TutorialAnimationKind, reducedMotion: Boolean) {
    val phase = if (reducedMotion) {
        0.55f
    } else {
        val transition = rememberInfiniteTransition(label = "tutorial animation")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_400), RepeatMode.Restart),
            label = "tutorial phase",
        )
        animated
    }
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = Modifier.size(54.dp)) {
        // Work in a 48 x 48 logical canvas so the glyph fills the same visual area at every density.
        val unit = size.minDimension / 48f
        fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
        fun pixels(value: Float) = value * unit
        val center = point(24f, 24f)
        when (kind) {
            TutorialAnimationKind.TAP -> {
                drawLine(primary, point(8f, 24f), point(35f, 24f), pixels(5f), StrokeCap.Round)
                drawLine(primary, point(35f, 24f), point(27f, 17f), pixels(5f), StrokeCap.Round)
                drawLine(primary, point(35f, 24f), point(27f, 31f), pixels(5f), StrokeCap.Round)
                drawCircle(
                    MagnetrailPull.copy(alpha = 1f - phase),
                    pixels(5f + phase * 12f),
                    point(16f, 24f),
                    style = Stroke(pixels(3f)),
                )
            }
            TutorialAnimationKind.BLOCK -> {
                val x = 7f + phase * 22f
                drawCircle(primary, pixels(5f), point(x, 24f))
                drawLine(MagnetrailPush, point(34f, 10f), point(34f, 38f), pixels(7f), StrokeCap.Round)
                drawCircle(
                    MagnetrailPush.copy(alpha = 1f - phase),
                    pixels(6f + phase * 6f),
                    point(34f, 24f),
                    style = Stroke(pixels(2f)),
                )
            }
            TutorialAnimationKind.MAGNET -> {
                drawCircle(MagnetrailPull, pixels(10f), center, style = Stroke(pixels(5f)))
                val distance = pixels(18f - phase * 8f)
                drawCircle(primary, pixels(4.5f), Offset(center.x - distance, center.y))
                drawCircle(primary, pixels(4.5f), Offset(center.x + distance, center.y))
                drawCircle(
                    MagnetrailPull.copy(alpha = 0.25f),
                    pixels(13f + phase * 6f),
                    center,
                    style = Stroke(pixels(2f)),
                )
            }
            TutorialAnimationKind.POLARITY -> {
                val color = if (phase < 0.5f) MagnetrailPull else MagnetrailPush
                drawCircle(surface, pixels(12f), center)
                drawCircle(color, pixels(12f), center, style = Stroke(pixels(5f)))
                drawArc(
                    color,
                    phase * 360f,
                    110f,
                    false,
                    point(5f, 5f),
                    androidx.compose.ui.geometry.Size(pixels(38f), pixels(38f)),
                    style = Stroke(pixels(3f)),
                )
            }
            TutorialAnimationKind.ORDER -> {
                val active = (phase * 3f).toInt().coerceIn(0, 2)
                repeat(3) { index ->
                    val x = 10f + index * 14f
                    if (index < 2) {
                        drawLine(
                            MagnetrailMuted,
                            point(x + 5f, 24f),
                            point(x + 9f, 24f),
                            pixels(2f),
                        )
                    }
                    drawCircle(
                        if (index == active) MagnetrailPull else primary,
                        pixels(if (index == active) 6f else 4.5f),
                        point(x, 24f),
                    )
                }
            }
            TutorialAnimationKind.SCAN -> {
                repeat(3) { index ->
                    val coordinate = 10f + index * 14f
                    drawLine(MagnetrailMuted, point(7f, coordinate), point(41f, coordinate), pixels(1.5f))
                    drawLine(MagnetrailMuted, point(coordinate, 7f), point(coordinate, 41f), pixels(1.5f))
                }
                val scanX = 7f + phase * 34f
                drawLine(MagnetrailPull, point(scanX, 6f), point(scanX, 42f), pixels(3f), StrokeCap.Round)
            }
            TutorialAnimationKind.VISIBILITY -> {
                drawCircle(MagnetrailPull, pixels(6f), point(8f, 24f))
                drawCircle(primary, pixels(5f), point(40f, 24f))
                drawLine(
                    MagnetrailPull.copy(alpha = 0.30f + phase * 0.60f),
                    point(14f, 24f),
                    point(34f, 24f),
                    pixels(3f),
                    StrokeCap.Round,
                )
                drawRoundRect(
                    MagnetrailPush.copy(alpha = 1f - phase),
                    point(21f, 16f),
                    androidx.compose.ui.geometry.Size(pixels(6f), pixels(16f)),
                    androidx.compose.ui.geometry.CornerRadius(pixels(2f)),
                )
            }
            TutorialAnimationKind.REVEAL -> {
                drawCircle(primary.copy(alpha = 0.35f + phase * 0.65f), pixels(7f), point(34f, 24f))
                val blockerX = 24f - phase * 12f
                drawRoundRect(
                    MagnetrailPush,
                    point(blockerX - 5f, 17f),
                    androidx.compose.ui.geometry.Size(pixels(10f), pixels(14f)),
                    androidx.compose.ui.geometry.CornerRadius(pixels(3f)),
                )
                drawLine(MagnetrailPull, point(7f, 24f), point(28f, 24f), pixels(3f), StrokeCap.Round)
            }
            TutorialAnimationKind.CHOICE -> {
                drawCircle(primary, pixels(5f), point(9f, 24f))
                drawLine(MagnetrailMuted, point(14f, 24f), point(28f, 13f), pixels(3f), StrokeCap.Round)
                drawLine(MagnetrailPull, point(14f, 24f), point(28f, 35f), pixels(4f), StrokeCap.Round)
                drawCircle(MagnetrailMuted, pixels(5f), point(35f, 11f))
                drawCircle(MagnetrailPull, pixels(6f + phase * 2f), point(35f, 37f))
            }
            TutorialAnimationKind.MASTERY -> {
                repeat(3) { index ->
                    val y = 12f + index * 12f
                    val active = phase * 3f >= index
                    val color = if (active) MagnetrailPull else MagnetrailMuted
                    drawCircle(color, pixels(4f), point(10f, y))
                    drawLine(color, point(18f, y), point(39f, y), pixels(3f), StrokeCap.Round)
                }
            }
        }
    }
}

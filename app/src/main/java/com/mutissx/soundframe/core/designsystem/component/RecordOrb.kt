package com.mutissx.soundframe.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.designsystem.theme.LocalReduceMotion
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * The signature record control. Idle: a breathing luminous orb. Recording: the core morphs into a
 * stop square, rings ripple with the live level and a gradient arc traces the 10-second budget.
 */
@Composable
fun RecordOrb(
    recording: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    level: Float = 0f,
    progress: Float = 0f,
    size: Dp = 116.dp,
    enabled: Boolean = true,
) {
    val description = stringResource(if (recording) R.string.cd_stop_recording else R.string.cd_start_recording)
    val reduceMotion = LocalReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val morph by animateFloatAsState(if (recording) 1f else 0f, SfMotion.gentle(), label = "morph")
    val smoothLevel by animateFloatAsState(level, tween(110), label = "level")
    val shownProgress by animateFloatAsState(progress, tween(120, easing = LinearEasing), label = "progress")
    val breath = rememberInfiniteTransition(label = "breath").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "breath",
    )


    Canvas(
        modifier
            .size(size * 1.7f)
            .pressFeedback(interaction, 0.93f)
            .semantics { contentDescription = description }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        val c = center
        val r = size.toPx() / 2f
        val phase = if (reduceMotion) 0f else breath.value
        val wave = sin(phase * 2f * PI.toFloat())

        // Ambient halo.
        val haloRadius = r * (1.45f + 0.06f * wave + 0.35f * smoothLevel * morph)
        drawCircle(
            Brush.radialGradient(
                listOf(SfColors.Rose.copy(alpha = 0.32f + 0.25f * smoothLevel * morph), SfColors.Violet.copy(alpha = 0.10f), Color.Transparent),
                center = c, radius = haloRadius,
            ),
            radius = haloRadius, center = c,
        )

        // Ripples: idle they drift outward slowly; recording they follow the level.
        for (i in 0 until 3) {
            val f = (phase + i / 3f) % 1f
            val idleRadius = r * (1.0f + 0.45f * f)
            val liveRadius = r * (1.05f + 0.12f * i + 0.5f * smoothLevel * (1f - i * 0.2f))
            val radius = idleRadius + (liveRadius - idleRadius) * morph
            val alpha = (1f - f) * 0.35f * (1f - morph) + (0.4f - i * 0.1f) * morph
            drawCircle(
                Brush.sweepGradient(SfColors.RecordGradient + SfColors.RecordGradient.first(), c),
                radius = radius, center = c, alpha = alpha.coerceIn(0f, 1f), style = Stroke(1.5.dp.toPx()),
            )
        }

        // Budget arc.
        if (morph > 0.01f) {
            val arcRadius = r * 1.18f
            drawCircle(Color.White.copy(alpha = 0.08f * morph), arcRadius, c, style = Stroke(3.dp.toPx()))
            rotate(-90f, c) {
                drawArc(
                    Brush.sweepGradient(listOf(SfColors.Aurora, SfColors.Cyan, SfColors.Violet, SfColors.Rose, SfColors.Aurora), c),
                    startAngle = 0f, sweepAngle = 360f * shownProgress, useCenter = false,
                    topLeft = Offset(c.x - arcRadius, c.y - arcRadius), size = Size(arcRadius * 2, arcRadius * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round), alpha = morph,
                )
            }
        }

        // Core.
        val coreRadius = r * (0.78f + 0.03f * wave * (1f - morph) + 0.06f * smoothLevel * morph)
        drawCircle(
            Brush.linearGradient(SfColors.RecordGradient, start = Offset(c.x - coreRadius, c.y - coreRadius), end = Offset(c.x + coreRadius, c.y + coreRadius)),
            radius = coreRadius, center = c,
        )
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent), center = Offset(c.x - coreRadius * 0.35f, c.y - coreRadius * 0.45f), radius = coreRadius * 0.9f),
            radius = coreRadius, center = c,
        )

        // Glyph: a soft dot that becomes a rounded stop square.
        val glyph = r * (0.26f + 0.06f * morph)
        val corner = glyph * (1f - 0.72f * morph)
        drawRoundRect(
            Color.White.copy(alpha = 0.95f),
            topLeft = Offset(c.x - glyph, c.y - glyph),
            size = Size(glyph * 2, glyph * 2),
            cornerRadius = CornerRadius(corner, corner),
        )
    }
}

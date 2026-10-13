package com.mutissx.soundframe.feature.generation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.common.Format
import com.mutissx.soundframe.core.designsystem.art.ArtworkCanvas
import com.mutissx.soundframe.core.designsystem.art.rememberArtClock
import com.mutissx.soundframe.core.designsystem.art.saturation
import com.mutissx.soundframe.core.designsystem.component.AmbientBackground
import com.mutissx.soundframe.core.designsystem.component.GhostButton
import com.mutissx.soundframe.core.designsystem.component.LuminousButton
import com.mutissx.soundframe.core.designsystem.component.Overline
import com.mutissx.soundframe.core.designsystem.component.StatBlock
import com.mutissx.soundframe.core.designsystem.theme.LocalReduceMotion
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfMotion
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SfSpacing
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.core.graphics.Rng
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.feature.common.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The transformation moment. Driven by real progress: the waveform contracts and bursts into
 * light, the sequence holds in an orbit until analysis has truly finished, then the artwork
 * resolves from grey geometry into colour. No fake percentages anywhere.
 */
@Composable
fun GenerationScreen(
    viewModel: GenerationViewModel,
    onReady: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentState by rememberUpdatedState(state)
    val reduceMotion = LocalReduceMotion.current
    val contract = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val reveal = remember { Animatable(0f) }
    val color = remember { Animatable(0f) }
    var artPrepared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val speed = if (reduceMotion) 0.4f else 1f
        contract.animateTo(1f, tween((1000 * speed).toInt(), easing = SfMotion.EaseInOut))
        burst.animateTo(1f, tween((1100 * speed).toInt(), easing = SfMotion.EaseOutExpo))
        // Hold the orbit until the artwork is fully prepared, so the reveal never animates an empty
        // frame or competes with preparation for the CPU.
        snapshotFlow { currentState.isReady && artPrepared }.first { it }
        launch { burst.animateTo(0f, tween(900)) }
        reveal.animateTo(1f, tween((1400 * speed).toInt(), easing = SfMotion.EaseOutExpo))
        color.animateTo(1f, tween((1100 * speed).toInt(), easing = SfMotion.EaseInOut))
        delay((1600 * speed).toLong())
        currentState.sessionId?.let(onReady)
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(energy = { burst.value * 0.6f + reveal.value * 0.3f })
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(SfSpacing.screen),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(SfSpacing.xl))
            Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
                TransformationField(state.waveform, { contract.value }, { burst.value }, Modifier.fillMaxSize())
                val features = state.features
                val settings = state.settings
                if (features != null && settings != null) {
                    ArtworkCanvas(
                        features, settings,
                        Modifier
                            .fillMaxSize(0.92f)
                            .graphicsLayer {
                                alpha = reveal.value
                                val s = 0.82f + 0.18f * reveal.value
                                scaleX = s
                                scaleY = s
                            }
                            .saturation { color.value }
                            .clip(SfShapes.artwork)
                            .border(1.dp, Color(0x2EFFFFFF), SfShapes.artwork),
                        contentDescription = stringResource(R.string.generation_artwork_description),
                        fadeIn = false,
                        onPrepared = { artPrepared = true },
                    )
                }
            }
            Spacer(Modifier.height(SfSpacing.xl))
            if (state.error != null) {
                FailureContent(state.error!!, onRetry = viewModel::retry, onBack = onBack)
            } else {
                Captions(state, revealed = color.value > 0.5f)
            }
        }
    }
}

@Composable
private fun Captions(state: GenerationUiState, revealed: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        if (!revealed) {
            Overline(stringResource(if (state.features == null) R.string.generation_analysing else R.string.generation_sculpting), dot = true)
            Spacer(Modifier.height(SfSpacing.sm))
            Text(
                stringResource(R.string.generation_listening),
                style = SoundframeTheme.type.display, color = SfColors.TextPrimary, textAlign = TextAlign.Center,
            )
        } else {
            Overline(state.settings?.style?.let { stringResource(it.label) }.orEmpty(), color = SfColors.TextSecondary)
            Spacer(Modifier.height(SfSpacing.sm))
            Text(state.title, style = SoundframeTheme.type.display, color = SfColors.TextPrimary, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(SfSpacing.lg))
        AnimatedVisibility(state.features != null, enter = fadeIn(tween(700)) + slideInVertically { it / 2 }) {
            state.features?.let { FeatureFacts(it) }
        }
    }
}

/** Real measurements from the analysis — the user sees what shaped their artwork. */
@Composable
private fun FeatureFacts(features: AudioFeatures) {
    Row(horizontalArrangement = Arrangement.spacedBy(SfSpacing.xs)) {
        StatBlock(Format.hertz(features.spectralCentroid), stringResource(R.string.stat_brightness), Modifier.weight(1f))
        StatBlock(Format.rate(features.transientDensity), stringResource(R.string.stat_onsets), Modifier.weight(1f))
        StatBlock(Format.decibels(features.dynamicRangeDb), stringResource(R.string.stat_dynamics), Modifier.weight(1f))
    }
}

@Composable
private fun FailureContent(@StringRes message: Int, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.generation_failed_title), style = SoundframeTheme.type.display, color = SfColors.TextPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(SfSpacing.sm))
        Text(stringResource(message), style = SoundframeTheme.type.body, color = SfColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(SfSpacing.lg))
        LuminousButton(stringResource(R.string.action_try_again), onRetry, Modifier.fillMaxWidth())
        Spacer(Modifier.height(SfSpacing.sm))
        GhostButton(stringResource(R.string.action_go_back), onBack, Modifier.fillMaxWidth())
    }
}

/** Waveform bars collapse into a point of light, which bursts into an orbiting particle field. */
@Composable
private fun TransformationField(waveform: List<Float>, contract: () -> Float, burst: () -> Float, modifier: Modifier) {
    val clock = rememberArtClock(running = !LocalReduceMotion.current)
    val particles = remember {
        val rng = Rng(7)
        FloatArray(160 * 3) { i -> if (i % 3 == 0) rng.range(0f, 6.283f) else rng.range(0.25f, 1f) }
    }
    val colors = remember { listOf(SfColors.Aurora, SfColors.Cyan, SfColors.Violet, SfColors.Rose) }
    Canvas(modifier) {
        val k = contract()
        val b = burst()
        val t = clock.value
        val c = center
        val bars = waveform.ifEmpty { List(56) { 0.15f + 0.1f * sin(it * 0.6f) } }
        val spacing = size.width * 0.86f / bars.size
        if (k < 1f) {
            for ((i, v) in bars.withIndex()) {
                val x = c.x + (i - bars.size / 2f) * spacing * (1f - k)
                val h = (size.height * 0.28f * (0.08f + v)) * (1f - k * 0.92f)
                drawLine(
                    Brush.verticalGradient(listOf(colors[i % 4], colors[(i + 1) % 4]), c.y - h, c.y + h),
                    Offset(x, c.y - h), Offset(x, c.y + h),
                    strokeWidth = (spacing * 0.5f).coerceAtLeast(2f), cap = StrokeCap.Round, alpha = 1f - k * 0.5f,
                )
            }
        }
        val core = size.minDimension * (0.015f + 0.05f * k * (1f - b * 0.5f))
        drawCircle(Brush.radialGradient(listOf(Color.White, SfColors.Violet.copy(alpha = 0.4f), Color.Transparent), c, core * 4f), core * 4f, c, alpha = k)
        if (b > 0.001f) {
            val reach = size.minDimension * 0.45f
            for (p in 0 until particles.size / 3) {
                val angle = particles[p * 3] + t * 0.25f * particles[p * 3 + 2]
                val r = reach * b * particles[p * 3 + 1] * (0.92f + 0.08f * sin(t * 2f + p))
                drawCircle(
                    colors[p % 4],
                    radius = (1.2f + 2.2f * particles[p * 3 + 2]) * density / 2f,
                    center = Offset(c.x + cos(angle) * r, c.y + sin(angle) * r),
                    alpha = (0.35f + 0.65f * particles[p * 3 + 2]) * b,
                )
            }
        }
    }
}

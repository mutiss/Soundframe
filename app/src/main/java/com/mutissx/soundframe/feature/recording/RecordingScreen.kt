package com.mutissx.soundframe.feature.recording

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.common.ShareHelper
import com.mutissx.soundframe.core.designsystem.art.rememberArtClock
import com.mutissx.soundframe.core.designsystem.component.AmbientBackground
import com.mutissx.soundframe.core.designsystem.component.GlassSurface
import com.mutissx.soundframe.core.designsystem.component.LuminousButton
import com.mutissx.soundframe.core.designsystem.component.Overline
import com.mutissx.soundframe.core.designsystem.component.RecordOrb
import com.mutissx.soundframe.core.designsystem.component.SfTopBar
import com.mutissx.soundframe.core.designsystem.component.TextAction
import com.mutissx.soundframe.core.designsystem.icon.SfIcons
import com.mutissx.soundframe.core.designsystem.theme.LocalReduceMotion
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SfSpacing
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.core.graphics.Rng
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RecordingScreen(
    autoStart: Boolean,
    onBack: () -> Unit,
    onRecorded: (String) -> Unit,
    onImport: (String) -> Unit,
    viewModel: RecordingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val haptics = LocalHapticFeedback.current

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val canAskAgain = activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) ?: true
        viewModel.onAction(RecordingAction.PermissionResult(granted, canAskAgain))
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it.toString()) }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        viewModel.onEntered(granted, autoStart)
    }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RecordingEffect.RequestPermission -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                is RecordingEffect.OpenGeneration -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRecorded(effect.sessionId)
                }
            }
        }
    }
    // Never keep the microphone open in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onAction(RecordingAction.Backgrounded) }

    RecordingContent(
        state = state,
        onBack = onBack,
        onToggle = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            viewModel.onAction(RecordingAction.ToggleRecording)
        },
        onDismissError = { viewModel.onAction(RecordingAction.DismissError) },
        onOpenSettings = { ShareHelper.openAppSettings(context) },
        onImport = { importer.launch(arrayOf("audio/*")) },
    )
}

@Composable
private fun RecordingContent(
    state: RecordingUiState,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onDismissError: () -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
) {
    val recording = state.phase == RecordingPhase.RECORDING
    val energy by animateFloatAsState(if (recording) state.level else 0f, tween(140), label = "energy")
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(
            colors = if (recording) listOf(SfColors.Rose, SfColors.Violet, SfColors.Solar) else listOf(SfColors.Violet, SfColors.Cyan, SfColors.Rose),
            energy = { energy },
        )
        Column(Modifier.fillMaxSize()) {
            SfTopBar(title = null, onBack = onBack, trailing = { StatusPill(recording) })
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LiveSoundVisual(
                    history = state.history,
                    level = energy,
                    active = recording,
                    modifier = Modifier.fillMaxWidth(0.92f).aspectRatio(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        String.format(Locale.US, "%04.1f", state.elapsedMs / 1000f),
                        style = SoundframeTheme.type.numeric,
                        color = SfColors.TextPrimary,
                    )
                    Text(stringResource(R.string.recording_of_ten), style = SoundframeTheme.type.caption, color = SfColors.TextSecondary)
                }
            }
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = SfSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContent(
                    targetState = bottomMessage(state),
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "hint",
                ) { message ->
                    Text(
                        stringResource(message),
                        style = SoundframeTheme.type.body,
                        color = if (state.silenced && recording) SfColors.Solar else SfColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = SfSpacing.xl)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Spacer(Modifier.height(SfSpacing.md))
                RecordOrb(
                    recording = recording,
                    onClick = onToggle,
                    level = state.level,
                    progress = state.progress,
                    enabled = state.permission != MicPermission.PERMANENTLY_DENIED,
                )
                AnimatedVisibility(!recording) {
                    TextAction(stringResource(R.string.recording_import), onImport)
                }
            }
        }

        AnimatedVisibility(
            visible = state.permission == MicPermission.DENIED || state.permission == MicPermission.PERMANENTLY_DENIED || state.phase == RecordingPhase.ERROR,
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            when {
                state.permission == MicPermission.PERMANENTLY_DENIED -> NoticeCard(
                    title = R.string.recording_blocked_title,
                    body = R.string.recording_blocked_body,
                    action = R.string.recording_open_settings,
                    onAction = onOpenSettings,
                )
                state.permission == MicPermission.DENIED && state.phase != RecordingPhase.ERROR -> NoticeCard(
                    title = R.string.recording_rationale_title,
                    body = R.string.recording_rationale_body,
                    action = R.string.recording_allow,
                    onAction = onToggle,
                )
                else -> NoticeCard(
                    title = R.string.recording_error_title,
                    body = state.errorRes ?: R.string.error_generic,
                    action = R.string.action_retry,
                    onAction = { onDismissError(); onToggle() },
                )
            }
        }
    }
}

@StringRes
private fun bottomMessage(state: RecordingUiState): Int = when {
    state.phase == RecordingPhase.RECORDING && state.silenced -> R.string.recording_hint_silenced
    state.phase == RecordingPhase.RECORDING -> R.string.recording_hint_active
    else -> R.string.recording_hint_idle
}

@Composable
private fun StatusPill(recording: Boolean) {
    GlassSurface(shape = SfShapes.pill) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            val clock = rememberArtClock(running = recording && !LocalReduceMotion.current)
            Canvas(Modifier.size(8.dp)) {
                val pulse = if (recording) 0.5f + 0.5f * sin(clock.value * 6f) else 1f
                drawCircle(if (recording) SfColors.Rose.copy(alpha = 0.4f + 0.6f * pulse) else SfColors.Aurora)
            }
            Spacer(Modifier.width(8.dp))
            Overline(stringResource(if (recording) R.string.recording_status_recording else R.string.recording_status_ready), color = SfColors.TextPrimary)
        }
    }
}

@Composable
private fun NoticeCard(@StringRes title: Int, @StringRes body: Int, @StringRes action: Int, onAction: () -> Unit) {
    GlassSurface(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(SfSpacing.md),
        strong = true,
    ) {
        Column(Modifier.padding(SfSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(SfIcons.Mic, contentDescription = null, tint = SfColors.Aurora, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(title), style = SoundframeTheme.type.title, color = SfColors.TextPrimary)
            }
            Spacer(Modifier.height(SfSpacing.xs))
            Text(stringResource(body), style = SoundframeTheme.type.bodySmall, color = SfColors.TextSecondary)
            Spacer(Modifier.height(SfSpacing.md))
            LuminousButton(stringResource(action), onAction, Modifier.fillMaxWidth(), height = 52.dp)
        }
    }
}

/**
 * Live audio-reactive visual: the recent loudness history wraps around a luminous ring as radial
 * filaments, while a field of motes breathes with the current level.
 */
@Composable
private fun LiveSoundVisual(history: List<Float>, level: Float, active: Boolean, modifier: Modifier = Modifier) {
    val clock = rememberArtClock(running = !LocalReduceMotion.current)
    val motes = remember {
        val rng = Rng(42)
        FloatArray(90 * 3) { i -> if (i % 3 == 2) rng.range(0.4f, 1.6f) else rng.nextFloat() }
    }
    Canvas(modifier) {
        val t = clock.value
        val c = center
        val base = size.minDimension * 0.27f
        val ringColors = listOf(SfColors.Aurora, SfColors.Cyan, SfColors.Violet, SfColors.Rose, SfColors.Aurora)

        drawCircle(
            Brush.radialGradient(listOf(SfColors.Violet.copy(alpha = 0.18f + 0.4f * level), Color.Transparent), c, base * (1.6f + level)),
            base * (1.6f + level), c,
        )

        for (m in 0 until motes.size / 3) {
            val angle = motes[m * 3] * 2f * PI.toFloat() + t * 0.05f * motes[m * 3 + 2]
            val radius = base * (1.15f + motes[m * 3 + 1] * 0.75f) * (1f + 0.25f * level * motes[m * 3 + 2])
            drawCircle(
                ringColors[m % 4].copy(alpha = 0.25f + 0.5f * level),
                radius = 1.5.dp.toPx() * motes[m * 3 + 2],
                center = Offset(c.x + cos(angle) * radius, c.y + sin(angle) * radius),
            )
        }

        val filaments = 96
        val stroke = 2.4.dp.toPx()
        for (i in 0 until filaments) {
            val angle = i / filaments.toFloat() * 2f * PI.toFloat() - PI.toFloat() / 2f
            val sample = if (history.isEmpty()) 0f else history[(i * history.size / filaments).coerceAtMost(history.size - 1)]
            val idle = 0.04f + 0.03f * sin(t * 1.4f + i * 0.35f)
            val length = base * (if (active) 0.06f + sample * 0.7f else idle)
            val start = Offset(c.x + cos(angle) * base, c.y + sin(angle) * base)
            val end = Offset(c.x + cos(angle) * (base + length), c.y + sin(angle) * (base + length))
            drawLine(
                Brush.linearGradient(listOf(ringColors[(i * 4 / filaments) % 5], ringColors[(i * 4 / filaments + 1) % 5]), start, end),
                start, end, strokeWidth = stroke, cap = StrokeCap.Round,
            )
        }
        drawCircle(Brush.sweepGradient(ringColors, c), base, c, style = Stroke(1.5.dp.toPx()), alpha = 0.6f)
    }
}

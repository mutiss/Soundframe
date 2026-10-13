package com.mutissx.soundframe.feature.export

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.common.ShareHelper
import com.mutissx.soundframe.core.designsystem.art.ArtworkCanvas
import com.mutissx.soundframe.core.designsystem.component.AmbientBackground
import com.mutissx.soundframe.core.designsystem.component.ArtLoader
import com.mutissx.soundframe.core.designsystem.component.GhostButton
import com.mutissx.soundframe.core.designsystem.component.GlassSurface
import com.mutissx.soundframe.core.designsystem.component.LuminousButton
import com.mutissx.soundframe.core.designsystem.component.SegmentedTabs
import com.mutissx.soundframe.core.designsystem.component.SettingRow
import com.mutissx.soundframe.core.designsystem.component.SfSlider
import com.mutissx.soundframe.core.designsystem.component.SfSwitch
import com.mutissx.soundframe.core.designsystem.component.SfTopBar
import com.mutissx.soundframe.core.designsystem.component.pressFeedback
import com.mutissx.soundframe.core.designsystem.icon.SfIcons
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SfSpacing
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.domain.model.ExportImageSize
import com.mutissx.soundframe.domain.usecase.ExportVideoUseCase
import com.mutissx.soundframe.feature.common.label
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ExportScreen(viewModel: ExportViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var toast by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExportEffect.Share -> ShareHelper.share(context, effect.file)
                is ExportEffect.Message -> toast = effect.text
            }
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_400)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        Column(Modifier.fillMaxSize()) {
            SfTopBar(stringResource(R.string.export_title), onBack)
            val artwork = state.artwork
            if (artwork == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.error != null) Text(stringResource(state.error!!), style = SoundframeTheme.type.body, color = SfColors.TextSecondary)
                    else ArtLoader(Modifier.size(56.dp))
                }
                return@Column
            }

            val targetAspect = when {
                state.mode == ExportMode.VIDEO -> 9f / 16f
                else -> state.size.width.toFloat() / state.size.height
            }
            val aspect by animateFloatAsState(targetAspect, label = "aspect")
            Box(Modifier.weight(1f).fillMaxWidth().padding(SfSpacing.md), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .aspectRatio(aspect, matchHeightConstraintsFirst = true)
                        .clip(SfShapes.tile)
                        .border(1.dp, Color(0x2EFFFFFF), SfShapes.tile),
                ) {
                    ArtworkCanvas(
                        artwork.features,
                        artwork.settings,
                        Modifier.fillMaxSize(),
                        animate = state.mode == ExportMode.VIDEO && artwork.settings.animate,
                        signature = state.signature,
                        contentDescription = stringResource(R.string.export_preview),
                    )
                    val job = state.activeJob
                    if (job is ExportJob.Rendering) RenderOverlay(job.progress)
                }
            }

            GlassSurface(Modifier.fillMaxWidth().padding(horizontal = SfSpacing.sm).navigationBarsPadding(), strong = true) {
                Column(Modifier.padding(SfSpacing.md)) {
                    SegmentedTabs(
                        options = ExportMode.entries.filter { it != ExportMode.VIDEO || state.videoAvailable }.map { stringResource(it.label) },
                        selected = state.mode.ordinal,
                        onSelect = { viewModel.onAction(ExportAction.SetMode(ExportMode.entries[it])) },
                    )
                    Spacer(Modifier.height(SfSpacing.md))
                    AnimatedContent(state.mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mode") { mode ->
                        if (mode == ExportMode.IMAGE) ImageOptions(state, viewModel::onAction) else VideoOptions(state, viewModel::onAction)
                    }
                    SettingRow(stringResource(R.string.export_signature), stringResource(R.string.export_signature_body)) {
                        SfSwitch(state.signature, { viewModel.onAction(ExportAction.SetSignature(it)) })
                    }
                    Spacer(Modifier.height(SfSpacing.sm))
                    ExportActions(state, viewModel::onAction)
                }
            }
        }

        AnimatedVisibility(
            toast != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
        ) {
            GlassSurface(shape = SfShapes.pill, strong = true) {
                Text(toast?.let { stringResource(it) }.orEmpty(), style = SoundframeTheme.type.label, color = SfColors.TextPrimary, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun ImageOptions(state: ExportUiState, onAction: (ExportAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(SfSpacing.xs)) {
        ExportImageSize.entries.forEach { size ->
            SizeCard(size, size == state.size, Modifier.weight(1f)) { onAction(ExportAction.SetSize(size)) }
        }
    }
}

@Composable
private fun SizeCard(size: ExportImageSize, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .pressFeedback(interaction)
            .clip(SfShapes.small)
            .background(if (selected) Color(0x24FFFFFF) else Color(0x0DFFFFFF))
            .border(1.dp, if (selected) Color(0x66FFFFFF) else Color(0x14FFFFFF), SfShapes.small)
            .semantics { this.selected = selected }
            .clickable(interaction, indication = null, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val ratio = size.width.toFloat() / size.height
        Box(
            Modifier
                .height(28.dp)
                .aspectRatio(ratio)
                .clip(SfShapes.small)
                .background(if (selected) SfColors.SignatureBrush else Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF)))),
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(size.label), style = SoundframeTheme.type.label, color = if (selected) SfColors.TextPrimary else SfColors.TextSecondary)
        Text(stringResource(R.string.export_dimensions, size.width, size.height), style = SoundframeTheme.type.caption, color = SfColors.TextTertiary)
    }
}

@Composable
private fun VideoOptions(state: ExportUiState, onAction: (ExportAction) -> Unit) {
    Column {
        val range = (ExportVideoUseCase.MAX_SECONDS - ExportVideoUseCase.MIN_SECONDS).toFloat()
        SfSlider(
            value = (state.durationSec - ExportVideoUseCase.MIN_SECONDS) / range,
            onValueChange = { onAction(ExportAction.SetDuration((ExportVideoUseCase.MIN_SECONDS + it * range).roundToInt())) },
            label = stringResource(R.string.export_duration),
            valueText = stringResource(R.string.export_duration_value, state.durationSec),
        )
        SettingRow(
            stringResource(R.string.export_original_sound),
            stringResource(if (state.hasAudio) R.string.export_original_sound_body else R.string.export_original_sound_missing),
        ) {
            SfSwitch(state.includeAudio, { onAction(ExportAction.SetIncludeAudio(it)) }, enabled = state.hasAudio)
        }
    }
}

@Composable
private fun ExportActions(state: ExportUiState, onAction: (ExportAction) -> Unit) {
    AnimatedContent(state.activeJob, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it::class }, label = "actions") { job ->
        when (job) {
            is ExportJob.Done -> Row(horizontalArrangement = Arrangement.spacedBy(SfSpacing.xs)) {
                LuminousButton(stringResource(R.string.action_share), { onAction(ExportAction.Share) }, Modifier.weight(1f), icon = SfIcons.Share)
                GhostButton(stringResource(R.string.action_save), { onAction(ExportAction.SaveToDevice) }, Modifier.weight(1f).height(58.dp), icon = SfIcons.Download)
            }
            is ExportJob.Rendering -> GhostButton(
                stringResource(if (state.mode == ExportMode.VIDEO) R.string.export_cancel else R.string.export_rendering),
                { if (state.mode == ExportMode.VIDEO) onAction(ExportAction.Cancel) },
                Modifier.fillMaxWidth(),
                icon = SfIcons.Close,
                enabled = state.mode == ExportMode.VIDEO,
            )
            else -> Column {
                if (job is ExportJob.Failed) {
                    Text(stringResource(job.message), style = SoundframeTheme.type.bodySmall, color = SfColors.Danger, modifier = Modifier.padding(bottom = SfSpacing.xs))
                }
                LuminousButton(
                    if (state.mode == ExportMode.IMAGE) stringResource(R.string.export_render_png, stringResource(state.size.label)) else stringResource(R.string.export_render_video),
                    { onAction(ExportAction.Render) },
                    Modifier.fillMaxWidth(),
                    icon = if (state.mode == ExportMode.IMAGE) SfIcons.Image else SfIcons.Video,
                )
            }
        }
    }
}

/** Real progress from the encoder, drawn as a luminous ring — the only percentage in the app. */
@Composable
private fun RenderOverlay(progress: Float?) {
    Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        if (progress == null) {
            ArtLoader(Modifier.size(72.dp))
        } else {
            val shown by animateFloatAsState(progress, label = "render")
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(112.dp)) {
                    drawCircle(Color(0x1FFFFFFF), style = Stroke(4.dp.toPx()))
                    drawArc(
                        Brush.sweepGradient(SfColors.Signature + SfColors.Signature.first()),
                        -90f, 360f * shown, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(shown * 100).roundToInt()}%", style = SoundframeTheme.type.title, color = SfColors.TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(SfIcons.Video, contentDescription = null, tint = SfColors.TextSecondary, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.export_encoding), style = SoundframeTheme.type.caption, color = SfColors.TextSecondary)
                    }
                }
            }
        }
    }
}

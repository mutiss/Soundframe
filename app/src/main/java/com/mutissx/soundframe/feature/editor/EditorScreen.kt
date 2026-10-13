package com.mutissx.soundframe.feature.editor

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.designsystem.art.ArtworkCanvas
import com.mutissx.soundframe.core.designsystem.component.ArtLoader
import com.mutissx.soundframe.core.designsystem.component.GhostButton
import com.mutissx.soundframe.core.designsystem.component.GlassIconButton
import com.mutissx.soundframe.core.designsystem.component.GlassSurface
import com.mutissx.soundframe.core.designsystem.component.LuminousButton
import com.mutissx.soundframe.core.designsystem.component.PaletteSwatch
import com.mutissx.soundframe.core.designsystem.component.SegmentedTabs
import com.mutissx.soundframe.core.designsystem.component.SfDialog
import com.mutissx.soundframe.core.designsystem.component.SfSlider
import com.mutissx.soundframe.core.designsystem.component.SfSwitch
import com.mutissx.soundframe.core.designsystem.component.SfTextField
import com.mutissx.soundframe.core.designsystem.component.pressFeedback
import com.mutissx.soundframe.core.designsystem.icon.SfIcons
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfMotion
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SfSpacing
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.core.graphics.Backgrounds
import com.mutissx.soundframe.core.graphics.Palettes
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.BackgroundTone
import com.mutissx.soundframe.domain.model.PaletteId
import com.mutissx.soundframe.domain.usecase.TitleRules
import com.mutissx.soundframe.feature.common.label
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onOpenExport: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    var banner by remember { mutableStateOf<Int?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is EditorEffect.OpenExport -> onOpenExport(effect.artworkId)
                is EditorEffect.Message -> banner = effect.text
            }
        }
    }
    LaunchedEffect(state.savedPulse) {
        if (state.savedPulse > 0) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            banner = R.string.editor_saved
        }
    }
    LaunchedEffect(banner) {
        if (banner != null) {
            delay(2_400)
            banner = null
        }
    }

    val unsavedNew = state.artworkId == null && state.features != null
    BackHandler(enabled = unsavedNew) { confirmLeave = true }
    val handleBack = { if (unsavedNew) confirmLeave = true else onBack() }

    Box(Modifier.fillMaxSize().background(SfColors.Ink0)) {
        val features = state.features
        // Keep the composition's centre visible above the control sheet.
        val lift by animateDpAsState(if (state.chromeVisible) MAX_LIFT else 0.dp, SfMotion.gentle(), label = "lift")
        when {
            features != null -> ArtworkCanvas(
                features, state.settings,
                Modifier
                    // Oversized by twice the maximum lift so moving it never reveals an edge.
                    .layout { measurable, constraints ->
                        val extra = (MAX_LIFT * 2).roundToPx()
                        val placeable = measurable.measure(
                            Constraints.fixed(constraints.maxWidth, constraints.maxHeight + extra),
                        )
                        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -extra / 2) }
                    }
                    .graphicsLayer { translationY = -lift.toPx() }
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = stringResource(R.string.editor_toggle_controls)) {
                        viewModel.onAction(EditorAction.ToggleChrome)
                    },
                contentDescription = stringResource(R.string.artwork_description, stringResource(state.settings.style.label), state.title),
            )
            state.error != null -> ErrorState(state.error!!, onBack)
            else -> ArtLoader(Modifier.size(56.dp).align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = state.chromeVisible && features != null,
            enter = fadeIn(tween(SfMotion.MEDIUM)),
            exit = fadeOut(tween(SfMotion.MEDIUM)),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent))),
                )
                EditorTopBar(
                    title = state.title,
                    saved = !state.dirty && state.artworkId != null,
                    saving = state.isSaving,
                    savedPulse = state.savedPulse,
                    onBack = handleBack,
                    onRename = { renaming = true },
                    onSave = { viewModel.onAction(EditorAction.Save) },
                )
                features?.let {
                    EditorSheet(
                        state = state,
                        features = it,
                        onAction = viewModel::onAction,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = banner != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp),
        ) {
            GlassSurface(shape = SfShapes.pill, strong = true) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(SfIcons.Check, contentDescription = null, tint = SfColors.Aurora, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(banner?.let { stringResource(it) }.orEmpty(), style = SoundframeTheme.type.label, color = SfColors.TextPrimary)
                }
            }
        }
    }

    if (renaming) {
        var draft by rememberSaveable { mutableStateOf(state.title) }
        SfDialog(
            title = stringResource(R.string.editor_name_title),
            onDismiss = { renaming = false },
            confirmText = stringResource(R.string.action_rename),
            confirmEnabled = TitleRules.normalize(draft) != null,
            onConfirm = {
                viewModel.onAction(EditorAction.Rename(draft))
                renaming = false
            },
        ) {
            SfTextField(draft, { draft = it.take(TitleRules.MAX_LENGTH) }, placeholder = stringResource(R.string.editor_name_placeholder), onDone = {
                viewModel.onAction(EditorAction.Rename(draft))
                renaming = false
            })
        }
    }
    if (confirmLeave) {
        SfDialog(
            title = stringResource(R.string.editor_leave_title),
            onDismiss = { confirmLeave = false },
            confirmText = stringResource(R.string.editor_discard),
            dismissText = stringResource(R.string.editor_keep_editing),
            destructive = true,
            onConfirm = {
                confirmLeave = false
                onBack()
            },
        ) {
            Text(stringResource(R.string.editor_leave_body), style = SoundframeTheme.type.body, color = SfColors.TextSecondary)
        }
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    saved: Boolean,
    saving: Boolean,
    savedPulse: Int,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = SfSpacing.md, vertical = SfSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(SfIcons.Back, stringResource(R.string.action_back), onBack)
        Row(
            Modifier
                .weight(1f)
                .padding(horizontal = SfSpacing.sm)
                .clip(SfShapes.pill)
                .clickable(onClickLabel = stringResource(R.string.editor_rename_artwork), role = Role.Button, onClick = onRename)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = SoundframeTheme.type.title, color = SfColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(6.dp))
            Icon(SfIcons.Edit, contentDescription = null, tint = SfColors.TextSecondary, modifier = Modifier.size(14.dp))
        }
        SaveButton(saved, saving, savedPulse, onSave)
    }
}

/** Save control with a ring burst when a save completes. */
@Composable
private fun SaveButton(saved: Boolean, saving: Boolean, pulse: Int, onSave: () -> Unit) {
    val burst = remember { Animatable(1f) }
    LaunchedEffect(pulse) {
        if (pulse > 0) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(700, easing = SfMotion.EaseOutExpo))
        }
    }
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(48.dp)) {
            val p = burst.value
            if (p < 1f) {
                drawCircle(
                    Brush.sweepGradient(SfColors.Signature + SfColors.Signature.first()),
                    radius = size.minDimension / 2f * (1f + p * 0.9f),
                    alpha = 1f - p,
                    style = Stroke(2.dp.toPx() * (1f - p) + 0.5f),
                )
            }
        }
        if (saving) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { ArtLoader(Modifier.size(28.dp)) }
        } else {
            GlassIconButton(
                if (saved) SfIcons.Check else SfIcons.Bookmark,
                stringResource(if (saved) R.string.editor_saved_state else R.string.editor_save),
                onSave,
                highlighted = saved,
            )
        }
    }
}

@Composable
private fun EditorSheet(state: EditorUiState, features: AudioFeatures, onAction: (EditorAction) -> Unit, modifier: Modifier) {
    GlassSurface(
        modifier.fillMaxWidth().padding(SfSpacing.sm).navigationBarsPadding(),
        shape = SfShapes.card,
        strong = true,
    ) {
        Column(Modifier.padding(vertical = SfSpacing.md)) {
            SegmentedTabs(
                options = EditorPanel.entries.map { stringResource(it.label) },
                selected = state.panel.ordinal,
                onSelect = { onAction(EditorAction.ShowPanel(EditorPanel.entries[it])) },
                modifier = Modifier.padding(horizontal = SfSpacing.md),
            )
            Spacer(Modifier.height(SfSpacing.md))
            AnimatedContent(
                targetState = state.panel,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
                label = "panel",
                modifier = Modifier.height(132.dp),
            ) { panel ->
                when (panel) {
                    EditorPanel.STYLE -> StylePanel(features, state, onAction)
                    EditorPanel.PALETTE -> PalettePanel(state.settings.palette) { onAction(EditorAction.SelectPalette(it)) }
                    EditorPanel.TUNE -> TunePanel(state, onAction)
                    EditorPanel.GROUND -> GroundPanel(state.settings.background) { onAction(EditorAction.SelectBackground(it)) }
                }
            }
            Spacer(Modifier.height(SfSpacing.sm))
            LuminousButton(
                stringResource(R.string.action_export_share),
                onClick = { onAction(EditorAction.Export) },
                icon = SfIcons.Share,
                loading = state.isSaving,
                modifier = Modifier.fillMaxWidth().padding(horizontal = SfSpacing.md),
            )
        }
    }
}

@Composable
private fun StylePanel(features: AudioFeatures, state: EditorUiState, onAction: (EditorAction) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = SfSpacing.md), horizontalArrangement = Arrangement.spacedBy(SfSpacing.sm)) {
        items(ArtStyle.entries, key = { it.name }) { style ->
            val selected = style == state.settings.style
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .pressFeedback(interaction)
                    .semantics { this.selected = selected }
                    .clickable(interaction, indication = null, role = Role.RadioButton, onClickLabel = stringResource(style.label)) {
                        onAction(EditorAction.SelectStyle(style))
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val previewSettings = remember(state.settings, style) { state.settings.copy(style = style, animate = false) }
                ArtworkCanvas(
                    features, previewSettings,
                    Modifier
                        .size(86.dp)
                        .clip(SfShapes.small)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) SfColors.SignatureBrush else Brush.linearGradient(listOf(Color(0x26FFFFFF), Color(0x26FFFFFF))),
                            SfShapes.small,
                        ),
                    animate = false,
                )
                Spacer(Modifier.height(6.dp))
                Text(stringResource(style.label), style = SoundframeTheme.type.caption, color = if (selected) SfColors.TextPrimary else SfColors.TextTertiary)
            }
        }
    }
}

@Composable
private fun PalettePanel(selected: PaletteId, onSelect: (PaletteId) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = SfSpacing.md), horizontalArrangement = Arrangement.spacedBy(SfSpacing.md)) {
        items(PaletteId.entries, key = { it.name }) { id ->
            val colors = remember(id) { Palettes.of(id).stops.map { Color(it) } }
            PaletteSwatch(colors, stringResource(id.label), id == selected, onClick = { onSelect(id) }, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun TunePanel(state: EditorUiState, onAction: (EditorAction) -> Unit) {
    Column(Modifier.padding(horizontal = SfSpacing.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(SfSpacing.md)) {
            SfSlider(
                state.settings.intensity, { onAction(EditorAction.SetIntensity(it)) },
                label = stringResource(R.string.editor_intensity), valueText = "${(state.settings.intensity * 100).roundToInt()}",
                modifier = Modifier.weight(1f),
            )
            SfSlider(
                state.settings.complexity, { onAction(EditorAction.SetComplexity(it)) },
                label = stringResource(R.string.editor_complexity), valueText = "${(state.settings.complexity * 100).roundToInt()}",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(SfSpacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(
                if (state.settings.variation == 0) stringResource(R.string.editor_new_variation) else stringResource(R.string.editor_variation_n, state.settings.variation),
                onClick = { onAction(EditorAction.NextVariation) },
                icon = SfIcons.Sparkle,
                height = 44.dp,
            )
            if (state.settings.variation != 0) {
                Spacer(Modifier.width(SfSpacing.xs))
                GlassIconButton(SfIcons.Undo, stringResource(R.string.editor_original), { onAction(EditorAction.OriginalVariation) }, size = 44.dp)
            }
            Spacer(Modifier.weight(1f))
            Icon(SfIcons.Motion, contentDescription = null, tint = SfColors.TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            SfSwitch(state.settings.animate, { onAction(EditorAction.ToggleAnimation) })
        }
    }
}

@Composable
private fun GroundPanel(selected: BackgroundTone, onSelect: (BackgroundTone) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SfSpacing.md, vertical = SfSpacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        BackgroundTone.entries.forEach { tone ->
            val isSelected = tone == selected
            val interaction = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .pressFeedback(interaction)
                    .semantics { this.selected = isSelected }
                    .clickable(interaction, indication = null, role = Role.RadioButton, onClickLabel = stringResource(tone.label)) { onSelect(tone) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(58.dp)
                        .border(2.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(Backgrounds.base(tone)), Color(Backgrounds.deep(tone)))))
                        .border(1.dp, Color(0x33FFFFFF), CircleShape),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(tone.label), style = SoundframeTheme.type.caption, color = if (isSelected) SfColors.TextPrimary else SfColors.TextTertiary)
            }
        }
    }
}

@Composable
private fun ErrorState(@StringRes message: Int, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(SfSpacing.screen), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.editor_nothing), style = SoundframeTheme.type.display, color = SfColors.TextPrimary)
        Spacer(Modifier.height(SfSpacing.sm))
        Text(stringResource(message), style = SoundframeTheme.type.body, color = SfColors.TextSecondary)
        Spacer(Modifier.height(SfSpacing.lg))
        GhostButton(stringResource(R.string.action_go_back), onBack)
    }
}

private val MAX_LIFT = 150.dp

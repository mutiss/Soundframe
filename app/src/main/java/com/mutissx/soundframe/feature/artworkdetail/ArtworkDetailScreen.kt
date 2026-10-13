package com.mutissx.soundframe.feature.artworkdetail

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.common.Format
import com.mutissx.soundframe.core.designsystem.art.ArtworkCanvas
import com.mutissx.soundframe.core.designsystem.art.sharedArtwork
import com.mutissx.soundframe.core.designsystem.component.AmbientBackground
import com.mutissx.soundframe.core.designsystem.component.ArtLoader
import com.mutissx.soundframe.core.designsystem.component.GhostButton
import com.mutissx.soundframe.core.designsystem.component.GlassIconButton
import com.mutissx.soundframe.core.designsystem.component.LuminousButton
import com.mutissx.soundframe.core.designsystem.component.Overline
import com.mutissx.soundframe.core.designsystem.component.SfDialog
import com.mutissx.soundframe.core.designsystem.component.SfTextField
import com.mutissx.soundframe.core.designsystem.component.SfTopBar
import com.mutissx.soundframe.core.designsystem.component.StatBlock
import com.mutissx.soundframe.core.designsystem.icon.SfIcons
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SfSpacing
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.domain.model.Artwork
import com.mutissx.soundframe.domain.repository.ArtworkRepository
import com.mutissx.soundframe.domain.repository.AudioPlayer
import com.mutissx.soundframe.domain.usecase.DeleteArtworkUseCase
import com.mutissx.soundframe.domain.usecase.RenameArtworkUseCase
import com.mutissx.soundframe.domain.usecase.ResolvePlayableAudioUseCase
import com.mutissx.soundframe.domain.usecase.TitleRules
import com.mutissx.soundframe.domain.usecase.ToggleFavoriteUseCase
import com.mutissx.soundframe.feature.common.label
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class ArtworkDetailUiState(
    val loading: Boolean = true,
    val artwork: Artwork? = null,
    val playablePath: String? = null,
    val isPlaying: Boolean = false,
    val playback: Float = 0f,
    val deleted: Boolean = false,
)

class ArtworkDetailViewModel(
    private val artworkId: String,
    private val artworks: ArtworkRepository,
    private val player: AudioPlayer,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val renameArtwork: RenameArtworkUseCase,
    private val deleteArtwork: DeleteArtworkUseCase,
    private val resolveAudio: ResolvePlayableAudioUseCase,
) : ViewModel() {
    private val playable = MutableStateFlow<String?>(null)
    private val deleted = MutableStateFlow(false)

    val state: StateFlow<ArtworkDetailUiState> = combine(
        artworks.observeArtwork(artworkId), playable, player.isPlaying, player.progress, deleted,
    ) { artwork, path, playing, progress, isDeleted ->
        ArtworkDetailUiState(false, artwork, path, playing, progress, isDeleted)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtworkDetailUiState())

    init {
        // Audio may have been deleted or never kept; only offer playback if the file exists.
        viewModelScope.launch {
            artworks.getArtwork(artworkId)?.let { playable.value = resolveAudio(it) }
        }
    }

    fun togglePlayback() {
        val path = playable.value ?: return
        if (player.isPlaying.value) player.stop() else player.play(path)
    }

    fun toggleFavorite() {
        viewModelScope.launch { toggleFavoriteUseCase(artworkId) }
    }

    fun rename(title: String) {
        viewModelScope.launch { renameArtwork(artworkId, title) }
    }

    fun delete() {
        viewModelScope.launch {
            player.stop()
            deleteArtwork(artworkId)
            deleted.value = true
        }
    }

    fun stopPlayback() = player.stop()

    override fun onCleared() {
        player.release()
    }
}

@Composable
fun ArtworkDetailScreen(
    viewModel: ArtworkDetailViewModel,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onExport: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        val artwork = state.artwork
        if (artwork == null) {
            if (state.loading) ArtLoader(Modifier.size(56.dp).align(Alignment.Center))
            else Text(stringResource(R.string.detail_missing), style = SoundframeTheme.type.body, color = SfColors.TextSecondary, modifier = Modifier.align(Alignment.Center))
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                SfTopBar(
                    title = null,
                    onBack = { viewModel.stopPlayback(); onBack() },
                    trailing = {
                        GlassIconButton(
                            if (artwork.isFavorite) SfIcons.HeartFilled else SfIcons.Heart,
                            stringResource(if (artwork.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
                            { viewModel.toggleFavorite() },
                            tint = if (artwork.isFavorite) SfColors.Rose else SfColors.TextPrimary,
                        )
                    },
                )
                ArtworkCanvas(
                    artwork.features, artwork.settings,
                    Modifier
                        .padding(horizontal = SfSpacing.md)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .sharedArtwork(artwork.id)
                        .clip(SfShapes.artwork),
                    contentDescription = stringResource(R.string.artwork_description, stringResource(artwork.settings.style.label), artwork.title),
                )
                Column(Modifier.padding(SfSpacing.screen)) {
                    Overline(stringResource(R.string.gallery_item_meta, stringResource(artwork.settings.style.label), stringResource(artwork.settings.palette.label)), dot = true)
                    Spacer(Modifier.height(SfSpacing.xs))
                    Text(artwork.title, style = SoundframeTheme.type.display, color = SfColors.TextPrimary)
                    Text(Format.date(artwork.createdAt), style = SoundframeTheme.type.caption, color = SfColors.TextTertiary)
                    Spacer(Modifier.height(SfSpacing.lg))

                    Overline(stringResource(R.string.detail_signature))
                    Spacer(Modifier.height(SfSpacing.sm))
                    val f = artwork.features
                    Row(horizontalArrangement = Arrangement.spacedBy(SfSpacing.xs)) {
                        StatBlock(Format.seconds(f.durationSec), stringResource(R.string.stat_length), Modifier.weight(1f))
                        StatBlock(Format.hertz(f.spectralCentroid), stringResource(R.string.stat_brightness), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(SfSpacing.xs))
                    Row(horizontalArrangement = Arrangement.spacedBy(SfSpacing.xs)) {
                        StatBlock(Format.decibels(f.dynamicRangeDb), stringResource(R.string.stat_dynamics), Modifier.weight(1f))
                        StatBlock(Format.rate(f.transientDensity), stringResource(R.string.stat_onsets), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(SfSpacing.lg))

                    if (state.playablePath != null) {
                        PlaybackButton(state.isPlaying, state.playback, viewModel::togglePlayback)
                        Spacer(Modifier.height(SfSpacing.md))
                    }
                    LuminousButton(stringResource(R.string.action_export_share), { viewModel.stopPlayback(); onExport(artwork.id) }, Modifier.fillMaxWidth(), icon = SfIcons.Share)
                    Spacer(Modifier.height(SfSpacing.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(SfSpacing.xs)) {
                        GhostButton(stringResource(R.string.action_edit), { viewModel.stopPlayback(); onEdit(artwork.id) }, Modifier.weight(1f), icon = SfIcons.Edit)
                        GhostButton(stringResource(R.string.action_rename), { renaming = true }, Modifier.weight(1f))
                        GhostButton(stringResource(R.string.action_delete), { confirmDelete = true }, Modifier.weight(1f), icon = SfIcons.Trash, contentColor = SfColors.Danger)
                    }
                    Spacer(Modifier.navigationBarsPadding().height(SfSpacing.lg))
                }
            }
        }
    }

    val artwork = state.artwork
    if (renaming && artwork != null) {
        var draft by rememberSaveable { mutableStateOf(artwork.title) }
        SfDialog(
            title = stringResource(R.string.editor_rename_artwork),
            onDismiss = { renaming = false },
            confirmText = stringResource(R.string.action_rename),
            confirmEnabled = TitleRules.normalize(draft) != null,
            onConfirm = { viewModel.rename(draft); renaming = false },
        ) {
            SfTextField(draft, { draft = it.take(TitleRules.MAX_LENGTH) }, onDone = { viewModel.rename(draft); renaming = false })
        }
    }
    if (confirmDelete) {
        SfDialog(
            title = stringResource(R.string.detail_delete_title),
            onDismiss = { confirmDelete = false },
            confirmText = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = { confirmDelete = false; viewModel.delete() },
        ) {
            Text(
                stringResource(R.string.detail_delete_body),
                style = SoundframeTheme.type.body, color = SfColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun PlaybackButton(playing: Boolean, progress: Float, onToggle: () -> Unit) {
    val shown by animateFloatAsState(progress, label = "playback")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(56.dp)) {
                drawArc(
                    Brush.sweepGradient(listOf(SfColors.Aurora, SfColors.Violet, SfColors.Rose, SfColors.Aurora)),
                    -90f, 360f * shown, false, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            GlassIconButton(if (playing) SfIcons.Pause else SfIcons.Play, stringResource(if (playing) R.string.detail_pause else R.string.detail_play), onToggle)
        }
        Spacer(Modifier.size(SfSpacing.sm))
        Column {
            Text(stringResource(if (playing) R.string.detail_playing else R.string.detail_hear), style = SoundframeTheme.type.label, color = SfColors.TextPrimary)
            Text(stringResource(R.string.detail_private), style = SoundframeTheme.type.caption, color = SfColors.TextTertiary)
        }
    }
}

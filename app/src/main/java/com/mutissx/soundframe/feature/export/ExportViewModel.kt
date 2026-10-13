package com.mutissx.soundframe.feature.export

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.common.Format
import com.mutissx.soundframe.domain.model.Artwork
import com.mutissx.soundframe.domain.model.ArtworkNotFoundException
import com.mutissx.soundframe.domain.model.ExportImageSize
import com.mutissx.soundframe.domain.repository.ArtworkExporter
import com.mutissx.soundframe.domain.repository.ArtworkRepository
import com.mutissx.soundframe.domain.repository.ExportedFile
import com.mutissx.soundframe.domain.repository.PreferencesRepository
import com.mutissx.soundframe.domain.repository.VideoExportProgress
import com.mutissx.soundframe.domain.usecase.ExportImageUseCase
import com.mutissx.soundframe.domain.usecase.ExportVideoUseCase
import com.mutissx.soundframe.domain.usecase.ResolvePlayableAudioUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.ceil

enum class ExportMode(@StringRes val label: Int) { IMAGE(R.string.export_image), VIDEO(R.string.export_video) }

sealed interface ExportJob {
    data object Idle : ExportJob
    /** [progress] is real encoder progress for video, null for image renders. */
    data class Rendering(val progress: Float?) : ExportJob
    data class Done(val file: ExportedFile) : ExportJob
    data class Failed(@StringRes val message: Int) : ExportJob
}

@Immutable
data class ExportUiState(
    val artwork: Artwork? = null,
    @StringRes val error: Int? = null,
    val mode: ExportMode = ExportMode.IMAGE,
    val size: ExportImageSize = ExportImageSize.SQUARE,
    val signature: Boolean = true,
    val durationSec: Int = ExportVideoUseCase.MAX_SECONDS,
    val includeAudio: Boolean = true,
    val hasAudio: Boolean = false,
    val videoAvailable: Boolean = true,
    val image: ExportJob = ExportJob.Idle,
    val video: ExportJob = ExportJob.Idle,
) {
    val activeJob: ExportJob get() = if (mode == ExportMode.IMAGE) image else video
}

sealed interface ExportAction {
    data class SetMode(val mode: ExportMode) : ExportAction
    data class SetSize(val size: ExportImageSize) : ExportAction
    data class SetSignature(val enabled: Boolean) : ExportAction
    data class SetDuration(val seconds: Int) : ExportAction
    data class SetIncludeAudio(val enabled: Boolean) : ExportAction
    data object Render : ExportAction
    data object Cancel : ExportAction
    data object Share : ExportAction
    data object SaveToDevice : ExportAction
}

sealed interface ExportEffect {
    data class Share(val file: ExportedFile) : ExportEffect
    data class Message(@StringRes val text: Int) : ExportEffect
}

class ExportViewModel(
    private val artworkId: String,
    private val artworks: ArtworkRepository,
    private val preferences: PreferencesRepository,
    private val exportImage: ExportImageUseCase,
    private val exportVideo: ExportVideoUseCase,
    private val exporter: ArtworkExporter,
    private val resolveAudio: ResolvePlayableAudioUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportUiState(videoAvailable = exportVideo.isAvailable))
    val state: StateFlow<ExportUiState> = _state.asStateFlow()
    private val _effects = Channel<ExportEffect>(Channel.BUFFERED)
    val effects: Flow<ExportEffect> = _effects.receiveAsFlow()
    private var renderJob: Job? = null

    init {
        viewModelScope.launch {
            val artwork = artworks.getArtwork(artworkId)
            if (artwork == null) {
                _state.update { it.copy(error = Format.error(ArtworkNotFoundException(artworkId))) }
                return@launch
            }
            val hasAudio = resolveAudio(artwork) != null
            val signature = preferences.preferences.first().watermarkExports
            val duration = ceil(artwork.features.durationSec).toInt().coerceIn(ExportVideoUseCase.MIN_SECONDS, ExportVideoUseCase.MAX_SECONDS)
            _state.update {
                it.copy(artwork = artwork.copy(audioPath = artwork.audioPath.takeIf { hasAudio }), hasAudio = hasAudio, includeAudio = hasAudio, signature = signature, durationSec = duration)
            }
        }
    }

    fun onAction(action: ExportAction) {
        when (action) {
            is ExportAction.SetMode -> _state.update { it.copy(mode = action.mode) }
            is ExportAction.SetSize -> changeOption { it.copy(size = action.size, image = ExportJob.Idle) }
            is ExportAction.SetSignature -> changeOption { it.copy(signature = action.enabled, image = ExportJob.Idle, video = ExportJob.Idle) }
            is ExportAction.SetDuration -> changeOption {
                it.copy(durationSec = action.seconds.coerceIn(ExportVideoUseCase.MIN_SECONDS, ExportVideoUseCase.MAX_SECONDS), video = ExportJob.Idle)
            }
            is ExportAction.SetIncludeAudio -> changeOption { it.copy(includeAudio = action.enabled && it.hasAudio, video = ExportJob.Idle) }
            ExportAction.Render -> render()
            ExportAction.Cancel -> cancel()
            ExportAction.Share -> (_state.value.activeJob as? ExportJob.Done)?.let { _effects.trySend(ExportEffect.Share(it.file)) }
            ExportAction.SaveToDevice -> saveToDevice()
        }
    }

    /** Options are locked while rendering; changing them invalidates a finished file. */
    private fun changeOption(transform: (ExportUiState) -> ExportUiState) {
        if (_state.value.activeJob is ExportJob.Rendering) return
        _state.update(transform)
    }

    private fun render() {
        val current = _state.value
        val artwork = current.artwork ?: return
        if (renderJob?.isActive == true) return
        renderJob = viewModelScope.launch {
            when (current.mode) {
                ExportMode.IMAGE -> {
                    _state.update { it.copy(image = ExportJob.Rendering(null)) }
                    try {
                        val file = exportImage(artwork, current.size, current.signature)
                        _state.update { it.copy(image = ExportJob.Done(file)) }
                    } catch (e: CancellationException) {
                        _state.update { it.copy(image = ExportJob.Idle) }
                        throw e
                    } catch (e: Exception) {
                        _state.update { it.copy(image = ExportJob.Failed(Format.error(e))) }
                    }
                }
                ExportMode.VIDEO -> {
                    _state.update { it.copy(video = ExportJob.Rendering(0f)) }
                    try {
                        exportVideo(artwork, current.durationSec, current.includeAudio, current.signature).collect { progress ->
                            _state.update {
                                it.copy(
                                    video = when (progress) {
                                        is VideoExportProgress.Rendering -> ExportJob.Rendering(progress.fraction)
                                        is VideoExportProgress.Done -> ExportJob.Done(progress.file)
                                    },
                                )
                            }
                        }
                    } catch (e: CancellationException) {
                        _state.update { it.copy(video = ExportJob.Idle) }
                        throw e
                    } catch (e: Exception) {
                        _state.update { it.copy(video = ExportJob.Failed(Format.error(e))) }
                    }
                }
            }
        }
    }

    private fun cancel() {
        renderJob?.cancel()
        _effects.trySend(ExportEffect.Message(R.string.export_cancelled))
    }

    private fun saveToDevice() {
        val done = _state.value.activeJob as? ExportJob.Done ?: return
        viewModelScope.launch {
            val ok = exporter.saveToDevice(done.file)
            val message = when {
                !ok -> R.string.export_save_failed
                done.file.mimeType.startsWith("video/") -> R.string.export_saved_movies
                else -> R.string.export_saved_pictures
            }
            _effects.send(ExportEffect.Message(message))
        }
    }
}

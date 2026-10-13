package com.mutissx.soundframe.feature.generation

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutissx.soundframe.app.navigation.GenerationRoute
import com.mutissx.soundframe.core.audio.PcmUtils
import com.mutissx.soundframe.core.common.Format
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.SessionExpiredException
import com.mutissx.soundframe.domain.repository.CreationSessionRepository
import com.mutissx.soundframe.domain.repository.TitleVocabularyProvider
import com.mutissx.soundframe.domain.usecase.AnalyzeSessionUseCase
import com.mutissx.soundframe.domain.usecase.ArtDirection
import com.mutissx.soundframe.domain.usecase.ImportAudioUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class GenerationUiState(
    val waveform: List<Float> = emptyList(),
    val sessionId: String? = null,
    val features: AudioFeatures? = null,
    val settings: ArtworkSettings? = null,
    val title: String = "",
    @StringRes val error: Int? = null,
) {
    val isReady: Boolean get() = features != null && settings != null && sessionId != null
}

class GenerationViewModel(
    private val route: GenerationRoute,
    private val sessions: CreationSessionRepository,
    private val importAudio: ImportAudioUseCase,
    private val analyzeSession: AnalyzeSessionUseCase,
    private val vocabulary: TitleVocabularyProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(GenerationUiState())
    val state: StateFlow<GenerationUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        start()
    }

    fun retry() {
        _state.value = GenerationUiState()
        start()
    }

    private fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                val session = when {
                    route.sessionId != null -> sessions.get(route.sessionId) ?: throw SessionExpiredException()
                    route.importUri != null -> importAudio(route.importUri)
                    else -> throw SessionExpiredException()
                }
                _state.update { it.copy(sessionId = session.id, waveform = PcmUtils.peakEnvelope(session.clip.samples, WAVEFORM_BARS)) }
                val features = analyzeSession(session.id)
                val settings = ArtDirection.recommendSettings(features)
                _state.update {
                    it.copy(features = features, settings = settings, title = ArtDirection.suggestTitle(features, settings.style, vocabulary.vocabulary()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = Format.error(e)) }
            }
        }
    }

    private companion object {
        const val WAVEFORM_BARS = 56
    }
}

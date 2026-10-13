package com.mutissx.soundframe.feature.recording

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutissx.soundframe.core.common.Format
import com.mutissx.soundframe.domain.model.AudioClip
import com.mutissx.soundframe.domain.model.RecordingException
import com.mutissx.soundframe.domain.usecase.RecordAudioUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MicPermission { UNKNOWN, GRANTED, DENIED, PERMANENTLY_DENIED }

enum class RecordingPhase { IDLE, RECORDING, ERROR }

@Immutable
data class RecordingUiState(
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val permission: MicPermission = MicPermission.UNKNOWN,
    val level: Float = 0f,
    val elapsedMs: Long = 0,
    val history: List<Float> = emptyList(),
    val silenced: Boolean = false,
    @StringRes val errorRes: Int? = null,
) {
    val progress: Float get() = (elapsedMs.toFloat() / AudioClip.MAX_DURATION_MS).coerceIn(0f, 1f)
}

sealed interface RecordingAction {
    data class PermissionResult(val granted: Boolean, val canAskAgain: Boolean) : RecordingAction
    data object ToggleRecording : RecordingAction
    data object Backgrounded : RecordingAction
    data object DismissError : RecordingAction
}

sealed interface RecordingEffect {
    data object RequestPermission : RecordingEffect
    data class OpenGeneration(val sessionId: String) : RecordingEffect
}

class RecordingViewModel(private val recordAudio: RecordAudioUseCase) : ViewModel() {

    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()
    private val _effects = Channel<RecordingEffect>(Channel.BUFFERED)
    val effects: Flow<RecordingEffect> = _effects.receiveAsFlow()

    private var recordingJob: Job? = null
    private var levelJob: Job? = null

    fun onAction(action: RecordingAction) {
        when (action) {
            is RecordingAction.PermissionResult -> onPermission(action)
            RecordingAction.ToggleRecording -> toggle()
            RecordingAction.Backgrounded -> onBackgrounded()
            RecordingAction.DismissError -> _state.update { it.copy(phase = RecordingPhase.IDLE, errorRes = null) }
        }
    }

    /** Called once on entry with the current permission so the screen can auto-start. */
    fun onEntered(permissionGranted: Boolean, autoStart: Boolean) {
        _state.update { it.copy(permission = if (permissionGranted) MicPermission.GRANTED else it.permission) }
        if (autoStart) toggle()
    }

    private fun onPermission(result: RecordingAction.PermissionResult) {
        val permission = when {
            result.granted -> MicPermission.GRANTED
            result.canAskAgain -> MicPermission.DENIED
            else -> MicPermission.PERMANENTLY_DENIED
        }
        _state.update { it.copy(permission = permission) }
        if (result.granted) start()
    }

    private fun toggle() {
        if (recordingJob?.isActive == true) {
            recordAudio.stop()
            return
        }
        when (_state.value.permission) {
            MicPermission.GRANTED -> start()
            MicPermission.PERMANENTLY_DENIED -> Unit // The screen shows the settings route instead.
            else -> _effects.trySend(RecordingEffect.RequestPermission)
        }
    }

    private fun start() {
        if (recordingJob?.isActive == true) return
        _state.update { it.copy(phase = RecordingPhase.RECORDING, errorRes = null, elapsedMs = 0, level = 0f, history = emptyList()) }
        levelJob = viewModelScope.launch {
            recordAudio.level.collect { level ->
                _state.update {
                    if (it.phase != RecordingPhase.RECORDING) it
                    else it.copy(level = level.amplitude, elapsedMs = level.elapsedMs, history = level.history, silenced = level.silenced)
                }
            }
        }
        recordingJob = viewModelScope.launch {
            try {
                val session = recordAudio()
                _state.update { it.copy(phase = RecordingPhase.IDLE, level = 0f) }
                _effects.send(RecordingEffect.OpenGeneration(session.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: RecordingException) {
                val permission = if (e is RecordingException.PermissionDenied) MicPermission.DENIED else _state.value.permission
                _state.update { it.copy(phase = RecordingPhase.ERROR, errorRes = Format.error(e), permission = permission, level = 0f) }
            } catch (e: Exception) {
                _state.update { it.copy(phase = RecordingPhase.ERROR, errorRes = Format.error(e), level = 0f) }
            } finally {
                levelJob?.cancel()
            }
        }
    }

    private fun onBackgrounded() {
        if (recordingJob?.isActive != true) return
        recordingJob?.cancel()
        levelJob?.cancel()
        _state.update {
            it.copy(phase = RecordingPhase.ERROR, level = 0f, errorRes = Format.error(RecordingException.Interrupted()))
        }
    }
}

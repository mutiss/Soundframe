package com.mutissx.soundframe.domain.usecase

import com.mutissx.soundframe.domain.model.AudioClip
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.AudioSource
import com.mutissx.soundframe.domain.model.CreationSession
import com.mutissx.soundframe.domain.model.RecordingException
import com.mutissx.soundframe.domain.model.SessionExpiredException
import com.mutissx.soundframe.domain.repository.AudioAnalyzer
import com.mutissx.soundframe.domain.repository.AudioDecoder
import com.mutissx.soundframe.domain.repository.AudioRecorder
import com.mutissx.soundframe.domain.repository.CreationSessionRepository
import com.mutissx.soundframe.domain.repository.RecorderLevel
import kotlinx.coroutines.flow.StateFlow

class RecordAudioUseCase(
    private val recorder: AudioRecorder,
    private val sessions: CreationSessionRepository,
) {
    val level: StateFlow<RecorderLevel> get() = recorder.level

    suspend operator fun invoke(maxDurationMs: Long = AudioClip.MAX_DURATION_MS): CreationSession {
        val clip = recorder.record(maxDurationMs)
        if (clip.durationMs < MIN_DURATION_MS) throw RecordingException.TooShort()
        return sessions.create(clip, AudioSource.RECORDED)
    }

    fun stop() = recorder.requestStop()

    companion object {
        const val MIN_DURATION_MS = 400L
    }
}

class ImportAudioUseCase(
    private val decoder: AudioDecoder,
    private val sessions: CreationSessionRepository,
) {
    suspend operator fun invoke(uri: String): CreationSession {
        val clip = decoder.decode(uri, AudioClip.MAX_DURATION_MS)
        return sessions.create(clip, AudioSource.IMPORTED)
    }
}

/** Analyses a session once; later calls reuse the cached features. */
class AnalyzeSessionUseCase(
    private val analyzer: AudioAnalyzer,
    private val sessions: CreationSessionRepository,
) {
    suspend operator fun invoke(sessionId: String): AudioFeatures {
        val session = sessions.get(sessionId) ?: throw SessionExpiredException()
        session.features?.let { return it }
        val features = analyzer.analyze(session.clip)
        sessions.attachFeatures(sessionId, features)
        return features
    }
}

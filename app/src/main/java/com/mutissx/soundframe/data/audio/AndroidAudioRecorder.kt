package com.mutissx.soundframe.data.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.mutissx.soundframe.core.audio.PcmUtils
import com.mutissx.soundframe.core.common.AppDispatchers
import com.mutissx.soundframe.domain.model.AudioClip
import com.mutissx.soundframe.domain.model.RecordingException
import com.mutissx.soundframe.domain.repository.AudioRecorder
import com.mutissx.soundframe.domain.repository.RecorderLevel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Captures mono 16-bit PCM straight into a preallocated buffer sized for the maximum duration,
 * so the read loop never allocates audio memory. Levels are published per 23 ms chunk.
 */
class AndroidAudioRecorder(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : AudioRecorder {

    private val _level = MutableStateFlow(RecorderLevel())
    override val level: StateFlow<RecorderLevel> = _level.asStateFlow()
    private val stopRequested = AtomicBoolean(false)

    override fun requestStop() = stopRequested.set(true)

    @SuppressLint("MissingPermission") // Checked explicitly below.
    override suspend fun record(maxDurationMs: Long): AudioClip = withContext(dispatchers.io) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw RecordingException.PermissionDenied()
        }
        stopRequested.set(false)
        _level.value = RecorderLevel()

        val rate = AudioClip.DEFAULT_SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw RecordingException.MicrophoneUnavailable()
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuffer, CHUNK * 2 * 4))
                .build()
        } catch (e: UnsupportedOperationException) {
            throw RecordingException.MicrophoneUnavailable(e)
        } catch (e: SecurityException) {
            throw RecordingException.PermissionDenied()
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw RecordingException.MicrophoneUnavailable()
        }

        val maxSamples = (rate * maxDurationMs / 1000).toInt()
        val samples = ShortArray(maxSamples)
        val history = FloatArray(HISTORY)
        var historyCount = 0
        var written = 0
        val silenced = AtomicBoolean(false)
        val callback = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                val mine = configs.firstOrNull { it.clientAudioSessionId == record.audioSessionId }
                silenced.set(mine?.isClientSilenced == true)
            }
        }
        record.registerAudioRecordingCallback(context.mainExecutor, callback)
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw RecordingException.MicrophoneUnavailable()
            while (written < maxSamples && !stopRequested.get()) {
                ensureActive()
                val read = record.read(samples, written, min(CHUNK, maxSamples - written))
                if (read < 0) {
                    // Dead object / invalid operation: the OS took the mic away. Keep what we have
                    // if it's long enough to be meaningful.
                    if (written >= rate) break else throw RecordingException.Interrupted()
                }
                if (read == 0) continue
                val amplitude = PcmUtils.level(samples, written, read)
                written += read
                if (historyCount < HISTORY) {
                    history[historyCount++] = amplitude
                } else {
                    System.arraycopy(history, 1, history, 0, HISTORY - 1)
                    history[HISTORY - 1] = amplitude
                }
                _level.value = RecorderLevel(
                    amplitude = amplitude,
                    elapsedMs = written * 1000L / rate,
                    history = history.copyOf(historyCount).asList(),
                    silenced = silenced.get(),
                )
            }
        } finally {
            record.unregisterAudioRecordingCallback(callback)
            runCatching { record.stop() }
            record.release()
        }
        AudioClip(samples.copyOf(written), rate)
    }

    private companion object {
        const val CHUNK = 1024
        const val HISTORY = 96
    }
}

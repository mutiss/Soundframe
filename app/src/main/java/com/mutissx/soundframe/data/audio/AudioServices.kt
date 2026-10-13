package com.mutissx.soundframe.data.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import com.mutissx.soundframe.core.audio.FeatureExtractor
import com.mutissx.soundframe.core.audio.WavCodec
import com.mutissx.soundframe.core.common.AppDispatchers
import com.mutissx.soundframe.core.common.FileOps
import com.mutissx.soundframe.core.common.StoragePaths
import com.mutissx.soundframe.domain.model.AudioClip
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.StorageException
import com.mutissx.soundframe.domain.repository.AudioAnalyzer
import com.mutissx.soundframe.domain.repository.AudioPlayer
import com.mutissx.soundframe.domain.repository.AudioStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class DefaultAudioAnalyzer(private val dispatchers: AppDispatchers) : AudioAnalyzer {
    override suspend fun analyze(clip: AudioClip): AudioFeatures = withContext(dispatchers.default) {
        val context = currentCoroutineContext()
        FeatureExtractor().extract(clip.toFloatArray(), clip.sampleRate) { context.ensureActive() }
    }
}

/** Stores clips as standard WAV files so they stay playable by MediaPlayer and exportable. */
class FileAudioStore(
    private val paths: StoragePaths,
    private val dispatchers: AppDispatchers,
) : AudioStore {

    override suspend fun save(name: String, clip: AudioClip, temporary: Boolean): String = withContext(dispatchers.io) {
        val dir = if (temporary) paths.sessionDir else paths.audioDir
        val bytes = WavCodec.encode(clip)
        FileOps.ensureSpace(dir, bytes.size.toLong())
        val file = File(dir, "$name.wav")
        FileOps.writeAtomically(file) { it.writeBytes(bytes) }
        file.absolutePath
    }

    override suspend fun load(path: String): AudioClip = withContext(dispatchers.io) {
        val file = File(path)
        if (!file.exists()) throw StorageException.MissingFile(path)
        try {
            WavCodec.decode(file.readBytes())
        } catch (e: IOException) {
            throw StorageException.MissingFile(path)
        }
    }

    override suspend fun exists(path: String): Boolean = withContext(dispatchers.io) { File(path).isFile }

    override suspend fun delete(path: String) {
        withContext(dispatchers.io) { File(path).delete() }
    }
}

class MediaAudioPlayer(dispatchers: AppDispatchers) : AudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val _isPlaying = MutableStateFlow(false)
    private val _progress = MutableStateFlow(0f)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    override val progress: StateFlow<Float> = _progress.asStateFlow()

    override fun play(path: String) {
        stop()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            mp.setDataSource(path)
            mp.setOnCompletionListener { stop() }
            mp.prepare()
            mp.start()
        } catch (e: Exception) {
            mp.release()
            return
        }
        player = mp
        _isPlaying.value = true
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                val duration = p.duration.coerceAtLeast(1)
                _progress.value = p.currentPosition.toFloat() / duration
                delay(32)
            }
        }
    }

    override fun stop() {
        ticker?.cancel()
        ticker = null
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        _isPlaying.value = false
        _progress.value = 0f
    }

    override fun release() {
        stop()
        scope.cancel()
    }
}

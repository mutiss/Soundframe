package com.mutissx.soundframe.testing

import com.mutissx.soundframe.core.audio.FeatureExtractor
import com.mutissx.soundframe.domain.model.Artwork
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioClip
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.AudioSource
import com.mutissx.soundframe.domain.model.CreationSession
import com.mutissx.soundframe.domain.model.ExportImageSize
import com.mutissx.soundframe.domain.model.GallerySort
import com.mutissx.soundframe.domain.model.PremiumFeature
import com.mutissx.soundframe.domain.model.StorageException
import com.mutissx.soundframe.domain.repository.ArtworkExporter
import com.mutissx.soundframe.domain.repository.ArtworkRepository
import com.mutissx.soundframe.domain.repository.AudioAnalyzer
import com.mutissx.soundframe.domain.repository.AudioRecorder
import com.mutissx.soundframe.domain.repository.AudioStore
import com.mutissx.soundframe.domain.repository.Clock
import com.mutissx.soundframe.domain.repository.CreationSessionRepository
import com.mutissx.soundframe.domain.repository.ExportedFile
import com.mutissx.soundframe.domain.repository.FeatureEntitlements
import com.mutissx.soundframe.domain.repository.IdGenerator
import com.mutissx.soundframe.domain.repository.PreferencesRepository
import com.mutissx.soundframe.domain.repository.RecorderLevel
import com.mutissx.soundframe.domain.repository.ThumbnailRenderer
import com.mutissx.soundframe.domain.repository.UserPreferences
import com.mutissx.soundframe.domain.repository.VideoExportProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.awaitCancellation
import kotlin.math.PI
import kotlin.math.sin

/** Deterministic synthetic signals. */
object Signals {
    const val RATE = 44_100

    fun sine(freq: Float, seconds: Float, amplitude: Float = 0.5f, rate: Int = RATE): FloatArray =
        FloatArray((seconds * rate).toInt()) { amplitude * sin(2.0 * PI * freq * it / rate).toFloat() }

    fun silence(seconds: Float, rate: Int = RATE) = FloatArray((seconds * rate).toInt())

    /** Short decaying noise bursts every [interval] seconds over a quiet floor. */
    fun clicks(interval: Float, seconds: Float, rate: Int = RATE): FloatArray {
        val out = FloatArray((seconds * rate).toInt())
        var seed = 12345L
        val period = (interval * rate).toInt()
        for (i in out.indices) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            val noise = ((seed ushr 33).toFloat() / (1L shl 31).toFloat()) - 0.5f
            val sinceClick = i % period
            val envelope = kotlin.math.exp(-sinceClick / (0.008f * rate))
            out[i] = noise * 1.6f * envelope + noise * 0.002f
        }
        return out
    }

    fun whiteNoise(seconds: Float, amplitude: Float = 0.3f, rate: Int = RATE): FloatArray {
        var seed = 987654321L
        return FloatArray((seconds * rate).toInt()) {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            (((seed ushr 33).toFloat() / (1L shl 31).toFloat()) * 2f - 1f) * amplitude
        }
    }

    fun toClip(samples: FloatArray, rate: Int = RATE) =
        AudioClip(ShortArray(samples.size) { (samples[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }, rate)

    fun features(samples: FloatArray, rate: Int = RATE): AudioFeatures = FeatureExtractor().extract(samples, rate)
}

fun sampleArtwork(id: String = "a1", features: AudioFeatures = Signals.features(Signals.sine(440f, 1f))) = Artwork(
    id = id,
    title = "Velvet Drift",
    createdAt = 1_000,
    updatedAt = 1_000,
    features = features,
    settings = ArtworkSettings(),
    audioPath = "/audio/$id.wav",
    thumbnailPath = "/thumbs/$id.webp",
    isFavorite = false,
    engineVersion = 1,
)

class FakeClock(var time: Long = 5_000) : Clock {
    override fun now() = time
}

class SequentialIds : IdGenerator {
    private var next = 0
    override fun newId() = "id-${next++}"
}

class FakeAudioRecorder : AudioRecorder {
    override val level = MutableStateFlow(RecorderLevel())
    var result: CompletableDeferred<AudioClip> = CompletableDeferred()
    var stopRequested = false

    override suspend fun record(maxDurationMs: Long): AudioClip = result.await()
    override fun requestStop() {
        stopRequested = true
    }
}

class FakeAnalyzer : AudioAnalyzer {
    var calls = 0
    override suspend fun analyze(clip: AudioClip): AudioFeatures {
        calls++
        return FeatureExtractor().extract(clip.toFloatArray(), clip.sampleRate)
    }
}

class FakeSessions : CreationSessionRepository {
    val sessions = mutableMapOf<String, CreationSession>()
    private val ids = SequentialIds()

    override suspend fun create(clip: AudioClip, source: AudioSource): CreationSession =
        CreationSession("session-${ids.newId()}", clip, source).also { sessions[it.id] = it }

    override suspend fun get(id: String) = sessions[id]
    override suspend fun attachFeatures(id: String, features: AudioFeatures) {
        sessions[id] = sessions.getValue(id).copy(features = features)
    }
    override suspend fun discard(id: String) {
        sessions.remove(id)
    }
}

class FakeAudioStore : AudioStore {
    val files = mutableMapOf<String, AudioClip>()
    var failWith: Exception? = null

    override suspend fun save(name: String, clip: AudioClip, temporary: Boolean): String {
        failWith?.let { throw it }
        return "/audio/$name.wav".also { files[it] = clip }
    }
    override suspend fun load(path: String) = files[path] ?: throw StorageException.MissingFile(path)
    override suspend fun exists(path: String) = path in files
    override suspend fun delete(path: String) {
        files.remove(path)
    }
}

class FakeThumbnails : ThumbnailRenderer {
    val existing = mutableSetOf<String>()
    var renders = 0
    override suspend fun render(id: String, features: AudioFeatures, settings: ArtworkSettings): String {
        renders++
        return "/thumbs/$id.webp".also { existing += it }
    }
    override suspend fun exists(path: String) = path in existing
    override suspend fun delete(path: String) {
        existing.remove(path)
    }
}

class FakeArtworkRepository : ArtworkRepository {
    val items = MutableStateFlow<Map<String, Artwork>>(emptyMap())

    override fun observeArtworks(sort: GallerySort, favoritesOnly: Boolean): Flow<List<Artwork>> = items.map { map ->
        map.values.filter { !favoritesOnly || it.isFavorite }
            .let { list -> if (sort == GallerySort.NEWEST) list.sortedByDescending { it.createdAt } else list.sortedBy { it.createdAt } }
    }
    override fun observeArtwork(id: String): Flow<Artwork?> = items.map { it[id] }
    override suspend fun getArtwork(id: String) = items.value[id]
    override suspend fun upsert(artwork: Artwork) {
        items.value = items.value + (artwork.id to artwork)
    }
    override suspend fun rename(id: String, title: String) = update(id) { it.copy(title = title) }
    override suspend fun setFavorite(id: String, favorite: Boolean) = update(id) { it.copy(isFavorite = favorite) }
    override suspend fun updateThumbnail(id: String, path: String?) = update(id) { it.copy(thumbnailPath = path) }
    override suspend fun delete(id: String) {
        items.value = items.value - id
    }
    override suspend fun allArtworks() = items.value.values.toList()

    private fun update(id: String, transform: (Artwork) -> Artwork) {
        items.value[id]?.let { items.value = items.value + (id to transform(it)) }
    }
}

class FakePreferences(initial: UserPreferences = UserPreferences()) : PreferencesRepository {
    val state = MutableStateFlow(initial)
    override val preferences: Flow<UserPreferences> = state
    override suspend fun setOnboardingCompleted(completed: Boolean) { state.value = state.value.copy(onboardingCompleted = completed) }
    override suspend fun setKeepOriginalAudio(keep: Boolean) { state.value = state.value.copy(keepOriginalAudio = keep) }
    override suspend fun setWatermarkExports(enabled: Boolean) { state.value = state.value.copy(watermarkExports = enabled) }
    override suspend fun setReduceMotion(enabled: Boolean) { state.value = state.value.copy(reduceMotion = enabled) }
}

class FakeEntitlements(unlocked: Set<PremiumFeature> = PremiumFeature.entries.toSet()) : FeatureEntitlements {
    override val unlocked: StateFlow<Set<PremiumFeature>> = MutableStateFlow(unlocked)
    override fun isUnlocked(feature: PremiumFeature) = feature in unlocked.value
}

class FakeExporter : ArtworkExporter {
    var imageFailure: Exception? = null
    var lastImage: Triple<String, ExportImageSize, Boolean>? = null
    var lastVideo: Triple<Int, Boolean, Boolean>? = null
    /** When true the video flow suspends forever after the first progress, to test cancellation. */
    var hangVideo = false
    var videoCancelled = false

    override suspend fun exportImage(artwork: Artwork, size: ExportImageSize, watermark: Boolean): ExportedFile {
        imageFailure?.let { throw it }
        lastImage = Triple(artwork.id, size, watermark)
        return ExportedFile("/exports/${artwork.id}.png", "image/png")
    }

    override fun exportVideo(artwork: Artwork, durationSec: Int, includeAudio: Boolean, watermark: Boolean): Flow<VideoExportProgress> = flow {
        lastVideo = Triple(durationSec, includeAudio, watermark)
        emit(VideoExportProgress.Rendering(0.5f))
        if (hangVideo) {
            try {
                awaitCancellation()
            } finally {
                videoCancelled = true
            }
        }
        emit(VideoExportProgress.Done(ExportedFile("/exports/${artwork.id}.mp4", "video/mp4")))
    }

    override suspend fun saveToDevice(file: ExportedFile) = true
}

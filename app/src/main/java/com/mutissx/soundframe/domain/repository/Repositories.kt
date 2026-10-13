package com.mutissx.soundframe.domain.repository

import com.mutissx.soundframe.domain.model.Artwork
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioClip
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.AudioSource
import com.mutissx.soundframe.domain.model.CreationSession
import com.mutissx.soundframe.domain.model.ExportImageSize
import com.mutissx.soundframe.domain.model.GallerySort
import com.mutissx.soundframe.domain.model.PremiumFeature
import com.mutissx.soundframe.domain.model.TitleVocabulary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Live microphone level published while recording. */
data class RecorderLevel(
    /** Perceptual loudness 0..1 of the most recent buffer. */
    val amplitude: Float = 0f,
    val elapsedMs: Long = 0,
    /** Rolling history of recent amplitudes, oldest first. */
    val history: List<Float> = emptyList(),
    /** True when the OS silences our capture because another app owns the microphone. */
    val silenced: Boolean = false,
)

interface AudioRecorder {
    val level: StateFlow<RecorderLevel>

    /**
     * Records until [maxDurationMs] elapses or [requestStop] is called, then returns the clip.
     * Cancelling the calling coroutine stops the capture and releases the microphone.
     * @throws com.mutissx.soundframe.domain.model.RecordingException
     */
    suspend fun record(maxDurationMs: Long): AudioClip

    fun requestStop()
}

interface AudioDecoder {
    /**
     * Decodes the document at [uri] to mono PCM at [AudioClip.DEFAULT_SAMPLE_RATE], truncated to
     * [maxDurationMs]. @throws com.mutissx.soundframe.domain.model.ImportException
     */
    suspend fun decode(uri: String, maxDurationMs: Long): AudioClip
}

interface AudioAnalyzer {
    suspend fun analyze(clip: AudioClip): AudioFeatures
}

/** App-private persistence of audio clips. Paths are opaque strings to the domain. */
interface AudioStore {
    suspend fun save(name: String, clip: AudioClip, temporary: Boolean): String
    suspend fun load(path: String): AudioClip
    suspend fun exists(path: String): Boolean
    suspend fun delete(path: String)
}

interface AudioPlayer {
    val isPlaying: StateFlow<Boolean>
    /** Playback progress 0..1. */
    val progress: StateFlow<Float>
    fun play(path: String)
    fun stop()
    fun release()
}

interface CreationSessionRepository {
    suspend fun create(clip: AudioClip, source: AudioSource): CreationSession
    suspend fun get(id: String): CreationSession?
    suspend fun attachFeatures(id: String, features: AudioFeatures)
    suspend fun discard(id: String)
}

interface ArtworkRepository {
    fun observeArtworks(sort: GallerySort, favoritesOnly: Boolean = false): Flow<List<Artwork>>
    fun observeArtwork(id: String): Flow<Artwork?>
    suspend fun getArtwork(id: String): Artwork?
    suspend fun upsert(artwork: Artwork)
    suspend fun rename(id: String, title: String)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun updateThumbnail(id: String, path: String?)
    suspend fun delete(id: String)
    suspend fun allArtworks(): List<Artwork>
}

/** Renders artwork previews to app-private files. */
interface ThumbnailRenderer {
    suspend fun render(id: String, features: AudioFeatures, settings: ArtworkSettings): String
    suspend fun exists(path: String): Boolean
    suspend fun delete(path: String)
}

data class ExportedFile(val path: String, val mimeType: String)

sealed interface VideoExportProgress {
    data class Rendering(val fraction: Float) : VideoExportProgress
    data class Done(val file: ExportedFile) : VideoExportProgress
}

interface ArtworkExporter {
    suspend fun exportImage(artwork: Artwork, size: ExportImageSize, watermark: Boolean): ExportedFile

    /** Cold flow: collecting renders the video, cancelling the collection aborts and cleans up. */
    fun exportVideo(artwork: Artwork, durationSec: Int, includeAudio: Boolean, watermark: Boolean): Flow<VideoExportProgress>

    /** Copies an exported file into the user's shared Pictures/Movies collection. */
    suspend fun saveToDevice(file: ExportedFile): Boolean
}

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val keepOriginalAudio: Boolean = true,
    val watermarkExports: Boolean = true,
    val reduceMotion: Boolean = false,
)

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setKeepOriginalAudio(keep: Boolean)
    suspend fun setWatermarkExports(enabled: Boolean)
    suspend fun setReduceMotion(enabled: Boolean)
}

/** Abstraction that lets billing be added later without touching features. */
interface FeatureEntitlements {
    val unlocked: Flow<Set<PremiumFeature>>
    fun isUnlocked(feature: PremiumFeature): Boolean
}

fun interface Clock {
    fun now(): Long
}

fun interface IdGenerator {
    fun newId(): String
}

/** Supplies generated-title words in the user's language. */
fun interface TitleVocabularyProvider {
    fun vocabulary(): TitleVocabulary
}

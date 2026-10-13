package com.mutissx.soundframe.domain.usecase

import com.mutissx.soundframe.domain.model.Artwork
import com.mutissx.soundframe.domain.model.ArtworkNotFoundException
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.ExportImageSize
import com.mutissx.soundframe.domain.model.PremiumFeature
import com.mutissx.soundframe.domain.model.SessionExpiredException
import com.mutissx.soundframe.domain.repository.ArtworkExporter
import com.mutissx.soundframe.domain.repository.ArtworkRepository
import com.mutissx.soundframe.domain.repository.AudioStore
import com.mutissx.soundframe.domain.repository.Clock
import com.mutissx.soundframe.domain.repository.CreationSessionRepository
import com.mutissx.soundframe.domain.repository.ExportedFile
import com.mutissx.soundframe.domain.repository.FeatureEntitlements
import com.mutissx.soundframe.domain.repository.IdGenerator
import com.mutissx.soundframe.domain.repository.PreferencesRepository
import com.mutissx.soundframe.domain.repository.ThumbnailRenderer
import com.mutissx.soundframe.domain.repository.TitleVocabularyProvider
import com.mutissx.soundframe.domain.repository.VideoExportProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

object TitleRules {
    const val MAX_LENGTH = 48

    fun normalize(raw: String): String? =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH).takeIf { it.isNotEmpty() }
}

class SaveArtworkUseCase(
    private val artworks: ArtworkRepository,
    private val sessions: CreationSessionRepository,
    private val audioStore: AudioStore,
    private val thumbnails: ThumbnailRenderer,
    private val preferences: PreferencesRepository,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val vocabulary: TitleVocabularyProvider,
) {
    /** Persists a brand-new artwork created from a session. */
    suspend fun saveNew(sessionId: String, title: String, settings: ArtworkSettings): Artwork {
        val session = sessions.get(sessionId) ?: throw SessionExpiredException()
        val features = session.features ?: throw SessionExpiredException()
        val id = ids.newId()
        val keepAudio = preferences.preferences.first().keepOriginalAudio
        val audioPath = if (keepAudio) audioStore.save(id, session.clip, temporary = false) else null
        val thumb = thumbnails.render(id, features, settings)
        val now = clock.now()
        val artwork = Artwork(
            id = id,
            title = TitleRules.normalize(title) ?: ArtDirection.suggestTitle(features, settings.style, vocabulary.vocabulary()),
            createdAt = now,
            updatedAt = now,
            features = features,
            settings = settings,
            audioPath = audioPath,
            thumbnailPath = thumb,
            isFavorite = false,
            engineVersion = VisualParamMapper.ENGINE_VERSION,
        )
        artworks.upsert(artwork)
        return artwork
    }

    /** Updates the look and title of an existing artwork. Audio and features never change. */
    suspend fun update(id: String, title: String, settings: ArtworkSettings): Artwork {
        val existing = artworks.getArtwork(id) ?: throw ArtworkNotFoundException(id)
        val thumb = thumbnails.render(id, existing.features, settings)
        val updated = existing.copy(
            title = TitleRules.normalize(title) ?: existing.title,
            settings = settings,
            thumbnailPath = thumb,
            updatedAt = clock.now(),
            engineVersion = VisualParamMapper.ENGINE_VERSION,
        )
        artworks.upsert(updated)
        return updated
    }
}

class RenameArtworkUseCase(private val artworks: ArtworkRepository) {
    /** @return false when the title is empty after normalisation. */
    suspend operator fun invoke(id: String, raw: String): Boolean {
        val title = TitleRules.normalize(raw) ?: return false
        artworks.rename(id, title)
        return true
    }
}

class ToggleFavoriteUseCase(private val artworks: ArtworkRepository) {
    suspend operator fun invoke(id: String) {
        val artwork = artworks.getArtwork(id) ?: return
        artworks.setFavorite(id, !artwork.isFavorite)
    }
}

class DeleteArtworkUseCase(
    private val artworks: ArtworkRepository,
    private val audioStore: AudioStore,
    private val thumbnails: ThumbnailRenderer,
) {
    suspend operator fun invoke(id: String) {
        val artwork = artworks.getArtwork(id) ?: return
        artwork.audioPath?.let { audioStore.delete(it) }
        artwork.thumbnailPath?.let { thumbnails.delete(it) }
        artworks.delete(id)
    }
}

class DeleteAllDataUseCase(
    private val artworks: ArtworkRepository,
    private val deleteArtwork: DeleteArtworkUseCase,
) {
    suspend operator fun invoke() {
        artworks.allArtworks().forEach { deleteArtwork(it.id) }
    }
}

/** Regenerates a thumbnail if its file was lost (e.g. cleared storage or corruption). */
class EnsureThumbnailUseCase(
    private val artworks: ArtworkRepository,
    private val thumbnails: ThumbnailRenderer,
) {
    suspend operator fun invoke(artwork: Artwork): String {
        val current = artwork.thumbnailPath
        if (current != null && thumbnails.exists(current)) return current
        val path = thumbnails.render(artwork.id, artwork.features, artwork.settings)
        artworks.updateThumbnail(artwork.id, path)
        return path
    }
}

/** Audio may have been deleted or never kept; the UI hides playback in that case. */
class ResolvePlayableAudioUseCase(private val audioStore: AudioStore) {
    suspend operator fun invoke(artwork: Artwork): String? =
        artwork.audioPath?.takeIf { audioStore.exists(it) }
}

class ExportImageUseCase(
    private val exporter: ArtworkExporter,
    private val entitlements: FeatureEntitlements,
) {
    suspend operator fun invoke(artwork: Artwork, size: ExportImageSize, watermark: Boolean): ExportedFile {
        val allowedSize = if (size == ExportImageSize.ULTRA && !entitlements.isUnlocked(PremiumFeature.ULTRA_RESOLUTION_EXPORT)) {
            ExportImageSize.SQUARE
        } else size
        val brand = watermark || !entitlements.isUnlocked(PremiumFeature.BRANDING_REMOVAL)
        return exporter.exportImage(artwork, allowedSize, brand)
    }
}

class ExportVideoUseCase(
    private val exporter: ArtworkExporter,
    private val entitlements: FeatureEntitlements,
) {
    val isAvailable: Boolean get() = entitlements.isUnlocked(PremiumFeature.VIDEO_EXPORT)

    operator fun invoke(artwork: Artwork, durationSec: Int, includeAudio: Boolean, watermark: Boolean): Flow<VideoExportProgress> {
        val brand = watermark || !entitlements.isUnlocked(PremiumFeature.BRANDING_REMOVAL)
        return exporter.exportVideo(artwork, durationSec.coerceIn(MIN_SECONDS, MAX_SECONDS), includeAudio && artwork.audioPath != null, brand)
    }

    companion object {
        const val MIN_SECONDS = 3
        const val MAX_SECONDS = 10
    }
}

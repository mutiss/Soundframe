package com.mutissx.soundframe.data.mapper

import com.mutissx.soundframe.data.local.ArtworkEntity
import com.mutissx.soundframe.domain.model.Artwork
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioFeatures
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object ArtworkMapper {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    fun toEntity(artwork: Artwork) = ArtworkEntity(
        id = artwork.id,
        title = artwork.title,
        createdAt = artwork.createdAt,
        updatedAt = artwork.updatedAt,
        featuresJson = json.encodeToString(AudioFeatures.serializer(), artwork.features),
        settingsJson = json.encodeToString(ArtworkSettings.serializer(), artwork.settings),
        audioPath = artwork.audioPath,
        thumbnailPath = artwork.thumbnailPath,
        isFavorite = artwork.isFavorite,
        engineVersion = artwork.engineVersion,
    )

    /** @return null for rows whose JSON is corrupt, so one bad row can't break the gallery. */
    fun toDomain(entity: ArtworkEntity): Artwork? = try {
        Artwork(
            id = entity.id,
            title = entity.title,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            features = json.decodeFromString(AudioFeatures.serializer(), entity.featuresJson),
            settings = json.decodeFromString(ArtworkSettings.serializer(), entity.settingsJson),
            audioPath = entity.audioPath,
            thumbnailPath = entity.thumbnailPath,
            isFavorite = entity.isFavorite,
            engineVersion = entity.engineVersion,
        )
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

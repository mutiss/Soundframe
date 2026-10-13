package com.mutissx.soundframe.domain.model

import kotlinx.serialization.Serializable

/** The five generative engines. Order is the order shown in the editor. User-facing names live in resources. */
@Serializable
enum class ArtStyle { AURA, BLOOM, PARTICLES, TOPOGRAPHY, ECHO }

@Serializable
enum class PaletteId { AURORA, SOLAR_FLARE, DEEP_OCEAN, ROSE_QUARTZ, ELECTRIC_VIOLET, NOCTURNE, MONOCHROME }

@Serializable
enum class BackgroundTone(val isLight: Boolean) {
    VOID(false),
    MIDNIGHT(false),
    EMBER(false),
    PEARL(true),
}

/** User-editable rendering choices. Never mutates the underlying [AudioFeatures]. */
@Serializable
data class ArtworkSettings(
    val style: ArtStyle = ArtStyle.AURA,
    val palette: PaletteId = PaletteId.AURORA,
    val background: BackgroundTone = BackgroundTone.VOID,
    /** 0..1, 0.5 is the neutral, audio-derived intensity. */
    val intensity: Float = 0.5f,
    /** 0..1, 0.5 is the neutral, audio-derived complexity. */
    val complexity: Float = 0.5f,
    /** Deterministic variation index; 0 is the canonical artwork for a recording. */
    val variation: Int = 0,
    val animate: Boolean = true,
)

data class Artwork(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val features: AudioFeatures,
    val settings: ArtworkSettings,
    val audioPath: String?,
    val thumbnailPath: String?,
    val isFavorite: Boolean,
    val engineVersion: Int,
)

enum class GallerySort { NEWEST, OLDEST }

enum class ExportImageSize(val width: Int, val height: Int) {
    SQUARE(1080, 1080),
    STORY(1080, 1920),
    ULTRA(2048, 2048),
}

/** Source of a creation session: freshly recorded or imported from a document. */
enum class AudioSource { RECORDED, IMPORTED }

/**
 * Localised words for generated titles. [pattern] receives the adjective as `%1$s` and the noun as
 * `%2$s`, so languages can choose their own word order.
 */
data class TitleVocabulary(
    val pattern: String,
    val quietDark: List<String>,
    val quietBright: List<String>,
    val loudDark: List<String>,
    val loudBright: List<String>,
    val nouns: Map<ArtStyle, List<String>>,
) {
    companion object {
        val ENGLISH = TitleVocabulary(
            pattern = "%1\$s %2\$s",
            quietDark = listOf("Velvet", "Hushed", "Deep", "Slow"),
            quietBright = listOf("Silver", "Glass", "Pale", "Whispered"),
            loudDark = listOf("Thunder", "Molten", "Heavy", "Ember"),
            loudBright = listOf("Electric", "Radiant", "Wild", "Golden"),
            nouns = mapOf(
                ArtStyle.AURA to listOf("Drift", "Veil", "Current", "Ribbon"),
                ArtStyle.BLOOM to listOf("Bloom", "Petal", "Orchid", "Corolla"),
                ArtStyle.PARTICLES to listOf("Constellation", "Swarm", "Dust", "Rain"),
                ArtStyle.TOPOGRAPHY to listOf("Terrain", "Tide", "Ridge", "Contour"),
                ArtStyle.ECHO to listOf("Echo", "Ripple", "Pulse", "Resonance"),
            ),
        )
    }
}

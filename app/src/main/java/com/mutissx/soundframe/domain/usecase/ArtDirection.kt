package com.mutissx.soundframe.domain.usecase

import com.mutissx.soundframe.domain.model.AnalysisQuality
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.PaletteId
import com.mutissx.soundframe.domain.model.TitleVocabulary

/**
 * Chooses the opening style, palette and title for a new recording, so the very first artwork the
 * user sees already reflects the character of the sound.
 */
object ArtDirection {

    /** About -66 dBFS: below this a recording carries no usable sound at all. */
    private const val DIGITAL_SILENCE_RMS = 0.0005f

    fun recommendSettings(features: AudioFeatures): ArtworkSettings {
        val params = VisualParamMapper.map(features, ArtworkSettings())
        val regularity = features.rhythmicRegularity
        val style = when {
            regularity != null && regularity > 0.6f && features.transientDensity in 0.6f..4.5f -> ArtStyle.ECHO
            params.turbulence > 0.55f && features.transientDensity > 2f -> ArtStyle.PARTICLES
            params.turbulence > 0.45f -> ArtStyle.TOPOGRAPHY
            features.transientDensity > 1.5f -> ArtStyle.BLOOM
            else -> ArtStyle.AURA
        }
        val palette = when {
            features.quality == AnalysisQuality.MOSTLY_SILENT && features.rms < DIGITAL_SILENCE_RMS -> PaletteId.MONOCHROME
            features.lowFrequencyEnergy > 0.5f && params.energy < 0.55f -> PaletteId.DEEP_OCEAN
            params.energy > 0.65f && params.brightness < 0.6f -> PaletteId.SOLAR_FLARE
            features.transientDensity > 2.5f -> PaletteId.ELECTRIC_VIOLET
            params.turbulence < 0.25f && params.brightness > 0.5f -> PaletteId.ROSE_QUARTZ
            params.brightness < 0.3f -> PaletteId.NOCTURNE
            else -> PaletteId.AURORA
        }
        return ArtworkSettings(style = style, palette = palette)
    }

    /** Deterministic poetic title: an adjective chosen by loudness/brightness, a noun by style. */
    fun suggestTitle(features: AudioFeatures, style: ArtStyle, vocabulary: TitleVocabulary = TitleVocabulary.ENGLISH): String {
        val params = VisualParamMapper.map(features, ArtworkSettings(style = style))
        val pick = (params.seed ushr 8).toInt() and 0x7fffffff
        val adjectives = when {
            params.energy < 0.5f && params.brightness < 0.5f -> vocabulary.quietDark
            params.energy < 0.5f -> vocabulary.quietBright
            params.brightness < 0.5f -> vocabulary.loudDark
            else -> vocabulary.loudBright
        }
        val nouns = vocabulary.nouns[style].orEmpty().ifEmpty { TitleVocabulary.ENGLISH.nouns.getValue(style) }
        val adjective = adjectives.ifEmpty { TitleVocabulary.ENGLISH.quietDark }.let { it[pick % it.size] }
        return vocabulary.pattern.format(adjective, nouns[(pick / 7) % nouns.size])
    }
}

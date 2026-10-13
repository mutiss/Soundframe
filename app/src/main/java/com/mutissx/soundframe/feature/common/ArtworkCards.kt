package com.mutissx.soundframe.feature.common

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.audio.DemoSignal
import com.mutissx.soundframe.core.common.Format
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.Artwork
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.BackgroundTone
import com.mutissx.soundframe.domain.model.PaletteId

/** Lightweight, immutable projection of an artwork for lists. */
@Immutable
data class ArtworkCardModel(
    val id: String,
    val title: String,
    val date: String,
    val style: ArtStyle,
    val thumbnailPath: String?,
    val isFavorite: Boolean,
)

fun Artwork.toCard() = ArtworkCardModel(
    id = id,
    title = title,
    date = Format.date(createdAt),
    style = settings.style,
    thumbnailPath = thumbnailPath,
    isFavorite = isFavorite,
)

/** Demo compositions shown before the user has recorded anything. */
@Immutable
data class DemoComposition(val preset: DemoSignal.Preset, val settings: ArtworkSettings, @StringRes val caption: Int)

val DemoCompositions = listOf(
    DemoComposition(DemoSignal.Preset.VOICE, ArtworkSettings(ArtStyle.AURA, PaletteId.AURORA, complexity = 0.6f), R.string.demo_voice),
    DemoComposition(DemoSignal.Preset.MUSIC, ArtworkSettings(ArtStyle.BLOOM, PaletteId.SOLAR_FLARE, BackgroundTone.EMBER), R.string.demo_song),
    DemoComposition(DemoSignal.Preset.RAIN, ArtworkSettings(ArtStyle.PARTICLES, PaletteId.ELECTRIC_VIOLET, BackgroundTone.MIDNIGHT), R.string.demo_rain),
    DemoComposition(DemoSignal.Preset.OCEAN, ArtworkSettings(ArtStyle.TOPOGRAPHY, PaletteId.DEEP_OCEAN, BackgroundTone.MIDNIGHT), R.string.demo_ocean),
    DemoComposition(DemoSignal.Preset.HEARTBEAT, ArtworkSettings(ArtStyle.ECHO, PaletteId.ROSE_QUARTZ), R.string.demo_heartbeat),
)

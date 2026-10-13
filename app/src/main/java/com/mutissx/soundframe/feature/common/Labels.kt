package com.mutissx.soundframe.feature.common

import androidx.annotation.StringRes
import com.mutissx.soundframe.R
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.BackgroundTone
import com.mutissx.soundframe.domain.model.ExportImageSize
import com.mutissx.soundframe.domain.model.PaletteId

/** User-facing names for domain enums; the domain itself stays free of UI text. */
@get:StringRes
val ArtStyle.label: Int
    get() = when (this) {
        ArtStyle.AURA -> R.string.style_aura
        ArtStyle.BLOOM -> R.string.style_bloom
        ArtStyle.PARTICLES -> R.string.style_particles
        ArtStyle.TOPOGRAPHY -> R.string.style_topography
        ArtStyle.ECHO -> R.string.style_echo
    }

@get:StringRes
val PaletteId.label: Int
    get() = when (this) {
        PaletteId.AURORA -> R.string.palette_aurora
        PaletteId.SOLAR_FLARE -> R.string.palette_solar_flare
        PaletteId.DEEP_OCEAN -> R.string.palette_deep_ocean
        PaletteId.ROSE_QUARTZ -> R.string.palette_rose_quartz
        PaletteId.ELECTRIC_VIOLET -> R.string.palette_electric_violet
        PaletteId.NOCTURNE -> R.string.palette_nocturne
        PaletteId.MONOCHROME -> R.string.palette_monochrome
    }

@get:StringRes
val BackgroundTone.label: Int
    get() = when (this) {
        BackgroundTone.VOID -> R.string.ground_void
        BackgroundTone.MIDNIGHT -> R.string.ground_midnight
        BackgroundTone.EMBER -> R.string.ground_ember
        BackgroundTone.PEARL -> R.string.ground_pearl
    }

@get:StringRes
val ExportImageSize.label: Int
    get() = when (this) {
        ExportImageSize.SQUARE -> R.string.export_square
        ExportImageSize.STORY -> R.string.export_story
        ExportImageSize.ULTRA -> R.string.export_ultra
    }

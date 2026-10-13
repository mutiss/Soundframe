package com.mutissx.soundframe.core.graphics

import android.graphics.BlendMode
import android.graphics.Canvas
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.BackgroundTone
import com.mutissx.soundframe.domain.model.VisualParams
import com.mutissx.soundframe.domain.usecase.VisualParamMapper

/** Everything a renderer needs to know about a composition. Immutable. */
class ArtScene(
    val params: VisualParams,
    val palette: Palette,
    val background: BackgroundTone,
) {
    val isLight: Boolean get() = background.isLight

    /** Additive light on dark grounds, ink-like multiply on light ones. */
    val glowBlend: BlendMode get() = if (isLight) BlendMode.MULTIPLY else BlendMode.SCREEN

    /**
     * Palette lookup shaped by the sound: bright sounds sit on the luminous end of the palette,
     * wide-band sounds use more of its range.
     */
    fun color(t: Float): Int {
        val range = 0.55f + 0.45f * params.spread
        val offset = (1f - params.brightness) * 0.45f
        val c = palette.sample(t * range + offset)
        return if (isLight) ColorUtil.lerp(c, INK, 0.28f) else c
    }

    companion object {
        private const val INK = 0xFF1A1622.toInt()

        fun from(features: AudioFeatures, settings: ArtworkSettings): ArtScene = ArtScene(
            params = VisualParamMapper.map(features, settings),
            palette = Palettes.of(settings.palette),
            background = settings.background,
        )
    }
}

/**
 * Geometry built once for a given scene and size. Drawing must be cheap: [drawStatic] holds the
 * time-independent layer (cached by the UI in an offscreen layer), [drawDynamic] runs every frame
 * and only transforms or samples precomputed data. Not thread-safe: draw from one thread.
 */
interface PreparedArt {
    val width: Int
    val height: Int
    fun drawStatic(canvas: Canvas)
    fun drawDynamic(canvas: Canvas, timeSec: Float)
}

fun interface ArtRenderer {
    /** Heavy work lives here and may run on a background thread. */
    fun prepare(scene: ArtScene, width: Int, height: Int): PreparedArt
}

object ArtRenderers {
    fun of(style: ArtStyle): ArtRenderer = when (style) {
        ArtStyle.AURA -> AuraRenderer
        ArtStyle.BLOOM -> BloomRenderer
        ArtStyle.PARTICLES -> ParticlesRenderer
        ArtStyle.TOPOGRAPHY -> TopographyRenderer
        ArtStyle.ECHO -> EchoRenderer
    }
}

/** Grows without per-frame allocation once warmed up. */
internal class FloatList(initial: Int = 1024) {
    var data = FloatArray(initial)
        private set
    var size = 0
        private set

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = v
    }

    fun add4(a: Float, b: Float, c: Float, d: Float) {
        if (size + 4 > data.size) data = data.copyOf(maxOf(data.size * 2, size + 4))
        data[size++] = a; data[size++] = b; data[size++] = c; data[size++] = d
    }

    fun clear() { size = 0 }

    fun toArray(): FloatArray = data.copyOf(size)
}

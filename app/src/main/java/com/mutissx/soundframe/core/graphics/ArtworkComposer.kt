package com.mutissx.soundframe.core.graphics

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import com.mutissx.soundframe.domain.model.ArtStyle
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Assembles background + style + optional signature into one [PreparedArt]. The same code path is
 * used for live previews, thumbnails, PNG and MP4 export, so exports always match what the user saw.
 */
object ArtworkComposer {

    /** Canonical moment used for stills (thumbnails, PNG) and for previews with animation off. */
    const val STILL_TIME = 0f

    fun prepare(
        style: ArtStyle,
        scene: ArtScene,
        width: Int,
        height: Int,
        signature: Typeface? = null,
        withSignature: Boolean = false,
    ): PreparedArt {
        val art = ArtRenderers.of(style).prepare(scene, width, height)
        val background = BackgroundLayer(scene, width, height)
        val mark = if (withSignature) SignatureLayer(scene, width, height, signature) else null
        return object : PreparedArt {
            override val width = width
            override val height = height
            override fun drawStatic(canvas: Canvas) {
                background.draw(canvas)
                art.drawStatic(canvas)
            }

            override fun drawDynamic(canvas: Canvas, timeSec: Float) {
                art.drawDynamic(canvas, timeSec)
                mark?.draw(canvas)
            }
        }
    }

    /** Background only (base, glows, vignette) — for compositions that place the art themselves. */
    fun drawBackground(scene: ArtScene, canvas: Canvas, width: Int, height: Int) =
        BackgroundLayer(scene, width, height).draw(canvas)

    /** Draws a single, complete frame. Used by thumbnail and image export. */
    fun drawFrame(art: PreparedArt, canvas: Canvas, timeSec: Float) {
        art.drawStatic(canvas)
        art.drawDynamic(canvas, timeSec)
    }
}

private class BackgroundLayer(scene: ArtScene, private val w: Int, private val h: Int) {
    private val base = Backgrounds.base(scene.background)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val glowA: Shader
    private val glowB: Shader
    private val vignette: Shader

    init {
        val diag = hypot(w.toFloat(), h.toFloat())
        val glowAlpha = if (scene.isLight) 0.10f else 0.20f + 0.10f * scene.params.energy
        glowA = RadialGradient(
            w * 0.22f, h * 0.18f, diag * 0.55f,
            intArrayOf(ColorUtil.withAlpha(scene.color(0.15f), glowAlpha), ColorUtil.withAlpha(scene.color(0.15f), 0f)),
            null, Shader.TileMode.CLAMP,
        )
        glowB = RadialGradient(
            w * 0.82f, h * 0.86f, diag * 0.5f,
            intArrayOf(ColorUtil.withAlpha(scene.color(0.85f), glowAlpha * 0.8f), ColorUtil.withAlpha(scene.color(0.85f), 0f)),
            null, Shader.TileMode.CLAMP,
        )
        val deep = Backgrounds.deep(scene.background)
        vignette = RadialGradient(
            w / 2f, h / 2f, diag * 0.62f,
            intArrayOf(ColorUtil.withAlpha(deep, 0f), ColorUtil.withAlpha(deep, 0f), ColorUtil.withAlpha(deep, if (scene.isLight) 0.35f else 0.75f)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
        )
    }

    fun draw(canvas: Canvas) {
        canvas.drawColor(base)
        paint.shader = glowA
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = glowB
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = vignette
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null
    }
}

/** Subtle SOUNDFRAME signature for exported compositions. */
private class SignatureLayer(scene: ArtScene, w: Int, h: Int, typeface: Typeface?) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        fontVariationSettings = "'wght' 700"
        textSize = min(w, h) * 0.022f
        letterSpacing = 0.42f
        color = if (scene.isLight) 0x991A1622.toInt() else 0x99FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = scene.color(0.3f) }
    private val x = w / 2f
    private val y = h - max(h * 0.045f, paint.textSize * 2.2f)

    fun draw(canvas: Canvas) {
        val text = "SOUNDFRAME"
        val half = paint.measureText(text) / 2f
        canvas.drawText(text, x, y, paint)
        canvas.drawCircle(x - half - paint.textSize * 0.9f, y - paint.textSize * 0.34f, paint.textSize * 0.22f, dot)
    }
}

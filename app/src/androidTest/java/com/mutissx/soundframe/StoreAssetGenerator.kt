package com.mutissx.soundframe

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mutissx.soundframe.core.audio.DemoSignal
import com.mutissx.soundframe.core.graphics.ArtRenderers
import com.mutissx.soundframe.core.graphics.ArtScene
import com.mutissx.soundframe.core.graphics.ArtworkComposer
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.BackgroundTone
import com.mutissx.soundframe.domain.model.PaletteId
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders Play Store graphics with the real generative engine. Skipped unless run with
 * `-e storeAssets true`. Output goes to the app's private files dir (`files/store/`).
 */
@RunWith(AndroidJUnit4::class)
class StoreAssetGenerator {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val out = File(context.filesDir, "store").apply { mkdirs() }

    private fun art(preset: DemoSignal.Preset, settings: ArtworkSettings, w: Int, h: Int, time: Float = 0f): Bitmap {
        val features = DemoSignal.features(preset)
        val prepared = ArtworkComposer.prepare(settings.style, ArtScene.from(features, settings), w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { ArtworkComposer.drawFrame(prepared, Canvas(it), time) }
    }

    private fun save(bitmap: Bitmap, name: String) {
        File(out, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun generate() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeAssets") == "true")

        // Icon candidates (512 × 512, full-bleed; Play applies its own corner mask).
        val candidates = mapOf(
            "icon_bloom" to (DemoSignal.Preset.VOICE to ArtworkSettings(ArtStyle.BLOOM, PaletteId.AURORA, BackgroundTone.VOID, complexity = 0.35f)),
            "icon_echo" to (DemoSignal.Preset.HEARTBEAT to ArtworkSettings(ArtStyle.ECHO, PaletteId.AURORA, BackgroundTone.VOID, intensity = 0.75f)),
            "icon_bloom_violet" to (DemoSignal.Preset.MUSIC to ArtworkSettings(ArtStyle.BLOOM, PaletteId.ELECTRIC_VIOLET, BackgroundTone.MIDNIGHT, complexity = 0.3f)),
            "icon_aura" to (DemoSignal.Preset.VOICE to ArtworkSettings(ArtStyle.AURA, PaletteId.AURORA, BackgroundTone.VOID, complexity = 0.3f)),
        )
        candidates.forEach { (name, spec) -> save(art(spec.first, spec.second, 512, 512), "$name.png") }

        // Adaptive launcher icon layer: 108 dp at xxxhdpi = 432 px, of which launchers show only the
        // central 72 dp (288 px). The composition is rendered at that safe size and centred.
        val (preset, settings) = candidates.getValue("icon_bloom")
        val scene = ArtScene.from(DemoSignal.features(preset), settings)
        val flower = ArtRenderers.of(settings.style).prepare(scene, 288, 288)
        val launcher = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        Canvas(launcher).apply {
            ArtworkComposer.drawBackground(scene, this, 432, 432)
            translate(72f, 72f)
            flower.drawStatic(this)
            flower.drawDynamic(this, 0f)
        }
        save(launcher, "launcher_background_432.png")

        val serif = ResourcesCompat.getFont(context, R.font.instrument_serif_regular)
        val sans = ResourcesCompat.getFont(context, R.font.manrope_variable)
        featureGraphic("feature_graphic_en.png", "Every sound", "has a shape.", "Turn sound into generative art", serif, sans)
        featureGraphic("feature_graphic_es.png", "Cada sonido", "tiene una forma.", "Convierte el sonido en arte generativo", serif, sans)
    }

    /** 1024 × 500: a wide Aura composition with an editorial headline over a soft scrim. */
    private fun featureGraphic(name: String, line1: String, line2: String, caption: String, serif: Typeface?, sans: Typeface?) {
        val w = 1024
        val h = 500
        val bitmap = art(
            DemoSignal.Preset.VOICE,
            ArtworkSettings(ArtStyle.AURA, PaletteId.AURORA, BackgroundTone.VOID, intensity = 0.7f, complexity = 0.7f),
            w, h, time = 2.5f,
        )
        val canvas = Canvas(bitmap)
        val scrim = Paint().apply {
            shader = LinearGradient(0f, 0f, w * 0.72f, 0f, intArrayOf(0xF2040406.toInt(), 0x99040406.toInt(), 0x00040406), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), scrim)

        val left = 64f
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sans
            fontVariationSettings = "'wght' 800"
            textSize = 22f
            letterSpacing = 0.42f
            color = 0xFFF5F3FA.toInt()
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(left, 0f, left + 14f, 14f, intArrayOf(0xFF6CF5D2.toInt(), 0xFFA35CFF.toInt()), null, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(left + 7f, 118f, 7f, dot)
        canvas.drawText("SOUNDFRAME", left + 26f, 126f, mark)

        val headline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = serif
            textSize = 84f
            color = 0xFFF5F3FA.toInt()
        }
        canvas.drawText(line1, left - 4f, 245f, headline)
        headline.shader = LinearGradient(left, 0f, left + 520f, 0f, intArrayOf(0xFF6CF5D2.toInt(), 0xFF5B7CFA.toInt(), 0xFFFF6AC8.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawText(line2, left - 4f, 330f, headline)

        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = sans
            fontVariationSettings = "'wght' 500"
            textSize = 26f
            color = 0xCCADA9BD.toInt()
        }
        canvas.drawText(caption, left, 392f, sub)
        save(bitmap, name)
    }
}

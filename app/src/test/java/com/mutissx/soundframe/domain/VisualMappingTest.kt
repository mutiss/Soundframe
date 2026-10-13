package com.mutissx.soundframe.domain

import com.mutissx.soundframe.domain.model.AnalysisQuality
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.PaletteId
import com.mutissx.soundframe.domain.model.VisualParams
import com.mutissx.soundframe.domain.usecase.ArtDirection
import com.mutissx.soundframe.domain.usecase.VisualParamMapper
import com.mutissx.soundframe.testing.Signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VisualMappingTest {

    private val voice = Signals.features(Signals.sine(220f, 2f, 0.3f) + Signals.sine(1800f, 2f, 0.1f))
    private val rain = Signals.features(Signals.whiteNoise(3f, 0.2f))
    private val heartbeat = Signals.features(Signals.clicks(0.8f, 4f))
    private val silence = Signals.features(Signals.silence(2f))

    private fun VisualParams.scalars() = listOf(energy, intensity, weight, detail, brightness, spread, contrast, turbulence, motion, stillness, complexity)

    @Test
    fun `mapping is deterministic`() {
        val settings = ArtworkSettings(style = ArtStyle.BLOOM, palette = PaletteId.NOCTURNE, variation = 3)
        assertEquals(VisualParamMapper.map(voice, settings), VisualParamMapper.map(voice, settings))
        // A re-analysis of the same audio reproduces the same artwork.
        val again = Signals.features(Signals.sine(220f, 2f, 0.3f) + Signals.sine(1800f, 2f, 0.1f))
        assertEquals(VisualParamMapper.map(voice, settings), VisualParamMapper.map(again, settings))
    }

    @Test
    fun `all parameters stay within bounds, even for silence and noise`() {
        listOf(voice, rain, heartbeat, silence).forEach { features ->
            listOf(0f, 0.5f, 1f).forEach { control ->
                val p = VisualParamMapper.map(features, ArtworkSettings(intensity = control, complexity = control))
                p.scalars().forEach { assertTrue("out of range: $it", it.isFinite() && it in 0f..1f) }
                assertTrue(p.repetition in 3..14)
                assertTrue(p.symmetry in 3..12)
                (p.envelope + p.flux + p.spectrum).forEach { assertTrue(it in 0f..1f) }
            }
        }
    }

    @Test
    fun `different sounds produce distinguishable parameters`() {
        val a = VisualParamMapper.map(voice, ArtworkSettings())
        val b = VisualParamMapper.map(rain, ArtworkSettings())
        val c = VisualParamMapper.map(heartbeat, ArtworkSettings())
        assertNotEquals(a.seed, b.seed)
        assertTrue("noise should be more turbulent", b.turbulence > a.turbulence + 0.3f)
        assertTrue("rain should be brighter than a voice", b.brightness > a.brightness)
        assertTrue("heartbeat clicks should create more repetition", c.repetition > a.repetition)
    }

    @Test
    fun `variations change the seed but not the sound-derived character`() {
        val base = VisualParamMapper.map(voice, ArtworkSettings(variation = 0))
        val varied = VisualParamMapper.map(voice, ArtworkSettings(variation = 1))
        assertNotEquals(base.seed, varied.seed)
        assertEquals(base.energy, varied.energy)
        assertEquals(base.brightness, varied.brightness)
        assertEquals(base.envelope, varied.envelope)
    }

    @Test
    fun `user controls are monotonic`() {
        val low = VisualParamMapper.map(voice, ArtworkSettings(intensity = 0.1f, complexity = 0.1f))
        val high = VisualParamMapper.map(voice, ArtworkSettings(intensity = 0.9f, complexity = 0.9f))
        assertTrue(high.intensity > low.intensity)
        assertTrue(high.complexity > low.complexity)
    }

    @Test
    fun `art direction reacts to the character of the sound`() {
        assertEquals(PaletteId.MONOCHROME, ArtDirection.recommendSettings(silence).palette)
        assertEquals(AnalysisQuality.MOSTLY_SILENT, silence.quality)
        assertEquals(ArtStyle.ECHO, ArtDirection.recommendSettings(heartbeat).style)
        val rainStyle = ArtDirection.recommendSettings(rain).style
        assertTrue(rainStyle == ArtStyle.TOPOGRAPHY || rainStyle == ArtStyle.PARTICLES)
        assertEquals(ArtDirection.suggestTitle(voice, ArtStyle.AURA), ArtDirection.suggestTitle(voice, ArtStyle.AURA))
    }

    @Test
    fun `a quiet room is sound, not silence`() {
        // Same level as a real recording of a quiet room on a Pixel 9 Pro XL (~-55 dBFS RMS).
        val room = Signals.features(Signals.whiteNoise(4f, amplitude = 0.003f))
        assertTrue("rms=${room.rms}", room.rms in 0.001f..0.003f)
        assertNotEquals(AnalysisQuality.MOSTLY_SILENT, room.quality)
        assertNotEquals(PaletteId.MONOCHROME, ArtDirection.recommendSettings(room).palette)
        assertTrue(VisualParamMapper.map(room, ArtworkSettings()).energy > 0.05f)
    }

    @Test
    fun `domain layer has no Android dependencies`() {
        val root = listOf("src/main/java/com/mutissx/soundframe/domain", "app/src/main/java/com/mutissx/soundframe/domain")
            .map(::File).first { it.exists() }
        val offenders = root.walkTopDown().filter { it.extension == "kt" }
            .filter { file -> file.readLines().any { it.startsWith("import android") || it.startsWith("import androidx") } }
            .map { it.name }.toList()
        assertTrue("Android imports in domain: $offenders", offenders.isEmpty())
    }
}

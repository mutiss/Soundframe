package com.mutissx.soundframe.core.audio

import com.mutissx.soundframe.domain.model.AnalysisQuality
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.testing.Signals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureExtractorTest {

    private fun AudioFeatures.assertFinite() {
        val scalars = listOf(
            rms, peakAmplitude, crestFactorDb, dynamicRangeDb, spectralCentroid, spectralBandwidth, spectralFlatness,
            lowFrequencyEnergy, midFrequencyEnergy, highFrequencyEnergy, zeroCrossingRate, transientDensity,
            silenceRatio, clippingRatio,
        )
        scalars.forEach { assertTrue("non-finite feature: $this", it.isFinite()) }
        (energyEnvelope + spectralFlux + spectrumProfile).forEach { assertTrue(it.isFinite() && it in 0f..1f) }
        assertEquals(AudioFeatures.ENVELOPE_POINTS, energyEnvelope.size)
        assertEquals(AudioFeatures.ENVELOPE_POINTS, spectralFlux.size)
        assertEquals(AudioFeatures.SPECTRUM_BANDS, spectrumProfile.size)
    }

    @Test
    fun `rms and peak of a sine are correct`() {
        val f = Signals.features(Signals.sine(440f, 2f, amplitude = 0.5f))
        assertEquals(0.5f / kotlin.math.sqrt(2f), f.rms, 0.005f)
        assertEquals(0.5f, f.peakAmplitude, 0.005f)
        assertEquals(3.01f, f.crestFactorDb, 0.1f)
        assertEquals(AnalysisQuality.OK, f.quality)
        f.assertFinite()
    }

    @Test
    fun `spectral centroid follows the tone frequency`() {
        val low = Signals.features(Signals.sine(300f, 1.5f))
        val high = Signals.features(Signals.sine(3000f, 1.5f))
        assertEquals(300f, low.spectralCentroid, 40f)
        assertEquals(3000f, high.spectralCentroid, 150f)
        assertTrue(low.spectralBandwidth < 300f)
    }

    @Test
    fun `band energies separate bass from treble`() {
        val bass = Signals.features(Signals.sine(100f, 1.5f))
        val treble = Signals.features(Signals.sine(8000f, 1.5f))
        assertTrue(bass.lowFrequencyEnergy > 0.9f)
        assertTrue(treble.highFrequencyEnergy > 0.9f)
        assertEquals(1f, bass.lowFrequencyEnergy + bass.midFrequencyEnergy + bass.highFrequencyEnergy, 1e-3f)
    }

    @Test
    fun `zero crossing rate matches twice the frequency`() {
        val f = Signals.features(Signals.sine(1000f, 1f))
        // A sine crosses zero twice per period: ZCR ≈ 2f / sampleRate.
        assertEquals(2 * 1000f / Signals.RATE, f.zeroCrossingRate, 0.002f)
    }

    @Test
    fun `noise is flatter than a tone`() {
        val tone = Signals.features(Signals.sine(880f, 1.5f))
        val noise = Signals.features(Signals.whiteNoise(1.5f))
        assertTrue("tone=${tone.spectralFlatness} noise=${noise.spectralFlatness}", noise.spectralFlatness > tone.spectralFlatness * 5)
        assertTrue(noise.spectralFlatness > 0.3f)
    }

    @Test
    fun `silence yields defined zeros and the silent quality flag`() {
        val f = Signals.features(Signals.silence(3f))
        assertEquals(0f, f.rms, 0f)
        assertEquals(0f, f.spectralCentroid, 0f)
        assertEquals(1f, f.silenceRatio, 0f)
        assertEquals(0, f.onsetCount)
        assertNull(f.rhythmicRegularity)
        assertEquals(AnalysisQuality.MOSTLY_SILENT, f.quality)
        f.assertFinite()
    }

    @Test
    fun `very short and empty input are handled`() {
        val short = Signals.features(Signals.sine(440f, 0.05f))
        assertEquals(AnalysisQuality.TOO_SHORT, short.quality)
        short.assertFinite()
        val empty = FeatureExtractor().extract(FloatArray(0), Signals.RATE)
        assertEquals(AnalysisQuality.TOO_SHORT, empty.quality)
        empty.assertFinite()
    }

    @Test
    fun `clipping is detected for extremely loud input`() {
        val loud = Signals.sine(200f, 1f, amplitude = 2f).map { it.coerceIn(-1f, 1f) }.toFloatArray()
        val f = Signals.features(loud)
        assertTrue(f.clippingRatio > 0.1f)
        assertEquals(AnalysisQuality.CLIPPED, f.quality)
        assertEquals(1f, f.peakAmplitude, 1e-3f)
        f.assertFinite()
    }

    @Test
    fun `onsets are detected on regular clicks with high regularity`() {
        val f = Signals.features(Signals.clicks(interval = 0.5f, seconds = 4f))
        assertEquals(8f, f.onsetCount.toFloat(), 1.5f)
        assertEquals(2f, f.transientDensity, 0.4f)
        val regularity = f.rhythmicRegularity
        assertNotNull(regularity)
        assertTrue("regularity=$regularity", regularity!! > 0.8f)
    }

    @Test
    fun `steady tone has no rhythm claim`() {
        val f = Signals.features(Signals.sine(440f, 3f))
        assertTrue(f.onsetCount < 4)
        assertNull(f.rhythmicRegularity)
    }

    @Test
    fun `dynamic range reflects loud and quiet passages`() {
        val loud = Signals.sine(440f, 1f, amplitude = 0.8f)
        val quiet = Signals.sine(440f, 1f, amplitude = 0.02f)
        val varied = Signals.features(loud + quiet + loud)
        val steady = Signals.features(Signals.sine(440f, 3f, amplitude = 0.4f))
        assertTrue(varied.dynamicRangeDb > 20f)
        assertTrue(steady.dynamicRangeDb < 3f)
    }

    @Test
    fun `envelope follows a fade in`() {
        val n = Signals.RATE * 2
        val fade = FloatArray(n) { i -> (i.toFloat() / n) * 0.6f * kotlin.math.sin(2 * Math.PI * 330 * i / Signals.RATE).toFloat() }
        val f = Signals.features(fade)
        assertTrue(f.energyEnvelope.first() < 0.15f)
        assertEquals(1f, f.energyEnvelope.max(), 1e-6f)
        assertTrue(f.energyEnvelope.last() > 0.85f)
    }

    @Test
    fun `dc offset does not distort analysis`() {
        val clean = Signals.features(Signals.sine(500f, 1f, 0.3f))
        val offset = Signals.features(Signals.sine(500f, 1f, 0.3f).map { it + 0.2f }.toFloatArray())
        assertEquals(clean.rms, offset.rms, 0.003f)
        assertEquals(clean.spectralCentroid, offset.spectralCentroid, 15f)
    }

    @Test
    fun `cancellation hook is honoured`() {
        var calls = 0
        assertThrows(IllegalStateException::class.java) {
            FeatureExtractor().extract(Signals.sine(440f, 2f), Signals.RATE) {
                if (++calls > 3) throw IllegalStateException("cancelled")
            }
        }
        assertFalse(calls == 0)
    }
}

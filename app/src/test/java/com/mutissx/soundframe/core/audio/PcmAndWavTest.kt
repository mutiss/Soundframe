package com.mutissx.soundframe.core.audio

import com.mutissx.soundframe.domain.model.AudioClip
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PcmAndWavTest {

    @Test
    fun `wav round trip preserves samples and rate`() {
        val clip = AudioClip(ShortArray(1000) { (it * 37 - 16000).toShort() }, 22_050)
        val decoded = WavCodec.decode(WavCodec.encode(clip))
        assertEquals(22_050, decoded.sampleRate)
        assertArrayEquals(clip.samples, decoded.samples)
    }

    @Test
    fun `corrupt wav is rejected`() {
        assertThrows(IOException::class.java) { WavCodec.decode(ByteArray(10)) }
        val truncated = WavCodec.encode(AudioClip(ShortArray(100), 44_100)).copyOf(30)
        assertThrows(IOException::class.java) { WavCodec.decode(truncated) }
    }

    @Test
    fun `downmix averages channels`() {
        val stereo = shortArrayOf(100, 300, -200, 200, 1000, 0)
        assertArrayEquals(shortArrayOf(200, 0, 500), PcmUtils.downmix(stereo, stereo.size, 2))
    }

    @Test
    fun `resample changes length proportionally and keeps a tone's level`() {
        val input = ShortArray(48_000) { (10_000 * kotlin.math.sin(2 * Math.PI * 440 * it / 48_000)).toInt().toShort() }
        val out = PcmUtils.resample(input, 48_000, 44_100)
        assertEquals(44_100f, out.size.toFloat(), 2f)
        val peak = out.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue(peak in 9_000..10_001)
    }

    @Test
    fun `level maps silence to zero and full scale to one`() {
        assertEquals(0f, PcmUtils.level(ShortArray(512), 0, 512), 0f)
        assertEquals(1f, PcmUtils.level(ShortArray(512) { 32767 }, 0, 512), 1e-3f)
    }

    @Test
    fun `peak envelope is normalised`() {
        val samples = ShortArray(1000) { if (it < 500) 1000 else 4000 }
        val env = PcmUtils.peakEnvelope(samples, 10)
        assertEquals(10, env.size)
        assertEquals(0.25f, env.first(), 1e-3f)
        assertEquals(1f, env.last(), 1e-6f)
        assertTrue(PcmUtils.peakEnvelope(ShortArray(0), 4).all { it == 0f })
    }
}

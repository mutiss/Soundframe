package com.mutissx.soundframe.core.audio

import com.mutissx.soundframe.domain.model.AudioClip
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Minimal RIFF/WAVE codec for mono 16-bit PCM — the only format SOUNDFRAME writes. */
object WavCodec {
    private const val HEADER_SIZE = 44

    fun encode(clip: AudioClip): ByteArray {
        val dataSize = clip.samples.size * 2
        val buffer = ByteBuffer.allocate(HEADER_SIZE + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataSize)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(1) // mono
        buffer.putInt(clip.sampleRate)
        buffer.putInt(clip.sampleRate * 2)
        buffer.putShort(2)
        buffer.putShort(16)
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataSize)
        buffer.asShortBuffer().put(clip.samples)
        return buffer.array()
    }

    /** Decodes 16-bit PCM WAV (mono or stereo, downmixed). Skips unknown chunks. */
    fun decode(bytes: ByteArray): AudioClip {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 12 || tag(buffer, 0) != "RIFF" || tag(buffer, 8) != "WAVE") throw IOException("Not a WAV file")
        var pos = 12
        var channels = 1
        var sampleRate = 0
        var bits = 16
        while (pos + 8 <= bytes.size) {
            val id = tag(buffer, pos)
            val size = buffer.getInt(pos + 4)
            val body = pos + 8
            if (size < 0 || body + size > bytes.size) throw IOException("Corrupt WAV chunk")
            when (id) {
                "fmt " -> {
                    val format = buffer.getShort(body).toInt()
                    if (format != 1) throw IOException("Unsupported WAV encoding $format")
                    channels = buffer.getShort(body + 2).toInt()
                    sampleRate = buffer.getInt(body + 4)
                    bits = buffer.getShort(body + 14).toInt()
                }
                "data" -> {
                    if (bits != 16 || sampleRate <= 0 || channels <= 0) throw IOException("Unsupported WAV layout")
                    val frames = size / (2 * channels)
                    val samples = ShortArray(frames) { f ->
                        var acc = 0
                        for (c in 0 until channels) acc += buffer.getShort(body + (f * channels + c) * 2)
                        (acc / channels).toShort()
                    }
                    return AudioClip(samples, sampleRate)
                }
            }
            pos = body + size + (size and 1)
        }
        throw IOException("WAV has no data chunk")
    }

    private fun tag(buffer: ByteBuffer, at: Int): String =
        String(ByteArray(4) { buffer.get(at + it) }, Charsets.US_ASCII)
}

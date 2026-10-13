package com.mutissx.soundframe.data.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.mutissx.soundframe.core.audio.PcmUtils
import com.mutissx.soundframe.core.common.AppDispatchers
import com.mutissx.soundframe.domain.model.AudioClip
import com.mutissx.soundframe.domain.model.ImportException
import com.mutissx.soundframe.domain.repository.AudioDecoder
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteOrder

/**
 * Decodes any platform-supported audio document (MP3, AAC/M4A, OGG, FLAC, WAV, Opus...) with
 * MediaExtractor + MediaCodec, then downmixes and resamples to the analysis format.
 * Only the first [AudioClip.MAX_DURATION_MS] are decoded.
 */
class MediaAudioDecoder(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : AudioDecoder {

    override suspend fun decode(uri: String, maxDurationMs: Long): AudioClip = withContext(dispatchers.io) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            try {
                extractor.setDataSource(context, Uri.parse(uri), null)
            } catch (e: IOException) {
                throw ImportException.Unreadable(e)
            } catch (e: SecurityException) {
                throw ImportException.Unreadable(e)
            } catch (e: IllegalArgumentException) {
                throw ImportException.UnsupportedFormat(e)
            }
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw ImportException.NoAudioTrack()
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: throw ImportException.UnsupportedFormat()
            codec = try {
                MediaCodec.createDecoderByType(mime).also {
                    it.configure(inputFormat, null, null, 0)
                    it.start()
                }
            } catch (e: Exception) {
                throw ImportException.UnsupportedFormat(e)
            }

            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            var pcm = ShortArray(sampleRate * channels * 2)
            var count = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                ensureActive()
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val format = codec.outputFormat
                    sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    } else AudioFormat.ENCODING_PCM_16BIT
                } else if (out >= 0) {
                    val buffer = codec.getOutputBuffer(out)!!.order(ByteOrder.nativeOrder())
                    buffer.position(info.offset)
                    buffer.limit(info.offset + info.size)
                    val values = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) info.size / 4 else info.size / 2
                    if (count + values > pcm.size) pcm = pcm.copyOf(maxOf(pcm.size * 2, count + values))
                    if (encoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        val floats = buffer.asFloatBuffer()
                        for (i in 0 until values) pcm[count + i] = (floats.get(i).coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    } else {
                        buffer.asShortBuffer().get(pcm, count, values)
                    }
                    count += values
                    codec.releaseOutputBuffer(out, false)
                    val maxValues = sampleRate.toLong() * channels * maxDurationMs / 1000
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0 || count >= maxValues) outputDone = true
                }
            }
            if (count == 0) throw ImportException.NoAudioTrack()
            val mono = PcmUtils.downmix(pcm, count, channels)
            val resampled = PcmUtils.resample(mono, sampleRate, AudioClip.DEFAULT_SAMPLE_RATE)
            val maxSamples = (AudioClip.DEFAULT_SAMPLE_RATE * maxDurationMs / 1000).toInt()
            AudioClip(if (resampled.size > maxSamples) resampled.copyOf(maxSamples) else resampled, AudioClip.DEFAULT_SAMPLE_RATE)
        } catch (e: MediaCodec.CodecException) {
            throw ImportException.UnsupportedFormat(e)
        } catch (e: IllegalStateException) {
            throw ImportException.UnsupportedFormat(e)
        } finally {
            codec?.let {
                runCatching { it.stop() }
                it.release()
            }
            extractor.release()
        }
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}

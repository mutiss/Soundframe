package com.mutissx.soundframe.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import com.mutissx.soundframe.core.graphics.PreparedArt
import com.mutissx.soundframe.domain.model.AudioClip
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders an animated artwork to H.264 MP4 with an optional AAC track.
 *
 * Frames are drawn with the same software canvas as PNG export, converted to YUV 4:2:0 and queued
 * through [MediaCodec.getInputImage] with explicit presentation timestamps. This avoids the
 * wall-clock timestamps of Surface input, so the video always plays at exactly [FPS] regardless of
 * how fast the device renders. Blocking: call from a background dispatcher.
 */
internal class VideoEncoder(
    private val art: PreparedArt,
    private val width: Int,
    private val height: Int,
    private val durationSec: Int,
    private val audio: AudioClip?,
    private val output: File,
) {

    private class EncodedSample(val data: ByteArray, val ptsUs: Long, val flags: Int)

    fun encode(animate: Boolean, checkCancelled: () -> Unit, onProgress: (Float) -> Unit) {
        var video: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        val staticLayer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var success = false
        try {
            art.drawStatic(Canvas(staticLayer))
            val canvas = Canvas(frame)
            val argb = IntArray(width * height)

            val (audioFormat, audioSamples) = audio?.let { encodeAudio(it, checkCancelled) } ?: (null to emptyList())

            val format = videoFormat(width, height)
            val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
                ?: throw IllegalStateException("No encoder for ${width}x$height")
            video = MediaCodec.createByCodecName(codecName)
            video.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            video.start()
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val totalFrames = durationSec * FPS
            val info = MediaCodec.BufferInfo()
            var videoTrack = -1
            var frameIndex = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                checkCancelled()
                if (!inputDone) {
                    val index = video.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val ptsUs = frameIndex * 1_000_000L / FPS
                        if (frameIndex >= totalFrames) {
                            video.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            canvas.drawBitmap(staticLayer, 0f, 0f, null)
                            art.drawDynamic(canvas, if (animate) frameIndex.toFloat() / FPS else 0f)
                            frame.getPixels(argb, 0, width, 0, 0, width, height)
                            val image = video.getInputImage(index) ?: throw IllegalStateException("Encoder gave no input image")
                            writeYuv(argb, image)
                            video.queueInputBuffer(index, 0, width * height * 3 / 2, ptsUs, 0)
                            frameIndex++
                            onProgress(frameIndex.toFloat() / totalFrames * 0.98f)
                        }
                    }
                }
                var out = video.dequeueOutputBuffer(info, if (inputDone) TIMEOUT_US else 0)
                while (out != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val mx = muxer
                        videoTrack = mx.addTrack(video.outputFormat)
                        val audioTrack = audioFormat?.let { mx.addTrack(it) } ?: -1
                        mx.start()
                        muxerStarted = true
                        if (audioTrack >= 0) {
                            val audioInfo = MediaCodec.BufferInfo()
                            for (s in audioSamples) {
                                audioInfo.set(0, s.data.size, s.ptsUs, s.flags)
                                mx.writeSampleData(audioTrack, ByteBuffer.wrap(s.data), audioInfo)
                            }
                        }
                    } else if (out >= 0) {
                        val buffer = video.getOutputBuffer(out)!!
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (!isConfig && info.size > 0 && muxerStarted) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(videoTrack, buffer, info)
                        }
                        video.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                            break
                        }
                    }
                    out = video.dequeueOutputBuffer(info, 0)
                }
            }
            onProgress(1f)
            success = true
        } finally {
            video?.let {
                runCatching { it.stop() }
                it.release()
            }
            muxer?.let {
                if (muxerStarted) runCatching { it.stop() }
                it.release()
            }
            staticLayer.recycle()
            frame.recycle()
            if (!success) output.delete()
        }
    }

    /** Encodes the whole clip (≤ 10 s, ~160 KB of AAC) up front so tracks can be added together. */
    private fun encodeAudio(clip: AudioClip, checkCancelled: () -> Unit): Pair<MediaFormat?, List<EncodedSample>> {
        val maxSamples = minOf(clip.samples.size, clip.sampleRate * durationSec)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, clip.sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val samples = ArrayList<EncodedSample>()
        var outputFormat: MediaFormat? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var position = 0
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                checkCancelled()
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.nativeOrder())
                        buffer.clear()
                        val ptsUs = position * 1_000_000L / clip.sampleRate
                        val count = minOf(buffer.remaining() / 2, maxSamples - position)
                        if (count <= 0) {
                            codec.queueInputBuffer(index, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buffer.asShortBuffer().put(clip.samples, position, count)
                            codec.queueInputBuffer(index, 0, count * 2, ptsUs, 0)
                            position += count
                        }
                    }
                }
                val out = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    out >= 0 -> {
                        val buffer = codec.getOutputBuffer(out)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            val bytes = ByteArray(info.size)
                            buffer.position(info.offset)
                            buffer.get(bytes)
                            samples.add(EncodedSample(bytes, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()))
                        }
                        codec.releaseOutputBuffer(out, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        return outputFormat to samples
    }

    /** ARGB → BT.601 limited-range YUV 4:2:0, honouring each plane's row and pixel strides. */
    private fun writeYuv(argb: IntArray, image: Image) {
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val yBuf = yPlane.buffer
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val yRow = yPlane.rowStride
        val yPix = yPlane.pixelStride
        for (row in 0 until height) {
            val rowBase = row * width
            val outBase = row * yRow
            for (col in 0 until width) {
                val c = argb[rowBase + col]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                yBuf.put(outBase + col * yPix, (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).toByte())
            }
        }
        val uRow = uPlane.rowStride
        val uPix = uPlane.pixelStride
        val vRow = vPlane.rowStride
        val vPix = vPlane.pixelStride
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val c = argb[(row * 2) * width + col * 2]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                uBuf.put(row * uRow + col * uPix, (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).toByte())
                vBuf.put(row * vRow + col * vPix, (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).toByte())
            }
        }
    }

    companion object {
        const val FPS = 30
        private const val TIMEOUT_US = 10_000L

        private fun videoFormat(width: Int, height: Int): MediaFormat =
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, if (width >= 1080) 12_000_000 else 6_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

        /** 1080×1920 when the device's encoder supports it, otherwise 720×1280. */
        fun supportedPortraitSize(): Pair<Int, Int>? {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            return listOf(1080 to 1920, 720 to 1280).firstOrNull { (w, h) -> list.findEncoderForFormat(videoFormat(w, h)) != null }
        }
    }
}

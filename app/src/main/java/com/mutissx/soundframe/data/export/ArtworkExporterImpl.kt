package com.mutissx.soundframe.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Environment
import android.provider.MediaStore
import com.mutissx.soundframe.core.common.AppDispatchers
import com.mutissx.soundframe.core.common.BrandTypeface
import com.mutissx.soundframe.core.common.FileOps
import com.mutissx.soundframe.core.common.StoragePaths
import com.mutissx.soundframe.core.graphics.ArtScene
import com.mutissx.soundframe.core.graphics.ArtworkComposer
import com.mutissx.soundframe.domain.model.Artwork
import com.mutissx.soundframe.domain.model.ExportException
import com.mutissx.soundframe.domain.model.ExportImageSize
import com.mutissx.soundframe.domain.repository.ArtworkExporter
import com.mutissx.soundframe.domain.repository.AudioStore
import com.mutissx.soundframe.domain.repository.ExportedFile
import com.mutissx.soundframe.domain.repository.VideoExportProgress
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.io.File

class ArtworkExporterImpl(
    private val context: Context,
    private val paths: StoragePaths,
    private val audioStore: AudioStore,
    private val brand: BrandTypeface,
    private val dispatchers: AppDispatchers,
) : ArtworkExporter {

    override suspend fun exportImage(artwork: Artwork, size: ExportImageSize, watermark: Boolean): ExportedFile =
        withContext(dispatchers.default) {
            FileOps.deleteOlderThan(paths.exportDir, STALE_EXPORT_MS)
            FileOps.ensureSpace(paths.exportDir, size.width.toLong() * size.height * 4)
            val scene = ArtScene.from(artwork.features, artwork.settings)
            // Geometry is rebuilt at the target resolution — never an upscaled preview.
            val art = ArtworkComposer.prepare(artwork.settings.style, scene, size.width, size.height, brand.typeface, watermark)
            ensureActive()
            val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            try {
                ArtworkComposer.drawFrame(art, Canvas(bitmap), ArtworkComposer.STILL_TIME)
                ensureActive()
                val file = File(paths.exportDir, "${fileStem(artwork)}-${size.width}x${size.height}.png")
                FileOps.writeAtomically(file) { tmp ->
                    tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                ExportedFile(file.absolutePath, "image/png")
            } finally {
                bitmap.recycle()
            }
        }

    override fun exportVideo(artwork: Artwork, durationSec: Int, includeAudio: Boolean, watermark: Boolean): Flow<VideoExportProgress> =
        channelFlow {
            send(VideoExportProgress.Rendering(0f))
            val file = withContext(dispatchers.default) {
                FileOps.deleteOlderThan(paths.exportDir, STALE_EXPORT_MS)
                FileOps.ensureSpace(paths.exportDir, 40L * 1024 * 1024)
                val audio = if (includeAudio) artwork.audioPath?.let { runCatching { audioStore.load(it) }.getOrNull() } else null
                val (width, height) = VideoEncoder.supportedPortraitSize()
                    ?: throw ExportException("This device has no compatible H.264 encoder")
                val scene = ArtScene.from(artwork.features, artwork.settings)
                val art = ArtworkComposer.prepare(artwork.settings.style, scene, width, height, brand.typeface, watermark)
                val out = File(paths.exportDir, "${fileStem(artwork)}-${durationSec}s.mp4")
                val context = currentCoroutineContext()
                VideoEncoder(art, width, height, durationSec, audio, out).encode(
                    animate = artwork.settings.animate,
                    checkCancelled = { context.ensureActive() },
                    onProgress = { trySend(VideoExportProgress.Rendering(it)) },
                )
                out
            }
            send(VideoExportProgress.Done(ExportedFile(file.absolutePath, "video/mp4")))
        }

    override suspend fun saveToDevice(file: ExportedFile): Boolean = withContext(dispatchers.io) {
        val source = File(file.path)
        if (!source.exists()) return@withContext false
        val isVideo = file.mimeType.startsWith("video/")
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, file.mimeType)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Soundframe",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return@withContext false
        try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: throw ExportException("Could not open destination")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            false
        }
    }

    private fun fileStem(artwork: Artwork): String {
        val slug = artwork.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "artwork" }
        return "soundframe-$slug"
    }

    private companion object {
        const val STALE_EXPORT_MS = 6L * 60 * 60 * 1000
    }
}

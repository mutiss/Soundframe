package com.mutissx.soundframe.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import com.mutissx.soundframe.core.common.AppDispatchers
import com.mutissx.soundframe.core.common.FileOps
import com.mutissx.soundframe.core.common.StoragePaths
import com.mutissx.soundframe.core.graphics.ArtScene
import com.mutissx.soundframe.core.graphics.ArtworkComposer
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.repository.ThumbnailRenderer
import kotlinx.coroutines.withContext
import java.io.File

class ThumbnailRendererImpl(
    private val paths: StoragePaths,
    private val dispatchers: AppDispatchers,
) : ThumbnailRenderer {

    override suspend fun render(id: String, features: AudioFeatures, settings: ArtworkSettings): String =
        withContext(dispatchers.default) {
            val scene = ArtScene.from(features, settings)
            val art = ArtworkComposer.prepare(settings.style, scene, SIZE, SIZE)
            val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
            try {
                ArtworkComposer.drawFrame(art, Canvas(bitmap), ArtworkComposer.STILL_TIME)
                val file = File(paths.thumbnailDir, "$id.webp")
                FileOps.ensureSpace(paths.thumbnailDir, 512L * 1024)
                FileOps.writeAtomically(file) { tmp ->
                    tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, it) }
                }
                file.absolutePath
            } finally {
                bitmap.recycle()
            }
        }

    override suspend fun exists(path: String): Boolean = withContext(dispatchers.io) { File(path).let { it.isFile && it.length() > 0 } }

    override suspend fun delete(path: String) {
        withContext(dispatchers.io) { File(path).delete() }
    }

    private companion object {
        const val SIZE = 720
    }
}

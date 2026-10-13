package com.mutissx.soundframe.core.designsystem.art

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mutissx.soundframe.core.designsystem.component.ArtLoader
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Process-wide memory cache of decoded thumbnails, keyed by path and modification time. */
private object ThumbnailCache {
    val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
}

/**
 * Shows a rendered artwork thumbnail. If the file is missing or unreadable, [onMissing] is called so
 * the owner can regenerate it from the stored audio features.
 */
@Composable
fun ThumbnailImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onMissing: () -> Unit = {},
) {
    val missingCallback by rememberUpdatedState(onMissing)
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path == null) {
            missingCallback()
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            val file = File(path)
            if (!file.isFile) return@withContext null
            val key = "$path@${file.lastModified()}"
            ThumbnailCache.cache.get(key) ?: BitmapFactory.decodeFile(path)?.asImageBitmap()?.also {
                ThumbnailCache.cache.put(key, it)
            }
        }
        if (loaded == null) missingCallback() else bitmap = loaded
    }
    Box(modifier.background(Brush.verticalGradient(listOf(SfColors.Ink3, SfColors.Ink1)))) {
        Crossfade(bitmap, label = "thumb") { image ->
            if (image != null) {
                Image(image, contentDescription, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ArtLoader(Modifier.size(28.dp))
                }
            }
        }
    }
}

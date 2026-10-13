package com.mutissx.soundframe.core.designsystem.art

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mutissx.soundframe.core.designsystem.theme.LocalReduceMotion
import com.mutissx.soundframe.core.designsystem.theme.SfMotion
import com.mutissx.soundframe.core.graphics.ArtScene
import com.mutissx.soundframe.core.graphics.ArtworkComposer
import com.mutissx.soundframe.core.graphics.PreparedArt
import com.mutissx.soundframe.core.common.BrandTypeface
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * Live generative artwork. Geometry is prepared off the main thread whenever the scene or size
 * changes and crossfaded in. The time-independent layer is rasterised into a bitmap on the same
 * background thread, so the first frame of a new artwork is a single texture draw instead of
 * thousands of strokes on the RenderThread; only the dynamic layer redraws each frame (reading
 * time in the draw phase, so no recomposition).
 *
 * [onPrepared] fires once the first artwork is ready to draw. Pass [fadeIn] = false when the
 * caller animates the artwork's appearance itself.
 */
@Composable
fun ArtworkCanvas(
    features: AudioFeatures,
    settings: ArtworkSettings,
    modifier: Modifier = Modifier,
    animate: Boolean = settings.animate,
    signature: Boolean = false,
    contentDescription: String? = null,
    fadeIn: Boolean = true,
    onPrepared: () -> Unit = {},
) {
    val reduceMotion = LocalReduceMotion.current
    val brand = koinInject<BrandTypeface>()
    val preparedCallback by rememberUpdatedState(onPrepared)
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scene = remember(features, settings.palette, settings.background, settings.intensity, settings.complexity, settings.variation) {
        ArtScene.from(features, settings)
    }
    var current by remember { mutableStateOf<ArtFrame?>(null) }
    var previous by remember { mutableStateOf<ArtFrame?>(null) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(scene, settings.style, size, signature) {
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        // Debounce rapid size changes (e.g. during layout animations) before doing heavy work.
        if (current != null) delay(60)
        val typeface = if (signature) brand.typeface else null
        val prepared = withContext(Dispatchers.Default) {
            val art = ArtworkComposer.prepare(settings.style, scene, size.width, size.height, typeface, signature)
            ensureActive()
            val still = Bitmap.createBitmap(art.width, art.height, Bitmap.Config.ARGB_8888)
            art.drawStatic(android.graphics.Canvas(still))
            still.prepareToDraw()
            ArtFrame(art, still)
        }
        val first = current == null
        previous = current
        current = prepared
        if (first) preparedCallback()
        if (!first) {
            fade.snapTo(0f)
            fade.animateTo(1f, tween(SfMotion.CROSSFADE, easing = SfMotion.EaseInOut))
        } else if (fadeIn) {
            fade.snapTo(0f)
            fade.animateTo(1f, tween(SfMotion.MEDIUM))
        }
        previous = null
    }

    val clock = rememberArtClock(running = animate && !reduceMotion)
    Box(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it }
            .then(
                if (contentDescription != null) {
                    Modifier.semantics {
                        this.contentDescription = contentDescription
                        role = Role.Image
                    }
                } else Modifier,
            ),
    ) {
        previous?.let { ArtLayer(it, clock) { 1f } }
        current?.let { ArtLayer(it, clock) { fade.value } }
    }
}

/** A prepared artwork plus its static layer, already rasterised at the prepared size. */
private class ArtFrame(val art: PreparedArt, val still: Bitmap)

private val stillPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)

@Composable
private fun ArtLayer(frame: ArtFrame, time: State<Float>, alpha: () -> Float) {
    val art = frame.art
    Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha() }) {
        Canvas(Modifier.fillMaxSize()) {
            drawPrepared(art) { it.drawBitmap(frame.still, 0f, 0f, stillPaint) }
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = time.value
            drawPrepared(art) { art.drawDynamic(it, t) }
        }
    }
}

private inline fun DrawScope.drawPrepared(art: PreparedArt, block: (android.graphics.Canvas) -> Unit) {
    val sx = size.width / art.width
    val sy = size.height / art.height
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        native.save()
        native.scale(sx, sy)
        block(native)
        native.restore()
    }
}

/**
 * Seconds of animation time, advanced by the frame clock only while [running] and the lifecycle is
 * resumed — animations stop entirely in the background. When stopped it rests on the canonical
 * still frame, so a paused preview matches exported stills exactly.
 */
@Composable
fun rememberArtClock(running: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(ArtworkComposer.STILL_TIME) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(running, lifecycle) {
        if (!running) {
            time.floatValue = ArtworkComposer.STILL_TIME
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = -1L
            while (isActive) {
                withFrameNanos { now ->
                    if (last >= 0) time.floatValue += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                    last = now
                }
            }
        }
    }
    return time
}

/** Desaturates everything drawn by the modified element; [amount] 0 = grayscale, 1 = full colour. */
fun Modifier.saturation(amount: () -> Float): Modifier = drawWithContent {
    val s = amount().coerceIn(0f, 1f)
    if (s >= 0.999f) {
        drawContent()
    } else {
        val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(s) }) }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }
}

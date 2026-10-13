package com.mutissx.soundframe.core.designsystem.art

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Continuity between a gallery tile and the artwork detail: both ends declare the same key and the
 * artwork morphs between their bounds. A no-op outside a shared transition (previews, tests).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(artworkId: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(shared) {
        this@sharedArtwork.sharedBounds(
            sharedContentState = rememberSharedContentState("artwork-$artworkId"),
            animatedVisibilityScope = visibility,
        )
    }
}

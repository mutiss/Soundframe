package com.mutissx.soundframe.app.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.mutissx.soundframe.core.designsystem.art.LocalNavAnimatedVisibilityScope
import com.mutissx.soundframe.core.designsystem.art.LocalSharedTransitionScope
import com.mutissx.soundframe.core.designsystem.component.SfBottomBar
import com.mutissx.soundframe.core.designsystem.component.TopLevelDestination
import com.mutissx.soundframe.core.designsystem.theme.SfMotion
import com.mutissx.soundframe.feature.artworkdetail.ArtworkDetailScreen
import com.mutissx.soundframe.feature.editor.EditorScreen
import com.mutissx.soundframe.feature.export.ExportScreen
import com.mutissx.soundframe.feature.gallery.GalleryScreen
import com.mutissx.soundframe.feature.generation.GenerationScreen
import com.mutissx.soundframe.feature.home.HomeScreen
import com.mutissx.soundframe.feature.onboarding.OnboardingScreen
import com.mutissx.soundframe.feature.recording.RecordingScreen
import com.mutissx.soundframe.feature.settings.SettingsScreen
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SoundframeNavHost(startWithOnboarding: Boolean) {
    val nav = rememberNavController()
    // Decided once: later preference changes must not rebuild the graph.
    val start: Any = remember { if (startWithOnboarding) OnboardingRoute else HomeRoute }
    val entry by nav.currentBackStackEntryAsState()
    val topLevel = entry?.destination?.let { d ->
        when {
            d.hasRoute<HomeRoute>() -> TopLevelDestination.STUDIO
            d.hasRoute<GalleryRoute>() -> TopLevelDestination.GALLERY
            d.hasRoute<SettingsRoute>() -> TopLevelDestination.SETTINGS
            else -> null
        }
    }

    SharedTransitionLayout(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            Box(Modifier.fillMaxSize()) {
                NavHost(
                    navController = nav,
                    startDestination = start,
                    enterTransition = { fadeIn(tween(SfMotion.MEDIUM)) + scaleIn(tween(SfMotion.MEDIUM, easing = SfMotion.EaseOutExpo), initialScale = 0.98f) },
                    exitTransition = { fadeOut(tween(SfMotion.SHORT)) },
                    popEnterTransition = { fadeIn(tween(SfMotion.MEDIUM)) },
                    popExitTransition = { fadeOut(tween(SfMotion.SHORT)) + scaleOut(tween(SfMotion.MEDIUM), targetScale = 0.98f) },
                ) {
                    graph(nav)
                }
                AnimatedVisibility(
                    visible = topLevel != null,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                ) {
                    SfBottomBar(topLevel ?: TopLevelDestination.STUDIO, onSelect = { nav.navigateTopLevel(it) })
                }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    val route: Any = when (destination) {
        TopLevelDestination.STUDIO -> HomeRoute
        TopLevelDestination.GALLERY -> GalleryRoute
        TopLevelDestination.SETTINGS -> SettingsRoute
    }
    navigate(route) {
        popUpTo<HomeRoute> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Registers a destination and exposes its AnimatedVisibilityScope for shared-element transitions. */
private inline fun <reified T : Any> NavGraphBuilder.screen(
    noinline content: @Composable AnimatedVisibilityScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
            content(entry)
        }
    }
}

private fun NavGraphBuilder.graph(nav: NavHostController) {
    screen<OnboardingRoute> {
        OnboardingScreen(onFinished = {
            nav.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
        })
    }
    screen<HomeRoute> {
        HomeScreen(
            onRecord = { nav.navigate(RecordingRoute(autoStart = true)) },
            onImport = { uri -> nav.navigate(GenerationRoute(importUri = uri)) },
            onOpenGallery = { nav.navigateTopLevel(TopLevelDestination.GALLERY) },
            onOpenArtwork = { id -> nav.navigate(ArtworkDetailRoute(id)) },
        )
    }
    screen<RecordingRoute> { entry ->
        val route = entry.toRoute<RecordingRoute>()
        RecordingScreen(
            autoStart = route.autoStart,
            onBack = { nav.popBackStack() },
            onRecorded = { sessionId ->
                nav.navigate(GenerationRoute(sessionId = sessionId)) { popUpTo<RecordingRoute> { inclusive = true } }
            },
            onImport = { uri ->
                nav.navigate(GenerationRoute(importUri = uri)) { popUpTo<RecordingRoute> { inclusive = true } }
            },
        )
    }
    screen<GenerationRoute> { entry ->
        val route = entry.toRoute<GenerationRoute>()
        GenerationScreen(
            viewModel = koinViewModel { parametersOf(route) },
            onReady = { sessionId ->
                nav.navigate(EditorRoute(sessionId = sessionId)) { popUpTo<GenerationRoute> { inclusive = true } }
            },
            onBack = { nav.popBackStack() },
        )
    }
    screen<EditorRoute> { entry ->
        val route = entry.toRoute<EditorRoute>()
        EditorScreen(
            viewModel = koinViewModel { parametersOf(route) },
            onBack = { nav.popBackStack() },
            onOpenExport = { id -> nav.navigate(ExportRoute(id)) },
        )
    }
    screen<GalleryRoute> {
        GalleryScreen(
            onOpenArtwork = { id -> nav.navigate(ArtworkDetailRoute(id)) },
            onRecord = { nav.navigate(RecordingRoute(autoStart = true)) },
        )
    }
    screen<ArtworkDetailRoute> { entry ->
        val route = entry.toRoute<ArtworkDetailRoute>()
        ArtworkDetailScreen(
            viewModel = koinViewModel { parametersOf(route.artworkId) },
            onBack = { nav.popBackStack() },
            onEdit = { id -> nav.navigate(EditorRoute(artworkId = id)) },
            onExport = { id -> nav.navigate(ExportRoute(id)) },
        )
    }
    screen<ExportRoute> { entry ->
        val route = entry.toRoute<ExportRoute>()
        ExportScreen(viewModel = koinViewModel { parametersOf(route.artworkId) }, onBack = { nav.popBackStack() })
    }
    screen<SettingsRoute> {
        SettingsScreen()
    }
}

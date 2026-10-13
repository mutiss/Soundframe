package com.mutissx.soundframe.app.navigation

import kotlinx.serialization.Serializable

@Serializable
data object OnboardingRoute

@Serializable
data object HomeRoute

@Serializable
data class RecordingRoute(val autoStart: Boolean = false)

/** Exactly one of [sessionId] (fresh recording) or [importUri] (document to decode) is set. */
@Serializable
data class GenerationRoute(val sessionId: String? = null, val importUri: String? = null)

/** Exactly one of [sessionId] (new creation) or [artworkId] (saved artwork) is set. */
@Serializable
data class EditorRoute(val sessionId: String? = null, val artworkId: String? = null)

@Serializable
data object GalleryRoute

@Serializable
data class ArtworkDetailRoute(val artworkId: String)

@Serializable
data class ExportRoute(val artworkId: String)

@Serializable
data object SettingsRoute

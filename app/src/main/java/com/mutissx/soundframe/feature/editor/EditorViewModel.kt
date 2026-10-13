package com.mutissx.soundframe.feature.editor

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mutissx.soundframe.R
import com.mutissx.soundframe.app.navigation.EditorRoute
import com.mutissx.soundframe.core.common.Format
import com.mutissx.soundframe.data.mapper.ArtworkMapper
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.ArtworkNotFoundException
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.BackgroundTone
import com.mutissx.soundframe.domain.model.PaletteId
import com.mutissx.soundframe.domain.repository.ArtworkRepository
import com.mutissx.soundframe.domain.repository.TitleVocabularyProvider
import com.mutissx.soundframe.domain.usecase.AnalyzeSessionUseCase
import com.mutissx.soundframe.domain.usecase.ArtDirection
import com.mutissx.soundframe.domain.usecase.SaveArtworkUseCase
import com.mutissx.soundframe.domain.usecase.TitleRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditorPanel(@StringRes val label: Int) {
    STYLE(R.string.editor_panel_style),
    PALETTE(R.string.editor_panel_palette),
    TUNE(R.string.editor_panel_tune),
    GROUND(R.string.editor_panel_ground),
}

@Immutable
data class EditorUiState(
    val loading: Boolean = true,
    val features: AudioFeatures? = null,
    val settings: ArtworkSettings = ArtworkSettings(),
    val title: String = "",
    val artworkId: String? = null,
    val isSaving: Boolean = false,
    val dirty: Boolean = true,
    val savedPulse: Int = 0,
    val panel: EditorPanel = EditorPanel.STYLE,
    val chromeVisible: Boolean = true,
    @StringRes val error: Int? = null,
)

sealed interface EditorAction {
    data class SelectStyle(val style: ArtStyle) : EditorAction
    data class SelectPalette(val palette: PaletteId) : EditorAction
    data class SelectBackground(val background: BackgroundTone) : EditorAction
    data class SetIntensity(val value: Float) : EditorAction
    data class SetComplexity(val value: Float) : EditorAction
    data object ToggleAnimation : EditorAction
    data object NextVariation : EditorAction
    data object OriginalVariation : EditorAction
    data class Rename(val title: String) : EditorAction
    data class ShowPanel(val panel: EditorPanel) : EditorAction
    data object ToggleChrome : EditorAction
    data object Save : EditorAction
    data object Export : EditorAction
}

sealed interface EditorEffect {
    data class OpenExport(val artworkId: String) : EditorEffect
    data class Message(@StringRes val text: Int) : EditorEffect
}

/**
 * Editing only ever changes [ArtworkSettings] and the title — the analysed features are read-only.
 * Settings and title live in [SavedStateHandle] so they survive rotation and process death.
 */
class EditorViewModel(
    private val route: EditorRoute,
    private val savedState: SavedStateHandle,
    private val artworks: ArtworkRepository,
    private val analyzeSession: AnalyzeSessionUseCase,
    private val saveArtwork: SaveArtworkUseCase,
    private val vocabulary: TitleVocabularyProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()
    private val _effects = Channel<EditorEffect>(Channel.BUFFERED)
    val effects: Flow<EditorEffect> = _effects.receiveAsFlow()

    init {
        load()
    }

    private fun load() = viewModelScope.launch {
        try {
            val restoredSettings = savedState.get<String>(KEY_SETTINGS)?.let {
                runCatching { ArtworkMapper.json.decodeFromString(ArtworkSettings.serializer(), it) }.getOrNull()
            }
            val restoredTitle = savedState.get<String>(KEY_TITLE)
            val savedId = savedState.get<String>(KEY_ARTWORK_ID) ?: route.artworkId
            if (savedId != null) {
                val artwork = artworks.getArtwork(savedId) ?: throw ArtworkNotFoundException(savedId)
                _state.update {
                    it.copy(
                        loading = false, features = artwork.features, artworkId = artwork.id,
                        settings = restoredSettings ?: artwork.settings, title = restoredTitle ?: artwork.title,
                        dirty = restoredSettings != null && restoredSettings != artwork.settings,
                    )
                }
            } else {
                val sessionId = route.sessionId ?: throw IllegalStateException("Editor opened without a source")
                val features = analyzeSession(sessionId)
                val settings = restoredSettings ?: ArtDirection.recommendSettings(features)
                _state.update {
                    it.copy(
                        loading = false, features = features, settings = settings,
                        title = restoredTitle ?: ArtDirection.suggestTitle(features, settings.style, vocabulary.vocabulary()),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = Format.error(e)) }
        }
    }

    fun onAction(action: EditorAction) {
        when (action) {
            is EditorAction.SelectStyle -> editSettings { it.copy(style = action.style) }
            is EditorAction.SelectPalette -> editSettings { it.copy(palette = action.palette) }
            is EditorAction.SelectBackground -> editSettings { it.copy(background = action.background) }
            is EditorAction.SetIntensity -> editSettings { it.copy(intensity = action.value.coerceIn(0f, 1f)) }
            is EditorAction.SetComplexity -> editSettings { it.copy(complexity = action.value.coerceIn(0f, 1f)) }
            EditorAction.ToggleAnimation -> editSettings { it.copy(animate = !it.animate) }
            EditorAction.NextVariation -> editSettings { it.copy(variation = it.variation + 1) }
            EditorAction.OriginalVariation -> editSettings { it.copy(variation = 0) }
            is EditorAction.Rename -> rename(action.title)
            is EditorAction.ShowPanel -> _state.update { it.copy(panel = action.panel) }
            EditorAction.ToggleChrome -> _state.update { it.copy(chromeVisible = !it.chromeVisible) }
            EditorAction.Save -> save(thenExport = false)
            EditorAction.Export -> save(thenExport = true)
        }
    }

    private fun editSettings(transform: (ArtworkSettings) -> ArtworkSettings) {
        _state.update { current ->
            val next = transform(current.settings)
            savedState[KEY_SETTINGS] = ArtworkMapper.json.encodeToString(ArtworkSettings.serializer(), next)
            current.copy(settings = next, dirty = true)
        }
    }

    private fun rename(raw: String) {
        val title = TitleRules.normalize(raw) ?: return
        savedState[KEY_TITLE] = title
        _state.update { it.copy(title = title, dirty = true) }
    }

    private fun save(thenExport: Boolean) {
        val current = _state.value
        if (current.isSaving || current.features == null) return
        if (!current.dirty && current.artworkId != null) {
            if (thenExport) _effects.trySend(EditorEffect.OpenExport(current.artworkId))
            return
        }
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val artwork = if (current.artworkId != null) {
                    saveArtwork.update(current.artworkId, current.title, current.settings)
                } else {
                    saveArtwork.saveNew(route.sessionId!!, current.title, current.settings)
                }
                savedState[KEY_ARTWORK_ID] = artwork.id
                _state.update {
                    // Only clear `dirty` if nothing changed while we were saving.
                    val unchanged = it.settings == current.settings && it.title == current.title
                    it.copy(isSaving = false, artworkId = artwork.id, dirty = !unchanged, savedPulse = it.savedPulse + 1)
                }
                if (thenExport) _effects.send(EditorEffect.OpenExport(artwork.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false) }
                _effects.send(EditorEffect.Message(Format.error(e)))
            }
        }
    }

    private companion object {
        const val KEY_SETTINGS = "editor_settings"
        const val KEY_TITLE = "editor_title"
        const val KEY_ARTWORK_ID = "editor_artwork_id"
    }
}

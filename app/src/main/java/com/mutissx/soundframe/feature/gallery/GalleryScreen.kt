package com.mutissx.soundframe.feature.gallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.designsystem.art.ThumbnailImage
import com.mutissx.soundframe.core.designsystem.art.sharedArtwork
import com.mutissx.soundframe.core.designsystem.component.AmbientBackground
import com.mutissx.soundframe.core.designsystem.component.ArtLoader
import com.mutissx.soundframe.core.designsystem.component.GlassIconButton
import com.mutissx.soundframe.core.designsystem.component.LuminousButton
import com.mutissx.soundframe.core.designsystem.component.Overline
import com.mutissx.soundframe.core.designsystem.component.SelectablePill
import com.mutissx.soundframe.core.designsystem.icon.SfIcons
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfMotion
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SfSpacing
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.domain.model.GallerySort
import com.mutissx.soundframe.domain.repository.ArtworkRepository
import com.mutissx.soundframe.domain.usecase.EnsureThumbnailUseCase
import com.mutissx.soundframe.domain.usecase.ToggleFavoriteUseCase
import com.mutissx.soundframe.feature.common.ArtworkCardModel
import com.mutissx.soundframe.feature.common.label
import com.mutissx.soundframe.feature.common.toCard
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Immutable
data class GalleryUiState(
    val loading: Boolean = true,
    val items: List<ArtworkCardModel> = emptyList(),
    val sort: GallerySort = GallerySort.NEWEST,
    val favoritesOnly: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModel(
    private val artworks: ArtworkRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val ensureThumbnail: EnsureThumbnailUseCase,
) : ViewModel() {
    private val sort = MutableStateFlow(GallerySort.NEWEST)
    private val favoritesOnly = MutableStateFlow(false)

    val state: StateFlow<GalleryUiState> = combine(sort, favoritesOnly) { s, f -> s to f }
        .flatMapLatest { (s, f) ->
            artworks.observeArtworks(s, f).map { list -> GalleryUiState(false, list.map { it.toCard() }, s, f) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    fun setSort(value: GallerySort) { sort.value = value }
    fun setFavoritesOnly(value: Boolean) { favoritesOnly.value = value }
    fun toggleFavorite(id: String) { viewModelScope.launch { toggleFavorite.invoke(id) } }
    fun onThumbnailMissing(id: String) {
        viewModelScope.launch { artworks.getArtwork(id)?.let { runCatching { ensureThumbnail(it) } } }
    }
}

@Composable
fun GalleryScreen(
    onOpenArtwork: (String) -> Unit,
    onRecord: () -> Unit,
    viewModel: GalleryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        AmbientBackground()
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(start = SfSpacing.md, end = SfSpacing.md, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(SfSpacing.sm),
            verticalItemSpacing = SfSpacing.md,
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            item(span = StaggeredGridItemSpan.FullLine, key = "header") {
                Header(state, onSort = viewModel::setSort, onFavorites = viewModel::setFavoritesOnly)
            }
            when {
                state.loading -> item(span = StaggeredGridItemSpan.FullLine, key = "loading") {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { ArtLoader(Modifier.size(48.dp)) }
                }
                state.items.isEmpty() -> item(span = StaggeredGridItemSpan.FullLine, key = "empty") {
                    EmptyGallery(state.favoritesOnly, onRecord)
                }
                else -> itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                    GalleryTile(
                        item = item,
                        // Alternate proportions give the grid an editorial rhythm.
                        aspect = if (index % 3 == 1) 0.78f else 1f,
                        onClick = { onOpenArtwork(item.id) },
                        onFavorite = { viewModel.toggleFavorite(item.id) },
                        onMissing = { viewModel.onThumbnailMissing(item.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(state: GalleryUiState, onSort: (GallerySort) -> Unit, onFavorites: (Boolean) -> Unit) {
    Column(Modifier.padding(top = SfSpacing.lg, bottom = SfSpacing.sm, start = SfSpacing.xs)) {
        Overline(pluralStringResource(R.plurals.gallery_count, state.items.size, state.items.size), dot = true)
        Spacer(Modifier.height(SfSpacing.xs))
        Text(stringResource(R.string.nav_gallery), style = SoundframeTheme.type.hero, color = SfColors.TextPrimary)
        Spacer(Modifier.height(SfSpacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(SfSpacing.xs)) {
            SelectablePill(stringResource(R.string.gallery_newest), state.sort == GallerySort.NEWEST, { onSort(GallerySort.NEWEST) })
            SelectablePill(stringResource(R.string.gallery_oldest), state.sort == GallerySort.OLDEST, { onSort(GallerySort.OLDEST) })
            SelectablePill(stringResource(R.string.gallery_favourites), state.favoritesOnly, { onFavorites(!state.favoritesOnly) }, icon = SfIcons.Heart)
        }
    }
}

@Composable
private fun GalleryTile(
    item: ArtworkCardModel,
    aspect: Float,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onMissing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 1f else 0f, SfMotion.press(), label = "tilePress")
    Column(
        modifier
            .graphicsLayer {
                // Subtle tilt toward the viewer on touch.
                val s = 1f - 0.04f * press
                scaleX = s
                scaleY = s
                rotationX = 4f * press
                cameraDistance = 12f * density
            }
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.action_open_item, item.title), onClick = onClick),
    ) {
        Box {
            ThumbnailImage(
                item.thumbnailPath, item.title,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .sharedArtwork(item.id)
                    .clip(SfShapes.tile)
                    .border(1.dp, Color(0x1FFFFFFF), SfShapes.tile),
                onMissing = onMissing,
            )
            GlassIconButton(
                if (item.isFavorite) SfIcons.HeartFilled else SfIcons.Heart,
                stringResource(if (item.isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite),
                onFavorite,
                size = 40.dp,
                tint = if (item.isFavorite) SfColors.Rose else SfColors.TextPrimary,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            )
        }
        Spacer(Modifier.height(SfSpacing.xs))
        Text(item.title, style = SoundframeTheme.type.label, color = SfColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(stringResource(R.string.gallery_item_meta, stringResource(item.style.label), item.date), style = SoundframeTheme.type.caption, color = SfColors.TextTertiary)
    }
}

@Composable
private fun EmptyGallery(favoritesOnly: Boolean, onRecord: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = SfSpacing.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        ArtLoader(Modifier.size(96.dp))
        Spacer(Modifier.height(SfSpacing.lg))
        Text(
            stringResource(if (favoritesOnly) R.string.gallery_no_favourites_title else R.string.gallery_empty_title),
            style = SoundframeTheme.type.display, color = SfColors.TextPrimary,
        )
        Spacer(Modifier.height(SfSpacing.xs))
        Text(
            stringResource(if (favoritesOnly) R.string.gallery_no_favourites_body else R.string.gallery_empty_body),
            style = SoundframeTheme.type.body, color = SfColors.TextSecondary,
        )
        if (!favoritesOnly) {
            Spacer(Modifier.height(SfSpacing.lg))
            LuminousButton(stringResource(R.string.gallery_record), onRecord, icon = SfIcons.Mic)
        }
    }
}

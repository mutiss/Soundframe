package com.mutissx.soundframe.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mutissx.soundframe.R
import com.mutissx.soundframe.core.audio.DemoSignal
import com.mutissx.soundframe.core.designsystem.art.ArtworkCanvas
import com.mutissx.soundframe.core.designsystem.art.ThumbnailImage
import com.mutissx.soundframe.core.designsystem.component.AmbientBackground
import com.mutissx.soundframe.core.designsystem.component.GhostButton
import com.mutissx.soundframe.core.designsystem.component.GlassSurface
import com.mutissx.soundframe.core.designsystem.component.Overline
import com.mutissx.soundframe.core.designsystem.component.RecordOrb
import com.mutissx.soundframe.core.designsystem.component.TextAction
import com.mutissx.soundframe.core.designsystem.component.Wordmark
import com.mutissx.soundframe.core.designsystem.component.pressFeedback
import com.mutissx.soundframe.core.designsystem.icon.SfIcons
import com.mutissx.soundframe.core.designsystem.theme.LocalReduceMotion
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import com.mutissx.soundframe.core.designsystem.theme.SfShapes
import com.mutissx.soundframe.core.designsystem.theme.SfSpacing
import com.mutissx.soundframe.core.designsystem.theme.SoundframeTheme
import com.mutissx.soundframe.core.graphics.Palettes
import com.mutissx.soundframe.domain.model.GallerySort
import com.mutissx.soundframe.domain.repository.ArtworkRepository
import com.mutissx.soundframe.domain.usecase.EnsureThumbnailUseCase
import com.mutissx.soundframe.feature.common.ArtworkCardModel
import com.mutissx.soundframe.feature.common.DemoCompositions
import com.mutissx.soundframe.feature.common.label
import com.mutissx.soundframe.feature.common.toCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class HomeUiState(val recent: List<ArtworkCardModel> = emptyList(), val total: Int = 0)

class HomeViewModel(
    private val artworks: ArtworkRepository,
    private val ensureThumbnail: EnsureThumbnailUseCase,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = artworks.observeArtworks(GallerySort.NEWEST)
        .map { list -> HomeUiState(list.take(RECENT_COUNT).map { it.toCard() }, list.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onThumbnailMissing(id: String) {
        viewModelScope.launch { artworks.getArtwork(id)?.let { runCatching { ensureThumbnail(it) } } }
    }

    private companion object {
        const val RECENT_COUNT = 10
    }
}

@Composable
fun HomeScreen(
    onRecord: () -> Unit,
    onImport: (String) -> Unit,
    onOpenGallery: () -> Unit,
    onOpenArtwork: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it.toString()) }
    }
    val reduceMotion = LocalReduceMotion.current
    var heroIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        while (true) {
            delay(8_000)
            heroIndex = (heroIndex + 1) % DemoCompositions.size
        }
    }
    val hero = DemoCompositions[heroIndex]
    val palette = remember(hero) { Palettes.of(hero.settings.palette).stops.map { Color(it) } }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(colors = listOf(palette[2], palette[0], palette[4]), energy = { DemoSignal.liveLevel(System.nanoTime() / 1e9f) * 0.3f })
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(bottom = 120.dp),
        ) {
            Wordmark(Modifier.padding(horizontal = SfSpacing.screen, vertical = SfSpacing.md))
            Spacer(Modifier.height(SfSpacing.sm))
            val prefix = stringResource(R.string.home_headline_prefix)
            val accent = stringResource(R.string.home_headline_accent)
            val suffix = stringResource(R.string.home_headline_suffix)
            Text(
                buildAnnotatedString {
                    append(prefix)
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, brush = Brush.linearGradient(palette))) { append(accent) }
                    append(suffix)
                },
                style = SoundframeTheme.type.hero,
                color = SfColors.TextPrimary,
                modifier = Modifier.padding(horizontal = SfSpacing.screen),
            )
            Spacer(Modifier.height(SfSpacing.lg))

            HeroArtwork(heroIndex, onRecord)

            Spacer(Modifier.height(SfSpacing.sm))
            Text(
                stringResource(R.string.home_record_hint),
                style = SoundframeTheme.type.caption, color = SfColors.TextSecondary,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(SfSpacing.md))
            GhostButton(
                stringResource(R.string.home_import), onClick = { importer.launch(arrayOf("audio/*")) },
                icon = SfIcons.Import, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(SfSpacing.xl))

            Row(
                Modifier.fillMaxWidth().padding(start = SfSpacing.screen, end = SfSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Overline(stringResource(R.string.home_recent), dot = true, modifier = Modifier.weight(1f))
                if (state.total > 0) TextAction(stringResource(R.string.home_see_all, state.total), onOpenGallery, color = SfColors.TextPrimary)
            }
            Spacer(Modifier.height(SfSpacing.sm))
            if (state.recent.isEmpty()) {
                EmptyRecent(Modifier.padding(horizontal = SfSpacing.screen))
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = SfSpacing.screen),
                    horizontalArrangement = Arrangement.spacedBy(SfSpacing.sm),
                ) {
                    items(state.recent, key = { it.id }) { card ->
                        RecentCard(card, onClick = { onOpenArtwork(card.id) }, onMissing = { viewModel.onThumbnailMissing(card.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroArtwork(index: Int, onRecord: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = SfSpacing.screen)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.92f)
                .shadow(40.dp, SfShapes.artwork, ambientColor = SfColors.Violet, spotColor = SfColors.Cyan)
                .clip(SfShapes.artwork)
                .border(1.dp, Brush.linearGradient(listOf(Color(0x40FFFFFF), Color(0x08FFFFFF))), SfShapes.artwork),
        ) {
            AnimatedContent(index, transitionSpec = { fadeIn(tween(1400)) togetherWith fadeOut(tween(1400)) }, label = "hero") { i ->
                val demo = DemoCompositions[i]
                val features = remember(demo.preset) { DemoSignal.features(demo.preset) }
                ArtworkCanvas(features, demo.settings, Modifier.fillMaxSize(), contentDescription = stringResource(R.string.home_hero_description, stringResource(demo.caption)))
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0xB3000000))),
            )
            AnimatedContent(
                index,
                transitionSpec = { fadeIn(tween(700, 500)) togetherWith fadeOut(tween(400)) },
                modifier = Modifier.align(Alignment.BottomStart).padding(SfSpacing.lg).padding(bottom = 40.dp),
                label = "heroCaption",
            ) { i ->
                val demo = DemoCompositions[i]
                Column {
                    Overline(stringResource(demo.settings.style.label), color = SfColors.TextPrimary)
                    Text(stringResource(R.string.home_hero_from, stringResource(demo.caption)), style = SoundframeTheme.type.headline, color = SfColors.TextPrimary)
                }
            }
        }
        RecordOrb(
            recording = false,
            onClick = onRecord,
            size = 84.dp,
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = 72.dp),
        )
    }
    Spacer(Modifier.height(64.dp))
}

@Composable
private fun RecentCard(card: ArtworkCardModel, onClick: () -> Unit, onMissing: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .width(148.dp)
            .pressFeedback(interaction)
            .clickable(interaction, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.action_open_item, card.title), onClick = onClick),
    ) {
        ThumbnailImage(
            card.thumbnailPath, card.title,
            Modifier.size(148.dp).clip(SfShapes.tile).border(1.dp, Color(0x1FFFFFFF), SfShapes.tile),
            onMissing = onMissing,
        )
        Spacer(Modifier.height(SfSpacing.xs))
        Text(card.title, style = SoundframeTheme.type.label, color = SfColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(card.date, style = SoundframeTheme.type.caption, color = SfColors.TextTertiary)
    }
}

@Composable
private fun EmptyRecent(modifier: Modifier = Modifier) {
    GlassSurface(modifier.fillMaxWidth(), shape = SfShapes.tile) {
        Column(Modifier.padding(SfSpacing.lg)) {
            Text(stringResource(R.string.home_empty_title), style = SoundframeTheme.type.title, color = SfColors.TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.home_empty_body),
                style = SoundframeTheme.type.bodySmall, color = SfColors.TextSecondary,
            )
        }
    }
}

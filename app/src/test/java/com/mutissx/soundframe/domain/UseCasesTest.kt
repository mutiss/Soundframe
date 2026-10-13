package com.mutissx.soundframe.domain

import com.mutissx.soundframe.data.mapper.ArtworkMapper
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.ArtworkSettings
import com.mutissx.soundframe.domain.model.AudioClip
import com.mutissx.soundframe.domain.model.AudioSource
import com.mutissx.soundframe.domain.model.ExportImageSize
import com.mutissx.soundframe.domain.model.PremiumFeature
import com.mutissx.soundframe.domain.model.RecordingException
import com.mutissx.soundframe.domain.model.SessionExpiredException
import com.mutissx.soundframe.domain.model.TitleVocabulary
import com.mutissx.soundframe.domain.repository.UserPreferences
import com.mutissx.soundframe.domain.repository.VideoExportProgress
import com.mutissx.soundframe.domain.usecase.AnalyzeSessionUseCase
import com.mutissx.soundframe.domain.usecase.DeleteArtworkUseCase
import com.mutissx.soundframe.domain.usecase.EnsureThumbnailUseCase
import com.mutissx.soundframe.domain.usecase.ExportImageUseCase
import com.mutissx.soundframe.domain.usecase.ExportVideoUseCase
import com.mutissx.soundframe.domain.usecase.RecordAudioUseCase
import com.mutissx.soundframe.domain.usecase.RenameArtworkUseCase
import com.mutissx.soundframe.domain.usecase.ResolvePlayableAudioUseCase
import com.mutissx.soundframe.domain.usecase.SaveArtworkUseCase
import com.mutissx.soundframe.domain.usecase.TitleRules
import com.mutissx.soundframe.domain.usecase.ToggleFavoriteUseCase
import com.mutissx.soundframe.testing.FakeAnalyzer
import com.mutissx.soundframe.testing.FakeArtworkRepository
import com.mutissx.soundframe.testing.FakeAudioRecorder
import com.mutissx.soundframe.testing.FakeAudioStore
import com.mutissx.soundframe.testing.FakeClock
import com.mutissx.soundframe.testing.FakeEntitlements
import com.mutissx.soundframe.testing.FakeExporter
import com.mutissx.soundframe.testing.FakePreferences
import com.mutissx.soundframe.testing.FakeSessions
import com.mutissx.soundframe.testing.FakeThumbnails
import com.mutissx.soundframe.testing.SequentialIds
import com.mutissx.soundframe.testing.Signals
import com.mutissx.soundframe.testing.sampleArtwork
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UseCasesTest {

    private val artworks = FakeArtworkRepository()
    private val sessions = FakeSessions()
    private val audioStore = FakeAudioStore()
    private val thumbnails = FakeThumbnails()
    private val prefs = FakePreferences()
    private val clock = FakeClock()
    private val save = SaveArtworkUseCase(artworks, sessions, audioStore, thumbnails, prefs, clock, SequentialIds()) { TitleVocabulary.ENGLISH }

    private suspend fun analysedSession(): String {
        val session = sessions.create(Signals.toClip(Signals.sine(440f, 1f)), AudioSource.RECORDED)
        AnalyzeSessionUseCase(FakeAnalyzer(), sessions)(session.id)
        return session.id
    }

    @Test
    fun `analysis is cached per session`() = runTest {
        val analyzer = FakeAnalyzer()
        val session = sessions.create(Signals.toClip(Signals.sine(440f, 1f)), AudioSource.RECORDED)
        val useCase = AnalyzeSessionUseCase(analyzer, sessions)
        val first = useCase(session.id)
        val second = useCase(session.id)
        assertEquals(first, second)
        assertEquals(1, analyzer.calls)
    }

    @Test
    fun `missing session is reported as expired`() = runTest {
        val error = runCatching { AnalyzeSessionUseCase(FakeAnalyzer(), sessions)("nope") }.exceptionOrNull()
        assertTrue(error is SessionExpiredException)
    }

    @Test
    fun `too-short recordings are rejected`() = runTest {
        val recorder = FakeAudioRecorder()
        recorder.result.complete(AudioClip(ShortArray(1000), 44_100))
        val error = runCatching { RecordAudioUseCase(recorder, sessions)() }.exceptionOrNull()
        assertTrue(error is RecordingException.TooShort)
        assertTrue(sessions.sessions.isEmpty())
    }

    @Test
    fun `saving keeps audio, renders a thumbnail and falls back to a generated title`() = runTest {
        val sessionId = analysedSession()
        val artwork = save.saveNew(sessionId, "   ", ArtworkSettings(style = ArtStyle.ECHO))
        assertNotNull(artwork.audioPath)
        assertTrue(audioStore.files.containsKey(artwork.audioPath))
        assertEquals(1, thumbnails.renders)
        assertTrue(artwork.title.isNotBlank())
        assertEquals(artwork, artworks.getArtwork(artwork.id))
    }

    @Test
    fun `audio is not kept when the user opted out`() = runTest {
        prefs.state.value = UserPreferences(keepOriginalAudio = false)
        val artwork = save.saveNew(analysedSession(), "Rain", ArtworkSettings())
        assertNull(artwork.audioPath)
        assertTrue(audioStore.files.isEmpty())
    }

    @Test
    fun `updating changes settings but never the analysed features`() = runTest {
        val original = save.saveNew(analysedSession(), "First", ArtworkSettings())
        clock.time = 9_000
        val updated = save.update(original.id, "Second", ArtworkSettings(style = ArtStyle.BLOOM))
        assertEquals(original.features, updated.features)
        assertEquals(original.createdAt, updated.createdAt)
        assertEquals(9_000, updated.updatedAt)
        assertEquals("Second", updated.title)
        assertEquals(ArtStyle.BLOOM, updated.settings.style)
    }

    @Test
    fun `storage failures surface to the caller`() = runTest {
        audioStore.failWith = com.mutissx.soundframe.domain.model.StorageException.InsufficientSpace()
        val sessionId = analysedSession()
        val error = runCatching { save.saveNew(sessionId, "x", ArtworkSettings()) }.exceptionOrNull()
        assertTrue(error is com.mutissx.soundframe.domain.model.StorageException.InsufficientSpace)
        assertTrue(artworks.items.value.isEmpty())
    }

    @Test
    fun `rename validates titles`() = runTest {
        artworks.upsert(sampleArtwork())
        val rename = RenameArtworkUseCase(artworks)
        assertFalse(rename("a1", "   "))
        assertTrue(rename("a1", "  Ocean   at   night "))
        assertEquals("Ocean at night", artworks.getArtwork("a1")!!.title)
        assertEquals(TitleRules.MAX_LENGTH, TitleRules.normalize("x".repeat(100))!!.length)
    }

    @Test
    fun `favourite toggles`() = runTest {
        artworks.upsert(sampleArtwork())
        val toggle = ToggleFavoriteUseCase(artworks)
        toggle("a1")
        assertTrue(artworks.getArtwork("a1")!!.isFavorite)
        toggle("a1")
        assertFalse(artworks.getArtwork("a1")!!.isFavorite)
    }

    @Test
    fun `delete removes row, audio and thumbnail`() = runTest {
        val artwork = sampleArtwork()
        artworks.upsert(artwork)
        audioStore.files[artwork.audioPath!!] = AudioClip(ShortArray(10), 44_100)
        thumbnails.existing += artwork.thumbnailPath!!
        DeleteArtworkUseCase(artworks, audioStore, thumbnails)("a1")
        assertNull(artworks.getArtwork("a1"))
        assertTrue(audioStore.files.isEmpty())
        assertTrue(thumbnails.existing.isEmpty())
    }

    @Test
    fun `missing thumbnails are regenerated, missing audio hides playback`() = runTest {
        val artwork = sampleArtwork()
        artworks.upsert(artwork)
        val path = EnsureThumbnailUseCase(artworks, thumbnails)(artwork)
        assertEquals(1, thumbnails.renders)
        assertEquals(path, artworks.getArtwork("a1")!!.thumbnailPath)
        EnsureThumbnailUseCase(artworks, thumbnails)(artworks.getArtwork("a1")!!)
        assertEquals(1, thumbnails.renders)

        assertNull(ResolvePlayableAudioUseCase(audioStore)(artwork))
    }

    @Test
    fun `exports respect entitlements and clamp video options`() = runTest {
        val exporter = FakeExporter()
        val locked = FakeEntitlements(emptySet())
        ExportImageUseCase(exporter, locked)(sampleArtwork(), ExportImageSize.ULTRA, watermark = false)
        assertEquals(ExportImageSize.SQUARE, exporter.lastImage!!.second)
        assertTrue("branding stays without entitlement", exporter.lastImage!!.third)

        val unlocked = FakeEntitlements(setOf(PremiumFeature.ULTRA_RESOLUTION_EXPORT, PremiumFeature.BRANDING_REMOVAL))
        ExportImageUseCase(exporter, unlocked)(sampleArtwork(), ExportImageSize.ULTRA, watermark = false)
        assertEquals(ExportImageSize.ULTRA, exporter.lastImage!!.second)
        assertFalse(exporter.lastImage!!.third)

        val events = ExportVideoUseCase(exporter, FakeEntitlements())(sampleArtwork().copy(audioPath = null), 60, includeAudio = true, watermark = true).toList()
        assertEquals(ExportVideoUseCase.MAX_SECONDS, exporter.lastVideo!!.first)
        assertFalse("no audio track without a recording", exporter.lastVideo!!.second)
        assertTrue(events.last() is VideoExportProgress.Done)
    }

    @Test
    fun `mapper round-trips and skips corrupt rows`() {
        val artwork = sampleArtwork()
        val entity = ArtworkMapper.toEntity(artwork)
        assertEquals(artwork, ArtworkMapper.toDomain(entity))
        assertNull(ArtworkMapper.toDomain(entity.copy(featuresJson = "{broken")))
    }
}

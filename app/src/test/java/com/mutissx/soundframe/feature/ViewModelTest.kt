package com.mutissx.soundframe.feature

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.mutissx.soundframe.app.navigation.EditorRoute
import com.mutissx.soundframe.domain.model.ArtStyle
import com.mutissx.soundframe.domain.model.AudioSource
import com.mutissx.soundframe.domain.model.ExportImageSize
import com.mutissx.soundframe.domain.model.RecordingException
import com.mutissx.soundframe.domain.model.StorageException
import com.mutissx.soundframe.domain.model.TitleVocabulary
import com.mutissx.soundframe.domain.usecase.AnalyzeSessionUseCase
import com.mutissx.soundframe.domain.usecase.ExportImageUseCase
import com.mutissx.soundframe.domain.usecase.ExportVideoUseCase
import com.mutissx.soundframe.domain.usecase.RecordAudioUseCase
import com.mutissx.soundframe.domain.usecase.ResolvePlayableAudioUseCase
import com.mutissx.soundframe.domain.usecase.SaveArtworkUseCase
import com.mutissx.soundframe.feature.editor.EditorAction
import com.mutissx.soundframe.feature.editor.EditorEffect
import com.mutissx.soundframe.feature.editor.EditorViewModel
import com.mutissx.soundframe.feature.export.ExportAction
import com.mutissx.soundframe.feature.export.ExportJob
import com.mutissx.soundframe.feature.export.ExportMode
import com.mutissx.soundframe.feature.export.ExportViewModel
import com.mutissx.soundframe.feature.recording.MicPermission
import com.mutissx.soundframe.feature.recording.RecordingAction
import com.mutissx.soundframe.feature.recording.RecordingEffect
import com.mutissx.soundframe.feature.recording.RecordingPhase
import com.mutissx.soundframe.feature.recording.RecordingViewModel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // --- Recording -------------------------------------------------------------------------

    @Test
    fun `recording without permission asks for it, permanent denial is remembered`() = runTest(dispatcher) {
        val vm = RecordingViewModel(RecordAudioUseCase(FakeAudioRecorder(), FakeSessions()))
        vm.effects.test {
            vm.onEntered(permissionGranted = false, autoStart = true)
            assertEquals(RecordingEffect.RequestPermission, awaitItem())
            vm.onAction(RecordingAction.PermissionResult(granted = false, canAskAgain = false))
            advanceUntilIdle()
            assertEquals(MicPermission.PERMANENTLY_DENIED, vm.state.value.permission)
            assertEquals(RecordingPhase.IDLE, vm.state.value.phase)
            vm.onAction(RecordingAction.ToggleRecording)
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `successful recording opens generation`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder()
        val vm = RecordingViewModel(RecordAudioUseCase(recorder, FakeSessions()))
        vm.effects.test {
            vm.onEntered(permissionGranted = true, autoStart = true)
            advanceUntilIdle()
            assertEquals(RecordingPhase.RECORDING, vm.state.value.phase)
            vm.onAction(RecordingAction.ToggleRecording)
            assertTrue(recorder.stopRequested)
            recorder.result.complete(Signals.toClip(Signals.sine(440f, 2f)))
            advanceUntilIdle()
            assertTrue(awaitItem() is RecordingEffect.OpenGeneration)
            assertEquals(RecordingPhase.IDLE, vm.state.value.phase)
        }
    }

    @Test
    fun `microphone failure and interruption become recoverable errors`() = runTest(dispatcher) {
        val recorder = FakeAudioRecorder()
        val vm = RecordingViewModel(RecordAudioUseCase(recorder, FakeSessions()))
        vm.onEntered(permissionGranted = true, autoStart = true)
        advanceUntilIdle()
        recorder.result.completeExceptionally(RecordingException.MicrophoneUnavailable())
        advanceUntilIdle()
        assertEquals(RecordingPhase.ERROR, vm.state.value.phase)
        assertNotNull(vm.state.value.errorRes)

        vm.onAction(RecordingAction.DismissError)
        recorder.result = kotlinx.coroutines.CompletableDeferred()
        vm.onAction(RecordingAction.ToggleRecording)
        advanceUntilIdle()
        assertEquals(RecordingPhase.RECORDING, vm.state.value.phase)
        vm.onAction(RecordingAction.Backgrounded)
        advanceUntilIdle()
        assertEquals(RecordingPhase.ERROR, vm.state.value.phase)
    }

    // --- Editor ----------------------------------------------------------------------------

    private suspend fun editorFixture(): Triple<EditorViewModel, FakeArtworkRepository, SavedStateHandle> {
        val sessions = FakeSessions()
        val session = sessions.create(Signals.toClip(Signals.sine(330f, 1.5f)), AudioSource.RECORDED)
        val artworks = FakeArtworkRepository()
        val analyze = AnalyzeSessionUseCase(FakeAnalyzer(), sessions)
        val save = SaveArtworkUseCase(artworks, sessions, FakeAudioStore(), FakeThumbnails(), FakePreferences(), FakeClock(), SequentialIds()) { TitleVocabulary.ENGLISH }
        val handle = SavedStateHandle()
        return Triple(EditorViewModel(EditorRoute(sessionId = session.id), handle, artworks, analyze, save) { TitleVocabulary.ENGLISH }, artworks, handle)
    }

    @Test
    fun `editor edits settings without touching features and saves`() = runTest(dispatcher) {
        val (vm, artworks, handle) = editorFixture()
        advanceUntilIdle()
        val features = vm.state.value.features
        assertNotNull(features)
        vm.onAction(EditorAction.SelectStyle(ArtStyle.TOPOGRAPHY))
        vm.onAction(EditorAction.SetIntensity(2f))
        assertEquals(ArtStyle.TOPOGRAPHY, vm.state.value.settings.style)
        assertEquals(1f, vm.state.value.settings.intensity)
        assertEquals(features, vm.state.value.features)
        assertNotNull("settings survive process death", handle.get<String>("editor_settings"))

        vm.onAction(EditorAction.Save)
        advanceUntilIdle()
        val id = vm.state.value.artworkId
        assertNotNull(id)
        assertFalse(vm.state.value.dirty)
        assertEquals(ArtStyle.TOPOGRAPHY, artworks.getArtwork(id!!)!!.settings.style)
    }

    @Test
    fun `export from editor saves first then navigates`() = runTest(dispatcher) {
        val (vm, _, _) = editorFixture()
        advanceUntilIdle()
        vm.effects.test {
            vm.onAction(EditorAction.Export)
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is EditorEffect.OpenExport)
            assertEquals(vm.state.value.artworkId, (effect as EditorEffect.OpenExport).artworkId)
        }
    }

    @Test
    fun `variations are deterministic and reversible`() = runTest(dispatcher) {
        val (vm, _, _) = editorFixture()
        advanceUntilIdle()
        vm.onAction(EditorAction.NextVariation)
        vm.onAction(EditorAction.NextVariation)
        assertEquals(2, vm.state.value.settings.variation)
        vm.onAction(EditorAction.OriginalVariation)
        assertEquals(0, vm.state.value.settings.variation)
    }

    // --- Export ----------------------------------------------------------------------------

    private fun exportFixture(exporter: FakeExporter): ExportViewModel {
        val artworks = FakeArtworkRepository()
        kotlinx.coroutines.runBlocking { artworks.upsert(sampleArtwork()) }
        val entitlements = FakeEntitlements()
        return ExportViewModel(
            "a1", artworks, FakePreferences(), ExportImageUseCase(exporter, entitlements),
            ExportVideoUseCase(exporter, entitlements), exporter, ResolvePlayableAudioUseCase(FakeAudioStore()),
        )
    }

    @Test
    fun `image export reports success and failure`() = runTest(dispatcher) {
        val exporter = FakeExporter()
        val vm = exportFixture(exporter)
        advanceUntilIdle()
        assertFalse("audio file missing, so no soundtrack", vm.state.value.hasAudio)
        vm.onAction(ExportAction.SetSize(ExportImageSize.STORY))
        vm.onAction(ExportAction.Render)
        advanceUntilIdle()
        assertTrue(vm.state.value.image is ExportJob.Done)
        assertEquals(ExportImageSize.STORY, exporter.lastImage!!.second)

        exporter.imageFailure = StorageException.InsufficientSpace()
        vm.onAction(ExportAction.SetSize(ExportImageSize.SQUARE))
        vm.onAction(ExportAction.Render)
        advanceUntilIdle()
        assertTrue(vm.state.value.image is ExportJob.Failed)
    }

    @Test
    fun `video export can be cancelled and cleans up`() = runTest(dispatcher) {
        val exporter = FakeExporter().apply { hangVideo = true }
        val vm = exportFixture(exporter)
        advanceUntilIdle()
        vm.onAction(ExportAction.SetMode(ExportMode.VIDEO))
        vm.onAction(ExportAction.Render)
        advanceUntilIdle()
        assertEquals(ExportJob.Rendering(0.5f), vm.state.value.video)
        vm.onAction(ExportAction.Cancel)
        advanceUntilIdle()
        assertEquals(ExportJob.Idle, vm.state.value.video)
        assertTrue(exporter.videoCancelled)
    }
}

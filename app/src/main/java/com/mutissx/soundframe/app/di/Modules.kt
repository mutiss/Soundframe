package com.mutissx.soundframe.app.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.mutissx.soundframe.app.MainViewModel
import com.mutissx.soundframe.app.navigation.EditorRoute
import com.mutissx.soundframe.app.navigation.GenerationRoute
import com.mutissx.soundframe.core.common.AppDispatchers
import com.mutissx.soundframe.core.common.BrandTypeface
import com.mutissx.soundframe.core.common.StoragePaths
import com.mutissx.soundframe.data.audio.AndroidAudioRecorder
import com.mutissx.soundframe.data.audio.DefaultAudioAnalyzer
import com.mutissx.soundframe.data.audio.FileAudioStore
import com.mutissx.soundframe.data.audio.MediaAudioDecoder
import com.mutissx.soundframe.data.audio.MediaAudioPlayer
import com.mutissx.soundframe.data.export.ArtworkExporterImpl
import com.mutissx.soundframe.data.export.ThumbnailRendererImpl
import com.mutissx.soundframe.data.local.SoundframeDatabase
import com.mutissx.soundframe.data.repository.ArtworkRepositoryImpl
import com.mutissx.soundframe.data.repository.CreationSessionRepositoryImpl
import com.mutissx.soundframe.data.repository.FreeEntitlements
import com.mutissx.soundframe.data.repository.PreferencesRepositoryImpl
import com.mutissx.soundframe.data.repository.ResourceTitleVocabulary
import com.mutissx.soundframe.data.repository.SystemClock
import com.mutissx.soundframe.data.repository.UuidGenerator
import com.mutissx.soundframe.domain.repository.ArtworkExporter
import com.mutissx.soundframe.domain.repository.ArtworkRepository
import com.mutissx.soundframe.domain.repository.AudioAnalyzer
import com.mutissx.soundframe.domain.repository.AudioDecoder
import com.mutissx.soundframe.domain.repository.AudioPlayer
import com.mutissx.soundframe.domain.repository.AudioRecorder
import com.mutissx.soundframe.domain.repository.AudioStore
import com.mutissx.soundframe.domain.repository.Clock
import com.mutissx.soundframe.domain.repository.CreationSessionRepository
import com.mutissx.soundframe.domain.repository.FeatureEntitlements
import com.mutissx.soundframe.domain.repository.IdGenerator
import com.mutissx.soundframe.domain.repository.PreferencesRepository
import com.mutissx.soundframe.domain.repository.ThumbnailRenderer
import com.mutissx.soundframe.domain.repository.TitleVocabularyProvider
import com.mutissx.soundframe.domain.usecase.AnalyzeSessionUseCase
import com.mutissx.soundframe.domain.usecase.DeleteAllDataUseCase
import com.mutissx.soundframe.domain.usecase.DeleteArtworkUseCase
import com.mutissx.soundframe.domain.usecase.EnsureThumbnailUseCase
import com.mutissx.soundframe.domain.usecase.ExportImageUseCase
import com.mutissx.soundframe.domain.usecase.ExportVideoUseCase
import com.mutissx.soundframe.domain.usecase.ImportAudioUseCase
import com.mutissx.soundframe.domain.usecase.RecordAudioUseCase
import com.mutissx.soundframe.domain.usecase.RenameArtworkUseCase
import com.mutissx.soundframe.domain.usecase.ResolvePlayableAudioUseCase
import com.mutissx.soundframe.domain.usecase.SaveArtworkUseCase
import com.mutissx.soundframe.domain.usecase.ToggleFavoriteUseCase
import com.mutissx.soundframe.feature.artworkdetail.ArtworkDetailViewModel
import com.mutissx.soundframe.feature.editor.EditorViewModel
import com.mutissx.soundframe.feature.export.ExportViewModel
import com.mutissx.soundframe.feature.gallery.GalleryViewModel
import com.mutissx.soundframe.feature.generation.GenerationViewModel
import com.mutissx.soundframe.feature.home.HomeViewModel
import com.mutissx.soundframe.feature.onboarding.OnboardingViewModel
import com.mutissx.soundframe.feature.recording.RecordingViewModel
import com.mutissx.soundframe.feature.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val coreModule = module {
    single { AppDispatchers() }
    single { StoragePaths(androidContext()) }
    single { BrandTypeface(androidContext()) }
    single<Clock> { SystemClock }
    single<IdGenerator> { UuidGenerator }
}

val dataModule = module {
    single {
        Room.databaseBuilder(androidContext(), SoundframeDatabase::class.java, SoundframeDatabase.NAME).build()
    }
    single { get<SoundframeDatabase>().artworkDao() }
    single { PreferenceDataStoreFactory.create { androidContext().preferencesDataStoreFile("soundframe_preferences") } }

    singleOf(::ArtworkRepositoryImpl) bind ArtworkRepository::class
    singleOf(::CreationSessionRepositoryImpl) bind CreationSessionRepository::class
    singleOf(::PreferencesRepositoryImpl) bind PreferencesRepository::class
    singleOf(::FileAudioStore) bind AudioStore::class
    singleOf(::FreeEntitlements) bind FeatureEntitlements::class
    single<AudioRecorder> { AndroidAudioRecorder(androidContext(), get()) }
    single<AudioDecoder> { MediaAudioDecoder(androidContext(), get()) }
    singleOf(::DefaultAudioAnalyzer) bind AudioAnalyzer::class
    factoryOf(::MediaAudioPlayer) bind AudioPlayer::class
    singleOf(::ThumbnailRendererImpl) bind ThumbnailRenderer::class
    single<TitleVocabularyProvider> { ResourceTitleVocabulary(androidContext()) }
    single<ArtworkExporter> { ArtworkExporterImpl(androidContext(), get(), get(), get(), get()) }
}

val domainModule = module {
    factoryOf(::RecordAudioUseCase)
    factoryOf(::ImportAudioUseCase)
    factoryOf(::AnalyzeSessionUseCase)
    factoryOf(::SaveArtworkUseCase)
    factoryOf(::RenameArtworkUseCase)
    factoryOf(::ToggleFavoriteUseCase)
    factoryOf(::DeleteArtworkUseCase)
    factoryOf(::DeleteAllDataUseCase)
    factoryOf(::EnsureThumbnailUseCase)
    factoryOf(::ResolvePlayableAudioUseCase)
    factoryOf(::ExportImageUseCase)
    factoryOf(::ExportVideoUseCase)
}

val featureModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::OnboardingViewModel)
    viewModelOf(::HomeViewModel)
    viewModelOf(::RecordingViewModel)
    viewModelOf(::GalleryViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { (route: GenerationRoute) -> GenerationViewModel(route, get(), get(), get(), get()) }
    viewModel { (route: EditorRoute) -> EditorViewModel(route, get(), get(), get(), get(), get()) }
    viewModel { (id: String) -> ArtworkDetailViewModel(id, get(), get(), get(), get(), get(), get()) }
    viewModel { (id: String) -> ExportViewModel(id, get(), get(), get(), get(), get(), get()) }
}

val appModules = listOf(coreModule, dataModule, domainModule, featureModule)

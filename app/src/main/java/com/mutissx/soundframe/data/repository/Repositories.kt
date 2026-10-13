package com.mutissx.soundframe.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.mutissx.soundframe.core.common.AppDispatchers
import com.mutissx.soundframe.core.common.FileOps
import com.mutissx.soundframe.core.common.StoragePaths
import com.mutissx.soundframe.data.local.ArtworkDao
import com.mutissx.soundframe.data.mapper.ArtworkMapper
import com.mutissx.soundframe.domain.model.Artwork
import com.mutissx.soundframe.domain.model.AudioClip
import com.mutissx.soundframe.domain.model.AudioFeatures
import com.mutissx.soundframe.domain.model.AudioSource
import com.mutissx.soundframe.domain.model.CreationSession
import com.mutissx.soundframe.domain.model.GallerySort
import com.mutissx.soundframe.domain.model.PremiumFeature
import com.mutissx.soundframe.domain.repository.ArtworkRepository
import com.mutissx.soundframe.domain.repository.AudioStore
import com.mutissx.soundframe.domain.repository.Clock
import com.mutissx.soundframe.domain.repository.CreationSessionRepository
import com.mutissx.soundframe.domain.repository.FeatureEntitlements
import com.mutissx.soundframe.domain.repository.IdGenerator
import com.mutissx.soundframe.domain.repository.PreferencesRepository
import com.mutissx.soundframe.domain.repository.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ArtworkRepositoryImpl(
    private val dao: ArtworkDao,
    private val clock: Clock,
    private val dispatchers: AppDispatchers,
) : ArtworkRepository {

    override fun observeArtworks(sort: GallerySort, favoritesOnly: Boolean): Flow<List<Artwork>> {
        val source = when (sort) {
            GallerySort.NEWEST -> dao.observeNewest(favoritesOnly)
            GallerySort.OLDEST -> dao.observeOldest(favoritesOnly)
        }
        return source.map { rows -> rows.mapNotNull(ArtworkMapper::toDomain) }.flowOn(dispatchers.default)
    }

    override fun observeArtwork(id: String): Flow<Artwork?> =
        dao.observe(id).map { it?.let(ArtworkMapper::toDomain) }.flowOn(dispatchers.default)

    override suspend fun getArtwork(id: String): Artwork? = dao.get(id)?.let(ArtworkMapper::toDomain)

    override suspend fun upsert(artwork: Artwork) = dao.upsert(ArtworkMapper.toEntity(artwork))

    override suspend fun rename(id: String, title: String) = dao.rename(id, title, clock.now())

    override suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)

    override suspend fun updateThumbnail(id: String, path: String?) = dao.setThumbnail(id, path)

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun allArtworks(): List<Artwork> = dao.all().mapNotNull(ArtworkMapper::toDomain)
}

/**
 * Keeps the in-progress creation in memory and mirrors its audio to the cache directory, so a
 * session survives process death between recording and saving (features are recomputed).
 */
class CreationSessionRepositoryImpl(
    private val audioStore: AudioStore,
    private val paths: StoragePaths,
    private val ids: IdGenerator,
    private val dispatchers: AppDispatchers,
) : CreationSessionRepository {
    private val sessions = ConcurrentHashMap<String, CreationSession>()

    override suspend fun create(clip: AudioClip, source: AudioSource): CreationSession {
        withContext(dispatchers.io) { FileOps.deleteOlderThan(paths.sessionDir, STALE_MS) }
        val session = CreationSession(ids.newId(), clip, source)
        sessions[session.id] = session
        runCatching { audioStore.save(session.id, clip, temporary = true) }
        return session
    }

    override suspend fun get(id: String): CreationSession? {
        sessions[id]?.let { return it }
        val path = File(paths.sessionDir, "$id.wav").absolutePath
        if (!audioStore.exists(path)) return null
        val clip = runCatching { audioStore.load(path) }.getOrNull() ?: return null
        return CreationSession(id, clip, AudioSource.RECORDED).also { sessions[id] = it }
    }

    override suspend fun attachFeatures(id: String, features: AudioFeatures) {
        sessions.computeIfPresent(id) { _, s -> s.copy(features = features) }
    }

    override suspend fun discard(id: String) {
        sessions.remove(id)
        audioStore.delete(File(paths.sessionDir, "$id.wav").absolutePath)
    }

    private companion object {
        const val STALE_MS = 24L * 60 * 60 * 1000
    }
}

class PreferencesRepositoryImpl(private val store: DataStore<Preferences>) : PreferencesRepository {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_completed")
        val keepAudio = booleanPreferencesKey("keep_original_audio")
        val watermark = booleanPreferencesKey("watermark_exports")
        val reduceMotion = booleanPreferencesKey("reduce_motion")
    }

    override val preferences: Flow<UserPreferences> = store.data.map { p ->
        UserPreferences(
            onboardingCompleted = p[Keys.onboarding] ?: false,
            keepOriginalAudio = p[Keys.keepAudio] ?: true,
            watermarkExports = p[Keys.watermark] ?: true,
            reduceMotion = p[Keys.reduceMotion] ?: false,
        )
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) { store.edit { it[Keys.onboarding] = completed } }
    override suspend fun setKeepOriginalAudio(keep: Boolean) { store.edit { it[Keys.keepAudio] = keep } }
    override suspend fun setWatermarkExports(enabled: Boolean) { store.edit { it[Keys.watermark] = enabled } }
    override suspend fun setReduceMotion(enabled: Boolean) { store.edit { it[Keys.reduceMotion] = enabled } }
}

/** MVP: everything is unlocked. Swap for a billing-backed implementation later. */
class FreeEntitlements : FeatureEntitlements {
    private val all = MutableStateFlow(PremiumFeature.entries.toSet())
    override val unlocked: StateFlow<Set<PremiumFeature>> = all
    override fun isUnlocked(feature: PremiumFeature): Boolean = feature in all.value
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

object UuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

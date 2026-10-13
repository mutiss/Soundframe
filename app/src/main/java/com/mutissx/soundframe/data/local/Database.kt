package com.mutissx.soundframe.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Metadata only. Audio and thumbnails live as files; features and settings are small JSON
 * documents so the artwork can be re-rendered at any resolution without re-analysing audio.
 */
@Entity(tableName = "artworks", indices = [Index("createdAt"), Index("isFavorite")])
data class ArtworkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val featuresJson: String,
    val settingsJson: String,
    val audioPath: String?,
    val thumbnailPath: String?,
    val isFavorite: Boolean,
    val engineVersion: Int,
)

@Dao
interface ArtworkDao {
    @Query("SELECT * FROM artworks WHERE (:favoritesOnly = 0 OR isFavorite = 1) ORDER BY createdAt DESC")
    fun observeNewest(favoritesOnly: Boolean): Flow<List<ArtworkEntity>>

    @Query("SELECT * FROM artworks WHERE (:favoritesOnly = 0 OR isFavorite = 1) ORDER BY createdAt ASC")
    fun observeOldest(favoritesOnly: Boolean): Flow<List<ArtworkEntity>>

    @Query("SELECT * FROM artworks WHERE id = :id")
    fun observe(id: String): Flow<ArtworkEntity?>

    @Query("SELECT * FROM artworks WHERE id = :id")
    suspend fun get(id: String): ArtworkEntity?

    @Query("SELECT * FROM artworks")
    suspend fun all(): List<ArtworkEntity>

    @Upsert
    suspend fun upsert(entity: ArtworkEntity)

    @Query("UPDATE artworks SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, now: Long)

    @Query("UPDATE artworks SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query("UPDATE artworks SET thumbnailPath = :path WHERE id = :id")
    suspend fun setThumbnail(id: String, path: String?)

    @Query("DELETE FROM artworks WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(entities = [ArtworkEntity::class], version = 1, exportSchema = true)
abstract class SoundframeDatabase : RoomDatabase() {
    abstract fun artworkDao(): ArtworkDao

    companion object {
        const val NAME = "soundframe.db"
    }
}

package com.mutissx.soundframe

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mutissx.soundframe.data.local.ArtworkEntity
import com.mutissx.soundframe.data.local.SoundframeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtworkDaoTest {
    private lateinit var db: SoundframeDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SoundframeDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(id: String, createdAt: Long, favorite: Boolean = false) = ArtworkEntity(
        id = id, title = "Artwork $id", createdAt = createdAt, updatedAt = createdAt,
        featuresJson = "{}", settingsJson = "{}", audioPath = null, thumbnailPath = null,
        isFavorite = favorite, engineVersion = 1,
    )

    @Test
    fun sortingFavoritesRenameAndDelete() = runTest {
        val dao = db.artworkDao()
        dao.upsert(entity("a", 1))
        dao.upsert(entity("b", 3, favorite = true))
        dao.upsert(entity("c", 2))

        assertEquals(listOf("b", "c", "a"), dao.observeNewest(false).first().map { it.id })
        assertEquals(listOf("a", "c", "b"), dao.observeOldest(false).first().map { it.id })
        assertEquals(listOf("b"), dao.observeNewest(true).first().map { it.id })

        dao.rename("a", "Renamed", 10)
        assertEquals("Renamed", dao.get("a")!!.title)
        assertEquals(10, dao.get("a")!!.updatedAt)

        dao.setFavorite("a", true)
        assertTrue(dao.get("a")!!.isFavorite)

        dao.delete("a")
        assertNull(dao.get("a"))
        assertEquals(2, dao.all().size)
    }
}

package com.breakyuna.esjzone.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.breakyuna.esjzone.database.entity.Bookmark
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
    fun getAll(): List<Bookmark>

    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE chapter_url = :chapterUrl LIMIT 1")
    fun findByChapterUrl(chapterUrl: String): Bookmark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(bookmark: Bookmark)

    @Delete
    fun delete(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE chapter_url = :chapterUrl")
    fun deleteByChapterUrl(chapterUrl: String)
}

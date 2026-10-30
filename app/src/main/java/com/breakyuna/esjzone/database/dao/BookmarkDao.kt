package com.breakyuna.esjzone.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle
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
    fun insertRaw(bookmark: Bookmark)

    @Transaction
    fun insert(bookmark: Bookmark) {
        val name = validNovelTitle(bookmark.novelName, bookmark.novelId, bookmark.chapterUrl, bookmark.chapterName)
            .ifBlank { findByChapterUrl(bookmark.chapterUrl)?.let {
                validNovelTitle(it.novelName, it.novelId, it.chapterUrl, it.chapterName)
            }.orEmpty() }
        insertRaw(bookmark.copy(novelName = name))
    }

    @Query("SELECT * FROM bookmarks WHERE novel_id = :novelId OR " +
        "(:novelId != '' AND novel_id = '' AND chapter_url LIKE '%/forum/' || :novelId || '/%')")
    fun forNovel(novelId: String): List<Bookmark>

    @Query("UPDATE bookmarks SET novel_name = :name WHERE chapter_url = :chapterUrl AND novel_name = :previous")
    fun repairNovelName(chapterUrl: String, previous: String, name: String)

    @Delete
    fun delete(bookmark: Bookmark)

    @Delete
    fun deleteAll(bookmarks: List<Bookmark>)

    @Query("DELETE FROM bookmarks WHERE chapter_url = :chapterUrl")
    fun deleteByChapterUrl(chapterUrl: String)
}

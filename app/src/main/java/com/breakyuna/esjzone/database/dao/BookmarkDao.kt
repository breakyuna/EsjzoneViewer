package com.breakyuna.esjzone.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle
import com.breakyuna.esjzone.database.entity.Bookmark
import kotlinx.coroutines.flow.Flow

data class BookmarkCoverRef(
    @ColumnInfo(name = "novel_id") val novelId: String,
    @ColumnInfo(name = "chapter_url") val chapterUrl: String
)

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE trim(novel_name) = '' OR trim(novel_name) = novel_id OR trim(novel_name) = trim(chapter_name) OR novel_name LIKE '%/detail/%' OR novel_name LIKE '%/forum/%'")
    suspend fun titleRepairCandidates(): List<Bookmark>

    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
    fun getAll(): List<Bookmark>

    @Query("SELECT * FROM bookmarks ORDER BY created_at DESC")
    fun observeAll(): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks WHERE chapter_url = :chapterUrl ORDER BY created_at DESC LIMIT 1")
    fun findByChapterUrl(chapterUrl: String): Bookmark?

    @Query("SELECT * FROM bookmarks WHERE chapter_url = :chapterUrl ORDER BY created_at DESC")
    fun forChapter(chapterUrl: String): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE chapter_url LIKE '%' || :baseChapterKey || '%' ORDER BY created_at DESC")
    fun forChapterKeyRaw(baseChapterKey: String): List<Bookmark>

    fun forChapterKey(chapterKey: String): List<Bookmark> {
        val base = chapterKey.substringBefore('#').substringBefore('?')
        return if (base.isBlank()) emptyList() else forChapterKeyRaw(base)
    }

    @Query("SELECT DISTINCT novel_id, chapter_url FROM bookmarks")
    fun coverRefs(): List<BookmarkCoverRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRaw(bookmark: Bookmark)

    @Transaction
    fun insert(bookmark: Bookmark) {
        val name = validNovelTitle(bookmark.novelName, bookmark.novelId, bookmark.chapterUrl, bookmark.chapterName)
            .ifBlank { forChapter(bookmark.chapterUrl).firstNotNullOfOrNull {
                validNovelTitle(it.novelName, it.novelId, it.chapterUrl, it.chapterName).takeIf(String::isNotBlank)
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

}

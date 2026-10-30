package com.breakyuna.esjzone.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.breakyuna.esjzone.database.entity.ReadingStat
import kotlinx.coroutines.flow.Flow
import com.breakyuna.esjzone.novellibrary.novel.validNovelTitle

@Dao
interface ReadingStatDao {
    @Query("SELECT * FROM reading_stats ORDER BY date DESC")
    fun observeAll(): Flow<List<ReadingStat>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfMissing(stat: ReadingStat): Long

    @Query("UPDATE reading_stats SET duration_ms = duration_ms + :durationMs, book_name = CASE WHEN :bookName != '' THEN :bookName ELSE book_name END WHERE date = :date AND book_key = :bookKey")
    fun addDuration(date: String, bookKey: String, bookName: String, durationMs: Long)

    @Transaction
    fun add(stat: ReadingStat) {
        if (stat.durationMs <= 0L) return
        val name = validNovelTitle(stat.bookName, novelTitleIdForKey(stat.bookKey), stat.bookKey)
        if (insertIfMissing(stat.copy(bookName = name)) == -1L) {
            addDuration(stat.date, stat.bookKey, name, stat.durationMs)
        }
    }

    @Query("SELECT * FROM reading_stats")
    fun getAll(): List<ReadingStat>
    @Query("UPDATE reading_stats SET duration_ms = MAX(duration_ms, :duration), book_name = CASE WHEN :name != '' THEN :name ELSE book_name END WHERE date = :date AND book_key = :key")
    fun mergeDuration(date: String, key: String, name: String, duration: Long)
    @Transaction
    fun merge(stat: ReadingStat) {
        val name = validNovelTitle(stat.bookName, novelTitleIdForKey(stat.bookKey), stat.bookKey)
        if (insertIfMissing(stat.copy(bookName = name)) == -1L) mergeDuration(stat.date, stat.bookKey, name, stat.durationMs)
    }
    @Query("SELECT * FROM reading_stats WHERE book_key = :key ORDER BY date DESC")
    fun forBook(key: String): List<ReadingStat>

    @Query("UPDATE reading_stats SET book_name = :name WHERE book_key = :key AND book_name = :previous")
    fun repairBookName(key: String, previous: String, name: String)

    @Query("DELETE FROM reading_stats")
    fun deleteAll()
}

private fun novelTitleIdForKey(key: String): String =
    com.breakyuna.esjzone.novellibrary.novel.novelTitleId(novelUrl = key).ifBlank { key }

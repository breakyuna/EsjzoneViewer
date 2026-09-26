package com.breakyuna.esjzone.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.breakyuna.esjzone.database.entity.ReadingStat
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingStatDao {
    @Query("SELECT * FROM reading_stats ORDER BY date DESC")
    fun observeAll(): Flow<List<ReadingStat>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfMissing(stat: ReadingStat): Long

    @Query("UPDATE reading_stats SET duration_ms = duration_ms + :durationMs, book_name = :bookName WHERE date = :date AND book_key = :bookKey")
    fun addDuration(date: String, bookKey: String, bookName: String, durationMs: Long)

    @Transaction
    fun add(stat: ReadingStat) {
        if (stat.durationMs <= 0L) return
        if (insertIfMissing(stat) == -1L) {
            addDuration(stat.date, stat.bookKey, stat.bookName, stat.durationMs)
        }
    }

    @Query("DELETE FROM reading_stats")
    fun deleteAll()
}

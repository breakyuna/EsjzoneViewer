package com.breakyuna.esjzone.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface BookshelfDao {

    @Query(
        "SELECT * FROM bookshelf WHERE scope = :scope AND visible = 1 " +
            "ORDER BY added_at DESC"
    )
    fun observeVisible(scope: String): Flow<List<BookshelfEntry>>

    @Query("SELECT * FROM bookshelf WHERE scope = :scope ORDER BY added_at DESC")
    suspend fun getAll(scope: String): List<BookshelfEntry>

    @Query("SELECT * FROM bookshelf WHERE scope = :scope AND book_key = :bookKey LIMIT 1")
    fun observe(scope: String, bookKey: String): Flow<BookshelfEntry?>

    @Query("SELECT * FROM bookshelf WHERE scope = :scope AND book_key = :bookKey LIMIT 1")
    suspend fun find(scope: String, bookKey: String): BookshelfEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BookshelfEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<BookshelfEntry>)

    /** Persists a batch of removal intents atomically before one sync pass. */
    @Transaction
    suspend fun upsertRemovalIntents(entries: List<BookshelfEntry>) {
        upsertAll(entries)
    }

    /** Imports must never replace a concurrent local intent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: BookshelfEntry): Long

    @Query(
        "UPDATE bookshelf SET sync_state = :state, visible = :visible, " +
            "retry_count = :retryCount, last_error = :lastError " +
            "WHERE scope = :scope AND book_key = :bookKey AND operation_version = :version"
    )
    suspend fun updateStateIfVersion(
        scope: String,
        bookKey: String,
        version: Long,
        state: String,
        visible: Boolean,
        retryCount: Int = 0,
        lastError: String? = null
    ): Int

    @Query(
        "UPDATE bookshelf SET retry_count = retry_count + 1, last_error = :lastError " +
            "WHERE scope = :scope AND book_key = :bookKey AND operation_version = :version"
    )
    suspend fun markRetry(scope: String, bookKey: String, version: Long, lastError: String?): Int

    @Query(
        "UPDATE bookshelf SET title = CASE WHEN title = '' THEN :title ELSE title END, " +
            "author = CASE WHEN author = '' THEN :author ELSE author END, " +
            "cover_url = CASE WHEN cover_url = '' THEN :coverUrl ELSE cover_url END, " +
            "is_adult = CASE WHEN :isAdult = 1 THEN 1 ELSE is_adult END " +
            "WHERE scope = :scope AND book_key = :bookKey AND sync_state != 'PENDING_REMOVE'"
    )
    suspend fun supplementMetadata(
        scope: String,
        bookKey: String,
        title: String,
        author: String,
        coverUrl: String,
        isAdult: Boolean
    )

    @Query("DELETE FROM bookshelf WHERE scope = :scope AND book_key = :bookKey AND operation_version = :version")
    suspend fun deleteIfVersion(scope: String, bookKey: String, version: Long): Int
}

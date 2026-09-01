package com.breakyuna.esjzone.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Local-first shelf row.  A hidden PENDING_REMOVE row is a tombstone: it must
 * remain in the database until the remote toggle has been confirmed.
 */
@Entity(
    tableName = "bookshelf",
    primaryKeys = ["scope", "book_key"],
    indices = [Index(value = ["scope", "visible", "added_at"])]
)
data class BookshelfEntry(
    @ColumnInfo(name = "scope") val scope: String,
    @ColumnInfo(name = "book_key") val bookKey: String,
    @ColumnInfo(name = "novel_id") val novelId: String = "",
    @ColumnInfo(name = "url") val url: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String = "",
    @ColumnInfo(name = "cover_url") val coverUrl: String = "",
    @ColumnInfo(name = "is_adult", defaultValue = "0") val isAdult: Boolean = false,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sync_state") val syncState: String = BookshelfSyncState.SYNCED,
    @ColumnInfo(name = "visible") val visible: Boolean = true,
    @ColumnInfo(name = "retry_count") val retryCount: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
    /** Monotonically increases for each local intent, protecting against stale responses. */
    @ColumnInfo(name = "operation_version") val operationVersion: Long = 0L
)

object BookshelfSyncState {
    const val SYNCED = "SYNCED"
    const val PENDING_ADD = "PENDING_ADD"
    const val PENDING_REMOVE = "PENDING_REMOVE"
}

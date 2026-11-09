package com.breakyuna.esjzone.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A device-local reader bookmark. One row per chapter; [anchor] (when present)
 * pins the paragraph with source-text coordinates shared with local history.
 */
@Entity(tableName = "bookmarks")
data class Bookmark(
    /** Canonical chapter URL makes repeated taps idempotent. */
    @PrimaryKey
    @ColumnInfo(name = "chapter_url") val chapterUrl: String,
    @ColumnInfo(name = "novel_id") val novelId: String,
    @ColumnInfo(name = "novel_name") val novelName: String,
    @ColumnInfo(name = "chapter_name") val chapterName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /** Encoded ReaderAnchor; null for chapter-only bookmarks, which open at the chapter start. */
    @ColumnInfo(name = "content_anchor", defaultValue = "NULL") val anchor: String? = null,
    /** Short displayed-text excerpt captured when saved; null when unavailable. */
    @ColumnInfo(name = "excerpt", defaultValue = "NULL") val excerpt: String? = null
)

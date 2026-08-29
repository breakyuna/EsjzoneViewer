package com.breakyuna.esjzone.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** A device-local bookmark for a chapter in the reader. */
@Entity(tableName = "bookmarks")
data class Bookmark(
    /** Canonical chapter URL makes repeated taps idempotent. */
    @PrimaryKey
    @ColumnInfo(name = "chapter_url") val chapterUrl: String,
    @ColumnInfo(name = "novel_id") val novelId: String,
    @ColumnInfo(name = "novel_name") val novelName: String,
    @ColumnInfo(name = "chapter_name") val chapterName: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)

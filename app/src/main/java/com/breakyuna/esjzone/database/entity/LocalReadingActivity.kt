package com.breakyuna.esjzone.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single reader session stored only on this device.
 *
 * This table intentionally has no network counterpart: local reading
 * activity must never be uploaded to the ESJ account history.
 */
@Entity(
    tableName = "local_reading_history",
    indices = [Index(value = ["last_read_at"])]
)
data class LocalReadingActivity(
    @PrimaryKey
    @ColumnInfo(name = "activity_id") val activityId: String,
    @ColumnInfo(name = "novel_id") val novelId: String,
    @ColumnInfo(name = "novel_name") val novelName: String,
    @ColumnInfo(name = "novel_url") val novelUrl: String,
    @ColumnInfo(name = "chapter_url") val chapterUrl: String,
    @ColumnInfo(name = "chapter_name") val chapterName: String,
    @ColumnInfo(name = "chapter_index") val chapterIndex: Int,
    @ColumnInfo(name = "total_chapters") val totalChapters: Int,
    @ColumnInfo(name = "chapter_progress") val chapterProgress: Float,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "last_read_at") val lastReadAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long
)

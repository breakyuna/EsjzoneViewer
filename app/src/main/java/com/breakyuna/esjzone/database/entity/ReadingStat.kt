package com.breakyuna.esjzone.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** Device-only reading time, independent of the latest reading position. */
@Entity(
    tableName = "reading_stats",
    primaryKeys = ["date", "book_key"],
    indices = [Index(value = ["book_key"])]
)
data class ReadingStat(
    val date: String,
    @ColumnInfo(name = "book_key") val bookKey: String,
    @ColumnInfo(name = "book_name") val bookName: String,
    @ColumnInfo(name = "duration_ms") val durationMs: Long
)

package com.breakyuna.esjzone.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.google.gson.annotations.SerializedName

/** Device-only reading time, independent of the latest reading position. */
@Entity(
    tableName = "reading_stats",
    primaryKeys = ["date", "book_key"],
    indices = [Index(value = ["book_key"])]
)
// Local backups serialize this row with Gson. Alternates read archives exported by
// release builds before the keep rule, where R8 renamed the fields in declaration order.
data class ReadingStat(
    @SerializedName(value = "date", alternate = ["a"]) val date: String,
    @SerializedName(value = "bookKey", alternate = ["b"]) @ColumnInfo(name = "book_key") val bookKey: String,
    @SerializedName(value = "bookName", alternate = ["c"]) @ColumnInfo(name = "book_name") val bookName: String,
    @SerializedName(value = "durationMs", alternate = ["d"]) @ColumnInfo(name = "duration_ms") val durationMs: Long
)

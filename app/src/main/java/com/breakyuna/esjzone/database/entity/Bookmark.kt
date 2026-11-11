package com.breakyuna.esjzone.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.UUID

/**
 * A device-local reader bookmark. [anchor] (when present)
 * pins the paragraph with source-text coordinates shared with local history.
 *
 * Local backups serialize this row with Gson. Release 0.2.5 had no keep rule for it,
 * so R8 renamed the fields in declaration order; the alternates read those archives.
 */
@Entity(tableName = "bookmarks", indices = [Index(value = ["chapter_url"])])
data class Bookmark(
    @SerializedName(value = "chapterUrl", alternate = ["a"]) @ColumnInfo(name = "chapter_url") val chapterUrl: String,
    @SerializedName(value = "novelId", alternate = ["b"]) @ColumnInfo(name = "novel_id") val novelId: String,
    @SerializedName(value = "novelName", alternate = ["c"]) @ColumnInfo(name = "novel_name") val novelName: String,
    @SerializedName(value = "chapterName", alternate = ["d"]) @ColumnInfo(name = "chapter_name") val chapterName: String,
    @SerializedName(value = "createdAt", alternate = ["e"]) @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /** Encoded ReaderAnchor; null for chapter-only bookmarks, which open at the chapter start. */
    @SerializedName(value = "anchor", alternate = ["f"]) @ColumnInfo(name = "content_anchor", defaultValue = "NULL") val anchor: String? = null,
    /** Short displayed-text excerpt captured when saved; null when unavailable. */
    @SerializedName(value = "excerpt", alternate = ["g"]) @ColumnInfo(name = "excerpt", defaultValue = "NULL") val excerpt: String? = null,
    @SerializedName("id") @PrimaryKey @ColumnInfo(name = "bookmark_id") val id: String = UUID.randomUUID().toString()
)

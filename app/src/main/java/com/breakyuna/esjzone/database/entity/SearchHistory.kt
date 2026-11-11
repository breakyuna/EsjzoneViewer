package com.breakyuna.esjzone.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity
// Local backups serialize this row with Gson. Alternates read archives exported by
// release builds before the keep rule, where R8 renamed the fields in declaration order.
data class SearchHistory(
    @SerializedName(value = "index", alternate = ["a"]) @PrimaryKey(autoGenerate = true) val index: Int = 0,
    @SerializedName(value = "keyword", alternate = ["b"]) @ColumnInfo(name = "keyword") val keyword: String,
    @SerializedName(value = "time", alternate = ["c"]) @ColumnInfo(name = "time") var time: String
)

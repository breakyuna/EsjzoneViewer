package com.breakyuna.esjzone.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalReadingActivityDao {

    @Query("SELECT * FROM local_reading_history ORDER BY last_read_at DESC, started_at DESC")
    fun observeAll(): Flow<List<LocalReadingActivity>>

    @Query(
        "SELECT * FROM local_reading_history " +
            "ORDER BY last_read_at DESC, started_at DESC LIMIT 1"
    )
    fun getLatest(): LocalReadingActivity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(activity: LocalReadingActivity)

    @Query(
        "DELETE FROM local_reading_history WHERE activity_id != :activityId AND (" +
            "(:novelId != '' AND novel_id = :novelId) OR " +
            "(:novelUrl != '' AND novel_url = :novelUrl))"
    )
    fun deleteOtherRecordsForNovel(activityId: String, novelId: String, novelUrl: String)

    /** Keeps one mutable latest-position row for each novel. */
    @Transaction
    fun upsertLatest(activity: LocalReadingActivity) {
        deleteOtherRecordsForNovel(
            activityId = activity.activityId,
            novelId = activity.novelId,
            novelUrl = activity.novelUrl
        )
        upsert(activity)
    }

    @Query(
        "UPDATE local_reading_history SET novel_cover_url = :coverUrl " +
            "WHERE activity_id = :activityId"
    )
    fun updateCover(activityId: String, coverUrl: String)

    @Query("DELETE FROM local_reading_history WHERE activity_id = :activityId")
    fun deleteById(activityId: String)

    @Query("DELETE FROM local_reading_history")
    fun deleteAll()
}

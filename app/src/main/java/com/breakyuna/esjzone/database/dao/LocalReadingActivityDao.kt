package com.breakyuna.esjzone.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
        "UPDATE local_reading_history SET novel_cover_url = :coverUrl " +
            "WHERE activity_id = :activityId"
    )
    fun updateCover(activityId: String, coverUrl: String)

    @Query("DELETE FROM local_reading_history WHERE activity_id = :activityId")
    fun deleteById(activityId: String)

    @Query("DELETE FROM local_reading_history")
    fun deleteAll()
}

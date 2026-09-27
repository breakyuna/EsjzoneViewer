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

    @Query("SELECT * FROM local_reading_history ORDER BY last_read_at DESC, started_at DESC")
    suspend fun getAll(): List<LocalReadingActivity>

    @Query(
        "SELECT * FROM local_reading_history " +
            "ORDER BY last_read_at DESC, started_at DESC LIMIT 1"
    )
    fun getLatest(): LocalReadingActivity?

    @Query(
        "SELECT * FROM local_reading_history WHERE novel_id = :novelId " +
            "ORDER BY last_read_at DESC, started_at DESC LIMIT 1"
    )
    fun getLatestForNovel(novelId: String): LocalReadingActivity?

    @Query(
        "SELECT * FROM local_reading_history WHERE " +
            "(:novelId != '' AND novel_id = :novelId) OR " +
            "(:novelUrl != '' AND novel_url = :novelUrl) " +
            "ORDER BY last_read_at DESC LIMIT 1"
    )
    fun getLatestForIdentity(novelId: String, novelUrl: String): LocalReadingActivity?

    @Query(
        "SELECT * FROM local_reading_history WHERE " +
            "(:novelId != '' AND novel_id = :novelId) OR " +
            "(:novelUrl != '' AND novel_url = :novelUrl) " +
            "ORDER BY last_read_at DESC LIMIT 1"
    )
    fun observeLatestForIdentity(novelId: String, novelUrl: String): Flow<LocalReadingActivity?>

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
        val previous = getLatestForIdentity(activity.novelId, activity.novelUrl)
        val validName = activity.novelName.trim().takeIf {
            it.isNotBlank() && it != activity.novelId && it != activity.chapterName
        }
        deleteOtherRecordsForNovel(
            activityId = activity.activityId,
            novelId = activity.novelId,
            novelUrl = activity.novelUrl
        )
        upsert(activity.copy(
            novelName = validName ?: previous?.novelName?.takeIf(String::isNotBlank)
                ?: activity.novelName,
            novelCoverUrl = activity.novelCoverUrl.ifBlank {
                previous?.novelCoverUrl.orEmpty()
            }
        ))
    }

    @Query(
        "UPDATE local_reading_history SET novel_cover_url = :coverUrl " +
            "WHERE activity_id = :activityId"
    )
    fun updateCover(activityId: String, coverUrl: String)

    @Query("UPDATE local_reading_history SET novel_name = :name WHERE activity_id = :activityId")
    fun updateName(activityId: String, name: String)

    /**
     * Marks an existing local reading session as the most recently opened
     * without replacing its restored chapter or progress with an initial
     * reader position.
     */
    @Query(
        "UPDATE local_reading_history SET last_read_at = :lastReadAt WHERE " +
            "(:novelId != '' AND novel_id = :novelId) OR " +
            "(:novelUrl != '' AND novel_url = :novelUrl)"
    )
    fun touchLatestForIdentity(novelId: String, novelUrl: String, lastReadAt: Long): Int

    @Query("DELETE FROM local_reading_history WHERE activity_id = :activityId")
    fun deleteById(activityId: String)

    @Query("DELETE FROM local_reading_history WHERE activity_id IN (:activityIds)")
    fun deleteByIds(activityIds: List<String>): Int

    @Query("DELETE FROM local_reading_history")
    fun deleteAll()
}

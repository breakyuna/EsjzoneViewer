package com.breakyuna.esjzone.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.breakyuna.esjzone.database.dao.BookmarkDao
import com.breakyuna.esjzone.database.dao.CacheDao
import com.breakyuna.esjzone.database.dao.LocalReadingActivityDao
import com.breakyuna.esjzone.database.dao.SearchHistoryDao
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.Cache
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.database.entity.SearchHistory

@Database(
    entities = [Cache::class, SearchHistory::class, Bookmark::class, LocalReadingActivity::class],
    version = 5,
    exportSchema = false
)
abstract class GeneralDatabase : RoomDatabase() {

    abstract fun cacheDao(): CacheDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun localReadingActivityDao(): LocalReadingActivityDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bookmarks (
                        chapter_url TEXT NOT NULL PRIMARY KEY,
                        novel_id TEXT NOT NULL,
                        novel_name TEXT NOT NULL,
                        chapter_name TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS local_reading_history (
                        activity_id TEXT NOT NULL PRIMARY KEY,
                        novel_id TEXT NOT NULL,
                        novel_name TEXT NOT NULL,
                        novel_url TEXT NOT NULL,
                        chapter_url TEXT NOT NULL,
                        chapter_name TEXT NOT NULL,
                        chapter_index INTEGER NOT NULL,
                        total_chapters INTEGER NOT NULL,
                        chapter_progress REAL NOT NULL,
                        started_at INTEGER NOT NULL,
                        last_read_at INTEGER NOT NULL,
                        duration_ms INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_local_reading_history_last_read_at
                    ON local_reading_history(last_read_at)
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    ALTER TABLE local_reading_history
                    ADD COLUMN novel_cover_url TEXT NOT NULL DEFAULT ''
                    """.trimIndent()
                )
            }
        }

        /** Removes older per-session duplicates while preserving the latest position. */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    DELETE FROM local_reading_history
                    WHERE EXISTS (
                        SELECT 1
                        FROM local_reading_history AS newer
                        WHERE (
                            (local_reading_history.novel_id != '' AND
                                newer.novel_id = local_reading_history.novel_id)
                            OR
                            (local_reading_history.novel_url != '' AND
                                newer.novel_url = local_reading_history.novel_url)
                        )
                        AND (
                            newer.last_read_at > local_reading_history.last_read_at
                            OR (
                                newer.last_read_at = local_reading_history.last_read_at
                                AND newer.started_at > local_reading_history.started_at
                            )
                            OR (
                                newer.last_read_at = local_reading_history.last_read_at
                                AND newer.started_at = local_reading_history.started_at
                                AND newer.rowid > local_reading_history.rowid
                            )
                        )
                    )
                    """.trimIndent()
                )
            }
        }
    }

}

package com.breakyuna.esjzone.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.breakyuna.esjzone.database.dao.BookmarkDao
import com.breakyuna.esjzone.database.dao.CacheDao
import com.breakyuna.esjzone.database.dao.SearchHistoryDao
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.Cache
import com.breakyuna.esjzone.database.entity.SearchHistory

@Database(
    entities = [Cache::class, SearchHistory::class, Bookmark::class],
    version = 2,
    exportSchema = false
)
abstract class GeneralDatabase : RoomDatabase() {

    abstract fun cacheDao(): CacheDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun bookmarkDao(): BookmarkDao

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
    }

}

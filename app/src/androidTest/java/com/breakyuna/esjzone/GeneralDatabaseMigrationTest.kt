package com.breakyuna.esjzone

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.database.GeneralDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the frozen historical fixtures through Room's real open/upgrade path.
 *
 * The fixtures are deliberately created as versioned SQLite databases instead of
 * calling Migration.migrate directly. This keeps the test useful before the
 * first schema export is checked in, while Room still validates the complete
 * version-6 schema after every migration path.
 */
@RunWith(AndroidJUnit4::class)
class GeneralDatabaseMigrationTest {

    @Test
    fun migrateVersion1To6PreservesLegacyRowsAndCreatesAllCurrentTables() {
        val databaseName = "general-migration-v1-${System.nanoTime()}"
        createFixtureDatabase(databaseName, version = 1) { database ->
            database.execSQL(
                "CREATE TABLE cache (" +
                    "`index` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "cache_key TEXT NOT NULL, cache_value TEXT NOT NULL)"
            )
            database.execSQL(
                "CREATE TABLE searchhistory (" +
                    "`index` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "keyword TEXT NOT NULL, time TEXT NOT NULL)"
            )
            database.execSQL(
                "INSERT INTO cache(`index`, cache_key, cache_value) " +
                    "VALUES (1, 'fixture.domain', 'https://example.test')"
            )
            database.execSQL(
                "INSERT INTO searchhistory(`index`, keyword, time) " +
                    "VALUES (1, 'fixture query', '2026-09-07T00:00:00Z')"
            )
        }

        val database = openWithMigrations(
            databaseName,
            GeneralDatabase.MIGRATION_1_2,
            GeneralDatabase.MIGRATION_2_3,
            GeneralDatabase.MIGRATION_3_4,
            GeneralDatabase.MIGRATION_4_5,
            GeneralDatabase.MIGRATION_5_6
        )
        try {
            val sqlite = database.openHelper.writableDatabase
            sqlite.query(
                "SELECT cache_value FROM cache WHERE cache_key = ?",
                arrayOf("fixture.domain")
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("https://example.test", cursor.getString(0))
            }
            sqlite.query(
                "SELECT keyword FROM searchhistory WHERE `index` = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("fixture query", cursor.getString(0))
            }
            assertCurrentTablesExist(sqlite)
        } finally {
            database.close()
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrateVersion4To6KeepsLatestReadingRowAndEnforcesBookshelfKey() {
        val databaseName = "general-migration-v4-${System.nanoTime()}"
        createFixtureDatabase(databaseName, version = 4) { database ->
            createVersion4Schema(database)
            insertReadingRow(database, "old", lastReadAt = 10, startedAt = 1)
            insertReadingRow(database, "new", lastReadAt = 20, startedAt = 2)
        }

        val database = openWithMigrations(
            databaseName,
            GeneralDatabase.MIGRATION_4_5,
            GeneralDatabase.MIGRATION_5_6
        )
        try {
            val sqlite = database.openHelper.writableDatabase
            sqlite.query(
                "SELECT activity_id, novel_cover_url FROM local_reading_history"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("new", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertTrue(!cursor.moveToNext())
            }

            val bookshelfRow = "(" +
                "'domain:example.test', 'novel-1', '1', " +
                "'https://example.test/novel/1', 'Migration fixture', 'author', '', " +
                "0, 123, 'SYNCED', 1, 0, NULL, 1)"
            sqlite.execSQL(
                "INSERT INTO bookshelf(" +
                    "scope, book_key, novel_id, url, title, author, cover_url, is_adult, " +
                    "added_at, sync_state, visible, retry_count, last_error, operation_version) " +
                    "VALUES $bookshelfRow"
            )
            sqlite.query(
                "SELECT title FROM bookshelf WHERE scope = ? AND book_key = ?",
                arrayOf("domain:example.test", "novel-1")
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Migration fixture", cursor.getString(0))
            }

            var duplicateRejected = false
            try {
                sqlite.execSQL(
                    "INSERT INTO bookshelf(" +
                        "scope, book_key, novel_id, url, title, author, cover_url, is_adult, " +
                        "added_at, sync_state, visible, retry_count, last_error, operation_version) " +
                        "VALUES $bookshelfRow"
                )
            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                duplicateRejected = true
            }
            assertTrue("composite primary key must reject duplicate shelf rows", duplicateRejected)
        } finally {
            database.close()
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .deleteDatabase(databaseName)
        }
    }

    private fun createFixtureDatabase(
        databaseName: String,
        version: Int,
        createSchemaAndSeed: (SupportSQLiteDatabase) -> Unit
    ) {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        ).name(databaseName).callback(object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(database: SupportSQLiteDatabase) {
                createSchemaAndSeed(database)
            }

            override fun onUpgrade(
                database: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) = error("historical fixture unexpectedly upgraded: $oldVersion -> $newVersion")
        }).build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        try {
            helper.writableDatabase
        } finally {
            helper.close()
        }
    }

    private fun openWithMigrations(
        databaseName: String,
        vararg migrations: androidx.room.migration.Migration
    ): GeneralDatabase {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        return Room.databaseBuilder(context, GeneralDatabase::class.java, databaseName)
            .addMigrations(*migrations)
            .build()
    }

    private fun createVersion4Schema(database: SupportSQLiteDatabase) {
        database.execSQL(
            "CREATE TABLE cache (" +
                "`index` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "cache_key TEXT NOT NULL, cache_value TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE searchhistory (" +
                "`index` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "keyword TEXT NOT NULL, time TEXT NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE bookmarks (" +
                "chapter_url TEXT NOT NULL PRIMARY KEY, novel_id TEXT NOT NULL, " +
                "novel_name TEXT NOT NULL, chapter_name TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE TABLE local_reading_history (" +
                "activity_id TEXT NOT NULL PRIMARY KEY, novel_id TEXT NOT NULL, " +
                "novel_name TEXT NOT NULL, novel_url TEXT NOT NULL, chapter_url TEXT NOT NULL, " +
                "chapter_name TEXT NOT NULL, chapter_index INTEGER NOT NULL, " +
                "total_chapters INTEGER NOT NULL, chapter_progress REAL NOT NULL, " +
                "started_at INTEGER NOT NULL, last_read_at INTEGER NOT NULL, " +
                "duration_ms INTEGER NOT NULL, novel_cover_url TEXT NOT NULL DEFAULT '')"
        )
        database.execSQL(
            "CREATE INDEX index_local_reading_history_last_read_at " +
                "ON local_reading_history(last_read_at)"
        )
    }

    private fun insertReadingRow(
        database: SupportSQLiteDatabase,
        activityId: String,
        lastReadAt: Long,
        startedAt: Long
    ) {
        database.execSQL(
            "INSERT INTO local_reading_history(" +
                "activity_id, novel_id, novel_name, novel_url, chapter_url, chapter_name, " +
                "chapter_index, total_chapters, chapter_progress, started_at, last_read_at, " +
                "duration_ms, novel_cover_url) VALUES (?, '1', 'Book', " +
                "'https://example.test/novel/1', ?, 'Chapter', 1, 10, 0.1, ?, ?, 100, '')",
            arrayOf<Any>(
                activityId,
                "https://example.test/chapter/$activityId",
                startedAt,
                lastReadAt
            )
        )
    }

    private fun assertCurrentTablesExist(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN " +
                "('cache', 'searchhistory', 'bookmarks', 'local_reading_history', 'bookshelf')"
        ).use { cursor ->
            var count = 0
            while (cursor.moveToNext()) count++
            assertEquals(5, count)
        }
    }
}

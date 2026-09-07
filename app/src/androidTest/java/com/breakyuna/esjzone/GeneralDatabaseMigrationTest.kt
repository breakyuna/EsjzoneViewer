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
            createVersion1Schema(database)
            insertBaseRows(database)
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
            assertBaseRowsPreserved(sqlite)
            assertCurrentTablesExist(sqlite)
        } finally {
            database.close()
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrateVersion2To6PreservesBookmarksAndBaseRows() {
        val databaseName = "general-migration-v2-${System.nanoTime()}"
        createFixtureDatabase(databaseName, version = 2) { database ->
            createVersion2Schema(database)
            insertBaseRows(database)
            insertBookmark(database)
        }

        val database = openWithMigrations(
            databaseName,
            GeneralDatabase.MIGRATION_2_3,
            GeneralDatabase.MIGRATION_3_4,
            GeneralDatabase.MIGRATION_4_5,
            GeneralDatabase.MIGRATION_5_6
        )
        try {
            val sqlite = database.openHelper.writableDatabase
            assertBaseRowsPreserved(sqlite)
            assertBookmarkPreserved(sqlite)
            assertCurrentTablesExist(sqlite)
        } finally {
            database.close()
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrateVersion3To6PreservesRowsAndKeepsLatestReadingPosition() {
        val databaseName = "general-migration-v3-${System.nanoTime()}"
        createFixtureDatabase(databaseName, version = 3) { database ->
            createVersion3Schema(database)
            insertBaseRows(database)
            insertBookmark(database)
            insertVersion3ReadingRow(database, "old", lastReadAt = 10, startedAt = 1)
            insertVersion3ReadingRow(database, "new", lastReadAt = 20, startedAt = 2)
        }

        val database = openWithMigrations(
            databaseName,
            GeneralDatabase.MIGRATION_3_4,
            GeneralDatabase.MIGRATION_4_5,
            GeneralDatabase.MIGRATION_5_6
        )
        try {
            val sqlite = database.openHelper.writableDatabase
            assertBaseRowsPreserved(sqlite)
            assertBookmarkPreserved(sqlite)
            assertLatestReadingRow(sqlite)
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
            insertBaseRows(database)
            insertBookmark(database)
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
            assertBaseRowsPreserved(sqlite)
            assertBookmarkPreserved(sqlite)
            assertLatestReadingRow(sqlite)
            assertCurrentTablesExist(sqlite)

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

            sqlite.execSQL(
                "INSERT INTO bookshelf(" +
                    "scope, book_key, novel_id, url, title, author, cover_url, is_adult, " +
                    "added_at, sync_state, visible, retry_count, last_error, operation_version) " +
                    "VALUES ('domain:example.test', 'tombstone', '2', " +
                    "'https://example.test/novel/2', 'Removed locally', '', '', 0, 124, " +
                    "'PENDING_REMOVE', 0, 3, 'network unavailable', 8)"
            )
            sqlite.query(
                "SELECT sync_state, visible, retry_count, last_error, operation_version " +
                    "FROM bookshelf WHERE scope = ? AND book_key = ?",
                arrayOf("domain:example.test", "tombstone")
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("PENDING_REMOVE", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
                assertEquals(3, cursor.getInt(2))
                assertEquals("network unavailable", cursor.getString(3))
                assertEquals(8L, cursor.getLong(4))
            }
        } finally {
            database.close()
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrateVersion4To6UsesStableReadingHistoryTieBreakers() {
        val databaseName = "general-migration-v4-ties-${System.nanoTime()}"
        createFixtureDatabase(databaseName, version = 4) { database ->
            createVersion4Schema(database)
            insertReadingRow(
                database, "started-old", lastReadAt = 30, startedAt = 1,
                novelId = "tie-start", novelUrl = "https://example.test/novel/tie-start"
            )
            insertReadingRow(
                database, "started-new", lastReadAt = 30, startedAt = 2,
                novelId = "tie-start", novelUrl = "https://example.test/novel/tie-start"
            )
            insertReadingRow(
                database, "rowid-old", lastReadAt = 40, startedAt = 4,
                novelId = "tie-rowid", novelUrl = "https://example.test/novel/tie-rowid"
            )
            insertReadingRow(
                database, "rowid-new", lastReadAt = 40, startedAt = 4,
                novelId = "tie-rowid", novelUrl = "https://example.test/novel/tie-rowid"
            )
            insertReadingRow(
                database, "url-old", lastReadAt = 50, startedAt = 1,
                novelId = "", novelUrl = "https://example.test/novel/url-only"
            )
            insertReadingRow(
                database, "url-new", lastReadAt = 51, startedAt = 1,
                novelId = "", novelUrl = "https://example.test/novel/url-only"
            )
        }

        val database = openWithMigrations(
            databaseName,
            GeneralDatabase.MIGRATION_4_5,
            GeneralDatabase.MIGRATION_5_6
        )
        try {
            val sqlite = database.openHelper.writableDatabase
            sqlite.query(
                "SELECT activity_id FROM local_reading_history ORDER BY activity_id"
            ).use { cursor ->
                val winners = buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
                assertEquals(listOf("rowid-new", "started-new", "url-new"), winners)
            }
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
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(databaseName)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseName).callback(object : SupportSQLiteOpenHelper.Callback(version) {
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
        createVersion3Schema(database)
        database.execSQL(
            "ALTER TABLE local_reading_history " +
                "ADD COLUMN novel_cover_url TEXT NOT NULL DEFAULT ''"
        )
    }

    private fun createVersion1Schema(database: SupportSQLiteDatabase) {
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
    }

    private fun createVersion2Schema(database: SupportSQLiteDatabase) {
        createVersion1Schema(database)
        database.execSQL(
            "CREATE TABLE bookmarks (" +
                "chapter_url TEXT NOT NULL PRIMARY KEY, novel_id TEXT NOT NULL, " +
                "novel_name TEXT NOT NULL, chapter_name TEXT NOT NULL, created_at INTEGER NOT NULL)"
        )
    }

    private fun createVersion3Schema(database: SupportSQLiteDatabase) {
        createVersion2Schema(database)
        database.execSQL(
            "CREATE TABLE local_reading_history (" +
                "activity_id TEXT NOT NULL PRIMARY KEY, novel_id TEXT NOT NULL, " +
                "novel_name TEXT NOT NULL, novel_url TEXT NOT NULL, chapter_url TEXT NOT NULL, " +
                "chapter_name TEXT NOT NULL, chapter_index INTEGER NOT NULL, " +
                "total_chapters INTEGER NOT NULL, chapter_progress REAL NOT NULL, " +
                "started_at INTEGER NOT NULL, last_read_at INTEGER NOT NULL, " +
                "duration_ms INTEGER NOT NULL)"
        )
        database.execSQL(
            "CREATE INDEX index_local_reading_history_last_read_at " +
                "ON local_reading_history(last_read_at)"
        )
    }

    private fun insertBaseRows(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO cache(`index`, cache_key, cache_value) " +
                "VALUES (1, 'fixture.domain', 'https://example.test')"
        )
        database.execSQL(
            "INSERT INTO searchhistory(`index`, keyword, time) " +
                "VALUES (1, 'fixture query', '2026-09-07T00:00:00Z')"
        )
    }

    private fun insertBookmark(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO bookmarks(chapter_url, novel_id, novel_name, chapter_name, created_at) " +
                "VALUES ('https://example.test/chapter/bookmark', '1', 'Book', 'Bookmark', 123)"
        )
    }

    private fun insertReadingRow(
        database: SupportSQLiteDatabase,
        activityId: String,
        lastReadAt: Long,
        startedAt: Long,
        novelId: String = "1",
        novelUrl: String = "https://example.test/novel/1"
    ) {
        database.execSQL(
            "INSERT INTO local_reading_history(" +
                "activity_id, novel_id, novel_name, novel_url, chapter_url, chapter_name, " +
                "chapter_index, total_chapters, chapter_progress, started_at, last_read_at, " +
                "duration_ms, novel_cover_url) VALUES (?, ?, 'Book', ?, ?, " +
                "'Chapter', 1, 10, 0.1, ?, ?, 100, '')",
            arrayOf<Any>(
                activityId,
                novelId,
                novelUrl,
                "$novelUrl/chapter/$activityId",
                startedAt,
                lastReadAt
            )
        )
    }

    private fun insertVersion3ReadingRow(
        database: SupportSQLiteDatabase,
        activityId: String,
        lastReadAt: Long,
        startedAt: Long
    ) {
        database.execSQL(
            "INSERT INTO local_reading_history(" +
                "activity_id, novel_id, novel_name, novel_url, chapter_url, chapter_name, " +
                "chapter_index, total_chapters, chapter_progress, started_at, last_read_at, " +
                "duration_ms) VALUES (?, '1', 'Book', 'https://example.test/novel/1', ?, " +
                "'Chapter', 1, 10, 0.1, ?, ?, 100)",
            arrayOf<Any>(
                activityId,
                "https://example.test/chapter/$activityId",
                startedAt,
                lastReadAt
            )
        )
    }

    private fun assertBaseRowsPreserved(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT cache_key, cache_value FROM cache WHERE `index` = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("fixture.domain", cursor.getString(0))
            assertEquals("https://example.test", cursor.getString(1))
            assertTrue(!cursor.moveToNext())
        }
        database.query(
            "SELECT keyword, time FROM searchhistory WHERE `index` = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("fixture query", cursor.getString(0))
            assertEquals("2026-09-07T00:00:00Z", cursor.getString(1))
            assertTrue(!cursor.moveToNext())
        }
    }

    private fun assertBookmarkPreserved(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT chapter_name, created_at FROM bookmarks WHERE chapter_url = ?",
            arrayOf("https://example.test/chapter/bookmark")
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Bookmark", cursor.getString(0))
            assertEquals(123L, cursor.getLong(1))
        }
    }

    private fun assertLatestReadingRow(database: SupportSQLiteDatabase) {
        database.query(
            "SELECT activity_id, novel_cover_url FROM local_reading_history"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("new", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertTrue(!cursor.moveToNext())
        }
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

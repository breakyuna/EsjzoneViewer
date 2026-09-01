package com.breakyuna.esjzone

import android.content.ContentValues
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.breakyuna.esjzone.database.GeneralDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Verifies the version 1 to 6 migration chain against a real device SQLite engine. */
@RunWith(AndroidJUnit4::class)
class GeneralDatabaseMigrationTest {

    @Test
    fun migrationChainCreatesBookshelfWithExpectedSchemaAndConstraints() {
        val migrations = listOf(
            GeneralDatabase.MIGRATION_1_2,
            GeneralDatabase.MIGRATION_2_3,
            GeneralDatabase.MIGRATION_3_4,
            GeneralDatabase.MIGRATION_4_5,
            GeneralDatabase.MIGRATION_5_6
        )
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(
            ApplicationProvider.getApplicationContext()
        ).name(null).callback(object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(database: SupportSQLiteDatabase) {
                migrations[0].migrate(database)
                migrations[1].migrate(database)
                database.execSQL(
                    "INSERT INTO bookmarks(chapter_url, novel_id, novel_name, chapter_name, created_at) " +
                        "VALUES ('https://example.test/chapter/1', '1', 'Book', 'Chapter', 10)"
                )
                database.execSQL(
                    "INSERT INTO local_reading_history(" +
                        "activity_id, novel_id, novel_name, novel_url, chapter_url, chapter_name, " +
                        "chapter_index, total_chapters, chapter_progress, started_at, last_read_at, duration_ms" +
                        ") VALUES ('old', '1', 'Book', 'https://example.test/novel/1', " +
                        "'https://example.test/chapter/1', 'Old', 1, 10, 0.1, 1, 10, 100)"
                )
                database.execSQL(
                    "INSERT INTO local_reading_history(" +
                        "activity_id, novel_id, novel_name, novel_url, chapter_url, chapter_name, " +
                        "chapter_index, total_chapters, chapter_progress, started_at, last_read_at, duration_ms" +
                        ") VALUES ('new', '1', 'Book', 'https://example.test/novel/1', " +
                        "'https://example.test/chapter/2', 'New', 2, 10, 0.2, 2, 20, 200)"
                )
                migrations.drop(0).drop(1).forEach { it.migrate(database) }
            }

            override fun onUpgrade(
                database: SupportSQLiteDatabase,
                oldVersion: Int,
                newVersion: Int
            ) {
                migrations.filter { it.startVersion >= oldVersion && it.endVersion <= newVersion }
                    .forEach { it.migrate(database) }
            }
        }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(configuration)

        try {
            val database = helper.writableDatabase
            database.query("SELECT chapter_name FROM bookmarks WHERE chapter_url = ?", arrayOf("https://example.test/chapter/1")).use {
                assertTrue(it.moveToFirst())
                assertEquals("Chapter", it.getString(0))
            }
            database.query("SELECT activity_id, novel_cover_url FROM local_reading_history").use {
                assertTrue(it.moveToFirst())
                assertEquals("new", it.getString(0))
                assertEquals("", it.getString(1))
                assertTrue(!it.moveToNext())
            }
            val values = ContentValues().apply {
                put("scope", "domain:example.test")
                put("book_key", "novel-1")
                put("novel_id", "1")
                put("url", "https://example.test/novel/1")
                put("title", "Migration fixture")
                put("author", "author")
                put("cover_url", "")
                put("is_adult", 0)
                put("added_at", 123L)
                put("sync_state", "SYNCED")
                put("visible", 1)
                put("retry_count", 0)
                putNull("last_error")
                put("operation_version", 1L)
            }
            assertEquals(1L, database.insert("bookshelf", 0, values))

            database.query("SELECT title FROM bookshelf WHERE scope = ? AND book_key = ?", arrayOf("domain:example.test", "novel-1")).use {
                assertTrue(it.moveToFirst())
                assertEquals("Migration fixture", it.getString(0))
            }

            database.query("PRAGMA index_list('bookshelf')").use { cursor ->
                var foundIndex = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) ==
                        "index_bookshelf_scope_visible_added_at"
                    ) {
                        foundIndex = true
                    }
                }
                assertTrue(foundIndex)
            }

            var duplicateRejected = false
            try {
                database.insert("bookshelf", 0, values)
            } catch (_: android.database.sqlite.SQLiteConstraintException) {
                duplicateRejected = true
            }
            assertTrue("composite primary key must reject duplicate shelf rows", duplicateRejected)
        } finally {
            helper.close()
        }
    }
}

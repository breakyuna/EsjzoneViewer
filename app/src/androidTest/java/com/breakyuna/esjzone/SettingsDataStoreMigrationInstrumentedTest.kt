package com.breakyuna.esjzone

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.data.settings.SettingsDataStore
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.Cache
import com.breakyuna.esjzone.ui.designsystem.AppThemeVariant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies settings migration against Room's real generated DAO implementation. */
@RunWith(AndroidJUnit4::class)
class SettingsDataStoreMigrationInstrumentedTest {

    @Test
    fun migration_copiesRowsAndLeavesSessionData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            database.cacheDao().insertAll(
                Cache(key = "show_adult", value = "false"),
                Cache(key = "theme", value = AppThemeVariant.LAVENDER.name),
                Cache(key = "domain", value = "www.esjzone.one"),
                Cache(key = "language", value = "en"),
                Cache(key = "reader_auto_save", value = "true"),
                Cache(key = "session_cookie", value = "fixture-session")
            )
            val settings = SettingsDataStore(
                context,
                scope,
                "instrument-settings-${System.nanoTime()}.preferences_pb"
            )

            settings.migrateFromLegacy(database)
            assertEquals(false, settings.adult.first { !it })
            assertEquals(AppThemeVariant.LAVENDER, settings.theme.first { it == AppThemeVariant.LAVENDER })
            assertEquals("www.esjzone.one", settings.domain.first { it == "www.esjzone.one" })
            assertTrue(settings.readerAutoSave.first { it })
            assertNull(database.cacheDao().findByKey("show_adult"))
            assertNull(database.cacheDao().findByKey("theme"))
            assertEquals("fixture-session", database.cacheDao().findByKey("session_cookie")?.value)

            // The durable marker makes retries idempotent even if legacy rows are reintroduced.
            database.cacheDao().insertAll(Cache(key = "theme", value = AppThemeVariant.MINT.name))
            settings.migrateFromLegacy(database)
            assertEquals(AppThemeVariant.LAVENDER, settings.theme.first())
            assertEquals(AppThemeVariant.MINT.name, database.cacheDao().findByKey("theme")?.value)
        } finally {
            scope.cancel()
            database.close()
        }
    }

    @Test
    fun migration_recoversCorruptLegacyValuesToDefaults() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, GeneralDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            database.cacheDao().insertAll(
                Cache(key = "show_adult", value = "not-a-boolean"),
                Cache(key = "adult", value = "also-not-a-boolean"),
                Cache(key = "theme", value = "removed-theme"),
                Cache(key = "domain", value = "https://untrusted.example"),
                Cache(key = "language", value = "fr"),
                Cache(key = "reader_auto_save", value = "NaN")
            )
            val settings = SettingsDataStore(
                context,
                scope,
                "instrument-settings-corrupt-${System.nanoTime()}.preferences_pb"
            )
            settings.migrateFromLegacy(database)
            assertTrue(settings.adult.first())
            assertEquals(AppThemeVariant.DEFAULT, settings.theme.first())
            assertEquals("www.esjzone.cc", settings.domain.first())
            assertEquals(AppLanguage.SYSTEM, settings.language.first())
            assertTrue(settings.readerAutoSave.first())
        } finally {
            scope.cancel()
            database.close()
        }
    }
}

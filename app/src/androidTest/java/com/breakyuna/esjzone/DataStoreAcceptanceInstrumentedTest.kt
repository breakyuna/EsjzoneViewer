package com.breakyuna.esjzone

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import com.breakyuna.esjzone.data.settings.ReaderSettingsDataStore
import com.breakyuna.esjzone.data.settings.SettingsDataStore
import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderFont
import com.breakyuna.esjzone.ui.reader.ReaderImageBackground
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import com.breakyuna.esjzone.ui.reader.ReaderTool
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance coverage for the preference boundaries used during startup. */
class DataStoreAcceptanceInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun readerFontsAndIndependentGesturesPersistAndReset() = runBlocking {
        val scope = newScope()
        try {
            val reader = ReaderSettingsDataStore(context, scope, fileName("reader-font-gestures"))
            reader.save(ReaderSettings(
                font = ReaderFont.LXGW_WENKAI,
                tapPagingEnabled = false,
                scrollSideGesturesEnabled = true,
                pagedBookmarkGesturesEnabled = false,
                toolbarTools = listOf(com.breakyuna.esjzone.ui.reader.ReaderTool.BRIGHTNESS, com.breakyuna.esjzone.ui.reader.ReaderTool.CONTENTS),
                toolbarOrder = ReaderTool.decodeOrder("BRIGHTNESS,NOVEL_DETAIL,CONTENTS"),
                brightness = 0.35f,
                keepScreenOn = true,
                leftTapForward = true,
                eyeProtectionEnabled = true,
                showSystemStatusBar = true,
                showSystemNavigationBar = false,
                showChapterName = false,
                showTimeBattery = true
            ))
            val first = reader.settings.first { it.font == ReaderFont.LXGW_WENKAI }
            assertFalse(first.tapPagingEnabled)
            assertTrue(first.scrollSideGesturesEnabled)
            assertFalse(first.pagedBookmarkGesturesEnabled)
            assertEquals(listOf(com.breakyuna.esjzone.ui.reader.ReaderTool.BRIGHTNESS, com.breakyuna.esjzone.ui.reader.ReaderTool.CONTENTS), first.toolbarTools)
            assertEquals(ReaderTool.decodeOrder("BRIGHTNESS,NOVEL_DETAIL,CONTENTS"), first.toolbarOrder)
            assertEquals(0.35f, first.brightness, 0.001f)
            assertTrue(first.keepScreenOn)
            assertTrue(first.leftTapForward)
            assertTrue(first.eyeProtectionEnabled)
            assertTrue(first.showSystemStatusBar)
            assertFalse(first.showSystemNavigationBar)
            assertFalse(first.showChapterName)
            assertTrue(first.showTimeBattery)
            reader.save(first.copy(font = ReaderFont.SOURCE_HAN_SERIF, tapPagingEnabled = true, scrollSideGesturesEnabled = false))
            val second = reader.settings.first { it.font == ReaderFont.SOURCE_HAN_SERIF }
            assertTrue(second.tapPagingEnabled)
            assertFalse(second.scrollSideGesturesEnabled)
            reader.save(second.copy(font = ReaderFont.SOURCE_HAN_SANS))
            assertEquals(ReaderFont.SOURCE_HAN_SANS, reader.settings.first { it.font == ReaderFont.SOURCE_HAN_SANS }.font)
            reader.save(ReaderSettings())
            val reset = reader.settings.first { it.font == ReaderFont.SYSTEM }
            assertTrue(reset.tapPagingEnabled)
            assertTrue(reset.scrollSideGesturesEnabled)
            assertTrue(reset.pagedBookmarkGesturesEnabled)
            assertEquals(com.breakyuna.esjzone.ui.reader.ReaderTool.defaults, reset.toolbarTools)
            assertEquals(ReaderTool.defaultOrder, reset.toolbarOrder)
            assertEquals(-1f, reset.brightness, 0.001f)
            assertFalse(reset.keepScreenOn)
            assertFalse(reset.leftTapForward)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun settings_defaults_areStableOnFreshStore() = runBlocking {
        val scope = newScope()
        try {
            val settings = SettingsDataStore(context, scope, fileName("settings-defaults"))
            assertEquals(AppPalette.MONOCHROME, settings.palette.first())
            assertTrue(settings.adult.first())
            assertTrue(settings.hideHomeRecommendations.first())
            assertEquals("www.esjzone.cc", settings.domain.first())
            assertEquals(AppLanguage.SYSTEM, settings.language.first())
            assertTrue(settings.readerAutoSave.first())
            assertFalse(settings.bookshelfListView.first())
            assertFalse(settings.novelListGridView.first())
            assertFalse(settings.novelListAdultOnly.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun settings_values_persistAcrossStoreRecreation() = runBlocking {
        val fileName = fileName("settings-restart")
        val firstScope = newScope()
        try {
            val first = SettingsDataStore(context, firstScope, fileName)
            first.setPalette(AppPalette.INK)
            first.setAdult(false)
            first.setHideHomeRecommendations(false)
            first.setDomain("www.esjzone.one")
            first.setLanguage(AppLanguage.SIMPLIFIED_CHINESE)
            first.setReaderAutoSave(false)
            first.setBookshelfListView(true)
            first.setNovelListGridView(true)
            first.setNovelListAdultOnly(true)
            assertFalse(first.adult.first { !it })
            assertEquals(AppPalette.INK, first.palette.first { it == AppPalette.INK })
            assertFalse(first.hideHomeRecommendations.first { !it })
            assertTrue(first.bookshelfListView.first { it })
            assertTrue(first.novelListGridView.first { it })
            assertTrue(first.novelListAdultOnly.first { it })
        } finally {
            firstScope.cancel()
        }

        val secondScope = newScope()
        try {
            val second = SettingsDataStore(context, secondScope, fileName)
            assertEquals(AppPalette.INK, second.palette.first { it == AppPalette.INK })
            assertFalse(second.adult.first { !it })
            assertFalse(second.hideHomeRecommendations.first { !it })
            assertEquals("www.esjzone.one", second.domain.first())
            assertEquals(AppLanguage.SIMPLIFIED_CHINESE, second.language.first())
            assertFalse(second.readerAutoSave.first())
            assertTrue(second.bookshelfListView.first { it })
            assertTrue(second.novelListGridView.first())
            assertTrue(second.novelListAdultOnly.first())
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun reader_defaults_andCorruptSave_recoverWithinSupportedRanges() = runBlocking {
        val scope = newScope()
        try {
            val reader = ReaderSettingsDataStore(context, scope, fileName("reader-corrupt"))
            assertEquals(ReaderSettings(), reader.settings.first())
            reader.save(
                ReaderSettings(
                    background = ReaderBackground.DARK,
                    font = ReaderFont.SERIF,
                    fontSizeSp = Float.NaN,
                    letterSpacingSp = Float.POSITIVE_INFINITY,
                    lineSpacingSp = -100f,
                    paragraphSpacingDp = 100f,
                    pageSpacingDp = 1f,
                    horizontalPaddingDp = 100f,
                    script = ReaderScript.TRADITIONAL
                )
            )
            val recovered = reader.settings.first { it.background == ReaderBackground.DARK }
            assertEquals(18f, recovered.fontSizeSp)
            assertEquals(0.3f, recovered.letterSpacingSp)
            assertEquals(4f, recovered.lineSpacingSp)
            assertEquals(32f, recovered.paragraphSpacingDp)
            assertEquals(16f, recovered.pageSpacingDp)
            assertEquals(48f, recovered.horizontalPaddingDp)
            assertEquals(ReaderFont.SERIF, recovered.font)
            assertEquals(ReaderScript.TRADITIONAL, recovered.script)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun reader_legacyMigration_recoversMalformedEnumsAndNumbers() = runBlocking {
        val legacy = context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        legacy.edit()
            .clear()
            .putString("background", "UNKNOWN_BACKGROUND")
            .putString("font", "UNKNOWN_FONT")
            .putFloat("font_size", Float.NaN)
            .putFloat("letter_spacing", 99f)
            .putFloat("line_spacing", -5f)
            .putFloat("paragraph_spacing", Float.POSITIVE_INFINITY)
            .putFloat("page_spacing", 1f)
            .putFloat("horizontal_padding", 99f)
            .putString("script", "UNKNOWN_SCRIPT")
            .commit()
        val scope = newScope()
        try {
            val reader = ReaderSettingsDataStore(context, scope, fileName("reader-corrupt-legacy"))
            reader.migrateFromLegacy(context)
            val recovered = reader.settings.first { it.fontSizeSp == 18f }
            assertEquals(ReaderBackground.SYSTEM, recovered.background)
            assertEquals(ReaderFont.SYSTEM, recovered.font)
            assertEquals(18f, recovered.fontSizeSp)
            assertEquals(2f, recovered.letterSpacingSp)
            assertEquals(10f, recovered.lineSpacingSp)
            assertEquals(10f, recovered.paragraphSpacingDp)
            assertEquals(32f, recovered.pageSpacingDp)
            assertEquals(48f, recovered.horizontalPaddingDp)
            assertEquals(ReaderScript.ORIGINAL, recovered.script)
        } finally {
            scope.cancel()
            legacy.edit().clear().commit()
        }
    }

    @Test
    fun reader_legacyMigration_isIdempotentAndCurrentSettingsWin() = runBlocking {
        val legacy = context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        legacy.edit()
            .clear()
            .putString("background", ReaderBackground.SEPIA.name)
            .putString("font", ReaderFont.MONOSPACE.name)
            .putFloat("font_size", 24f)
            .putFloat("letter_spacing", 1.2f)
            .putFloat("line_spacing", 18f)
            .putFloat("paragraph_spacing", 24f)
            .putFloat("page_spacing", 64f)
            .putFloat("horizontal_padding", 40f)
            .putString("script", ReaderScript.SIMPLIFIED.name)
            .commit()

        val fileName = fileName("reader-migration")
        val firstScope = newScope()
        try {
            val first = ReaderSettingsDataStore(context, firstScope, fileName)
            first.migrateFromLegacy(context)
            assertEquals(ReaderBackground.SEPIA, first.settings.first { it.background == ReaderBackground.SEPIA }.background)
            assertEquals(24f, first.settings.value.fontSizeSp)
        } finally {
            firstScope.cancel()
        }

        legacy.edit().putString("background", ReaderBackground.DARK.name).commit()
        val secondScope = newScope()
        try {
            val second = ReaderSettingsDataStore(context, secondScope, fileName)
            second.migrateFromLegacy(context)
            assertEquals(ReaderBackground.SEPIA, second.settings.first().background)
        } finally {
            secondScope.cancel()
            legacy.edit().clear().commit()
        }
    }

    @Test
    fun reader_legacyMigration_doesNotOverwriteExistingDataStoreSettings() = runBlocking {
        val legacy = context.getSharedPreferences("reader_settings", Context.MODE_PRIVATE)
        legacy.edit().clear().putString("background", ReaderBackground.DARK.name).commit()
        val scope = newScope()
        try {
            val reader = ReaderSettingsDataStore(context, scope, fileName("reader-current-wins"))
            reader.save(ReaderSettings(background = ReaderBackground.PAPER))
            reader.migrateFromLegacy(context)
            assertEquals(ReaderBackground.PAPER, reader.settings.first { it.background == ReaderBackground.PAPER }.background)
        } finally {
            scope.cancel()
            legacy.edit().clear().commit()
        }
    }

    @Test
    fun oldGlobalIndentMigratesToZeroOrTwoCharacters() = runBlocking {
        val oldKey = booleanPreferencesKey("first_line_indent")
        val newKey = intPreferencesKey("first_line_indent_chars")
        val migratedKey = booleanPreferencesKey("legacy_reader_settings_migration_complete")
        val cases = listOf(
            Triple(null, null, 0),
            Triple(false, null, 0),
            Triple(true, null, 2),
            Triple(true, 0, 0),
            Triple(null, 1, 2),
            Triple(null, 2, 2),
            Triple(null, 9, 0)
        )
        for ((old, existing, expected) in cases) {
            val name = fileName("reader-indent")
            val file = context.preferencesDataStoreFile(name)
            val legacyScope = newScope()
            try {
                val store = PreferenceDataStoreFactory.create(scope = legacyScope, produceFile = { file })
                store.edit {
                    old?.let { enabled -> it[oldKey] = enabled }
                    it[migratedKey] = true
                    existing?.let { chars -> it[newKey] = chars }
                    it[intPreferencesKey("book_first_line_indent_chars:book-1")] = 2
                }
            } finally {
                requireNotNull(legacyScope.coroutineContext[Job]).cancelAndJoin()
            }
            val readerScope = newScope()
            try {
                val reader = ReaderSettingsDataStore(context, readerScope, name)
                reader.migrateFromLegacy(context)
                val (snapshot, _) = reader.backupSnapshot()
                assertEquals(expected, snapshot.get("first_line_indent_chars").asInt)
            } finally {
                requireNotNull(readerScope.coroutineContext[Job]).cancelAndJoin()
            }
            val verifyScope = newScope()
            try {
                val store = PreferenceDataStoreFactory.create(scope = verifyScope, produceFile = { file })
                val persisted = store.data.first()
                assertFalse(persisted.contains(oldKey))
                assertEquals(expected, persisted[newKey] ?: 0)
            } finally {
                requireNotNull(verifyScope.coroutineContext[Job]).cancelAndJoin()
                file.delete()
            }
        }
    }

    @Test
    fun globalIndentSavesTheLatestSelectionAndResetsWithReaderSettings() = runBlocking {
        val name = fileName("reader-indent")
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)
        try {
            val reader = ReaderSettingsDataStore(context, scope, name)
            val defaults = ReaderSettings()
            val indented = defaults.copy(firstLineIndentChars = 2)
            assertEquals(0, reader.settings.value.firstLineIndentChars)
            reader.saveDebounced(indented)
            reader.saveDebounced(defaults)
            reader.saveDebounced(indented)
            reader.saveScriptInBackground(ReaderScript.TRADITIONAL)
            withTimeout(5_000) {
                assertEquals(2, reader.backupSnapshot().first.get("first_line_indent_chars").asInt)
                assertEquals(indented.copy(script = ReaderScript.TRADITIONAL),
                    reader.settings.first { it.script == ReaderScript.TRADITIONAL })
            }
            reader.saveDebounced(defaults)
            withTimeout(5_000) {
                assertEquals(0, reader.backupSnapshot().first.get("first_line_indent_chars").asInt)
                assertEquals(defaults, reader.settings.first { it.firstLineIndentChars == 0 })
            }
        } finally {
            job.cancelAndJoin()
            context.preferencesDataStoreFile(name).delete()
        }
    }

    @Test
    fun legacyBackgroundRetainsPhotoColorBlurAndTransparency() = runBlocking {
        val storeName = "reader-legacy-photo-${UUID.randomUUID()}.preferences_pb"
        val photoName = "${UUID.randomUUID()}.jpg"
        val legacyJob = SupervisorJob()
        try {
            val legacy = PreferenceDataStoreFactory.create(scope = CoroutineScope(legacyJob + Dispatchers.IO),
                produceFile = { context.preferencesDataStoreFile(storeName) })
            legacy.edit {
                it[stringPreferencesKey("background")] = ReaderBackground.CUSTOM_IMAGE.name
                it[stringPreferencesKey("background_image_file")] = photoName
                it[longPreferencesKey("background_image_text_color")] = 0xFF202020L
                it[floatPreferencesKey("background_image_blur")] = 50f
                it[floatPreferencesKey("background_image_transparency")] = 90f
            }
        } finally { legacyJob.cancelAndJoin() }
        val job = SupervisorJob()
        try {
            val current = ReaderSettingsDataStore(context, CoroutineScope(job + Dispatchers.IO), storeName)
            val image = current.readImageBackground()
            assertEquals(photoName, image.fileName)
            assertEquals(0xFF202020L, image.textColor)
            assertEquals(50f, image.blur, 0f)
            assertEquals(90f, image.transparency, 0f)
            assertEquals(0.5f, image.cropX, 0f)
            assertEquals(0.5f, image.cropY, 0f)
            assertTrue(image.glowEnabled)
            assertEquals(70f, image.glowStrength, 0f)
            assertEquals(ReaderBackground.CUSTOM_IMAGE, current.settings.first { it.background == ReaderBackground.CUSTOM_IMAGE }.background)
        } finally { job.cancelAndJoin(); context.preferencesDataStoreFile(storeName).delete() }
    }

    @Test
    fun applyingPhotoRetainsPendingReaderEditsAndSurvivesStoreRecreation() = runBlocking {
        withTimeout(5_000) {
            val fileName = "reader-image-${UUID.randomUUID()}.preferences_pb"
            val original = ReaderSettings(fontSizeSp = 24f, horizontalPaddingDp = 28f)
            val photo = ReaderImageBackground("${UUID.randomUUID()}.jpg", 0xFF111111L, glowEnabled = false, glowStrength = 37f)
            val firstJob = SupervisorJob()
            try {
                val first = ReaderSettingsDataStore(context, CoroutineScope(firstJob + Dispatchers.IO), fileName)
                assertEquals(70f, first.readImageBackground().glowStrength, 0f)
                first.saveDebounced(original)
                first.saveImageBackground(photo)
                val applied = first.settings.first { it.background == ReaderBackground.CUSTOM_IMAGE }
                assertEquals(original.copy(background = ReaderBackground.CUSTOM_IMAGE), applied)
                first.save(applied.copy(background = ReaderBackground.SYSTEM))
                assertEquals(photo, first.readImageBackground())
            } finally { firstJob.cancelAndJoin() }
            val secondJob = SupervisorJob()
            try {
                val second = ReaderSettingsDataStore(context, CoroutineScope(secondJob + Dispatchers.IO), fileName)
                assertEquals(photo, second.readImageBackground())
                assertEquals(original, second.settings.first { it.fontSizeSp == 24f })
            } finally { secondJob.cancelAndJoin(); context.preferencesDataStoreFile(fileName).delete() }
        }
    }

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun fileName(prefix: String): String = "$prefix-${System.nanoTime()}.preferences_pb"
}

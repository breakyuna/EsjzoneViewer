package com.breakyuna.esjzone

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.breakyuna.esjzone.data.settings.ReaderSettingsDataStore
import com.breakyuna.esjzone.data.settings.SettingsDataStore
import com.breakyuna.esjzone.ui.designsystem.AppThemeVariant
import com.breakyuna.esjzone.ui.reader.ReaderBackground
import com.breakyuna.esjzone.ui.reader.ReaderFont
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.ReaderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance coverage for the preference boundaries used during startup. */
class DataStoreAcceptanceInstrumentedTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun settings_defaults_areStableOnFreshStore() = runBlocking {
        val scope = newScope()
        try {
            val settings = SettingsDataStore(context, scope, fileName("settings-defaults"))
            assertTrue(settings.adult.first())
            assertEquals(AppThemeVariant.DEFAULT, settings.theme.first())
            assertEquals("www.esjzone.cc", settings.domain.first())
            assertEquals(AppLanguage.SYSTEM, settings.language.first())
            assertTrue(settings.readerAutoSave.first())
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
            first.setAdult(false)
            first.setTheme(AppThemeVariant.SUNSET)
            first.setDomain("www.esjzone.one")
            first.setLanguage(AppLanguage.SIMPLIFIED_CHINESE)
            first.setReaderAutoSave(false)
            assertEquals(AppThemeVariant.SUNSET, first.theme.first { it == AppThemeVariant.SUNSET })
            assertFalse(first.adult.first { !it })
        } finally {
            firstScope.cancel()
        }

        val secondScope = newScope()
        try {
            val second = SettingsDataStore(context, secondScope, fileName)
            assertFalse(second.adult.first { !it })
            assertEquals(AppThemeVariant.SUNSET, second.theme.first())
            assertEquals("www.esjzone.one", second.domain.first())
            assertEquals(AppLanguage.SIMPLIFIED_CHINESE, second.language.first())
            assertFalse(second.readerAutoSave.first())
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

    private fun newScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun fileName(prefix: String): String = "$prefix-${System.nanoTime()}.preferences_pb"
}

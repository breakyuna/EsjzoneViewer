package com.breakyuna.esjzone

import android.content.Context
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.data.settings.SettingsDataStore
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugLoggingSettingsInstrumentedTest {
    @Test fun debugLoggingDefaultsOffAndRestoresAcrossStoreRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "debug-logging-${System.nanoTime()}.preferences_pb"
        val previousMode = AppLogger.debugEnabled
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val first = SettingsDataStore(context, firstScope, name)
            first.restoreLoggingMode()
            assertFalse(AppLogger.debugEnabled)
            first.setDebugLogging(true)
            withTimeout(5_000) { first.debugLogging.first { it } }
            assertTrue(AppLogger.debugEnabled)
            firstScope.cancel()
            firstScope.coroutineContext[Job]?.join()

            AppLogger.setDebugMode(false)
            val second = SettingsDataStore(context, secondScope, name)
            second.restoreLoggingMode()
            assertTrue(AppLogger.debugEnabled)
            second.setDebugLogging(false)
            withTimeout(5_000) { second.debugLogging.first { !it } }
            assertFalse(AppLogger.debugEnabled)
        } finally {
            firstScope.cancel()
            secondScope.cancel()
            firstScope.coroutineContext[Job]?.join()
            secondScope.coroutineContext[Job]?.join()
            AppLogger.setDebugMode(previousMode)
            context.preferencesDataStoreFile(name).delete()
        }
    }
}

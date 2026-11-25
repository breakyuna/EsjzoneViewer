package com.breakyuna.esjzone

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.storage.StorageAccessException
import com.breakyuna.esjzone.storage.StorageFile
import com.breakyuna.esjzone.storage.UserStorage
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.PublicLogWriter
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PublicLogWriterInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val treeUri get() = DocumentsContract.buildTreeDocumentUri(
        "com.breakyuna.esjzone.storage.test.documents", "root")

    private fun directory(): StorageFile {
        context.contentResolver.call(treeUri, "grant-fixture-tree", null, null)
        return StorageFile(StorageFile.fromTree(context, treeUri), "case-${UUID.randomUUID()}")
            .apply { check(mkdirs()) }
    }

    private fun denyOpen(root: StorageFile, denied: Boolean) {
        context.contentResolver.call(treeUri, "configure-fixture", root.name, Bundle().apply {
            putString("mode", if (denied) "security" else null)
            putString("operation", "open")
        })
    }

    private fun text(file: StorageFile) = file.inputStream().bufferedReader().use { it.readText() }

    @Test fun restoresPublicLogsBeforeStorageInitializationAndRecoversFromStorageAccessFailure() = runBlocking {
        for (pending in listOf(false, true)) {
            val root = directory()
            val files = File(context.cacheDir, "log-selection-${UUID.randomUUID()}").apply { mkdirs() }
            val isolated = object : ContextWrapper(context) {
                override fun getApplicationContext(): Context = this
                override fun getFilesDir(): File = files
            }
            val selectedUri = DocumentsContract.buildTreeDocumentUri(treeUri.authority,
                DocumentsContract.getDocumentId(root.uri))
            val seedJob = SupervisorJob()
            val seed = PreferenceDataStoreFactory.create(scope = CoroutineScope(seedJob + Dispatchers.IO),
                produceFile = { isolated.preferencesDataStoreFile("user_storage.preferences_pb") })
            try {
                try {
                    seed.edit {
                        it[stringPreferencesKey("directory_uri")] = if (pending) UserStorage.PRIVATE_LOCATION
                            else selectedUri.toString()
                        if (pending) it[stringPreferencesKey("migration_target_uri")] = selectedUri.toString()
                    }
                } finally { seedJob.cancelAndJoin() }
                val storage = UserStorage(isolated)
                storage.operationMutex.lock()
                try {
                    val logs = requireNotNull(withTimeout(5_000) { storage.logDirectories().first() })
                    assertEquals("", storage.activeLocation.value) // initialize() has not run.
                    assertEquals("logs", logs.name)
                    val writer = PublicLogWriter(sessionTime = "2026-11-01_18-45-39-845")
                    writer.append(AppLogger.sanitizeForDisplay("Startup password=fixture-secret"))
                    writer.attach(logs)
                    val logFile = StorageFile(root, "logs/app_2026-11-01_18-45-39-845.txt")
                    val record = text(logFile)
                    assertTrue(record.contains("Startup"))
                    assertFalse(record.contains("fixture-secret"))
                    if (!pending) {
                        denyOpen(root, true)
                        try {
                            assertThrows(StorageAccessException::class.java) { writer.append("During failure") }
                        } finally {
                            denyOpen(root, false)
                        }
                        writer.append("Recovered")
                        assertTrue(text(logFile).contains("During failure\nRecovered\n"))
                    }
                } finally { storage.operationMutex.unlock() }
            } finally {
                denyOpen(root, false)
                root.deleteRecursively()
                files.deleteRecursively()
            }
        }
    }
}

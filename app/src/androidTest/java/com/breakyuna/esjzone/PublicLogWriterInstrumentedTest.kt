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

    private fun text(file: StorageFile) = file.inputStream().bufferedReader().use { it.readText() }

    @Test fun restoresPublicLogsBeforeStorageInitializationEvenWithMigrationLockHeld() = runBlocking {
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
                    val writer = PublicLogWriter()
                    writer.append(AppLogger.sanitizeForDisplay("Startup password=fixture-secret"))
                    writer.attach(logs)
                    val record = text(StorageFile(root, "logs/app_logs.log"))
                    assertTrue(record.contains("Startup"))
                    assertFalse(record.contains("fixture-secret"))
                } finally { storage.operationMutex.unlock() }
            } finally {
                root.deleteRecursively()
                files.deleteRecursively()
            }
        }
    }

    @Test fun appendsRotatesSurvivesWriterRestartAndClearsOnlyLogFiles() {
        val shared = directory()
        val local = File(context.cacheDir, "local-logs-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            for (root in listOf(shared, StorageFile(local))) {
                val keep = StorageFile(root, "downloads/keep.txt")
                check(requireNotNull(keep.parentFile).mkdirs())
                keep.writeText("keep")
                val logs = StorageFile(root, "logs")
                val writer = PublicLogWriter(maxBytes = 64)
                writer.attach(logs)
                writer.append("a".repeat(30))
                writer.append("b".repeat(30))
                writer.append("c".repeat(30))
                assertEquals("a".repeat(30) + "\n" + "b".repeat(30) + "\n",
                    text(StorageFile(logs, "app_logs_old.log")))
                assertEquals("c".repeat(30) + "\n", text(StorageFile(logs, "app_logs.log")))
                val restarted = PublicLogWriter(maxBytes = 64)
                restarted.attach(logs)
                restarted.append("Restarted 日志")
                assertTrue(text(StorageFile(logs, "app_logs.log")).startsWith("c".repeat(30)))
                assertTrue(text(StorageFile(logs, "app_logs.log")).contains("Restarted 日志"))
                restarted.writeCrashReport("Startup failure")
                assertEquals("Startup failure", text(StorageFile(logs, "last_crash.log")))
                restarted.clear()
                assertTrue(logs.entries().isEmpty())
                assertEquals("keep", text(keep))
            }
        } finally {
            shared.deleteRecursively()
            local.deleteRecursively()
        }
    }

    @Test fun inaccessibleProviderRetainsRecordsAndDirectorySwitchUsesNewSelection() {
        val first = directory()
        val second = directory()
        fun denyOpen(denied: Boolean) {
            context.contentResolver.call(treeUri, "configure-fixture", first.name, Bundle().apply {
                putString("mode", if (denied) "security" else null)
                putString("operation", "open")
            })
        }
        try {
            val writer = PublicLogWriter()
            writer.attach(StorageFile(first, "logs"))
            writer.append("Before failure")
            denyOpen(true)
            assertThrows(StorageAccessException::class.java) { writer.append("During failure") }
            denyOpen(false)
            writer.append("Recovered")
            assertEquals("Before failure\nDuring failure\nRecovered\n",
                text(StorageFile(first, "logs/app_logs.log")))
            writer.attach(StorageFile(second, "logs"))
            writer.append("New selection")
            assertEquals("New selection\n", text(StorageFile(second, "logs/app_logs.log")))
            assertFalse(text(StorageFile(first, "logs/app_logs.log")).contains("New selection"))
        } finally {
            denyOpen(false)
            first.deleteRecursively()
            second.deleteRecursively()
        }
    }
}

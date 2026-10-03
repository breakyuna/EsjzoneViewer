package com.breakyuna.esjzone.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.*
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.offline.NovelDownloadStore
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Application-owned settings and scheduling; archives contain only LocalBackup's allowlist. */
class AutoBackup(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { this.context.preferencesDataStoreFile("auto_backup.preferences_pb") }
    )
    private val enabledKey = booleanPreferencesKey("enabled")
    private val scopeKey = stringPreferencesKey("bookshelf_scope")
    private val completedKey = longPreferencesKey("completed_at")
    val enabled = store.data.map { it[enabledKey] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val completedAt = store.data.map { it[completedKey] ?: 0L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)

    fun startScheduling() {
        scope.launch {
            store.data.map { it[enabledKey] == true }.distinctUntilChanged().collect { active ->
                val manager = WorkManager.getInstance(context)
                if (active) manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                        .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true)
                            .setRequiresStorageNotLow(true).build()).build())
                else manager.cancelUniqueWork(WORK_NAME)
            }
        }
    }

    suspend fun setEnabled(value: Boolean, bookshelfScope: String) {
        store.edit { it[enabledKey] = value; it[scopeKey] = bookshelfScope }
    }

    suspend fun backupScope(): String? = store.data.first().let {
        if (it[enabledKey] == true) it[scopeKey] else null
    }

    suspend fun completed(time: Long) { store.edit { it[completedKey] = time } }
    fun archives(): List<File> = directory(context).listFiles().orEmpty()
        .filter { it.isFile && it.name.startsWith("esjzone-auto-") && it.extension == "zip" }
        .sortedByDescending { it.name }

    companion object {
        private const val WORK_NAME = "local-auto-backup"
        fun directory(context: Context) = File(context.filesDir, "auto_backups")
        internal fun obsoleteArchives(files: List<File>): List<File> = files
            .filter { it.name.startsWith("esjzone-auto-") && it.extension == "zip" }
            .sortedByDescending { it.name }.drop(3)
    }
}

class AutoBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = EsjzoneApplication.instance.container
        val backup = container.autoBackup
        val directory = AutoBackup.directory(applicationContext)
        val pending = File(directory, "pending.zip.part")
        try {
            val bookshelfScope = backup.backupScope() ?: return@withContext Result.success()
            check(directory.isDirectory || directory.mkdirs())
            NovelDownloadStore.initialize(applicationContext)
            LocalBackup.export(applicationContext, Uri.fromFile(pending), container.database,
                bookshelfScope, BackupCategory.entries.toSet())
            ensureActive()
            if (backup.backupScope() != bookshelfScope) return@withContext Result.success()
            val time = System.currentTimeMillis()
            check(pending.renameTo(File(directory, "esjzone-auto-$time.zip")))
            AutoBackup.obsoleteArchives(backup.archives()).forEach { it.delete() }
            backup.completed(time)
            Result.success()
        } catch (error: CancellationException) { throw error
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } finally { pending.delete() }
    }
}

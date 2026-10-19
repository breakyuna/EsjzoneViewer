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
import com.breakyuna.esjzone.storage.StorageFile
import com.breakyuna.esjzone.storage.UserStorage
import com.breakyuna.esjzone.util.copyWithCancellation
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.sync.withLock
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
    private val includeDownloadsKey = booleanPreferencesKey("include_downloads")
    private val intervalKey = intPreferencesKey("interval_hours")
    private val completedKey = longPreferencesKey("completed_at")
    private val failedKey = booleanPreferencesKey("failed")
    val failed = store.data.map { it[failedKey] == true }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val enabled = store.data.map { it[enabledKey] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val includeDownloads = store.data.map { it[includeDownloadsKey] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)
    val completedAt = store.data.map { it[completedKey] ?: 0L }
        .stateIn(scope, SharingStarted.Eagerly, 0L)
    val intervalHours = store.data.map { normalizedInterval(it[intervalKey]) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_INTERVAL_HOURS)

    fun startScheduling() {
        scope.launch {
            store.data.map { (it[enabledKey] == true) to normalizedInterval(it[intervalKey]) }
                .distinctUntilChanged().collect { (active, hours) ->
                val manager = WorkManager.getInstance(context)
                if (active) manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<AutoBackupWorker>(hours.toLong(), TimeUnit.HOURS)
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

    suspend fun setIncludeDownloads(value: Boolean) {
        store.edit { it[includeDownloadsKey] = value }
    }

    suspend fun setIntervalHours(value: Int) {
        require(value in INTERVAL_HOURS)
        store.edit { it[intervalKey] = value }
    }

    suspend fun backupCategories(): Set<BackupCategory> = store.data.first().let { preferences ->
        BackupCategory.entries.filter {
            it != BackupCategory.DOWNLOADS || preferences[includeDownloadsKey] == true
        }.toSet()
    }

    suspend fun completed(time: Long) { store.edit { it[completedKey] = time; it[failedKey] = false } }
    suspend fun recordFailure() { store.edit { it[failedKey] = true } }
    fun archiveDirectory(): StorageFile = EsjzoneApplication.instance.container.storage.backups()
    fun archives(): List<StorageFile> = archiveDirectory().listFiles().orEmpty()
        .filter { it.isFile && UserStorage.isAutoBackupName(it.name) }
        .sortedByDescending { BackupFileNames.automaticTime(it.name) }

    fun deleteArchive(file: StorageFile): Boolean {
        if (NovelDownloadStore.migrating.value) return false
        require(file.parentFile == archiveDirectory() && UserStorage.isAutoBackupName(file.name))
        return !file.exists() || file.delete()
    }

    companion object {
        const val DEFAULT_INTERVAL_HOURS = 12
        val INTERVAL_HOURS = listOf(6, 12, 24, 48)
        internal fun normalizedInterval(value: Int?): Int = value?.takeIf { it in INTERVAL_HOURS } ?: DEFAULT_INTERVAL_HOURS
        private const val WORK_NAME = "local-auto-backup"
        internal fun obsoleteArchives(files: List<StorageFile>): List<StorageFile> = files
            .filter { UserStorage.isAutoBackupName(it.name) }
            .sortedByDescending { BackupFileNames.automaticTime(it.name) }.drop(3)

        internal fun pruneArchives(files: List<StorageFile>, migrationPending: Boolean = false): Boolean {
            // A cleanup journal may still need these archives to validate the old copies.
            if (migrationPending) return true
            var removedAll = true
            obsoleteArchives(files).forEach { file ->
                try {
                    if (!file.delete()) removedAll = false
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    removedAll = false
                }
            }
            return removedAll
        }
    }
}

class AutoBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val container = EsjzoneApplication.instance.container
        container.initializeDownloads()
        container.storage.operationMutex.withLock {
            val backup = container.autoBackup
            val pending = File.createTempFile("auto-backup-", ".zip.part", applicationContext.cacheDir)
            var publishing: StorageFile? = null
            try {
                val bookshelfScope = backup.backupScope() ?: return@withLock Result.success()
                val categories = backup.backupCategories()
                LocalBackup.export(applicationContext, Uri.fromFile(pending), container.database,
                    bookshelfScope, categories)
                ensureActive()
                if (backup.backupScope() != bookshelfScope || backup.backupCategories() != categories) {
                    return@withLock Result.success()
                }
                val directory = backup.archiveDirectory()
                check(directory.mkdirs())
                val time = System.currentTimeMillis()
                val destination = StorageFile(directory, BackupFileNames.automatic(time))
                val temporary = StorageFile.createTempFile("pending-", ".zip.part", directory)
                publishing = temporary
                val transferContext = currentCoroutineContext()
                pending.inputStream().use { input -> temporary.outputStream().use { output ->
                    input.copyWithCancellation(output) { transferContext.ensureActive() }
                } }
                check(temporary.length() == pending.length())
                ensureActive()
                check(temporary.renameTo(destination))
                backup.completed(time)
                // Cleanup cannot turn a successfully published backup into a failed job.
                try {
                    if (!AutoBackup.pruneArchives(backup.archives(), migrationPending = NovelDownloadStore.migrating.value)) {
                        AppLogger.w("AutoBackup", "New backup saved; some old archives could not be removed")
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.w("AutoBackup", "New backup saved; archive cleanup is unavailable", error)
                }
                Result.success()
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                backup.recordFailure()
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            } finally {
                pending.delete()
                publishing?.let { runCatching { it.delete() } }
            }
        }
    }
}

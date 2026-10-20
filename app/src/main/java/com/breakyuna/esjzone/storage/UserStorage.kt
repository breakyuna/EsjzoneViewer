package com.breakyuna.esjzone.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStoreFile
import com.breakyuna.esjzone.offline.NovelDownloadStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

enum class StorageTransferPhase { IDLE, QUEUED, COPYING, VERIFYING, CLEANING, WAITING_FOR_READER, DONE, FAILED, CLEANUP_FAILED }

data class StorageTransferProgress(
    val phase: StorageTransferPhase = StorageTransferPhase.IDLE,
    val book: String = "",
    val completed: Int = 0,
    val total: Int = 0
) {
    val running: Boolean get() = phase in setOf(StorageTransferPhase.QUEUED, StorageTransferPhase.COPYING,
        StorageTransferPhase.VERIFYING, StorageTransferPhase.CLEANING, StorageTransferPhase.WAITING_FOR_READER)
}

class StorageMigrationBusyException : IOException("Download storage is being migrated")

/** Selected location and durable transfer journal. The active root changes only after validation. */
class UserStorage(context: Context) {
    private val context = context.applicationContext
    private val store = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { this.context.preferencesDataStoreFile("user_storage.preferences_pb") }
    )
    private val locationKey = stringPreferencesKey("directory_uri")
    private val labelKey = stringPreferencesKey("directory_label")
    private val pendingKey = stringPreferencesKey("migration_target_uri")
    private val pendingLabelKey = stringPreferencesKey("migration_target_label")
    private val cleanupSourceKey = stringPreferencesKey("migration_cleanup_source")
    private val cleanupBooksKey = stringSetPreferencesKey("migration_cleanup_books")
    private val cleanupTargetsKey = stringSetPreferencesKey("migration_cleanup_targets")
    private val cleanupBackupsKey = stringSetPreferencesKey("migration_cleanup_backups")
    private val stagingKey = stringPreferencesKey("migration_staging_directory")
    private val backupStagingKey = stringPreferencesKey("migration_backup_staging_file")
    private val failedKey = booleanPreferencesKey("migration_failed")
    private val setupUpgradeKey = booleanPreferencesKey("setup_from_private_storage")
    private val mutableSetupUpgrade = MutableStateFlow<Boolean?>(null)
    val setupUpgrade = mutableSetupUpgrade.asStateFlow()
    private val mutableLocation = MutableStateFlow("")
    val location = mutableLocation.asStateFlow()
    private val mutableActiveLocation = MutableStateFlow("")
    val activeLocation = mutableActiveLocation.asStateFlow()
    private val mutableLabel = MutableStateFlow("")
    val label = mutableLabel.asStateFlow()
    private val mutableTransfer = MutableStateFlow(StorageTransferProgress())
    val transfer = mutableTransfer.asStateFlow()
    internal val operationMutex = Mutex()
    private val readers = AtomicInteger()
    @Volatile private var root: StorageFile? = null
    @Volatile private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        operationMutex.withLock {
            if (initialized) return@withLock
            val preferences = store.data.first()
            val saved = preferences[locationKey].orEmpty()
            root = sharedRoot(saved)
            mutableActiveLocation.value = saved
            val pending = preferences[pendingKey]
            mutableLocation.value = pending ?: saved
            mutableLabel.value = preferences[pendingLabelKey] ?: preferences[labelKey].orEmpty()
            mutableSetupUpgrade.value = preferences[setupUpgradeKey]
            val hasCleanup = preferences[cleanupSourceKey] != null
            mutableTransfer.value = StorageTransferProgress(when {
                preferences[failedKey] == true && pending != null -> StorageTransferPhase.FAILED
                preferences[failedKey] == true && hasCleanup -> StorageTransferPhase.CLEANUP_FAILED
                pending != null || hasCleanup -> StorageTransferPhase.QUEUED
                else -> StorageTransferPhase.IDLE
            })
            NovelDownloadStore.setStorageMigrationPending(pending != null || hasCleanup)
            initialized = true
            if (mutableTransfer.value.running) StorageMigrationWorker.enqueue(context)
        }
    }

    suspend fun prepareSetup(restoredSession: Boolean) {
        initialize()
        // A restored transfer may hold operationMutex for the entire copy. Known setup state
        // needs no mutation and must let LoadingScreen enter the app immediately.
        if (mutableLocation.value.isNotBlank() || mutableSetupUpgrade.value != null) return
        operationMutex.withLock {
            if (mutableLocation.value.isBlank() && mutableSetupUpgrade.value == null) {
                store.edit { it[setupUpgradeKey] = restoredSession }
                mutableSetupUpgrade.value = restoredSession
            }
        }
    }

    private fun sharedRoot(location: String): StorageFile? = location
        .takeIf { it.isNotBlank() && it != PRIVATE_LOCATION }
        ?.let { StorageFile.fromTree(context, Uri.parse(it)) }

    /** Queue the same durable transfer, targeting the original private directories. */
    suspend fun moveToPrivate() = withContext(Dispatchers.IO) {
        initialize()
        operationMutex.withLock {
            check(!mutableTransfer.value.running)
            val preferences = store.data.first()
            check(preferences[cleanupSourceKey] == null && preferences[pendingKey] == null)
            check(root != null)
            withContext(NonCancellable) {
                store.edit {
                    it[pendingKey] = PRIVATE_LOCATION
                    it[pendingLabelKey] = ""
                    it[failedKey] = false
                }
                NovelDownloadStore.setStorageMigrationPending(true)
                mutableLocation.value = PRIVATE_LOCATION
                mutableLabel.value = ""
                mutableTransfer.value = StorageTransferProgress(StorageTransferPhase.QUEUED)
                StorageMigrationWorker.enqueue(context, replace = true)
            }
        }
    }

    fun downloads(): StorageFile = root?.let { StorageFile(it, "downloads") }
        ?: StorageFile(File(context.filesDir, "downloaded_novels"))
    fun backups(): StorageFile = root?.let { StorageFile(it, "autobackup") }
        ?: StorageFile(File(context.filesDir, "auto_backups"))

    // Keep old image URIs valid for readers that were opened before the root switch.
    fun retainReader() { readers.incrementAndGet() }
    fun releaseReader() {
        if (readers.decrementAndGet() == 0 && mutableTransfer.value.phase == StorageTransferPhase.WAITING_FOR_READER) {
            StorageMigrationWorker.enqueue(context, replace = true)
        }
    }

    /** Only validation and durable enqueue run in the UI operation, never the file transfer. */
    suspend fun select(uri: Uri, migrate: Boolean): Boolean = withContext(Dispatchers.IO) {
        initialize()
        operationMutex.withLock {
            check(!mutableTransfer.value.running)
            val preferences = store.data.first()
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            val target = StorageFile.fromTree(context, uri)
            check(target.isDirectory)
            val label = target.name
            if (preferences[cleanupSourceKey] != null) {
                // Re-authorize either journaled root without redirecting pending deletions elsewhere.
                check(uri.toString() == preferences[locationKey] || uri.toString() == preferences[cleanupSourceKey])
                store.edit { it[failedKey] = false }
                mutableTransfer.value = StorageTransferProgress(StorageTransferPhase.QUEUED)
                StorageMigrationWorker.enqueue(context, replace = true)
                return@withLock true
            }
            val nextDownloads = StorageFile(target, "downloads")
            check(nextDownloads.mkdirs() && StorageFile(target, "autobackup").mkdirs())
            val probe = StorageFile.createTempFile("access-", ".tmp", target)
            val renamed = StorageFile(target, "${probe.name}.renamed")
            try { check(probe.renameTo(renamed)) } finally { probe.delete(); renamed.delete() }
            StorageFile(nextDownloads, ".nomedia").outputStream().close()
            withContext(NonCancellable) {
                if (migrate && uri.toString() != preferences[locationKey]) {
                    store.edit {
                        if (it[pendingKey] != uri.toString()) {
                            it.remove(stagingKey); it.remove(backupStagingKey)
                        }
                        it[pendingKey] = uri.toString(); it[pendingLabelKey] = label; it[failedKey] = false
                    }
                    NovelDownloadStore.setStorageMigrationPending(true)
                    mutableLocation.value = uri.toString()
                    mutableLabel.value = label
                    mutableTransfer.value = StorageTransferProgress(StorageTransferPhase.QUEUED)
                    StorageMigrationWorker.enqueue(context, replace = true)
                } else {
                    NovelDownloadStore.setStorageMigrationPending(true)
                    try {
                        store.edit {
                            it[locationKey] = uri.toString(); it[labelKey] = label
                            it.remove(pendingKey); it.remove(pendingLabelKey); it.remove(stagingKey); it.remove(backupStagingKey); it[failedKey] = false
                        }
                        root = target
                        mutableActiveLocation.value = uri.toString()
                        NovelDownloadStore.restoreStorage(nextDownloads)
                        mutableLocation.value = uri.toString()
                        mutableLabel.value = label
                        mutableTransfer.value = StorageTransferProgress(StorageTransferPhase.DONE)
                    } finally {
                        val remaining = store.data.first()
                        NovelDownloadStore.setStorageMigrationPending(remaining[pendingKey] != null || remaining[cleanupSourceKey] != null)
                    }
                }
            }
            true
        }
    }

    suspend fun retryTransfer() = withContext(Dispatchers.IO) {
        initialize()
        operationMutex.withLock {
            val preferences = store.data.first()
            check(preferences[pendingKey] != null || preferences[cleanupSourceKey] != null)
            store.edit { it[failedKey] = false }
            mutableTransfer.value = StorageTransferProgress(StorageTransferPhase.QUEUED)
            StorageMigrationWorker.enqueue(context, replace = true)
        }
    }

    /** False means a reader still holds old image locations; WorkManager can retry cleanup later. */
    internal suspend fun transferFiles(onProgress: (StorageTransferProgress) -> Unit): Boolean = withContext(Dispatchers.IO) {
        initialize()
        operationMutex.withLock {
            val transferContext = currentCoroutineContext()
            val checkActive = { transferContext.ensureActive() }
            val report: (StorageTransferProgress) -> Unit = { mutableTransfer.value = it; onProgress(it) }
            var preferences = store.data.first()
            val pending = preferences[pendingKey]
            if (pending != null) {
                val target = sharedRoot(pending)
                val nextDownloads = target?.let { StorageFile(it, "downloads") }
                    ?: StorageFile(File(context.filesDir, "downloaded_novels"))
                val nextBackups = target?.let { StorageFile(it, "autobackup") }
                    ?: StorageFile(File(context.filesDir, "auto_backups"))
                val previousDownloads = downloads()
                val previousBackups = backups()
                if (previousDownloads.isDocument) check(previousDownloads.parentFile?.isDirectory == true)
                check(nextDownloads.mkdirs() && nextBackups.mkdirs())
                // Only remove the exact staging directory journaled by our own interrupted attempt.
                preferences[stagingKey]?.let { name ->
                    val staging = StorageFile(nextDownloads, name)
                    check(!staging.exists() || staging.deleteRecursively())
                    store.edit { it.remove(stagingKey) }
                }
                preferences[backupStagingKey]?.let { name ->
                    val staging = StorageFile(nextBackups, name)
                    check(!staging.exists() || staging.delete())
                    store.edit { it.remove(backupStagingKey) }
                }
                report(StorageTransferProgress(StorageTransferPhase.COPYING))
                val archives = previousBackups.listFiles().orEmpty().filter { it.isFile && isAutoBackupName(it.name) }
                archives.forEachIndexed { index, archive ->
                    checkActive()
                    report(StorageTransferProgress(StorageTransferPhase.COPYING, archive.name, index, archives.size))
                    StorageFile(nextBackups, archive.name).copyFrom(archive, checkActive,
                        beforeTemporaryCreate = { temporary ->
                            runBlocking { store.edit { it[backupStagingKey] = temporary.name } }
                        })
                }
                // Blocking DataStore writes here run on this worker's IO thread before staging creation.
                val migration = NovelDownloadStore.migrateStorage(nextDownloads,
                    onProgress = report,
                    stagingJournal = { name ->
                        runBlocking { store.edit { it[stagingKey] = name } }
                    },
                    holdWritesUntilCommit = true,
                    checkActive = checkActive)
                try {
                    withContext(NonCancellable) {
                        try {
                            store.edit {
                                it[cleanupSourceKey] = it[locationKey].orEmpty()
                                it[cleanupBooksKey] = migration.books.map { book -> book.name }.toSet()
                                it[cleanupTargetsKey] = migration.targets.map { (source, destination) -> "$source/$destination" }.toSet()
                                it[cleanupBackupsKey] = archives.map { archive -> archive.name }.toSet()
                                it[locationKey] = pending; it[labelKey] = it[pendingLabelKey].orEmpty()
                                it.remove(pendingKey); it.remove(pendingLabelKey); it.remove(stagingKey); it.remove(backupStagingKey)
                                it[failedKey] = false
                            }
                        } catch (error: Exception) {
                            NovelDownloadStore.restoreStorage(previousDownloads)
                            throw error
                        }
                        root = target
                        mutableActiveLocation.value = pending
                    }
                } finally {
                    withContext(NonCancellable) {
                        val remaining = store.data.first()
                        // Cleanup retries revalidate targets; keep them unchanged until the journal clears.
                        NovelDownloadStore.setStorageMigrationPending(remaining[pendingKey] != null || remaining[cleanupSourceKey] != null)
                    }
                }
            }
            preferences = store.data.first()
            val cleanupSource = preferences[cleanupSourceKey]
            if (cleanupSource != null) {
                if (readers.get() > 0) {
                    report(StorageTransferProgress(StorageTransferPhase.WAITING_FOR_READER))
                    return@withLock false
                }
                val oldRoot = sharedRoot(cleanupSource)
                val oldDownloads = oldRoot?.let { StorageFile(it, "downloads") }
                    ?: StorageFile(File(context.filesDir, "downloaded_novels"))
                val oldBackups = oldRoot?.let { StorageFile(it, "autobackup") }
                    ?: StorageFile(File(context.filesDir, "auto_backups"))
                val books = preferences[cleanupBooksKey].orEmpty().toList()
                val targets = preferences[cleanupTargetsKey].orEmpty().associate {
                    it.substringBefore('/') to it.substringAfter('/')
                }
                val archives = preferences[cleanupBackupsKey].orEmpty().toList()
                val originals = books.map { cleanupBooksKey to StorageFile(oldDownloads, it) } +
                    archives.map { cleanupBackupsKey to StorageFile(oldBackups, it) }
                originals.forEachIndexed { index, (key, file) ->
                    checkActive()
                    report(StorageTransferProgress(StorageTransferPhase.CLEANING, file.name, index, originals.size))
                    if (pending == null) {
                        // On restart the destination may have been disconnected or modified externally.
                        if (key == cleanupBooksKey) {
                            NovelDownloadStore.verifyMigratedBook(file,
                                StorageFile(downloads(), checkNotNull(targets[file.name])), checkActive)
                        } else {
                            val copied = StorageFile(backups(), file.name)
                            check(copied.isFile)
                            if (file.exists()) check(copied.sameContent(file, checkActive))
                        }
                    }
                    check(!file.exists() || file.deleteRecursively())
                    // An interrupted delete is retried using only the remaining committed source names.
                    store.edit { it[key] = it[key].orEmpty() - file.name }
                }
                withContext(NonCancellable) {
                    store.edit {
                        it.remove(cleanupSourceKey); it.remove(cleanupBooksKey); it.remove(cleanupTargetsKey); it.remove(cleanupBackupsKey)
                        it[failedKey] = false
                    }
                    NovelDownloadStore.finishStorageMigration()
                }
            }
            report(StorageTransferProgress(StorageTransferPhase.DONE))
            true
        }
    }

    internal suspend fun recordTransferFailure() {
        store.edit { it[failedKey] = true }
        val cleanup = store.data.first()[cleanupSourceKey] != null
        mutableTransfer.value = StorageTransferProgress(if (cleanup) StorageTransferPhase.CLEANUP_FAILED else StorageTransferPhase.FAILED)
    }

    suspend fun reindex() = withContext(Dispatchers.IO) {
        initialize()
        check(!mutableTransfer.value.running)
        operationMutex.withLock {
            root = sharedRoot(store.data.first()[locationKey].orEmpty())
            NovelDownloadStore.reindex(downloads())
            NovelDownloadStore.listDownloadedNovels()
        }
    }

    companion object {
        const val PRIVATE_LOCATION = "private"
        internal fun isAutoBackupName(name: String): Boolean =
            com.breakyuna.esjzone.backup.BackupFileNames.automaticTime(name) != null
    }
}

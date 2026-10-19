package com.breakyuna.esjzone.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.breakyuna.esjzone.offline.NovelDownloadStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One selected tree with separate downloads and autobackup children, as in Mihon. */
class UserStorage(context: Context) {
    private val context = context.applicationContext
    private val store = PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { this.context.preferencesDataStoreFile("user_storage.preferences_pb") }
    )
    private val locationKey = stringPreferencesKey("directory_uri")
    private val labelKey = stringPreferencesKey("directory_label")
    private val setupUpgradeKey = booleanPreferencesKey("setup_from_private_storage")
    private val mutableSetupUpgrade = MutableStateFlow<Boolean?>(null)
    val setupUpgrade = mutableSetupUpgrade.asStateFlow()
    private val mutableLocation = MutableStateFlow("")
    val location = mutableLocation.asStateFlow()
    private val mutableLabel = MutableStateFlow("")
    val label = mutableLabel.asStateFlow()
    internal val operationMutex = Mutex()
    @Volatile private var root: StorageFile? = null
    private var initialized = false

    suspend fun initialize() = operationMutex.withLock {
        if (!initialized) {
            val preferences = store.data.first()
            val saved = preferences[locationKey].orEmpty()
            root = saved.takeIf(String::isNotBlank)?.let { StorageFile.fromTree(context, Uri.parse(it)) }
            mutableLocation.value = saved
            mutableLabel.value = preferences[labelKey].orEmpty()
            mutableSetupUpgrade.value = preferences[setupUpgradeKey]
            initialized = true
        }
    }

    /** Keep the original onboarding copy even if the app restarts after the first login. */
    suspend fun prepareSetup(restoredSession: Boolean) {
        initialize()
        operationMutex.withLock {
            if (mutableLocation.value.isBlank() && mutableSetupUpgrade.value == null) {
                store.edit { it[setupUpgradeKey] = restoredSession }
                mutableSetupUpgrade.value = restoredSession
            }
        }
    }

    fun downloads(): StorageFile = root?.let { StorageFile(it, "downloads") }
        ?: StorageFile(File(context.filesDir, "downloaded_novels"))

    fun backups(): StorageFile = root?.let { StorageFile(it, "autobackup") }
        ?: StorageFile(File(context.filesDir, "auto_backups"))

    /** False means the new location is active, but some original files could not be removed. */
    suspend fun select(uri: Uri, migrate: Boolean): Boolean = withContext(Dispatchers.IO) {
        initialize()
        operationMutex.withLock {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            val target = StorageFile.fromTree(context, uri)
            check(target.isDirectory)
            val label = target.name
            val nextDownloads = StorageFile(target, "downloads")
            val nextBackups = StorageFile(target, "autobackup")
            check(nextDownloads.mkdirs() && nextBackups.mkdirs())
            // Verify both writing and renaming before migrating any original data.
            val probe = StorageFile.createTempFile("access-", ".tmp", target)
            val renamed = StorageFile(target, "${probe.name}.renamed")
            try { check(probe.renameTo(renamed)) } finally { probe.delete(); renamed.delete() }
            StorageFile(nextDownloads, ".nomedia").outputStream().close()
            val previousDownloads = downloads()
            val previousBackups = backups()
            if (migrate && previousDownloads.isDocument) check(previousDownloads.parentFile?.isDirectory == true)
            val oldArchives = if (migrate && previousBackups != nextBackups) {
                previousBackups.listFiles().orEmpty().filter { it.isFile && isAutoBackupName(it.name) }
            } else emptyList()
            val transferContext = currentCoroutineContext()
            val checkActive = { transferContext.ensureActive() }
            oldArchives.forEach { StorageFile(nextBackups, it.name).copyFrom(it, checkActive) }
            // The store holds its IO lock during this copy, then invalidates in-flight write guards.
            val migration = if (migrate) NovelDownloadStore.migrateStorage(nextDownloads, checkActive)
            else {
                NovelDownloadStore.restoreStorage(nextDownloads)
                NovelDownloadStore.StorageMigration(previousDownloads, emptyList())
            }
            withContext(NonCancellable) {
                try {
                    store.edit { it[locationKey] = uri.toString(); it[labelKey] = label }
                } catch (error: Exception) {
                    NovelDownloadStore.restoreStorage(previousDownloads)
                    throw error
                }
                root = target
                mutableLabel.value = label
                mutableLocation.value = uri.toString()
                // Only files copied by this operation are removed, after the new location is durable.
                val originals = oldArchives + migration.books
                originals.map { runCatching { it.deleteRecursively() }.getOrDefault(false) }.all { it }
            }
        }
    }

    suspend fun reindex() = withContext(Dispatchers.IO) {
        initialize()
        operationMutex.withLock {
            // Recreate document handles so externally moved/replaced files are resolved again.
            root = mutableLocation.value.takeIf(String::isNotBlank)?.let { StorageFile.fromTree(context, Uri.parse(it)) }
            NovelDownloadStore.initialize(context, downloads())
            NovelDownloadStore.reindex(downloads())
            NovelDownloadStore.listDownloadedNovels()
        }
    }

    companion object {
        internal fun isAutoBackupName(name: String): Boolean =
            com.breakyuna.esjzone.backup.BackupFileNames.automaticTime(name) != null
    }
}

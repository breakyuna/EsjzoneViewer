package com.breakyuna.esjzone

import android.content.Context
import android.content.ContextWrapper
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.storage.StorageFile
import com.breakyuna.esjzone.storage.StorageTransferPhase
import com.breakyuna.esjzone.storage.UserStorage
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserStorageLifecycleInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val treeUri get() = DocumentsContract.buildTreeDocumentUri(
        "com.breakyuna.esjzone.storage.test.documents", "root")

    private fun isolatedContext(directory: File): Context = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
    }

    private suspend fun seed(context: Context, update: (MutablePreferences) -> Unit) {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { context.preferencesDataStoreFile("user_storage.preferences_pb") })
        try { store.edit { update(it) } } finally { job.cancelAndJoin() }
    }

    @Test fun knownSetupStateDoesNotWaitForTheTransferLock() = runBlocking {
        for (selected in listOf(true, false)) {
            val directory = File(context.cacheDir, "startup-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val isolated = isolatedContext(directory)
                seed(isolated) {
                    if (selected) it[stringPreferencesKey("directory_uri")] = treeUri.toString()
                    else it[booleanPreferencesKey("setup_from_private_storage")] = true
                }
                val storage = UserStorage(isolated)
                storage.initialize()
                // A restored worker can own this lock before LoadingScreen calls prepareSetup.
                storage.operationMutex.lock()
                try {
                    withTimeout(1_000) { storage.prepareSetup(restoredSession = true) }
                    if (selected) assertEquals(treeUri.toString(), storage.location.value)
                    else assertEquals(true, storage.setupUpgrade.value)
                } finally { storage.operationMutex.unlock() }
            } finally { directory.deleteRecursively() }
        }
    }

    @Test fun sharedDownloadsAndBackupsReturnToOriginalPrivatePathsWithoutLosingSetupChoice() = runBlocking {
        context.contentResolver.call(treeUri, "grant-fixture-tree", null, null)
        val directory = File(context.cacheDir, "return-private-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = isolatedContext(directory)
        val id = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
        val novel = "https://www.esjzone.cc/detail/$id.html"
        val chapter = Chapter("Chapter", "https://www.esjzone.cc/forum/$id/1.html", false)
        val tree = StorageFile(StorageFile.fromTree(context, treeUri), "case-${UUID.randomUUID()}")
            .apply { check(mkdirs()) }
        val selectedUri = DocumentsContract.buildTreeDocumentUri(treeUri.authority,
            DocumentsContract.getDocumentId(tree.uri))
        val sharedDownloads = StorageFile(tree, "downloads")
        val archiveName = "esjzone-auto-$id.zip"
        val sharedArchive = StorageFile(StorageFile(tree, "autobackup"), archiveName)
        var sharedBook: StorageFile? = null
        try {
            NovelDownloadStore.initialize(context)
            val privateDownloads = File(directory, "downloaded_novels")
            NovelDownloadStore.initializeDirectory(privateDownloads)
            requireNotNull(NovelDownloadStore.saveChapter("Round trip $id", novel, "", listOf(chapter), chapter,
                DetailedChapter("Chapter", listOf(TextComponent("Preserved text")), null, null),
                Authorization("", "", "www.esjzone.cc")))
            val migration = NovelDownloadStore.migrateStorage(sharedDownloads) { }
            sharedBook = StorageFile(sharedDownloads, migration.targets.values.single())
            privateDownloads.deleteRecursively()
            sharedArchive.parentFile!!.mkdirs()
            sharedArchive.writeText("fixture archive")
            seed(isolated) {
                it[stringPreferencesKey("directory_uri")] = selectedUri.toString()
                it[stringPreferencesKey("migration_target_uri")] = UserStorage.PRIVATE_LOCATION
                it[booleanPreferencesKey("migration_failed")] = true
            }
            val storage = UserStorage(isolated)
            storage.initialize()
            assertEquals(UserStorage.PRIVATE_LOCATION, storage.location.value)
            assertEquals(selectedUri.toString(), storage.activeLocation.value)
            assertTrue(storage.transferFiles { })
            assertEquals(UserStorage.PRIVATE_LOCATION, storage.activeLocation.value)
            assertEquals(privateDownloads.absolutePath, storage.downloads().absolutePath)
            assertEquals("Preserved text", NovelDownloadStore.readChapter(chapter.url)!!.content.filterIsInstance<TextComponent>().single().text)
            assertEquals("fixture archive", File(directory, "auto_backups/$archiveName").readText())
            assertFalse(requireNotNull(sharedBook).exists())
            assertFalse(sharedArchive.exists())
            storage.reindex()
            assertEquals(1, NovelDownloadStore.listDownloadedNovels().size)
            storage.prepareSetup(restoredSession = true)
            assertEquals(UserStorage.PRIVATE_LOCATION, storage.location.value)
        } finally {
            NovelDownloadStore.finishStorageMigration()
            sharedBook?.deleteRecursively()
            sharedArchive.delete()
            tree.deleteRecursively()
            directory.deleteRecursively()
            val container = EsjzoneApplication.instance.container
            container.initializeDownloads()
            NovelDownloadStore.restoreStorage(container.storage.downloads())
        }
    }

    @Test fun committedAndRestoredCleanupProtectTargetsUntilTheJournalClears() = runBlocking {
        for (restored in listOf(false, true)) verifyCleanupProtection(restored)
    }

    private suspend fun verifyCleanupProtection(restored: Boolean) {
        context.contentResolver.call(treeUri, "grant-fixture-tree", null, null)
        val directory = File(context.cacheDir, "cleanup-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated = isolatedContext(directory)
        val id = UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
        val novel = "https://www.esjzone.cc/detail/$id.html"
        val chapter = Chapter("Chapter", "https://www.esjzone.cc/forum/$id/1.html", false)
        val target = StorageFile(StorageFile.fromTree(context, treeUri), "downloads")
        var copied: StorageFile? = null
        try {
            NovelDownloadStore.initialize(context)
            NovelDownloadStore.initializeDirectory(File(directory, "downloaded_novels"))
            requireNotNull(NovelDownloadStore.saveChapter("Journal $id", novel, "", listOf(chapter), chapter,
                DetailedChapter("Chapter", listOf(TextComponent("Saved text")), null, null),
                Authorization("", "", "www.esjzone.cc")))
            val original = StorageFile(File(directory, "downloaded_novels").listFiles()!!.single())
            val migration = if (restored) {
                NovelDownloadStore.migrateStorage(target, holdWritesUntilCommit = true) { }
            } else null
            copied = StorageFile(target, migration?.targets?.getValue(original.name) ?: original.name)
            seed(isolated) {
                if (migration != null) {
                    it[stringPreferencesKey("directory_uri")] = treeUri.toString()
                    it[stringPreferencesKey("migration_cleanup_source")] = ""
                    it[stringSetPreferencesKey("migration_cleanup_books")] = setOf(original.name)
                    it[stringSetPreferencesKey("migration_cleanup_targets")] =
                        migration.targets.map { (source, destination) -> "$source/$destination" }.toSet()
                } else {
                    it[stringPreferencesKey("migration_target_uri")] = treeUri.toString()
                }
                // Prevent automatic enqueue: this test drives the resumed worker explicitly.
                it[booleanPreferencesKey("migration_failed")] = true
            }
            NovelDownloadStore.finishStorageMigration() // Model losing the in-memory barrier on restart.
            val storage = UserStorage(isolated)
            storage.initialize()
            assertEquals(treeUri.toString(), storage.location.value)
            assertEquals(if (restored) treeUri.toString() else "", storage.activeLocation.value)
            // The selected location is already the target. Observers must still receive the commit.
            val activeRootChanged = CoroutineScope(currentCoroutineContext()).async(start = CoroutineStart.UNDISPATCHED) {
                storage.activeLocation.first { it == treeUri.toString() }
                storage.backups().absolutePath
            }
            assertTrue(NovelDownloadStore.migrating.value)
            storage.retainReader()
            try {
                assertFalse(storage.transferFiles { })
                val expectedBackups = StorageFile(StorageFile.fromTree(context, treeUri), "autobackup")
                assertEquals(expectedBackups.absolutePath, withTimeout(1_000) { activeRootChanged.await() })
            } finally { activeRootChanged.cancelAndJoin() }
            assertEquals(treeUri.toString(), storage.activeLocation.value)
            assertEquals(StorageTransferPhase.WAITING_FOR_READER, storage.transfer.value.phase)
            assertTrue(NovelDownloadStore.migrating.value)
            assertFalse(NovelDownloadStore.delete(novel))
            assertEquals(0, NovelDownloadStore.deleteAll(listOf(novel)))
            assertNotNull(NovelDownloadStore.readChapter(chapter.url))
            storage.recordTransferFailure()
            assertEquals(StorageTransferPhase.CLEANUP_FAILED, storage.transfer.value.phase)
            assertTrue(NovelDownloadStore.migrating.value)
            assertFalse(NovelDownloadStore.delete(novel))
            storage.releaseReader()
            assertTrue(storage.transferFiles { })
            assertFalse(original.exists())
            assertFalse(NovelDownloadStore.migrating.value)
            assertTrue(NovelDownloadStore.delete(novel))
        } finally {
            NovelDownloadStore.finishStorageMigration()
            copied?.deleteRecursively()
            directory.deleteRecursively()
            val container = EsjzoneApplication.instance.container
            container.initializeDownloads()
            NovelDownloadStore.restoreStorage(container.storage.downloads())
        }
    }
}

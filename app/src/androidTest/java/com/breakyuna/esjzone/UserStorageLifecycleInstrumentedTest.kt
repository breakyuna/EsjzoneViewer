package com.breakyuna.esjzone

import android.content.Context
import android.content.ContextWrapper
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.storage.StorageFile
import com.breakyuna.esjzone.storage.StorageTransferException
import com.breakyuna.esjzone.storage.StorageTransferFailure
import com.breakyuna.esjzone.storage.StorageTransferFailureReason
import com.breakyuna.esjzone.storage.StorageTransferPhase
import com.breakyuna.esjzone.storage.UserStorage
import com.breakyuna.esjzone.storage.message
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
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
            val reported = mutableListOf<com.breakyuna.esjzone.storage.StorageTransferProgress>()
            assertTrue(storage.transferFiles { reported += it })
            assertTrue(reported.any { it.phase == StorageTransferPhase.COPYING })
            assertTrue(reported.any { it.phase == StorageTransferPhase.VERIFYING &&
                it.total > 0 && it.completed == it.total })
            assertTrue(reported.any { it.phase == StorageTransferPhase.CLEANING })
            assertEquals(StorageTransferPhase.DONE, reported.last().phase)
            assertEquals(reported.last(), storage.transfer.value)
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

    @Test fun failureReasonAndConflictingPathSurviveRestartAndUsePhaseSpecificGuidance() = runBlocking {
        for (cleanup in listOf(false, true)) {
            val directory = File(context.cacheDir, "failure-${UUID.randomUUID()}").apply { mkdirs() }
            val restartedDirectory = File(context.cacheDir, "failure-restored-${UUID.randomUUID()}").apply { mkdirs() }
            try {
                val isolated = isolatedContext(directory)
                seed(isolated) {
                    it[booleanPreferencesKey("migration_failed")] = true
                    if (cleanup) {
                        it[stringPreferencesKey("migration_cleanup_source")] = ""
                        it[stringPreferencesKey("directory_uri")] = UserStorage.PRIVATE_LOCATION
                    } else it[stringPreferencesKey("migration_target_uri")] = UserStorage.PRIVATE_LOCATION
                }
                val failure = StorageTransferFailure(StorageTransferFailureReason.TARGET_MANIFEST_CONFLICT,
                    "downloads/Novel/manifest.json")
                val storage = UserStorage(isolated)
                storage.initialize()
                storage.recordTransferFailure(StorageTransferException(failure))
                val restartedContext = isolatedContext(restartedDirectory)
                val copied = restartedContext.preferencesDataStoreFile("user_storage.preferences_pb")
                copied.parentFile!!.mkdirs()
                isolated.preferencesDataStoreFile("user_storage.preferences_pb").copyTo(copied)
                val restarted = UserStorage(restartedContext)
                restarted.initialize()
                assertEquals(failure, restarted.transfer.value.failure)
                assertEquals(if (cleanup) StorageTransferPhase.CLEANUP_FAILED else StorageTransferPhase.FAILED,
                    restarted.transfer.value.phase)
                val message = restarted.transfer.value.message(context)
                assertTrue(message.contains(failure.item))
                assertTrue(message.contains(context.getString(if (cleanup) R.string.storage_failure_cleanup_conflict
                    else R.string.storage_failure_manifest_conflict)))
            } finally {
                NovelDownloadStore.finishStorageMigration()
                directory.deleteRecursively()
                restartedDirectory.deleteRecursively()
            }
        }
    }
}

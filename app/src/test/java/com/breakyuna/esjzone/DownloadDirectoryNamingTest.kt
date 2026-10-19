package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.storage.StorageFile
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class DownloadDirectoryNamingTest {
    private fun novel(id: Int) = "/detail/$id.html"
    private fun chapter(id: Int) = Chapter("Chapter", "/forum/$id/1.html", false)
    private fun save(name: String, id: Int, text: String = "正文 $id") = requireNotNull(
        NovelDownloadStore.saveChapter(name, novel(id), "", listOf(chapter(id)), chapter(id),
            DetailedChapter("Chapter", listOf(TextComponent(text)), null, null),
            Authorization("", "", "www.esjzone.cc"))
    )

    private fun withStorage(block: (File) -> Unit) {
        val root = Files.createTempDirectory("download-names-").toFile()
        try {
            NovelDownloadStore.initializeDirectory(root)
            block(root)
        } finally { root.deleteRecursively() }
    }

    @Test fun readableNamesKeepBookIdentityAcrossMirrorsAndTitleChanges() = withStorage { root ->
        save("人类可读的书名", 81001)
        assertEquals("人类可读的书名", root.listFiles()!!.single().name)
        save("作品的新名字", 81001)
        assertEquals("人类可读的书名", root.listFiles()!!.single().name)
        assertEquals("作品的新名字", NovelDownloadStore.manifest("https://www.esjzone.one/detail/81001.html")!!.name)
        assertNotNull(NovelDownloadStore.readChapter(chapter(81001).url))
        assertTrue(NovelDownloadStore.delete(novel(81001)))
        assertTrue(root.listFiles()!!.isEmpty())
    }

    @Test fun duplicateAndSanitizedTitlesNeverMergeBooksOrOverwriteUnrelatedFiles() = withStorage { root ->
        File(root, "同名作品").writeText("unrelated file")
        save("同名作品", 81001)
        save("同名作品", 81002)
        save("目录/书名", 81003)
        save("目录\\书名", 81004)
        assertEquals("unrelated file", File(root, "同名作品").readText())
        val folders = root.listFiles()!!.filter(File::isDirectory)
        assertEquals(4, folders.map(File::getName).distinct().size)
        assertEquals(2, folders.count { it.name.startsWith("同名作品 (") })
        assertTrue(folders.any { it.name == "目录_书名" })
        for (id in 81001..81004) assertEquals("正文 $id",
            NovelDownloadStore.readChapter(chapter(id).url)!!.content.filterIsInstance<TextComponent>().single().text)
        assertTrue(NovelDownloadStore.delete(novel(81001)))
        assertNotNull(NovelDownloadStore.readChapter(chapter(81002).url))
    }

    @Test fun deletedBookCannotAccessDeleteOrOverwriteAReusedTitleDirectory() {
        for (batch in listOf(false, true)) withStorage { root ->
            save("同名作品", 81001)
            val deletedUrl = "https://www.esjzone.one${novel(81001)}"
            if (batch) assertEquals(1, NovelDownloadStore.deleteAll(listOf(deletedUrl)))
            else assertTrue(NovelDownloadStore.delete(deletedUrl))
            assertTrue(root.listFiles()!!.isEmpty())

            // Reuse the path before any lookup can discard the deleted book's old mapping.
            save("同名作品", 81002)
            assertEquals("同名作品", root.listFiles()!!.single().name)
            assertNull(NovelDownloadStore.manifest(novel(81001)))
            if (batch) assertEquals(0, NovelDownloadStore.deleteAll(listOf(deletedUrl)))
            else assertTrue(NovelDownloadStore.delete(deletedUrl))
            assertEquals(novel(81002), NovelDownloadStore.manifest(novel(81002))!!.url)
            assertEquals("正文 81002", NovelDownloadStore.readChapter(chapter(81002).url)!!
                .content.filterIsInstance<TextComponent>().single().text)

            save("同名作品", 81001, "重新下载的正文")
            assertEquals(2, root.listFiles()!!.size)
            assertEquals(listOf(chapter(81002).url),
                NovelDownloadStore.manifest(novel(81002))!!.chapters.map { it.url })
            assertEquals("正文 81002", NovelDownloadStore.readChapter(chapter(81002).url)!!
                .content.filterIsInstance<TextComponent>().single().text)
            assertEquals("重新下载的正文", NovelDownloadStore.readChapter(chapter(81001).url)!!
                .content.filterIsInstance<TextComponent>().single().text)
        }
    }

    @Test fun legacyHashFoldersBecomeReadableWithoutChangingChapterFiles() = withStorage { root ->
        val manifest = save("旧下载书名", 81001)
        val chapterFile = manifest.chapters.single().fileName
        val hash = MessageDigest.getInstance("SHA-256").digest(novel(81001).toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val legacy = File(root, hash)
        check(root.listFiles()!!.single().renameTo(legacy))
        val other = Files.createTempDirectory("download-names-other-").toFile()
        try {
            // A new location followed by the original models a fresh store initialization.
            NovelDownloadStore.initializeDirectory(other)
            NovelDownloadStore.initializeDirectory(root)
            assertNotNull(NovelDownloadStore.readChapter(chapter(81001).url))
            assertFalse(legacy.exists())
            assertTrue(File(root, "旧下载书名/$chapterFile").isFile)
            assertEquals(1, NovelDownloadStore.listDownloadedNovels().size)
        } finally { other.deleteRecursively() }
    }

    @Test fun rescanFindsManuallyRenamedFoldersAndBackupImportUsesManifestIdentity() = withStorage { root ->
        save("作品.tmp", 81001)
        val renamed = File(root, "手动整理后的书名")
        check(root.listFiles()!!.single().renameTo(renamed))
        NovelDownloadStore.reindex()
        assertNotNull(NovelDownloadStore.readChapter(chapter(81001).url))
        val backup = Files.createTempDirectory("download-names-backup-").toFile()
        val restored = Files.createTempDirectory("download-names-restored-").toFile()
        try {
            NovelDownloadStore.exportBackup(backup)
            // Old archives may have hash or arbitrary folder names; the manifest owns identity.
            check(backup.listFiles()!!.single().renameTo(File(backup, "old-archive-folder")))
            NovelDownloadStore.initializeDirectory(restored)
            NovelDownloadStore.importBackup(backup)
            assertEquals("作品.tmp", restored.listFiles()!!.single().name)
            assertNotNull(NovelDownloadStore.readChapter(chapter(81001).url))
            save("作品新版名称", 81001)
            NovelDownloadStore.importBackup(backup)
            assertEquals(1, restored.listFiles()!!.size)
            assertEquals("作品新版名称", NovelDownloadStore.manifest(novel(81001))!!.name)
        } finally { backup.deleteRecursively(); restored.deleteRecursively() }
    }

    @Test fun migrationSeparatesDuplicateTitlesAndCanReuseTheSameLibraryOnRetry() = withStorage { root ->
        save("同名作品", 81001)
        save("同名作品", 81002)
        val target = Files.createTempDirectory("download-names-migrated-").toFile()
        try {
            val migration = NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            assertEquals(2, migration.books.size)
            assertEquals(2, target.listFiles()!!.size)
            assertTrue(target.listFiles()!!.all { it.name.startsWith("同名作品") })
            for (id in 81001..81002) assertNotNull(NovelDownloadStore.readChapter(chapter(id).url))
            // Simulate a failed preference write: source files survive, and retry reuses copies.
            NovelDownloadStore.restoreStorage(StorageFile(root))
            NovelDownloadStore.migrateStorage(StorageFile(target)) { }
            assertEquals(2, target.listFiles()!!.size)
            assertEquals(2, NovelDownloadStore.listDownloadedNovels().size)
        } finally { target.deleteRecursively() }
    }

    @Test fun explicitBackupReplacementRemovesExtraNovelsAndValidatesBeforeDeleting() = withStorage { root ->
        val backup = Files.createTempDirectory("replacement-backup-").toFile()
        try {
            save("原作品", 81001, "备份中的正文")
            NovelDownloadStore.exportBackup(backup)
            save("原作品", 81001, "当前的正文")
            save("额外作品", 81002)
            NovelDownloadStore.importBackup(backup, replaceExisting = true)
            assertEquals("备份中的正文", NovelDownloadStore.readChapter(chapter(81001).url)!!
                .content.filterIsInstance<TextComponent>().single().text)
            assertNull(NovelDownloadStore.manifest(novel(81002)))
            assertEquals(1, NovelDownloadStore.listDownloadedNovels().size)

            save("保留作品", 81002)
            File(backup.listFiles()!!.single(), "manifest.json").writeText("invalid")
            assertThrows(IllegalArgumentException::class.java) {
                NovelDownloadStore.importBackup(backup, replaceExisting = true)
            }
            assertNotNull(NovelDownloadStore.readChapter(chapter(81001).url))
            assertNotNull(NovelDownloadStore.readChapter(chapter(81002).url))

            backup.deleteRecursively()
            check(backup.mkdirs())
            NovelDownloadStore.importBackup(backup, replaceExisting = true)
            assertTrue(NovelDownloadStore.listDownloadedNovels().isEmpty())
            assertTrue(root.listFiles().orEmpty().none { it.isDirectory })
        } finally { backup.deleteRecursively() }
    }

    @Test fun longAndUnsafeTitlesRemainSingleSafeUtf8Components() {
        for (title in listOf(" ../危险\\目录:*?\"<>|\n", ".", "..", "   ", "长书名😀".repeat(100))) {
            val name = NovelDownloadStore.novelDirectoryName(title)
            assertTrue(name.isNotBlank() && name != "." && name != "..")
            assertFalse(name.startsWith('.'))
            assertTrue(name.none { it < ' ' || it in "<>:\"/\\|?*" })
            assertTrue(name.toByteArray(Charsets.UTF_8).size <= 180)
            assertFalse(Character.isHighSurrogate(name.last()))
        }
    }
}

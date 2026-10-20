package com.breakyuna.esjzone.ui.page

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
import com.breakyuna.esjzone.R
import java.io.File
import java.nio.file.Files

/** Called on IO. System totals include installation artifacts and unclassified app data. */
internal fun readStorageUsage(context: Context): List<StorageUsage> {
    val storageManager = requireNotNull(context.getSystemService(StorageManager::class.java))
    val statsManager = requireNotNull(context.getSystemService(StorageStatsManager::class.java))
    val volumes = (listOf(context.dataDir) + context.getExternalFilesDirs(null).filterNotNull() +
        context.externalCacheDirs.filterNotNull() + context.externalMediaDirs.filterNotNull())
        .filter { it.exists() }
        .map { storageManager.getUuidForPath(it) }.distinct()
    val totals = volumes.map { statsManager.queryStatsForUid(it, Process.myUid()) }
    val dataBytes = totals.sumOf { it.dataBytes }
    val appInfo = context.applicationInfo
    val apkBytes = (listOf(appInfo.sourceDir) + appInfo.splitSourceDirs.orEmpty())
        .distinct().sumOf { directoryBytes(File(it)) }
    val privateUsage = classifyStorageUsage(context.dataDir, context.filesDir, context.cacheDir,
        totals.sumOf { it.appBytes }, apkBytes, dataBytes)
    val storage = com.breakyuna.esjzone.EsjzoneApplication.instance.container.storage
    if (!storage.downloads().isDocument) return privateUsage
    // Shared files are outside the UID totals; add them after calculating private "other data".
    fun sharedParts(directory: com.breakyuna.esjzone.storage.StorageFile, labels: List<Int>,
                    labelFor: (com.breakyuna.esjzone.storage.StorageFile) -> Int): List<StorageUsagePart> {
        val bytes = labels.associateWith { 0L }.toMutableMap()
        directory.fileEntries().forEach { entry ->
            val label = labelFor(entry.file)
            bytes[label] = bytes.getValue(label) + entry.size
        }
        return labels.map { StorageUsagePart(it, bytes.getValue(it)) }
    }
    val downloads = sharedParts(storage.downloads(), listOf(R.string.storage_download_chapters,
        R.string.storage_download_images, R.string.storage_download_records)) { file ->
        when {
            file.parentFile?.name == "images" || file.name == "cover.jpg" -> R.string.storage_download_images
            file.name.startsWith("chapter-") && file.extension == "json" -> R.string.storage_download_chapters
            else -> R.string.storage_download_records
        }
    }
    val backups = sharedParts(storage.backups(), listOf(R.string.storage_backup_archives,
        R.string.storage_backup_pending)) { file ->
        if (com.breakyuna.esjzone.storage.UserStorage.isAutoBackupName(file.name)) R.string.storage_backup_archives
        else R.string.storage_backup_pending
    }
    return privateUsage.map { entry ->
        val additional = when (entry.category) {
            StorageCategory.DOWNLOADS -> downloads
            StorageCategory.BACKUPS -> backups
            else -> emptyList()
        }.associateBy { it.labelRes }
        entry.copy(parts = entry.parts.map { part -> part.copy(bytes = part.bytes + (additional[part.labelRes]?.bytes ?: 0L)) })
    }
}

/** Each file belongs to one detail; category totals and ring segments share these values. */
internal fun classifyStorageUsage(
    data: File, files: File, cache: File, appBytes: Long, apkBytes: Long, dataBytes: Long
): List<StorageUsage> {
    fun part(label: Int, file: File) = StorageUsagePart(label, directoryBytes(file))
    val installation = apkBytes.coerceIn(0L, appBytes)
    val downloads = directoryParts(File(files, "downloaded_novels"), listOf(
        R.string.storage_download_chapters, R.string.storage_download_images, R.string.storage_download_records
    )) { file ->
        when {
            file.parentFile?.name == "images" || file.name == "cover.jpg" -> R.string.storage_download_images
            file.name.startsWith("chapter-") && file.extension == "json" -> R.string.storage_download_chapters
            else -> R.string.storage_download_records
        }
    }
    val backups = directoryParts(File(files, "auto_backups"), listOf(
        R.string.storage_backup_archives, R.string.storage_backup_pending
    )) { file ->
        if (file.name.startsWith("esjzone-auto-") && file.extension == "zip") R.string.storage_backup_archives
        else R.string.storage_backup_pending
    }
    val usage = listOf(
        StorageUsage(StorageCategory.APPLICATION, listOf(
            StorageUsagePart(R.string.storage_installation_apk, installation),
            StorageUsagePart(R.string.storage_installation_other, appBytes - installation))),
        StorageUsage(StorageCategory.DOWNLOADS, downloads),
        StorageUsage(StorageCategory.CACHE, listOf(
            part(R.string.storage_page_cache, File(files, "novel_page_cache")),
            part(R.string.settings_image_cache, File(cache, "image_cache")),
            part(R.string.cache_layer_home, File(files, "home_data_snapshot.json")),
            part(R.string.cache_layer_history, File(files, "history_cache")))),
        StorageUsage(StorageCategory.BACKUPS, backups),
        StorageUsage(StorageCategory.READING_RESOURCES, listOf(
            part(R.string.storage_bookshelf_covers, File(files, "bookshelf_covers")),
            part(R.string.storage_reader_fonts, File(files, "reader_fonts")))),
        StorageUsage(StorageCategory.APP_DATA, listOf(
            part(R.string.storage_databases, File(data, "databases")),
            part(R.string.storage_webview, File(data, "app_webview")),
            StorageUsagePart(R.string.storage_settings, directoryBytes(File(data, "shared_prefs")) +
                directoryBytes(File(files, "datastore"))),
            part(R.string.storage_analysis_log, File(files, "cache_analysis"))))
    )
    val classifiedData = usage.filter { it.category != StorageCategory.APPLICATION }.sumOf { it.bytes }
    // Files may change while scanning; never produce a negative remainder or slice.
    return usage.map { entry ->
        if (entry.category == StorageCategory.APP_DATA) entry.copy(parts = entry.parts +
            StorageUsagePart(R.string.storage_other_data, (dataBytes - classifiedData).coerceAtLeast(0L)))
        else entry
    }
}

internal fun directoryBytes(file: File): Long {
    var bytes = 0L
    forEachStorageFile(file) { bytes += it.length() }
    return bytes
}

private fun directoryParts(file: File, labels: List<Int>, labelFor: (File) -> Int): List<StorageUsagePart> {
    val bytes = labels.associateWith { 0L }.toMutableMap()
    forEachStorageFile(file) { child ->
        val label = labelFor(child)
        bytes[label] = bytes.getValue(label) + child.length()
    }
    return labels.map { StorageUsagePart(it, bytes.getValue(it)) }
}

private fun forEachStorageFile(file: File, action: (File) -> Unit) {
    if (Files.isSymbolicLink(file.toPath())) return
    if (file.isFile) { action(file); return }
    if (!file.exists()) return
    val children = file.listFiles() ?: run {
        // A directory may disappear between the existence check and listing it.
        if (!file.exists()) return
        throw java.io.IOException("Unable to calculate storage usage")
    }
    children.forEach { forEachStorageFile(it, action) }
}

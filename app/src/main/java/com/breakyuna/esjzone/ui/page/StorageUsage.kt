package com.breakyuna.esjzone.ui.page

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager
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
    val files = context.filesDir
    val usage = listOf(
        StorageUsage(StorageCategory.APPLICATION, totals.sumOf { it.appBytes }),
        StorageUsage(StorageCategory.DOWNLOADS, directoryBytes(File(files, "downloaded_novels"))),
        StorageUsage(StorageCategory.CACHE, directoryBytes(File(files, "novel_page_cache"))),
        StorageUsage(StorageCategory.CACHE, directoryBytes(File(context.cacheDir, "image_cache"))),
        StorageUsage(StorageCategory.READING_RESOURCES, directoryBytes(File(files, "bookshelf_covers"))),
        StorageUsage(StorageCategory.READING_RESOURCES, directoryBytes(File(files, "reader_fonts"))),
        StorageUsage(StorageCategory.BACKUPS, directoryBytes(File(files, "auto_backups"))),
        StorageUsage(StorageCategory.APP_DATA, directoryBytes(File(context.dataDir, "databases"))),
        StorageUsage(StorageCategory.CACHE, directoryBytes(File(files, "history_cache")) +
            directoryBytes(File(files, "home_data_snapshot.json"))),
        StorageUsage(StorageCategory.APP_DATA, directoryBytes(File(context.dataDir, "app_webview"))),
        StorageUsage(StorageCategory.APP_DATA, directoryBytes(File(context.dataDir, "shared_prefs")) +
            directoryBytes(File(files, "datastore")))
    )
    val classifiedData = usage.filter { it.category != StorageCategory.APPLICATION }.sumOf { it.bytes }
    // Files may change while scanning; never produce a negative remainder or slice.
    val combined = usage + StorageUsage(StorageCategory.APP_DATA, (dataBytes - classifiedData).coerceAtLeast(0L))
    return StorageCategory.entries.map { category ->
        StorageUsage(category, combined.filter { it.category == category }.sumOf { it.bytes })
    }
}

internal fun directoryBytes(file: File): Long {
    if (Files.isSymbolicLink(file.toPath())) return 0L
    if (file.isFile) return file.length()
    if (!file.exists()) return 0L
    val children = file.listFiles() ?: run {
        // A directory may disappear between the existence check and listing it.
        if (!file.exists()) return 0L
        throw java.io.IOException("Unable to calculate storage usage")
    }
    return children.sumOf(::directoryBytes)
}

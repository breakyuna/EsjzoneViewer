package com.breakyuna.esjzone.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.breakyuna.esjzone.data.settings.ReaderSettingsDataStore
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.*
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.ui.reader.ReaderImageBackgroundStore
import com.breakyuna.esjzone.util.copyWithCancellation
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.io.File
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class BackupCategory {
    UNDERLINES, BOOKMARKS, DOWNLOADS, READING, HISTORY, SEARCH, GROUPS, BOOKSHELF, READER_SETTINGS
}

data class BackupGroups(val names: List<String>, val members: Map<String, String>)

private data class BackupShelfBook(
    @SerializedName("bookKey") val bookKey: String,
    @SerializedName("novelId") val novelId: String,
    @SerializedName("url") val url: String,
    @SerializedName("title") val title: String,
    @SerializedName("author") val author: String,
    @SerializedName("coverUrl") val coverUrl: String,
    @SerializedName("isAdult") val isAdult: Boolean,
    @SerializedName("addedAt") val addedAt: Long
) {
    constructor(row: BookshelfEntry) : this(
        row.bookKey, row.novelId, row.url, row.title, row.author, row.coverUrl, row.isAdult, row.addedAt
    )

    fun toEntry(scope: String) = BookshelfEntry(
        scope = scope, bookKey = bookKey, novelId = novelId, url = url, title = title, author = author,
        coverUrl = coverUrl, isAdult = isAdult, addedAt = addedAt
    )
}

/** Versioned, explicit allowlist: never serializes the database or account settings wholesale. */
object LocalBackup {
    private val gson = Gson()
    private const val BACKGROUND_PHOTO = "reader-background.jpg"
    private const val MAX_BACKGROUND_BYTES = 32L * 1024 * 1024

    suspend fun export(
        context: Context,
        uri: Uri,
        database: GeneralDatabase,
        scope: String,
        selected: Set<BackupCategory>,
        readerSettings: ReaderSettingsDataStore? = null
    ) = withContext(Dispatchers.IO) {
        val transferContext = currentCoroutineContext()
        val checkActive = { transferContext.ensureActive() }
        checkActive()
        require(selected.isNotEmpty())
        val staging = createStaging(context)
        try {
            val json = JsonObject().apply {
                addProperty("format", "esjzone-local-backup")
                val hasNewCategories = BackupCategory.BOOKSHELF in selected || BackupCategory.READER_SETTINGS in selected
                addProperty("version", if (hasNewCategories) 3 else 2)
            }
            database.withTransaction {
                checkActive()
                if (BackupCategory.UNDERLINES in selected) json.add("underlines", gson.toJsonTree(
                    database.cacheDao().getReaderUnderlines().associate { it.key to it.value }))
                if (BackupCategory.BOOKMARKS in selected) json.add("bookmarks", gson.toJsonTree(database.bookmarkDao().getAll()))
                if (BackupCategory.HISTORY in selected) json.add("history", gson.toJsonTree(database.localReadingActivityDao().getAll()))
                if (BackupCategory.READING in selected) {
                    json.add("reading", gson.toJsonTree(database.readingStatDao().getAll()))
                    json.add("readingTags", gson.toJsonTree(database.cacheDao().getReadingTags().associate { it.key to it.value }))
                }
                if (BackupCategory.SEARCH in selected) json.add("search", gson.toJsonTree(database.searchHistoryDao().getAll()))
                if (BackupCategory.BOOKSHELF in selected) {
                    val books = database.bookshelfDao().getAll(scope).filter { it.visible }.map(::BackupShelfBook)
                    json.add("bookshelf", gson.toJsonTree(books))
                }
                if (BackupCategory.GROUPS in selected) {
                    val dao = database.bookshelfGroupDao()
                    json.add("groups", gson.toJsonTree(BackupGroups(dao.groups(scope).map { it.name },
                        dao.members(scope).associate { it.bookKey to it.groupName })))
                }
            }
            if (BackupCategory.DOWNLOADS in selected) {
                json.addProperty("downloads", true)
            }
            val photo = File(staging, BACKGROUND_PHOTO)
            if (BackupCategory.READER_SETTINGS in selected) {
                ReaderImageBackgroundStore.savedPhotoMutex.withLock {
                    val (settings, appearance) = requireNotNull(readerSettings).backupSnapshot()
                    val source = ReaderImageBackgroundStore.file(context, appearance)
                    if (source != null && source.isFile && source.length() > 0L) {
                        source.inputStream().buffered().use { input ->
                            photo.outputStream().buffered().use { input.copyWithCancellation(it, checkActive) }
                        }
                        settings.addProperty("hasBackgroundPhoto", true)
                    }
                    json.add("readerSettings", settings)
                }
            }
            File(staging, "backup.json").writeText(gson.toJson(json))
            ZipOutputStream(checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                .buffered(64 * 1024)).use { zip ->
                fun appendFile(file: File) {
                    appendZipFile(zip, file, file.relativeTo(staging).invariantSeparatorsPath, checkActive)
                }
                appendFile(File(staging, "backup.json"))
                if (photo.isFile) appendFile(photo)
                if (BackupCategory.DOWNLOADS in selected) {
                    NovelDownloadStore.exportBackup(File(staging, "downloads"), checkActive) { snapshot ->
                        try {
                            snapshot.walkTopDown().filter(File::isFile).forEach(::appendFile)
                        } finally { snapshot.deleteRecursively() }
                    }
                }
            }
        } finally { staging.deleteRecursively() }
    }

    internal fun appendZipFile(zip: ZipOutputStream, file: File, name: String, checkActive: () -> Unit = {}) {
        checkActive()
        file.inputStream().buffered(64 * 1024).use { input ->
            input.mark(2)
            val gzip = input.read() == 0x1f && input.read() == 0x8b
            input.reset()
            // Level zero avoids recompressing gzip/images and needs no extra CRC pre-read.
            zip.setLevel(if (gzip || "/images/" in name) Deflater.NO_COMPRESSION else Deflater.DEFAULT_COMPRESSION)
            zip.putNextEntry(ZipEntry(name))
            input.copyWithCancellation(zip, checkActive)
            zip.closeEntry()
        }
    }

    suspend fun restore(
        context: Context,
        uri: Uri,
        database: GeneralDatabase,
        scope: String,
        selected: Set<BackupCategory>,
        readerSettings: ReaderSettingsDataStore? = null
    ) = withContext(Dispatchers.IO) {
        val transferContext = currentCoroutineContext()
        val checkActive = { transferContext.ensureActive() }
        checkActive()
        require(selected.isNotEmpty())
        val staging = createStaging(context)
        try {
            ZipInputStream(checkNotNull(context.contentResolver.openInputStream(uri))).use { zip ->
                val seen = HashSet<String>()
                val limit = (staging.usableSpace - 64L * 1024 * 1024).coerceAtLeast(0)
                var extracted = 0L
                while (true) {
                    checkActive()
                    val entry = zip.nextEntry ?: break
                    val target = safeEntry(staging, entry.name)
                    require(seen.add(entry.name) && seen.size <= 200_000)
                    if ((entry.name.startsWith("downloads/") && BackupCategory.DOWNLOADS !in selected) ||
                        (entry.name == BACKGROUND_PHOTO && BackupCategory.READER_SETTINGS !in selected)
                    ) {
                        // Drain skipped data with cancellation points.
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            checkActive()
                            if (zip.read(buffer) < 0) break
                        }
                        zip.closeEntry()
                        continue
                    }
                    if (!entry.isDirectory) {
                        var entryBytes = 0L
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                checkActive()
                                val count = zip.read(buffer)
                                checkActive()
                                if (count < 0) break
                                extracted += count
                                require(extracted <= limit)
                                if (entry.name == BACKGROUND_PHOTO) {
                                    entryBytes += count
                                    require(entryBytes <= MAX_BACKGROUND_BYTES)
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            checkActive()
            val json = JsonParser.parseString(File(staging, "backup.json").readText()).asJsonObject
            require(json["format"]?.asString == "esjzone-local-backup" && json["version"]?.asInt in 1..3)
            require(selected.any { category -> json.has(when (category) {
                BackupCategory.UNDERLINES -> "underlines"
                BackupCategory.BOOKMARKS -> "bookmarks"
                BackupCategory.DOWNLOADS -> "downloads"
                BackupCategory.READING -> "reading"
                BackupCategory.HISTORY -> "history"
                BackupCategory.SEARCH -> "search"
                BackupCategory.GROUPS -> "groups"
                BackupCategory.BOOKSHELF -> "bookshelf"
                BackupCategory.READER_SETTINGS -> "readerSettings"
            }) })
            val underlines = if (BackupCategory.UNDERLINES in selected && json.has("underlines"))
                json.getAsJsonObject("underlines").entrySet().associate { (key, value) ->
                    require(key.startsWith(ReaderUnderlines.KEY_PREFIX) && key.length > ReaderUnderlines.KEY_PREFIX.length)
                    key to ReaderUnderlines.decode(value.asString)
                } else emptyMap()
            val bookmarks = if (BackupCategory.BOOKMARKS in selected && json.has("bookmarks"))
                gson.fromJson(json["bookmarks"], Array<Bookmark>::class.java).toList() else emptyList()
            val history = if (BackupCategory.HISTORY in selected && json.has("history"))
                gson.fromJson(json["history"], Array<LocalReadingActivity>::class.java).toList() else emptyList()
            val reading = if (BackupCategory.READING in selected && json.has("reading"))
                gson.fromJson(json["reading"], Array<ReadingStat>::class.java).toList() else emptyList()
            val tags = if (BackupCategory.READING in selected && json.has("readingTags")) json.getAsJsonObject("readingTags") else JsonObject()
            require(tags.keySet().all { it.startsWith(com.breakyuna.esjzone.database.ReadingStatisticsRecorder.TAGS_KEY_PREFIX) })
            tags.entrySet().forEach { (_, value) ->
                require(JsonParser.parseString(value.asString).asJsonArray.all { it.isJsonPrimitive && it.asJsonPrimitive.isString })
            }
            val search = if (BackupCategory.SEARCH in selected && json.has("search"))
                gson.fromJson(json["search"], Array<SearchHistory>::class.java).toList() else emptyList()
            val groups = if (BackupCategory.GROUPS in selected && json.has("groups"))
                gson.fromJson(json["groups"], BackupGroups::class.java) else null
            val bookshelf = if (BackupCategory.BOOKSHELF in selected && json.has("bookshelf"))
                gson.fromJson(json["bookshelf"], Array<BackupShelfBook>::class.java).map { it.toEntry(scope) }
                else emptyList()
            require(bookshelf.all { it.bookKey.isNotBlank() && it.url.isNotBlank() })
            val savedSettings = if (BackupCategory.READER_SETTINGS in selected && json.has("readerSettings"))
                json.getAsJsonObject("readerSettings") else null
            val readerStore = if (savedSettings != null) requireNotNull(readerSettings) else null
            val readerPreferences = savedSettings?.let { requireNotNull(readerStore).decodeBackup(it) }
            val photo = File(staging, BACKGROUND_PHOTO)
            val decodedPhoto = if (savedSettings?.get("hasBackgroundPhoto")?.asBoolean == true) {
                require(photo.isFile)
                ReaderImageBackgroundStore.render(photo)
            } else null

            require(bookmarks.all { it.chapterUrl.isNotBlank() })
            require(history.all { it.activityId.isNotBlank() && it.chapterProgress in 0f..1f && it.durationMs >= 0 &&
                (it.anchor == null || com.breakyuna.esjzone.domain.reader.ReaderAnchor.decode(it.anchor) != null) &&
                (it.bookProgress == null || it.bookProgress.isFinite() && it.bookProgress in 0f..1f) })
            require(reading.all { it.bookKey.isNotBlank() && it.durationMs >= 0 && runCatching { java.time.LocalDate.parse(it.date) }.isSuccess })
            require(groups == null || (groups.names.all { it.isNotBlank() } && groups.members.values.all { it in groups.names }))
            val restoreDatabase: suspend () -> Unit = {
                database.withTransaction {
                    checkActive()
                    // Only replace categories that were selected AND actually included in this archive.
                    val cache = database.cacheDao()
                    if (BackupCategory.UNDERLINES in selected && json.has("underlines"))
                        cache.getReaderUnderlines().forEach { cache.deleteByKey(it.key) }
                    if (BackupCategory.BOOKMARKS in selected && json.has("bookmarks"))
                        database.bookmarkDao().deleteAll(database.bookmarkDao().getAll())
                    if (BackupCategory.HISTORY in selected && json.has("history"))
                        database.localReadingActivityDao().deleteAll()
                    if (BackupCategory.READING in selected && json.has("reading")) {
                        database.readingStatDao().deleteAll()
                        cache.getReadingTags().forEach { cache.deleteByKey(it.key) }
                    }
                    if (BackupCategory.SEARCH in selected && json.has("search"))
                        database.searchHistoryDao().delete(*database.searchHistoryDao().getAll().toTypedArray())
                    if (groups != null) {
                        val dao = database.bookshelfGroupDao()
                        dao.ungroup(scope, dao.members(scope).map { it.bookKey })
                        dao.groups(scope).forEach { dao.deleteGroup(scope, it.name) }
                    }
                    underlines.forEach { (key, incoming) ->
                        val dao = database.cacheDao()
                        val existing = ReaderUnderlines.decode(dao.findByKey(key)?.value)
                        val merged = incoming.fold(existing) { rows, mark -> ReaderUnderlines.update(rows, mark, false) }
                        dao.putAtomic(key, ReaderUnderlines.encode(merged))
                    }
                    bookmarks.forEach { row ->
                        val old = database.bookmarkDao().findByChapterUrl(row.chapterUrl)
                        if (old == null || row.createdAt > old.createdAt) database.bookmarkDao().insert(row)
                    }
                    history.sortedBy { it.lastReadAt }.forEach { row ->
                        val dao = database.localReadingActivityDao()
                        val old = dao.getLatestForIdentity(row.novelId, row.novelUrl)
                        if (old == null || row.lastReadAt > old.lastReadAt) dao.upsertLatest(row)
                    }
                    reading.forEach(database.readingStatDao()::merge)
                    tags.entrySet().forEach { (key, value) ->
                        if (database.cacheDao().findByKey(key) == null) database.cacheDao().putAtomic(key, value.asString)
                    }
                    search.forEach { row ->
                        if (database.searchHistoryDao().findByKeyword(row.keyword) == null)
                            database.searchHistoryDao().insertAll(row.copy(index = 0))
                    }
                    if (bookshelf.isNotEmpty()) database.bookshelfDao().insertAllIfAbsent(bookshelf)
                    groups?.let { data ->
                        val dao = database.bookshelfGroupDao()
                        data.names.forEach { dao.add(BookshelfGroup(scope, it)) }
                        val existing = dao.members(scope).map { it.bookKey }.toSet()
                        data.members.filterKeys { it !in existing }.forEach { (key, name) ->
                            dao.assign(BookshelfGroupMember(scope, key, name))
                        }
                    }
                }
            }
            if (BackupCategory.DOWNLOADS in selected && json["downloads"]?.asBoolean == true) {
                NovelDownloadStore.withRestoredBackup(File(staging, "downloads"), checkActive, restoreDatabase)
            } else {
                restoreDatabase()
            }
            if (readerPreferences != null && readerStore != null) {
                ReaderImageBackgroundStore.savedPhotoMutex.withLock {
                    withContext(NonCancellable) {
                        val target = decodedPhoto?.let { ReaderImageBackgroundStore.persist(context, photo, it) }
                        try {
                            readerStore.restoreBackup(readerPreferences, target?.name)
                        } catch (error: Exception) {
                            target?.delete()
                            throw error
                        }
                        ReaderImageBackgroundStore.removeUnused(context, target)
                        readerStore.restoreWebsiteHistorySetting(readerPreferences)
                    }
                }
            }
            com.breakyuna.esjzone.database.NovelTitleRepository.requestRepair()
        } finally { staging.deleteRecursively() }
    }

    internal fun safeEntry(root: File, name: String): File {
        require(name == "backup.json" || name == BACKGROUND_PHOTO || name.startsWith("downloads/"))
        require('\\' !in name && name.split('/').none { it == ".." || it == "." })
        val target = File(root, name).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator))
        return target
    }

    private fun createStaging(context: Context): File =
        java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "local-backup-").toFile()
}

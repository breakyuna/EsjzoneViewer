package com.breakyuna.esjzone.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.*
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.domain.reader.ReaderUnderlines
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class BackupCategory { UNDERLINES, BOOKMARKS, DOWNLOADS, READING, HISTORY, SEARCH, GROUPS }

data class BackupGroups(val names: List<String>, val members: Map<String, String>)

/** Versioned, explicit allowlist: never serializes the database or account settings wholesale. */
object LocalBackup {
    private val gson = Gson()

    suspend fun export(context: Context, uri: Uri, database: GeneralDatabase, scope: String,
                       selected: Set<BackupCategory>) = withContext(Dispatchers.IO) {
        require(selected.isNotEmpty())
        val staging = createStaging(context)
        try {
            val json = JsonObject().apply {
                addProperty("format", "esjzone-local-backup")
                addProperty("version", 2)
            }
            database.withTransaction {
                if (BackupCategory.UNDERLINES in selected) json.add("underlines", gson.toJsonTree(
                    database.cacheDao().getReaderUnderlines().associate { it.key to it.value }))
                if (BackupCategory.BOOKMARKS in selected) json.add("bookmarks", gson.toJsonTree(database.bookmarkDao().getAll()))
                if (BackupCategory.HISTORY in selected) json.add("history", gson.toJsonTree(database.localReadingActivityDao().getAll()))
                if (BackupCategory.READING in selected) {
                    json.add("reading", gson.toJsonTree(database.readingStatDao().getAll()))
                    json.add("readingTags", gson.toJsonTree(database.cacheDao().getReadingTags().associate { it.key to it.value }))
                }
                if (BackupCategory.SEARCH in selected) json.add("search", gson.toJsonTree(database.searchHistoryDao().getAll()))
                if (BackupCategory.GROUPS in selected) {
                    val dao = database.bookshelfGroupDao()
                    json.add("groups", gson.toJsonTree(BackupGroups(dao.groups(scope).map { it.name },
                        dao.members(scope).associate { it.bookKey to it.groupName })))
                    val localScope = com.breakyuna.esjzone.database.BookshelfRepository.WENKU8_SCOPE
                    json.add("wenku8Groups", gson.toJsonTree(BackupGroups(dao.groups(localScope).map { it.name },
                        dao.members(localScope).associate { it.bookKey to it.groupName })))
                }
            }
            if (BackupCategory.DOWNLOADS in selected) {
                json.addProperty("downloads", true)
            }
            File(staging, "backup.json").writeText(gson.toJson(json))
            ZipOutputStream(checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))).use { zip ->
                fun appendFile(file: File) {
                    zip.putNextEntry(ZipEntry(file.relativeTo(staging).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                appendFile(File(staging, "backup.json"))
                if (BackupCategory.DOWNLOADS in selected) {
                    NovelDownloadStore.exportBackup(File(staging, "downloads")) { snapshot ->
                        try {
                            snapshot.walkTopDown().filter(File::isFile).forEach(::appendFile)
                        } finally { snapshot.deleteRecursively() }
                    }
                }
            }
        } finally { staging.deleteRecursively() }
    }

    suspend fun restore(context: Context, uri: Uri, database: GeneralDatabase, scope: String,
                        selected: Set<BackupCategory>) = withContext(Dispatchers.IO) {
        require(selected.isNotEmpty())
        val staging = createStaging(context)
        try {
            ZipInputStream(checkNotNull(context.contentResolver.openInputStream(uri))).use { zip ->
                val seen = HashSet<String>()
                val limit = (staging.usableSpace - 64L * 1024 * 1024).coerceAtLeast(0)
                var extracted = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val target = safeEntry(staging, entry.name)
                    require(seen.add(entry.name) && seen.size <= 200_000)
                    if (entry.name.startsWith("downloads/") && BackupCategory.DOWNLOADS !in selected) {
                        zip.closeEntry()
                        continue
                    }
                    if (!entry.isDirectory) {
                        target.parentFile?.mkdirs()
                        target.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val count = zip.read(buffer)
                                if (count < 0) break
                                extracted += count
                                require(extracted <= limit)
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val json = JsonParser.parseString(File(staging, "backup.json").readText()).asJsonObject
            require(json["format"]?.asString == "esjzone-local-backup" && json["version"]?.asInt in 1..2)
            require(selected.any { category -> json.has(when (category) {
                BackupCategory.UNDERLINES -> "underlines"
                BackupCategory.BOOKMARKS -> "bookmarks"
                BackupCategory.DOWNLOADS -> "downloads"
                BackupCategory.READING -> "reading"
                BackupCategory.HISTORY -> "history"
                BackupCategory.SEARCH -> "search"
                BackupCategory.GROUPS -> "groups"
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
            val wenkuGroups = if (BackupCategory.GROUPS in selected && json.has("wenku8Groups"))
                gson.fromJson(json["wenku8Groups"], BackupGroups::class.java) else null
            require(wenkuGroups == null || (wenkuGroups.names.all { it.isNotBlank() } &&
                wenkuGroups.members.values.all { it in wenkuGroups.names }))
            require(bookmarks.all { it.chapterUrl.isNotBlank() })
            require(history.all { it.activityId.isNotBlank() && it.chapterProgress in 0f..1f && it.durationMs >= 0 &&
                (it.anchor == null || com.breakyuna.esjzone.domain.reader.ReaderAnchor.decode(it.anchor) != null) &&
                (it.bookProgress == null || it.bookProgress.isFinite() && it.bookProgress in 0f..1f) })
            require(reading.all { it.bookKey.isNotBlank() && it.durationMs >= 0 && runCatching { java.time.LocalDate.parse(it.date) }.isSuccess })
            require(groups == null || (groups.names.all { it.isNotBlank() } && groups.members.values.all { it in groups.names }))
            database.withTransaction {
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
                groups?.let { data ->
                    val dao = database.bookshelfGroupDao()
                    data.names.forEach { dao.add(BookshelfGroup(scope, it)) }
                    val existing = dao.members(scope).map { it.bookKey }.toSet()
                    data.members.filterKeys { it !in existing }.forEach { (key, name) ->
                        dao.assign(BookshelfGroupMember(scope, key, name))
                    }
                }
                wenkuGroups?.let { data ->
                    val localScope = com.breakyuna.esjzone.database.BookshelfRepository.WENKU8_SCOPE
                    val dao = database.bookshelfGroupDao()
                    data.names.forEach { dao.add(BookshelfGroup(localScope, it)) }
                    val existing = dao.members(localScope).map { it.bookKey }.toSet()
                    data.members.filterKeys { it !in existing }.forEach { (key, name) ->
                        dao.assign(BookshelfGroupMember(localScope, key, name))
                    }
                }
            }
            if (BackupCategory.DOWNLOADS in selected && json["downloads"]?.asBoolean == true)
                NovelDownloadStore.importBackup(File(staging, "downloads"))
        } finally { staging.deleteRecursively() }
    }

    internal fun safeEntry(root: File, name: String): File {
        require(name == "backup.json" || name.startsWith("downloads/"))
        require('\\' !in name && name.split('/').none { it == ".." || it == "." })
        val target = File(root, name).canonicalFile
        require(target.path.startsWith(root.canonicalPath + File.separator))
        return target
    }

    private fun createStaging(context: Context): File =
        java.nio.file.Files.createTempDirectory(context.cacheDir.toPath(), "local-backup-").toFile()
}

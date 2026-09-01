package com.breakyuna.esjzone.offline

import android.content.Context
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.features.getChapterDetail
import com.breakyuna.esjzone.novellibrary.component.ChapterItem
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.analyseComponents
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedChapter
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.Gson
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Request
import org.jsoup.Jsoup

data class DownloadProgress(
    val completed: Int,
    val total: Int,
    val chapterName: String
)

data class DownloadedNovelManifest(
    val version: Int = 1,
    val name: String,
    val url: String,
    val coverUrl: String,
    val views: Int,
    val likes: Int,
    val words: Int,
    val type: String,
    val author: String,
    val forumUrl: String,
    val tags: List<String>,
    val isAdult: Boolean,
    val description: String,
    val sourceUrl: String?,
    val updatedAt: String?,
    val chapters: List<DownloadedChapterRecord>,
    val downloadedAt: Long,
    val complete: Boolean
)

data class DownloadedChapterRecord(
    val index: Int,
    val name: String,
    val url: String,
    val fileName: String,
    val downloaded: Boolean
)

data class DownloadedChapterContent(
    val name: String,
    val url: String,
    val components: List<DownloadedComponent>,
    val contentHtml: String? = null,
    val baseUrl: String? = null
)

data class DownloadedComponent(
    val type: String,
    val value: String,
    val localFile: String? = null,
    val mediaType: String? = null
)

/**
 * Persistent, user-requested novel downloads.
 *
 * This store intentionally lives outside PageCache: downloaded chapters must not disappear
 * when the user clears the temporary page cache or when that cache reaches its size limit.
 */
object NovelDownloadStore {

    private const val MANIFEST_FILE = "manifest.json"
    private const val TEXT_COMPONENT = "text"
    private const val IMAGE_COMPONENT = "image"

    private val gson = Gson()
    private val ioLock = Any()

    @Volatile
    private var rootDirectory: File? = null

    fun initialize(context: Context) {
        val directory = File(context.applicationContext.filesDir, "downloaded_novels")
        if (directory.isDirectory || directory.mkdirs()) {
            rootDirectory = directory
        } else {
            AppLogger.e("NovelDownloadStore", "Unable to create the novel download directory")
        }
    }

    fun manifest(novelUrl: String): DownloadedNovelManifest? = synchronized(ioLock) {
        readManifest(directoryFor(novelUrl, create = false))
    }

    fun isDownloaded(novelUrl: String): Boolean = manifest(novelUrl)?.complete == true

    /**
     * Downloads missing chapters and resumes an interrupted download when possible.
     * Existing chapter files are retained, while a refreshed table of contents can add chapters.
     */
    suspend fun download(
        authorization: Authorization,
        novel: DetailedNovel,
        baseUrl: String? = null,
        onProgress: (DownloadProgress) -> Unit = {}
    ): DownloadedNovelManifest {
        val orderedChapters = novel.chapterList.orderedChapters
            .distinctBy { chapterKey(it.url) }
        require(orderedChapters.isNotEmpty()) { "This novel has no downloadable chapters" }

        val directory = directoryFor(novel.url, create = true)
            ?: error("Novel download storage is unavailable")
        val previousManifest = synchronized(ioLock) { readManifest(directory) }
        val previousByUrl = previousManifest?.chapters
            .orEmpty()
            .associateBy { chapterKey(it.url) }

        var records = orderedChapters.mapIndexed { index, chapter ->
            val previous = previousByUrl[chapterKey(chapter.url)]
            val fileName = previous?.fileName ?: chapterFileName(chapter.url)
            val chapterFile = File(directory, fileName)
            DownloadedChapterRecord(
                index = index,
                name = chapter.name,
                url = chapter.url,
                fileName = fileName,
                downloaded = previous?.downloaded == true && chapterFile.isFile
            )
        }

        var currentManifest = manifestFrom(
            novel = novel,
            records = records,
            downloadedAt = previousManifest?.downloadedAt ?: 0L,
            complete = records.all { it.downloaded }
        )
        synchronized(ioLock) { writeManifest(directory, currentManifest) }

        var completed = records.count { it.downloaded }
        onProgress(DownloadProgress(completed, records.size, ""))

        for ((index, record) in records.withIndex()) {
            currentCoroutineContext().ensureActive()
            if (record.downloaded) continue

            onProgress(DownloadProgress(completed, records.size, record.name))
            val detail = EsjzoneClient.getChapterDetail(
                authorization = authorization,
                chapter = Chapter(record.name, record.url, false),
                preferDownloaded = false,
                forceRefresh = false,
                baseUrl = baseUrl
            )
            val storedChapter = DownloadedChapterContent(
                name = detail.name.ifBlank { record.name },
                url = record.url,
                components = detail.content.mapNotNull { component ->
                    when (component) {
                        is TextComponent -> DownloadedComponent(
                            type = TEXT_COMPONENT,
                            value = component.plainText()
                        )

                        is ImageComponent -> DownloadedComponent(
                            type = IMAGE_COMPONENT,
                            value = component.url
                        ).withDownloadedImage(
                            downloadImage(authorization, directory, component.url, detail.sourceUrl ?: baseUrl)
                        )

                        else -> null
                    }
                },
                contentHtml = detail.contentHtml,
                baseUrl = detail.sourceUrl ?: EsjzoneUrls.resolve(record.url, baseUrl ?: EsjzoneUrls.Base)
            )
            synchronized(ioLock) {
                writeJson(File(directory, record.fileName), storedChapter)
            }

            records = records.toMutableList().also { mutable ->
                mutable[index] = record.copy(downloaded = true)
            }
            completed += 1
            currentManifest = manifestFrom(
                novel = novel,
                records = records,
                downloadedAt = System.currentTimeMillis(),
                complete = completed == records.size
            )
            synchronized(ioLock) { writeManifest(directory, currentManifest) }
            onProgress(DownloadProgress(completed, records.size, record.name))
        }

        if (!currentManifest.complete) {
            currentManifest = manifestFrom(
                novel = novel,
                records = records,
                downloadedAt = System.currentTimeMillis(),
                complete = true
            )
            synchronized(ioLock) { writeManifest(directory, currentManifest) }
        }
        return currentManifest
    }

    /** Returns a downloaded chapter without touching the network. */
    fun readChapter(chapterUrl: String): DetailedChapter? = synchronized(ioLock) {
        val match = findChapter(chapterUrl) ?: return@synchronized null
        val stored = readJson(
            resolveLocalFile(match.directory, match.record.fileName)
                ?: return@synchronized null,
            DownloadedChapterContent::class.java
        ) ?: return@synchronized null

        val previous = match.manifest.chapters.getOrNull(match.record.index - 1)
            ?.toChapter()
        val next = match.manifest.chapters.getOrNull(match.record.index + 1)
            ?.toChapter()

        DetailedChapter(
            name = stored.name,
            content = restoreComponents(match.directory, stored),
            previous = previous,
            next = next,
            contentHtml = stored.contentHtml,
            sourceUrl = stored.baseUrl ?: stored.url
        )
    }

    /** Reconstructs enough detail data to open a fully downloaded novel while offline. */
    fun readDetailedNovel(novelUrl: String): DetailedNovel? = synchronized(ioLock) {
        val stored = readManifest(directoryFor(novelUrl, create = false))
            ?.takeIf { it.complete }
            ?: return@synchronized null
        val chapters = stored.chapters
            .filter { it.downloaded }
            .map { ChapterItem(it.toChapter()) }

        DetailedNovel(
            name = stored.name,
            url = stored.url,
            coverUrl = stored.coverUrl,
            views = stored.views,
            likes = stored.likes,
            words = stored.words,
            type = stored.type,
            author = stored.author,
            forumUrl = stored.forumUrl,
            tags = stored.tags,
            isAdult = stored.isAdult,
            isFavorite = false,
            description = NovelDescription(
                stored.description.takeIf { it.isNotBlank() }
                    ?.let { listOf(TextComponent(it)) }
                    ?: emptyList()
            ),
            chapterList = NovelChapterList(chapters),
            sourceUrl = stored.sourceUrl,
            updatedAt = stored.updatedAt
        )
    }

    fun chapterContent(
        novelUrl: String,
        record: DownloadedChapterRecord
    ): DownloadedChapterContent? = synchronized(ioLock) {
        val directory = directoryFor(novelUrl, create = false) ?: return@synchronized null
        val file = resolveLocalFile(directory, record.fileName) ?: return@synchronized null
        readJson(file, DownloadedChapterContent::class.java)
    }

    fun imageFile(novelUrl: String, component: DownloadedComponent): File? = synchronized(ioLock) {
        val relative = component.localFile ?: return@synchronized null
        val directory = directoryFor(novelUrl, create = false) ?: return@synchronized null
        resolveLocalFile(directory, relative)?.takeIf(File::isFile)
    }

    private fun manifestFrom(
        novel: DetailedNovel,
        records: List<DownloadedChapterRecord>,
        downloadedAt: Long,
        complete: Boolean
    ) = DownloadedNovelManifest(
        name = novel.name,
        url = novel.url,
        coverUrl = novel.coverUrl,
        views = novel.views,
        likes = novel.likes,
        words = novel.words,
        type = novel.type,
        author = novel.author,
        forumUrl = novel.forumUrl,
        tags = novel.tags,
        isAdult = novel.isAdult,
        description = novel.description.components.joinToString("\n") { component ->
            when (component) {
                is TextComponent -> component.plainText()
                is ImageComponent -> component.url
                else -> ""
            }
        }.trim(),
        sourceUrl = novel.sourceUrl,
        updatedAt = novel.updatedAt,
        chapters = records,
        downloadedAt = downloadedAt,
        complete = complete
    )

    private fun findChapter(chapterUrl: String): ChapterMatch? {
        val target = chapterKey(chapterUrl)
        rootDirectory?.listFiles()
            ?.asSequence()
            ?.filter(File::isDirectory)
            ?.forEach { directory ->
                val manifest = readManifest(directory) ?: return@forEach
                val record = manifest.chapters.firstOrNull {
                    it.downloaded && chapterKey(it.url) == target
                } ?: return@forEach
                return ChapterMatch(directory, manifest, record)
            }
        return null
    }

    private fun restoreComponents(
        novelDirectory: File,
        stored: DownloadedChapterContent
    ): List<Component> {
        val html = stored.contentHtml
        if (!html.isNullOrBlank()) {
            val baseUrl = stored.baseUrl ?: EsjzoneUrls.resolve(stored.url)
            val document = Jsoup.parseBodyFragment(html, baseUrl)
            val downloadedImages = stored.components
                .filter { it.type == IMAGE_COMPONENT }
            document.select("img").forEach { image ->
                val candidates = IMAGE_URL_ATTRIBUTES.mapNotNull { attribute ->
                    image.absUrl(attribute)
                        .ifBlank { image.attr(attribute) }
                        .trim()
                        .takeIf(String::isNotBlank)
                }
                val saved = downloadedImages.firstOrNull { component ->
                    component.value in candidates
                }
                val localImage = saved?.localFile
                    ?.let { relative -> resolveLocalFile(novelDirectory, relative) }
                    ?.takeIf(File::isFile)
                if (localImage != null) {
                    IMAGE_URL_ATTRIBUTES.forEach(image::removeAttr)
                    image.removeAttr("srcset")
                    image.attr("src", localImage.toURI().toString())
                }
            }
            return analyseComponents(document.body())
        }

        return stored.components.map { component ->
            if (component.type == IMAGE_COMPONENT) {
                val localImage = component.localFile
                    ?.let { relative -> resolveLocalFile(novelDirectory, relative) }
                    ?.takeIf(File::isFile)
                ImageComponent(localImage?.toURI()?.toString() ?: component.value)
            } else {
                TextComponent(component.value)
            }
        }
    }

    private fun directoryFor(novelUrl: String, create: Boolean): File? {
        val root = rootDirectory ?: return null
        val directory = File(root, digest(canonicalKey(novelUrl)))
        if (directory.isDirectory) return directory
        return if (create && directory.mkdirs()) directory else null
    }

    private fun readManifest(directory: File?): DownloadedNovelManifest? {
        if (directory == null) return null
        return readJson(File(directory, MANIFEST_FILE), DownloadedNovelManifest::class.java)
    }

    private fun writeManifest(directory: File, manifest: DownloadedNovelManifest) {
        writeJson(File(directory, MANIFEST_FILE), manifest)
    }

    private fun downloadImage(
        authorization: Authorization,
        novelDirectory: File,
        rawUrl: String,
        baseUrl: String?
    ): DownloadedImage? {
        if (rawUrl.isBlank()) return null
        return runCatching {
            val url = EsjzoneUrls.resolve(rawUrl, baseUrl ?: EsjzoneUrls.Base)
            val imagePrefix = "image-${digest(url)}."
            val imagesDirectory = File(novelDirectory, "images")
            imagesDirectory.listFiles()
                ?.firstOrNull { it.isFile && it.name.startsWith(imagePrefix) }
                ?.let { existing ->
                    return@runCatching DownloadedImage(
                        relativeName = "images/${existing.name}",
                        mediaType = mediaTypeFromUrl(existing.name)
                    )
                }
            val response = EsjzoneClient.authenticatedClient(authorization).newCall(
                Request.Builder()
                    .url(url)
                    .headers(EsjzoneClient.headers)
                    .get()
                    .build()
            ).execute()
            response.use {
                if (!it.isSuccessful) error("Image request failed with HTTP ${it.code}")
                val body = it.body ?: error("Image response is empty")
                val contentLength = body.contentLength()
                if (contentLength > MAX_IMAGE_BYTES) error("Chapter image is too large")

                val mediaType = body.contentType()?.toString()?.substringBefore(';')
                    ?.trim()
                    ?.takeIf { value -> value.startsWith("image/") }
                    ?: mediaTypeFromUrl(url)
                val extension = extensionFor(mediaType, url)
                if (!imagesDirectory.isDirectory && !imagesDirectory.mkdirs()) {
                    error("Unable to create the chapter image directory")
                }
                val relativeName = "images/$imagePrefix$extension"
                val destination = File(novelDirectory, relativeName)
                if (!destination.isFile) {
                    val temporary = File(imagesDirectory, "${destination.name}.tmp")
                    try {
                        body.byteStream().use { input ->
                            temporary.outputStream().buffered().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > MAX_IMAGE_BYTES) {
                                        error("Chapter image is too large")
                                    }
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        moveReplacing(temporary, destination)
                    } finally {
                        if (temporary.isFile) temporary.delete()
                    }
                }
                DownloadedImage(relativeName, mediaType)
            }
        }.onFailure { error ->
            AppLogger.w("NovelDownloadStore", "Unable to download a chapter image", error)
        }.getOrNull()
    }

    private fun writeJson(file: File, value: Any) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(gson.toJson(value), StandardCharsets.UTF_8)
        moveReplacing(temporary, file)
    }

    private fun moveReplacing(temporary: File, destination: File) {
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: Exception) {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    private fun <T> readJson(file: File, type: Class<T>): T? {
        if (!file.isFile) return null
        return runCatching {
            file.bufferedReader(StandardCharsets.UTF_8).use { gson.fromJson(it, type) }
        }.onFailure { error ->
            AppLogger.w("NovelDownloadStore", "Unable to read ${file.name}", error)
        }.getOrNull()
    }

    private fun chapterFileName(url: String): String = "chapter-${digest(chapterKey(url))}.json"

    private fun resolveLocalFile(directory: File, relative: String): File? {
        return runCatching {
            val candidate = File(directory, relative).canonicalFile
            val parent = directory.canonicalFile
            candidate.takeIf {
                it.path == parent.path || it.path.startsWith(parent.path + File.separator)
            }
        }.getOrNull()
    }

    private fun extensionFor(mediaType: String, url: String): String = when (mediaType) {
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/svg+xml" -> "svg"
        else -> url.substringBefore('?').substringAfterLast('.', "img")
            .lowercase()
            .takeIf { it.matches(Regex("[a-z0-9]{1,5}")) }
            ?: "img"
    }

    private fun mediaTypeFromUrl(url: String): String = when (
        url.substringBefore('?').substringAfterLast('.', "").lowercase()
    ) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    private fun canonicalKey(url: String): String =
        EsjzoneUrls.canonicalPageKey(url).ifBlank { url.trim() }

    /** Host aliases are equivalent, but a fragment can identify a distinct TOC entry. */
    private fun chapterKey(url: String): String {
        val resolved = EsjzoneUrls.resolve(url).trim()
        val fragment = resolved.substringAfter('#', "").trim()
        val page = canonicalKey(resolved)
        return if (fragment.isBlank()) page else "$page#$fragment"
    }

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun DownloadedChapterRecord.toChapter() = Chapter(name, url, false)

    private fun TextComponent.plainText(): String = buildString {
        append(text)
        getExtras().forEach { append(it.plainText()) }
    }

    private fun DownloadedComponent.withDownloadedImage(image: DownloadedImage?) = copy(
        localFile = image?.relativeName,
        mediaType = image?.mediaType
    )

    private data class DownloadedImage(
        val relativeName: String,
        val mediaType: String
    )

    private data class ChapterMatch(
        val directory: File,
        val manifest: DownloadedNovelManifest,
        val record: DownloadedChapterRecord
    )

    private const val MAX_IMAGE_BYTES = 32L * 1024L * 1024L
    private val IMAGE_URL_ATTRIBUTES = listOf(
        "data-src",
        "data-original",
        "data-lazy-src",
        "src"
    )
}

package com.breakyuna.esjzone.data.repository

import androidx.compose.ui.graphics.Color
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.entity.Bookmark
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderBookmark
import com.breakyuna.esjzone.domain.reader.ReaderChapterDocument
import com.breakyuna.esjzone.domain.reader.ReaderChapterRef
import com.breakyuna.esjzone.domain.reader.ReaderChapterRepository
import com.breakyuna.esjzone.domain.reader.ReaderRuby
import com.breakyuna.esjzone.domain.reader.ReaderTextStyle
import com.breakyuna.esjzone.domain.reader.ReadingProgress
import com.breakyuna.esjzone.domain.reader.ReadingProgressRepository
import com.breakyuna.esjzone.domain.reader.ReaderBookmarkRepository
import com.breakyuna.esjzone.domain.repository.ReaderRepository
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.component.BackgroundColorTextStyle
import com.breakyuna.esjzone.novellibrary.component.BoldTextStyle
import com.breakyuna.esjzone.novellibrary.component.ColorTextStyle
import com.breakyuna.esjzone.novellibrary.component.Component
import com.breakyuna.esjzone.novellibrary.component.FontSizeTextStyle
import com.breakyuna.esjzone.novellibrary.component.FuriganaTextStyle
import com.breakyuna.esjzone.novellibrary.component.ImageComponent
import com.breakyuna.esjzone.novellibrary.component.ItalicTextStyle
import com.breakyuna.esjzone.novellibrary.component.LineThroughTextStyle
import com.breakyuna.esjzone.novellibrary.component.NewLineComponent
import com.breakyuna.esjzone.novellibrary.component.TextComponent
import com.breakyuna.esjzone.novellibrary.component.TextStyle
import com.breakyuna.esjzone.novellibrary.component.UnderlineTextStyle
import com.breakyuna.esjzone.novellibrary.novel.Chapter

/** Adapts the verified legacy chapter loader without moving network or cache policy into domain. */
class LegacyReaderChapterRepository(
    private val delegate: ReaderRepository,
    private val authorization: Authorization
) : ReaderChapterRepository {
    override suspend fun load(chapter: ReaderChapterRef): ReaderChapterDocument {
        val source = Chapter(name = chapter.name, url = chapter.url, isHistory = false)
        val detail = delegate.chapter(authorization, source)
        return ReaderChapterDocument(
            chapter = chapter,
            blocks = detail.content.flatMap(Component::toReaderBlocks),
            previous = detail.previous?.toReaderRef(),
            next = detail.next?.toReaderRef(),
            contentHtml = detail.contentHtml,
            sourceUrl = detail.sourceUrl
        )
    }
}

class DatabaseReadingProgressRepository(
    database: GeneralDatabase
) : ReadingProgressRepository {
    private val dao = database.localReadingActivityDao()

    override suspend fun latest(novelId: String): ReadingProgress? =
        dao.getLatestForNovel(novelId)?.toDomain()

    override suspend fun save(progress: ReadingProgress) {
        dao.upsertLatest(progress.toEntity())
    }
}

class DatabaseReaderBookmarkRepository(
    database: GeneralDatabase
) : ReaderBookmarkRepository {
    private val dao = database.bookmarkDao()

    override suspend fun find(chapterUrl: String): ReaderBookmark? =
        dao.findByChapterUrl(chapterUrl)?.toDomain()

    override suspend fun save(bookmark: ReaderBookmark) {
        dao.insert(bookmark.toEntity())
    }

    override suspend fun remove(bookmark: ReaderBookmark) {
        dao.delete(bookmark.toEntity())
    }
}

private fun Chapter.toReaderRef() = ReaderChapterRef(name = name, url = url)

private fun Component.toReaderBlocks(): List<ReaderBlock> = when (this) {
    is ImageComponent -> listOf(ReaderBlock.Image(url))
    is NewLineComponent -> listOf(ReaderBlock.LineBreak)
    is TextComponent -> {
        val ruby = getStyles().filterIsInstance<FuriganaTextStyle>().firstOrNull()?.readingText()
        listOf(
            ReaderBlock.Text(
                value = text,
                styles = getStyles().mapNotNull(TextStyle::toReaderStyle).toSet(),
                ruby = ruby?.let { ReaderRuby(base = text, reading = it.asPlainText()) }
            )
        ) + getExtras().flatMap(TextComponent::toReaderBlocks)
    }
    else -> emptyList()
}

private fun TextComponent.asPlainText(): String =
    text + getExtras().joinToString(separator = "") { it.asPlainText() }

private fun TextStyle.toReaderStyle(): ReaderTextStyle? = when {
    this === BoldTextStyle -> ReaderTextStyle.Bold
    this === ItalicTextStyle -> ReaderTextStyle.Italic
    this === UnderlineTextStyle -> ReaderTextStyle.Underline
    this === LineThroughTextStyle -> ReaderTextStyle.StrikeThrough
    this is FontSizeTextStyle -> ReaderTextStyle.FontSizePx(size())
    this is ColorTextStyle -> color().toReaderColor(::ReaderTextStyle.ForegroundColor)
    this is BackgroundColorTextStyle -> color().toReaderColor(::ReaderTextStyle.BackgroundColor)
    this is FuriganaTextStyle -> null
    else -> null
}

private fun Color.toReaderColor(factory: (Int, Int, Int) -> ReaderTextStyle): ReaderTextStyle =
    factory((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private fun LocalReadingActivity.toDomain() = ReadingProgress(
    activityId, novelId, novelName, novelUrl, chapterUrl, chapterName, chapterIndex, totalChapters,
    chapterProgress, startedAt, lastReadAt, durationMs, novelCoverUrl
)

private fun ReadingProgress.toEntity() = LocalReadingActivity(
    activityId = activityId,
    novelId = novelId,
    novelName = novelName,
    novelUrl = novelUrl,
    chapterUrl = chapterUrl,
    chapterName = chapterName,
    chapterIndex = chapterIndex,
    totalChapters = totalChapters,
    chapterProgress = chapterProgress,
    startedAt = startedAt,
    lastReadAt = lastReadAt,
    durationMs = durationMs,
    novelCoverUrl = novelCoverUrl
)

private fun Bookmark.toDomain() = ReaderBookmark(
    chapterUrl, novelId, novelName, chapterName, createdAt
)

private fun ReaderBookmark.toEntity() = Bookmark(
    chapterUrl, novelId, novelName, chapterName, createdAt
)

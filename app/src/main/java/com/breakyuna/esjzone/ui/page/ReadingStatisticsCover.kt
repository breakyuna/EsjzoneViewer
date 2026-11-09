package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.entity.LocalReadingActivity
import com.breakyuna.esjzone.database.readingStatisticsBookKey
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** DAO rows are newest first; keep the most recent matching identity. */
internal fun statisticsActivitiesByBook(activities: List<LocalReadingActivity>): Map<String, LocalReadingActivity> =
    buildMap {
        activities.forEach { activity ->
            val key = readingStatisticsBookKey(activity.novelId, activity.novelUrl, activity.chapterUrl)
            if (key.isNotBlank() && key !in this) put(key, activity)
        }
    }

/** A statistics key may be a novel id, a detail path or an external novel URL. */
internal fun statisticsCoverLookupUrl(bookKey: String): String? {
    val key = bookKey.trim()
    if (key.matches(Regex("[0-9]+"))) return "/detail/$key.html"
    if (key.matches(Regex("/detail/[0-9]+(?:\\.html)?/?"))) return key
    val url = key.toHttpUrlOrNull() ?: return null
    if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
    // A chapter or discussion URL alone does not identify its book's detail page.
    if (url.encodedPath.startsWith("/forum/")) return null
    return url.toString()
}

/** Recover a known novel source when its independent local history was deleted. */
internal suspend fun statisticsCoverFallbackActivity(bookKey: String, bookName: String): LocalReadingActivity? {
    if (bookKey.isBlank()) return null
    val lookup = statisticsCoverLookupUrl(bookKey)
    val shelfKey = lookup?.let(BookshelfRepository::keyFor) ?: bookKey
    val shelf = PresentationAccess.database.bookshelfDao().forNovel(bookKey, shelfKey)
        .asSequence().filter { it.visible && it.url.isNotBlank() }
        .sortedByDescending { it.addedAt }.firstOrNull()
    val downloaded = if (shelf == null && lookup != null) PresentationAccess.downloads.manifest(lookup) else null
    val novelUrl = shelf?.url ?: downloaded?.url ?: return null
    return LocalReadingActivity(
        activityId = "",
        novelId = shelf?.novelId?.takeIf(String::isNotBlank) ?: bookKey,
        novelName = bookName,
        novelUrl = novelUrl,
        chapterUrl = "",
        chapterName = "",
        chapterIndex = -1,
        totalChapters = 0,
        chapterProgress = 0f,
        startedAt = 0L,
        lastReadAt = 0L,
        durationMs = 0L,
        novelCoverUrl = shelf?.coverUrl ?: downloaded?.coverUrl.orEmpty()
    )
}

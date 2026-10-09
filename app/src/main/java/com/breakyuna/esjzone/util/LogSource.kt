package com.breakyuna.esjzone.util

import java.net.URI

enum class LogSource(val label: String) { APP("App"), ESJZONE("ESJZone"), WENKU8("Wenku8") }

internal fun logSourceForTag(tag: String): LogSource = when {
    tag.startsWith("Wenku") -> LogSource.WENKU8
    tag in esjzoneLogTags -> LogSource.ESJZONE
    else -> LogSource.APP
}

/** Shared readers and detail loaders must identify the requested site explicitly. */
internal fun logSourceForUrl(url: String): LogSource = when (runCatching { URI(url).host?.lowercase() }.getOrNull()) {
    "www.wenku8.net" -> LogSource.WENKU8
    "esjzone.cc", "www.esjzone.cc", "esjzone.one", "www.esjzone.one" -> LogSource.ESJZONE
    null -> if (url.startsWith('/')) LogSource.ESJZONE else LogSource.APP
    else -> LogSource.APP
}

private val esjzoneLogTags = setOf(
    "AuthorizationCookieJar", "PersistentCookieJar", "CommunitySyncManager", "GetHomeData",
    "EsjzoneClient", "GetChapterDetail", "GetNovelDetail", "GetCommunity", "HistoryDataCache", "HomeDataCache", "IsAuthorized",
    "Login", "Logout", "RequestAuthToken", "LoginScreen", "HomeTabModel", "HistoryPageModel",
    "CategoryModel", "CategoryPageModel", "NovelListPage", "NovelListPageModel", "SearchPageModel", "ProfileTab",
    "LoadingScreen", "BookshelfRepository",
    "CommentPageModel", "ForumPageModel", "ForumCategoryPageModel", "ForumBoardPageModel", "ForumPostPageModel"
)

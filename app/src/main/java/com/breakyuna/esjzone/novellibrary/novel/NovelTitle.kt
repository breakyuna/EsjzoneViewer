package com.breakyuna.esjzone.novellibrary.novel

private val titleDetailId = Regex("/detail/([^/?#]+?)(?:\\.html)?/?(?:[?#]|$)")
private val titleForumId = Regex("/forum/([0-9]+)/")

internal fun novelTitleId(novelId: String = "", novelUrl: String = ""): String =
    novelId.trim().ifBlank {
        titleDetailId.find(novelUrl)?.groupValues?.getOrNull(1)
            ?: titleForumId.find(novelUrl)?.groupValues?.getOrNull(1).orEmpty()
    }

/** Reject identity placeholders, while keeping actual numeric titles such as 1984. */
internal fun validNovelTitle(
    name: String,
    novelId: String = "",
    novelUrl: String = "",
    chapterName: String = ""
): String {
    val title = name.trim()
    val id = novelTitleId(novelId, novelUrl)
    return title.takeUnless {
        it.isBlank() || it == id || it == novelUrl.trim() ||
            (chapterName.isNotBlank() && it == chapterName.trim()) ||
            (id.isNotBlank() && novelTitleId(novelUrl = it) == id)
    }.orEmpty()
}

internal fun preferredNovelTitle(name: String, previous: String, novelId: String = "", novelUrl: String = ""): String =
    validNovelTitle(name, novelId, novelUrl).ifBlank { validNovelTitle(previous, novelId, novelUrl) }

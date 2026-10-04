package com.breakyuna.esjzone.novellibrary.novel

import androidx.compose.runtime.Immutable
import com.breakyuna.esjzone.novellibrary.component.Component
import java.io.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.data.settings.SettingsDefaults

private val URL_REGEX = "(?:https?://[^/]+)?/forum/([0-9]+)/[0-9]+\\.html".toRegex()
private val forumPath = Regex("^/forum/[0-9]+/[0-9]+\\.html$", RegexOption.IGNORE_CASE)
private val wenkuPath = Regex("^/novel/[0-9]+/[0-9]+/[0-9]+\\.htm$", RegexOption.IGNORE_CASE)

enum class ChapterSource { ESJ_ZONE, WENKU8, UNSUPPORTED_EXTERNAL }

fun resolveChapterSource(rawUrl: String, baseUrl: String = EsjzoneUrls.Base): ChapterSource {
    val raw = rawUrl.trim()
    if (raw.isEmpty() || raw.startsWith("//") || raw.contains('\\')) return ChapterSource.UNSUPPORTED_EXTERNAL
    if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
        val original = raw.toHttpUrlOrNull() ?: return ChapterSource.UNSUPPORTED_EXTERNAL
        if (original.username.isNotEmpty() || original.password.isNotEmpty() ||
            (original.host != "www.wenku8.net" &&
                SettingsDefaults.DOMAINS.none { it.equals(original.host, ignoreCase = true) })) {
            return ChapterSource.UNSUPPORTED_EXTERNAL
        }
    }
    val resolved = EsjzoneUrls.resolve(raw, baseUrl).toHttpUrlOrNull()
        ?: return ChapterSource.UNSUPPORTED_EXTERNAL
    if (!resolved.isHttps || resolved.username.isNotEmpty() || resolved.password.isNotEmpty()) {
        return ChapterSource.UNSUPPORTED_EXTERNAL
    }
    return when {
        SettingsDefaults.DOMAINS.any { it.equals(resolved.host, ignoreCase = true) } &&
            forumPath.matches(resolved.encodedPath) -> ChapterSource.ESJ_ZONE
        resolved.host == "www.wenku8.net" && wenkuPath.matches(resolved.encodedPath) -> ChapterSource.WENKU8
        else -> ChapterSource.UNSUPPORTED_EXTERNAL
    }
}

@Immutable
data class Chapter(
    val name: String,
    val url: String,
    val isHistory: Boolean
) : Serializable {

    val isExternal: Boolean
        get() = source == ChapterSource.UNSUPPORTED_EXTERNAL

    val source: ChapterSource get() = resolveChapterSource(url)

    fun novelId(): String {
        return URL_REGEX.find(this.url)?.groupValues?.getOrNull(1).orEmpty()
    }
}

@Immutable
data class DetailedChapter(
    val name: String,
    val content: List<Component>,
    val previous: Chapter?,
    val next: Chapter?,
    val contentHtml: String? = null,
    val sourceUrl: String? = null,
    val body: com.breakyuna.esjzone.data.reader.ChapterBody? = null,
    val imageLocations: Map<String, String> = emptyMap()
) : Serializable

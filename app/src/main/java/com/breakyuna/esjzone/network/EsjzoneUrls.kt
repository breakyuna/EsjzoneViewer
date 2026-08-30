package com.breakyuna.esjzone.network

import android.net.Uri
import com.breakyuna.esjzone.GlobalSettings
import org.jsoup.nodes.Element

object EsjzoneUrls {

    private val FORUM_BOARD_PATH = Regex("^/forum/[0-9]+/([0-9]+)$")

    /** Resolves a page link without rewriting a valid cross-host URL from the site. */
    fun resolve(rawUrl: String): String {
        val url = rawUrl.trim()
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> "$Base$url"
            else -> "$Base/$url"
        }
    }

    /**
     * Returns a stable page identity for navigation and state keys.
     * Host aliases and fragments do not identify a different ESJ page.
     */
    fun canonicalPageKey(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        val resolved = resolve(rawUrl).substringBefore('#')
        return runCatching {
            val uri = Uri.parse(resolved)
            val path = uri.encodedPath
                ?.trimEnd('/')
                ?.ifBlank { "/" }
                ?: "/"
            path
        }.getOrElse {
            resolved.substringBefore('#').substringBefore('?').trimEnd('/').ifBlank { "/" }
        }
    }

    /** Converts a single-novel forum board URL to the corresponding detail URL. */
    fun novelDetailUrlFromForumBoard(rawUrl: String): String? =
        FORUM_BOARD_PATH.matchEntire(canonicalPageKey(rawUrl))
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { "/detail/$it.html" }

    val BaseWithoutProtocol: String
        get() = GlobalSettings.domain.value

    val Base: String
        get() = "https://$BaseWithoutProtocol"

    val EmptyCover: String
        // UI layers render the bundled placeholder for missing covers.  Keeping
        // this value blank prevents a slow or unavailable mirror image from
        // replacing the user's fixed local placeholder.
        get() = ""

    /** Returns an empty value for the site's stock no-cover image. */
    fun coverOrEmpty(rawUrl: String?): String {
        val value = rawUrl?.trim().orEmpty()
        if (value.isBlank()) return EmptyCover

        // The site has used several names for its gray no-cover asset over
        // time.  Compare the URL path (rather than the complete URL) so query
        // cache-busters and host aliases do not bypass the fallback.
        val path = runCatching {
            Uri.parse(resolve(value)).path.orEmpty()
        }.getOrElse {
            value.substringBefore('?').substringBefore('#')
        }
        val fileName = path.substringAfterLast('/').lowercase()
        val stem = fileName.substringBeforeLast('.', fileName)
        val isMissingCover = fileName in MISSING_COVER_NAMES ||
            stem in MISSING_COVER_STEMS ||
            MISSING_COVER_STEMS.any { marker ->
                stem.startsWith("${marker}_") || stem.startsWith("${marker}-")
            }

        return if (isMissingCover) {
            EmptyCover
        } else {
            // Coil cannot resolve a relative URL without a base URI.  The
            // parsers accept both absolute and relative image attributes, so
            // normalize valid relative cover URLs here as well.
            resolve(value)
        }
    }

    /**
     * Extracts the actual cover candidate from an ESJ lazy-loaded novel card.
     * Different page templates use different data-* attributes, and `src` is
     * often only a gray placeholder when lazy loading is enabled.
     */
    fun coverUrlFromNovelCard(card: Element?): String {
        return coverUrlFromImage(card?.selectFirst("img"))
    }

    /** Extracts a cover from an image element, skipping known placeholders. */
    fun coverUrlFromImage(image: Element?): String {
        return imageUrlCandidatesFrom(image)
            .map(::coverOrEmpty)
            .firstOrNull { it.isNotBlank() }
            ?: EmptyCover
    }

    private fun imageUrlCandidatesFrom(image: Element?): Sequence<String> {
        if (image == null) return emptySequence()
        return IMAGE_URL_ATTRIBUTES.asSequence()
            .mapNotNull { attribute ->
                val raw = image.attr(attribute).trim()
                if (raw.isBlank() || raw.startsWith("data:", ignoreCase = true)) {
                    null
                } else {
                    image.absUrl(attribute).trim().takeIf { it.isNotBlank() }
                        ?: raw
                }
            }
    }

    private val IMAGE_URL_ATTRIBUTES = listOf(
        "data-src",
        "data-original",
        "data-lazy-src",
        "data-original-src",
        "src"
    )

    private val MISSING_COVER_NAMES = setOf(
        "empty.jpg",
        "empty.jpeg",
        "empty.png",
        "empty.webp",
        "no-cover.jpg",
        "no-cover.png",
        "no-cover.webp",
        "no_cover.jpg",
        "no_cover.png",
        "no_cover.webp",
        "empty-cover.jpg",
        "empty-cover.png",
        "empty-cover.webp",
        "empty_cover.jpg",
        "empty_cover.png",
        "empty_cover.webp",
        "nocover.jpg",
        "nocover.png",
        "nocover.webp"
    )

    private val MISSING_COVER_STEMS = setOf(
        "empty",
        "empty_cover",
        "empty-cover",
        "no_cover",
        "no-cover",
        "nocover",
        "noimage",
        "no_image",
        "no-image",
        "nophoto",
        "no_photo",
        "no-photo",
        "placeholder",
        "default_cover",
        "default-cover",
        "cover_default",
        "cover-default"
    )

    val Home: String
        get() = Base
    val Forum: String
        get() = "$Base/forum"
    val Guestbook: String
        get() = "$Base/guestbook/"
    val Tags: String
        get() = "$Base/tags"

    object My {

        val Profile: String
            get() = "$Base/my/profile"

        val Login: String
            get() = "$Base/my/login"

        val Logout: String
            get() = "$Base/my/logout"

        val Favorite: String
            get() = "$Base/my/favorite"

        val View: String
            get() = "$Base/my/view"

    }

    object Inc {

        val MemLogin: String
            get() = "$Base/inc/mem_login.php"

        val MemFavorite: String
            get() = "$Base/inc/mem_favorite.php"

        val MemViewDel: String
            get() = "$Base/inc/mem_view_del.php"

    }

    object Novel {

        val AllRecentlyUpdate: String get() = "$Base/list-01"
        val AllRecentlyUpload: String get() = "$Base/list-02"
        val AllHighestRating: String get() = "$Base/list-03"
        val AllMostViews: String get() = "$Base/list-04"
        val AllMostChapters: String get() = "$Base/list-05"
        val AllMostComments: String get() = "$Base/list-06"
        val AllMostFavorites: String get() = "$Base/list-07"
        val AllMostWords: String get() = "$Base/list-08"

        val JapaneseRecentlyUpload: String get() = "$Base/list-12"
        val JapaneseRecentlyUpdate: String get() = "$Base/list-11"
        val JapaneseHighestRating: String get() = "$Base/list-13"
        val JapaneseMostViews: String get() = "$Base/list-14"
        val JapaneseMostChapters: String get() = "$Base/list-15"
        val JapaneseMostComments: String get() = "$Base/list-16"
        val JapaneseMostFavorites: String get() = "$Base/list-17"
        val JapaneseMostWords: String get() = "$Base/list-18"

        val OriginalRecentlyUpdate: String get() = "$Base/list-21"
        val OriginalRecentlyUpload: String get() = "$Base/list-22"
        val OriginalHighestRating: String get() = "$Base/list-23"
        val OriginalMostViews: String get() = "$Base/list-24"
        val OriginalMostChapters: String get() = "$Base/list-25"
        val OriginalMostComments: String get() = "$Base/list-26"
        val OriginalMostFavorites: String get() = "$Base/list-27"
        val OriginalMostWords: String get() = "$Base/list-28"

        val KoreanRecentlyUpload: String get() = "$Base/list-32"
        val KoreanRecentlyUpdate: String get() = "$Base/list-31"
        val KoreanHighestRating: String get() = "$Base/list-33"
        val KoreanMostViews: String get() = "$Base/list-34"
        val KoreanMostChapters: String get() = "$Base/list-35"
        val KoreanMostComments: String get() = "$Base/list-36"
        val KoreanMostFavorites: String get() = "$Base/list-37"
        val KoreanMostWords: String get() = "$Base/list-38"

    }

}

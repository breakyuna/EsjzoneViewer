package com.breakyuna.esjzone.network

import java.net.URI
import com.breakyuna.esjzone.GlobalSettings
import org.jsoup.nodes.Element
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object EsjzoneUrls {

    private val FORUM_BOARD_PATH = Regex("^/forum/[0-9]+/([0-9]+)$")

    /**
     * Resolves a site link while guaranteeing that the result is an HTTPS HTTP URL.
     * ESJ occasionally emits absolute HTTP links; upgrading those links keeps normal
     * navigation working without ever allowing a caller to opt into cleartext traffic.
     */
    fun resolve(rawUrl: String): String {
        return resolve(rawUrl, Base)
    }

    fun resolve(rawUrl: String, baseUrl: String): String {
        val url = rawUrl.trim()
        if (url.isBlank()) return ""
        val base = normalizeHttpUrl(baseUrl.trim()) ?: return ""
        val candidate = if (url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
        ) {
            url
        } else {
            base.toHttpUrl().resolve(url)?.toString() ?: return ""
        }
        return normalizeHttpUrl(candidate).orEmpty()
    }

    fun baseForDomain(domain: String): String = "https://${domain.trim()
        .replaceFirst(Regex("(?i)^https?://"), "")
        .trimEnd('/')}"

    private fun normalizeHttpUrl(rawUrl: String): String? {
        val parsed = rawUrl.toHttpUrlOrNull() ?: return null
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
        return when (parsed.scheme.lowercase()) {
            "https" -> parsed.toString()
            "http" -> parsed.newBuilder()
                .scheme("https")
                .apply { if (parsed.port == 80) port(443) }
                .build()
                .toString()
            else -> null
        }
    }

    /**
     * Builds a tag/search result URL using the route exposed by ESJ.
     *
     * The site's first page keeps the historical `/tags/{keyword}/` route for
     * the default (all novels + recently updated) query.  Every non-default
     * query, and every paginated request, uses the two-digit
     * `/{category}{sort}` route (`tags-14` = Japanese + most views).  The
     * nullable category preserves the old overload semantics for callers that
     * only need the default tag URL.
     */
    fun tagsUrl(
        keyword: String,
        sort: Int? = null,
        page: Int? = null,
        category: Int? = null
    ): String {
        val categoryWasSpecified = category != null
        val safeCategory = (category ?: 0).takeIf { it in VALID_TAG_CATEGORIES } ?: 0
        val safeSort = (sort ?: 1).coerceIn(MIN_TAG_SORT, MAX_TAG_SORT)
        val useLegacyDefaultRoute = !categoryWasSpecified && sort == null && page == null
        val useDefaultFirstPageRoute =
            safeCategory == DEFAULT_TAG_CATEGORY &&
                safeSort == DEFAULT_TAG_SORT &&
                page == null
        val route = if (useLegacyDefaultRoute || useDefaultFirstPageRoute) {
            "tags"
        } else {
            "tags-${safeCategory}${safeSort}"
        }

        val builder = Base.toHttpUrl().newBuilder()
        builder.addPathSegment(route)
        builder.addPathSegment(keyword)
        if (page != null) builder.addPathSegment("$page.html")
        return builder.build().toString()
    }

    private const val DEFAULT_TAG_CATEGORY = 0
    private const val DEFAULT_TAG_SORT = 1
    private const val MIN_TAG_SORT = 1
    private const val MAX_TAG_SORT = 8
    private val VALID_TAG_CATEGORIES = setOf(0, 1, 2, 3)

    /**
     * Returns a stable page identity for navigation and state keys.
     * Host aliases and fragments do not identify a different ESJ page.
     */
    fun canonicalPageKey(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        val resolved = resolve(rawUrl).substringBefore('#')
        return runCatching {
            val uri = URI(resolved)
            val path = uri.path
                ?.trimEnd('/')
                ?.ifBlank { "/" }
                ?: "/"
            path
        }.getOrElse {
            val withoutQuery = resolved.substringBefore('?').substringBefore('#')
            val pathPart = if (withoutQuery.contains("://")) {
                "/" + withoutQuery.substringAfter("://").substringAfter('/', "")
            } else {
                withoutQuery
            }
            pathPart.trimEnd('/').ifBlank { "/" }
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
            URI(resolve(value)).path.orEmpty()
        }.getOrElse {
            value.substringBefore('?').substringBefore('#')
        }
        val fileName = path.substringAfterLast('/').lowercase()
        val stem = fileName.substringBeforeLast('.', fileName)
        val isMissingCover = stem in MISSING_COVER_FINGERPRINTS ||
            fileName in MISSING_COVER_NAMES ||
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
        if (card == null) return EmptyCover
        return card.select(COVER_SOURCE_SELECTOR)
            .asSequence()
            .flatMap(::imageUrlCandidatesFrom)
            .map(::coverOrEmpty)
            .firstOrNull { it.isNotBlank() }
            ?: EmptyCover
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

    private const val COVER_SOURCE_SELECTOR =
        "img, [data-src], [data-original], [data-lazy-src], [data-original-src]"

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

    /**
     * Content fingerprints used by ESJ for externally hosted no-cover images.
     * Match only the filename stem so CDN size folders, hosts, extensions and
     * cache-busting query parameters cannot bypass the local fallback.
     */
    private val MISSING_COVER_FINGERPRINTS = setOf(
        "861e5157abc25f92f6b49af0f1465927"
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

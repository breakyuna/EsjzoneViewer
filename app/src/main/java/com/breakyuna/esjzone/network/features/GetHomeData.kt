package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.novellibrary.novel.analyseDescription
import com.breakyuna.esjzone.novellibrary.novel.preview
import com.breakyuna.esjzone.util.AppLogger
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

suspend fun EsjzoneClient.getHomeData(
    authorization: Authorization,
    forceRefresh: Boolean = false,
    onProgress: ((HomeData) -> Unit)? = null
): HomeData = withContext(Dispatchers.IO) {
    AppLogger.i("GetHomeData", "Fetching home data from ${EsjzoneUrls.Home} (forceRefresh=$forceRefresh)")

    val homeDeferred = async {
        cancellablePageRequest { getPage(
            authorization,
            EsjzoneUrls.Home,
            PageCacheTtl.HOME,
            forceRefresh = forceRefresh,
            pageKind = PageKind.HOME
        ) }
    }
    val previousWeekly = HomeDataCache.readSnapshot(authorization)?.weeklyUpdates.orEmpty()
    var weeklyFailure: com.breakyuna.esjzone.network.LoadFailureKind? = null
    val weeklyUpdatesDeferred = async {
        try {
            cancellablePageRequest { getWeeklyUpdates(authorization, forceRefresh = forceRefresh) }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("GetHomeData", "Error parsing weekly updates", error)
            weeklyFailure = error.loadFailureKind()
            previousWeekly
        }
    }

    val responseBody = homeDeferred.await()
    val document = Jsoup.parse(responseBody)

    val recentlyUpdateTranslatedNovels = mutableListOf<CoveredNovel>()
    val recentlyUpdateOriginalNovels = mutableListOf<CoveredNovel>()
    val recentlyUpdateTranslatedR18Novels = mutableListOf<CoveredNovel>()
    val recentlyUpdateOriginalR18Novels = mutableListOf<CoveredNovel>()
    val recommendationNovels = mutableListOf<CoveredNovel>()

    try {
        for (recentlyUpdateTranslatedData in selectHomeSectionCards(document, HomeSection.TRANSLATED)) {
            recentlyUpdateTranslatedNovels.add(
                parseNovelCard(recentlyUpdateTranslatedData, false, NovelCardLayout.HOME)
            )
        }
    } catch (e: Exception) {
        AppLogger.w("GetHomeData", "Error parsing recentlyUpdateTranslatedNovels", e)
    }

    try {
        for (recentlyUpdateOriginalData in selectHomeSectionCards(document, HomeSection.ORIGINAL)) {
            recentlyUpdateOriginalNovels.add(
                parseNovelCard(recentlyUpdateOriginalData, false, NovelCardLayout.HOME)
            )
        }
    } catch (e: Exception) {
        AppLogger.w("GetHomeData", "Error parsing recentlyUpdateOriginalNovels", e)
    }

    try {
        for (recentlyUpdateTranslatedR18Data in selectHomeSectionCards(document, HomeSection.TRANSLATED_R18)) {
            recentlyUpdateTranslatedR18Novels.add(
                parseNovelCard(recentlyUpdateTranslatedR18Data, true, NovelCardLayout.HOME)
            )
        }
    } catch (e: Exception) {
        AppLogger.w("GetHomeData", "Error parsing recentlyUpdateTranslatedR18Novels", e)
    }

    try {
        for (recentlyUpdateOriginalR18Data in selectHomeSectionCards(document, HomeSection.ORIGINAL_R18)) {
            recentlyUpdateOriginalR18Novels.add(
                parseNovelCard(recentlyUpdateOriginalR18Data, true, NovelCardLayout.HOME)
            )
        }
    } catch (e: Exception) {
        AppLogger.w("GetHomeData", "Error parsing recentlyUpdateOriginalR18Novels", e)
    }

    try {
        for (recommendationData in selectHomeSectionCards(document, HomeSection.RECOMMENDATION)) {
            recommendationNovels.add(
                // parseNovelCard performs the same R18 badge check for every
                // recommendation card while retaining the original ordering.
                parseNovelCard(recommendationData, false, NovelCardLayout.HOME)
            )
        }
    } catch (e: Exception) {
        AppLogger.w("GetHomeData", "Error parsing recommendationNovels", e)
    }

    if (recentlyUpdateTranslatedNovels.isEmpty() &&
        recentlyUpdateOriginalNovels.isEmpty() &&
        recentlyUpdateTranslatedR18Novels.isEmpty() &&
        recentlyUpdateOriginalR18Novels.isEmpty() &&
        recommendationNovels.isEmpty()
    ) {
        // A valid home page may have an empty individual section, but an entirely
        // empty result means the HTML template was not actually the ESJ home page.
        throw IOException("ESJ home page contained no recognizable novel cards")
    }

    AppLogger.i("GetHomeData", "Home data parsed successfully: rec=${recommendationNovels.size}, trans=${recentlyUpdateTranslatedNovels.size}, orig=${recentlyUpdateOriginalNovels.size}")

    val popularSeeds = selectWeeklyPopularSeeds(document)

    // Reuse already parsed covers without decoding detail files before the first publication.
    // Visible carousel items are enriched separately after the home sections settle.
    val initialPopular = initialWeeklyPopular(
        popularSeeds, HomeDataCache.readSnapshot(authorization)?.weeklyPopular.orEmpty()
    )

    // Grab weekly updates if already finished
    val initialWeeklyUpdates = if (weeklyUpdatesDeferred.isCompleted) {
        weeklyUpdatesDeferred.await()
    } else {
        previousWeekly
    }

    val initialHomeData = HomeData(
        recentlyUpdateTranslatedNovels,
        recentlyUpdateOriginalNovels,
        recentlyUpdateTranslatedR18Novels,
        recentlyUpdateOriginalR18Novels,
        recommendationNovels,
        initialWeeklyUpdates,
        initialPopular,
        weeklyFailure = if (weeklyUpdatesDeferred.isCompleted) weeklyFailure else null
    )

    // Publish the home sections before reading or parsing any popular detail pages.
    onProgress?.invoke(initialHomeData)

    val finalWeeklyUpdates = weeklyUpdatesDeferred.await()

    HomeData(
        recentlyUpdateTranslatedNovels,
        recentlyUpdateOriginalNovels,
        recentlyUpdateTranslatedR18Novels,
        recentlyUpdateOriginalR18Novels,
        recommendationNovels,
        finalWeeklyUpdates,
        initialPopular,
        weeklyFailure = weeklyFailure
    )
}

internal fun mergeWeeklyPopularEnrichment(
    previous: WeeklyPopularNovel,
    result: WeeklyPopularNovel
): WeeklyPopularNovel {
    if (result.coverFailure == null) return result
    if (previous.coverResolved == true || EsjzoneUrls.coverOrEmpty(previous.coverUrl).isNotBlank()) return previous
    return previous.copy(coverFailure = result.coverFailure)
}

internal fun initialWeeklyPopular(
    seeds: List<WeeklyPopularSeed>,
    cached: List<WeeklyPopularNovel>
): List<WeeklyPopularNovel> {
    val cachedByKey = cached.associateBy { EsjzoneUrls.canonicalPageKey(it.url) }
    return seeds.map { seed ->
        cachedByKey[EsjzoneUrls.canonicalPageKey(seed.url)]?.copy(
            rank = seed.rank, weeklyViews = seed.weeklyViews, name = seed.name, url = seed.url
        ) ?: seedToInitialWeeklyPopular(seed)
    }
}

internal fun seedToInitialWeeklyPopular(seed: WeeklyPopularSeed): WeeklyPopularNovel =
    WeeklyPopularNovel(
        coverResolved = false,
        rank = seed.rank,
        weeklyViews = seed.weeklyViews,
        descriptionPreview = "",
        type = "",
        coverUrl = "",
        name = seed.name,
        url = seed.url,
        views = seed.weeklyViews,
        likes = 0,
        isAdult = false,
        author = null
    )

internal data class WeeklyPopularSeed(
    val rank: Int,
    val weeklyViews: Int,
    val name: String,
    val url: String
)

internal fun selectWeeklyPopularSeeds(document: org.jsoup.nodes.Document): List<WeeklyPopularSeed> =
    document.select(".widget-categories-hot li")
        .take(10)
        .mapIndexedNotNull { index, item ->
            val link = item.selectFirst("a[href*='/detail/']") ?: return@mapIndexedNotNull null
            val name = link.text().trim()
            val url = link.attr("href").trim()
            if (name.isBlank() || url.isBlank()) return@mapIndexedNotNull null
            WeeklyPopularSeed(
                rank = index + 1,
                weeklyViews = parseOptionalCardCount(item.selectFirst("span")?.text()) ?: 0,
                name = name,
                url = url
            )
        }

/**
 * Reads only carousel metadata from a detail page. Deliberately avoids parsing the chapter tree
 * and comments, which can be very large for popular long-running novels.
 */
internal fun EsjzoneClient.enrichWeeklyPopular(
    authorization: Authorization,
    seed: WeeklyPopularSeed
): WeeklyPopularNovel {
    val targetUrl = EsjzoneUrls.resolve(seed.url)
    val responseBody = getPage(
        authorization = authorization,
        url = targetUrl,
        maxAgeMillis = PageCacheTtl.DETAIL,
        forceRefresh = false,
        pageKind = PageKind.DETAIL
    )
    val document = Jsoup.parse(responseBody, targetUrl)
    val detail = document.selectFirst(".book-detail") ?: document
    val description = document.selectFirst(
        ".book-description, #description, .description, [data-description]"
    )?.let(::analyseDescription) ?: NovelDescription(emptyList())
    val tags = document.select(
        ".widget-tags a, .widget-tags a.tag, a.tag[href*='/tags/'], .book-detail .tags a, .book-tags a"
    ).map { it.text().trim() }
    val parseCount: (String) -> Int = { raw -> raw.filter(Char::isDigit).toIntOrNull() ?: 0 }
    val type = detail.selectFirst("ul li[data-field='type'], ul li")?.text().orEmpty()
        .trim()
        .replaceFirst(Regex("^[^：:]+[：:]\\s*"), "")
        .trim()

    return WeeklyPopularNovel(
        coverResolved = true,
        rank = seed.rank,
        weeklyViews = seed.weeklyViews,
        descriptionPreview = description.preview(100),
        type = type,
        coverUrl = EsjzoneUrls.coverUrlFromImage(
            document.selectFirst(".product-gallery img, .book-detail img")
        ),
        name = detail.selectFirst("h2")?.text()?.trim().orEmpty().ifBlank { seed.name },
        url = seed.url,
        views = parseCount(document.selectFirst("#vtimes")?.text().orEmpty()),
        likes = parseCount(document.selectFirst("#favorite")?.text().orEmpty()),
        isAdult = tags.any { it.equals("R18", ignoreCase = true) },
        author = detail.selectFirst("ul li a[href^='/tags/'], a[href^='/tags/']")
            ?.text()?.trim()?.takeIf(String::isNotBlank)
    )
}

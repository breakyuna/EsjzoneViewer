package com.breakyuna.esjzone.network.features

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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val weeklyUpdatesDeferred = async {
        try {
            cancellablePageRequest { getWeeklyUpdates(authorization, forceRefresh = forceRefresh) }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("GetHomeData", "Error parsing weekly updates", error)
            emptyList()
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

    // Build initial popular carousel items: immediately enrich any seeds that are already cached,
    // and provide baseline fallback items for uncached seeds so initial render is zero-latency.
    val initialPopular = popularSeeds.map { seed ->
        val targetUrl = EsjzoneUrls.resolve(seed.url)
        if (hasCachedPage(authorization, targetUrl, PageCacheTtl.DETAIL)) {
            runCatching { enrichWeeklyPopular(authorization, seed) }.getOrNull()
                ?: seedToInitialWeeklyPopular(seed)
        } else {
            seedToInitialWeeklyPopular(seed)
        }
    }

    // Grab weekly updates if already finished
    val initialWeeklyUpdates = if (weeklyUpdatesDeferred.isCompleted) {
        weeklyUpdatesDeferred.await()
    } else {
        emptyList()
    }

    val initialHomeData = HomeData(
        recentlyUpdateTranslatedNovels,
        recentlyUpdateOriginalNovels,
        recentlyUpdateTranslatedR18Novels,
        recentlyUpdateOriginalR18Novels,
        recommendationNovels,
        initialWeeklyUpdates,
        initialPopular
    )

    // Emit initial parsed home data immediately so UI displays with zero wait!
    onProgress?.invoke(initialHomeData)

    // Popular covers must not wait for the independent weekly-update request.
    // The page client's existing six-request limit bounds detail enrichment.
    // Publish each completed item immediately, keeping the original ranking order.
    val seedsToEnrich = popularSeeds.filterIndexed { index, _ ->
        val item = initialPopular.getOrNull(index)
        item == null || item.coverUrl.isBlank()
    }

    var currentPopular = initialPopular
    val progressLock = Mutex()
    coroutineScope {
        seedsToEnrich.forEach { seed ->
            launch {
                val enriched = try {
                    cancellablePageRequest { enrichWeeklyPopular(authorization, seed) }
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLogger.w("GetHomeData", "Failed to enrich weekly popular item: ${seed.name}", error)
                    null
                }
                if (enriched != null) {
                    progressLock.withLock {
                        val previous = currentPopular.firstOrNull { it.url == seed.url }
                        currentPopular = currentPopular.map { item ->
                            if (item.url == seed.url) enriched else item
                        }
                        if (previous != null && enriched != previous &&
                            (enriched.coverUrl.isNotBlank() || enriched.isAdult != previous.isAdult)
                        ) {
                            onProgress?.invoke(
                                HomeData(
                                    recentlyUpdateTranslatedNovels,
                                    recentlyUpdateOriginalNovels,
                                    recentlyUpdateTranslatedR18Novels,
                                    recentlyUpdateOriginalR18Novels,
                                    recommendationNovels,
                                    if (weeklyUpdatesDeferred.isCompleted) weeklyUpdatesDeferred.await()
                                    else initialWeeklyUpdates,
                                    currentPopular
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    val finalWeeklyUpdates = weeklyUpdatesDeferred.await()

    HomeData(
        recentlyUpdateTranslatedNovels,
        recentlyUpdateOriginalNovels,
        recentlyUpdateTranslatedR18Novels,
        recentlyUpdateOriginalR18Novels,
        recommendationNovels,
        finalWeeklyUpdates,
        currentPopular
    )
}

internal fun seedToInitialWeeklyPopular(seed: WeeklyPopularSeed): WeeklyPopularNovel =
    WeeklyPopularNovel(
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
private fun EsjzoneClient.enrichWeeklyPopular(
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

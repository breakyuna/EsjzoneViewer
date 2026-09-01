package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.util.AppLogger
import java.io.IOException
import org.jsoup.Jsoup

fun EsjzoneClient.getHomeData(authorization: Authorization): HomeData {
    AppLogger.i("GetHomeData", "Fetching home data from ${EsjzoneUrls.Home}")

    val responseBody = getPage(
        authorization,
        EsjzoneUrls.Home,
        PageCacheTtl.HOME,
        pageKind = PageKind.HOME
    )

    val document = Jsoup.parse(responseBody)

    val recentlyUpdateTranslatedNovels = mutableListOf<CoveredNovel>()
    val recentlyUpdateOriginalNovels = mutableListOf<CoveredNovel>()
    val recentlyUpdateTranslatedR18Novels = mutableListOf<CoveredNovel>()
    val recentlyUpdateOriginalR18Novels = mutableListOf<CoveredNovel>()
    val recommendationNovels = mutableListOf<CoveredNovel>()

    fun parseCount(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        val digits = raw.replace(Regex("[^0-9]"), "")
        return digits.toIntOrNull() ?: 0
    }

    try {
        for (recentlyUpdateTranslatedData in EsjzoneXPaths.Home.RecentlyUpdateTranslated.All.evaluate(document).elements) {
            recentlyUpdateTranslatedNovels.add(
                CoveredNovelImpl(
                    EsjzoneUrls.coverUrlFromNovelCard(recentlyUpdateTranslatedData),
                    EsjzoneXPaths.Home.Novel.Name.evaluate(recentlyUpdateTranslatedData).get() ?: "",
                    EsjzoneXPaths.Home.Novel.Url.evaluate(recentlyUpdateTranslatedData).get() ?: "",
                    parseCount(EsjzoneXPaths.Home.Novel.Views.evaluate(recentlyUpdateTranslatedData).get()),
                    parseCount(EsjzoneXPaths.Home.Novel.Likes.evaluate(recentlyUpdateTranslatedData).get()),
                    false
                )
            )
        }
    } catch (e: Exception) {
        AppLogger.w("GetHomeData", "Error parsing recentlyUpdateTranslatedNovels", e)
    }

    try {
        for (recentlyUpdateOriginalData in EsjzoneXPaths.Home.RecentlyUpdateOriginal.All.evaluate(document).elements) {
            recentlyUpdateOriginalNovels.add(
                CoveredNovelImpl(
                    EsjzoneUrls.coverUrlFromNovelCard(recentlyUpdateOriginalData),
                    EsjzoneXPaths.Home.Novel.Name.evaluate(recentlyUpdateOriginalData).get() ?: "",
                    EsjzoneXPaths.Home.Novel.Url.evaluate(recentlyUpdateOriginalData).get() ?: "",
                    parseCount(EsjzoneXPaths.Home.Novel.Views.evaluate(recentlyUpdateOriginalData).get()),
                    parseCount(EsjzoneXPaths.Home.Novel.Likes.evaluate(recentlyUpdateOriginalData).get()),
                    false
                )
            )
        }
    } catch (e: Exception) {
        AppLogger.w("GetHomeData", "Error parsing recentlyUpdateOriginalNovels", e)
    }

    try {
        for (recentlyUpdateTranslatedR18Data in EsjzoneXPaths.Home.RecentlyUpdateTranslatedR18.All.evaluate(document).elements) {
            recentlyUpdateTranslatedR18Novels.add(
                CoveredNovelImpl(
                    EsjzoneUrls.coverUrlFromNovelCard(recentlyUpdateTranslatedR18Data),
                    EsjzoneXPaths.Home.Novel.Name.evaluate(recentlyUpdateTranslatedR18Data).get() ?: "",
                    EsjzoneXPaths.Home.Novel.Url.evaluate(recentlyUpdateTranslatedR18Data).get() ?: "",
                    parseCount(EsjzoneXPaths.Home.Novel.Views.evaluate(recentlyUpdateTranslatedR18Data).get()),
                    parseCount(EsjzoneXPaths.Home.Novel.Likes.evaluate(recentlyUpdateTranslatedR18Data).get()),
                    true
                )
            )
        }
    } catch (e: Exception) {
        AppLogger.w("GetHomeData", "Error parsing recentlyUpdateTranslatedR18Novels", e)
    }

    try {
        for (recentlyUpdateOriginalR18Data in EsjzoneXPaths.Home.RecentlyUpdateOriginalR18.All.evaluate(document).elements) {
            recentlyUpdateOriginalR18Novels.add(
                CoveredNovelImpl(
                    EsjzoneUrls.coverUrlFromNovelCard(recentlyUpdateOriginalR18Data),
                    EsjzoneXPaths.Home.Novel.Name.evaluate(recentlyUpdateOriginalR18Data).get() ?: "",
                    EsjzoneXPaths.Home.Novel.Url.evaluate(recentlyUpdateOriginalR18Data).get() ?: "",
                    parseCount(EsjzoneXPaths.Home.Novel.Views.evaluate(recentlyUpdateOriginalR18Data).get()),
                    parseCount(EsjzoneXPaths.Home.Novel.Likes.evaluate(recentlyUpdateOriginalR18Data).get()),
                    true
                )
            )
        }
    } catch (e: Exception) {
        AppLogger.w("GetHomeData", "Error parsing recentlyUpdateOriginalR18Novels", e)
    }

    try {
        for (recommendationData in EsjzoneXPaths.Home.Recommendation.All.evaluate(document).elements) {
            val r18BadgeElements = EsjzoneXPaths.Home.Novel.R18Badge.evaluate(recommendationData).elements
            val isR18 = r18BadgeElements.firstOrNull()?.attr("class")?.contains("badge") == true

            recommendationNovels.add(
                CoveredNovelImpl(
                    EsjzoneUrls.coverUrlFromNovelCard(recommendationData),
                    EsjzoneXPaths.Home.Novel.Name.evaluate(recommendationData).get() ?: "",
                    EsjzoneXPaths.Home.Novel.Url.evaluate(recommendationData).get() ?: "",
                    parseCount(EsjzoneXPaths.Home.Novel.Views.evaluate(recommendationData).get()),
                    parseCount(EsjzoneXPaths.Home.Novel.Likes.evaluate(recommendationData).get()),
                    isR18
                )
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

    return HomeData(
        recentlyUpdateTranslatedNovels,
        recentlyUpdateOriginalNovels,
        recentlyUpdateTranslatedR18Novels,
        recentlyUpdateOriginalR18Novels,
        recommendationNovels
    )
}

package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.HistoryNovel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

fun EsjzoneClient.getHistories(authorization: Authorization): List<HistoryNovel> {
    val httpClient = OkHttpClient.Builder()
        .cookieJar(AuthorizationCookieJar(authorization))
        .build()

    val response = httpClient.newCall(
        Request.Builder()
            .url(EsjzoneUrls.My.View)
            .get()
            .headers(this.headers)
            .build()
    ).execute()

    val responseBody = response.body!!.string()
    response.close()

    val document = Jsoup.parse(responseBody)

    val novels = mutableListOf<HistoryNovel>()

    for (element in EsjzoneXPaths.Profile.View.Novel.evaluate(document).elements) {
        val vid = element.attr("id").substring(5)
        val novelData = EsjzoneXPaths.Profile.View.TitleAndUrl.evaluate(element).elements[0]
        val chapterData = EsjzoneXPaths.Profile.View.Chapter.evaluate(element).elements[0]
        novels.add(
            HistoryNovel(
                novelData.text(),
                novelData.attr("href"),
                vid,
                Chapter(
                    chapterData.text(),
                    "${EsjzoneUrls.Base}${chapterData.attr("href")}",
                    true
                )
            )
        )
    }

    return novels.toList()
}
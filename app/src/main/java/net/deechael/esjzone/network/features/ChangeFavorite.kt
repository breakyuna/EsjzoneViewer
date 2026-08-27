package net.deechael.esjzone.network.features

import net.deechael.esjzone.network.Authorization
import net.deechael.esjzone.network.AuthorizationCookieJar
import net.deechael.esjzone.network.EsjzoneClient
import net.deechael.esjzone.network.EsjzoneUrls
import net.deechael.esjzone.novellibrary.novel.Novel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.EMPTY_REQUEST

fun EsjzoneClient.changeFavorites(authorization: Authorization, novel: Novel) {
    val fullUrl = if (novel.url.startsWith("http://") || novel.url.startsWith("https://")) {
        novel.url.replaceFirst(Regex("^https?://[^/]+"), EsjzoneUrls.Base)
    } else if (novel.url.startsWith("/")) {
        "${EsjzoneUrls.Base}${novel.url}"
    } else {
        "${EsjzoneUrls.Base}/${novel.url}"
    }
    val authToken = this.requestAuthToken(authorization, fullUrl)

    val httpClient = OkHttpClient.Builder()
        .cookieJar(AuthorizationCookieJar(authorization))
        .build()

    httpClient.newCall(
        Request.Builder()
            .url(EsjzoneUrls.Inc.MemFavorite)
            .post(EMPTY_REQUEST)
            .headers(this.headers)
            .header("Authorization", authToken)
            .build()
    ).execute().close()
}


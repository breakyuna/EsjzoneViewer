package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.novellibrary.novel.Novel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.EMPTY_REQUEST

fun EsjzoneClient.changeFavorites(authorization: Authorization, novel: Novel) {
    runNetworkSafely("ChangeFavorite", Unit) {
        val fullUrl = if (novel.url.startsWith("http://") || novel.url.startsWith("https://")) {
            novel.url.replaceFirst(Regex("^https?://[^/]+"), EsjzoneUrls.Base)
        } else if (novel.url.startsWith("/")) {
            "${EsjzoneUrls.Base}${novel.url}"
        } else {
            "${EsjzoneUrls.Base}/${novel.url}"
        }
        val authToken = this.requestAuthToken(authorization, fullUrl)
        if (authToken.isNotBlank()) {
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
            ).execute().use { }
        }
    }
}

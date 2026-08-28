package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import okhttp3.OkHttpClient
import okhttp3.Request

fun EsjzoneClient.logout(authorization: Authorization) {
    runNetworkSafely("Logout", Unit) {
        val httpClient = OkHttpClient.Builder()
            .cookieJar(AuthorizationCookieJar(authorization))
            .build()

        httpClient.newCall(
            Request.Builder()
                .url(EsjzoneUrls.My.Logout)
                .get()
                .headers(this.headers)
                .build()
        ).execute().use { }
    }
}

package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

fun EsjzoneClient.removeHistory(authorization: Authorization, vid: String) {
    val authToken = this.requestAuthToken(authorization, EsjzoneUrls.My.View)

    val httpClient = OkHttpClient.Builder()
        .cookieJar(AuthorizationCookieJar(authorization))
        .build()

    val response = httpClient.newCall(
        Request.Builder()
            .url(EsjzoneUrls.Inc.MemViewDel)
            .post(
                FormBody.Builder()
                    .add("vid", vid)
                    .build()
            )
            .headers(this.headers)
            .header("Authorization", authToken)
            .build()
    ).execute()

    response.close()


}
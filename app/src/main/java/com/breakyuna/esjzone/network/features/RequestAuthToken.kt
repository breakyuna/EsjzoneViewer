package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.util.removeLeft
import com.breakyuna.esjzone.util.removeRight
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

fun EsjzoneClient.requestAuthToken(authorization: Authorization, url: String): String {
    val httpClient = OkHttpClient.Builder()
        .cookieJar(AuthorizationCookieJar(authorization))
        .build()

    val response = httpClient.newCall(
        Request.Builder()
            .url(url)
            .post(
                FormBody.Builder()
                    .add("plxf", "getAuthToken")
                    .build()
            )
            .headers(this.headers)
            .build()
    ).execute()


    val authorizationToken = response.body!!.string().removeLeft(9).removeRight(10)
    response.close()

    return authorizationToken
}
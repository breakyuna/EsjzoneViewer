package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

fun EsjzoneClient.requestAuthToken(authorization: Authorization, url: String): String {
    return try {
        val httpClient = OkHttpClient.Builder()
            .cookieJar(AuthorizationCookieJar(authorization))
            .build()

        httpClient.newCall(
            Request.Builder()
                .url(url)
                .post(
                    FormBody.Builder()
                        .add("plxf", "getAuthToken")
                        .build()
                )
                .headers(this.headers)
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) {
                ""
            } else {
                parseAuthorizationToken(response.body?.string().orEmpty()).orEmpty()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e("RequestAuthToken", "Auth token request failed", e)
        ""
    }
}

package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

fun EsjzoneClient.isAuthorized(authorization: Authorization): Boolean {
    if (authorization.ewsKey.isBlank() || authorization.ewsKey == "null" ||
        authorization.ewsToken.isBlank() || authorization.ewsToken == "null") {
        AppLogger.i("IsAuthorized", "No stored authorization credentials found")
        return false
    }

    return try {
        AppLogger.i("IsAuthorized", "Checking authorization with server at ${EsjzoneUrls.My.Profile}")
        val httpClient = OkHttpClient.Builder()
            .cookieJar(AuthorizationCookieJar(authorization))
            .build()

        val response = httpClient.newCall(
            Request.Builder()
                .url(EsjzoneUrls.My.Profile)
                .get()
                .headers(this.headers)
                .build()
        ).execute()

        val responseBody = response.bodyStringOrEmpty()

        val document = Jsoup.parse(responseBody)
        val isAuth = EsjzoneXPaths.Profile.Username.evaluate(document).list().isNotEmpty()
        AppLogger.i("IsAuthorized", "Authorization check result: isAuthorized=$isAuth")
        isAuth
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e("IsAuthorized", "Failed to check authorization due to network/parsing exception", e)
        false
    }
}

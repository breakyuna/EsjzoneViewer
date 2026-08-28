package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.AuthorizationCookieJar
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneXPaths
import com.breakyuna.esjzone.novellibrary.user.UserProfile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

fun EsjzoneClient.getUserProfile(authorization: Authorization): UserProfile {
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

    val name = EsjzoneXPaths.Profile.Username.evaluate(document).get() ?: "User"
    val avatarUrl = EsjzoneXPaths.Profile.AvatarUrl.evaluate(document).get() ?: ""

    return UserProfile(
        name,
        avatarUrl,
    )
}

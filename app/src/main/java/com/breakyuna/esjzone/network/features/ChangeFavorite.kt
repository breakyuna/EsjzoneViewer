package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.novellibrary.novel.Novel
import okhttp3.Request
import okhttp3.internal.EMPTY_REQUEST

fun EsjzoneClient.changeFavorites(authorization: Authorization, novel: Novel) {
    runNetworkSafely("ChangeFavorite", Unit) {
        toggleFavorite(authorization, novel)
    }
}

/** Executes the site's toggle and accepts only a non-login/non-error response. */
fun EsjzoneClient.toggleFavorite(authorization: Authorization, novel: Novel): Boolean {
    return runNetworkSafely("ChangeFavorite", false) {
        val fullUrl = EsjzoneUrls.resolve(novel.url)
        val authToken = this.requestAuthToken(authorization, fullUrl)
        if (authToken.isBlank()) return@runNetworkSafely false
        authenticatedClient(authorization).newCall(
                Request.Builder()
                    .url(EsjzoneUrls.Inc.MemFavorite)
                    .post(EMPTY_REQUEST)
                    .headers(this.headers)
                    .header("Authorization", authToken)
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val finalUrl = response.request.url.toString()
                val body = response.body?.string().orEmpty()
                // The endpoint is documented as returning a short marker, not
                // a page. A 200 login/WAF HTML response must stay pending.
                if (PageResponsePolicy.looksLikeBlockedOrLoginPage(body, finalUrl)) {
                    return@use false
                }
                if (body.contains("<html", ignoreCase = true) ||
                    body.contains("<form", ignoreCase = true) ||
                    Regex("(?i)\\b(?:error|failed|failure|unauthorized|invalid)\\b")
                        .containsMatchIn(body)
                ) {
                    return@use false
                }
                invalidateFavoriteCache(authorization, fullUrl)
                true
            }
    }
}

/** Overload used by the shelf while retaining the stable URL from its row. */
fun EsjzoneClient.toggleFavorite(authorization: Authorization, entry: BookshelfEntry): Boolean =
    toggleFavorite(authorization, object : Novel {
        override val name: String = entry.title
        override val url: String = entry.url
    })

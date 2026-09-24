package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.readTextBounded
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.database.entity.BookshelfEntry
import com.breakyuna.esjzone.novellibrary.novel.Novel
import com.google.gson.JsonParser
import okhttp3.Request
import okhttp3.RequestBody

fun EsjzoneClient.changeFavorites(authorization: Authorization, novel: Novel) {
    runNetworkSafely("ChangeFavorite", Unit) {
        toggleFavorite(authorization, novel)
    }
}

/** Executes the site's toggle; only a confirmed business success completes a shelf intent. */
fun EsjzoneClient.toggleFavorite(authorization: Authorization, novel: Novel): Boolean {
    return runNetworkSafely("ChangeFavorite", false) {
        val fullUrl = EsjzoneUrls.resolve(novel.url)
        val authToken = this.requestAuthToken(authorization, fullUrl)
        if (authToken.isBlank()) return@runNetworkSafely false
        authenticatedClient(authorization).newCall(
                Request.Builder()
                    .url(EsjzoneUrls.Inc.MemFavorite)
                    .post(RequestBody.EMPTY)
                    .headers(this.headers)
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Authorization", authToken)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val body = response.body?.readTextBounded().orEmpty()
                if (!isFavoriteToggleSuccess(body)) return@use false
                invalidateFavoriteCache(authorization, fullUrl)
                true
            }
    }
}

/** The response is JSON even though the site labels it text/html. */
internal fun isFavoriteToggleSuccess(body: String): Boolean = runCatching {
    val root = JsonParser.parseString(body)
    if (!root.isJsonObject) return@runCatching false
    val status = root.asJsonObject.get("status")
    status != null && status.isJsonPrimitive && status.asJsonPrimitive.isNumber &&
        status.asString == "200"
}.getOrDefault(false)

/** Overload used by the shelf while retaining the stable URL from its row. */
fun EsjzoneClient.toggleFavorite(authorization: Authorization, entry: BookshelfEntry): Boolean =
    toggleFavorite(authorization, object : Novel {
        override val name: String = entry.title
        override val url: String = entry.url
    })

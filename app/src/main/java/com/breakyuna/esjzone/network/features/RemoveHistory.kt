package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.readTextBounded
import com.google.gson.JsonParser
import okhttp3.FormBody
import okhttp3.Request

fun EsjzoneClient.removeHistory(authorization: Authorization, vid: String) {
    removeHistories(authorization, setOf(vid))
}

/** One token per batch; stop at the first unconfirmed write without retrying it. */
fun EsjzoneClient.removeHistories(authorization: Authorization, viewIds: Set<String>): Set<String> {
    if (viewIds.isEmpty() || viewIds.any { it.isBlank() }) return emptySet()
    val base = EsjzoneUrls.baseForDomain(authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol })
    val viewUrl = EsjzoneUrls.resolve("/my/view", base)
    val deleteUrl = EsjzoneUrls.resolve("/inc/mem_view_del.php", base)
    val epoch = sessionEpoch()
    val client = authenticatedClient(authorization).newBuilder()
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()
    val authToken = requestAuthToken(authorization, viewUrl)
    if (authToken.isBlank()) return emptySet()
    val deleted = linkedSetOf<String>()
    // Invalidate once before the batch: a lost response may still mean a completed deletion.
    invalidatePage(authorization, viewUrl)
    HistoryDataCache.clearSnapshot(authorization)
    for (viewId in viewIds) {
        if (epoch != sessionEpoch() || Thread.currentThread().isInterrupted) break
        val accepted = runNetworkSafely("RemoveHistory", false) {
            client.newCall(
                Request.Builder()
                    .url(deleteUrl)
                    .post(FormBody.Builder().add("vid", viewId).build())
                    .headers(headers)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("Authorization", authToken)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build()
            ).execute().use { response ->
                response.isSuccessful && isHistoryDeleteSuccess(response.body?.readTextBounded().orEmpty())
            }
        }
        if (!accepted) break
        deleted.add(viewId)
    }
    return deleted
}

internal fun isHistoryDeleteSuccess(body: String): Boolean = runCatching {
    val root = JsonParser.parseString(body)
    if (!root.isJsonObject) return@runCatching false
    val status = root.asJsonObject.get("status")
    status != null && status.isJsonPrimitive && status.asJsonPrimitive.isNumber && status.asString == "200"
}.getOrDefault(false)

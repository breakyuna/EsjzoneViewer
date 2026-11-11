package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.pageRequestCancellation
import com.breakyuna.esjzone.network.readTextBounded
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import okhttp3.FormBody
import okhttp3.Request

fun EsjzoneClient.requestAuthToken(authorization: Authorization, url: String): String {
    // Inside a cancellable page request, cancelling the caller also cancels this call.
    val cancellation = pageRequestCancellation.get()
    return try {
        authenticatedClient(authorization).newCall(
            Request.Builder()
                .url(url)
                .post(
                    FormBody.Builder()
                        .add("plxf", "getAuthToken")
                        .build()
                )
                .headers(this.headers)
                .header("Accept", "text/javascript, text/html, application/xml, text/xml, */*")
                .build()
        ).also { cancellation?.attach(it) }.execute().use { response ->
            if (!response.isSuccessful) {
                ""
            } else {
                parseAuthorizationToken(response.body?.readTextBounded().orEmpty()).orEmpty()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (cancellation?.isCancelled() != true) AppLogger.e("RequestAuthToken", "Auth token request failed", e)
        ""
    } finally {
        cancellation?.detachCall()
    }
}

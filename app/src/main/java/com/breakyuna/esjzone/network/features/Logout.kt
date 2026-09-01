package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import okhttp3.Request

fun EsjzoneClient.logout(authorization: Authorization) {
    // Use an isolated, non-persisting cookie jar. The request is deliberately
    // synchronous and bounded: returning before it finishes could let a late
    // server-side logout invalidate a session created by the next login.
    val client = logoutClient(authorization)
    try {
        client.newCall(
            Request.Builder()
                .url(EsjzoneUrls.My.Logout)
                .get()
                .headers(this@logout.headers)
                .build()
        ).execute().use { }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        AppLogger.w("Logout", "Remote logout failed; local session will still be cleared", error)
    } finally {
        // This always runs after the bounded remote attempt, so no old request can
        // clear a newly persisted session after logout() returns.
        clearSession(authorization.domain)
    }
}

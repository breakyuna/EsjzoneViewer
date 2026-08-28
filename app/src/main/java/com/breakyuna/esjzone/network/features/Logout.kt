package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import okhttp3.Request

fun EsjzoneClient.logout(authorization: Authorization) {
    runNetworkSafely("Logout", Unit) {
        authenticatedClient(authorization).newCall(
            Request.Builder()
                .url(EsjzoneUrls.My.Logout)
                .get()
                .headers(this.headers)
                .build()
        ).execute().use { response ->
            if (response.isSuccessful) {
                clearPageCache()
            }
        }
    }
}

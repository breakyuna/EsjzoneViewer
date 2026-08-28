package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import okhttp3.FormBody
import okhttp3.Request

fun EsjzoneClient.removeHistory(authorization: Authorization, vid: String) {
    runNetworkSafely("RemoveHistory", Unit) {
        val authToken = this.requestAuthToken(authorization, EsjzoneUrls.My.View)
        if (authToken.isNotBlank()) {
            authenticatedClient(authorization).newCall(
                Request.Builder()
                    .url(EsjzoneUrls.Inc.MemViewDel)
                    .post(
                        FormBody.Builder()
                            .add("vid", vid)
                            .build()
                    )
                    .headers(this.headers)
                    .header("Authorization", authToken)
                    .build()
            ).execute().use { response ->
                if (response.isSuccessful) clearPageCache()
            }
        }
    }


}

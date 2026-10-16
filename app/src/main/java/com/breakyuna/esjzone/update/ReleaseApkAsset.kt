package com.breakyuna.esjzone.update

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal data class ReleaseApkAsset(
    val name: String,
    val url: String,
    val size: Long,
    val digest: String? = null
) {
    companion object {
        fun select(tag: String, assets: List<ReleaseApkAsset>): ReleaseApkAsset? {
            val apks = assets.filter { asset ->
                val url = asset.url.toHttpUrlOrNull() ?: return@filter false
                asset.name.endsWith(".apk", ignoreCase = true) && asset.size > 0 &&
                    url.scheme == "https" && url.host == "github.com" && url.port == 443 &&
                    url.username.isEmpty() && url.password.isEmpty() &&
                    url.query == null && url.fragment == null &&
                    url.pathSegments == listOf("breakyuna", "EsjzoneViewer", "releases", "download", tag, asset.name) &&
                    (asset.digest == null || asset.digest.matches(Regex("sha256:[0-9a-fA-F]{64}")))
            }
            return apks.firstOrNull { it.name == "EsjzoneViewer-$tag.apk" } ?: apks.singleOrNull()
        }
    }
}

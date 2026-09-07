package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.novellibrary.user.UserProfile
import org.jsoup.Jsoup

fun EsjzoneClient.getUserProfile(authorization: Authorization): UserProfile {
    val responseBody = getPage(
        authorization,
        EsjzoneUrls.My.Profile,
        PageCacheTtl.PROFILE,
        pageKind = PageKind.ACCOUNT
    )

    val document = Jsoup.parse(responseBody)

    val name = profileName(document)
    val avatarUrl = profileAvatarUrl(document)

    return UserProfile(
        name,
        avatarUrl,
    )
}

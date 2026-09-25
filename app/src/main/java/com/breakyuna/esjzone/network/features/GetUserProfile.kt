package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.novellibrary.user.UserProfile
import org.jsoup.Jsoup

fun EsjzoneClient.getUserProfile(
    authorization: Authorization,
    forceRefresh: Boolean = false
): UserProfile {
    val responseBody = getPage(
        authorization,
        EsjzoneUrls.resolve(
            "/my/profile",
            EsjzoneUrls.baseForDomain(authorization.domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol })
        ),
        PageCacheTtl.PROFILE,
        forceRefresh = forceRefresh,
        pageKind = PageKind.ACCOUNT
    )

    val document = Jsoup.parse(responseBody)

    val name = profileName(document)
    val avatarUrl = profileAvatarUrl(document)
    val experience = profileExperience(document)
    val level = profileLevel(document)

    return UserProfile(
        name,
        avatarUrl,
        experience,
        level,
    )
}

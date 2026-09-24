package com.breakyuna.esjzone.app

import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.novellibrary.user.UserProfile
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Persists the account-scoped profile snapshot shared by profile refreshes and remote writes. */
suspend fun cacheUserProfile(
    authorization: Authorization,
    domain: String,
    profile: UserProfile
) = withContext(Dispatchers.IO) {
    val prefix = profileCachePrefix(authorization, domain)
    val dao = PresentationAccess.database.cacheDao()
    dao.put("${prefix}name", profile.name)
    dao.put("${prefix}avatar", profile.avatarUrl)
    dao.put("${prefix}experience", profile.exp?.toString().orEmpty())
    dao.put("${prefix}level", profile.level.orEmpty())
}

internal fun profileCachePrefix(authorization: Authorization, domain: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(EsjzoneClient.accountScope(authorization).toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "profile:shared:$digest:"
}

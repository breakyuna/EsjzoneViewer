package com.breakyuna.esjzone.network

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

internal fun normalizedEmailDigest(email: String): String = MessageDigest.getInstance("SHA-256")
    .digest(email.trim().lowercase(Locale.ROOT).toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal fun matchesKnownAccountEmail(activeDigest: String?, email: String): Boolean? =
    activeDigest?.let { it == normalizedEmailDigest(email) }

/** Keeps mirror sessions together while preventing a different login from inheriting local data. */
internal fun chooseSharedAccountIdentity(
    activeEmailDigest: String?,
    requestedEmailDigest: String,
    activeIdentity: String?,
    mappedIdentity: String?
): String? = activeIdentity
    ?.takeIf { activeEmailDigest == requestedEmailDigest && it.isNotBlank() }
    ?: mappedIdentity?.takeIf(String::isNotBlank)

internal fun mirrorLoginOrder(selectedDomain: String, domains: List<String>): List<String> =
    listOf(selectedDomain) + domains.filter { it != selectedDomain }

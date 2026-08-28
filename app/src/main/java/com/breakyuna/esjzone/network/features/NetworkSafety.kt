package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import okhttp3.Response

/**
 * Reads an OkHttp response body without assuming that the server returned one.
 * The response is always closed after the body has been consumed.
 */
internal fun Response.bodyStringOrEmpty(): String = use {
    body?.string().orEmpty()
}

/**
 * Extracts the token returned by the login endpoint while tolerating empty or
 * unexpected responses such as an HTML error page.
 */
internal fun parseAuthorizationToken(body: String): String? {
    val trimmed = body.trim()
    val openingTag = "<JinJing>"
    val closingTag = "</JinJing>"
    val start = trimmed.indexOf(openingTag)
    val end = trimmed.lastIndexOf(closingTag)

    if (start < 0 || end <= start + openingTag.length) {
        return null
    }

    return trimmed
        .substring(start + openingTag.length, end)
        .trim()
        .takeIf { it.isNotEmpty() }
}

/**
 * Prevents synchronous network/parser failures from escaping a coroutine.
 * Cancellation must continue to propagate so that disposed screens do not
 * keep doing work after their coroutine has been cancelled.
 */
internal inline fun <T> runNetworkSafely(
    tag: String,
    fallback: T,
    block: () -> T
): T {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.e(tag, "Network or response parsing failed", e)
        fallback
    }
}

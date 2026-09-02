package com.breakyuna.esjzone.network

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Collections
import java.util.IdentityHashMap
import javax.net.ssl.SSLException

/** The two user-facing classes of failures that can occur while loading a page. */
enum class LoadFailureKind {
    NETWORK,
    CLIENT
}

/** Marks an exception raised at an actual OkHttp transport boundary. */
internal class NetworkRequestException(
    val requestUrl: String,
    cause: Throwable
) : java.io.IOException("Network request failed for $requestUrl", cause)

/** A server-side HTTP failure while requesting a page or dynamic table. */
internal class NetworkHttpException(
    val requestUrl: String,
    val statusCode: Int
) : java.io.IOException("HTTP $statusCode returned for $requestUrl")

/**
 * Maps an error to a user-facing class without treating every IOException as a
 * connectivity problem.  Several ESJ parsers intentionally use IOException
 * for malformed or unexpected server data, which is a client error.
 */
internal fun Throwable.loadFailureKind(): LoadFailureKind {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    var current: Throwable? = this
    while (current != null && seen.add(current)) {
        when (current) {
            is NetworkRequestException,
            is NetworkHttpException,
            is UnknownHostException,
            is ConnectException,
            is NoRouteToHostException,
            is SocketTimeoutException,
            is SocketException,
            is SSLException,
            is InterruptedIOException -> return LoadFailureKind.NETWORK
            is UntrustedPageException -> {
                // HTTP failures and WAF blocks mean the site could not serve
                // the requested page.  A structurally invalid or redirected
                // page, however, points to a client/parser or session issue.
                val reason = current.validation.reason.lowercase()
                return if (reason.startsWith("http ") ||
                    reason.contains("access challenge") ||
                    reason.contains("block page")
                ) {
                    LoadFailureKind.NETWORK
                } else {
                    LoadFailureKind.CLIENT
                }
            }
        }
        current = current.cause
    }
    return LoadFailureKind.CLIENT
}

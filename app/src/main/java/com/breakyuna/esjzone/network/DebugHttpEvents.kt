package com.breakyuna.esjzone.network

import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.diagnosticUrl
import com.breakyuna.esjzone.util.logSourceForUrl
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicLong
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Dispatcher
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

/** Observe transport without consuming bodies, changing headers or changing retry behavior. */
internal object DebugHttpEvents : EventListener.Factory {
    override fun create(call: Call): EventListener =
        if (AppLogger.debugEnabled) HttpTrace(call) else EventListener.NONE
}

private class HttpTrace(call: Call) : EventListener() {
    private val id = AppLogger.newTraceId("http")
    private val parent = AppLogger.currentTraceId()
    private val source = logSourceForUrl(call.request().url.toString())
    private val started = System.nanoTime()
    private val previous = AtomicLong(started)

    private fun event(stage: String, detail: String = "") {
        runCatching {
            val now = System.nanoTime()
            val delta = now - previous.getAndSet(now)
            AppLogger.trace("HttpDiagnostic", source) {
                "id=$id, parent=$parent, stage=$stage, elapsedMs=${(now - started) / 1_000_000}, " +
                    "deltaMs=${delta / 1_000_000}, $detail"
            }
        }
    }

    private fun failure(stage: String, error: IOException) {
        event(stage, "type=${error.javaClass.name}")
        if (AppLogger.debugEnabled) runCatching {
            AppLogger.w("HttpDiagnostic", "id=$id, parent=$parent, stage=$stage", error, source)
        }
    }

    override fun callStart(call: Call) = event("call-start",
        "method=${call.request().method}, requested=${diagnosticUrl(call.request().url.toString())}, " +
            "timeoutMs=${call.timeout().timeoutNanos() / 1_000_000}")
    override fun dispatcherQueueStart(call: Call, dispatcher: Dispatcher) =
        event("queue-start", "queued=${dispatcher.queuedCallsCount()}, running=${dispatcher.runningCallsCount()}")
    override fun dispatcherQueueEnd(call: Call, dispatcher: Dispatcher) = event("queue-end")
    override fun proxySelectStart(call: Call, url: okhttp3.HttpUrl) = event("proxy-start")
    override fun proxySelectEnd(call: Call, url: okhttp3.HttpUrl, proxies: List<Proxy>) =
        event("proxy-end", "types=${proxies.map { it.type() }}")
    override fun dnsStart(call: Call, domainName: String) = event("dns-start", "host=$domainName")
    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) =
        event("dns-end", "host=$domainName, addresses=${inetAddressList.map { it.hostAddress }}")
    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) =
        event("connect-start", "address=${inetSocketAddress.address?.hostAddress}, port=${inetSocketAddress.port}, proxy=${proxy.type()}")
    override fun secureConnectStart(call: Call) = event("tls-start")
    override fun secureConnectEnd(call: Call, handshake: Handshake?) =
        event("tls-end", "tls=${handshake?.tlsVersion}, cipher=${handshake?.cipherSuite}")
    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) =
        event("connect-end", "protocol=$protocol")
    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy,
        protocol: Protocol?, ioe: IOException) = failure("connect-failed", ioe)
    override fun connectionAcquired(call: Call, connection: Connection) =
        event("connection-acquired", "connection=${System.identityHashCode(connection)}, protocol=${connection.protocol()}")
    override fun connectionReleased(call: Call, connection: Connection) = event("connection-released")
    override fun requestHeadersStart(call: Call) = event("request-headers-start")
    override fun requestHeadersEnd(call: Call, request: Request) = event("request-headers-end",
        "method=${request.method}, actual=${diagnosticUrl(request.url.toString())}, " +
            "headerCount=${request.headers.size}, cookiePresent=${request.header("Cookie") != null}, " +
            "authorizationPresent=${request.header("Authorization") != null}")
    override fun requestBodyStart(call: Call) = event("request-body-start")
    override fun requestBodyEnd(call: Call, byteCount: Long) = event("request-body-end", "bytes=$byteCount")
    override fun requestFailed(call: Call, ioe: IOException) = failure("request-failed", ioe)
    override fun responseHeadersStart(call: Call) = event("response-headers-start")
    override fun responseHeadersEnd(call: Call, response: Response) = event("response-headers-end",
        "status=${response.code}, actual=${diagnosticUrl(response.request.url.toString())}, protocol=${response.protocol}, " +
            "mime=${diagnosticMime(response.header("Content-Type"))}, " +
            "contentLength=${response.header("Content-Length")?.toLongOrNull()}, " +
            "setCookieCount=${response.headers.values("Set-Cookie").size}, cfRayPresent=${response.header("cf-ray") != null}, " +
            "location=${diagnosticUrl(response.header("Location")?.let(response.request.url::resolve)?.toString())}")
    override fun responseBodyStart(call: Call) = event("response-body-start")
    override fun responseBodyEnd(call: Call, byteCount: Long) = event("response-body-end", "bytes=$byteCount")
    override fun responseFailed(call: Call, ioe: IOException) = failure("response-failed", ioe)
    override fun canceled(call: Call) = event("cancelled")
    override fun callEnd(call: Call) = event("call-end", "cancelled=${call.isCanceled()}")
    override fun callFailed(call: Call, ioe: IOException) = failure("call-failed", ioe)
}

internal fun diagnosticMime(raw: String?): String = raw?.substringBefore(';')?.trim()?.lowercase()?.takeIf {
    it in setOf("text/html", "application/xhtml+xml", "application/json", "text/plain", "image/jpeg",
        "image/png", "image/webp", "image/gif", "application/octet-stream", "application/zip")
} ?: if (raw == null) "missing" else "other"

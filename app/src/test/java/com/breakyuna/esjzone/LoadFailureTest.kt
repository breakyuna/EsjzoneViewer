package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.NetworkHttpException
import com.breakyuna.esjzone.network.NetworkRequestException
import com.breakyuna.esjzone.network.PageResponsePolicy
import com.breakyuna.esjzone.network.UntrustedPageException
import com.breakyuna.esjzone.network.features.ForumBoardDataException
import com.breakyuna.esjzone.network.loadFailureKind
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadFailureTest {
    private val url = "https://www.esjzone.cc/"

    @Test
    fun transportFailuresRemainNetworkErrorsThroughWrappers() {
        listOf(
            UnknownHostException("unavailable"),
            SocketTimeoutException("timeout"),
            NetworkHttpException(url, 429),
            NetworkRequestException(url, IOException("unexpected end of stream"))
        ).forEach { error ->
            assertEquals(LoadFailureKind.NETWORK, ExecutionException(error).loadFailureKind())
        }
    }

    @Test
    fun localIoAndParserFailuresAreNotConnectivityErrors() {
        listOf(
            IOException("local file could not be read"),
            ForumBoardDataException("Forum topic response was not valid JSON"),
            IllegalStateException("missing required element")
        ).forEach { error ->
            assertEquals(LoadFailureKind.CLIENT, error.loadFailureKind())
        }
    }

    @Test
    fun httpAndAccessBlocksAreNetworkFailures() {
        val blocked = "<html><body><h1>Access denied</h1></body></html>"
        listOf(
            PageResponsePolicy.validate(503, "unavailable", url),
            PageResponsePolicy.validate(200, blocked, url)
        ).forEach { validation ->
            assertEquals(
                LoadFailureKind.NETWORK,
                UntrustedPageException(url, validation).loadFailureKind()
            )
        }
    }

    @Test
    fun unrecognizedPageIsAClientFailure() {
        val validation = PageResponsePolicy.validate(200, "not an HTML page", url)
        assertEquals(
            LoadFailureKind.CLIENT,
            UntrustedPageException(url, validation).loadFailureKind()
        )
    }
}

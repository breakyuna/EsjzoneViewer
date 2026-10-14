package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.NetworkRequestException
import com.breakyuna.esjzone.network.NetworkHttpException
import com.breakyuna.esjzone.network.features.WeeklyPopularSeed
import com.breakyuna.esjzone.network.features.seedToInitialWeeklyPopular
import com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel
import com.breakyuna.esjzone.novellibrary.novel.CoverFailure
import com.breakyuna.esjzone.novellibrary.novel.CoverSource
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.breakyuna.esjzone.novellibrary.novel.coverFailure
import com.google.gson.Gson
import java.io.FileNotFoundException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverStateTest {
    @Test
    fun emptyMetadataDoesNotBecomeConfirmedMissingBeforeLookupFinishes() {
        assertEquals(CoverState.Unknown, CoverSource.fromUrl("", CoverState.Unknown).state)
        assertEquals(CoverState.Resolving, CoverSource.fromUrl("", CoverState.Resolving).state)
        assertEquals(CoverState.Missing, CoverSource.fromUrl("").state)
        assertEquals(CoverState.Missing, CoverSource.fromUrl("/assets/img/empty_cover.jpg").state)
    }

    @Test
    fun missingLocalCopyIsUnavailableRatherThanMissingAtTheSite() {
        val source = CoverSource.fromUrl(null, CoverState.Failed(CoverFailure.UNAVAILABLE))
        assertEquals(CoverState.Failed(CoverFailure.UNAVAILABLE), source.state)
        val cached = CoverSource.fromUrl("file:///covers/book.jpg", source.state)
        assertEquals(CoverState.Loading, cached.state)
        assertEquals("file:///covers/book.jpg", cached.url)
    }

    @Test
    fun metadataTimeoutAndSuccessfulEmptyDetailProduceDifferentStates() {
        val seed = seedToInitialWeeklyPopular(WeeklyPopularSeed(1, 10, "Book", "/detail/1.html"))
        assertEquals(CoverState.Resolving, seed.coverState)
        assertEquals(CoverState.Missing, seed.copy(coverResolved = true).coverState)
        val timeout = NetworkRequestException("https://example.com/detail/1.html", SocketTimeoutException())
        assertEquals(CoverState.Failed(CoverFailure.TIMEOUT), seed.copy(coverFailure = timeout.coverFailure()).coverState)
        assertEquals(CoverState.Loading, seed.copy(coverUrl = "https://example.com/cover.jpg").coverState)
    }

    @Test
    fun failureClassificationDistinguishesTimeoutFromOtherNetworkAndFileFailures() {
        assertEquals(CoverFailure.TIMEOUT, InterruptedIOException("timeout").coverFailure())
        assertEquals(CoverFailure.NETWORK, UnknownHostException().coverFailure())
        assertEquals(CoverFailure.NETWORK, ConnectException().coverFailure())
        assertEquals(CoverFailure.NETWORK, SSLException("certificate rejected").coverFailure())
        assertEquals(CoverFailure.NETWORK, NetworkHttpException("https://example.com/cover.jpg", 503).coverFailure())
        assertEquals(CoverFailure.OTHER, FileNotFoundException().coverFailure())
        assertEquals(CoverFailure.OTHER, IllegalArgumentException("invalid image").coverFailure())
    }

    @Test
    fun homeSnapshotRetainsMissingAndFailedStatesAndTreatsOldEmptyRecordsAsUnknown() {
        val gson = Gson()
        listOf(
            WeeklyPopularNovel(coverResolved = true),
            WeeklyPopularNovel(coverFailure = CoverFailure.TIMEOUT)
        ).forEach { novel ->
            val restored = gson.fromJson(gson.toJson(novel), WeeklyPopularNovel::class.java)
            assertEquals(novel.coverState, restored.coverState)
        }
        val old = gson.fromJson("{\"coverUrl\":\"\"}", WeeklyPopularNovel::class.java)
        assertEquals(CoverState.Unknown, old.coverState)
    }
}

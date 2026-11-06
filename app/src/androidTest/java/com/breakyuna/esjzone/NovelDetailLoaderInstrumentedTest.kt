package com.breakyuna.esjzone

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.NovelChapterList
import com.breakyuna.esjzone.novellibrary.novel.NovelDescription
import com.breakyuna.esjzone.ui.page.NovelDetailLoader
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovelDetailLoaderInstrumentedTest {
    private val authorization = Authorization("", "", "www.esjzone.cc")
    private fun cachedNovel() = DetailedNovel("Novel", "/detail/123.html", "/cover.jpg", 0, 0, 0,
        "", "", "", emptyList(), false, false, NovelDescription(emptyList()), NovelChapterList(emptyList()),
        adultStatusKnown = false)

    private suspend fun <T : Any> awaitValue(read: () -> T?): T = withTimeout(5_000) {
        var value: T? = null
        while (value == null) {
            value = withContext(Dispatchers.Main) { read() }
            if (value == null) delay(10)
        }
        requireNotNull(value)
    }

    @Test fun allowedModeUsesOfflineDetailsAndRestrictingItFetchesAVerifiedFlag() = runBlocking {
        val cached = cachedNovel()
        val requests = AtomicInteger()
        val model = withContext(Dispatchers.Main) {
            NovelDetailLoader(authorization, readDownloaded = { cached }, fetchDetail = {
                requests.incrementAndGet()
                cached.copy(isAdult = true, adultStatusKnown = true)
            }).also { it.load(cached, requireAdultStatus = false) }
        }
        try {
            val first = awaitValue { model.details[cached.url] }
            assertSame(cached, first)
            assertEquals(0, requests.get())
            withContext(Dispatchers.Main) { model.load(cached, requireAdultStatus = true) }
            val verified = awaitValue { model.details[cached.url]?.takeIf { it.adultStatusKnown } }
            assertTrue(verified.isAdult)
            assertEquals(1, requests.get())
        } finally { withContext(Dispatchers.Main) { model.viewModelScope.cancel() } }
    }

    @Test fun failedVerificationRetainsTheOfflineCoverForAllowedMode() = runBlocking {
        val cached = cachedNovel()
        val requests = AtomicInteger()
        val model = withContext(Dispatchers.Main) {
            NovelDetailLoader(authorization, readDownloaded = { cached }, fetchDetail = {
                requests.incrementAndGet()
                throw SocketTimeoutException("Fixture offline")
            }).also { it.load(cached, requireAdultStatus = true) }
        }
        try {
            val failure = awaitValue { model.failures[cached.url] }
            assertEquals(LoadFailureKind.NETWORK, failure)
            withContext(Dispatchers.Main) {
                assertSame(cached, model.details[cached.url])
                model.load(cached, requireAdultStatus = false)
                assertEquals(cached.coverUrl, model.details[cached.url]?.coverUrl)
            }
            assertEquals(1, requests.get())
        } finally { withContext(Dispatchers.Main) { model.viewModelScope.cancel() } }
    }

    @Test fun restrictionEnabledDuringOfflineReadStillTriggersVerification() = runBlocking {
        val cached = cachedNovel()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val requests = AtomicInteger()
        val model = withContext(Dispatchers.Main) {
            NovelDetailLoader(authorization, readDownloaded = {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                cached
            }, fetchDetail = {
                requests.incrementAndGet()
                cached.copy(adultStatusKnown = true)
            }).also { it.load(cached, requireAdultStatus = false) }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            withContext(Dispatchers.Main) { model.load(cached, requireAdultStatus = true) }
            release.countDown()
            awaitValue { model.details[cached.url]?.takeIf { it.adultStatusKnown } }
            assertEquals(1, requests.get())
        } finally {
            release.countDown()
            withContext(Dispatchers.Main) { model.viewModelScope.cancel() }
        }
    }
}

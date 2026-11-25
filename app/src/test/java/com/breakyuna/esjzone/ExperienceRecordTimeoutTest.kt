package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.NetworkRequestException
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.features.ExperienceRecordException
import com.breakyuna.esjzone.network.features.getExperienceRecords
import com.breakyuna.esjzone.network.features.isRequestTimeout
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecord
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordPage
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordType
import com.breakyuna.esjzone.ui.page.ExperienceRecordsCache
import com.breakyuna.esjzone.ui.page.ExperienceRecordsController
import com.breakyuna.esjzone.ui.page.ExperienceRecordsState
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExperienceRecordTimeoutTest {
    private val authorization = Authorization(domain = "www.esjzone.cc")
    private val url = "https://www.esjzone.cc/inc/mem_record_data.php"

    // ---- timeout classification ----

    @Test fun socketAndCallTimeoutsAreSlowResponses() {
        val read = NetworkRequestException(url, SocketTimeoutException("timeout"))
        val call = NetworkRequestException(url, InterruptedIOException("timeout"))
        assertTrue(read.isRequestTimeout())
        assertTrue(call.isRequestTimeout())
        assertTrue(RuntimeException(IOException(read)).isRequestTimeout())
        // Still a network failure for every other caller.
        assertEquals(LoadFailureKind.NETWORK, read.loadFailureKind())

        assertFalse(NetworkRequestException(url, ConnectException("refused")).isRequestTimeout())
        assertFalse(ExperienceRecordException("Experience record status 500").isRequestTimeout())
        assertFalse(IOException("Canceled").isRequestTimeout())
    }

    // ---- client configuration ----

    private fun response(request: Request, body: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(body.toResponseBody())
        .build()

    private val sharedField = EsjzoneClient::class.java.getDeclaredField("sharedHttpClient")
        .apply { isAccessible = true }
    private val originalShared = sharedField.get(EsjzoneClient) as OkHttpClient

    @After fun restoreSharedClient() {
        sharedField.set(EsjzoneClient, originalShared)
    }

    private fun sharedLikeProduction(builder: OkHttpClient.Builder = OkHttpClient.Builder()): OkHttpClient =
        builder
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

    @Test fun recordClientOnlyExtendsDataRequestBudget() {
        val budgets = Collections.synchronizedList(mutableListOf<Triple<String, Int, Long>>())
        val shared = sharedLikeProduction(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            budgets += Triple(
                request.url.encodedPath,
                chain.readTimeoutMillis(),
                chain.call().timeout().timeoutNanos()
            )
            val body = if (request.url.encodedPath == "/my/record") "<JinJing>t</JinJing>"
                else """{"status":200,"total":0,"rows":[]}"""
            response(request, body)
        })
        sharedField.set(EsjzoneClient, shared)

        val base = EsjzoneClient.memberRecordBaseClient()
        assertFalse(base.followRedirects)
        assertSame(shared.connectionPool, base.connectionPool)
        assertSame(base, EsjzoneClient.memberRecordBaseClient())

        val ordinary = EsjzoneClient.authenticatedClient(authorization)
        assertEquals(15_000, ordinary.readTimeoutMillis)
        assertEquals(30_000, ordinary.callTimeoutMillis)

        EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.MESSAGE, 0)
        assertEquals(
            listOf(
                Triple("/my/record", 15_000, TimeUnit.SECONDS.toNanos(30)),
                Triple("/inc/mem_record_data.php", 60_000, TimeUnit.SECONDS.toNanos(90))
            ),
            budgets.toList()
        )

        val replacement = sharedLikeProduction()
        sharedField.set(EsjzoneClient, replacement)
        assertSame(replacement.connectionPool, EsjzoneClient.memberRecordBaseClient().connectionPool)
    }

    @Test fun eachPageObtainsAndSendsItsOwnToken() {
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        var tokenCount = 0
        sharedField.set(EsjzoneClient, sharedLikeProduction(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val body = when (request.url.encodedPath) {
                "/my/record" -> "<JinJing>fixture-token-${++tokenCount}</JinJing>"
                "/inc/mem_record_data.php" -> {
                    if (request.header("Authorization") == "fixture-token-$tokenCount") {
                        """{"status":200,"total":45,"rows":[{"cdate":"2026-11-11","exp":"+5"}]}"""
                    } else {
                        """{"status":301,"total":0,"rows":[]}"""
                    }
                }
                else -> error("Unexpected request path")
            }
            response(request, body)
        }))

        val first = EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.COMMENT, 0)
        val next = EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.COMMENT, 30)
        assertEquals(45, first.total)
        assertEquals("+5", first.rows.single().exp)
        assertEquals("+5", next.rows.single().exp)

        assertEquals(listOf("POST", "GET", "POST", "GET"), requests.map { it.method })
        for (index in listOf(0, 2)) {
            val tokenRequest = requests[index]
            val form = tokenRequest.body as FormBody
            assertEquals("/my/record", tokenRequest.url.encodedPath)
            assertEquals("plxf", form.name(0))
            assertEquals("getAuthToken", form.value(0))

            val recordsRequest = requests[index + 1]
            assertEquals("https://www.esjzone.cc/my/record", recordsRequest.header("Referer"))
            assertEquals("XMLHttpRequest", recordsRequest.header("X-Requested-With"))
            assertEquals("fixture-token-${index / 2 + 1}", recordsRequest.header("Authorization"))
            assertEquals("type:3", recordsRequest.url.queryParameter("search"))
            assertEquals(if (index == 0) "0" else "30", recordsRequest.url.queryParameter("offset"))
        }
    }

    @Test fun missingTokenStopsBeforeRequestingRecords() {
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        sharedField.set(EsjzoneClient, sharedLikeProduction(OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            response(chain.request(), "<html>login</html>")
        }))

        assertThrows(ExperienceRecordException::class.java) {
            EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.POST, 0)
        }
        assertEquals(listOf("/my/record"), requests.map { it.url.encodedPath })
    }

    @Test fun rejectedTokenIsRefreshedOnceThenSucceeds() {
        val requests = Collections.synchronizedList(mutableListOf<Request>())
        var tokens = 0
        var data = 0
        sharedField.set(EsjzoneClient, sharedLikeProduction(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val body = if (request.url.encodedPath == "/my/record") "<JinJing>token-${++tokens}</JinJing>"
                else if (++data == 1) """{"status":301,"total":0,"rows":[]}"""
                else """{"status":200,"total":1,"rows":[{"cdate":"2026-11-11","exp":"+1"}]}"""
            response(request, body)
        }))

        val page = EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.MESSAGE, 0)

        assertEquals("+1", page.rows.single().exp)
        assertEquals(listOf("POST", "GET", "POST", "GET"), requests.map { it.method })
        assertEquals("token-1", requests[1].header("Authorization"))
        assertEquals("token-2", requests[3].header("Authorization"))
    }

    // ---- cancellation reaches the HTTP call ----

    @Test fun cancellingTheCallerCancelsTheSlowCall() = runBlocking {
        val entered = CountDownLatch(1)
        val sawCancel = AtomicBoolean(false)
        sharedField.set(EsjzoneClient, sharedLikeProduction(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            if (request.url.encodedPath == "/my/record") return@addInterceptor response(request, "<JinJing>t</JinJing>")
            entered.countDown()
            // Stand-in for a server that has not started answering yet.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline && !chain.call().isCanceled()) {
                try { Thread.sleep(10) } catch (_: InterruptedException) { }
            }
            sawCancel.set(chain.call().isCanceled())
            throw IOException("Canceled")
        }))

        val job = launch(Dispatchers.Default) {
            cancellablePageRequest {
                EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.MESSAGE, 0)
            }
        }
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!sawCancel.get() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue("OkHttp call was not cancelled", sawCancel.get())
    }

    // ---- controller: type switching and stale responses ----

    private class FakeLoader {
        val pending = mutableMapOf<Pair<ExperienceRecordType, Int>, CompletableDeferred<ExperienceRecordPage>>()
        val calls = Collections.synchronizedList(mutableListOf<Pair<ExperienceRecordType, Int>>())
        val cancelled = Collections.synchronizedList(mutableListOf<ExperienceRecordType>())
        /** When true the fake ignores cancellation, standing in for a late response. */
        var ignoreCancellation = false

        fun deferred(type: ExperienceRecordType, offset: Int = 0) =
            synchronized(pending) { pending.getOrPut(type to offset) { CompletableDeferred() } }

        suspend fun load(type: ExperienceRecordType, offset: Int): ExperienceRecordPage {
            calls += type to offset
            val result = deferred(type, offset)
            return try {
                if (ignoreCancellation) {
                    // Not cancellable: resumes even after the job was cancelled, like a
                    // blocking call that returns after the user switched categories.
                    suspendCoroutine { continuation ->
                        result.invokeOnCompletion {
                            continuation.resumeWith(runCatching { result.getCompleted() })
                        }
                    }
                } else result.await()
            } catch (error: CancellationException) {
                cancelled += type
                throw error
            }
        }
    }

    private fun page(vararg notes: String) =
        ExperienceRecordPage(total = notes.size, rows = notes.map { ExperienceRecord("t", "1", it) })

    private fun controller(loader: FakeLoader): Pair<ExperienceRecordsController, MutableStateFlow<ExperienceRecordsState>> {
        val state = MutableStateFlow(ExperienceRecordsState(type = ExperienceRecordType.POST))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return ExperienceRecordsController(scope, state, loader::load) to state
    }

    @Test fun switchingTypeCancelsPreviousRequestAndDeduplicatesReselection() {
        val loader = FakeLoader()
        val (controller, state) = controller(loader)
        controller.load()
        assertTrue(state.value.loading)
        controller.selectType(ExperienceRecordType.MESSAGE)
        controller.selectType(ExperienceRecordType.MESSAGE)
        assertEquals(listOf(ExperienceRecordType.POST), loader.cancelled.toList())
        assertEquals(listOf(ExperienceRecordType.POST to 0, ExperienceRecordType.MESSAGE to 0), loader.calls.toList())
        assertEquals(ExperienceRecordType.MESSAGE, state.value.type)
        assertTrue(state.value.loading)

        loader.deferred(ExperienceRecordType.MESSAGE).complete(page("message"))
        assertEquals(listOf("message"), state.value.rows.map { it.note })
        assertFalse(state.value.loading)
    }

    @Test fun lateResponseNeverOverwritesTheSelectedType() {
        val loader = FakeLoader().apply { ignoreCancellation = true }
        val (controller, state) = controller(loader)
        controller.load()
        controller.selectType(ExperienceRecordType.MESSAGE)
        loader.deferred(ExperienceRecordType.MESSAGE).complete(page("message"))
        // The POST request comes back afterwards, despite being cancelled.
        loader.deferred(ExperienceRecordType.POST).complete(page("post-1", "post-2"))
        assertEquals(ExperienceRecordType.MESSAGE, state.value.type)
        assertEquals(listOf("message"), state.value.rows.map { it.note })

        // A late failure from an old type is ignored the same way.
        loader.pending.remove(ExperienceRecordType.POST to 0)
        controller.selectType(ExperienceRecordType.COMMENT)
        controller.selectType(ExperienceRecordType.POST)
        loader.deferred(ExperienceRecordType.COMMENT)
            .completeExceptionally(NetworkRequestException(url, SocketTimeoutException("timeout")))
        assertEquals(ExperienceRecordType.POST, state.value.type)
        assertTrue(state.value.loading)
        assertFalse(state.value.timedOut)
    }

    @Test fun timeoutIsReportedWithoutAutomaticRetry() {
        val loader = FakeLoader()
        val (controller, state) = controller(loader)
        controller.selectType(ExperienceRecordType.MESSAGE)
        loader.deferred(ExperienceRecordType.MESSAGE)
            .completeExceptionally(NetworkRequestException(url, SocketTimeoutException("timeout")))
        assertTrue(state.value.timedOut)
        assertEquals(LoadFailureKind.NETWORK, state.value.failure)
        assertFalse(state.value.loading)
        assertEquals(1, loader.calls.size)

        // Retry is explicit and clears the slow-response flag while it runs.
        loader.pending.clear()
        controller.retry()
        assertEquals(2, loader.calls.size)
        assertTrue(state.value.loading)
        assertFalse(state.value.timedOut)
        loader.deferred(ExperienceRecordType.MESSAGE).completeExceptionally(ConnectException("refused"))
        assertFalse(state.value.timedOut)
        assertEquals(LoadFailureKind.NETWORK, state.value.failure)
    }

    @Test fun cachedCategoryIsShownDuringRefreshAndSurvivesTimeout() {
        val loader = FakeLoader()
        val cached = ExperienceRecordsState(
            type = ExperienceRecordType.MESSAGE,
            rows = listOf(ExperienceRecord("t", "+1", "cached")),
            total = 1, loaded = true, endReached = true
        )
        val cache = object : ExperienceRecordsCache {
            override suspend fun read(type: ExperienceRecordType) = cached.takeIf { it.type == type }
            override fun writer(type: ExperienceRecordType): suspend (ExperienceRecordsState) -> Unit = {}
        }
        val state = MutableStateFlow(ExperienceRecordsState())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ExperienceRecordsController(scope, state, loader::load, cache = cache)

        controller.load()
        assertEquals(listOf("cached"), state.value.rows.map { it.note })
        assertTrue(state.value.refreshing)
        loader.deferred(ExperienceRecordType.MESSAGE)
            .completeExceptionally(NetworkRequestException(url, SocketTimeoutException("timeout")))
        assertEquals(listOf("cached"), state.value.rows.map { it.note })
        assertTrue(state.value.refreshFailed)
        assertTrue(state.value.timedOut)
        assertFalse(state.value.loading)
    }

    @Test fun successfulPagesReplaceAndThenExtendTheCachedSnapshot() {
        val loader = FakeLoader()
        val stored = mutableMapOf<ExperienceRecordType, ExperienceRecordsState>()
        val cache = object : ExperienceRecordsCache {
            override suspend fun read(type: ExperienceRecordType) = stored[type]
            override fun writer(type: ExperienceRecordType): suspend (ExperienceRecordsState) -> Unit = { stored[type] = it }
        }
        val state = MutableStateFlow(ExperienceRecordsState())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ExperienceRecordsController(scope, state, loader::load, cache = cache)
        controller.load()
        val firstPage = (1..30).map { ExperienceRecord("t", "+1", "row-$it") }
        loader.deferred(ExperienceRecordType.MESSAGE).complete(ExperienceRecordPage(total = 31, rows = firstPage))
        assertEquals(30, stored[ExperienceRecordType.MESSAGE]?.rows?.size)
        controller.loadMore()
        loader.deferred(ExperienceRecordType.MESSAGE, 30)
            .complete(ExperienceRecordPage(total = 31, rows = listOf(ExperienceRecord("t", "+1", "last"))))
        assertEquals(31, stored[ExperienceRecordType.MESSAGE]?.rows?.size)
        assertTrue(stored.getValue(ExperienceRecordType.MESSAGE).endReached)
    }

    @Test fun switchingCategoriesRestoresOnlyTheSelectedCachedRecords() {
        val loader = FakeLoader()
        val cached = listOf(ExperienceRecordType.MESSAGE, ExperienceRecordType.COMMENT).associateWith { type ->
            ExperienceRecordsState(type = type, rows = listOf(ExperienceRecord("t", "+1", type.name)),
                total = 1, loaded = true, endReached = true)
        }
        val cache = object : ExperienceRecordsCache {
            override suspend fun read(type: ExperienceRecordType) = cached[type]
            override fun writer(type: ExperienceRecordType): suspend (ExperienceRecordsState) -> Unit = {}
        }
        val state = MutableStateFlow(ExperienceRecordsState())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ExperienceRecordsController(scope, state, loader::load, cache = cache)

        controller.load()
        assertEquals("MESSAGE", state.value.rows.single().note)
        controller.selectType(ExperienceRecordType.COMMENT)
        assertEquals("COMMENT", state.value.rows.single().note)
        assertEquals(listOf(ExperienceRecordType.MESSAGE), loader.cancelled.toList())
        loader.deferred(ExperienceRecordType.COMMENT).complete(page("fresh comment"))
        assertEquals("fresh comment", state.value.rows.single().note)
    }
}

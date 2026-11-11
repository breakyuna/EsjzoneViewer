package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.NetworkRequestException
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.features.ExperienceRecordException
import com.breakyuna.esjzone.network.features.experienceRecordStatus
import com.breakyuna.esjzone.network.features.getExperienceRecords
import com.breakyuna.esjzone.network.features.isRequestTimeout
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecord
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordPage
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordType
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
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
    }

    @Test fun otherFailuresAreNotTimeouts() {
        assertFalse(NetworkRequestException(url, ConnectException("refused")).isRequestTimeout())
        assertFalse(ExperienceRecordException("Experience record status 500").isRequestTimeout())
        assertFalse(IOException("Canceled").isRequestTimeout())
    }

    @Test fun statusIsReadFromJsonOnly() {
        assertEquals("301", experienceRecordStatus("""{"status":301,"total":0,"rows":[]}"""))
        assertEquals("200", experienceRecordStatus(""" {"status":"200"} """))
        assertNull(experienceRecordStatus("<html>login</html>"))
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

    @Test fun recordClientOnlyExtendsItsOwnBudget() {
        val shared = sharedLikeProduction()
        sharedField.set(EsjzoneClient, shared)

        val base = EsjzoneClient.memberRecordBaseClient()
        assertEquals(60_000, base.readTimeoutMillis)
        assertEquals(90_000, base.callTimeoutMillis)
        assertEquals(5_000, base.connectTimeoutMillis)
        assertFalse(base.followRedirects)
        assertSame(shared.connectionPool, base.connectionPool)
        // Built once per shared client, not per request.
        assertSame(base, EsjzoneClient.memberRecordBaseClient())

        val scoped = EsjzoneClient.memberRecordClient(authorization)
        assertEquals(60_000, scoped.readTimeoutMillis)
        assertEquals(90_000, scoped.callTimeoutMillis)
        assertFalse(scoped.followRedirects)
        assertSame(shared.connectionPool, scoped.connectionPool)

        // The shared client and ordinary authenticated requests keep 15 s / 30 s.
        val ordinary = EsjzoneClient.authenticatedClient(authorization)
        assertEquals(15_000, ordinary.readTimeoutMillis)
        assertEquals(30_000, ordinary.callTimeoutMillis)
        assertEquals(15_000, (sharedField.get(EsjzoneClient) as OkHttpClient).readTimeoutMillis)

        // Replacing the shared client (app initialization) rebuilds the profile from it.
        val replacement = sharedLikeProduction()
        sharedField.set(EsjzoneClient, replacement)
        assertSame(replacement.connectionPool, EsjzoneClient.memberRecordBaseClient().connectionPool)
    }

    @Test fun onlyTheDataRequestUsesTheLongBudget() {
        val budgets = Collections.synchronizedList(mutableListOf<Triple<String, Int, Long>>())
        sharedField.set(EsjzoneClient, sharedLikeProduction(OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            budgets += Triple(
                request.url.encodedPath,
                chain.readTimeoutMillis(),
                chain.call().timeout().timeoutNanos()
            )
            val body = if (request.url.encodedPath == "/my/record") "<JinJing>t</JinJing>"
                else """{"status":200,"total":0,"rows":[]}"""
            response(request, body)
        }))

        EsjzoneClient.getExperienceRecords(authorization, ExperienceRecordType.MESSAGE, 0)

        assertEquals(
            listOf(
                Triple("/my/record", 15_000, TimeUnit.SECONDS.toNanos(30)),
                Triple("/inc/mem_record_data.php", 60_000, TimeUnit.SECONDS.toNanos(90))
            ),
            budgets.toList()
        )
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
        val state = MutableStateFlow(ExperienceRecordsState())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        return ExperienceRecordsController(scope, state, loader::load) to state
    }

    @Test fun switchingTypeCancelsThePreviousRequest() {
        val loader = FakeLoader()
        val (controller, state) = controller(loader)
        controller.load()
        assertTrue(state.value.loading)
        controller.selectType(ExperienceRecordType.MESSAGE)
        assertEquals(listOf(ExperienceRecordType.POST), loader.cancelled.toList())
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

    @Test fun reselectingTheLoadingTypeDoesNotStackRequests() {
        val loader = FakeLoader()
        val (controller, _) = controller(loader)
        controller.selectType(ExperienceRecordType.MESSAGE)
        controller.selectType(ExperienceRecordType.MESSAGE)
        assertEquals(listOf(ExperienceRecordType.MESSAGE to 0), loader.calls.toList())
        assertTrue(loader.cancelled.isEmpty())
    }
}

package com.breakyuna.esjzone.network

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call

/** Bridges the synchronous HTML parser APIs to page jobs that users can cancel. */
internal suspend fun <T> cancellablePageRequest(background: Boolean = false, block: () -> T): T = suspendCancellableCoroutine { continuation ->
    val state = PageRequestCancellation(background)
    continuation.invokeOnCancellation { state.cancel() }
    Dispatchers.IO.dispatch(continuation.context, Runnable {
        if (!continuation.isActive) return@Runnable
        val previous = pageRequestCancellation.get()
        pageRequestCancellation.set(state)
        state.attach(Thread.currentThread())
        try {
            if (continuation.isActive) continuation.resume(block())
        } catch (error: Throwable) {
            if (continuation.isActive) continuation.resumeWithException(error)
        } finally {
            state.detach()
            pageRequestCancellation.set(previous)
            // A cancelled request must not leave the pooled IO thread interrupted.
            Thread.interrupted()
        }
    })
}

internal val pageRequestCancellation = ThreadLocal<PageRequestCancellation?>()

internal class PageRequestCancellation(val background: Boolean = false) {
    private var thread: Thread? = null
    private var call: Call? = null
    private var cancelled = false

    @Synchronized fun attach(current: Thread) {
        thread = current
        if (cancelled) current.interrupt()
    }

    @Synchronized fun attach(call: Call) {
        this.call = call
        if (cancelled) call.cancel()
    }

    @Synchronized fun detachCall() { call = null }

    @Synchronized fun detach() { thread = null; call = null }

    @Synchronized fun cancel() {
        cancelled = true
        call?.cancel()
        thread?.interrupt() // Wakes a permit or coalesced-future wait.
    }

    @Synchronized fun isCancelled(): Boolean = cancelled
}

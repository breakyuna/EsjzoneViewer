package com.breakyuna.esjzone

import com.breakyuna.esjzone.offline.NovelExportController
import com.breakyuna.esjzone.offline.NovelExportOutcome
import com.breakyuna.esjzone.offline.NovelExportTarget
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelExportControllerTest {

    private class FakeTarget(
        private val openResult: Boolean = true,
        private val deleteResult: Boolean = true
    ) : NovelExportTarget {
        val modes = Collections.synchronizedList(mutableListOf<String>())
        val bytes = ByteArrayOutputStream()
        @Volatile var closed = false
        @Volatile var deleted = 0

        override fun openOutputStream(mode: String): OutputStream? {
            modes += mode
            if (!openResult) return null
            return object : OutputStream() {
                override fun write(b: Int) = bytes.write(b)
                override fun close() { closed = true }
            }
        }

        override fun delete(): Boolean {
            deleted++
            return deleteResult
        }
    }

    private fun newScope() = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private class Outcomes {
        val list = Collections.synchronizedList(mutableListOf<NovelExportOutcome>())
        val latch = CountDownLatch(1)
        fun record(outcome: NovelExportOutcome) { list += outcome; latch.countDown() }
        fun await(): NovelExportOutcome {
            assertTrue("no outcome reported", latch.await(5, TimeUnit.SECONDS))
            return list.single()
        }
    }

    @Test
    fun successWritesWithTruncateModeAndKeepsDocument() = runBlocking {
        val outcomes = Outcomes()
        val controller = NovelExportController(newScope(), Dispatchers.IO, outcomes::record)
        val target = FakeTarget()

        val job = controller.start(target) { out, checkActive ->
            checkActive()
            out.write("hello".toByteArray())
        }!!
        withTimeout(5_000) { job.join() }

        assertEquals(NovelExportOutcome.Success, outcomes.await())
        assertEquals(listOf("wt"), target.modes)
        assertEquals("hello", target.bytes.toString(Charsets.UTF_8.name()))
        assertTrue(target.closed)
        assertEquals(0, target.deleted)
        assertFalse(controller.isExporting.value)
    }

    @Test
    fun failureDeletesDocumentAndReportsError() = runBlocking {
        val outcomes = Outcomes()
        val controller = NovelExportController(newScope(), Dispatchers.IO, outcomes::record)
        val target = FakeTarget()
        val boom = IOException("disk full")

        val job = controller.start(target) { out, _ ->
            out.write(1)
            throw boom
        }!!
        withTimeout(5_000) { job.join() }

        val outcome = outcomes.await() as NovelExportOutcome.Failed
        assertTrue(outcome.removed)
        assertEquals("disk full", outcome.error?.message)
        assertEquals(1, target.deleted)
        assertTrue(target.closed)
        assertFalse(controller.isExporting.value)
    }

    @Test
    fun unopenableDocumentIsDeletedAndReportedAsFailure() = runBlocking {
        val outcomes = Outcomes()
        val controller = NovelExportController(newScope(), Dispatchers.IO, outcomes::record)
        val target = FakeTarget(openResult = false, deleteResult = false)

        val job = controller.start(target) { _, _ -> error("must not write") }!!
        withTimeout(5_000) { job.join() }

        val outcome = outcomes.await() as NovelExportOutcome.Failed
        assertFalse(outcome.removed)
        assertEquals(listOf("wt"), target.modes)
        assertEquals(1, target.deleted)
    }

    @Test
    fun cancellingOwnerScopeStopsWriterAndDeletesDocument() = runBlocking {
        val outcomes = Outcomes()
        val scope = newScope()
        val controller = NovelExportController(scope, Dispatchers.IO, outcomes::record)
        val target = FakeTarget()
        val started = CountDownLatch(1)
        var chaptersAfterCancel = 0

        val job = controller.start(target) { out, checkActive ->
            started.countDown()
            // Simulates the chapter loop: blocking work that polls for cancellation.
            repeat(10_000) {
                checkActive()
                out.write(it)
                Thread.sleep(1)
                if (!scope.coroutineContext[kotlinx.coroutines.Job]!!.isActive) chaptersAfterCancel++
            }
        }!!
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertTrue(controller.isExporting.value)
        scope.cancel() // ViewModel cleared: page left
        withTimeout(5_000) { job.join() }

        val outcome = outcomes.await() as NovelExportOutcome.Cancelled
        assertTrue(outcome.removed)
        assertEquals(1, target.deleted)
        assertTrue(target.closed)
        assertTrue("writer kept running after cancel", chaptersAfterCancel <= 1)
        assertFalse(controller.isExporting.value)
    }

    @Test
    fun secondExportWhileRunningIsRejectedAndItsDocumentDiscarded() = runBlocking {
        val outcomes = Outcomes()
        val all = Collections.synchronizedList(mutableListOf<NovelExportOutcome>())
        val busyLatch = CountDownLatch(1)
        val controller = NovelExportController(newScope(), Dispatchers.IO) {
            all += it
            if (it is NovelExportOutcome.Busy) busyLatch.countDown() else outcomes.record(it)
        }
        val first = FakeTarget()
        val second = FakeTarget()
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)

        val job = controller.start(first) { out, _ ->
            started.countDown()
            release.await(5, TimeUnit.SECONDS)
            out.write(1)
        }!!
        assertTrue(started.await(5, TimeUnit.SECONDS))
        assertNull(controller.start(second) { _, _ -> error("must not run") })
        assertTrue(busyLatch.await(5, TimeUnit.SECONDS))
        release.countDown()
        withTimeout(5_000) { job.join() }

        assertEquals(NovelExportOutcome.Success, outcomes.await())
        assertEquals(NovelExportOutcome.Busy(removed = true), all.first { it is NovelExportOutcome.Busy })
        assertTrue(second.modes.isEmpty())
        assertEquals(1, second.deleted)
        assertEquals(0, first.deleted)

        // Lock is released afterwards.
        val third = FakeTarget()
        val next = controller.start(third) { out, _ -> out.write(2) }
        assertTrue(next != null)
        withTimeout(5_000) { next!!.join() }
    }
}

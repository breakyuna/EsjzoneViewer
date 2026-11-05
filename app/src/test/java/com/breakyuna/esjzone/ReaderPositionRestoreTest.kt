package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.restoreReaderPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPositionRestoreTest {
    @Test fun exactDeadlineDoesNotCountAsSuccessWhenFallbackIsUnconfirmed() = runBlocking {
        var fallbackCalled = false
        assertFalse(restoreReaderPosition(exact = { awaitCancellation() }, fallback = {
            fallbackCalled = true
            false
        }))
        assertTrue(fallbackCalled)
    }

    @Test fun confirmedExactPositionDoesNotUseFallback() = runBlocking {
        var fallbackCalled = false
        assertTrue(restoreReaderPosition(exact = { true }, fallback = { fallbackCalled = true; false }))
        assertFalse(fallbackCalled)
    }

    @Test fun exactFailureOnlySucceedsWithAConfirmedFallback() = runBlocking {
        var fallbackCalled = false
        assertTrue(restoreReaderPosition(exact = { error("Layout unavailable") }, fallback = {
            fallbackCalled = true
            true
        }))
        assertTrue(fallbackCalled)
        assertFalse(restoreReaderPosition(exact = { false }, fallback = { false }))
        assertFalse(restoreReaderPosition(exact = { error("Seek failed") }, fallback = { error("No measured target") }))
    }

    @Test fun leavingReaderCancelsRestorationWithoutFallback() = runBlocking {
        var fallbackCalled = false
        var cancelled = false
        try {
            restoreReaderPosition(exact = { throw CancellationException("Reader disposed") }, fallback = {
                fallbackCalled = true
                true
            })
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        assertFalse(fallbackCalled)
    }
}

package com.breakyuna.esjzone

import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.page.ReaderBoundaryVerification
import com.breakyuna.esjzone.ui.page.ReaderBoundaryVerifications
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderBoundaryVerificationTest {
    private val previous = Chapter("Previous", "/forum/1/1.html", false)
    private val next = Chapter("Next", "/forum/1/3.html", false)

    @Test fun oppositeSuccessCannotRemoveAnUnresolvedGate() {
        val gates = ReaderBoundaryVerifications()
        gates.remember(ReaderBoundaryVerification(previous, -1))
        gates.resolve(next)
        assertEquals(previous, gates.current?.chapter)
        assertTrue(gates.blocks(-1))
        assertFalse(gates.blocks(1))
    }

    @Test fun bothDirectionsSurviveAndRetryOnlyRemovesItsOwnGate() {
        val gates = ReaderBoundaryVerifications()
        gates.remember(ReaderBoundaryVerification(previous, -1))
        gates.remember(ReaderBoundaryVerification(next, 1))
        gates.markStorageUnavailable(next)
        assertEquals(previous, gates.current?.chapter)
        val retry = gates.take(next)
        assertEquals(next, retry?.chapter)
        assertTrue(retry?.storageUnavailable == true)
        assertEquals(previous, gates.current?.chapter)
        assertNull(gates.take(next))
        gates.remember(ReaderBoundaryVerification(next, 1, storageUnavailable = true))
        gates.resolve(previous.copy(url = "https://www.esjzone.one/forum/1/1.html"))
        assertEquals(next, gates.current?.chapter)
        assertTrue(gates.current?.storageUnavailable == true)
        gates.clear()
        assertNull(gates.current)
        assertFalse(gates.blocks(1))
    }

    @Test fun movingTheWindowDropsOnlyGatesThatAreNoLongerAdjacent() {
        val gates = ReaderBoundaryVerifications()
        gates.remember(ReaderBoundaryVerification(previous, -1))
        gates.remember(ReaderBoundaryVerification(next, 1))
        gates.retainTargets(previous, next)
        assertTrue(gates.blocks(-1, previous))
        assertFalse(gates.blocks(-1, next))
        gates.retainTargets(next, next)
        assertFalse(gates.blocks(-1))
        assertEquals(next, gates.current?.chapter)
        assertTrue(gates.blocks(1, next))
    }
}

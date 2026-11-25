package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.ReaderPageTurnQueue
import org.junit.Assert.*
import org.junit.Test

class ReaderPageTurnQueueTest {
    @Test fun tapsPreserveOrderAccelerateUnderRapidInputAndResetAfterDrain() {
        val queue = ReaderPageTurnQueue()
        val inputs = listOf(true, true, false, true, false)
        queue.enqueue(inputs.first(), 0)
        val normal = queue.durationMillis
        inputs.drop(1).forEachIndexed { index, forward -> queue.enqueue(forward, (index + 1) * 60L) }
        assertTrue(queue.durationMillis < normal)

        val completed = mutableListOf<Boolean>()
        while (queue.hasTurns) {
            completed += queue.next!!
            assertTrue(queue.durationMillis < normal)
            queue.complete()
        }
        assertEquals(inputs, completed)
        assertNull(queue.next)

        queue.enqueue(false, 2000)
        assertEquals(normal, queue.durationMillis, 0.001f)
    }

    @Test fun clearDiscardsPendingTurnsResetsSpeedAndIgnoresLateCompletions() {
        val queue = ReaderPageTurnQueue()
        queue.enqueue(true, 0)
        val normal = queue.durationMillis
        queue.enqueue(false, 20)
        queue.clear()
        queue.complete()
        assertFalse(queue.hasTurns)
        queue.enqueue(false, 30)
        assertEquals(false, queue.next)
        assertEquals(normal, queue.durationMillis, 0.001f)
        queue.complete()
        queue.complete()
        assertNull(queue.next)
    }
}

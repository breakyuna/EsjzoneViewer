package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.reader.ReaderPageTurnQueue
import org.junit.Assert.*
import org.junit.Test

class ReaderPageTurnQueueTest {
    @Test fun everyTapRetainsItsDirectionAndOrder() {
        val queue = ReaderPageTurnQueue()
        val inputs = listOf(true, true, false, true, false)
        inputs.forEachIndexed { index, forward -> queue.enqueue(forward, index * 60L) }
        val completed = mutableListOf<Boolean>()
        while (queue.hasTurns) {
            completed += queue.next!!
            queue.complete()
        }
        assertEquals(inputs, completed)
        assertNull(queue.next)
    }

    @Test fun newTapAcceleratesActiveTurnAndFastPaceLastsUntilQueueDrains() {
        val queue = ReaderPageTurnQueue()
        queue.enqueue(true, 0)
        val normal = queue.durationMillis
        queue.enqueue(true, 100)
        val accelerated = queue.durationMillis
        assertTrue(accelerated < normal)
        assertEquals(true, queue.next)
        queue.complete()
        assertTrue(queue.durationMillis < normal)
        queue.complete()
        queue.enqueue(false, 2000)
        assertEquals(normal, queue.durationMillis, 0.001f)
    }

    @Test fun heavyBurstKeepsAllTurnsAndNeverReachesZeroDuration() {
        val queue = ReaderPageTurnQueue()
        repeat(100) { queue.enqueue(it % 2 == 0, it.toLong()) }
        repeat(100) { index ->
            assertEquals(index % 2 == 0, queue.next)
            assertTrue(queue.durationMillis >= 48f)
            queue.complete()
        }
        assertFalse(queue.hasTurns)
    }

    @Test fun leavingReaderDiscardsOldTurnsAndResetsSpeed() {
        val queue = ReaderPageTurnQueue()
        queue.enqueue(true, 0)
        val normal = queue.durationMillis
        queue.enqueue(false, 20)
        queue.clear()
        assertFalse(queue.hasTurns)
        queue.enqueue(false, 30)
        assertEquals(false, queue.next)
        assertEquals(normal, queue.durationMillis, 0.001f)
    }

    @Test fun completionAfterClearLeavesQueueReadyForNewTurns() {
        val queue = ReaderPageTurnQueue()
        queue.enqueue(true, 0)
        val normal = queue.durationMillis
        queue.enqueue(false, 20)
        queue.clear()
        queue.complete()
        assertFalse(queue.hasTurns)
        queue.enqueue(false, 2000)
        assertEquals(false, queue.next)
        assertEquals(normal, queue.durationMillis, 0.001f)
        queue.complete()
        queue.complete()
        assertNull(queue.next)
    }
}

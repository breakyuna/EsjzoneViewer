package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.PageRequestGate
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class PageRequestGateTest {
    @Test fun speculativeRequestsCannotConsumeForegroundCapacity() {
        val gate = PageRequestGate(3)
        assertTrue(gate.acquire(true, 0, TimeUnit.MILLISECONDS))
        assertFalse(gate.acquire(true, 0, TimeUnit.MILLISECONDS))
        assertTrue(gate.acquire(false, 0, TimeUnit.MILLISECONDS))
        assertTrue(gate.acquire(false, 0, TimeUnit.MILLISECONDS))
        assertFalse(gate.acquire(false, 0, TimeUnit.MILLISECONDS))
        gate.release(false)
        assertTrue(gate.acquire(false, 0, TimeUnit.MILLISECONDS))
        gate.release(true)
        gate.release(false)
        gate.release(false)
        assertTrue(gate.acquire(true, 0, TimeUnit.MILLISECONDS))
        gate.release(true)
    }
}

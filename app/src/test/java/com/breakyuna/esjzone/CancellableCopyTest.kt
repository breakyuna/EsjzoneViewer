package com.breakyuna.esjzone

import com.breakyuna.esjzone.util.copyWithCancellation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CancellableCopyTest {
    @Test fun cancellationStopsBeforeWritingTheNextChunk() {
        val bytes = ByteArray(256 * 1024) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        try {
            ByteArrayInputStream(bytes).copyWithCancellation(output, ByteArray(64 * 1024)) {
                if (output.size() >= 64 * 1024) throw CancellationException("Cancelled fixture transfer")
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(64 * 1024, output.size())
        assertArrayEquals(bytes.copyOf(output.size()), output.toByteArray())
    }

    @Test fun copiesAllBytesWithoutClosingCallerStreams() {
        val bytes = ByteArray(130_001) { (it % 251).toByte() }
        val output = object : ByteArrayOutputStream() {
            var closed = false
            override fun close() { closed = true; super.close() }
        }
        ByteArrayInputStream(bytes).copyWithCancellation(output) { }
        assertArrayEquals(bytes, output.toByteArray())
        assertFalse(output.closed)
    }

    @Test fun sharedBufferCopiesDifferentFileSizesWithoutLeakingPreviousBytes() {
        val buffer = ByteArray(64 * 1024)
        for (size in listOf(130_001, 17, 0, 65_536, 65_537)) {
            val bytes = ByteArray(size) { ((it + size) % 251).toByte() }
            val input = object : ByteArrayInputStream(bytes) {
                override fun read(target: ByteArray, offset: Int, length: Int): Int =
                    super.read(target, offset, minOf(length, 997))
            }
            val output = ByteArrayOutputStream()
            input.copyWithCancellation(output, buffer) { }
            assertArrayEquals(bytes, output.toByteArray())
        }
    }
}

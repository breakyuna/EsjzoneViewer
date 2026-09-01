package com.breakyuna.esjzone

import com.breakyuna.esjzone.util.boundedUtf8LogRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogTextTest {
    @Test fun preservesShortRecords() {
        assertEquals("记录😀", boundedUtf8LogRecord("记录😀", 64))
    }

    @Test fun boundsUnicodeAndSmallBudgetsWithoutReplacementCharacters() {
        val record = "日志😀".repeat(100)
        for (budget in listOf(1, 2, 3, 4, 18, 19, 20, 64, 101)) {
            val output = boundedUtf8LogRecord(record, budget)
            assertTrue(output.toByteArray(Charsets.UTF_8).size <= budget)
            assertFalse(output.contains('\uFFFD'))
            assertEquals(output, String(output.toByteArray(Charsets.UTF_8), Charsets.UTF_8))
        }
    }
}

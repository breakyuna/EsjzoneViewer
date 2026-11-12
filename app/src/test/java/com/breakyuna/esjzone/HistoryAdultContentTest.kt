package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.historyContentVisible
import com.breakyuna.esjzone.ui.page.historyRowIncluded
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryAdultContentTest {
    @Test fun togglingContentRestoresOriginalOrderWithoutRemovingHistory() {
        val records = listOf("adult" to true, "unknown" to null, "general" to false)
        assertEquals(listOf("unknown", "general"), records.filter { historyRowIncluded(false, it.second) }.map { it.first })
        // Unknown entries remain eligible for resume, while list cards wait for verification.
        assertEquals(listOf("general"), records.filter { historyContentVisible(false, it.second) }.map { it.first })
        assertEquals(records, records.filter { historyRowIncluded(true, it.second) })
        assertEquals(records, records.filter { historyContentVisible(true, it.second) })
    }

    @Test fun allAdultRecordsProduceEmptyRowsBeforeAdaptiveLayout() {
        assertEquals(emptyList<Boolean?>(), listOf<Boolean?>(true, true).filter { historyRowIncluded(false, it) })
    }
}

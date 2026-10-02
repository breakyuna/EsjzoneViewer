package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.HistoryNovelSnapshot
import com.breakyuna.esjzone.network.features.isHistoryDeleteSuccess
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryDeleteResponseTest {
    @Test
    fun acceptsOnlyConfirmedBusinessSuccess() {
        assertTrue(isHistoryDeleteSuccess("""{"status":200,"msg":"","url":"","id":"","swal":""}"""))
        listOf(
            """{"status":403}""", """{"status":"200"}""", """{"status":200.5}""",
            """{"status":null}""", "{}", "[]", "<html>login</html>", "invalid json", ""
        ).forEach { assertFalse(isHistoryDeleteSuccess(it)) }
    }

    @Test
    fun legacySnapshotCannotSupplyNovelIdForDeletion() {
        val legacy = Gson().fromJson(
            """{"name":"Saved novel","url":"/detail/9001.html","vid":"9001"}""",
            HistoryNovelSnapshot::class.java
        ).toHistoryNovel()
        assertEquals("Saved novel", legacy.name)
        assertEquals("", legacy.vid)
    }

    @Test
    fun verifiedViewIdSurvivesSnapshotRoundTrip() {
        val snapshot = HistoryNovelSnapshot(name = "Saved novel", vid = "9101")
        val restored = Gson().fromJson(Gson().toJson(snapshot), HistoryNovelSnapshot::class.java).toHistoryNovel()
        assertEquals("9101", restored.vid)
    }
}

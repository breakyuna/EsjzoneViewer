package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.CloudViewRecord
import com.breakyuna.esjzone.network.features.CloudViewReportCodec
import com.breakyuna.esjzone.network.features.CloudViewReportStore
import com.breakyuna.esjzone.network.features.StoredCloudView
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CloudViewReportStoreTest {
    private val record = CloudViewRecord(
        site = "www.esjzone.cc",
        novelKey = "id:12",
        novelId = "12",
        novelUrl = "/detail/12.html",
        chapterKey = "/forum/12/1.html",
        chapterUrl = "https://www.esjzone.cc/forum/12/1.html",
        reported = false,
        updatedAt = 100L,
        account = "account-scope",
        generation = 7
    )

    private fun tempFile(): File = File.createTempFile("cloud-view-store", ".json").also { it.deleteOnExit() }

    private fun validJson(novel: String = "id:34") =
        """{"site":"www.esjzone.cc","novelKey":"$novel","novelId":"34","novelUrl":"/detail/34.html",""" +
            """"chapterKey":"/forum/34/8.html","chapterUrl":"https://www.esjzone.cc/forum/34/8.html",""" +
            """"reported":true,"updatedAt":200,"account":"account-scope"}"""

    @Test
    fun recordsRoundTripThroughTheStoredDto() {
        val file = tempFile()
        assertTrue(CloudViewReportStore(file).merge(record))
        // Generations are process-local and are not persisted.
        assertEquals(listOf(record.copy(generation = 0)), CloudViewReportStore(file).snapshot())
    }

    @Test
    fun savedJsonUsesTheSerializedNames() {
        val file = tempFile()
        CloudViewReportStore(file).merge(record)
        val saved = JsonParser.parseString(file.readText()).asJsonArray.single().asJsonObject
        assertEquals(
            setOf("site", "novelKey", "novelId", "novelUrl", "chapterKey", "chapterUrl", "reported", "updatedAt", "account"),
            saved.keySet()
        )
        assertEquals("id:12", saved["novelKey"].asString)
        assertEquals(false, saved["reported"].asBoolean)
        assertEquals(100L, saved["updatedAt"].asLong)
        assertEquals("account-scope", saved["account"].asString)
    }

    @Test
    fun obfuscatedEntriesFromOlderReleasesAreDroppedAndTheFileIsRewritten() {
        val file = tempFile()
        file.writeText(
            """[{"a":"www.esjzone.cc","b":"id:12","c":"12","d":"/detail/12.html","e":"/forum/12/1.html",""" +
                """"f":"https://www.esjzone.cc/forum/12/1.html","g":false,"h":100,"i":"account-scope","j":3},""" +
                "1,\"text\",null," + validJson() + "]"
        )
        val loaded = CloudViewReportStore(file).snapshot()
        assertEquals(listOf("id:34"), loaded.map { it.novelKey })
        assertTrue(loaded.single().reported)
        val rewritten = JsonParser.parseString(file.readText()).asJsonArray
        assertEquals(1, rewritten.size())
        assertEquals("id:34", rewritten.single().asJsonObject["novelKey"].asString)
    }

    @Test
    fun unreadableFileStartsFresh() {
        listOf("not json {", "{\"site\":\"www.esjzone.cc\"}", "").forEach { text ->
            val file = tempFile()
            file.writeText(text)
            val store = CloudViewReportStore(file)
            assertTrue(store.snapshot().isEmpty())
            assertEquals("[]", file.readText())
            assertTrue(store.merge(record))
            assertEquals(listOf("id:12"), CloudViewReportStore(file).snapshot().map { it.novelKey })
        }
    }

    @Test
    fun codecCountsDiscardedEntries() {
        val decoded = CloudViewReportCodec.decode("[" + validJson() + ",{\"novelKey\":\"id:1\"},[]]")
        assertEquals(listOf("id:34"), decoded.records.map { it.novelKey })
        assertEquals(2, decoded.discarded)
        assertEquals(decoded.records, CloudViewReportCodec.decode(CloudViewReportCodec.encode(decoded.records)).records)
    }

    @Test
    fun releaseBuildKeepsTheStoredDto() {
        val rules = File("proguard-rules.pro").readText()
        val className = StoredCloudView::class.java.name
        assertTrue(rules.contains("-keep class $className {"))
    }
}

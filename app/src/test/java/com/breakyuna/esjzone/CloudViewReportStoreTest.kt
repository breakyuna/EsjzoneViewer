package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.CloudViewRecord
import com.breakyuna.esjzone.network.features.CloudViewReportCodec
import com.breakyuna.esjzone.network.features.CloudViewReportStore
import com.breakyuna.esjzone.network.features.StoredCloudView
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun validRecord() = CloudViewRecord(
        "www.esjzone.cc", "id:34", "34", "/detail/34.html", "/forum/34/8.html",
        "https://www.esjzone.cc/forum/34/8.html", true, 200L, "account-scope"
    )

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
    fun obfuscatedEntriesFromOlderReleasesAreReadAndGarbageIsDropped() {
        val file = tempFile()
        val original = """[{"a":"www.esjzone.cc","b":"id:12","c":"12","d":"/detail/12.html","e":"/forum/12/1.html",""" +
            """"f":"https://www.esjzone.cc/forum/12/1.html","g":false,"h":100,"i":"account-scope","j":3},""" +
            """{"a":"www.esjzone.cc","b":"id:56","e":"/forum/56/1.html","j":1},""" +
            "1,\"text\",null," + validJson() + "]"
        file.writeText(original)
        val loaded = CloudViewReportStore(file).snapshot()
        assertEquals(listOf(record.copy(generation = 0), validRecord()), loaded)
        // Entries without an account cannot be reported and are dropped; the
        // original file is kept beside the rewritten one.
        assertEquals(original, File(file.parentFile, "${file.name}.bak").readText())
        val rewritten = JsonParser.parseString(file.readText()).asJsonArray
        assertEquals(listOf("id:12", "id:34"), rewritten.map { it.asJsonObject["novelKey"].asString })
        assertEquals(loaded, CloudViewReportStore(file).snapshot())
    }

    @Test
    fun unreadableFileStartsFreshAndKeepsACopy() {
        listOf("not json {", "{\"site\":\"www.esjzone.cc\"}", "").forEach { text ->
            val file = tempFile()
            file.writeText(text)
            val store = CloudViewReportStore(file)
            assertTrue(store.snapshot().isEmpty())
            assertEquals("[]", file.readText())
            assertEquals(text, File(file.parentFile, "${file.name}.bak").readText())
            assertTrue(store.merge(record))
            assertEquals(listOf("id:12"), CloudViewReportStore(file).snapshot().map { it.novelKey })
        }
    }

    @Test
    fun readableFileLeavesNoCopy() {
        val file = tempFile()
        file.writeText("[" + validJson() + "]")
        assertEquals(listOf(validRecord()), CloudViewReportStore(file).snapshot())
        assertFalse(File(file.parentFile, "${file.name}.bak").exists())
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

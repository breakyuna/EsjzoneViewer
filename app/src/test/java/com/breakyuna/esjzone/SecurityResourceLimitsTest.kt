package com.breakyuna.esjzone

import com.breakyuna.esjzone.util.BoundedInputStream
import com.breakyuna.esjzone.util.BoundedJson
import com.breakyuna.esjzone.ui.reader.ReaderScript
import com.breakyuna.esjzone.ui.reader.WebsiteScriptConverter
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SecurityResourceLimitsTest {
    @Test fun gzipLimitCountsDecodedBytes() {
        val bytes = ByteArrayOutputStream().apply {
            GZIPOutputStream(this).use { it.write(ByteArray(4096)) }
        }.toByteArray()
        assertThrows(IOException::class.java) {
            BoundedInputStream(GZIPInputStream(bytes.inputStream()), 64).use { it.readBytes() }
        }
    }

    @Test fun jsonRejectsDeepNestingAndLargeCollectionsBeforeModelParsing() {
        assertThrows(IOException::class.java) {
            BoundedJson.readText(("[".repeat(65) + "0" + "]".repeat(65)).reader())
        }
        assertThrows(IOException::class.java) {
            BoundedJson.readText(("[" + List(50_001) { "0" }.joinToString(",") + "]").reader())
        }
        val normal = "{\"chapters\":[{\"text\":\"正文\"}]}"
        assertEquals(normal, BoundedJson.readText(normal.reader()))
    }

    @Test fun webpageBudgetIsCumulativeAndReleasedBrowserCannotConvert() {
        var calls = 0
        val converter = WebsiteScriptConverter { text, _ -> calls++; "converted:$text" }
        val text = "字".repeat(32_768)
        repeat(128) { converter.convert(text, ReaderScript.SIMPLIFIED) }
        assertEquals(text, converter.convert(text, ReaderScript.SIMPLIFIED))
        assertEquals(128, calls)
        val closed = WebsiteScriptConverter { _, _ -> calls++; "unexpected" }
        closed.close()
        assertEquals("字", closed.convert("字", ReaderScript.SIMPLIFIED))
        assertEquals(128, calls)
    }
}

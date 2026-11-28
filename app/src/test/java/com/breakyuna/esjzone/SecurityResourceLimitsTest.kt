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

    @Test fun webpageBudgetResetsPerPageBeyondCumulativeCap() {
        var calls = 0
        val converter = WebsiteScriptConverter { text, _ -> calls++; "converted:$text" }
        val chunk = "字".repeat(WebsiteScriptConverter.MAX_TEXT_LENGTH)
        repeat(5) {
            repeat(128) {
                assertEquals("converted:$chunk", converter.convert(chunk, ReaderScript.SIMPLIFIED))
            }
            converter.resetPage()
        }
        assertEquals(640, calls)

        calls = 0
        repeat(5) {
            repeat(1_000) {
                assertEquals("converted:正文", converter.convert("正文", ReaderScript.SIMPLIFIED))
            }
            converter.resetPage()
        }
        assertEquals(5_000, calls)
    }

    @Test fun webpageSinglePageBoundsAndResetRecovery() {
        var calls = 0
        val converter = WebsiteScriptConverter { text, _ -> calls++; "converted:$text" }

        assertEquals("", converter.convert("", ReaderScript.SIMPLIFIED))
        assertEquals("   \n\t ", converter.convert("   \n\t ", ReaderScript.SIMPLIFIED))
        assertEquals("正文", converter.convert("正文", ReaderScript.ORIGINAL))
        val oversized = "字".repeat(WebsiteScriptConverter.MAX_TEXT_LENGTH + 1)
        assertEquals(oversized, converter.convert(oversized, ReaderScript.SIMPLIFIED))
        assertEquals(0, calls)

        val chunk = "字".repeat(WebsiteScriptConverter.MAX_TEXT_LENGTH)
        val chunksPerPage = WebsiteScriptConverter.MAX_PAGE_CHARS / WebsiteScriptConverter.MAX_TEXT_LENGTH
        repeat(chunksPerPage) {
            assertEquals("converted:$chunk", converter.convert(chunk, ReaderScript.SIMPLIFIED))
        }
        assertEquals(chunksPerPage, calls)
        assertEquals(chunk, converter.convert(chunk, ReaderScript.SIMPLIFIED))
        assertEquals(chunksPerPage, calls)

        converter.resetPage()
        assertEquals("converted:正文", converter.convert("正文", ReaderScript.SIMPLIFIED))
        assertEquals(chunksPerPage + 1, calls)

        converter.resetPage()
        calls = 0
        repeat(WebsiteScriptConverter.MAX_PAGE_CALLS) {
            assertEquals("converted:字", converter.convert("字", ReaderScript.SIMPLIFIED))
        }
        assertEquals(WebsiteScriptConverter.MAX_PAGE_CALLS, calls)
        assertEquals("字", converter.convert("字", ReaderScript.SIMPLIFIED))
        assertEquals(WebsiteScriptConverter.MAX_PAGE_CALLS, calls)

        converter.resetPage()
        assertEquals("converted:字", converter.convert("字", ReaderScript.SIMPLIFIED))
        assertEquals(WebsiteScriptConverter.MAX_PAGE_CALLS + 1, calls)
    }

    @Test fun webpageBatchConversionRespectsBoundsAndPreservesEdgeCases() {
        var transformItems = 0
        val converter = WebsiteScriptConverter { text, _ -> transformItems++; "converted:$text" }

        assertEquals(emptyList<String>(), converter.convertBatch(emptyList(), ReaderScript.SIMPLIFIED))
        val blankOnly = listOf("", "   ", "\n\t")
        assertEquals(blankOnly, converter.convertBatch(blankOnly, ReaderScript.SIMPLIFIED))
        assertEquals(listOf("正文"), converter.convertBatch(listOf("正文"), ReaderScript.ORIGINAL))
        assertEquals(0, transformItems)

        val oversized = "字".repeat(WebsiteScriptConverter.MAX_TEXT_LENGTH + 1)
        val mixed = listOf(
            "   ",
            "\"引號\"\\斜線\n換行",
            oversized,
            "\uD83D\uDE00測試",
            ""
        )
        assertEquals(
            listOf(
                "   ",
                "converted:\"引號\"\\斜線\n換行",
                oversized,
                "converted:\uD83D\uDE00測試",
                ""
            ),
            converter.convertBatch(mixed, ReaderScript.SIMPLIFIED)
        )
        assertEquals(2, transformItems)

        val tooManyItems = List(WebsiteScriptConverter.MAX_BATCH_ITEMS + 1) { "字" }
        assertEquals(tooManyItems, converter.convertBatch(tooManyItems, ReaderScript.SIMPLIFIED))
        val maxPayloadItems = List(
            WebsiteScriptConverter.MAX_BATCH_CHARS / WebsiteScriptConverter.MAX_TEXT_LENGTH + 1
        ) { "字".repeat(WebsiteScriptConverter.MAX_TEXT_LENGTH) }
        assertEquals(maxPayloadItems, converter.convertBatch(maxPayloadItems, ReaderScript.SIMPLIFIED))
        assertEquals(2, transformItems)
    }

    @Test fun releasedWebsiteConverterCannotConvertOrReset() {
        var calls = 0
        val closed = WebsiteScriptConverter { _, _ -> calls++; "unexpected" }
        closed.close()
        assertEquals("字", closed.convert("字", ReaderScript.SIMPLIFIED))
        assertEquals(listOf("字", "詞"), closed.convertBatch(listOf("字", "詞"), ReaderScript.SIMPLIFIED))
        closed.resetPage()
        assertEquals("字", closed.convert("字", ReaderScript.SIMPLIFIED))
        assertEquals(listOf("字", "詞"), closed.convertBatch(listOf("字", "詞"), ReaderScript.SIMPLIFIED))
        assertEquals(0, calls)
    }
}

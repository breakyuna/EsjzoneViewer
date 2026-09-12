package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.readTextBounded
import java.io.IOException
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedResponseBodyTest {
    private fun body(text: String, declaredLength: Long = -1L): ResponseBody = object : ResponseBody() {
        private val buffer = Buffer().writeUtf8(text)
        override fun contentType(): MediaType? = null
        override fun contentLength(): Long = declaredLength
        override fun source(): BufferedSource = buffer
    }

    @Test
    fun acceptsUnknownLengthAtExactByteLimit() {
        assertEquals("你好", body("你好").readTextBounded(maxBytes = 6))
    }

    @Test(expected = IOException::class)
    fun rejectsOversizedChunkedBody() {
        body("1234567").readTextBounded(maxBytes = 6)
    }

    @Test(expected = IOException::class)
    fun rejectsMisleadingContentLength() {
        body("1234567", declaredLength = 1).readTextBounded(maxBytes = 6)
    }

    @Test
    fun retainsOkHttpBomDecoding() {
        assertEquals("正文", body("\uFEFF正文").readTextBounded(maxBytes = 9))
    }
}

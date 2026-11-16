package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.ImageTransferInterceptor
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.blackholeSink
import okio.buffer
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTransferInterceptorTest {
    @Test fun endlessUnknownLengthImageIsStoppedAndClosed() {
        var closed = false
        val body = object : ResponseBody() {
            private val input = object : Source {
                override fun timeout() = Timeout.NONE
                override fun close() { closed = true }
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val count = minOf(8192L, byteCount).toInt()
                    sink.write(ByteArray(count))
                    return count.toLong()
                }
            }.buffer()
            override fun contentType() = null
            override fun contentLength() = -1L
            override fun source() = input
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(ImageTransferInterceptor { Long.MAX_VALUE })
            .addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(body).build()
            }.build()
        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url("https://images.example.test/a.png").build())
                .execute().use { it.body!!.source().readAll(blackholeSink()) }
        }
        assertTrue(closed)
    }
}

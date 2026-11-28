package com.breakyuna.esjzone.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

/** Application interceptor: counts bytes after OkHttp's transparent gzip decoding. */
internal class ImageTransferInterceptor(private val availableBytes: () -> Long) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        try {
            if (body.contentLength() > MAX_IMAGE_BYTES) throw IOException("Image exceeds transfer limit")
            val source = object : ForwardingSource(body.source()) {
                private var received = 0L
                private var nextSpaceCheck = SPACE_CHECK_INTERVAL
                override fun read(sink: Buffer, byteCount: Long): Long {
                    try {
                        if (received >= nextSpaceCheck) {
                            checkSpace()
                            nextSpaceCheck = received + SPACE_CHECK_INTERVAL
                        }
                        val count = super.read(sink, minOf(byteCount, MAX_IMAGE_BYTES - received + 1))
                        if (count > 0) received += count
                        if (received > MAX_IMAGE_BYTES) throw IOException("Image exceeds transfer limit")
                        return count
                    } catch (error: IOException) {
                        close()
                        throw error
                    }
                }
            }.buffer()
            return response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = source
            }).build()
        } catch (error: Exception) {
            response.close()
            throw error
        }
    }

    private fun checkSpace() {
        if (availableBytes() < MIN_FREE_BYTES) throw IOException("Insufficient space for image transfer")
    }

    companion object {
        const val MAX_IMAGE_BYTES = 32L * 1024 * 1024
        const val MIN_FREE_BYTES = 64L * 1024 * 1024
        private const val SPACE_CHECK_INTERVAL = 1024L * 1024L
    }
}

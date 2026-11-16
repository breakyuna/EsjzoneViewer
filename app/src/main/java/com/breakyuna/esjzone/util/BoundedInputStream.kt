package com.breakyuna.esjzone.util

import java.io.InputStream
import java.io.IOException

/** Applied to the decompressed stream, including files with no trustworthy size metadata. */
internal class BoundedInputStream(private val source: InputStream, private val limit: Long) : InputStream() {
    init { require(limit >= 0) }
    private var consumed = 0L
    private fun checkLimit() {
        if (consumed > limit) throw IOException("Stored text exceeds decompression limit")
    }
    override fun read(): Int {
        checkLimit()
        val value = source.read()
        if (value >= 0) record(1)
        return value
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        checkLimit()
        val count = source.read(buffer, offset, minOf(length.toLong(), limit - consumed + 1).toInt())
        if (count > 0) record(count)
        return count
    }
    private fun record(count: Int) {
        consumed += count
        checkLimit()
    }
    override fun close() = source.close()
}

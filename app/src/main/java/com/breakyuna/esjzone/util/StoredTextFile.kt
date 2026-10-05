package com.breakyuna.esjzone.util

import java.io.BufferedReader
import java.io.File
import java.io.Writer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** New text snapshots are gzip; existing UTF-8 files remain readable at the same paths. */
internal fun File.storedTextReader(): BufferedReader {
    val input = inputStream().buffered()
    return try {
        input.mark(2)
        val first = input.read()
        val second = input.read()
        input.reset()
        val decoded = if (first == 0x1f && second == 0x8b) GZIPInputStream(input) else input
        decoded.bufferedReader(Charsets.UTF_8)
    } catch (error: Exception) {
        input.close()
        throw error
    }
}

internal fun File.writeCompressedText(write: (Writer) -> Unit) {
    GZIPOutputStream(outputStream().buffered()).bufferedWriter(Charsets.UTF_8).use(write)
}

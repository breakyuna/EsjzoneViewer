package com.breakyuna.esjzone.util

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.IOException
import java.io.Reader

/** Validate the same bounded text that Gson will deserialize, before creating model collections. */
internal object BoundedJson {
    const val MAX_BYTES = 32L * 1024 * 1024
    const val MAX_CHARS = 16 * 1024 * 1024
    private const val MAX_TOKENS = 500_000
    const val MAX_ITEMS = 50_000
    private const val MAX_STRING = 4 * 1024 * 1024

    fun readText(reader: Reader): String {
        val text = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer, 0, minOf(buffer.size, MAX_CHARS - text.length + 1))
            if (count < 0) break
            if (text.length + count > MAX_CHARS) throw IOException("JSON text exceeds size limit")
            text.append(buffer, 0, count)
        }
        return text.toString().also(::validate)
    }

    private fun validate(text: String) {
        JsonReader(text.reader()).use { json ->
            val counts = ArrayList<Int>()
            var tokens = 0
            var roots = 0
            fun item() {
                if (counts.isEmpty()) roots++ else {
                    val last = counts.lastIndex
                    counts[last]++
                    if (counts[last] > MAX_ITEMS) throw IOException("JSON collection exceeds item limit")
                }
            }
            while (true) {
                val token = json.peek()
                if (token == JsonToken.END_DOCUMENT) break
                if (++tokens > MAX_TOKENS) throw IOException("JSON exceeds token limit")
                when (token) {
                    JsonToken.BEGIN_ARRAY, JsonToken.BEGIN_OBJECT -> {
                        item()
                        if (counts.size >= 64) throw IOException("JSON nesting exceeds limit")
                        counts.add(0)
                        if (token == JsonToken.BEGIN_ARRAY) json.beginArray() else json.beginObject()
                    }
                    JsonToken.END_ARRAY, JsonToken.END_OBJECT -> {
                        if (token == JsonToken.END_ARRAY) json.endArray() else json.endObject()
                        counts.removeAt(counts.lastIndex)
                    }
                    JsonToken.NAME -> if (json.nextName().length > 512) throw IOException("JSON key exceeds limit")
                    JsonToken.STRING, JsonToken.NUMBER -> {
                        item()
                        val length = json.nextString().length
                        val limit = if (token == JsonToken.NUMBER) 128 else MAX_STRING
                        if (length > limit) throw IOException("JSON value exceeds limit")
                    }
                    JsonToken.BOOLEAN -> { item(); json.nextBoolean() }
                    JsonToken.NULL -> { item(); json.nextNull() }
                    else -> throw IOException("Invalid JSON token")
                }
            }
            if (roots != 1 || counts.isNotEmpty()) throw IOException("Invalid JSON document")
        }
    }
}

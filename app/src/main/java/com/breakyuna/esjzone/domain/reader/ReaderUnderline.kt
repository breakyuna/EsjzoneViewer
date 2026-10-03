package com.breakyuna.esjzone.domain.reader

import com.google.gson.Gson
import java.security.MessageDigest

/** Offsets refer to one rendered AST block, before page splitting. */
data class ReaderUnderline(val blockIndex: Int, val signature: String, val start: Int, val end: Int)

object ReaderUnderlines {
    const val KEY_PREFIX = "reader_underlines:"
    private val gson = Gson()
    fun signature(block: ReaderBlock, renderedText: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$block\u0000$renderedText".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun decode(value: String?): List<ReaderUnderline> {
        if (value == null) return emptyList()
        require(value.isNotBlank()) { "Invalid underline data" }
        return requireNotNull(gson.fromJson(value, Array<ReaderUnderline>::class.java)) {
            "Invalid underline data"
        }.toList().also { rows ->
            require(rows.all { it.blockIndex >= 0 && it.start >= 0 && it.end > it.start &&
                it.signature.matches(Regex("[0-9a-f]{64}")) })
        }
    }
    fun encode(rows: List<ReaderUnderline>): String = gson.toJson(rows)
    fun overlaps(a: ReaderUnderline, b: ReaderUnderline) = a.blockIndex == b.blockIndex &&
        a.signature == b.signature && a.start < b.end && b.start < a.end
    fun update(rows: List<ReaderUnderline>, selection: ReaderUnderline, remove: Boolean): List<ReaderUnderline> {
        val overlapping = rows.filter { overlaps(it, selection) }
        val remaining = rows - overlapping.toSet()
        return if (remove) remaining else remaining + selection.copy(
            start = minOf(selection.start, overlapping.minOfOrNull { it.start } ?: selection.start),
            end = maxOf(selection.end, overlapping.maxOfOrNull { it.end } ?: selection.end)
        )
    }
}

package com.breakyuna.esjzone.ui.reader

import android.icu.text.Replaceable
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets

/** ICU edits the string through this interface, preserving source ranges even when lengths change. */
internal class ReaderMappedText(private val source: String) : Replaceable {
    private val value = StringBuilder(source)
    private val starts = source.indices.toMutableList()
    private val ends = source.indices.map { it + 1 }.toMutableList()
    override fun length() = value.length
    override fun charAt(offset: Int) = value[offset]
    override fun char32At(offset: Int): Int = if (value[offset].isLowSurrogate() && offset > 0 && value[offset - 1].isHighSurrogate())
        Character.toCodePoint(value[offset - 1], value[offset]) else Character.codePointAt(value, offset)
    override fun getChars(srcStart: Int, srcLimit: Int, dst: CharArray, dstStart: Int) {
        for (index in srcStart until srcLimit) dst[dstStart + index - srcStart] = value[index]
    }
    override fun hasMetaData() = true
    override fun replace(start: Int, limit: Int, chars: CharArray, charsStart: Int, charsLen: Int) =
        replace(start, limit, String(chars, charsStart, charsLen))
    override fun replace(start: Int, limit: Int, text: String) {
        if (value.substring(start, limit) == text) return
        val first = starts.getOrNull(start) ?: source.length
        val last = if (limit > start) ends[limit - 1] else first
        val newStarts = if (limit - start == text.length) starts.subList(start, limit).toList() else List(text.length) { first }
        val newEnds = if (limit - start == text.length) ends.subList(start, limit).toList() else List(text.length) { last }
        value.replace(start, limit, text)
        starts.subList(start, limit).clear()
        ends.subList(start, limit).clear()
        starts.addAll(start, newStarts)
        ends.addAll(start, newEnds)
    }
    override fun copy(start: Int, limit: Int, dest: Int) {
        val text = value.substring(start, limit)
        val copiedStarts = starts.subList(start, limit).toList()
        val copiedEnds = ends.subList(start, limit).toList()
        value.insert(dest, text)
        starts.addAll(dest, copiedStarts)
        ends.addAll(dest, copiedEnds)
    }
    fun snapshot() = ReaderTextOffsets(value.toString(), starts.toIntArray(), ends.toIntArray(), source.length)
}

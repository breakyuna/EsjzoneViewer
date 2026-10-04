package com.breakyuna.esjzone.ui.reader

import android.icu.text.Transliterator
import com.breakyuna.esjzone.domain.reader.ReaderBlock
import com.breakyuna.esjzone.domain.reader.ReaderTextOffsets

internal data class ReaderScriptSnapshot(
    val script: ReaderScript,
    val values: Map<String, String>,
    val blockMappings: Map<ReaderBlock, ReaderTextOffsets>
)

/**
 * Converts the reader's source text on demand so the original parsed chapter model
 * remains untouched. Android's ICU implementation handles phrase-independent
 * Traditional/Simplified character mappings and is available on the app's minSdk.
 */
object ReaderScriptConverter {

    private val traditionalToSimplified by lazy {
        Transliterator.getInstance("Traditional-Simplified")
    }

    private val simplifiedToTraditional by lazy {
        Transliterator.getInstance("Simplified-Traditional")
    }

    private val convertCache = object : LinkedHashMap<Pair<String, ReaderScript>, com.breakyuna.esjzone.domain.reader.ReaderTextOffsets>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, ReaderScript>, com.breakyuna.esjzone.domain.reader.ReaderTextOffsets>?): Boolean = size > 4096
    }

    private val transliteratorLock = Any()

    /** Converts on the caller's thread; callers should use a background dispatcher. */
    fun preload(texts: Iterable<String>, script: ReaderScript) {
        if (script == ReaderScript.ORIGINAL) return
        for (text in texts) {
            if (text.isNotEmpty()) {
                convert(text, script)
            }
        }
    }

    /** Pre-warms all text and ruby readings from a reader document AST in the background. */
    fun preload(document: com.breakyuna.esjzone.domain.reader.ReaderChapterDocument, script: ReaderScript) {
        if (script == ReaderScript.ORIGINAL) return
        if (document.chapter.name.isNotEmpty()) {
            convert(document.chapter.name, script)
        }
        for (block in document.blocks) {
            when (block) {
                is com.breakyuna.esjzone.domain.reader.ReaderBlock.Paragraph -> {
                    for (part in block.parts) {
                        if (part.value.isNotEmpty()) convert(part.value, script)
                        part.ruby?.reading?.let { if (it.isNotEmpty()) convert(it, script) }
                    }
                }
                is com.breakyuna.esjzone.domain.reader.ReaderBlock.Text -> {
                    if (block.value.isNotEmpty()) convert(block.value, script)
                    block.ruby?.reading?.let { if (it.isNotEmpty()) convert(it, script) }
                }
                else -> Unit
            }
        }
    }

    /** Builds a complete immutable transform table before a reader window is rendered. */
    internal fun snapshot(
        documents: Iterable<com.breakyuna.esjzone.domain.reader.ReaderChapterDocument>,
        script: ReaderScript,
        previous: ReaderScriptSnapshot? = null
    ): ReaderScriptSnapshot {
        if (script == ReaderScript.ORIGINAL) return ReaderScriptSnapshot(script, emptyMap(), emptyMap())
        val reusable = previous?.takeIf { it.script == script }
        val result = HashMap<String, String>()
        val blocks = HashMap<ReaderBlock, ReaderTextOffsets>()
        fun add(value: String?) {
            if (!value.isNullOrEmpty() && value !in result) {
                result[value] = reusable?.values?.get(value) ?: convert(value, script)
            }
        }
        documents.forEach { document ->
            add(document.chapter.name)
            document.blocks.forEach { block ->
                when (block) {
                    is com.breakyuna.esjzone.domain.reader.ReaderBlock.Paragraph -> block.parts.forEach { part ->
                        add(part.value)
                        add(part.ruby?.reading)
                    }
                    is com.breakyuna.esjzone.domain.reader.ReaderBlock.Text -> {
                        add(block.value)
                        add(block.ruby?.reading)
                    }
                    else -> Unit
                }
                blocks[block] = reusable?.blockMappings?.get(block) ?: blockMapping(block, script)
            }
        }
        return ReaderScriptSnapshot(script, result, blocks)
    }

    fun convert(text: String, script: ReaderScript): String = mapping(text, script).text

    fun mapping(text: String, script: ReaderScript): com.breakyuna.esjzone.domain.reader.ReaderTextOffsets {
        if (script == ReaderScript.ORIGINAL || text.isEmpty()) return com.breakyuna.esjzone.domain.reader.ReaderTextOffsets.identity(text)
        synchronized(convertCache) { convertCache[text to script] }?.let { return it }
        val mapped = ReaderMappedText(text)
        synchronized(transliteratorLock) {
            (if (script == ReaderScript.SIMPLIFIED) traditionalToSimplified else simplifiedToTraditional).transliterate(mapped)
        }
        return mapped.snapshot().also { synchronized(convertCache) { convertCache[text to script] = it } }
    }

    fun blockMapping(block: com.breakyuna.esjzone.domain.reader.ReaderBlock, script: ReaderScript): com.breakyuna.esjzone.domain.reader.ReaderTextOffsets = when (block) {
        is com.breakyuna.esjzone.domain.reader.ReaderBlock.Paragraph -> com.breakyuna.esjzone.domain.reader.ReaderTextOffsets.join(block.parts.map { mapping(it.value, script) })
        is com.breakyuna.esjzone.domain.reader.ReaderBlock.Text -> mapping(block.value, script)
        com.breakyuna.esjzone.domain.reader.ReaderBlock.LineBreak -> com.breakyuna.esjzone.domain.reader.ReaderTextOffsets.identity("\n")
        is com.breakyuna.esjzone.domain.reader.ReaderBlock.Image -> com.breakyuna.esjzone.domain.reader.ReaderTextOffsets.identity("")
    }
}

package com.breakyuna.esjzone.ui.reader

import android.icu.text.Transliterator

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

    fun convert(text: String, script: ReaderScript): String = when (script) {
        ReaderScript.ORIGINAL -> text
        ReaderScript.SIMPLIFIED -> traditionalToSimplified.transliterate(text)
        ReaderScript.TRADITIONAL -> simplifiedToTraditional.transliterate(text)
    }
}

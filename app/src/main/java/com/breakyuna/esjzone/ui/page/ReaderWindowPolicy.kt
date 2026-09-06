package com.breakyuna.esjzone.ui.page

import com.breakyuna.esjzone.domain.reader.ReaderScrollSnapshot as DomainReaderScrollSnapshot
import com.breakyuna.esjzone.domain.reader.ReaderWindowAnchor as DomainReaderWindowAnchor
import com.breakyuna.esjzone.domain.reader.shouldLoadNextChapter as domainShouldLoadNextChapter
import com.breakyuna.esjzone.domain.reader.shouldLoadPreviousChapter as domainShouldLoadPreviousChapter
import com.breakyuna.esjzone.domain.reader.trimReaderWindowKeys as domainTrimReaderWindowKeys

/** Transitional aliases keep the existing presentation call sites source-compatible. */
internal typealias ReaderScrollSnapshot = DomainReaderScrollSnapshot
internal typealias ReaderWindowAnchor = DomainReaderWindowAnchor

internal fun shouldLoadNextChapter(
    previous: ReaderScrollSnapshot?,
    current: ReaderScrollSnapshot,
    threshold: Int
): Boolean = domainShouldLoadNextChapter(previous, current, threshold)

internal fun shouldLoadPreviousChapter(
    previous: ReaderScrollSnapshot?,
    current: ReaderScrollSnapshot,
    threshold: Int
): Boolean = domainShouldLoadPreviousChapter(previous, current, threshold)

internal fun trimReaderWindowKeys(
    keys: List<String>,
    trimFromStart: Boolean,
    maxSize: Int,
    protectedKeys: Set<String>
): List<String> = domainTrimReaderWindowKeys(keys, trimFromStart, maxSize, protectedKeys)

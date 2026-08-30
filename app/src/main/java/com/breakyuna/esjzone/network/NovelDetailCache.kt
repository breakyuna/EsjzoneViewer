package com.breakyuna.esjzone.network

import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel

/**
 * Small process-local cache for parsed novel details.
 *
 * The disk cache stores raw HTML so parser changes remain migration-free. This layer only
 * avoids repeatedly parsing the same document while navigating between lists, history,
 * favorites, and the detail page during one process lifetime.
 */
internal object NovelDetailCache {

    private const val MAX_ENTRIES = 128
    private const val MAX_AGE_MILLIS = 30L * 60L * 1000L
    private val lock = Any()
    private val entries = object : LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    fun read(key: String, nowMillis: Long = System.currentTimeMillis()): DetailedNovel? =
        synchronized(lock) {
            val entry = entries[key] ?: return@synchronized null
            if (nowMillis - entry.storedAtMillis > MAX_AGE_MILLIS) {
                entries.remove(key)
                null
            } else {
                entry.novel
            }
        }

    fun write(key: String, novel: DetailedNovel) {
        synchronized(lock) {
            entries[key] = Entry(novel, System.currentTimeMillis())
        }
    }

    fun remove(key: String) {
        synchronized(lock) { entries.remove(key) }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
    }

    private data class Entry(
        val novel: DetailedNovel,
        val storedAtMillis: Long
    )
}

package com.breakyuna.esjzone.network

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class CacheEventType { HIT, UPDATE, EVICT, FAILURE }
internal enum class CacheLayer { PAGE, CHAPTER, LIST, DETAIL, HOME, HISTORY }
internal enum class CacheEventReason {
    FRESH, STALE, MEMORY, DISK, STORED, REPLACED, CAPACITY, EXPIRED, INVALIDATED,
    SOURCE_UPDATED, DOWNLOADED, CLEARED, INVALID_DATA, READ_FAILED, WRITE_FAILED,
    DELETE_FAILED, SUPERSEDED
}

internal data class CacheEvent(
    val id: Long,
    val timestamp: Long,
    val type: CacheEventType,
    val layer: CacheLayer,
    val reason: CacheEventReason,
    val entryId: String?,
    val bytes: Long?
)

internal data class CacheTraceState(
    val recording: Boolean = false,
    val events: List<CacheEvent> = emptyList()
)

/** Opt-in process-local diagnostics. Never retains keys, URLs, bodies or exception messages. */
internal object CacheDiagnostics {
    private const val MAX_EVENTS = 500
    private val lock = Any()
    private var nextId = 0L
    private val digestPattern = Regex("[a-f0-9]{64}")
    private val mutableState = MutableStateFlow(CacheTraceState())
    val state = mutableState.asStateFlow()

    fun setRecording(recording: Boolean) = synchronized(lock) {
        mutableState.value = mutableState.value.copy(recording = recording)
    }

    fun clear() = synchronized(lock) {
        mutableState.value = mutableState.value.copy(events = emptyList())
    }

    fun record(type: CacheEventType, layer: CacheLayer, reason: CacheEventReason,
               key: String? = null, fileId: String? = null, bytes: Long? = null) {
        CacheAnalysis.record(type, layer, reason, bytes)
        if (!mutableState.value.recording) return
        synchronized(lock) {
            val current = mutableState.value
            if (!current.recording) return
            val entryId = fileId?.takeIf { it.matches(digestPattern) }?.take(12)
                ?: key?.let {
                    MessageDigest.getInstance("SHA-256").digest(it.toByteArray(StandardCharsets.UTF_8))
                        .take(6).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
                }
            val event = CacheEvent(++nextId, System.currentTimeMillis(), type, layer, reason,
                entryId, bytes?.coerceAtLeast(0L))
            mutableState.value = current.copy(events = current.events.takeLast(MAX_EVENTS - 1) + event)
        }
    }
}

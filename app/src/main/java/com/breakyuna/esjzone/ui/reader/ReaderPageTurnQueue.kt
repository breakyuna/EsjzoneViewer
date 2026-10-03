package com.breakyuna.esjzone.ui.reader

import androidx.compose.runtime.mutableStateListOf

/** UI-thread FIFO: opposite directions remain separate turns, including the active turn. */
internal class ReaderPageTurnQueue {
    private val turns = mutableStateListOf<Boolean>()
    private var lastInputMillis = 0L
    private var burstDurationMillis = NORMAL_DURATION_MILLIS

    val hasTurns: Boolean get() = turns.isNotEmpty()
    val next: Boolean? get() = turns.firstOrNull()
    val durationMillis: Float
        get() = minOf(burstDurationMillis, NORMAL_DURATION_MILLIS / turns.size.coerceAtLeast(1))
            .coerceAtLeast(MIN_DURATION_MILLIS)

    fun enqueue(forward: Boolean, inputMillis: Long) {
        if (hasTurns) {
            val interval = (inputMillis - lastInputMillis).coerceAtLeast(1L)
            burstDurationMillis = minOf(burstDurationMillis, interval * 0.8f)
                .coerceAtLeast(MIN_DURATION_MILLIS)
        }
        lastInputMillis = inputMillis
        turns.add(forward)
    }

    fun complete() {
        if (hasTurns) turns.removeAt(0)
        if (!hasTurns) clear()
    }

    fun clear() {
        turns.clear()
        lastInputMillis = 0L
        burstDurationMillis = NORMAL_DURATION_MILLIS
    }

    private companion object {
        const val NORMAL_DURATION_MILLIS = 180f
        const val MIN_DURATION_MILLIS = 48f
    }
}

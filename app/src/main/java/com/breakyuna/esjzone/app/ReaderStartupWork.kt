package com.breakyuna.esjzone.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/** Nonessential storage work yields while startup is choosing or positioning the reader. */
internal object ReaderStartupWork {
    private val mutableAllowed = MutableStateFlow(false)
    val allowed: StateFlow<Boolean> = mutableAllowed.asStateFlow()

    fun setAllowed(allowed: Boolean) { mutableAllowed.value = allowed }

    suspend fun <T> runWhenAllowed(block: suspend () -> T): T = channelFlow<T> {
        allowed.collectLatest { ready -> if (ready) send(block()) }
    }.first()
}

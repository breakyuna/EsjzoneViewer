package com.breakyuna.esjzone.ui.reader

import android.view.KeyEvent

/** Activity-level bridge for hardware volume keys while a reader is visible. */
object ReaderVolumeKeyDispatcher {
    private var token: Any? = null
    private var handler: ((Int) -> Boolean)? = null

    @Synchronized
    fun register(onKey: (Int) -> Boolean): Any {
        val newToken = Any()
        token = newToken
        handler = onKey
        return newToken
    }

    @Synchronized
    fun unregister(registration: Any) {
        if (token === registration) {
            token = null
            handler = null
        }
    }

    @Synchronized
    fun dispatch(keyCode: Int): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        return handler?.invoke(keyCode) == true
    }
}

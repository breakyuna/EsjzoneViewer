package com.breakyuna.esjzone.util

import android.content.Context

class CrashHandler private constructor(
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private var isInstalled = false

        fun init(context: Context) {
            if (isInstalled) return
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            val crashHandler = CrashHandler(defaultHandler)
            Thread.setDefaultUncaughtExceptionHandler(crashHandler)
            isInstalled = true
            AppLogger.i("CrashHandler", "Global uncaught exception handler registered successfully")
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // Record full crash details to AppLogger (sync disk flush)
            AppLogger.crash(thread, throwable)
        } catch (e: Exception) {
            android.util.Log.e("CrashHandler", "Error while logging crash: ${AppLogger.sanitizeForDisplay(e.message.orEmpty())}")
        } finally {
            // Hand over to system default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}

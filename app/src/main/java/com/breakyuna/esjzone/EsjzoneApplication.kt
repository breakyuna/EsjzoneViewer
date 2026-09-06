package com.breakyuna.esjzone

import android.app.Application
import com.breakyuna.esjzone.app.AppContainer

/** Process owner for application infrastructure; Activities only consume this container. */
class EsjzoneApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

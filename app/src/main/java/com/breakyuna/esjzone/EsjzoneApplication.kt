package com.breakyuna.esjzone

import android.app.Application
import com.breakyuna.esjzone.app.AppContainer

/** Process owner for application infrastructure; Activities only consume this container. */
class EsjzoneApplication : Application() {
    companion object {
        lateinit var instance: EsjzoneApplication
            private set
    }

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = AppContainer(this)
    }
}

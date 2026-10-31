package com.breakyuna.esjzone

import android.app.Application
import com.breakyuna.esjzone.app.AppContainer
import com.breakyuna.esjzone.update.ReleaseUpdateChecker
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.CrashHandler
import com.breakyuna.esjzone.storage.UserStorage
import com.breakyuna.esjzone.util.StartupWatchdog
import android.os.Handler
import android.os.Looper

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
        AppLogger.init(this)
        CrashHandler.init(this)
        val storage = UserStorage(this)
        AppLogger.startPublicLogging(storage)
        val mainLooper = Looper.getMainLooper()
        val mainHandler = Handler(mainLooper)
        StartupWatchdog(
            mainThread = mainLooper.thread,
            postHeartbeat = { mainHandler.post(it) },
            onStall = AppLogger::startupStall
        ).start()
        AppLogger.i("Startup", "Checking installed update APK")
        ReleaseUpdateChecker.cleanupInstalledApk(this)
        AppLogger.i("Startup", "Creating application container")
        container = AppContainer(this, storage)
        AppLogger.i("Startup", "Application container ready")
        container.autoBackup.startScheduling()
    }
}

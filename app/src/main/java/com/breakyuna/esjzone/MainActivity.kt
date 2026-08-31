package com.breakyuna.esjzone

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.offline.NovelDownloadStore
import com.breakyuna.esjzone.ui.app.App
import com.breakyuna.esjzone.ui.theme.catppuccin.CatppuccinDynamicTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.CatppuccinThemeType
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.CrashHandler

class MainActivity : ComponentActivity() {

    companion object {

        lateinit var database: GeneralDatabase
        lateinit var imageLoader: ImageLoader

    }

    @SuppressLint("CoroutineCreationDuringComposition")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Logging and Crash Monitoring
        AppLogger.init(applicationContext)
        CrashHandler.init(applicationContext)
        AppLogger.i("MainActivity", "Activity onCreate started")

        EsjzoneClient.initialize(applicationContext)
        NovelDownloadStore.initialize(applicationContext)

        imageLoader = ImageLoader.Builder(this)
            .components {
                add(ImageDecoderDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    // Keep covers available after the process is recreated in
                    // the background; cacheDir can be purged at any time.
                    .directory(this.filesDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .respectCacheHeaders(false)
            .build()

        enableEdgeToEdge()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                AppLogger.i("MainActivity", "Initializing Room database...")
                database = Room.databaseBuilder(
                    this@MainActivity,
                    GeneralDatabase::class.java, "general"
                ).addMigrations(
                    GeneralDatabase.MIGRATION_1_2,
                    GeneralDatabase.MIGRATION_2_3,
                    GeneralDatabase.MIGRATION_3_4,
                    GeneralDatabase.MIGRATION_4_5
                ).build()

                val dao = database.cacheDao()

                if (dao.findByKey("theme") == null) {
                    dao.put("theme", GlobalSettings.theme.value.name)
                }
                if (dao.findByKey("domain") == null) {
                    dao.put("domain", GlobalSettings.domain.value)
                }

                val savedTheme = dao.findByKey("theme")?.value
                    ?: GlobalSettings.theme.value.name
                val savedDomain = dao.findByKey("domain")?.value
                    ?.takeIf { it in GlobalSettings.DOMAINS }
                    ?: GlobalSettings.domain.value
                GlobalSettings.domain.value = savedDomain
                GlobalSettings.theme.value = runCatching {
                    CatppuccinThemeType.valueOf(savedTheme)
                }.getOrElse {
                    AppLogger.w("MainActivity", "Invalid cached theme, using default", it)
                    CatppuccinThemeType.LATTE_YELLOW
                }
                AppLogger.i("MainActivity", "Settings restored: Domain=$savedDomain, Theme=$savedTheme")

                this.launch(Dispatchers.Main) {
                    setContent {
                        CatppuccinDynamicTheme {
                            App()
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("MainActivity", "Failed to initialize database or settings", e)
            }
        }
    }

}

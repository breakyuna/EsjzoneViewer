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
import com.breakyuna.esjzone.database.entity.Cache
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
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
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
                ).build()

                val dao = database.cacheDao()

                if (!dao.exists("theme")) {
                    dao.insertNotExists(
                        Cache(
                            key = "theme",
                            value = GlobalSettings.theme.value.name
                        )
                    )
                }

                if (!dao.exists("domain")) {
                    dao.insertNotExists(
                        Cache(
                            key = "domain",
                            value = GlobalSettings.domain.value
                        )
                    )
                }

                val savedTheme = dao.findByKey("theme").value
                val savedDomain = dao.findByKey("domain").value
                GlobalSettings.theme.value = CatppuccinThemeType.valueOf(savedTheme)
                GlobalSettings.domain.value = savedDomain
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
package com.breakyuna.esjzone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.room.Room
import coil.ImageLoader
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.database.BookshelfRepository
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

        private val startupState = MutableStateFlow<StartupState>(StartupState.Starting)
        val startup = startupState.asStateFlow()
        private val initMutex = Mutex()
        private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private suspend fun initializeOnce(context: android.content.Context) = initMutex.withLock {
            if (startupState.value is StartupState.Ready) return@withLock
            val appContext = context.applicationContext
            try {
                AppLogger.init(appContext)
                CrashHandler.init(appContext)
                EsjzoneClient.initialize(appContext)
                NovelDownloadStore.initialize(appContext)
                if (!::imageLoader.isInitialized) imageLoader = ImageLoader.Builder(appContext)
                    .components { add(ImageDecoderDecoder.Factory()) }
                    .memoryCache { MemoryCache.Builder(appContext).maxSizePercent(0.15).build() }
                    .diskCache {
                    DiskCache.Builder().directory(appContext.filesDir.resolve("image_cache"))
                            .maxSizePercent(0.05).build()
                    }.respectCacheHeaders(false).build()
                AppLogger.i("MainActivity", "Initializing Room database...")
                if (!::database.isInitialized) {
                    database = Room.databaseBuilder(appContext, GeneralDatabase::class.java, "general")
                        .addMigrations(GeneralDatabase.MIGRATION_1_2, GeneralDatabase.MIGRATION_2_3,
                            GeneralDatabase.MIGRATION_3_4, GeneralDatabase.MIGRATION_4_5,
                            GeneralDatabase.MIGRATION_5_6).build()
                    BookshelfRepository.initialize(database)
                }
                val dao = database.cacheDao()
                if (dao.findByKey("theme") == null) dao.put("theme", GlobalSettings.theme.value.name)
                if (dao.findByKey("domain") == null) dao.put("domain", GlobalSettings.domain.value)
                val savedTheme = dao.findByKey("theme")?.value ?: GlobalSettings.theme.value.name
                val savedDomain = dao.findByKey("domain")?.value?.takeIf { it in GlobalSettings.DOMAINS }
                    ?: GlobalSettings.domain.value
                GlobalSettings.domain.value = savedDomain
                GlobalSettings.theme.value = runCatching { CatppuccinThemeType.valueOf(savedTheme) }
                    .getOrElse { CatppuccinThemeType.LATTE_YELLOW }
                startupState.value = StartupState.Ready
            } catch (e: Exception) {
                AppLogger.e("MainActivity", "Failed to initialize database or settings", e)
                startupState.value = StartupState.Failed(AppLogger.sanitizeForDisplay(e.message ?: e.javaClass.simpleName))
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        val appContext = applicationContext
        setContent {
            val state by startup.collectAsState()
            if (state is StartupState.Ready) {
                CatppuccinDynamicTheme { App() }
            } else {
                StartupContent(state) { startupState.value = StartupState.Starting; initScope.launch { initializeOnce(appContext) } }
            }
        }
        initScope.launch { initializeOnce(appContext) }
    }

}

sealed interface StartupState {
    data object Starting : StartupState
    data object Ready : StartupState
    data class Failed(val reason: String) : StartupState
}

@Composable
private fun StartupContent(state: StartupState, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        if (state is StartupState.Starting) CircularProgressIndicator()
        Text(stringResource(if (state is StartupState.Failed) R.string.load_client_error else R.string.startup_loading),
            style = MaterialTheme.typography.titleMedium)
        if (state is StartupState.Failed) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = retry) { Text(stringResource(R.string.retry)) }
        }
    }
}

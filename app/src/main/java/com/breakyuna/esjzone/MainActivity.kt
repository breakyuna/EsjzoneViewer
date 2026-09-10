package com.breakyuna.esjzone

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import com.breakyuna.esjzone.ui.app.App
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.CrashHandler
import com.breakyuna.esjzone.update.ReleaseUpdateChecker
import com.breakyuna.esjzone.update.ReleaseUpdateDialog
import com.breakyuna.esjzone.util.LocaleHelper
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {

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
                val container = (appContext as EsjzoneApplication).container
                AppLogger.i("MainActivity", "Initializing settings and Room database...")
                val settings = container.settingsDataStore
                settings.migrateFromLegacy(container.database)
                container.readerSettingsDataStore.migrateFromLegacy(appContext)
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
            val appLanguage by PresentationAccess.settings.language
            val appTheme by PresentationAccess.settings.theme
            val baseContext = LocalContext.current
            val currentConfiguration = LocalConfiguration.current

            val localizedContext = remember(appLanguage, baseContext) {
                LocaleHelper.createLocalizedContext(baseContext, appLanguage)
            }
            val localizedConfiguration = remember(appLanguage, currentConfiguration, localizedContext) {
                Configuration(localizedContext.resources.configuration)
            }

            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration
            ) {
                AppTheme(variant = appTheme) {
                    if (state is StartupState.Ready) {
                        App()
                        ReleaseUpdateDialog()
                    } else {
                        StartupContent(state) { startupState.value = StartupState.Starting; initScope.launch { initializeOnce(appContext) } }
                    }
                }
            }
        }
        initScope.launch { initializeOnce(appContext) }
        ReleaseUpdateChecker.checkOnce(appContext)
    }

}

sealed interface StartupState {
    data object Starting : StartupState
    data object Ready : StartupState
    data class Failed(val reason: String) : StartupState
}

@Composable
private fun StartupContent(state: StartupState, retry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.esjzone_icon_round),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
                .padding(bottom = 18.dp)
                .size(72.dp)
                .clip(CircleShape)
        )
        Text(stringResource(R.string.app_name), style = AppTypography.displayMedium)
        if (state is StartupState.Starting) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(strokeWidth = 2.5.dp)
        }
        if (state is StartupState.Failed) {
            Text(
                stringResource(R.string.startup_failed),
                style = AppTypography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = retry) { Text(stringResource(R.string.retry)) }
        }
    }
}

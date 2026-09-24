package com.breakyuna.esjzone

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.breakyuna.esjzone.ui.reader.ReaderVolumeKeyDispatcher
import com.breakyuna.esjzone.util.LocaleHelper
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.repeatCount == 0 && ReaderVolumeKeyDispatcher.dispatch(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (ReaderVolumeKeyDispatcher.isIntercepting(keyCode)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    companion object {

        const val EXTRA_NOVEL_URL = "com.breakyuna.esjzone.extra.NOVEL_URL"
        private val pendingNovelUrlState = MutableStateFlow<String?>(null)
        val pendingTargetNovel = pendingNovelUrlState.asStateFlow()

        fun setPendingNovelUrl(url: String?) {
            pendingNovelUrlState.value = url
        }

        private val startupCoordinator = StartupCoordinator(
            mapFailure = { error ->
                AppLogger.sanitizeForDisplay(error.message ?: error.javaClass.simpleName)
            },
            onFailure = { error ->
                AppLogger.e("MainActivity", "Failed to initialize database or settings", error)
            }
        )
        val startup = startupCoordinator.state
        private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private suspend fun initializeOnce(context: android.content.Context) {
            val appContext = context.applicationContext
            startupCoordinator.initializeOnce {
                AppLogger.init(appContext)
                CrashHandler.init(appContext)
                val container = (appContext as EsjzoneApplication).container
                AppLogger.i("MainActivity", "Initializing network, download store, settings and Room database...")
                container.initializeAsync()
                val settings = container.settingsDataStore
                settings.migrateFromLegacy(container.database)
                container.readerSettingsDataStore.migrateFromLegacy(appContext)
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNovelIntent(intent)

        enableEdgeToEdge()
        val appContext = applicationContext
        setContent {
            val state by startup.collectAsStateWithLifecycle()
            val appLanguage by PresentationAccess.settings.language
            val baseContext = LocalContext.current
            val currentConfiguration = LocalConfiguration.current

            val localizedContext = remember(appLanguage, baseContext) {
                LocaleHelper.createLocalizedContext(baseContext, appLanguage)
            }
            val localizedConfiguration = remember(appLanguage, currentConfiguration, localizedContext) {
                Configuration(localizedContext.resources.configuration)
            }

            CompositionLocalProvider(
                LocalActivity provides this@MainActivity,
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedConfiguration
            ) {
                AppTheme {
                    if (state is StartupState.Ready) {
                        App()
                        ReleaseUpdateDialog()
                    } else {
                        StartupContent(state) {
                            startupCoordinator.retry()
                            initScope.launch { initializeOnce(appContext) }
                        }
                    }
                }
            }
        }
        initScope.launch { initializeOnce(appContext) }
        ReleaseUpdateChecker.checkOnce(appContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNovelIntent(intent)
    }

    private fun handleNovelIntent(intent: Intent?) {
        val novelUrl = intent?.getStringExtra(EXTRA_NOVEL_URL)
        if (!novelUrl.isNullOrBlank()) {
            setPendingNovelUrl(novelUrl)
        }
    }

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

package com.breakyuna.esjzone.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.hasCredentials
import com.breakyuna.esjzone.util.AppLogger

class LoadingScreen : Screen {

    override val key: ScreenKey = "LoadingScreen"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        LaunchedEffect(Unit) {
            val authorization = withContext(Dispatchers.IO) {
                try {
                    val dao = MainActivity.database.cacheDao()
                    if (dao.findByKey("ews_key") == null) {
                        dao.put("ews_key", "null")
                    }
                    if (dao.findByKey("ews_token") == null) {
                        dao.put("ews_token", "null")
                    }
                    if (dao.findByKey("show_adult") == null) {
                        dao.put("show_adult", "false")
                    }

                    val ewsKey = dao.findByKey("ews_key")?.value ?: "null"
                    val ewsToken = dao.findByKey("ews_token")?.value ?: "null"
                    val sessionDomain = dao.findByKey("session_domain")?.value
                    GlobalSettings.adult.value =
                        dao.findByKey("show_adult")?.value?.toBooleanStrictOrNull() ?: false

                    val selectedDomain = GlobalSettings.domain.value
                    val legacyAuthorization = Authorization(ewsKey, ewsToken, selectedDomain)
                    val legacySession = legacyAuthorization.takeIf {
                        it.hasCredentials() &&
                            (sessionDomain.isNullOrBlank() || sessionDomain == selectedDomain)
                    }
                    val storedAuthorization = EsjzoneClient.restoreAuthorization(
                        selectedDomain,
                        legacySession
                    )
                    storedAuthorization?.takeIf { it.hasCredentials() }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("LoadingScreen", "Failed to restore local state", e)
                    null
                }
            }

            if (authorization != null) {
                // Start the shelf retry/import in the process-scoped worker.
                // MainScreen and the shelf itself remain local-first and do
                // not wait for this network operation.
                BookshelfRepository.scheduleSync(authorization)
                navigator.replace(MainScreen(authorization = authorization))
            } else {
                navigator.replace(LoginScreen)
            }
        }
    }

}

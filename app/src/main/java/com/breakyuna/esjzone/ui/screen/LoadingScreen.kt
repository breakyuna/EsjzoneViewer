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
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.hasCredentials
import com.breakyuna.esjzone.network.features.AuthorizationCheckResult
import com.breakyuna.esjzone.network.features.checkAuthorization
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
                    if (storedAuthorization == null || !storedAuthorization.hasCredentials()) {
                        null
                    } else {
                        when (EsjzoneClient.checkAuthorization(storedAuthorization)) {
                            AuthorizationCheckResult.AUTHORIZED -> storedAuthorization
                            AuthorizationCheckResult.UNAUTHORIZED -> {
                                AppLogger.w(
                                    "LoadingScreen",
                                    "Stored session was rejected by the server"
                                )
                                EsjzoneClient.clearPageCache()
                                EsjzoneClient.clearSession(selectedDomain)
                                dao.deleteByKey("ews_key")
                                dao.deleteByKey("ews_token")
                                dao.deleteByKey("session_domain")
                                null
                            }
                            AuthorizationCheckResult.UNKNOWN -> {
                                AppLogger.w(
                                    "LoadingScreen",
                                    "Could not verify stored session; keeping local session"
                                )
                                storedAuthorization
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("LoadingScreen", "Failed to restore local state", e)
                    null
                }
            }

            if (authorization != null) {
                navigator.replace(MainScreen(authorization = authorization))
            } else {
                navigator.replace(LoginScreen)
            }
        }
    }

}

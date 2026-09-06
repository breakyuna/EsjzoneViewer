package com.breakyuna.esjzone.ui.screen
import com.breakyuna.esjzone.app.PresentationAccess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.navigation.AppDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.AuthorizationCheckResult
import com.breakyuna.esjzone.network.features.checkAuthorization
import com.breakyuna.esjzone.ui.navigation.LocalAppNavigator
import com.breakyuna.esjzone.ui.navigation.AdaptiveAppShell
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography

class MainScreen(val authorization: Authorization) : AppDestination {

    override val key: String = "MainScreen"

    @Composable
    override fun Content() {
        val appNavigator = LocalAppNavigator.current
        val activeDomain = PresentationAccess.settings.domain.value
        var authorizationCheckResult by remember(authorization) {
            mutableStateOf<AuthorizationCheckResult?>(null)
        }
        var sessionPromptDismissed by remember(authorization) {
            mutableStateOf(false)
        }

        LaunchedEffect(authorization, activeDomain) {
            val sessionDomain = authorization.domain.ifBlank { activeDomain }
            if (sessionDomain != activeDomain) return@LaunchedEffect

            val result = try {
                withContext(Dispatchers.IO) {
                    PresentationAccess.client.checkAuthorization(authorization)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AuthorizationCheckResult.UNKNOWN
            }

            // The selected mirror is mutable while this request is in flight.
            // A result for a previous domain must never prompt or alter the new UI.
            val isStillActive = withContext(Dispatchers.IO) {
                PresentationAccess.settings.domain.value == sessionDomain &&
                    PresentationAccess.client.restoreAuthorization(sessionDomain) == authorization
            }
            if (isStillActive) {
                authorizationCheckResult = result
            }
        }

        androidx.compose.runtime.CompositionLocalProvider(
            LocalAuthorization provides authorization
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val showSessionBanner = authorizationCheckResult ==
                    AuthorizationCheckResult.UNAUTHORIZED && !sessionPromptDismissed
                if (showSessionBanner) {
                    SessionExpiredBanner(
                        onRelogin = {
                            appNavigator?.replace(LoginScreen) ?: run {
                                sessionPromptDismissed = true
                            }
                        },
                        onContinueOffline = { sessionPromptDismissed = true }
                    )
                }
                AdaptiveAppShell(
                    authorization = authorization,
                    rootNavigator = appNavigator ?: error("Navigation 3 root is not provided"),
                    modifier = Modifier.weight(1f),
                    // The banner owns the status-bar inset while it is
                    // visible; the shell owns it in every other state.
                    topInsetConsumed = showSessionBanner
                )
            }
        }
    }

}

@Composable
private fun SessionExpiredBanner(
    onRelogin: () -> Unit,
    onContinueOffline: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 1.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
        ) {
            Text(
                text = stringResource(R.string.session_expired_title),
                style = AppTypography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.session_expired_message),
                style = AppTypography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onContinueOffline) {
                    Text(text = stringResource(R.string.session_expired_continue))
                }
                Button(onClick = onRelogin) {
                    Text(text = stringResource(R.string.session_expired_relogin))
                }
            }
        }
    }
}

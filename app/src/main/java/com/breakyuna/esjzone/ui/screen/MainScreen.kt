package com.breakyuna.esjzone.ui.screen

import android.os.SystemClock
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.features.AuthorizationCheckResult
import com.breakyuna.esjzone.network.features.checkAuthorization
import com.breakyuna.esjzone.ui.navigation.CoverTransition
import com.breakyuna.esjzone.ui.navigation.LocalAppNavigator
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.tab.FavoriteTab
import com.breakyuna.esjzone.ui.tab.HistoryTab
import com.breakyuna.esjzone.ui.tab.HomeTab
import com.breakyuna.esjzone.ui.tab.ProfileTab

class MainScreen(val authorization: Authorization) : Screen {

    override val key: ScreenKey = "MainScreen"

    @Composable
    override fun Content() {
        val appNavigator = LocalAppNavigator.current
        val activeDomain = GlobalSettings.domain.value
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
                    EsjzoneClient.checkAuthorization(authorization)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AuthorizationCheckResult.UNKNOWN
            }

            // The selected mirror is mutable while this request is in flight.
            // A result for a previous domain must never prompt or alter the new UI.
            val isStillActive = withContext(Dispatchers.IO) {
                GlobalSettings.domain.value == sessionDomain &&
                    EsjzoneClient.restoreAuthorization(sessionDomain) == authorization
            }
            if (isStillActive) {
                authorizationCheckResult = result
            }
        }

        CompositionLocalProvider(value = LocalAuthorization provides authorization) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (authorizationCheckResult == AuthorizationCheckResult.UNAUTHORIZED &&
                    !sessionPromptDismissed
                ) {
                    SessionExpiredBanner(
                        onRelogin = {
                            appNavigator?.replace(LoginScreen) ?: run {
                                sessionPromptDismissed = true
                            }
                        },
                        onContinueOffline = {
                            sessionPromptDismissed = true
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Keep the tab navigator above the page stack.  TabScreen is
                    // replaced while a detail/reader page is open; placing the tab
                    // navigator inside it would recreate it with HomeTab on return.
                    TabNavigator(tab = HomeTab) {
                        Navigator(screen = TabScreen) { navigator ->
                            CoverTransition(navigator = navigator) { screen ->
                                CompositionLocalProvider(value = LocalBaseNavigator provides navigator) {
                                    screen.Content()
                                }
                            }
                        }
                    }
                }
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
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.session_expired_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.session_expired_message),
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

private object TabScreen : Screen {
    private fun readResolve(): Any = TabScreen

    @Composable
    override fun Content() {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    tonalElevation = 3.dp,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    TabNavigationItem(tab = HomeTab)
                    TabNavigationItem(
                        tab = HistoryTab,
                        onDoubleClick = HistoryTab::requestOpenLastReading
                    )
                    TabNavigationItem(tab = FavoriteTab)
                    TabNavigationItem(tab = ProfileTab)
                }
            }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                color = MaterialTheme.colorScheme.background
            ) {
                CurrentTab()
            }
        }
    }

}

@Composable
private fun RowScope.TabNavigationItem(
    tab: Tab,
    onDoubleClick: (() -> Unit)? = null
) {
    val tabNavigator = LocalTabNavigator.current
    val isSelected = tabNavigator.current == tab
    val lastTapAt = remember { mutableLongStateOf(0L) }

    NavigationBarItem(
        selected = isSelected,
        onClick = {
            val now = SystemClock.uptimeMillis()
            val wasAlreadySelected = tabNavigator.current == tab
            val isDoubleClick = onDoubleClick != null &&
                wasAlreadySelected &&
                now - lastTapAt.longValue in 1..420
            if (isDoubleClick) {
                lastTapAt.longValue = 0L
                tabNavigator.current = tab
                onDoubleClick?.invoke()
            } else {
                lastTapAt.longValue = now
                tabNavigator.current = tab
            }
        },
        icon = {
            tab.options.icon?.let { icon ->
                Icon(painter = icon, contentDescription = tab.options.title)
            }
        },
        label = {
            Text(
                text = tab.options.title,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    )
}

package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.NoAdultContent
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import coil.annotation.ExperimentalCoilApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.logout
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.SettingsButton
import com.breakyuna.esjzone.ui.component.SettingsColumn
import com.breakyuna.esjzone.ui.component.SettingsCustom
import com.breakyuna.esjzone.ui.component.SettingsSwitch
import com.breakyuna.esjzone.ui.component.SettingsText
import com.breakyuna.esjzone.ui.navigation.LocalAppNavigator
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.screen.LoginScreen
import com.breakyuna.esjzone.ui.theme.catppuccin.CatppuccinThemeType

object SettingsPage : Screen {

    private fun readResolve(): Any = SettingsPage

    @OptIn(ExperimentalCoilApi::class)
    @Composable
    override fun Content() {
        val appNavigator = LocalAppNavigator.current
        val navigator = LocalBaseNavigator.current

        val authorization = LocalAuthorization.current
        val configuration = LocalConfiguration.current

        val scope = rememberCoroutineScope()

        fun persistCache(key: String, value: String) {
            scope.launch(Dispatchers.IO) {
                try {
                    MainActivity.database.cacheDao().put(key, value)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    com.breakyuna.esjzone.util.AppLogger.e(
                        "SettingsPage",
                        "Failed to persist setting: $key",
                        e
                    )
                }
            }
        }

        var adult by remember {
            GlobalSettings.adult
        }
        var showLogoutConfirmation by remember {
            mutableStateOf(false)
        }
        var localCacheStats by remember {
            mutableStateOf<LocalCacheStats?>(null)
        }

        fun refreshLocalCacheStats() {
            scope.launch(Dispatchers.IO) {
                val pageStats = EsjzoneClient.pageCacheStats()
                val imageBytes = MainActivity.imageLoader.diskCache?.size ?: 0L
                withContext(Dispatchers.Main) {
                    localCacheStats = LocalCacheStats(
                        pageBytes = pageStats.sizeBytes,
                        pageEntries = pageStats.entryCount,
                        imageBytes = imageBytes
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            refreshLocalCacheStats()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AppBar(
                title = stringResource(id = R.string.settings),
                onBack = {
                    navigator?.pop()
                }
            )

            SettingsColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                SettingsText(text = stringResource(id = R.string.settings_category_preference))
                SettingsCustom(
                    imageVector = Icons.Filled.Language,
                    text = stringResource(id = R.string.settings_domain)
                ) {
                    var currentDomain by remember {
                        GlobalSettings.domain
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 16.dp)
                    ) {
                        for (domain in GlobalSettings.DOMAINS) {
                            val selected = currentDomain == domain
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        currentDomain = domain
                                        GlobalSettings.domain.value = domain
                                        scope.launch(Dispatchers.IO) {
                                            EsjzoneClient.clearPageCache()
                                        }
                                        persistCache("domain", domain)
                                    },
                                colors = if (selected) {
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                } else {
                                    CardDefaults.outlinedCardColors()
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "https://$domain",
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                SettingsCustom(
                    imageVector = Icons.Filled.ColorLens,
                    text = stringResource(id = R.string.settings_theme)
                ) {
                    var theme by remember {
                        GlobalSettings.theme
                    }

                    Text(
                        text = "Frappe",
                        modifier = Modifier.padding(start = 16.dp),
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        for (type in CatppuccinThemeType.frappes()) {
                            Card(
                                modifier = Modifier
                                    .width((configuration.screenWidthDp / 5).dp)
                                    .height(((configuration.screenWidthDp / 5) * 0.65).dp)
                                    .padding(4.dp)
                                    .clickable {
                                        theme = type
                                        persistCache("theme", type.name)
                                    },
                                colors = CardColors(
                                    containerColor = type.baseColor,
                                    contentColor = Color.White,
                                    disabledContainerColor = type.baseColor,
                                    disabledContentColor = Color.White
                                )
                            ) {
                                Box(
                                    modifier = Modifier.aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (type == theme) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = ""
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Latte",
                        modifier = Modifier.padding(start = 16.dp),
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        for (type in CatppuccinThemeType.lattes()) {
                            Card(
                                modifier = Modifier
                                    .width((configuration.screenWidthDp / 5).dp)
                                    .height(((configuration.screenWidthDp / 5) * 0.65).dp)
                                    .padding(4.dp)
                                    .clickable {
                                        theme = type
                                        persistCache("theme", type.name)
                                    },
                                colors = CardColors(
                                    containerColor = type.baseColor,
                                    contentColor = Color.White,
                                    disabledContainerColor = type.baseColor,
                                    disabledContentColor = Color.White
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (type == theme) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = ""
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Macchiato",
                        modifier = Modifier.padding(start = 16.dp),
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        for (type in CatppuccinThemeType.macchiatos()) {
                            Card(
                                modifier = Modifier
                                    .width((configuration.screenWidthDp / 5).dp)
                                    .height(((configuration.screenWidthDp / 5) * 0.65).dp)
                                    .padding(4.dp)
                                    .clickable {
                                        theme = type
                                        persistCache("theme", type.name)
                                    },
                                colors = CardColors(
                                    containerColor = type.baseColor,
                                    contentColor = Color.White,
                                    disabledContainerColor = type.baseColor,
                                    disabledContentColor = Color.White
                                )
                            ) {
                                Box(
                                    modifier = Modifier.aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (type == theme) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = ""
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Mocha",
                        modifier = Modifier.padding(start = 16.dp),
                        fontSize = 16.sp
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        for (type in CatppuccinThemeType.mochas()) {
                            Card(
                                modifier = Modifier
                                    .width((configuration.screenWidthDp / 5).dp)
                                    .height(((configuration.screenWidthDp / 5) * 0.65).dp)
                                    .padding(4.dp)
                                    .clickable {
                                        theme = type
                                        persistCache("theme", type.name)
                                    },
                                colors = CardColors(
                                    containerColor = type.baseColor,
                                    contentColor = Color.White,
                                    disabledContainerColor = type.baseColor,
                                    disabledContentColor = Color.White
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (type == theme) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = ""
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                SettingsText(text = stringResource(id = R.string.settings_category_content))
                SettingsSwitch(
                    imageVector = Icons.Filled.NoAdultContent,
                    text = stringResource(id = R.string.settings_showadultcontent),
                    checked = adult
                ) {
                    adult = it
                    persistCache("show_adult", it.toString())
                }

                SettingsText(text = stringResource(id = R.string.settings_category_app))
                SettingsCustom(
                    imageVector = Icons.Filled.Storage,
                    text = stringResource(id = R.string.local_cache)
                ) {
                    Column(modifier = Modifier.padding(end = 16.dp)) {
                        Text(
                            text = stringResource(id = R.string.local_cache_description),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val stats = localCacheStats
                        if (stats == null) {
                            Text(
                                text = stringResource(id = R.string.local_cache_loading),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    id = R.string.local_cache_pages,
                                    formatBytes(stats.pageBytes),
                                    stats.pageEntries
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Text(
                                text = stringResource(
                                    id = R.string.local_cache_images,
                                    formatBytes(stats.imageBytes)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            TextButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        EsjzoneClient.clearPageCache()
                                        withContext(Dispatchers.Main) {
                                            refreshLocalCacheStats()
                                        }
                                    }
                                }
                            ) {
                                Text(text = stringResource(id = R.string.local_cache_clear_pages))
                            }
                            TextButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        MainActivity.imageLoader.memoryCache?.clear()
                                        MainActivity.imageLoader.diskCache?.clear()
                                        withContext(Dispatchers.Main) {
                                            refreshLocalCacheStats()
                                        }
                                    }
                                }
                            ) {
                                Text(text = stringResource(id = R.string.local_cache_clear_images))
                            }
                        }
                    }
                }
                SettingsButton(
                    imageVector = Icons.Filled.BugReport,
                    text = stringResource(id = R.string.system_logs)
                ) {
                    navigator?.pushIfNotCurrent(LogsPage)
                }
                SettingsButton(
                    imageVector = Icons.Filled.Info,
                    text = stringResource(id = R.string.about)
                ) {
                    navigator?.pushIfNotCurrent(AboutPage)
                }
                SettingsButton(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    text = stringResource(id = R.string.button_logout)
                ) {
                    showLogoutConfirmation = true
                }
            }
        }

        if (showLogoutConfirmation) {
            AlertDialog(
                onDismissRequest = {
                    showLogoutConfirmation = false
                },
                title = {
                    Text(text = stringResource(id = R.string.logout_confirm_message))
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showLogoutConfirmation = false
                        }
                    ) {
                        Text(text = stringResource(id = R.string.logout_cancel))
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showLogoutConfirmation = false
                            scope.launch(Dispatchers.IO) {
                                try {
                                    EsjzoneClient.logout(authorization)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    com.breakyuna.esjzone.util.AppLogger.w(
                                        "SettingsPage",
                                        "Logout request failed; clearing local session anyway",
                                        e
                                    )
                                }

                                try {
                                    EsjzoneClient.clearSession(
                                        authorization.domain.ifBlank { GlobalSettings.domain.value }
                                    )
                                    val dao = MainActivity.database.cacheDao()
                                    dao.deleteByKey("ews_key")
                                    dao.deleteByKey("ews_token")
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    com.breakyuna.esjzone.util.AppLogger.e(
                                        "SettingsPage",
                                        "Failed to clear local session",
                                        e
                                    )
                                }
                            }
                            appNavigator?.replaceAll(LoginScreen)
                        }
                    ) {
                        Text(text = stringResource(id = R.string.logout_confirm))
                    }
                }
            )
        }
    }

}

private data class LocalCacheStats(
    val pageBytes: Long,
    val pageEntries: Int,
    val imageBytes: Long
)

private fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val kib = 1024.0
    val mib = kib * 1024.0
    return when {
        safeBytes >= mib -> String.format("%.1f MiB", safeBytes / mib)
        safeBytes >= kib -> String.format("%.1f KiB", safeBytes / kib)
        else -> "$safeBytes B"
    }
}

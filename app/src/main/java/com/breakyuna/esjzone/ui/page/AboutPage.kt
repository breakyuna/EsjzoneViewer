package com.breakyuna.esjzone.ui.page

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.BuildConfig
import com.breakyuna.esjzone.Constants
import com.breakyuna.esjzone.ui.designsystem.AccountSummary
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.update.ReleaseCheckState
import com.breakyuna.esjzone.update.ReleaseUpdateChecker

/** Product identity and open-source attribution, with no build/debug noise. */
object AboutPage : AppDestination {
    private fun readResolve(): Any = AboutPage

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val uriHandler = LocalUriHandler.current
        val context = LocalContext.current
        val checkState by ReleaseUpdateChecker.status.collectAsState()
        val autoCheck by ReleaseUpdateChecker.autoCheck.collectAsState()
        LaunchedEffect(Unit) { ReleaseUpdateChecker.initialize(context) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.about), style = AppTypography.titleLarge) },
                    navigationIcon = { BackIconButton { navigator?.pop() } }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().accountContentWidth().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.lg),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
            ) {
                item(key = "about-identity") {
                    AccountSummary(
                        Icons.Filled.MenuBook,
                        stringResource(R.string.app_name),
                        stringResource(R.string.profile_about_description)
                    )
                }
                item(key = "about-version") {
                    AboutSection(title = stringResource(R.string.compile_information)) {
                        AboutRow(
                            stringResource(R.string.build_version),
                            BuildConfig.VERSION_NAME,
                            onClick = { ReleaseUpdateChecker.checkNow(context) }
                        )
                        Text(
                            text = when (val state = checkState) {
                                ReleaseCheckState.Checking -> stringResource(R.string.update_checking)
                                ReleaseCheckState.UpToDate -> stringResource(R.string.update_up_to_date)
                                ReleaseCheckState.Error -> stringResource(R.string.update_check_error)
                                is ReleaseCheckState.Available -> stringResource(R.string.update_available_status, state.version)
                                ReleaseCheckState.Idle -> stringResource(R.string.update_check_tap_version)
                            },
                            style = AppTypography.bodySmall,
                            color = if (checkState is ReleaseCheckState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.update_auto_check), style = AppTypography.bodyMedium)
                                Text(stringResource(R.string.update_auto_check_description), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = autoCheck, onCheckedChange = { ReleaseUpdateChecker.setAutoCheck(context, it) })
                        }
                    }
                }
                item(key = "about-maintainers") {
                    AboutSection(title = stringResource(R.string.maintainers)) {
                        Constants.MAINTAINERS.forEach { Text(it, style = AppTypography.bodyLarge) }
                    }
                }
                item(key = "about-contributors") {
                    AboutSection(title = stringResource(R.string.contributors)) {
                        Constants.CONTRIBUTORS.forEach { Text(it, style = AppTypography.bodyLarge) }
                    }
                }
                item(key = "about-libraries-title") { Text(stringResource(R.string.open_source_libraries), style = AppTypography.titleMedium) }
                items(Constants.OPEN_SOURCE_LIBRARIES, key = { "license:${it.name}" }, contentType = { "license" }) { library ->
                    Card(
                        onClick = {
                            try {
                                uriHandler.openUri(library.url)
                            } catch (_: IllegalArgumentException) {
                                Toast.makeText(context, R.string.update_browser_unavailable, Toast.LENGTH_SHORT).show()
                            } catch (_: android.content.ActivityNotFoundException) {
                                Toast.makeText(context, R.string.update_browser_unavailable, Toast.LENGTH_SHORT).show()
                            } catch (_: SecurityException) {
                                Toast.makeText(context, R.string.update_browser_unavailable, Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = AppShapes.standard,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                Text(library.name, style = AppTypography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(library.owner, style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(library.description, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item(key = "about-powered") {
                    Column(Modifier.fillMaxWidth().padding(top = AppSpacing.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(stringResource(R.string.about_powered_by), style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.about_designed_with), style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.about_design_system), style = AppTypography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(title, style = AppTypography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Card(shape = AppShapes.standard, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
            Column(Modifier.fillMaxWidth().padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) { content() }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier).padding(vertical = AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
        Text(label, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = AppTypography.titleMedium, modifier = Modifier.weight(0.6f))
    }
}

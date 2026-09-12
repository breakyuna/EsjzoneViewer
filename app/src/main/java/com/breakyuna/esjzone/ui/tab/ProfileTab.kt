package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.novellibrary.user.UserProfile
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavPadding
import com.breakyuna.esjzone.network.features.getUserProfile
import com.breakyuna.esjzone.ui.designsystem.AccountIconBadge
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.ui.designsystem.AppAvatarImage
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppShimmerPlaceholder
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppTab
import com.breakyuna.esjzone.ui.navigation.AppTabOptions
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.page.BookmarksPage
import com.breakyuna.esjzone.ui.page.DownloadPage
import com.breakyuna.esjzone.ui.page.SettingsPage
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import androidx.compose.ui.graphics.vector.rememberVectorPainter

/** Account hub. Cached profile data makes this tab useful while offline. */
object ProfileTab : AppTab {
    private fun readResolve(): Any = ProfileTab

    override val options: AppTabOptions
        @Composable get() = AppTabOptions(3, stringResource(R.string.screen_main_tab_profile), rememberVectorPainter(image = Icons.Filled.Person))

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val domain = authorization.domain.ifBlank { PresentationAccess.settings.domain.value }
        var profileName by rememberSaveable(domain, authorization.ewsKey) { mutableStateOf<String?>(null) }
        var profileAvatar by rememberSaveable(domain, authorization.ewsKey) { mutableStateOf("") }
        var loading by remember(domain, authorization) { mutableStateOf(true) }
        var retry by remember { mutableStateOf(0) }
        val profile = profileName?.let { UserProfile(it, profileAvatar) }
        val menuItems = profileMenuItems()

        LaunchedEffect(domain, authorization.ewsKey, authorization.ewsToken, retry) {
            loading = true
            val prefix = profileCachePrefix(authorization, domain)
            try {
                val cached = withContext(Dispatchers.IO) {
                    val dao = PresentationAccess.database.cacheDao()
                    val name = dao.findByKey("${prefix}name")?.value
                    val avatar = dao.findByKey("${prefix}avatar")?.value.orEmpty()
                    name?.takeIf(String::isNotBlank)?.let { UserProfile(it, avatar) }
                }
                if (cached != null) { profileName = cached.name; profileAvatar = cached.avatarUrl }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLogger.w("ProfileTab", "Failed to read cached profile", e) }
            try {
                val fresh = withContext(Dispatchers.IO) { PresentationAccess.client.getUserProfile(authorization) }
                profileName = fresh.name
                profileAvatar = fresh.avatarUrl
                withContext(Dispatchers.IO) {
                    val dao = PresentationAccess.database.cacheDao()
                    dao.put("${prefix}name", fresh.name)
                    dao.put("${prefix}avatar", fresh.avatarUrl)
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLogger.w("ProfileTab", "Profile unavailable; using local snapshot", e) } finally { loading = false }
        }

        Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.navigation_profile), style = AppTypography.titleLarge) }) }) { padding ->
            val navPadding = LocalFloatingNavPadding.current
            val layoutDirection = LocalLayoutDirection.current
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .accountContentWidth()
                    .padding(top = padding.calculateTopPadding()),
                contentPadding = PaddingValues(
                    start = AppSpacing.lg + navPadding.calculateStartPadding(layoutDirection),
                    end = AppSpacing.lg,
                    top = AppSpacing.lg,
                    bottom = AppSpacing.lg + navPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                item(key = "profile-hero") { ProfileHero(profile, domain, loading, onRetry = { retry++ }) }
                item(key = "profile-menu-title") { Text(stringResource(R.string.profile_tools), style = AppTypography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(menuItems, key = { it.id }, contentType = { "profile_action" }) { item ->
                    ProfileAction(item, onClick = { navigator?.pushIfNotCurrent(item.destination) })
                }
            }
        }
    }
}

private data class ProfileMenuItem(val id: String, val icon: ImageVector, val title: String, val subtitle: String, val destination: com.breakyuna.esjzone.ui.navigation.AppDestination)

@Composable
private fun profileMenuItems(): List<ProfileMenuItem> = listOf(
    ProfileMenuItem("bookmarks", Icons.Filled.Bookmark, stringResource(R.string.bookmarks), stringResource(R.string.bookmarks_description), BookmarksPage),
    ProfileMenuItem("downloads", Icons.Filled.Download, stringResource(R.string.downloads), stringResource(R.string.profile_downloads_description), DownloadPage),
    ProfileMenuItem("settings", Icons.Filled.Settings, stringResource(R.string.settings), stringResource(R.string.profile_settings_description), SettingsPage)
)

@Composable
private fun ProfileHero(profile: UserProfile?, domain: String, loading: Boolean, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(shape = AppShapes.prominent, colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth()
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerLow)))
            .padding(AppSpacing.xl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            if (profile == null && loading) {
                AppShimmerPlaceholder(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                )
            } else if (profile == null) {
                AccountIconBadge(Icons.Filled.Person)
            } else {
                AppAvatarImage(EsjzoneUrls.resolve(profile.avatarUrl, EsjzoneUrls.baseForDomain(domain)).takeIf(String::isNotBlank) ?: R.drawable.missing_cover, profile.name, Modifier.size(72.dp).clip(CircleShape), ContentScale.Crop)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                if (profile == null && loading) {
                    AppShimmerPlaceholder(
                        modifier = Modifier.fillMaxWidth(0.68f).height(28.dp),
                        shape = AppShapes.compact,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                    )
                    AppShimmerPlaceholder(
                        modifier = Modifier.fillMaxWidth(0.44f).height(16.dp),
                        shape = AppShapes.compact,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
                    )
                } else if (profile == null) {
                    Text(stringResource(R.string.navigation_profile), style = AppTypography.titleLarge)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                } else {
                    Text(profile.name, style = AppTypography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Text(stringResource(R.string.profile_signed_in), style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f))
                }
                Text(domain, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ProfileAction(item: ProfileMenuItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = AppShapes.standard, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(AppSpacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AccountIconBadge(item.icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) { Text(item.title, style = AppTypography.titleMedium); Text(item.subtitle, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

private fun profileCachePrefix(authorization: com.breakyuna.esjzone.network.Authorization, domain: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest("${authorization.ewsKey}:${authorization.ewsToken}".toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "profile:$domain:$digest:"
}

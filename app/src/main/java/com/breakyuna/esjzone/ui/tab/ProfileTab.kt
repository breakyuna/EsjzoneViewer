@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.tab

import com.breakyuna.esjzone.ui.designsystem.appUsesWideLayout
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.app.cacheUserProfile
import com.breakyuna.esjzone.app.profileCachePrefix
import com.breakyuna.esjzone.novellibrary.user.UserProfile
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavPadding
import com.breakyuna.esjzone.network.features.getUserProfile
import com.breakyuna.esjzone.ui.designsystem.AccountIconBadge
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.ui.designsystem.AppProfileAvatarImage
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
import com.breakyuna.esjzone.ui.navigation.ContainerTransformTarget
import com.breakyuna.esjzone.ui.navigation.containerTransformContent
import com.breakyuna.esjzone.ui.navigation.containerTransformSource
import com.breakyuna.esjzone.ui.page.ReadingStatisticsPage
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.LocaleHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
        val loginSessionChanges by PresentationAccess.client.loginSessionChanges.collectAsStateWithLifecycle()
        val loginSessionRevision = loginSessionChanges[domain] ?: 0L
        val avatarCacheKey = remember(domain, authorization) {
            "${profileCachePrefix(authorization, domain)}avatar"
        }
        var profileName by rememberSaveable(domain, authorization.ewsKey) { mutableStateOf<String?>(null) }
        var profileAvatar by rememberSaveable(domain, authorization.ewsKey) { mutableStateOf("") }
        var profileExp by rememberSaveable(domain, authorization.ewsKey) { mutableStateOf<Int?>(null) }
        var profileLevel by rememberSaveable(domain, authorization.ewsKey) { mutableStateOf<String?>(null) }
        var loading by remember(domain, authorization) { mutableStateOf(true) }
        var retry by remember { mutableStateOf(0) }
        val profile = profileName?.let { UserProfile(it, profileAvatar, profileExp, profileLevel) }
        val menuItems = profileMenuItems()
        val wide = appUsesWideLayout()

        LaunchedEffect(domain, authorization.ewsKey, authorization.ewsToken) {
            val prefix = profileCachePrefix(authorization, domain)
            // Comment confirmation persists the exact profile used as evidence.
            // Observe it so a retained tab updates without another network request.
            PresentationAccess.database.cacheDao().observeByKey("${prefix}experience")
                .map { it?.value?.toIntOrNull() }
                .distinctUntilChanged()
                .collect { profileExp = it }
        }

        LaunchedEffect(domain, authorization.ewsKey, authorization.ewsToken, retry, loginSessionRevision) {
            loading = true
            val prefix = profileCachePrefix(authorization, domain)
            try {
                val cached = withContext(Dispatchers.IO) {
                    val dao = PresentationAccess.database.cacheDao()
                    val name = dao.findByKey("${prefix}name")?.value
                    val avatar = dao.findByKey("${prefix}avatar")?.value.orEmpty()
                    val experience = dao.findByKey("${prefix}experience")?.value?.toIntOrNull()
                    val level = dao.findByKey("${prefix}level")?.value?.takeIf(String::isNotBlank)
                    name?.takeIf(String::isNotBlank)?.let { UserProfile(it, avatar, experience, level) }
                }
                if (cached != null) {
                    profileName = cached.name
                    profileAvatar = cached.avatarUrl
                    profileLevel = cached.level
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLogger.w("ProfileTab", "Failed to read cached profile", e) }
            try {
                val fresh = withContext(Dispatchers.IO) {
                    PresentationAccess.client.getUserProfile(
                        authorization,
                        forceRefresh = retry > 0
                    )
                }
                profileName = fresh.name
                profileAvatar = fresh.avatarUrl
                profileLevel = fresh.level
                withContext(Dispatchers.IO) { cacheUserProfile(authorization, domain, fresh) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { AppLogger.w("ProfileTab", "Profile unavailable; using local snapshot", e) } finally { loading = false }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.navigation_profile), style = AppTypography.titleLarge) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { padding ->
            val navPadding = LocalFloatingNavPadding.current
            val layoutDirection = LocalLayoutDirection.current
            PullToRefreshBox(
                isRefreshing = loading,
                onRefresh = { if (!loading) retry++ },
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
            ) {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .accountContentWidth(),
                contentPadding = PaddingValues(
                    start = AppSpacing.lg + navPadding.calculateStartPadding(layoutDirection),
                    end = AppSpacing.lg,
                    top = AppSpacing.lg,
                    bottom = AppSpacing.lg + navPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                if (wide) {
                    item(key = "profile-wide-hub") {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xl)) {
                            Box(Modifier.weight(1f)) {
                                ProfileHero(profile, domain, avatarCacheKey, loading, onRetry = { retry++ })
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                                Text(stringResource(R.string.profile_tools), style = AppTypography.labelMedium,
                                    modifier = Modifier.semantics { heading() },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ProfileActions(menuItems) { navigator?.pushIfNotCurrent(it.destination) }
                            }
                        }
                    }
                } else {
                    item(key = "profile-hero") { ProfileHero(profile, domain, avatarCacheKey, loading, onRetry = { retry++ }) }
                    item(key = "profile-menu-title") {
                        Text(stringResource(R.string.profile_tools), style = AppTypography.labelMedium,
                            modifier = Modifier.padding(start = AppSpacing.xs, top = AppSpacing.sm).semantics { heading() },
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    item(key = "profile-actions") {
                        ProfileActions(menuItems) { navigator?.pushIfNotCurrent(it.destination) }
                    }
                }
            }
            }
        }
    }
}

private data class ProfileMenuItem(val target: ContainerTransformTarget, val icon: ImageVector, val title: String, val destination: com.breakyuna.esjzone.ui.navigation.AppDestination)

@Composable
private fun profileMenuItems(): List<ProfileMenuItem> = listOf(
    ProfileMenuItem(ContainerTransformTarget.READING_STATISTICS, Icons.Filled.QueryStats, stringResource(R.string.reading_stats_title), ReadingStatisticsPage),
    ProfileMenuItem(ContainerTransformTarget.BOOKMARKS, Icons.Filled.Bookmark, stringResource(R.string.bookmarks), BookmarksPage),
    ProfileMenuItem(ContainerTransformTarget.DOWNLOADS, Icons.Filled.Download, stringResource(R.string.downloads), DownloadPage),
    ProfileMenuItem(ContainerTransformTarget.SETTINGS, Icons.Filled.Settings, stringResource(R.string.settings), SettingsPage)
)

@Composable
private fun ProfileActions(items: List<ProfileMenuItem>, onClick: (ProfileMenuItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                row.forEach { item ->
                    ProfileAction(item, modifier = Modifier.weight(1f).fillMaxHeight(), onClick = { onClick(item) })
                }
            }
        }
    }
}

@Composable
private fun ProfileHero(profile: UserProfile?, domain: String, avatarCacheKey: String, loading: Boolean, onRetry: () -> Unit) {
    Card(shape = AppShapes.prominent, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(AppSpacing.xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            if (profile == null && loading) {
                AppShimmerPlaceholder(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                )
            } else if (profile == null) {
                AccountIconBadge(Icons.Filled.Person)
            } else {
                AppProfileAvatarImage(
                    model = EsjzoneUrls.resolve(profile.avatarUrl, EsjzoneUrls.baseForDomain(domain)),
                    cacheKey = avatarCacheKey,
                    contentDescription = profile.name,
                    modifier = Modifier.size(72.dp).clip(CircleShape)
                )
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        Text(
                            text = profile.name,
                            style = AppTypography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        profile.exp?.let {
                            Text(
                                text = stringResource(R.string.profile_experience, it),
                                style = AppTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    profile.level?.let {
                        Text(
                            text = it.trim(),
                            style = AppTypography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
                Text(domain, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun ProfileAction(item: ProfileMenuItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val containerModifier = modifier.containerTransformSource(item.target)
    val contentModifier = Modifier.containerTransformContent()
    Surface(onClick = onClick, modifier = containerModifier, shape = AppShapes.standard, color = MaterialTheme.colorScheme.surface) {
        Column(
            contentModifier.fillMaxWidth().heightIn(min = 96.dp).padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(18.dp))
            }
            Text(item.title, style = AppTypography.labelLarge)
        }
    }
}

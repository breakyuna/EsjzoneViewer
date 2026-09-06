package com.breakyuna.esjzone.ui.discovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.component.AppNovelPreviewCard

/** Shared top-level chrome for Discovery destinations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(title, style = AppTypography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = androidx.compose.ui.res.stringResource(R.string.reader_back))
                }
            }
        },
        actions = {
            if (onRefresh != null) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = androidx.compose.ui.res.stringResource(R.string.comment_refresh))
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        modifier = modifier
    )
}

@Composable
fun DiscoveryScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            DiscoveryTopBar(title = title, onBack = onBack, onRefresh = onRefresh)
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

@Composable
fun DiscoveryNovelCard(
    novel: CoveredNovel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    AppNovelPreviewCard(
        novel = novel,
        compact = compact,
        showLatestChapter = compact,
        modifier = modifier.semantics {
            contentDescription = "${androidx.compose.ui.res.stringResource(R.string.forum_open_novel)}：${novel.name}"
            role = Role.Button
        },
        onClick = onClick
    )
}

/** A lightweight category result row; CategoryNovel has no card metadata. */
@Composable
fun DiscoveryCategoryNovelCard(
    novel: CategoryNovel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Button },
        shape = AppShapes.standard,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.AutoStories, contentDescription = null)
                }
            }
            Text(
                text = novel.name,
                style = AppTypography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Filled.ArrowForward, contentDescription = androidx.compose.ui.res.stringResource(R.string.forum_open_novel))
        }
    }
}

@Composable
fun DiscoveryCategoryCard(
    title: String,
    isAdult: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val accent = if (isAdult) colors.error else when (index % 3) {
        0 -> colors.primary
        1 -> colors.tertiary
        else -> colors.secondary
    }
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .semantics {
                contentDescription = "${androidx.compose.ui.res.stringResource(R.string.categories)}：$title"
                role = Role.Button
            },
        shape = AppShapes.prominent,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.26f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppSpacing.lg),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = CircleShape,
                color = accent.copy(alpha = 0.14f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Category, contentDescription = null, tint = accent)
                }
            }
            Text(
                text = title,
                style = AppTypography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class DiscoveryFilterOption(val value: Int, val label: String)

@Composable
fun DiscoveryFilterMenu(
    label: String,
    selected: DiscoveryFilterOption,
    options: List<DiscoveryFilterOption>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .semantics {
                    contentDescription = "$label：${selected.label}"
                    role = Role.Button
                },
            shape = AppShapes.compact,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Column(modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)) {
                Text(label, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected.label, style = AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelected(option.value)
                    }
                )
            }
        }
    }
}

@Composable
fun DiscoverySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = androidx.compose.ui.res.stringResource(R.string.search_placeholder)
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = androidx.compose.ui.res.stringResource(R.string.search_placeholder)
            },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            Row {
                if (value.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text(androidx.compose.ui.res.stringResource(R.string.clear)) }
                }
                TextButton(onClick = onSearch, enabled = value.isNotBlank()) { Text(androidx.compose.ui.res.stringResource(R.string.search_action)) }
            }
        },
        placeholder = { Text(placeholder) },
        shape = AppShapes.standard
    )
}

@Composable
fun DiscoveryLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        repeat(3) { index ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (index == 0) 124.dp else 104.dp),
                shape = AppShapes.standard,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {}
            if (index == 0) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun DiscoveryEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    actionLabel: String = "重试"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Icon(Icons.Filled.AutoStories, contentDescription = null, modifier = Modifier.size(40.dp))
        Text(title, style = AppTypography.titleMedium)
        Text(message, style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun DiscoveryErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    DiscoveryEmptyState(
        title = androidx.compose.ui.res.stringResource(R.string.load_failed),
        message = message,
        modifier = modifier,
        onAction = onRetry
    )
}

@Composable
fun DiscoveryOfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = AppShapes.compact
    ) {
        Text(
            text = androidx.compose.ui.res.stringResource(R.string.load_network_error),
            style = AppTypography.bodySmall,
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        )
    }
}

/** Inline footer used by all paginated Discovery lists. */
fun LazyListScope.discoveryLoadingFooter(
    loading: Boolean,
    hasMore: Boolean,
    onRetry: (() -> Unit)? = null,
    errorMessage: String? = null
) {
    if (!hasMore && errorMessage == null) return
    item(key = "discovery-pagination-footer", contentType = "pagination") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                errorMessage != null -> {
                    Text(errorMessage, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (onRetry != null) TextButton(onClick = onRetry) { Text(androidx.compose.ui.res.stringResource(R.string.retry)) }
                }
                loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else -> Text(androidx.compose.ui.res.stringResource(R.string.the_end), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

package com.breakyuna.esjzone.ui.discovery

import com.breakyuna.esjzone.ui.designsystem.appAccentColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Clear
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
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.component.AppNovelPreviewCard
import com.breakyuna.esjzone.ui.designsystem.AppShimmerPlaceholder
import com.breakyuna.esjzone.ui.designsystem.appAdultColors

/** Shared top-level chrome for Discovery destinations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryTopBar(
    title: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit)? = null
) {
    TopAppBar(
        title = {
            if (titleContent != null) {
                titleContent()
            } else {
                Text(title, style = AppTypography.titleLarge)
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.breakyuna.esjzone.ui.designsystem.globalStringResource(R.string.reader_back))
                }
            }
        },
        actions = {
            actions()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        modifier = modifier
    )
}

@Composable
fun DiscoveryScaffold(
    title: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable () -> Unit)? = null,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        topBar = {
            DiscoveryTopBar(title = title, onBack = onBack, actions = actions, titleContent = titleContent)
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content
    )
}

/** Top bar for search destinations containing a back action and an inline search field. */
@Composable
fun DiscoverySearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = AppSpacing.xs,
                    end = AppSpacing.md,
                    top = AppSpacing.sm,
                    bottom = AppSpacing.xs
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_back)
                )
            }
            DiscoverySearchField(
                value = query,
                onValueChange = onQueryChange,
                onSearch = onSearch,
                onClear = onClear,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DiscoveryNovelCard(
    novel: CoveredNovel,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val accessibilityLabel = stringResource(R.string.forum_open_novel)
    AppNovelPreviewCard(
        novel = novel,
        compact = compact,
        showLatestChapter = compact,
        modifier = modifier.semantics {
            contentDescription = "$accessibilityLabel：${novel.name}"
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(vertical = AppSpacing.md),
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
            style = AppTypography.labelLarge,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Icon(Icons.Filled.ArrowForward, contentDescription = com.breakyuna.esjzone.ui.designsystem.globalStringResource(R.string.forum_open_novel))
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
    val adultColors = appAdultColors()
    val accessibilityLabel = stringResource(R.string.categories)
    val accent = if (isAdult) adultColors.content else when (index % 3) {
        0 -> colors.primary
        1 -> colors.tertiary
        else -> appAccentColors().info
    }
    val accentContainer = if (isAdult) adultColors.container else accent.copy(alpha = 0.14f)
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .semantics {
                contentDescription = "$accessibilityLabel：$title"
                role = Role.Button
            },
        shape = AppShapes.prominent,
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = if (isAdult) BorderStroke(1.dp, adultColors.outline) else null
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
                color = accentContainer
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
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.heightIn(min = 36.dp) else Modifier)
                .clickable { expanded = true }
                .semantics {
                    contentDescription = "$label：${selected.label}"
                    role = Role.Button
                },
            shape = AppShapes.compact,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            if (compact) {
                Row(
                    modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        selected.label,
                        style = AppTypography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)) {
                    Text(label, style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selected.label, style = AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
    placeholder: String = com.breakyuna.esjzone.ui.designsystem.globalStringResource(R.string.search_placeholder),
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val accessibilityLabel = stringResource(R.string.search_placeholder)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val submitSearch = {
        if (value.isNotBlank()) {
            keyboardController?.hide()
            focusManager.clearFocus()
            onSearch()
        }
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = accessibilityLabel
            }
            .onKeyEvent { event ->
                if ((event.key == Key.Enter || event.key == Key.NumPadEnter) && event.type == KeyEventType.KeyDown) {
                    submitSearch()
                    true
                } else {
                    false
                }
            },
        singleLine = true,
        leadingIcon = leadingIcon,
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = stringResource(R.string.clear)
                        )
                    }
                }
                IconButton(onClick = submitSearch, enabled = value.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search_action),
                        tint = if (value.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
            }
        },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
        shape = AppShapes.standard,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppShimmerPlaceholder(
                    modifier = Modifier.size(width = 76.dp, height = if (index == 0) 108.dp else 96.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    AppShimmerPlaceholder(Modifier.fillMaxWidth(0.88f).height(20.dp), shape = AppShapes.compact)
                    AppShimmerPlaceholder(Modifier.fillMaxWidth(0.62f).height(14.dp), shape = AppShapes.compact)
                    AppShimmerPlaceholder(Modifier.fillMaxWidth(0.74f).height(14.dp), shape = AppShapes.compact)
                }
            }
        }
    }
}

@Composable
fun DiscoveryEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    actionLabel: String = stringResource(R.string.retry)
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AppSpacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Surface(shape = AppShapes.prominent, color = MaterialTheme.colorScheme.primaryContainer) {
            Icon(Icons.Filled.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(14.dp).size(32.dp))
        }
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
    com.breakyuna.esjzone.ui.designsystem.AppErrorState(
        title = stringResource(R.string.load_failed),
        message = message,
        modifier = modifier,
        retryLabel = stringResource(R.string.retry),
        onRetry = onRetry
    )
}

@Composable
fun DiscoveryOfflineBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = appAccentColors().infoContainer,
        contentColor = appAccentColors().info,
        shape = AppShapes.compact
    ) {
        Text(
            text = com.breakyuna.esjzone.ui.designsystem.globalStringResource(R.string.load_network_error),
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
                    if (onRetry != null) TextButton(onClick = onRetry) { Text(com.breakyuna.esjzone.ui.designsystem.globalStringResource(R.string.retry)) }
                }
                loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else -> Text(com.breakyuna.esjzone.ui.designsystem.globalStringResource(R.string.the_end), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

package com.breakyuna.esjzone.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.appSurfaceColors

/** Flat, edge-to-edge top bar for secondary destinations. */
@Composable
fun AppBackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    belowContent: @Composable ColumnScope.() -> Unit = {}
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.xs)
                    .padding(top = AppSpacing.md, bottom = AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(AppSpacing.xxxl)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back)
                    )
                }
                Text(
                    text = title,
                    style = AppTypography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = AppSpacing.sm)
                )
                actions()
            }
            belowContent()
            HorizontalDivider(
                thickness = 1.dp,
                color = appSurfaceColors().divider.copy(alpha = 0.6f)
            )
        }
    }
}

/** Home heading and shortcut row. The callbacks are destination-owned. */
@Composable
fun AppHomeHeader(
    domain: String,
    onSearch: () -> Unit,
    onCategories: () -> Unit,
    onForum: () -> Unit,
    onGuestbook: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Text(
                        text = stringResource(R.string.home_discover),
                        style = AppTypography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = AppShapes.pill,
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(MaterialTheme.colorScheme.tertiary, AppShapes.pill)
                            )
                            Text(
                                text = stringResource(R.string.home_site_status, domain),
                                style = AppTypography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(AppSpacing.xxxl)) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = stringResource(R.string.screen_main_tab_search)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            AppShortcut(Icons.Filled.Category, stringResource(R.string.categories), onCategories, Modifier.weight(1f))
            AppShortcut(Icons.Filled.Forum, stringResource(R.string.forum), onForum, Modifier.weight(1f))
            AppShortcut(Icons.Filled.ChatBubbleOutline, stringResource(R.string.guestbook), onGuestbook, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppShortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(AppTouchTargetSize)
            .semantics { role = Role.Button }
            .clickable(onClick = onClick),
        shape = AppShapes.compact,
        color = appSurfaceColors().subtle
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.sm),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(AppSpacing.xs))
            Text(label, style = AppTypography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val AppTouchTargetSize = 48.dp

/** Search input with explicit keyboard and clear actions. */
@Composable
fun AppSearchHeader(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AppSpacing.xs, end = AppSpacing.md, top = AppSpacing.md, bottom = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(AppSpacing.xxxl)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                shape = AppShapes.prominent,
                textStyle = AppTypography.bodyLarge,
                placeholder = {
                    Text(stringResource(R.string.search_placeholder), style = AppTypography.bodyMedium)
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.close))
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )
            Button(
                onClick = onSearch,
                enabled = enabled,
                modifier = Modifier.height(AppTouchTargetSize),
                shape = AppShapes.standard,
                contentPadding = ButtonDefaults.ContentPadding
            ) { Text(stringResource(R.string.search_action), style = AppTypography.labelLarge) }
        }
    }
}

/** Section heading with an optional trailing action. */
@Composable
fun AppSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp)
                .background(accent, AppShapes.pill)
        )
        Text(
            text = title,
            style = AppTypography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = AppSpacing.md)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, style = AppTypography.labelLarge)
                Icon(
                    imageVector = Icons.Filled.ArrowForwardIos,
                    contentDescription = actionLabel,
                    modifier = Modifier.padding(start = AppSpacing.xs).size(14.dp)
                )
            }
        }
    }
}

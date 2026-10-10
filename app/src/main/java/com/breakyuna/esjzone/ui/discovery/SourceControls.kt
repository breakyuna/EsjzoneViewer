package com.breakyuna.esjzone.ui.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

@Composable
internal fun sourceLabel(source: LibrarySource): String = stringResource(
    if (source == LibrarySource.ESJZONE) R.string.source_esjzone else R.string.source_wenku8
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceSelector(
    selected: LibrarySource?,
    onSelect: (LibrarySource?) -> Unit,
    modifier: Modifier = Modifier,
    includeAll: Boolean = false,
    enabled: Boolean = true
) {
    val choices = if (includeAll) listOf(null, LibrarySource.ESJZONE, LibrarySource.WENKU8)
        else listOf(LibrarySource.ESJZONE, LibrarySource.WENKU8)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs)) {
        choices.forEachIndexed { index, source ->
            SegmentedButton(
                selected = selected == source,
                onClick = { if (selected != source) onSelect(source) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                icon = {},
                label = {
                    Text(if (source == null) stringResource(R.string.source_all) else sourceLabel(source),
                        style = AppTypography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            )
        }
    }
}

@Composable
internal fun SourceBadge(source: LibrarySource, modifier: Modifier = Modifier) {
    Surface(modifier, shape = AppShapes.standard,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(sourceLabel(source), Modifier.padding(horizontal = AppSpacing.sm, vertical = 2.dp),
            style = AppTypography.labelMedium, maxLines = 1)
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.BookshelfSort
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import kotlin.math.roundToInt

@Composable
internal fun BookshelfOptionsSheet(
    selectedTab: Int, onTabChange: (Int) -> Unit,
    filter: BookshelfFilter, onFilter: (BookshelfFilter) -> Unit,
    sort: BookshelfSort.Order, byName: Boolean, onSort: (BookshelfSort.Order) -> Unit, onSortByName: () -> Unit,
    reversed: Boolean, onReverse: (Boolean) -> Unit,
    listView: Boolean, onListView: (Boolean) -> Unit,
    columns: Int, onColumns: (Int) -> Unit,
    showRecent: Boolean, onShowRecent: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            listOf(R.string.shelf_options_filter, R.string.bookshelf_sort, R.string.shelf_options_display).forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { onTabChange(index) }, text = { Text(stringResource(title)) })
            }
        }
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)) {
            when (selectedTab) {
                0 -> {
                    val labels = listOf(R.string.shelf_filter_all, R.string.bookshelf_filter_downloaded,
                        R.string.bookshelf_filter_updates, R.string.bookshelf_unread, R.string.shelf_filter_read)
                    BookshelfFilter.entries.forEachIndexed { index, value ->
                        OptionRow(stringResource(labels[index]), filter == value) { onFilter(value) }
                    }
                }
                1 -> {
                    OptionRow(stringResource(R.string.shelf_sort_name), byName, onSortByName)
                    BookshelfSort.Order.entries.forEach { value ->
                        OptionRow(stringResource(value.labelRes()), !byName && sort == value) { onSort(value) }
                    }
                    ToggleRow(stringResource(R.string.shelf_sort_reverse), reversed, onReverse)
                }
                2 -> {
                    OptionRow(stringResource(R.string.shelf_display_grid), !listView) { onListView(false) }
                    OptionRow(stringResource(R.string.shelf_display_list), listView) { onListView(true) }
                    if (!listView) {
                        Row(Modifier.fillMaxWidth().padding(top = AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.shelf_columns), modifier = Modifier.weight(1f))
                            Text(if (columns == 0) stringResource(R.string.shelf_columns_auto) else columns.toString())
                        }
                        Slider(value = columns.toFloat(), onValueChange = { onColumns(it.roundToInt()) }, valueRange = 0f..8f, steps = 7)
                    }
                    ToggleRow(stringResource(R.string.shelf_show_recent), showRecent, onShowRecent)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(vertical = AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(title, modifier = Modifier.padding(start = AppSpacing.md))
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).padding(vertical = AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(title, modifier = Modifier.padding(start = AppSpacing.md))
    }
}

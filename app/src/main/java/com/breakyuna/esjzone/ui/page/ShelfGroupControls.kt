package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

internal enum class ShelfGroupDialog { MANAGE, CREATE, RENAME, DELETE, MOVE, CREATE_AND_MOVE }

@Composable
internal fun ShelfGroupControls(
    names: List<String>,
    active: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit
) {
    val scroll = rememberLazyListState()
    LaunchedEffect(active, names) {
        val index = when (active) {
            null -> 0
            "" -> 1
            else -> names.indexOf(active).takeIf { it >= 0 }?.plus(2) ?: return@LaunchedEffect
        }
        val layout = scroll.layoutInfo
        val fullyVisible = layout.visibleItemsInfo.any {
            it.index == index && it.offset >= layout.viewportStartOffset &&
                it.offset + it.size <= layout.viewportEndOffset
        }
        if (!fullyVisible) scroll.animateScrollToItem(index)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(
            state = scroll,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "all") {
                FilterChip(selected = active == null, enabled = enabled, onClick = { onSelect(null) },
                    label = { Text(stringResource(R.string.group_all)) })
            }
            item(key = "ungrouped") {
                FilterChip(selected = active == "", enabled = enabled, onClick = { onSelect("") },
                    label = { Text(stringResource(R.string.group_ungrouped)) })
            }
            items(names, key = { "group:$it" }) { name ->
                FilterChip(selected = active == name, enabled = enabled, onClick = { onSelect(name) },
                    label = { Text(name, Modifier.widthIn(max = 160.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }
        IconButton(onClick = onManage, enabled = enabled) {
            Icon(Icons.Filled.FolderOpen, stringResource(R.string.group_manage))
        }
    }
}

/** One dialog at a time; cancel returns to the previous step without losing book selection. */
@Composable
internal fun ShelfGroupDialogs(
    dialog: ShelfGroupDialog,
    original: String,
    names: List<String>,
    selectedGroups: Set<String>,
    selectedCount: Int,
    saving: Boolean,
    failed: Boolean,
    onNavigate: (ShelfGroupDialog, String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (String?, Boolean) -> Unit
) {
    var target by rememberSaveable { mutableStateOf(selectedGroups.singleOrNull()) }
    val cancel: () -> Unit = {
        if (!saving) when (dialog) {
            ShelfGroupDialog.CREATE, ShelfGroupDialog.RENAME, ShelfGroupDialog.DELETE ->
                onNavigate(ShelfGroupDialog.MANAGE, "")
            ShelfGroupDialog.CREATE_AND_MOVE -> onNavigate(ShelfGroupDialog.MOVE, "")
            else -> onDismiss()
        }
    }
    when (dialog) {
        ShelfGroupDialog.CREATE, ShelfGroupDialog.RENAME, ShelfGroupDialog.CREATE_AND_MOVE -> {
            ShelfGroupNameDialog(
                original = original.takeIf { dialog == ShelfGroupDialog.RENAME },
                names = names,
                createAndMove = dialog == ShelfGroupDialog.CREATE_AND_MOVE,
                saving = saving,
                failed = failed,
                onDismiss = cancel,
                onConfirm = { name ->
                    when (dialog) {
                        ShelfGroupDialog.RENAME -> onRename(original, name)
                        ShelfGroupDialog.CREATE_AND_MOVE -> onMove(name, true)
                        else -> onCreate(name)
                    }
                }
            )
        }
        ShelfGroupDialog.DELETE -> AlertDialog(
            onDismissRequest = cancel,
            title = { Text(stringResource(R.string.group_delete)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.group_delete_hint, original))
                    GroupOperationError(failed)
                }
            },
            confirmButton = {
                TextButton(onClick = { onDelete(original) }, enabled = !saving) {
                    Text(stringResource(R.string.group_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = cancel, enabled = !saving) { Text(stringResource(android.R.string.cancel)) } }
        )
        ShelfGroupDialog.MANAGE -> AlertDialog(
            onDismissRequest = cancel,
            title = { Text(stringResource(R.string.group_manage)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.group_local_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onNavigate(ShelfGroupDialog.CREATE, "") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, null)
                        Text(stringResource(R.string.group_create), Modifier.padding(start = 8.dp))
                    }
                    if (names.isEmpty()) Text(stringResource(R.string.group_manage_empty))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        items(names, key = { it }) { name ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                IconButton(onClick = { onNavigate(ShelfGroupDialog.RENAME, name) }) {
                                    Icon(Icons.Filled.Edit, stringResource(R.string.group_rename_named, name))
                                }
                                IconButton(onClick = { onNavigate(ShelfGroupDialog.DELETE, name) }) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.group_delete_named, name), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.group_done)) } }
        )
        ShelfGroupDialog.MOVE -> AlertDialog(
            onDismissRequest = cancel,
            title = { Text(stringResource(R.string.group_move_title, selectedCount)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.group_move_hint), style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 320.dp).selectableGroup()) {
                        items(listOf("") + names, key = { it }) { name ->
                            Row(
                                Modifier.fillMaxWidth().selectable(
                                    selected = target == name, enabled = !saving,
                                    role = Role.RadioButton, onClick = { target = name }
                                ).padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = target == name, onClick = null, enabled = !saving)
                                Column(Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                                    Text(if (name.isEmpty()) stringResource(R.string.group_ungrouped) else name,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (selectedGroups.singleOrNull() == name) Text(
                                        stringResource(R.string.group_current),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { onNavigate(ShelfGroupDialog.CREATE_AND_MOVE, "") },
                        enabled = !saving, modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, null)
                        Text(stringResource(R.string.group_create_and_move), Modifier.padding(start = 8.dp))
                    }
                    GroupOperationError(failed)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saving && selectedCount > 0 && target != null &&
                        (target == "" || target in names) && selectedGroups.any { it != target },
                    onClick = { onMove(target?.takeIf { it.isNotEmpty() }, false) }
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.group_move_confirm))
                }
            },
            dismissButton = { TextButton(onClick = cancel, enabled = !saving) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}

@Composable
private fun ShelfGroupNameDialog(
    original: String?,
    names: List<String>,
    createAndMove: Boolean,
    saving: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by rememberSaveable(original, createAndMove, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(original.orEmpty(), selection = TextRange(0, original.orEmpty().length)))
    }
    val name = input.text.trim()
    val duplicate = name != original && name in names
    val canSave = name.isNotEmpty() && name != original && !duplicate && !saving
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(when {
            original != null -> R.string.group_rename
            createAndMove -> R.string.group_create_and_move
            else -> R.string.group_create
        })) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.text.length <= 60) input = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    enabled = !saving, singleLine = true,
                    label = { Text(stringResource(R.string.group_name)) },
                    isError = duplicate,
                    supportingText = { Text(stringResource(if (duplicate) R.string.group_name_duplicate else R.string.group_name_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(name) })
                )
                GroupOperationError(failed)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = canSave) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(when {
                    original != null -> R.string.group_save
                    createAndMove -> R.string.group_create_and_move
                    else -> R.string.group_create
                }))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(android.R.string.cancel)) } }
    )
}

@Composable
private fun GroupOperationError(failed: Boolean) {
    if (failed) Text(stringResource(R.string.group_error), color = MaterialTheme.colorScheme.error)
}

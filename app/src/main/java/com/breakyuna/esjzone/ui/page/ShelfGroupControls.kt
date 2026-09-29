package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

@Composable
internal fun ShelfGroupPicker(names: List<String>, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.group_move)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.group_ungrouped)) }
            names.forEach { name -> TextButton(onClick = { onSelect(name) }) { Text(name) } }
        } }, confirmButton = {}, dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        })
}

@Composable
internal fun ShelfGroupControls(names: List<String>, active: String?, onSelect: (String?) -> Unit, model: FavoritePageModel) {
    var managing by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = active == null, onClick = { onSelect(null) }, label = { Text(stringResource(R.string.group_all)) })
        FilterChip(selected = active == "", onClick = { onSelect("") }, label = { Text(stringResource(R.string.group_ungrouped)) })
        names.forEach { group -> FilterChip(selected = active == group, onClick = { onSelect(group) }, label = { Text(group) }) }
        TextButton(onClick = { managing = true }) { Text(stringResource(R.string.shelf_groups)) }
    }
    if (managing) AlertDialog(onDismissRequest = { managing = false }, title = { Text(stringResource(R.string.shelf_groups)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            TextButton(onClick = { original = null; name = ""; editing = true }) { Text(stringResource(R.string.group_create)) }
            names.forEach { group ->
                Column {
                    Text(group)
                    Row {
                        TextButton(onClick = { original = group; name = group; editing = true }) { Text(stringResource(R.string.group_rename)) }
                        TextButton(onClick = { deleting = group }) { Text(stringResource(R.string.group_delete)) }
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = { managing = false }) { Text(stringResource(android.R.string.ok)) } })
    if (editing) AlertDialog(onDismissRequest = { editing = false },
        title = { Text(stringResource(if (original == null) R.string.group_create else R.string.group_rename)) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true,
            label = { Text(stringResource(R.string.group_name)) }) },
        confirmButton = { TextButton(enabled = name.trim().isNotEmpty() && name.trim() !in names, onClick = {
            val old = original
            if (old == null) model.createGroup(name) else model.renameGroup(old, name)
            if (active == old && old != null) onSelect(name.trim())
            editing = false
        }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel)) } })
    deleting?.let { group -> AlertDialog(onDismissRequest = { deleting = null },
        title = { Text(stringResource(R.string.group_delete)) },
        text = { Text(stringResource(R.string.group_delete_hint, group)) },
        confirmButton = { TextButton(onClick = {
            model.deleteGroup(group); if (active == group) onSelect(null); deleting = null
        }) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(android.R.string.cancel)) } }) }
}

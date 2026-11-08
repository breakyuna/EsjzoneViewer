package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceRule
import com.breakyuna.esjzone.ui.reader.ReaderNameReplaceSettings
import java.util.UUID

@Composable
internal fun ReaderNameReplaceDialog(
    settings: ReaderNameReplaceSettings,
    onChange: (ReaderNameReplaceSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var editingRule by remember { mutableStateOf<ReaderNameReplaceRule?>(null) }
    var addingRule by remember { mutableStateOf(false) }
    fun replaceRule(rule: ReaderNameReplaceRule) {
        onChange(settings.copy(rules = settings.rules.map { if (it.id == rule.id) rule else it }))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_name_replace)) },
        text = {
            if (settings.rules.isEmpty()) {
                Text(
                    stringResource(R.string.reader_name_replace_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    settings.rules.forEach { rule ->
                        val displayedTarget = if (rule.target.isBlank()) {
                            stringResource(R.string.reader_name_replace_empty_target)
                        } else rule.target
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = { replaceRule(rule.copy(enabled = it)) }
                            )
                            Column(
                                modifier = Modifier.weight(1f).padding(horizontal = AppSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(rule.source, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    displayedTarget,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editingRule = rule }) {
                                Icon(
                                    Icons.Default.Edit,
                                    stringResource(R.string.reader_name_replace_edit)
                                )
                            }
                            IconButton(onClick = {
                                onChange(settings.copy(rules = settings.rules.filterNot { it.id == rule.id }))
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    stringResource(R.string.reader_name_replace_delete)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { addingRule = true }) {
                Text(stringResource(R.string.reader_name_replace_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    )

    val ruleBeingEdited = editingRule
    if (addingRule || ruleBeingEdited != null) {
        ReaderNameReplaceRuleEditor(
            rule = ruleBeingEdited,
            onDismiss = {
                addingRule = false
                editingRule = null
            },
            onSave = { source, target ->
                if (ruleBeingEdited == null) {
                    onChange(
                        settings.copy(
                            rules = settings.rules + ReaderNameReplaceRule(
                                id = UUID.randomUUID().toString(),
                                source = source,
                                target = target
                            )
                        )
                    )
                } else {
                    replaceRule(ruleBeingEdited.copy(source = source, target = target))
                }
                addingRule = false
                editingRule = null
            }
        )
    }
}

@Composable
private fun ReaderNameReplaceRuleEditor(
    rule: ReaderNameReplaceRule?,
    onDismiss: () -> Unit,
    onSave: (source: String, target: String) -> Unit
) {
    var source by remember(rule) { mutableStateOf(rule?.source.orEmpty()) }
    var target by remember(rule) { mutableStateOf(rule?.target.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(
                if (rule == null) R.string.reader_name_replace_add
                else R.string.reader_name_replace_edit
            ))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text(stringResource(R.string.reader_name_replace_source)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.reader_name_replace_target)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = source.isNotBlank(),
                onClick = { onSave(source, target) }
            ) { Text(stringResource(R.string.reader_name_replace_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

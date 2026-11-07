package com.breakyuna.esjzone.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

@Composable
internal fun SearchTagDialog(
    currentQuery: String,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit
) {
    var included by rememberSaveable { mutableStateOf("") }
    var excluded by rememberSaveable { mutableStateOf("") }
    val expression = remember(currentQuery, included, excluded) {
        buildSearchTagExpression(currentQuery, included, excluded)
    }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.search_tag_title)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.search_tag_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = included,
                    onValueChange = { included = it },
                    modifier = Modifier.fillMaxWidth().testTag("search-tag-include"),
                    label = { Text(stringResource(R.string.search_tag_include)) },
                    placeholder = { Text(stringResource(R.string.search_tag_include_hint)) },
                    singleLine = true,
                    isError = expression.invalidIncluded,
                    supportingText = if (expression.invalidIncluded) {
                        { Text(stringResource(R.string.search_tag_include_error)) }
                    } else null
                )
                OutlinedTextField(
                    value = excluded,
                    onValueChange = { excluded = it },
                    modifier = Modifier.fillMaxWidth().testTag("search-tag-exclude"),
                    label = { Text(stringResource(R.string.search_tag_exclude)) },
                    placeholder = { Text(stringResource(R.string.search_tag_exclude_hint)) },
                    singleLine = true,
                    isError = expression.invalidExcluded,
                    supportingText = if (expression.invalidExcluded) {
                        { Text(stringResource(R.string.search_tag_exclude_error)) }
                    } else null
                )
                if (expression.canSearch) {
                    Text(
                        stringResource(R.string.search_tag_preview),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.material3.Text(
                        expression.query,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("search-tag-preview")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onSearch(expression.query)
                },
                enabled = expression.canSearch,
                modifier = Modifier.testTag("search-tag-submit")
            ) { Text(stringResource(R.string.search_action)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

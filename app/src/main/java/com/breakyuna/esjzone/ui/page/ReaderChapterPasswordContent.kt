package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource

@Composable
internal fun ReaderChapterPasswordContent(
    chapter: Chapter,
    state: ReaderChapterPasswordState,
    contentColor: Color,
    onSubmit: (String) -> Unit,
    onComments: () -> Unit
) {
    // Never save the password in navigation, a ViewModel, or instance state.
    var password by remember(chapterIdentity(chapter)) { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(stringResource(R.string.reader_password_title), color = contentColor)
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.reader_password_label)) },
            singleLine = true,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = contentColor,
                unfocusedTextColor = contentColor,
                focusedLabelColor = contentColor,
                unfocusedLabelColor = contentColor,
                focusedBorderColor = contentColor,
                unfocusedBorderColor = contentColor.copy(alpha = 0.6f),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                cursorColor = contentColor
            )
        )
        val error = state.message ?: state.failure?.let {
            stringResource(if (it == LoadFailureKind.NETWORK) R.string.load_network_error else R.string.reader_password_failed)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            TextButton(
                enabled = password.isNotBlank() && !state.submitting,
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                onClick = {
                    val submitted = password
                    password = ""
                    focusManager.clearFocus()
                    keyboard?.hide()
                    onSubmit(submitted)
                }
            ) { Text(stringResource(R.string.reader_password_submit)) }
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                onClick = {
                    focusManager.clearFocus()
                    keyboard?.hide()
                    onComments()
                }
            ) { Text(stringResource(R.string.reader_comments_action)) }
        }
        if (state.submitting) CircularProgressIndicator(color = contentColor)
    }
}

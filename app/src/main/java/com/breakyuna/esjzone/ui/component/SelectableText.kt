package com.breakyuna.esjzone.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.GlobalToast

/**
 * Long-press text selection for read-only prose outside the reader (synopsis, comments, forum
 * posts, member-center fields). Wrap only the text bodies: never a clickable or long-clickable row,
 * because selection would compete with its long press. Links inside keep their tap handling.
 */
@Composable
fun SelectableProse(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SelectionContainer(modifier = modifier, content = content)
}

/** Copies plain text; Android 13+ shows its own clipboard confirmation, older versions get a toast. */
fun copyPlainText(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        GlobalToast.makeText(context, R.string.text_copied, GlobalToast.LENGTH_SHORT).show()
    }
}

package com.breakyuna.esjzone.ui.designsystem

import android.content.Context
import androidx.annotation.StringRes
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.ui.reader.ReaderScriptConverter

object GlobalToast {
    val LENGTH_SHORT: Int = android.widget.Toast.LENGTH_SHORT
    val LENGTH_LONG: Int = android.widget.Toast.LENGTH_LONG

    fun makeText(context: Context, text: CharSequence, duration: Int): android.widget.Toast =
        android.widget.Toast.makeText(context, convert(text.toString()), duration)

    fun makeText(context: Context, @StringRes textId: Int, duration: Int): android.widget.Toast =
        android.widget.Toast.makeText(context, convert(context.getString(textId)), duration)

    private fun convert(text: String): String = ReaderScriptConverter.convert(
        text, PresentationAccess.readerSettings.settings.value.script
    )
}

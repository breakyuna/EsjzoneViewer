package com.breakyuna.esjzone.ui.designsystem

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource as platformStringResource
import com.breakyuna.esjzone.ui.reader.ReaderScriptConverter

@Composable
fun globalStringResource(@StringRes id: Int): String {
    val original = platformStringResource(id)
    val script = LocalGlobalScript.current
    return remember(original, script) { ReaderScriptConverter.convert(original, script) }
}

@Composable
fun globalStringResource(@StringRes id: Int, vararg formatArgs: Any): String {
    val original = platformStringResource(id, *formatArgs)
    val script = LocalGlobalScript.current
    return remember(original, script) { ReaderScriptConverter.convert(original, script) }
}

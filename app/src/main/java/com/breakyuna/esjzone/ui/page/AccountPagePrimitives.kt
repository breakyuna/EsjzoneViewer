package com.breakyuna.esjzone.ui.page

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.R

/** Shared account-page navigation affordance; keeps hit target and label consistent. */
@Composable
internal fun BackIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
    }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.page

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.page.Wenku8LoginStatus as Status

/** Native inputs assist the real website form; Chromium owns submission and cookies. */
object Wenku8LoginPage : AppDestination {
    override val key = "Wenku8LoginPage"

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalBaseNavigator.current
        val keyboard = LocalSoftwareKeyboardController.current
        val model = rememberAppViewModel { Wenku8LoginModel(context.applicationContext) }
        // LocalContext is localized with createConfigurationContext and cannot expose the Activity.
        val activity = LocalActivity.current
        if (activity == null) {
            Text(stringResource(R.string.wenku8_login_window_unavailable))
            return
        }
        var attachedBrowser by remember(model, activity) { mutableStateOf<WebView?>(null) }
        var browserUnavailable by remember(model, activity) { mutableStateOf(false) }
        DisposableEffect(model, activity) {
            val result = runCatching { model.attach(activity) }
            attachedBrowser = result.getOrNull()
            browserUnavailable = result.isFailure
            onDispose { model.detach(activity) }
        }
        val browser = attachedBrowser
        if (browser == null) {
            if (!browserUnavailable) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return
            }
            Column(Modifier.padding(AppSpacing.md)) {
                Text(stringResource(R.string.wenku_webview_unavailable))
                TextButton(onClick = { model.prepareToLeave(); navigator?.pop() }) {
                    Text(stringResource(R.string.reader_back))
                }
            }
            return
        }
        // Deliberately not saveable: neither field enters Bundle, SavedState or disk.
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var durationMenu by remember { mutableStateOf(false) }
        var actionsMenu by remember { mutableStateOf(false) }
        val savedDuration by PresentationAccess.settings.wenkuLoginDurationFlow.collectAsState()
        var selectedDuration by remember { mutableStateOf<String?>(null) }
        val duration = selectedDuration ?: savedDuration
        val inputsEnabled = !model.clearing && !model.submitting && model.status != Status.CHECKING
        fun leave() {
            password = ""
            model.prepareToLeave()
            navigator?.pop()
        }
        fun back() {
            if (model.showWeb) model.hideWeb() else leave()
        }
        fun submit() {
            if (!model.canSubmit || username.isBlank() || password.isEmpty()) return
            keyboard?.hide()
            model.submit(username, password, duration)
            password = ""
            passwordVisible = false
        }
        BackHandler(enabled = navigator != null) { back() }
        LaunchedEffect(model.status) {
            if (model.status == Status.SUCCESS) { password = ""; keyboard?.hide() }
        }
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.wenku8_login_title)) }, navigationIcon = {
                IconButton(onClick = ::back) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reader_back))
                }
            }, actions = {
                if (model.showWeb) TextButton(onClick = model::hideWeb) {
                    Text(stringResource(R.string.wenku8_native_login))
                }
                Box {
                    IconButton(onClick = { actionsMenu = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.wenku8_local_actions))
                    }
                    DropdownMenu(expanded = actionsMenu, onDismissRequest = { actionsMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.wenku8_clear_session)) },
                            enabled = !model.clearing, onClick = {
                                actionsMenu = false; password = ""; model.clearSession()
                            })
                    }
                }
            })
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
                // Keep the same laid-out document alive underneath the opaque native surface.
                Column(Modifier.fillMaxSize()) {
                    AndroidView(factory = {
                        (browser.parent as? ViewGroup)?.removeView(browser)
                        browser
                    }, update = { view ->
                        view.importantForAccessibility = if (model.showWeb) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                            else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        view.isFocusableInTouchMode = model.showWeb
                        if (!model.showWeb && view.hasFocus()) view.clearFocus()
                    }, modifier = Modifier.fillMaxWidth().weight(1f))
                    if (model.showWeb) Surface(Modifier.fillMaxWidth()) {
                        Column {
                        model.status.message?.let { message -> Text(stringResource(message),
                            Modifier.padding(horizontal = AppSpacing.md)) }
                        TextButton(onClick = model::retryCheck, enabled = !model.clearing && model.status != Status.CHECKING,
                            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.wenku8_check_session)) }
                        }
                    }
                }
                if (!model.showWeb) Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(AppSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                            if (model.status == Status.SUCCESS) {
                                Text(stringResource(R.string.wenku8_signed_in))
                                Button(onClick = ::leave, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.wenku8_return))
                                }
                            } else {
                                OutlinedTextField(value = username, onValueChange = { username = it },
                                    label = { Text(stringResource(R.string.wenku8_username)) },
                                    singleLine = true, enabled = inputsEnabled, modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next))
                                OutlinedTextField(value = password, onValueChange = { password = it },
                                    label = { Text(stringResource(R.string.password)) }, singleLine = true,
                                    enabled = inputsEnabled, modifier = Modifier.fillMaxWidth(),
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }, enabled = inputsEnabled) {
                                            Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                stringResource(if (passwordVisible) R.string.password_hide else R.string.password_show))
                                        }
                                    }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(onDone = { submit() }))
                                Box {
                                    OutlinedButton(onClick = { durationMenu = true }, enabled = inputsEnabled,
                                        modifier = Modifier.fillMaxWidth()) {
                                        Text(stringResource(R.string.wenku8_duration_value, stringResource(durationLabel(duration))))
                                    }
                                    DropdownMenu(expanded = durationMenu, onDismissRequest = { durationMenu = false }) {
                                        SettingsDefaults.WENKU_LOGIN_DURATIONS.forEach { value ->
                                            DropdownMenuItem(text = { Text(stringResource(durationLabel(value))) }, onClick = {
                                                durationMenu = false
                                                selectedDuration = value
                                                PresentationAccess.settings.setWenkuLoginDuration(value)
                                            })
                                        }
                                    }
                                }
                                Button(onClick = ::submit, enabled = model.canSubmit && username.isNotBlank() && password.isNotEmpty(),
                                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.button_login)) }
                                if (model.busy) CircularProgressIndicator()
                                model.status.message?.let { message -> Text(stringResource(message),
                                    color = if (model.status in setOf(Status.NETWORK, Status.UNKNOWN, Status.INCOMPATIBLE, Status.REJECTED, Status.INVALID))
                                        MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) }
                                TextButton(onClick = { keyboard?.hide(); model.openWeb() }, enabled = !model.clearing) {
                                    Text(stringResource(R.string.wenku8_open_web_login))
                                }
                                if (!model.busy && !model.clearing) TextButton(onClick = model::retryCheck) {
                                    Text(stringResource(R.string.wenku8_check_session))
                                }
                                if (!model.busy && !model.clearing && !model.formReady) TextButton(onClick = model::loadLoginPage) {
                                    Text(stringResource(R.string.wenku8_reload_login))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun durationLabel(value: String): Int = when (value) {
        "0" -> R.string.wenku8_duration_process
        "86400" -> R.string.wenku8_duration_day
        "2592000" -> R.string.wenku8_duration_month
        else -> R.string.wenku8_duration_year
    }
}

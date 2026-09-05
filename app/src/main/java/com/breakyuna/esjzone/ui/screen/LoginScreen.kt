package com.breakyuna.esjzone.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.features.login
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.component.QuietGroup
import com.breakyuna.esjzone.ui.component.QuietSectionHeader
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object LoginScreen : Screen {
    private fun readResolve(): Any = LoginScreen

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val currentDomain by GlobalSettings.domain
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var emailError by remember { mutableStateOf(false) }
        var passwordError by remember { mutableStateOf(false) }
        var loggingIn by remember { mutableStateOf(false) }
        var loginFailed by remember { mutableStateOf(false) }

        fun submit() {
            emailError = email.trim().isBlank()
            passwordError = password.isBlank()
            if (emailError || passwordError || loggingIn) return
            loggingIn = true
            loginFailed = false
            val selectedDomain = currentDomain
            scope.launch {
                try {
                    val authorization = withContext(Dispatchers.IO) {
                        val result = EsjzoneClient.login(email.trim(), password)
                        if (result != null) {
                            MainActivity.database.runInTransaction {
                                val dao = MainActivity.database.cacheDao()
                                dao.put("domain", selectedDomain)
                                dao.put("session_domain", selectedDomain)
                                dao.put("ews_key", result.ewsKey)
                                dao.put("ews_token", result.ewsToken)
                            }
                        }
                        result
                    }
                    if (authorization != null) {
                        BookshelfRepository.scheduleSync(authorization)
                        navigator.replace(MainScreen(authorization))
                    } else {
                        loginFailed = true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("LoginScreen", "Login flow failed", e)
                    loginFailed = true
                } finally {
                    loggingIn = false
                }
            }
        }

        Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = QuietEditorial.pagePadding, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.esjzone_icon_round),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = stringResource(R.string.app_name),
                        style = QuietEditorial.display,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                QuietGroup {
                    QuietSectionHeader(
                        title = stringResource(R.string.login_site_title),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        GlobalSettings.DOMAINS.forEach { domain ->
                            FilterChip(
                                selected = currentDomain == domain,
                                onClick = {
                                    GlobalSettings.setDomain(domain)
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                            MainActivity.database.cacheDao().put("domain", domain)
                                        }.onFailure {
                                            AppLogger.e("LoginScreen", "Failed to persist selected domain", it)
                                        }
                                    }
                                },
                                enabled = !loggingIn,
                                label = { Text(domain) },
                                leadingIcon = if (currentDomain == domain) {
                                    {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                QuietGroup {
                    QuietSectionHeader(
                        title = stringResource(R.string.login_account_title),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; emailError = false; loginFailed = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        enabled = !loggingIn,
                        singleLine = true,
                        isError = emailError,
                        label = { Text(stringResource(R.string.email)) },
                        supportingText = if (emailError) {
                            { Text(stringResource(R.string.field_required)) }
                        } else null,
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        )
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; passwordError = false; loginFailed = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        enabled = !loggingIn,
                        singleLine = true,
                        isError = passwordError,
                        label = { Text(stringResource(R.string.password)) },
                        supportingText = if (passwordError) {
                            { Text(stringResource(R.string.field_required)) }
                        } else null,
                        leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !loggingIn
                            ) {
                                Icon(
                                    if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.password_hide else R.string.password_show
                                    )
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { submit() })
                    )
                    if (loginFailed) {
                        Text(
                            text = stringResource(R.string.login_fail),
                            style = QuietEditorial.body,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    Button(
                        onClick = ::submit,
                        enabled = !loggingIn,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = QuietEditorial.controlShape
                    ) {
                        if (loggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(stringResource(R.string.button_login))
                        }
                    }
                }
            }
        }
    }
}

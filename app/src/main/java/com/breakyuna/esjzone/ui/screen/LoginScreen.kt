package com.breakyuna.esjzone.ui.screen
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.cancellablePageRequest

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.LocalAppNavigator
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.network.features.CommunitySyncManager
import com.breakyuna.esjzone.network.features.LoginAttemptResult
import com.breakyuna.esjzone.network.features.loginWithOutcome
import com.breakyuna.esjzone.network.mirrorLoginOrder
import kotlinx.coroutines.flow.first
import com.breakyuna.esjzone.ui.component.AppGroup
import com.breakyuna.esjzone.ui.component.AppSectionHeader
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

import com.breakyuna.esjzone.ui.navigation.AppExitBackHandler

object LoginScreen : AppDestination {
    private fun readResolve(): Any = LoginScreen

    @Composable
    override fun Content() {
        AppExitBackHandler()
        val navigator = LocalAppNavigator.current ?: error("Navigation 3 root is not provided")
        val scope = rememberCoroutineScope()
        val currentDomain by PresentationAccess.settings.domain
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var emailError by remember { mutableStateOf(false) }
        var passwordError by remember { mutableStateOf(false) }
        var loggingIn by remember { mutableStateOf(false) }
        var loginFailed by remember { mutableStateOf(false) }
        var loginNetworkFailed by remember { mutableStateOf(false) }
        var accountMismatch by remember { mutableStateOf(false) }

        fun submit() {
            emailError = email.trim().isBlank()
            passwordError = password.isBlank()
            accountMismatch = PresentationAccess.client.matchesActiveAccountEmail(email) == false
            if (emailError || passwordError || accountMismatch || loggingIn) return
            val sameAccountBeforeLogin = PresentationAccess.client.matchesActiveAccountEmail(email) == true
            loggingIn = true
            loginFailed = false
            loginNetworkFailed = false
            val selectedDomain = currentDomain
            scope.launch {
                try {
                    val authorization = withContext(Dispatchers.IO) {
                        val sessions = mutableListOf<com.breakyuna.esjzone.network.Authorization>()
                        var networkFailure: IOException? = null
                        var receivedServerResult = false
                        mirrorLoginOrder(selectedDomain, PresentationAccess.settings.DOMAINS)
                            .forEach { domain ->
                                // Once a different account has logged in, an old session on
                                // the next mirror must not remain attached to that account.
                                if (sessions.isNotEmpty() && !sameAccountBeforeLogin) {
                                    PresentationAccess.client.clearSession(domain)
                                }
                                var siteSession: com.breakyuna.esjzone.network.Authorization? = null
                                for (attempt in 0 until 2) {
                                    try {
                                        when (val outcome = cancellablePageRequest {
                                            PresentationAccess.client.loginWithOutcome(email.trim(), password, domain)
                                        }) {
                                            is LoginAttemptResult.Success -> siteSession = outcome.authorization
                                            is LoginAttemptResult.NonSuccessStatus,
                                            LoginAttemptResult.InvalidResponse -> receivedServerResult = true
                                            is LoginAttemptResult.IoFailure -> throw outcome.error
                                        }
                                        // A complete server result will not improve through an I/O retry.
                                        break
                                    } catch (error: CancellationException) {
                                        throw error
                                    } catch (error: IOException) {
                                        networkFailure = error
                                        if (attempt == 0) delay(600L)
                                        else AppLogger.w("LoginScreen", "Site login unavailable: host=$domain", error)
                                    }
                                }
                                AppLogger.i("LoginScreen", "Site login result: host=$domain, success=${siteSession != null}")
                                siteSession?.let(sessions::add)
                            }
                        val result = sessions.firstOrNull()
                        if (result != null) {
                            // The preferred site may be unavailable on first install.
                            // Do not keep a different account's old session there.
                            if (result.domain != selectedDomain && !sameAccountBeforeLogin) {
                                PresentationAccess.client.clearSession(selectedDomain)
                            }
                            sessions.forEach { session ->
                                try {
                                    BookshelfRepository.migrateLegacyScopeIfNeeded(session)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    AppLogger.w("LoginScreen", "Legacy bookshelf migration deferred", error)
                                }
                            }
                        }
                        // A reachable mirror returned a definite non-network result.
                        // An I/O error from another mirror must not mask that response.
                        result ?: if (!receivedServerResult) networkFailure?.let { throw it } else null
                    }
                    if (authorization != null) {
                        if (authorization.domain != selectedDomain) {
                            PresentationAccess.settings.setDomain(authorization.domain)
                            PresentationAccess.settings.domainFlow.first { it == authorization.domain }
                        }
                        password = ""
                        BookshelfRepository.scheduleSync(authorization, delayMillis = 2000L)
                        CommunitySyncManager.schedulePreSync(authorization, delayMillis = 3500L)
                        navigator.replace(MainScreen(authorization))
                    } else {
                        loginFailed = true
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e("LoginScreen", "Login flow failed", e)
                    loginNetworkFailed = e.loadFailureKind() == LoadFailureKind.NETWORK
                    loginFailed = !loginNetworkFailed
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
                    .padding(horizontal = 24.dp, vertical = 32.dp),
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
                        style = AppTypography.displayMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                AppGroup {
                    AppSectionHeader(
                        title = stringResource(R.string.login_account_title),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; emailError = false; accountMismatch = false; loginFailed = false; loginNetworkFailed = false },
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
                        onValueChange = { password = it; passwordError = false; loginFailed = false; loginNetworkFailed = false },
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
                    if (accountMismatch) {
                        Text(
                            text = stringResource(R.string.login_same_account_required),
                            style = AppTypography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                    if (loginFailed || loginNetworkFailed) {
                        Text(
                            text = stringResource(if (loginNetworkFailed) R.string.login_network_fail else R.string.login_fail),
                            style = AppTypography.bodyMedium,
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
                        shape = AppShapes.standard
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

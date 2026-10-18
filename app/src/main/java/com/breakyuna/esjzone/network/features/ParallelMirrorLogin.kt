package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.database.BookshelfRepository
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.mirrorLoginOrder
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import okhttp3.Cookie
import okhttp3.HttpUrl
import java.io.IOException

private val mirrorLoginScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Both requests share one commit generation, while logout or another login invalidates the batch. */
internal class LoginSessionBatch(private val client: EsjzoneClient) {
    private var epoch = client.sessionEpoch()
    private var committed = false

    @Synchronized
    fun commit(url: HttpUrl, email: String, cookies: List<Cookie>, authorization: Authorization): Boolean {
        epoch = client.commitLoginSession(url, email, cookies, authorization, epoch, committed)
            ?: return false
        committed = true
        return true
    }
}

/** Deliver the first success immediately, then finish the remaining mirror without a second callback. */
internal suspend fun loginMirrorsInParallel(
    domains: List<String>,
    login: suspend (String) -> LoginAttemptResult,
    onFirstSuccess: (Authorization) -> Unit,
    onSuccess: suspend (Authorization) -> Unit = {}
): Authorization? = coroutineScope {
    val pending = domains.map { domain -> async { login(domain) } }.toMutableList()
    var firstSuccess: Authorization? = null
    var networkFailure: IOException? = null
    var receivedServerResult = false
    while (pending.isNotEmpty()) {
        val (request, outcome) = select {
            pending.forEach { request -> request.onAwait { request to it } }
        }
        pending.remove(request)
        when (outcome) {
            is LoginAttemptResult.Success -> {
                if (firstSuccess == null) {
                    firstSuccess = outcome.authorization
                    onFirstSuccess(outcome.authorization)
                }
                launch { onSuccess(outcome.authorization) }
            }
            is LoginAttemptResult.IoFailure -> networkFailure = outcome.error
            is LoginAttemptResult.NonSuccessStatus,
            LoginAttemptResult.InvalidResponse -> receivedServerResult = true
        }
    }
    if (firstSuccess == null && !receivedServerResult) networkFailure?.let { throw it }
    firstSuccess
}

/** Only the result wait belongs to the screen; after success the other mirror can finish in the process. */
internal suspend fun EsjzoneClient.loginMirrors(
    email: String,
    password: String,
    selectedDomain: String
): Authorization? {
    val firstResult = CompletableDeferred<Authorization?>()
    val sessionBatch = LoginSessionBatch(this)
    val worker = mirrorLoginScope.launch {
        try {
            val result = loginMirrorsInParallel(
                domains = mirrorLoginOrder(selectedDomain, SettingsDefaults.DOMAINS),
                login = { domain ->
                    var outcome: LoginAttemptResult = LoginAttemptResult.InvalidResponse
                    for (attempt in 0 until 2) {
                        outcome = cancellablePageRequest {
                            loginWithOutcome(email, password, domain, sessionBatch)
                        }
                        if (outcome !is LoginAttemptResult.IoFailure) break
                        if (attempt == 0) delay(600L)
                    }
                    AppLogger.i("LoginScreen", "Site login result: host=$domain, success=${outcome is LoginAttemptResult.Success}")
                    outcome
                },
                onFirstSuccess = { firstResult.complete(it) },
                onSuccess = { authorization ->
                    try {
                        if (accountScope(authorization) == activeAccountScopeOrNull()) {
                            BookshelfRepository.migrateLegacyScopeIfNeeded(authorization)
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        AppLogger.w("LoginScreen", "Legacy bookshelf migration deferred", error)
                    }
                }
            )
            firstResult.complete(result)
        } catch (error: CancellationException) {
            firstResult.cancel(error)
            throw error
        } catch (error: Exception) {
            if (!firstResult.completeExceptionally(error)) {
                AppLogger.w("LoginScreen", "Background mirror login failed", error)
            }
        }
    }
    var succeeded = false
    try {
        val result = firstResult.await()
        succeeded = result != null
        return result
    } finally {
        if (!succeeded) worker.cancel()
    }
}

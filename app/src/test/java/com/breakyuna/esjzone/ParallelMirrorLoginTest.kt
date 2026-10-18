package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.features.LoginAttemptResult
import com.breakyuna.esjzone.network.features.loginMirrorsInParallel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ParallelMirrorLoginTest {
    private val domains = listOf("www.esjzone.cc", "www.esjzone.one")

    @Test
    fun eitherMirrorCanOpenHomeBeforeTheOtherFinishesAndOnlyOnce() = runBlocking {
        withTimeout(5000L) {
            for (winner in domains) {
                val started = domains.associateWith { CompletableDeferred<Unit>() }
                val responses = domains.associateWith { CompletableDeferred<LoginAttemptResult>() }
                val first = CompletableDeferred<Authorization>()
                val callbacks = mutableListOf<Authorization>()
                val completed = mutableListOf<Authorization>()
                val worker = async {
                    loginMirrorsInParallel(
                        domains,
                        login = { domain ->
                            started.getValue(domain).complete(Unit)
                            responses.getValue(domain).await()
                        },
                        onFirstSuccess = { callbacks += it; first.complete(it) },
                        onSuccess = { completed += it }
                    )
                }
                // Both requests must start while neither is allowed to finish.
                started.values.forEach { it.await() }
                responses.getValue(winner).complete(success(winner))
                assertEquals(winner, first.await().domain)
                assertFalse(worker.isCompleted)

                val slower = domains.single { it != winner }
                responses.getValue(slower).complete(success(slower))
                assertEquals(winner, worker.await()?.domain)
                assertEquals(listOf(winner), callbacks.map { it.domain })
                assertEquals(domains.toSet(), completed.map { it.domain }.toSet())
            }
        }
    }

    @Test
    fun oneFailureStillWaitsForTheOtherMirrorsSuccess() = runBlocking {
        withTimeout(5000L) {
            val failed = CompletableDeferred<Unit>()
            val releaseSuccess = CompletableDeferred<Unit>()
            val first = CompletableDeferred<Authorization>()
            val worker = async {
                loginMirrorsInParallel(domains, login = { domain ->
                    if (domain == domains.first()) {
                        failed.complete(Unit)
                        LoginAttemptResult.NonSuccessStatus(403)
                    } else {
                        releaseSuccess.await()
                        success(domain)
                    }
                }, onFirstSuccess = { first.complete(it) })
            }
            failed.await()
            assertFalse(first.isCompleted)
            releaseSuccess.complete(Unit)
            assertEquals(domains.last(), first.await().domain)
            assertEquals(domains.last(), worker.await()?.domain)
        }
    }

    @Test
    fun definiteServerFailureIsNotMaskedByAnotherMirrorsNetworkFailure() = runBlocking {
        val result = loginMirrorsInParallel(domains, login = { domain ->
            if (domain == domains.first()) LoginAttemptResult.IoFailure(IOException("fixture failure"))
            else LoginAttemptResult.InvalidResponse
        }, onFirstSuccess = { fail("Neither mirror succeeded") })
        assertNull(result)
    }

    @Test
    fun allNetworkFailuresRemainNetworkFailures() = runBlocking {
        val error = IOException("fixture failure")
        try {
            loginMirrorsInParallel(domains, login = {
                LoginAttemptResult.IoFailure(error)
            }, onFirstSuccess = { fail("Neither mirror succeeded") })
            fail("Expected a network failure")
        } catch (actual: IOException) {
            assertEquals(error.message, actual.message)
        }
    }

    private fun success(domain: String) = LoginAttemptResult.Success(
        Authorization("fixture-key-$domain", "fixture-token-$domain", domain)
    )
}

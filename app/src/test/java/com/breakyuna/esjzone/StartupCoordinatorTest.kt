package com.breakyuna.esjzone

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupCoordinatorTest {
    @Test
    fun concurrentInitializationRunsOnlyOnce() = runBlocking {
        val coordinator = coordinator()
        assertEquals(StartupState.Starting, coordinator.state.value)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0

        val first = async {
            coordinator.initializeOnce {
                calls += 1
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val second = async {
            coordinator.initializeOnce { calls += 1 }
        }
        yield()
        assertEquals(1, calls)

        release.complete(Unit)
        first.await()
        second.await()
        coordinator.initializeOnce { calls += 1 }

        assertEquals(1, calls)
        assertEquals(StartupState.Ready, coordinator.state.value)
    }

    @Test
    fun initializationFailureIsMappedAndReported() = runBlocking {
        val failure = IllegalStateException("private diagnostic")
        var reported: Exception? = null
        val coordinator = StartupCoordinator(
            mapFailure = { "safe message" },
            onFailure = { reported = it },
        )

        coordinator.initializeOnce { throw failure }

        assertEquals(StartupState.Failed("safe message"), coordinator.state.value)
        assertEquals(failure, reported)
    }

    @Test
    fun retryReturnsToStartingAndCanSucceed() = runBlocking {
        val coordinator = coordinator()
        coordinator.initializeOnce { throw IllegalStateException("first attempt") }
        assertTrue(coordinator.state.value is StartupState.Failed)

        coordinator.retry()
        assertEquals(StartupState.Starting, coordinator.state.value)
        coordinator.initializeOnce { }

        assertEquals(StartupState.Ready, coordinator.state.value)
    }

    private fun coordinator() = StartupCoordinator(
        mapFailure = { it.message ?: it.javaClass.simpleName },
        onFailure = {},
    )
}

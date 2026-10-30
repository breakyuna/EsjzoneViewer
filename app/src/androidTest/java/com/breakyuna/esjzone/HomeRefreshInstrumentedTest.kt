package com.breakyuna.esjzone

import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel
import com.breakyuna.esjzone.ui.tab.HomeTabModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeRefreshInstrumentedTest {
    @Test fun repeatedRefreshWaitsForCancelledLoadAndPublishesOnlyNewestResult() = runBlocking {
        val oldStarted = CompletableDeferred<Unit>()
        val oldCancelled = CompletableDeferred<Unit>()
        val releaseOld = CompletableDeferred<Unit>()
        val fresh = HomeData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            weeklyPopular = listOf(WeeklyPopularNovel(name = "Fresh")))
        var calls = 0
        val model = withContext(Dispatchers.Main) {
            HomeTabModel(Authorization("", "", "example.invalid")) { force, progress ->
                calls++
                if (calls == 1) {
                    oldStarted.complete(Unit)
                    try { awaitCancellation() }
                    finally {
                        withContext(NonCancellable) {
                            oldCancelled.complete(Unit)
                            releaseOld.await()
                        }
                    }
                } else {
                    assertTrue(force)
                    progress(fresh)
                    fresh
                }
            }
        }
        try {
            withTimeout(5_000) {
                withContext(Dispatchers.Main) { model.getHomeData() }
                oldStarted.await()
                withContext(Dispatchers.Main) { model.reload() }
                oldCancelled.await()
                withContext(Dispatchers.Main) {
                    model.reload() // Replace a refresh that is still waiting for the first load.
                    assertEquals(1, calls)
                }
                releaseOld.complete(Unit)
                val state = model.state.first { it is HomeTabModel.State.Result && !it.isSyncing }
                    as HomeTabModel.State.Result
                assertEquals(fresh, state.homeData)
                assertFalse(state.isRefreshing)
                withContext(Dispatchers.Main) { assertEquals(2, calls) }
            }
        } finally {
            releaseOld.complete(Unit)
            withContext(Dispatchers.Main) { model.viewModelScope.cancel() }
        }
    }
    @Test fun failedRefreshRetainsContentAndExposesFailure() = runBlocking {
        val saved = HomeData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(),
            weeklyPopular = listOf(WeeklyPopularNovel(name = "Saved")))
        var calls = 0
        val model = withContext(Dispatchers.Main) {
            HomeTabModel(Authorization("", "", "example.invalid")) { _, _ ->
                if (++calls == 1) saved else throw java.net.SocketTimeoutException()
            }
        }
        try {
            withTimeout(5_000) {
                withContext(Dispatchers.Main) { model.getHomeData() }
                model.state.first { it is HomeTabModel.State.Result && !it.isSyncing }
                withContext(Dispatchers.Main) { model.reload() }
                val failed = model.state.first { it is HomeTabModel.State.Result && it.refreshFailure != null }
                    as HomeTabModel.State.Result
                assertEquals(saved, failed.homeData)
                assertFalse(failed.isRefreshing)
                assertEquals(com.breakyuna.esjzone.network.LoadFailureKind.NETWORK, failed.refreshFailure)
            }
        } finally { withContext(Dispatchers.Main) { model.viewModelScope.cancel() } }
    }

}

package com.breakyuna.esjzone

import com.breakyuna.esjzone.app.CoverLoadingState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverLoadingPolicyTest {
    @Test
    fun `first launch follows the visible tab including switching back`() {
        val home = CoverLoadingState(firstLaunch = true, visiblePage = "HOME")
        assertTrue(home.preloadHome)
        assertTrue(home.allowNetwork("HOME"))
        assertFalse(home.allowNetwork("BOOKSHELF"))

        val shelf = home.copy(visiblePage = "BOOKSHELF", homePreloads = 3)
        assertFalse(shelf.preloadHome)
        assertFalse(shelf.allowNetwork("HOME"))
        assertTrue(shelf.allowNetwork("BOOKSHELF"))
        assertTrue(shelf.copy(visiblePage = "HOME").preloadHome)
    }

    @Test
    fun `later launches prioritize home even while shelf is visible`() {
        val loading = CoverLoadingState(firstLaunch = false, visiblePage = "BOOKSHELF")
        assertTrue(loading.preloadHome)
        assertTrue(loading.allowNetwork("HOME"))
        assertFalse(loading.allowNetwork("BOOKSHELF"))

        val preloading = loading.copy(homeDataLoading = false, homePreloads = 1)
        assertFalse(preloading.allowNetwork("BOOKSHELF"))
        assertTrue(preloading.copy(homePreloads = 0).allowNetwork("BOOKSHELF"))
    }

    @Test
    fun `first launch on another configured tab does not preload hidden home`() {
        val state = CoverLoadingState(firstLaunch = true, visiblePage = "HISTORY")
        assertFalse(state.preloadHome)
        assertFalse(state.allowNetwork("BOOKSHELF"))
        assertTrue(state.allowNetwork("HISTORY"))
    }
}

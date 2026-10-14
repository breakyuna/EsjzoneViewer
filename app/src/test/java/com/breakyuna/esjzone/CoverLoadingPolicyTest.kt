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
    fun `visible shelf is never blocked by background home preloads`() {
        val loading = CoverLoadingState(firstLaunch = false, visiblePage = "BOOKSHELF")
        assertTrue(loading.preloadHome)
        assertFalse(loading.allowNetwork("HOME"))
        assertTrue(loading.allowNetwork("BOOKSHELF"))
        assertFalse(loading.allowBackgroundNetwork("BOOKSHELF"))

        val preloading = loading.copy(homeDataLoading = false, homePreloads = 1)
        assertTrue(preloading.allowNetwork("BOOKSHELF"))
        assertFalse(preloading.allowBackgroundNetwork("BOOKSHELF"))
        assertTrue(preloading.copy(homePreloads = 0).allowBackgroundNetwork("BOOKSHELF"))
    }

    @Test
    fun `first launch on another configured tab does not preload hidden home`() {
        val state = CoverLoadingState(firstLaunch = true, visiblePage = "HISTORY")
        assertFalse(state.preloadHome)
        assertFalse(state.allowNetwork("BOOKSHELF"))
        assertTrue(state.allowNetwork("HISTORY"))
    }
}

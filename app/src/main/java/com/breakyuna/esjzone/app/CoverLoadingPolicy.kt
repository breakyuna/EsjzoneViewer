package com.breakyuna.esjzone.app

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal data class CoverLoadingState(
    val firstLaunch: Boolean = true,
    val visiblePage: String = "HOME",
    val homeDataLoading: Boolean = true,
    val homePreloads: Int = 0
) {
    val preloadHome: Boolean get() = !firstLaunch || visiblePage == "HOME"
    fun allowNetwork(page: String): Boolean = when {
        firstLaunch -> page == visiblePage
        page == "HOME" -> true
        else -> !homeDataLoading && homePreloads == 0
    }
}

/** The install marker is persistent; first-launch mode lasts for this process. */
internal object CoverLoadingPolicy {
    private val mutableState = MutableStateFlow(CoverLoadingState())
    val state = mutableState.asStateFlow()
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        val marker = File(context.noBackupFilesDir, "cover_loading_started")
        // Existing installations already have the local database, including upgrades.
        val firstLaunch = !marker.exists() && !context.getDatabasePath("general").exists()
        marker.parentFile?.mkdirs()
        check(marker.exists() || marker.createNewFile())
        mutableState.update { it.copy(firstLaunch = firstLaunch) }
        initialized = true
    }

    fun showPage(page: String) = mutableState.update { it.copy(visiblePage = page) }
    fun homeDataLoading(loading: Boolean) = mutableState.update { it.copy(homeDataLoading = loading) }
    fun homePreloadStarted() = mutableState.update { it.copy(homePreloads = it.homePreloads + 1) }
    fun homePreloadFinished() = mutableState.update { it.copy(homePreloads = it.homePreloads - 1) }
}

internal val LocalCoverPage = staticCompositionLocalOf { "" }

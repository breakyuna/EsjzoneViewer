package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.wenku8.Wenku8Browse
import com.breakyuna.esjzone.network.wenku8.browseWenku8
import com.breakyuna.esjzone.network.wenku8.wenku8BrowseUrl
import com.breakyuna.esjzone.network.wenku8.Wenku8LoginRequiredException
import com.breakyuna.esjzone.network.wenku8.Wenku8ParseException
import com.breakyuna.esjzone.network.wenku8.Wenku8RestrictedException
import com.breakyuna.esjzone.network.external.CloudflareChallengeRequiredException
import com.breakyuna.esjzone.network.wenku8.Wenku8SearchRateLimitException
import com.breakyuna.esjzone.network.wenku8.Wenku8SearchResult
import com.breakyuna.esjzone.network.wenku8.Wenku8SearchType
import com.breakyuna.esjzone.network.wenku8.searchWenku8
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.discovery.DiscoveryErrorState
import com.breakyuna.esjzone.ui.discovery.DiscoveryEmptyState
import com.breakyuna.esjzone.network.wenku8.wenku8SearchUrl
import com.breakyuna.esjzone.network.external.CloudflareWebViewUnavailableException
import com.breakyuna.esjzone.network.external.WenkuCookieStoreUnavailableException
import androidx.compose.runtime.remember
import com.breakyuna.esjzone.ui.discovery.DiscoveryNovelCard
import com.breakyuna.esjzone.ui.discovery.DiscoverySearchTopBar
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.encodeRouteTokenPart
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class Wenku8Page(
    private val keyword: String = "",
    private val type: Wenku8SearchType = Wenku8SearchType.TITLE
) : AppDestination {
    override val key = "Wenku8Page:${type.name}:${encodeRouteTokenPart(keyword)}"

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val model = rememberAppViewModel { Wenku8PageModel(keyword, type) }
        val state by model.state.collectAsState()
        val adult by PresentationAccess.settings.adult
        var query by rememberSaveable { mutableStateOf(keyword) }
        var searchType by rememberSaveable { mutableStateOf(type) }
        var showLocalActions by remember { mutableStateOf(false) }
        var category by rememberSaveable { mutableStateOf(Wenku8Browse.HOME) }
        var savedPage by rememberSaveable { mutableStateOf(1) }
        var showVerification by remember { mutableStateOf(false) }
        var pendingSessionReturn by rememberSaveable { mutableStateOf(false) }
        fun openSession() {
            navigator?.let {
                pendingSessionReturn = true
                it.pushIfNotCurrent(Wenku8LoginPage)
            }
        }
        val currentPageKey = navigator?.lastItem?.key
        LaunchedEffect(currentPageKey, state) {
            if (currentPageKey == key && pendingSessionReturn && state != Wenku8PageModel.State.Loading) {
                pendingSessionReturn = false
                if (state == Wenku8PageModel.State.Idle) model.load(savedPage, forceRefresh = true, category = category)
                else model.load(model.page, forceRefresh = true)
            }
        }
        if (showVerification) {
            WenkuVerificationDialog(url = if (keyword.isBlank()) wenku8BrowseUrl(category, model.page)
                else wenku8SearchUrl(keyword, type, model.page),
                onVerified = { showVerification = false; model.load(model.page, forceRefresh = true) },
                onUnavailable = { showVerification = false },
                onDismiss = { showVerification = false })
        }
        fun submit() {
            val submitted = query.trim()
            if (submitted.isBlank()) return
            if (submitted == keyword && searchType == type) model.load(1, forceRefresh = true)
            else navigator?.replace(Wenku8Page(submitted, searchType))
        }
        LaunchedEffect(model) { if (state == Wenku8PageModel.State.Idle) model.load(savedPage, category = category) }
        LaunchedEffect(state) { (state as? Wenku8PageModel.State.Result)?.let { savedPage = it.result.page } }
        Scaffold(topBar = {
            Column {
                DiscoverySearchTopBar(query, { query = it }, ::submit, {
                    query = ""
                    if (keyword.isNotBlank()) navigator?.replace(Wenku8Page())
                }, { navigator?.pop() })
                if (keyword.isBlank()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Wenku8Browse.entries.forEach { entry ->
                            FilterChip(selected = category == entry,
                                enabled = state != Wenku8PageModel.State.Loading,
                                onClick = { category = entry; savedPage = 1; model.load(1, category = entry) },
                                label = { Text(stringResource(entry.labelResource())) })
                        }
                    }
                } else {
                    TextButton(onClick = { navigator?.replace(Wenku8Page()) }) {
                        Text(stringResource(R.string.wenku8_home))
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    FilterChip(selected = searchType == Wenku8SearchType.TITLE,
                        onClick = { searchType = Wenku8SearchType.TITLE }, label = { Text(stringResource(R.string.wenku8_search_title)) })
                    FilterChip(selected = searchType == Wenku8SearchType.AUTHOR,
                        onClick = { searchType = Wenku8SearchType.AUTHOR }, label = { Text(stringResource(R.string.author)) })
                    TextButton(onClick = { model.load(model.page, forceRefresh = true) },
                        enabled = state != Wenku8PageModel.State.Loading) { Text(stringResource(R.string.wenku8_refresh)) }
                    TextButton(onClick = { navigator?.pushIfNotCurrent(Wenku8BookshelfPage) }) { Text(stringResource(R.string.bookshelf)) }
                    TextButton(onClick = ::openSession) { Text(stringResource(R.string.wenku8_session)) }
                    Box {
                        IconButton(onClick = { showLocalActions = true }) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.wenku8_local_actions))
                        }
                        DropdownMenu(expanded = showLocalActions, onDismissRequest = { showLocalActions = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.history)) }, onClick = {
                                showLocalActions = false; navigator?.pushIfNotCurrent(HistoryPage)
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.downloads)) }, onClick = {
                                showLocalActions = false; navigator?.pushIfNotCurrent(DownloadPage)
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.bookmarks)) }, onClick = {
                                showLocalActions = false; navigator?.pushIfNotCurrent(BookmarksPage)
                            })
                        }
                    }
                }
            }
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                when (val snapshot = state) {
                    Wenku8PageModel.State.Idle -> Unit
                    Wenku8PageModel.State.Loading -> item { CircularProgressIndicator(Modifier.padding(AppSpacing.lg)) }
                    is Wenku8PageModel.State.Failed -> item {
                        DiscoveryErrorState(stringResource(snapshot.message), onRetry = { model.load(model.page, forceRefresh = true) })
                        if (snapshot.message == R.string.wenku8_login_required ||
                            snapshot.message == R.string.wenku8_verification_needed) {
                            TextButton(onClick = ::openSession) {
                                Text(stringResource(R.string.button_login))
                            }
                        }
                        if (snapshot.message == R.string.wenku8_verification_needed) {
                            TextButton(onClick = { showVerification = true }) { Text(stringResource(R.string.wenku_verification_open)) }
                        }
                    }
                    is Wenku8PageModel.State.Result -> {
                        val visibleBooks = snapshot.result.novels.filter { adult || !it.isAdult }
                        snapshot.result.sections.forEach { section ->
                            val sectionBooks = section.novels.filter { adult || !it.isAdult }
                            if (sectionBooks.isNotEmpty()) {
                                item(key = "section:${section.title}") {
                                    Text(section.title, Modifier.padding(AppSpacing.md))
                                }
                                items(sectionBooks, key = { "${section.title}:${it.url}" }) { book ->
                                    DiscoveryNovelCard(book, Modifier.padding(horizontal = AppSpacing.md)) {
                                        navigator?.pushIfNotCurrent(NovelPage(book))
                                    }
                                }
                            }
                        }
                        if (visibleBooks.isEmpty() && snapshot.result.sections.isEmpty()) item {
                            DiscoveryEmptyState(stringResource(R.string.search_no_results), stringResource(R.string.search_no_results_message))
                        }
                        items(visibleBooks, key = { it.url }) { book ->
                            DiscoveryNovelCard(book, Modifier.padding(horizontal = AppSpacing.md)) {
                                navigator?.pushIfNotCurrent(NovelPage(book))
                            }
                        }
                        if (snapshot.result.sections.isEmpty()) item {
                            Row(Modifier.padding(AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                                TextButton(onClick = { model.load(snapshot.result.page - 1) }, enabled = snapshot.result.page > 1) {
                                    Text(stringResource(R.string.wenku8_previous_page))
                                }
                                Text("${snapshot.result.page}/${snapshot.result.totalPages}")
                                TextButton(onClick = { model.load(snapshot.result.page + 1) }, enabled = snapshot.result.page < snapshot.result.totalPages) {
                                    Text(stringResource(R.string.wenku8_next_page))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal class Wenku8PageModel(private val keyword: String, private val type: Wenku8SearchType) :
    AppStateViewModel<Wenku8PageModel.State>(State.Idle) {
    var category = Wenku8Browse.HOME
        private set
    var page = 1
        private set
    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Result(val result: Wenku8SearchResult) : State
        data class Failed(val message: Int) : State
    }
    fun load(targetPage: Int, forceRefresh: Boolean = false, category: Wenku8Browse = this.category) {
        if (state.value == State.Loading || targetPage < 1) return
        this.category = category
        page = targetPage
        mutableState.value = State.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mutableState.value = State.Result(if (keyword.isBlank())
                    PresentationAccess.client.browseWenku8(category, targetPage, forceRefresh)
                else PresentationAccess.client.searchWenku8(keyword, type, targetPage, forceRefresh))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e("Wenku8PageModel", "Failed to load discovery page: category=$category, page=$targetPage", error)
                mutableState.value = State.Failed(when (error) {
                    is Wenku8LoginRequiredException -> R.string.wenku8_login_required
                    is Wenku8SearchRateLimitException -> R.string.wenku8_search_rate_limit
                    is CloudflareChallengeRequiredException -> R.string.wenku8_verification_needed
                    is CloudflareWebViewUnavailableException -> R.string.wenku_webview_unavailable
                    is WenkuCookieStoreUnavailableException -> R.string.wenku_cookie_store_unavailable
                    is Wenku8ParseException -> R.string.wenku8_page_unrecognized
                    is Wenku8RestrictedException -> R.string.wenku8_content_restricted
                    else -> if (error.loadFailureKind() == LoadFailureKind.NETWORK) R.string.load_network_error else R.string.search_failed
                })
            }
        }
    }
}

private fun Wenku8Browse.labelResource(): Int = when (this) {
    Wenku8Browse.HOME -> R.string.wenku8_home
    Wenku8Browse.ALL -> R.string.wenku8_all
    Wenku8Browse.POPULAR -> R.string.wenku8_popular
    Wenku8Browse.DAILY -> R.string.wenku8_daily
    Wenku8Browse.MONTHLY -> R.string.wenku8_monthly
    Wenku8Browse.UPDATED -> R.string.wenku8_updated
    Wenku8Browse.NEW -> R.string.wenku8_new
    Wenku8Browse.ANIME -> R.string.wenku8_anime
    Wenku8Browse.COMPLETED -> R.string.wenku8_completed
}

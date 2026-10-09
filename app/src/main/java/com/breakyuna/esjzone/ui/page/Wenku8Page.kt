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
        var savedPage by rememberSaveable { mutableStateOf(1) }
        var showVerification by remember { mutableStateOf(false) }
        if (showVerification && keyword.isNotBlank()) {
            WenkuVerificationDialog(url = wenku8SearchUrl(keyword, type, model.page),
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
        LaunchedEffect(model) { if (state == Wenku8PageModel.State.Idle) model.load(savedPage) }
        LaunchedEffect(state) { (state as? Wenku8PageModel.State.Result)?.let { savedPage = it.result.page } }
        Scaffold(topBar = {
            Column {
                DiscoverySearchTopBar(query, { query = it }, ::submit, { query = "" }, { navigator?.pop() })
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    FilterChip(selected = searchType == Wenku8SearchType.TITLE,
                        onClick = { searchType = Wenku8SearchType.TITLE }, label = { Text(stringResource(R.string.wenku8_search_title)) })
                    FilterChip(selected = searchType == Wenku8SearchType.AUTHOR,
                        onClick = { searchType = Wenku8SearchType.AUTHOR }, label = { Text(stringResource(R.string.author)) })
                    TextButton(onClick = { navigator?.pushIfNotCurrent(Wenku8BookshelfPage) }) { Text(stringResource(R.string.bookshelf)) }
                    TextButton(onClick = { navigator?.pushIfNotCurrent(Wenku8LoginPage) }) { Text(stringResource(R.string.wenku8_session)) }
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
                        if (snapshot.message == R.string.wenku8_verification_needed) {
                            TextButton(onClick = { showVerification = true }) { Text(stringResource(R.string.wenku_verification_open)) }
                        }
                    }
                    is Wenku8PageModel.State.Result -> {
                        val visibleBooks = snapshot.result.novels.filter { adult || !it.isAdult }
                        if (visibleBooks.isEmpty()) item {
                            DiscoveryEmptyState(stringResource(R.string.search_no_results), stringResource(R.string.search_no_results_message))
                        }
                        items(visibleBooks, key = { it.url }) { book ->
                            DiscoveryNovelCard(book, Modifier.padding(horizontal = AppSpacing.md)) {
                                navigator?.pushIfNotCurrent(NovelPage(book))
                            }
                        }
                        item {
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
    var page = 1
        private set
    sealed interface State {
        data object Idle : State
        data object Loading : State
        data class Result(val result: Wenku8SearchResult) : State
        data class Failed(val message: Int) : State
    }
    fun load(targetPage: Int, forceRefresh: Boolean = false) {
        if (state.value == State.Loading || targetPage < 1 || keyword.isBlank()) return
        page = targetPage
        mutableState.value = State.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                mutableState.value = State.Result(PresentationAccess.client.searchWenku8(keyword, type, targetPage, forceRefresh))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.value = State.Failed(when (error) {
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

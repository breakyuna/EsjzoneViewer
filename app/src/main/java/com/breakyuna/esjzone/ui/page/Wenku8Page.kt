package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.external.CloudflareChallengeRequiredException
import com.breakyuna.esjzone.network.external.CloudflareWebViewUnavailableException
import com.breakyuna.esjzone.network.external.WenkuCookieStoreUnavailableException
import com.breakyuna.esjzone.network.wenku8.*
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.discovery.*
import com.breakyuna.esjzone.ui.navigation.*
import com.breakyuna.esjzone.ui.tab.CategoryBrowserPage
import com.breakyuna.esjzone.ui.tab.SearchTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Retained route for saved navigation entries; new entry points live in the main pages. */
class Wenku8Page(
    private val keyword: String = "",
    private val type: Wenku8SearchType = Wenku8SearchType.TITLE
) : AppDestination {
    override val key = "Wenku8Page:${type.name}:${encodeRouteTokenPart(keyword)}"
    @Composable
    override fun Content() {
        val source = LocalDiscoverySource.current
        LaunchedEffect(Unit) { source.value = LibrarySource.WENKU8 }
        if (keyword.isBlank()) WenkuDiscoveryScreen(showBack = true)
        else SearchTab.SearchContent(LibrarySource.WENKU8, keyword, type)
    }
}

@Composable
internal fun WenkuDiscoveryScreen(showBack: Boolean = false) {
    val navigator = LocalBaseNavigator.current
    val source = LocalDiscoverySource.current
    DiscoveryScaffold(
        title = stringResource(R.string.home_discover),
        onBack = if (showBack) ({ navigator?.pop(); Unit }) else null,
        actions = {
            IconButton(onClick = { navigator?.pushIfNotCurrent(SearchTab) }) {
                Icon(Icons.Filled.Search, stringResource(R.string.search_action))
            }
            IconButton(onClick = { navigator?.pushIfNotCurrent(CategoryBrowserPage()) }) {
                Icon(Icons.Filled.Category, stringResource(R.string.categories))
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SourceSelector(LibrarySource.WENKU8, { selected ->
                selected?.let { source.value = it }
                if (showBack && selected == LibrarySource.ESJZONE) navigator?.pop()
            })
            WenkuFeed(modifier = Modifier.weight(1f))
        }
    }
}

/** A source-specific body inside the shared discovery/search shell. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WenkuFeed(
    keyword: String = "",
    type: Wenku8SearchType = Wenku8SearchType.TITLE,
    initialBrowse: Wenku8Browse = Wenku8Browse.HOME,
    showBrowse: Boolean = false,
    refreshRequest: Int = 0,
    modifier: Modifier = Modifier
) {
    val navigator = LocalBaseNavigator.current
    val key = "wenku-feed:${initialBrowse.name}:${type.name}:${encodeRouteTokenPart(keyword)}"
    val model = rememberAppViewModel(key = key) { Wenku8PageModel(keyword, type) }
    val state by model.state.collectAsStateWithLifecycle()
    val accountStatus = LocalWenkuAccountStatus.current
    val adult by PresentationAccess.settings.adult
    var category by rememberSaveable(key) { mutableStateOf(initialBrowse) }
    var savedPage by rememberSaveable(key) { mutableStateOf(1) }
    var handledRefresh by rememberSaveable(key) { mutableStateOf(refreshRequest) }
    var pendingSessionReturn by rememberSaveable(key) { mutableStateOf(false) }
    var showVerification by remember { mutableStateOf(false) }
    val currentPageKey = navigator?.lastItem?.key
    var returnKey by rememberSaveable(key) { mutableStateOf<String?>(null) }
    fun refresh() = model.load(model.page, forceRefresh = true)
    fun openSession() {
        navigator?.let {
            returnKey = it.lastItem?.key
            pendingSessionReturn = true
            it.pushIfNotCurrent(Wenku8LoginPage)
        }
    }
    LaunchedEffect(model) {
        if (state == Wenku8PageModel.State.Idle) model.load(savedPage, category = category)
    }
    LaunchedEffect(state) {
        (state as? Wenku8PageModel.State.Result)?.let { savedPage = it.result.page }
        if ((state as? Wenku8PageModel.State.Failed)?.message == R.string.wenku8_login_required) {
            accountStatus.value = WenkuAccountStatus.SIGNED_OUT
        }
    }
    LaunchedEffect(refreshRequest, state) {
        if (handledRefresh != refreshRequest && state != Wenku8PageModel.State.Loading) {
            handledRefresh = refreshRequest
            model.load(1, forceRefresh = true)
        }
    }
    LaunchedEffect(currentPageKey, state) {
        if (pendingSessionReturn && currentPageKey == returnKey && state != Wenku8PageModel.State.Loading) {
            pendingSessionReturn = false
            refresh()
        }
    }
    if (showVerification) WenkuVerificationDialog(
        url = if (keyword.isBlank()) wenku8BrowseUrl(category, model.page) else wenku8SearchUrl(keyword, type, model.page),
        onVerified = { showVerification = false; refresh() },
        onUnavailable = { showVerification = false },
        onDismiss = { showVerification = false }
    )
    Column(modifier) {
        if (showBrowse) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Wenku8Browse.entries.filter { it != Wenku8Browse.HOME }.forEach { entry ->
                FilterChip(selected = category == entry,
                    enabled = state != Wenku8PageModel.State.Loading,
                    onClick = { category = entry; savedPage = 1; model.load(1, category = entry) },
                    label = { Text(stringResource(entry.labelResource())) })
            }
        }
        PullToRefreshBox(
            isRefreshing = state == Wenku8PageModel.State.Loading,
            onRefresh = ::refresh,
            modifier = Modifier.fillMaxSize()
        ) {
            val navPadding = LocalFloatingNavPadding.current
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = AppSpacing.lg, end = AppSpacing.lg,
                    top = AppSpacing.sm, bottom = AppSpacing.lg + navPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                when (val snapshot = state) {
                    Wenku8PageModel.State.Idle, Wenku8PageModel.State.Loading -> Unit
                    is Wenku8PageModel.State.Failed -> item {
                        DiscoveryErrorState(stringResource(snapshot.message), onRetry = ::refresh)
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            if (snapshot.message == R.string.wenku8_login_required || snapshot.message == R.string.wenku8_verification_needed) {
                                TextButton(onClick = ::openSession) { Text(stringResource(R.string.button_login)) }
                            }
                            if (snapshot.message == R.string.wenku8_verification_needed) {
                                TextButton(onClick = { showVerification = true }) { Text(stringResource(R.string.wenku_verification_open)) }
                            }
                        }
                    }
                    is Wenku8PageModel.State.Result -> {
                        val sections = snapshot.result.sections.map { section ->
                            section.title to section.novels.filter { adult || !it.isAdult }
                        }.filter { it.second.isNotEmpty() }
                        val books = snapshot.result.novels.filter { adult || !it.isAdult }
                        if (books.isEmpty() && sections.isEmpty()) item {
                            DiscoveryEmptyState(stringResource(R.string.search_no_results), stringResource(R.string.search_no_results_message))
                        }
                        sections.forEachIndexed { sectionIndex, (title, sectionBooks) ->
                            item(key = "heading:$sectionIndex") {
                                Text(title, Modifier.padding(top = AppSpacing.md, bottom = AppSpacing.xs), style = AppTypography.titleMedium)
                            }
                            items(sectionBooks, key = { "section:$sectionIndex:${it.url}" }) { book ->
                                DiscoveryNovelCard(book) { navigator?.pushIfNotCurrent(NovelPage(book)) }
                            }
                        }
                        items(books, key = { "book:${it.url}" }) { book ->
                            DiscoveryNovelCard(book) { navigator?.pushIfNotCurrent(NovelPage(book)) }
                        }
                        if (snapshot.result.sections.isEmpty()) item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { model.load(snapshot.result.page - 1) }, enabled = snapshot.result.page > 1) {
                                    Text(stringResource(R.string.wenku8_previous_page))
                                }
                                Text("${snapshot.result.page}/${snapshot.result.totalPages}", Modifier.padding(AppSpacing.md))
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

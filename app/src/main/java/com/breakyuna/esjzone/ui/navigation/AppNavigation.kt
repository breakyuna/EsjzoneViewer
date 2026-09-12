package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.novellibrary.community.ForumCategory
import com.breakyuna.esjzone.novellibrary.community.ForumThread
import com.breakyuna.esjzone.novellibrary.community.ForumTopic
import com.breakyuna.esjzone.novellibrary.novel.Category
import com.breakyuna.esjzone.novellibrary.novel.Chapter
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.ui.page.BookmarksPage
import com.breakyuna.esjzone.ui.page.CategoryPage
import com.breakyuna.esjzone.ui.page.ChapterCommentsPage
import com.breakyuna.esjzone.ui.page.ChapterPage
import com.breakyuna.esjzone.ui.page.DownloadPage
import com.breakyuna.esjzone.ui.page.FavoritePage
import com.breakyuna.esjzone.ui.page.ForumBoardPage
import com.breakyuna.esjzone.ui.page.ForumCategoryPage
import com.breakyuna.esjzone.ui.page.ForumPage
import com.breakyuna.esjzone.ui.page.ForumPostPage
import com.breakyuna.esjzone.ui.page.GuestbookPage
import com.breakyuna.esjzone.ui.page.HistoryPage
import com.breakyuna.esjzone.ui.page.LogsPage
import com.breakyuna.esjzone.ui.page.NovelListPage
import com.breakyuna.esjzone.ui.page.NovelPage
import com.breakyuna.esjzone.ui.page.SearchPage
import com.breakyuna.esjzone.ui.page.SettingsPage
import com.breakyuna.esjzone.ui.tab.CategoryBrowserPage
import com.breakyuna.esjzone.ui.tab.SearchTab
import com.breakyuna.esjzone.ui.screen.LoadingScreen
import com.breakyuna.esjzone.ui.screen.LoginScreen
import com.breakyuna.esjzone.ui.screen.MainScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/**
 * All application destinations are serializable Navigation 3 keys.
 *
 * Legacy destinations are intentionally represented by a serializable route
 * rather than a Composable instance. The live registry is only a same-process
 * fast path; [LegacyRoute.restore] reconstructs a page from the saved route
 * arguments after process recreation. This keeps Navigation 3 in charge of
 * the runtime stack and prevents business models, cookies, or credentials from
 * being placed in SavedState.
 */
@Serializable
sealed interface AppNavKey : NavKey {
    @Serializable
    data object Loading : AppNavKey

    @Serializable
    data object Login : AppNavKey

    @Serializable
    data object Main : AppNavKey

    @Serializable
    data object HomeTab : AppNavKey

    @Serializable
    data object HistoryTab : AppNavKey

    @Serializable
    data object BookshelfTab : AppNavKey

    @Serializable
    data object ProfileTab : AppNavKey

    @Serializable
    data class Legacy(val route: LegacyRoute) : AppNavKey

    @Serializable
    data class Reader(val route: ReaderRoute) : AppNavKey
}

/**
 * Serializable route arguments for every currently reachable legacy page.
 *
 * A NavEntry is allowed to outlive the Composable instance that created it
 * (configuration change, process recreation, or saved back stack restore).
 * Consequently the back stack never stores an opaque Composable/token lookup
 * as its only source of truth. These routes carry the minimal primitive
 * arguments needed to recreate the existing page; the live registry remains a
 * fast-path only.
 */
@Serializable
sealed interface LegacyRoute {
    @Serializable data class Static(val token: String) : LegacyRoute
    @Serializable data class Search(val keyword: String) : LegacyRoute
    @Serializable data class Novel(val identity: String) : LegacyRoute
    @Serializable data class Category(val identity: String) : LegacyRoute
    @Serializable data class NovelList(
        val novelType: Int,
        val sortType: Int,
        val adultOnly: Boolean
    ) : LegacyRoute
    @Serializable data class ChapterComments(val identity: String) : LegacyRoute
    @Serializable data class ForumCategory(val id: String, val url: String) : LegacyRoute
    @Serializable data class ForumBoard(val identity: String) : LegacyRoute
    @Serializable data class ForumPost(val identity: String) : LegacyRoute
}

/** Reader arguments are separate to make the root shell boundary explicit. */
@Serializable
data class ReaderRoute(
    val novelId: String,
    val chapterIdentity: String
)

private fun LegacyRoute.token(): String = when (this) {
    is LegacyRoute.Static -> token
    is LegacyRoute.Search -> "SearchPage:$keyword"
    is LegacyRoute.Novel -> "NovelPage:$identity"
    is LegacyRoute.Category -> "CategoryPage:$identity"
    is LegacyRoute.NovelList -> "NovelListPage:$novelType:$sortType:$adultOnly"
    is LegacyRoute.ChapterComments -> "ChapterCommentsPage:$identity"
    is LegacyRoute.ForumCategory -> "ForumCategoryPage:$id:$url"
    is LegacyRoute.ForumBoard -> "ForumBoardPage:$identity"
    is LegacyRoute.ForumPost -> "ForumPostPage:$identity"
}

private fun routeFromToken(token: String): LegacyRoute {
    val kind = token.substringBefore(':')
    val argument = token.substringAfter(':', missingDelimiterValue = "")
    return when (kind) {
        "SearchPage" -> LegacyRoute.Search(argument)
        "NovelPage" -> LegacyRoute.Novel(argument)
        "CategoryPage" -> LegacyRoute.Category(argument)
        "NovelListPage" -> {
            val parts = argument.split(':')
            LegacyRoute.NovelList(
                novelType = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                sortType = parts.getOrNull(1)?.toIntOrNull() ?: 1,
                adultOnly = parts.getOrNull(2)?.toBooleanStrictOrNull() ?: false
            )
        }
        "ChapterCommentsPage" -> LegacyRoute.ChapterComments(argument)
        "ForumCategoryPage" -> {
            val parts = argument.split(':', limit = 2)
            LegacyRoute.ForumCategory(
                id = parts.getOrNull(0).orEmpty(),
                // Older persisted keys contained only the forum id. Derive
                // the canonical route shape so those entries remain
                // reconstructible after process recreation as well.
                url = parts.getOrNull(1).orEmpty().ifBlank {
                    "/forum/${parts.getOrNull(0).orEmpty()}/"
                }
            )
        }
        "ForumBoardPage" -> LegacyRoute.ForumBoard(argument)
        "ForumPostPage" -> LegacyRoute.ForumPost(argument)
        else -> LegacyRoute.Static(token)
    }
}

private fun readerRouteFromToken(token: String): ReaderRoute {
    val argument = token.substringAfter("ChapterPage:", missingDelimiterValue = "")
    val separator = argument.indexOf(':')
    return if (separator < 0) {
        ReaderRoute(novelId = "", chapterIdentity = argument)
    } else {
        ReaderRoute(
            novelId = argument.substring(0, separator),
            chapterIdentity = argument.substring(separator + 1)
        )
    }
}

/** Compose-only destination contract used by the transition adapter. */
interface AppDestination {
    val key: String
        get() = this::class.qualifiedName ?: this::class.simpleName.orEmpty()

    /** Reader is a root-level shell boundary, not a tab child destination. */
    val isReaderDestination: Boolean
        get() = false

    @Composable
    fun Content()
}

/** Tab metadata consumed by the adaptive shell. */
data class AppTabOptions(
    val index: Int,
    val title: String,
    val icon: androidx.compose.ui.graphics.painter.Painter
)

interface AppTab : AppDestination {
    @get:Composable
    val options: AppTabOptions
}

/**
 * Small state model base replacing the previous third-party screen model.
 * ViewModel lifetime is supplied by Navigation 3's ViewModel entry decorator.
 */
open class AppStateViewModel<S>(initialState: S) : ViewModel() {
    private val stateFlow = MutableStateFlow(initialState)
    val state: StateFlow<S> = stateFlow.asStateFlow()
    protected val mutableState: MutableStateFlow<S> = stateFlow
}

/** Creates arbitrary-argument feature ViewModels in the current NavEntry scope. */
@Composable
inline fun <reified VM : ViewModel> rememberAppViewModel(
    noinline factory: () -> VM
): VM {
    val owner = LocalViewModelStoreOwner.current
        ?: error("Navigation 3 ViewModel entry decorator is missing")
    return viewModel<VM>(
        viewModelStoreOwner = owner,
        factory = remember(owner, VM::class) {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = factory() as T
            }
        }
    )
}

/**
 * Navigation facade used by legacy presentation components during the
 * migration window. It mutates Navigation 3's typed list directly; there is
 * no second runtime navigator hidden underneath this class.
 */
class AppNavigator internal constructor(
    private val backStack: MutableList<NavKey>,
    private val registry: MutableMap<String, AppDestination>,
    private val rootNavigator: AppNavigator? = null,
    private val setAuthorization: ((Authorization) -> Unit)? = null
) {
    val items: List<AppDestination>
        get() = backStack.mapNotNull { key ->
            when (key) {
                AppNavKey.Loading -> registry["LoadingScreen"]
                AppNavKey.Login -> registry["LoginScreen"]
                AppNavKey.Main -> registry["MainScreen"]
                is AppNavKey.Legacy -> registry[key.route.token()]
                is AppNavKey.Reader -> registry[readerToken(key.route)]
                else -> null
            }
        }

    val lastItem: AppDestination?
        get() = items.lastOrNull()

    private val root: AppNavigator
        get() = rootNavigator ?: this

    fun push(destination: AppDestination) {
        if (destination.isReaderDestination) {
            root.openReader(destination)
            return
        }
        register(destination)
        backStack.add(AppNavKey.Legacy(routeFromToken(destination.key)))
    }

    fun pushIfNotCurrent(destination: AppDestination): Boolean {
        val existingIndex = backStack.indexOfLast { key ->
            key is AppNavKey.Legacy && key.route.token() == destination.key
        }
        if (existingIndex == backStack.lastIndex) return false
        if (existingIndex >= 0) {
            while (backStack.lastIndex > existingIndex) backStack.removeLastOrNull()
            return false
        }
        push(destination)
        return true
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeLastOrNull()
        return true
    }

    fun replace(destination: AppDestination) {
        when (destination) {
            is LoadingScreen -> {
                register(destination)
                replaceTop(AppNavKey.Loading)
            }
            LoginScreen -> {
                register(destination)
                replaceTop(AppNavKey.Login)
            }
            is MainScreen -> {
                register(destination)
                setAuthorization?.invoke(destination.authorization)
                replaceTop(AppNavKey.Main)
            }
            else -> {
                if (destination.isReaderDestination) {
                    root.openReader(destination)
                } else {
                    register(destination)
                    replaceTop(AppNavKey.Legacy(routeFromToken(destination.key)))
                }
            }
        }
    }

    fun replaceAll(destination: AppDestination) {
        when (destination) {
            LoginScreen -> {
                register(destination)
                backStack.clear()
                backStack.add(AppNavKey.Login)
            }
            is MainScreen -> {
                register(destination)
                setAuthorization?.invoke(destination.authorization)
                backStack.clear()
                backStack.add(AppNavKey.Main)
            }
            else -> {
                register(destination)
                backStack.clear()
                backStack.add(AppNavKey.Legacy(routeFromToken(destination.key)))
            }
        }
    }

    private fun replaceTop(key: AppNavKey) {
        if (backStack.isEmpty()) backStack.add(key) else backStack[backStack.lastIndex] = key
    }

    private fun register(destination: AppDestination) {
        registry[destination.key] = destination
    }

    internal fun child(stack: MutableList<NavKey>): AppNavigator =
        AppNavigator(stack, registry, root, setAuthorization)

    internal fun destination(route: LegacyRoute): AppDestination? =
        registry[route.token()] ?: route.restore()

    internal fun readerDestination(route: ReaderRoute): AppDestination? =
        registry[readerToken(route)] ?: route.restore()

    private fun openReader(destination: AppDestination) {
        register(destination)
        val route = readerRouteFromToken(destination.key)
        if (backStack.lastOrNull() !is AppNavKey.Reader) {
            backStack.add(AppNavKey.Reader(route))
        } else {
            backStack[backStack.lastIndex] = AppNavKey.Reader(route)
        }
    }
}

private fun readerToken(route: ReaderRoute): String =
    "ChapterPage:${route.novelId}:${route.chapterIdentity}"

private fun ReaderRoute.restore(): AppDestination = ChapterPage(
    novelId = novelId,
    chapter = Chapter(name = "", url = chapterIdentity, isHistory = true),
    history = ChapterStateHolder(),
    novelName = novelId,
    novelUrl = novelId.takeIf { it.isNotBlank() }
        ?.let { EsjzoneUrls.resolve("/detail/$it.html") }
        .orEmpty(),
    novelCoverUrl = ""
)

private fun LegacyRoute.restore(): AppDestination? = when (this) {
    is LegacyRoute.Static -> when {
        token == BookmarksPage.key || token.endsWith(".BookmarksPage") -> BookmarksPage
        token == DownloadPage.key || token.endsWith(".DownloadPage") -> DownloadPage
        token == FavoritePage.key || token.endsWith(".FavoritePage") -> FavoritePage
        token == HistoryPage.key || token.endsWith(".HistoryPage") -> HistoryPage
        token == LogsPage.key || token.endsWith(".LogsPage") -> LogsPage
        token == SettingsPage.key || token.endsWith(".SettingsPage") -> SettingsPage
        token == ForumPage.key || token.endsWith(".ForumPage") -> ForumPage
        token == GuestbookPage.key || token.endsWith(".GuestbookPage") -> GuestbookPage
        token == SearchTab.key || token.endsWith(".SearchTab") -> SearchTab
        token == CategoryBrowserPage().key -> CategoryBrowserPage()
        else -> null
    }
    is LegacyRoute.Search -> SearchPage(keyword)
    is LegacyRoute.Novel -> NovelPage(FavoriteNovel(name = "", url = identity))
    is LegacyRoute.Category -> CategoryPage(
        Category(name = "", url = identity, isAdult = false)
    )
    is LegacyRoute.NovelList -> NovelListPage(novelType, sortType, adultOnly)
    is LegacyRoute.ChapterComments -> ChapterCommentsPage("", identity)
    is LegacyRoute.ForumCategory -> ForumCategoryPage(
        ForumCategory(
            id = id,
            groupName = null,
            name = "",
            description = null,
            postCount = null,
            url = url
        )
    )
    is LegacyRoute.ForumBoard -> ForumBoardPage(
        ForumThread(
            categoryId = "",
            id = identity.substringAfterLast('/'),
            title = "",
            topicCount = null,
            replyCount = null,
            lastPostDate = null,
            url = identity
        )
    )
    is LegacyRoute.ForumPost -> ForumPostPage(
        ForumTopic(
            boardId = identity.trim('/').split('/').getOrNull(1).orEmpty(),
            id = identity.substringAfterLast('/'),
            title = "",
            author = null,
            createdAt = null,
            replyCount = null,
            viewCount = null,
            lastReplyAt = null,
            url = identity
        )
    )
}

/** Predictive-back compatible defaults: new content covers the old entry. */
internal val pushTransition: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    slideInHorizontally(initialOffsetX = { it }) togetherWith
        ExitTransition.KeepUntilTransitionsFinished
}

internal val popTransition: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    slideInHorizontally(initialOffsetX = { -it }) togetherWith
        slideOutHorizontally(targetOffsetX = { it })
}

internal val predictivePopTransition:
    AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform = { _ ->
    slideInHorizontally(initialOffsetX = { -it }) togetherWith
        slideOutHorizontally(targetOffsetX = { it })
}

/** Root Navigation 3 host. Loading is the session gate; Main owns tab stacks. */
@Composable
fun AppNavigation() {
    val backStack: MutableList<NavKey> = rememberNavBackStack(AppNavKey.Loading)
    val registry = remember { linkedMapOf<String, AppDestination>() }
    // A restored Reader can be the first visible root entry, so it cannot rely
    // on MainScreen's CompositionLocal scope. Read the process-persisted
    // cookie session before composing the root NavDisplay; LoadingScreen still
    // owns the legacy Room import for the normal Loading route.
    var authorization by remember {
        mutableStateOf(
            PresentationAccess.client.restoreAuthorization(
                PresentationAccess.settings.domain.value
            )
        )
    }
    val navigator = remember(backStack, registry) {
        AppNavigator(backStack, registry, setAuthorization = { authorization = it })
    }

    LaunchedEffect(Unit) {
        registry["LoadingScreen"] = LoadingScreen()
        registry["LoginScreen"] = LoginScreen
    }

    CompositionLocalProvider(
        LocalAppNavigator provides navigator,
        LocalBaseNavigator provides navigator,
        // Root-level entries (especially Reader) must see the same session as
        // Main. Reader itself is guarded below when no persisted session exists.
        LocalAuthorization provides (authorization ?: Authorization("", ""))
    ) {
        androidx.compose.material3.Surface(
            // The root host stays edge-to-edge. Loading/Login own their
            // standalone safe drawing insets; Main's adaptive shell owns
            // content/chrome insets; Reader owns its immersive insets.
            modifier = Modifier.fillMaxSize()
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { navigator.pop() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator()
                ),
                transitionSpec = pushTransition,
                popTransitionSpec = popTransition,
                predictivePopTransitionSpec = predictivePopTransition,
                entryProvider = entryProvider {
                    entry<AppNavKey.Loading> {
                        LoadingScreen().Content()
                    }
                    entry<AppNavKey.Login> {
                        LoginScreen.Content()
                    }
                    entry<AppNavKey.Main> {
                        val session = authorization
                        if (session == null) {
                            // A restored Main key may arrive before the
                            // in-memory authorization state. Re-enter the
                            // same Loading gate so the persisted session is
                            // checked instead of rendering a dead spinner.
                            LoadingScreen().Content()
                        } else {
                            CompositionLocalProvider(LocalAuthorization provides session) {
                                MainScreen(session).Content()
                            }
                        }
                    }
                    entry<AppNavKey.Reader> { key ->
                        // Reader presentation remains untouched in this stage;
                        // ChapterPage is the independent shell boundary.
                        val session = authorization
                        if (session == null) {
                            // A stale restored Reader must never run against
                            // LocalAuthorization's empty default value.
                            LoginScreen.Content()
                        } else {
                            CompositionLocalProvider(LocalAuthorization provides session) {
                                navigator.readerDestination(key.route)?.Content()
                            }
                        }
                    }
                    entry<AppNavKey.Legacy> { key ->
                        navigator.destination(key.route)?.Content()
                    }
                }
            )
        }
    }
}

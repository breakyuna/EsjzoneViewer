package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.getForumCategories
import com.breakyuna.esjzone.network.features.getForumBoard
import com.breakyuna.esjzone.network.features.getForumPost
import com.breakyuna.esjzone.network.features.getForumThreads
import com.breakyuna.esjzone.network.features.ForumBoardResult
import com.breakyuna.esjzone.novellibrary.community.ForumCategory
import com.breakyuna.esjzone.novellibrary.community.ForumPost
import com.breakyuna.esjzone.novellibrary.community.ForumTopic
import com.breakyuna.esjzone.novellibrary.community.ForumThread
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTouchTarget
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.appStateColors
import com.breakyuna.esjzone.ui.designsystem.rememberAppAdaptiveMetrics
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.ErrorState
import com.breakyuna.esjzone.ui.product.LoadingSkeleton
import com.breakyuna.esjzone.ui.product.OfflineState
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ForumPage : AppDestination {
    private fun readResolve(): Any = ForumPage

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { ForumPageModel(authorization) }
        val state by model.state.collectAsState()
        val metrics = rememberAppAdaptiveMetrics()

        Column(modifier = Modifier.fillMaxSize()) {
            CommunityTopBar(
                title = stringResource(id = R.string.forum),
                onBack = { navigator?.pop() }
            )

            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.forum_empty),
                onRetry = model::retry,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { categories ->
                val grouped = categories.groupBy { it.groupName.orEmpty() }
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = metrics.contentMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(
                        start = metrics.horizontalPadding,
                        end = metrics.horizontalPadding,
                        top = AppSpacing.lg,
                        bottom = AppSpacing.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    grouped.forEach { (groupName, groupCategories) ->
                        item(key = "group-$groupName", contentType = "forum-group") {
                            ForumGroupHeader(
                                name = groupName.ifBlank { stringResource(R.string.forum) },
                                boardCount = groupCategories.size
                            )
                        }
                        itemsIndexed(
                            groupCategories,
                            key = { _, category -> "forum-category-${category.id}" },
                            contentType = { _, _ -> "forum-category" }
                        ) { index, category ->
                            ForumCategoryCard(category = category, accentIndex = index) {
                                navigator?.pushIfNotCurrent(ForumCategoryPage(category))
                            }
                        }
                        item(key = "group-spacer-$groupName", contentType = "spacer") {
                            Spacer(modifier = Modifier.height(AppSpacing.lg))
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) { model.load() }
    }
}

class ForumCategoryPage(private val category: ForumCategory) : AppDestination {
    override val key: String =
        "ForumCategoryPage:" + category.id.ifBlank { category.url.trim() } + ":" +
            EsjzoneUrls.canonicalPageKey(category.url)
                .ifBlank { category.url.trim() }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { ForumCategoryPageModel(authorization, category) }
        val state by model.state.collectAsState()
        val metrics = rememberAppAdaptiveMetrics()

        Column(modifier = Modifier.fillMaxSize()) {
            CommunityTopBar(title = category.name, onBack = { navigator?.pop() })
            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.forum_threads_empty),
                onRetry = model::retry,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { threads ->
                LazyColumn(
                    modifier = Modifier
                        .widthIn(max = metrics.contentMaxWidth)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(bottom = AppSpacing.xxl)
                ) {
                    items(
                        threads,
                        key = { "forum-thread-${it.categoryId}-${it.id}" },
                        contentType = { "forum-thread" }
                    ) { thread ->
                        ForumThreadCard(thread) {
                            // A board can be either a novel forum or a nested
                            // topic board; ForumBoardPage detects the template.
                            navigator?.pushIfNotCurrent(
                                ForumBoardPage(thread)
                            )
                        }
                    }
                    item(key = "forum-threads-footer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(AppSpacing.xxl))
                    }
                }
            }
        }

        LaunchedEffect(Unit) { model.load() }
    }
}

class ForumBoardPage(private val thread: ForumThread) : AppDestination {
    override val key: String =
        "ForumBoardPage:" + EsjzoneUrls.canonicalPageKey(thread.url)
            .ifBlank { thread.id }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { ForumBoardPageModel(authorization, thread) }
        val state by model.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            CommunityTopBar(title = thread.title, onBack = { navigator?.pop() })
            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.forum_board_empty),
                onRetry = model::retry,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { board ->
                when (board) {
                    is ForumBoardResult.Novel -> {
                        ForumNovelBoardContent(
                            board = board,
                            thread = thread,
                            onOpenNovel = {
                                navigator?.pushIfNotCurrent(
                                    NovelPage(
                                        CategoryNovel(
                                            name = thread.title,
                                            url = board.detailUrl,
                                            forumUrl = thread.url
                                        )
                                    )
                                )
                            },
                            onTopicClick = { topic ->
                                navigator?.pushIfNotCurrent(ForumPostPage(topic))
                            }
                        )
                    }

                    is ForumBoardResult.Topics -> {
                        ForumTopicsContent(board.items) { topic ->
                            navigator?.pushIfNotCurrent(ForumPostPage(topic))
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) { model.load() }
    }
}

class ForumPostPage(private val topic: ForumTopic) : AppDestination {
    override val key: String =
        "ForumPostPage:" + EsjzoneUrls.canonicalPageKey(topic.url)
            .ifBlank { "${topic.boardId}-${topic.id}" }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { ForumPostPageModel(authorization, topic) }
        val commentsModel = rememberAppViewModel {
            CommentPageModel(authorization, topic.url)
        }
        val state by model.state.collectAsState()
        val postScrollState = rememberScrollState()
        val metrics = rememberAppAdaptiveMetrics()

        Column(modifier = Modifier.fillMaxSize()) {
            CommunityTopBar(title = topic.title, onBack = { navigator?.pop() })
            when (val snapshot = state) {
                is CommunityState.Loading -> LoadingSkeleton(modifier = Modifier.fillMaxWidth())
                is CommunityState.Error -> if (snapshot.failure == LoadFailureKind.NETWORK) {
                    OfflineState(modifier = Modifier.fillMaxWidth(), onRetry = model::retry)
                } else {
                    ErrorState(
                        title = stringResource(R.string.community_load_failed),
                        message = stringResource(R.string.community_empty_guidance),
                        modifier = Modifier.fillMaxWidth(),
                        onRetry = model::retry
                    )
                }
                is CommunityState.Empty -> EmptyState(
                    title = stringResource(R.string.forum_threads_empty),
                    message = stringResource(R.string.community_empty_guidance),
                    modifier = Modifier.fillMaxWidth()
                )
                is CommunityState.Result -> Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(max = metrics.contentMaxWidth)
                            .fillMaxWidth()
                            .align(Alignment.CenterHorizontally)
                            .verticalScroll(postScrollState)
                    ) {
                        ForumPostCard(snapshot.data)
                        CommentSectionContent(
                            model = commentsModel,
                            showHeader = true,
                            modifier = Modifier.fillMaxWidth(),
                            scrollState = postScrollState
                        )
                    }
                    CommentComposerHost(model = commentsModel)
                }
            }
        }

        LaunchedEffect(Unit) { model.load() }
    }
}

object GuestbookPage : AppDestination {
    private fun readResolve(): Any = GuestbookPage

    // Keep this static route stable across process recreation. Relying on the object class name
    // made the saved Navigation 3 key dependent on Kotlin's generated object name.
    override val key: String = "GuestbookPage"

    @Composable
    override fun Content() {
        val authorization = LocalAuthorization.current
        // Resolve the page once while this NavEntry is alive. The explicit relative route avoids
        // a partially restored domain value producing a different ViewModel identity mid-compose.
        val pageUrl = EsjzoneUrls.resolve("/guestbook/")
        val model = rememberAppViewModel {
            CommentPageModel(authorization, pageUrl)
        }
        CommentListPage(
            title = stringResource(id = R.string.guestbook),
            model = model
        )
    }
}

class ChapterCommentsPage(
    private val chapterName: String,
    private val chapterUrl: String
) : AppDestination {
    override val key: String =
        "ChapterCommentsPage:" + EsjzoneUrls.canonicalPageKey(chapterUrl)
            .ifBlank { chapterName.trim() }

    @Composable
    override fun Content() {
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel {
            CommentPageModel(authorization, chapterUrl)
        }
        CommentListPage(title = chapterName, model = model)
    }
}

@Composable
private fun ForumGroupHeader(name: String, boardCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.xs, bottom = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Surface(
            modifier = Modifier
                .width(4.dp)
                .height(24.dp),
            shape = RoundedCornerShape(99.dp),
            color = MaterialTheme.colorScheme.tertiary
        ) {}
        Text(
            text = name,
            style = AppTypography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = AppShapes.pill,
            color = appStateColors().containerRaised
        ) {
            Text(
                text = stringResource(R.string.forum_board_count, boardCount),
                style = AppTypography.labelMedium,
                color = appStateColors().contentMuted,
                modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
            )
        }
    }
}

@Composable
private fun ForumCategoryCard(
    category: ForumCategory,
    accentIndex: Int,
    onClick: () -> Unit
) {
    val iconTint = when (accentIndex % 3) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(),
        shape = AppShapes.prominent,
        colors = CardDefaults.cardColors(containerColor = appStateColors().containerRaised)
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.lg),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Surface(
                modifier = Modifier.size(AppTouchTarget.minimum),
                shape = AppShapes.standard,
                color = iconTint.copy(alpha = 0.13f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Text(
                    text = category.name,
                    style = AppTypography.titleMedium,
                    maxLines = 2
                )
                category.description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = AppTypography.bodyMedium,
                        color = appStateColors().contentMuted,
                        maxLines = 2
                    )
                }
                category.postCount?.let { count ->
                    Text(
                        text = stringResource(R.string.forum_post_count, count),
                        style = AppTypography.labelMedium,
                        color = appStateColors().contentMuted,
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun ForumThreadCard(thread: ForumThread, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
        shape = AppShapes.standard,
        colors = CardDefaults.cardColors(containerColor = appStateColors().containerRaised)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Text(
                text = thread.title,
                style = AppTypography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                thread.topicCount?.let {
                    Text(
                        text = stringResource(id = R.string.forum_topic_count, it),
                        style = AppTypography.labelMedium,
                        color = appStateColors().contentMuted
                    )
                }
                thread.replyCount?.let {
                    Text(
                        text = stringResource(id = R.string.forum_reply_count, it),
                        style = AppTypography.labelMedium,
                        color = appStateColors().contentMuted
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                thread.lastPostDate?.let {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = appStateColors().contentMuted
                    )
                    Text(
                        text = it,
                        style = AppTypography.bodySmall,
                        color = appStateColors().contentMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ForumTopicsContent(
    topics: List<ForumTopic>,
    onTopicClick: (ForumTopic) -> Unit
) {
    val metrics = rememberAppAdaptiveMetrics()
    if (topics.isEmpty()) {
        EmptyState(
            title = stringResource(id = R.string.forum_board_empty),
            message = stringResource(R.string.forum_board_empty_guidance),
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = metrics.contentMaxWidth)
                .fillMaxWidth()
                .fillMaxHeight(),
            contentPadding = PaddingValues(bottom = AppSpacing.xxl)
        ) {
            items(
                topics,
                key = { "forum-topic-${it.boardId}-${it.id}" },
                contentType = { "forum-topic" }
            ) { topic ->
                ForumTopicCard(topic) { onTopicClick(topic) }
            }
            item(key = "forum-topics-footer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(AppSpacing.xxl))
            }
        }
    }
}

@Composable
private fun ForumNovelBoardContent(
    board: ForumBoardResult.Novel,
    thread: ForumThread,
    onOpenNovel: () -> Unit,
    onTopicClick: (ForumTopic) -> Unit
) {
    val metrics = rememberAppAdaptiveMetrics()
    LazyColumn(
        modifier = Modifier
            .widthIn(max = metrics.contentMaxWidth)
            .fillMaxWidth()
            .fillMaxHeight(),
        contentPadding = PaddingValues(bottom = AppSpacing.xxl)
    ) {
        item(key = "forum-novel-info", contentType = "novel-board-header") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                shape = AppShapes.prominent,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    Text(
                        text = thread.title,
                        style = AppTypography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.forum_novel_board),
                        style = AppTypography.bodySmall,
                        color = appStateColors().contentMuted,
                        modifier = Modifier.padding(top = AppSpacing.xs)
                    )
                    OutlinedButton(
                        onClick = onOpenNovel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = AppSpacing.md)
                    ) {
                        Text(text = stringResource(id = R.string.forum_open_novel))
                    }
                }
            }
        }
        if (board.items.isEmpty()) {
            item(key = "forum-novel-empty", contentType = "empty-state") {
                EmptyState(
                    title = stringResource(id = R.string.forum_board_empty),
                    message = stringResource(R.string.forum_board_empty_guidance),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(
                board.items,
                key = { "forum-topic-${it.boardId}-${it.id}" },
                contentType = { "forum-topic" }
            ) { topic ->
                ForumTopicCard(topic) { onTopicClick(topic) }
            }
        }
        item(key = "forum-novel-footer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(AppSpacing.xxl))
        }
    }
}

@Composable
private fun ForumTopicCard(topic: ForumTopic, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xs),
        shape = AppShapes.standard,
        colors = CardDefaults.cardColors(containerColor = appStateColors().containerRaised)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Text(
                text = topic.title,
                style = AppTypography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val authorAndDate = listOfNotNull(topic.author, topic.createdAt)
                .joinToString(" · ")
            if (authorAndDate.isNotBlank()) {
                Text(
                    text = authorAndDate,
                    style = AppTypography.bodySmall,
                    color = appStateColors().contentMuted,
                    modifier = Modifier.padding(top = AppSpacing.sm)
                )
            }
            val stats = listOfNotNull(
                topic.replyCount?.let { stringResource(R.string.forum_reply_count, it) },
                topic.viewCount?.let { stringResource(R.string.forum_view_count, it) }
            ).joinToString(" · ")
            if (stats.isNotBlank() || !topic.lastReplyAt.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (stats.isNotBlank()) {
                        Text(
                            text = stats,
                            style = AppTypography.labelMedium,
                            color = appStateColors().contentMuted
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    topic.lastReplyAt?.let {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = appStateColors().contentMuted
                        )
                        Text(
                            text = it,
                            style = AppTypography.bodySmall,
                            color = appStateColors().contentMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumPostCard(post: ForumPost) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        shape = AppShapes.prominent,
        colors = CardDefaults.cardColors(containerColor = appStateColors().containerRaised)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Text(
                text = post.title,
                style = AppTypography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            val authorAndDate = listOfNotNull(post.author, post.createdAt)
                .joinToString(" · ")
            if (authorAndDate.isNotBlank()) {
                Text(
                    text = authorAndDate,
                    style = AppTypography.bodySmall,
                    color = appStateColors().contentMuted,
                    modifier = Modifier.padding(top = AppSpacing.sm)
                )
            }
            if (post.contentText.isNotBlank()) {
                Text(
                    text = post.contentText,
                    style = AppTypography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
    }
}

private class ForumPageModel(
    private val authorization: Authorization
) : AppStateViewModel<CommunityState<List<ForumCategory>>>(CommunityState.Loading) {
    private var loadStarted = false

    fun retry() = load()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = try {
                PresentationAccess.client.getForumCategories(authorization).toCommunityState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ForumPageModel", "Failed to load forum categories", error)
                loadStarted = false
                CommunityState.Error(error.loadFailureKind())
            }
        }
    }
}

private class ForumCategoryPageModel(
    private val authorization: Authorization,
    private val category: ForumCategory
) : AppStateViewModel<CommunityState<List<ForumThread>>>(CommunityState.Loading) {
    private var loadStarted = false

    fun retry() = load()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = try {
                PresentationAccess.client.getForumThreads(authorization, category).toCommunityState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(
                    "ForumCategoryPageModel",
                    "Failed to load forum category ${category.id}",
                    error
                )
                loadStarted = false
                CommunityState.Error(error.loadFailureKind())
            }
        }
    }
}

private class ForumBoardPageModel(
    private val authorization: Authorization,
    private val thread: ForumThread
) : AppStateViewModel<CommunityState<com.breakyuna.esjzone.network.features.ForumBoardResult>>(
    CommunityState.Loading
) {
    private var loadStarted = false

    fun retry() = load()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = try {
                CommunityState.Result(PresentationAccess.client.getForumBoard(authorization, thread))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(
                    "ForumBoardPageModel",
                    "Failed to load forum board ${thread.id}",
                    error
                )
                loadStarted = false
                CommunityState.Error(error.loadFailureKind())
            }
        }
    }
}

private class ForumPostPageModel(
    private val authorization: Authorization,
    private val topic: ForumTopic
) : AppStateViewModel<CommunityState<ForumPost>>(CommunityState.Loading) {
    private var loadStarted = false

    fun retry() = load()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = try {
                CommunityState.Result(PresentationAccess.client.getForumPost(authorization, topic))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(
                    "ForumPostPageModel",
                    "Failed to load forum topic ${topic.id}",
                    error
                )
                loadStarted = false
                CommunityState.Error(error.loadFailureKind())
            }
        }
    }
}

private fun <T> List<T>.toCommunityState(): CommunityState<List<T>> =
    if (isEmpty()) CommunityState.Empty else CommunityState.Result(this)

package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
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
import com.breakyuna.esjzone.ui.component.QuietBackHeader
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietErrorState
import com.breakyuna.esjzone.ui.component.QuietLoadingState
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object ForumPage : Screen {
    private fun readResolve(): Any = ForumPage

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel { ForumPageModel(authorization) }
        val state by model.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            QuietBackHeader(
                title = stringResource(id = R.string.forum),
                onBack = { navigator?.pop() }
            )

            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.forum_empty),
            ) { categories ->
                val grouped = categories.groupBy { it.groupName.orEmpty() }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = QuietEditorial.pagePadding,
                        end = QuietEditorial.pagePadding,
                        top = 18.dp,
                        bottom = 32.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    grouped.forEach { (groupName, groupCategories) ->
                        item(key = "group-$groupName") {
                            ForumGroupHeader(
                                name = groupName.ifBlank { stringResource(R.string.forum) },
                                boardCount = groupCategories.size
                            )
                        }
                        itemsIndexed(
                            groupCategories,
                            key = { _, category -> "forum-category-${category.id}" }
                        ) { index, category ->
                            ForumCategoryCard(category = category, accentIndex = index) {
                                navigator?.pushIfNotCurrent(ForumCategoryPage(category))
                            }
                        }
                        item(key = "group-spacer-$groupName") {
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) { model.load() }
    }
}

class ForumCategoryPage(private val category: ForumCategory) : Screen {
    override val key: ScreenKey =
        "ForumCategoryPage:" + category.id.ifBlank { category.url.trim() }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel { ForumCategoryPageModel(authorization, category) }
        val state by model.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            QuietBackHeader(title = category.name, onBack = { navigator?.pop() })
            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.forum_threads_empty),
            ) { threads ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(threads, key = { "forum-thread-${it.categoryId}-${it.id}" }) { thread ->
                        ForumThreadCard(thread) {
                            // A board can be either a novel forum or a nested
                            // topic board; ForumBoardPage detects the template.
                            navigator?.pushIfNotCurrent(
                                ForumBoardPage(thread)
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        LaunchedEffect(Unit) { model.load() }
    }
}

class ForumBoardPage(private val thread: ForumThread) : Screen {
    override val key: ScreenKey =
        "ForumBoardPage:" + EsjzoneUrls.canonicalPageKey(thread.url)
            .ifBlank { thread.id }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel { ForumBoardPageModel(authorization, thread) }
        val state by model.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            QuietBackHeader(title = thread.title, onBack = { navigator?.pop() })
            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.forum_board_empty)
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

class ForumPostPage(private val topic: ForumTopic) : Screen {
    override val key: ScreenKey =
        "ForumPostPage:" + EsjzoneUrls.canonicalPageKey(topic.url)
            .ifBlank { "${topic.boardId}-${topic.id}" }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel { ForumPostPageModel(authorization, topic) }
        val commentsModel = rememberScreenModel {
            CommentPageModel(authorization, topic.url)
        }
        val state by model.state.collectAsState()
        val postScrollState = rememberScrollState()

        Column(modifier = Modifier.fillMaxSize()) {
            QuietBackHeader(title = topic.title, onBack = { navigator?.pop() })
            when (val snapshot = state) {
                is CommunityState.Loading -> QuietLoadingState(modifier = Modifier.fillMaxSize())
                is CommunityState.Error -> QuietErrorState(
                    failure = snapshot.failure,
                    modifier = Modifier.fillMaxSize()
                )
                is CommunityState.Empty -> QuietEmptyState(
                    title = stringResource(R.string.forum_threads_empty),
                    message = stringResource(R.string.community_empty_guidance),
                    icon = Icons.Filled.Forum,
                    modifier = Modifier.fillMaxSize()
                )
                is CommunityState.Result -> Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
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

object GuestbookPage : Screen {
    private fun readResolve(): Any = GuestbookPage

    @Composable
    override fun Content() {
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel {
            CommentPageModel(authorization, EsjzoneUrls.Guestbook)
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
) : Screen {
    override val key: ScreenKey =
        "ChapterCommentsPage:" + EsjzoneUrls.canonicalPageKey(chapterUrl)
            .ifBlank { chapterName.trim() }

    @Composable
    override fun Content() {
        val authorization = LocalAuthorization.current
        val model = rememberScreenModel {
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
            .padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
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
            style = QuietEditorial.sectionTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Surface(
            shape = QuietEditorial.badgeShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ) {
            Text(
                text = stringResource(R.string.forum_board_count, boardCount),
                style = QuietEditorial.smallLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = QuietEditorial.largeShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 17.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(18.dp),
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
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = category.name,
                    style = QuietEditorial.cardTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )
                category.description?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        style = QuietEditorial.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                category.postCount?.let { count ->
                    Text(
                        text = stringResource(R.string.forum_post_count, count),
                        style = QuietEditorial.label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ForumThreadCard(thread: ForumThread, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clickable(onClick = onClick),
        shape = QuietEditorial.cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                thread.topicCount?.let {
                    Text(
                        text = stringResource(id = R.string.forum_topic_count, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                thread.replyCount?.let {
                    Text(
                        text = stringResource(id = R.string.forum_reply_count, it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                thread.lastPostDate?.let {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
        if (topics.isEmpty()) {
            QuietEmptyState(
            title = stringResource(id = R.string.forum_board_empty),
            message = stringResource(R.string.forum_board_empty_guidance),
            icon = Icons.Filled.Forum,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(topics, key = { "forum-topic-${it.boardId}-${it.id}" }) { topic ->
                ForumTopicCard(topic) { onTopicClick(topic) }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
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
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "forum-novel-info") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                shape = QuietEditorial.largeShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.forum_novel_board),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    OutlinedButton(
                        onClick = onOpenNovel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text(text = stringResource(id = R.string.forum_open_novel))
                    }
                }
            }
        }
        if (board.items.isEmpty()) {
            item(key = "forum-novel-empty") {
                QuietEmptyState(
                    title = stringResource(id = R.string.forum_board_empty),
                    message = stringResource(R.string.forum_board_empty_guidance),
                    icon = Icons.Filled.Forum,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            items(board.items, key = { "forum-topic-${it.boardId}-${it.id}" }) { topic ->
                ForumTopicCard(topic) { onTopicClick(topic) }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun ForumTopicCard(topic: ForumTopic, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clickable(onClick = onClick),
        shape = QuietEditorial.cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = topic.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val authorAndDate = listOfNotNull(topic.author, topic.createdAt)
                .joinToString(" · ")
            if (authorAndDate.isNotBlank()) {
                Text(
                    text = authorAndDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
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
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (stats.isNotBlank()) {
                        Text(
                            text = stats,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    topic.lastReplyAt?.let {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumPostCard(post: ForumPost) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = QuietEditorial.largeShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            val authorAndDate = listOfNotNull(post.author, post.createdAt)
                .joinToString(" · ")
            if (authorAndDate.isNotBlank()) {
                Text(
                    text = authorAndDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (post.contentText.isNotBlank()) {
                Text(
                    text = post.contentText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 18.dp)
                )
            }
        }
    }
}

private class ForumPageModel(
    private val authorization: Authorization
) : StateScreenModel<CommunityState<List<ForumCategory>>>(CommunityState.Loading) {
    private var loadStarted = false

    fun retry() = load()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = try {
                EsjzoneClient.getForumCategories(authorization).toCommunityState()
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
) : StateScreenModel<CommunityState<List<ForumThread>>>(CommunityState.Loading) {
    private var loadStarted = false

    fun retry() = load()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = try {
                EsjzoneClient.getForumThreads(authorization, category).toCommunityState()
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
) : StateScreenModel<CommunityState<com.breakyuna.esjzone.network.features.ForumBoardResult>>(
    CommunityState.Loading
) {
    private var loadStarted = false

    fun retry() = load()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = try {
                CommunityState.Result(EsjzoneClient.getForumBoard(authorization, thread))
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
) : StateScreenModel<CommunityState<ForumPost>>(CommunityState.Loading) {
    private var loadStarted = false

    fun retry() = load()

    fun load() {
        if (loadStarted) return
        loadStarted = true
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = try {
                CommunityState.Result(EsjzoneClient.getForumPost(authorization, topic))
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

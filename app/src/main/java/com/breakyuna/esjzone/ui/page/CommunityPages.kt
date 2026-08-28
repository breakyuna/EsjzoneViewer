package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getChapterComments
import com.breakyuna.esjzone.network.features.getForumCategories
import com.breakyuna.esjzone.network.features.getForumThreads
import com.breakyuna.esjzone.network.features.getGuestbookComments
import com.breakyuna.esjzone.network.features.CommentSubmissionNotVerifiedException
import com.breakyuna.esjzone.network.features.submitForumComment
import com.breakyuna.esjzone.novellibrary.community.ForumCategory
import com.breakyuna.esjzone.novellibrary.community.ForumThread
import com.breakyuna.esjzone.novellibrary.novel.Comment
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.util.AppLogger

object ForumPage : Screen {
    private fun readResolve(): Any = ForumPage

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val scope = rememberCoroutineScope()
        val model = rememberScreenModel { ForumPageModel(authorization, scope) }
        val state by model.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(
                title = stringResource(id = R.string.forum),
                onBack = { navigator?.pop() }
            )

            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.forum_empty)
            ) { categories ->
                val grouped = categories.groupBy { it.groupName.orEmpty() }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    grouped.forEach { (groupName, groupCategories) ->
                        if (groupName.isNotBlank()) {
                            item(key = "group-$groupName") {
                                Text(
                                    text = groupName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(
                                        start = 20.dp,
                                        end = 20.dp,
                                        top = 22.dp,
                                        bottom = 8.dp
                                    )
                                )
                            }
                        }
                        items(groupCategories, key = { "forum-category-${it.id}" }) { category ->
                            ForumCategoryCard(category) {
                                navigator?.push(ForumCategoryPage(category))
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        LaunchedEffect(currentCompositeKeyHash) { model.load() }
    }
}

class ForumCategoryPage(private val category: ForumCategory) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val scope = rememberCoroutineScope()
        val model = rememberScreenModel { ForumCategoryPageModel(authorization, scope, category) }
        val state by model.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(title = category.name, onBack = { navigator?.pop() })
            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.forum_threads_empty)
            ) { threads ->
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(threads, key = { "forum-thread-${it.categoryId}-${it.id}" }) { thread ->
                        ForumThreadCard(thread)
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }

        LaunchedEffect(currentCompositeKeyHash) { model.load() }
    }
}

object GuestbookPage : Screen {
    private fun readResolve(): Any = GuestbookPage

    @Composable
    override fun Content() {
        CommentListPage(
            title = stringResource(id = R.string.guestbook),
            chapterUrl = null
        )
    }
}

class ChapterCommentsPage(
    private val chapterName: String,
    private val chapterUrl: String
) : Screen {
    @Composable
    override fun Content() {
        CommentListPage(title = chapterName, chapterUrl = chapterUrl)
    }
}

@Composable
private fun CommentListPage(title: String, chapterUrl: String?) {
    val navigator = LocalBaseNavigator.current
    val authorization = LocalAuthorization.current
    val scope = rememberCoroutineScope()
    val model = rememberScreenModel {
        CommentPageModel(authorization, scope, chapterUrl)
    }
    val state by model.state.collectAsState()
    val isSubmitting by model.isSubmitting
    val submitError by model.submitError
    val submittedVersion by model.submittedVersion
    val lastCreatedCommentId by model.lastCreatedCommentId
    var draft by rememberSaveable(chapterUrl) { mutableStateOf("") }
    var replyToken by rememberSaveable(chapterUrl) { mutableStateOf<String?>(null) }
    var replyAuthor by rememberSaveable(chapterUrl) { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        AppBar(title = title, onBack = { navigator?.pop() })
        Box(modifier = Modifier.weight(1f)) {
            CommunityStateContent(
                state = state,
                emptyText = stringResource(id = R.string.comments_empty)
            ) { comments ->
                CommentList(
                    comments = comments,
                    lastCreatedCommentId = lastCreatedCommentId,
                    onReply = if (chapterUrl == null) null else { comment ->
                        replyToken = comment.replyToken
                        replyAuthor = comment.authorName
                        model.clearSubmitError()
                    }
                )
            }
        }
        if (chapterUrl != null && state is CommunityState.Result) {
            CommentComposer(
                draft = draft,
                replyAuthor = replyAuthor,
                isSubmitting = isSubmitting,
                error = submitError,
                onDraftChange = {
                    draft = it
                    model.clearSubmitError()
                },
                onCancelReply = {
                    replyToken = null
                    replyAuthor = null
                },
                onRefresh = {
                    model.clearSubmitError()
                    model.load()
                },
                onSubmit = { model.submit(draft, replyToken) }
            )
        }
    }

    LaunchedEffect(currentCompositeKeyHash) { model.load() }
    LaunchedEffect(submittedVersion) {
        if (submittedVersion > 0) {
            draft = ""
            replyToken = null
            replyAuthor = null
        }
    }
}

@Composable
private fun CommentList(
    comments: List<Comment>,
    lastCreatedCommentId: String?,
    onReply: ((Comment) -> Unit)?
) {
    if (comments.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.comments_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val pages = remember(comments) { comments.map { it.pageGroup }.distinct().sorted() }
    var selectedPageIndex by rememberSaveable(comments) { mutableIntStateOf(0) }
    val normalizedPageIndex = selectedPageIndex.coerceIn(
        0,
        (pages.size - 1).coerceAtLeast(0)
    )
    val selectedPage = pages.getOrNull(normalizedPageIndex)
    val visibleComments = remember(comments, selectedPage) {
        if (selectedPage == null) emptyList()
        else comments.filter { it.pageGroup == selectedPage }
    }

    LaunchedEffect(lastCreatedCommentId, pages) {
        val createdPage = comments.firstOrNull { it.id == lastCreatedCommentId }?.pageGroup
        val createdPageIndex = pages.indexOf(createdPage)
        if (createdPageIndex >= 0) selectedPageIndex = createdPageIndex
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (pages.size > 1) {
            CommentPager(
                page = normalizedPageIndex + 1,
                totalPages = pages.size,
                onPrevious = { selectedPageIndex = (selectedPageIndex - 1).coerceAtLeast(0) },
                onNext = {
                    selectedPageIndex = (selectedPageIndex + 1).coerceAtMost(pages.lastIndex)
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(visibleComments, key = { "comment-${it.parentPostId}-${it.id}" }) { comment ->
                val replyAction = onReply
                CommentCard(
                    comment = comment,
                    onReply = if (comment.replyToken != null && replyAction != null) {
                        { replyAction(comment) }
                    } else {
                        null
                    }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CommentComposer(
    draft: String,
    replyAuthor: String?,
    isSubmitting: Boolean,
    error: CommentSubmitError?,
    onDraftChange: (String) -> Unit,
    onCancelReply: () -> Unit,
    onRefresh: () -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (replyAuthor != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.comment_replying_to, replyAuthor),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onCancelReply, enabled = !isSubmitting) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(id = R.string.comment_cancel_reply)
                        )
                    }
                }
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
                placeholder = { Text(text = stringResource(id = R.string.comment_hint)) },
                minLines = 2,
                maxLines = 5
            )
            error?.let {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = it.messageResource),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 6.dp)
                    )
                    if (it == CommentSubmitError.NOT_VERIFIED) {
                        TextButton(onClick = onRefresh, enabled = !isSubmitting) {
                            Text(text = stringResource(id = R.string.comment_refresh))
                        }
                    }
                }
            }
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting && draft.isNotBlank(),
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
            ) {
                Icon(imageVector = Icons.Filled.Send, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        id = if (isSubmitting) {
                            R.string.comment_submitting
                        } else {
                            R.string.comment_send
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun CommentPager(
    page: Int,
    totalPages: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(onClick = onPrevious, enabled = page > 1) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
        }
        Text(
            text = stringResource(id = R.string.page_indicator, page, totalPages),
            style = MaterialTheme.typography.labelLarge
        )
        FilledTonalIconButton(onClick = onNext, enabled = page < totalPages) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
        }
    }
}

@Composable
private fun CommentCard(comment: Comment, onReply: (() -> Unit)?) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = comment.authorName ?: stringResource(id = R.string.anonymous_user),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                comment.floor?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            comment.createdAt?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = comment.contentText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp)
            )
            if (onReply != null) {
                TextButton(
                    onClick = onReply,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(text = stringResource(id = R.string.comment_reply))
                }
            }
        }
    }
}

@Composable
private fun ForumCategoryCard(category: ForumCategory, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Forum,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                category.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            category.postCount?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ForumThreadCard(thread: ForumThread) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        shape = RoundedCornerShape(18.dp)
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
private fun <T> CommunityStateContent(
    state: CommunityState<T>,
    emptyText: String,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is CommunityState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator() }

        is CommunityState.Error -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(id = R.string.community_load_failed),
                color = MaterialTheme.colorScheme.error
            )
        }

        is CommunityState.Empty -> Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        is CommunityState.Result -> content(state.data)
    }
}

private sealed class CommunityState<out T> {
    data object Loading : CommunityState<Nothing>()
    data object Empty : CommunityState<Nothing>()
    data object Error : CommunityState<Nothing>()
    data class Result<T>(val data: T) : CommunityState<T>()
}

private class ForumPageModel(
    private val authorization: Authorization,
    private val scope: CoroutineScope
) : StateScreenModel<CommunityState<List<ForumCategory>>>(CommunityState.Loading) {
    fun load() {
        scope.launch(Dispatchers.IO) {
            mutableState.value = try {
                EsjzoneClient.getForumCategories(authorization).toCommunityState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ForumPageModel", "Failed to load forum categories", error)
                CommunityState.Error
            }
        }
    }
}

private class ForumCategoryPageModel(
    private val authorization: Authorization,
    private val scope: CoroutineScope,
    private val category: ForumCategory
) : StateScreenModel<CommunityState<List<ForumThread>>>(CommunityState.Loading) {
    fun load() {
        scope.launch(Dispatchers.IO) {
            mutableState.value = try {
                EsjzoneClient.getForumThreads(authorization, category).toCommunityState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("ForumCategoryPageModel", "Failed to load forum category ${category.id}", error)
                CommunityState.Error
            }
        }
    }
}

private class CommentPageModel(
    private val authorization: Authorization,
    private val scope: CoroutineScope,
    private val chapterUrl: String?
) : StateScreenModel<CommunityState<List<Comment>>>(CommunityState.Loading) {
    val isSubmitting = mutableStateOf(false)
    val submitError = mutableStateOf<CommentSubmitError?>(null)
    val submittedVersion = mutableIntStateOf(0)
    val lastCreatedCommentId = mutableStateOf<String?>(null)

    fun load() {
        scope.launch(Dispatchers.IO) {
            mutableState.value = try {
                val comments = if (chapterUrl == null) {
                    EsjzoneClient.getGuestbookComments(authorization)
                } else {
                    EsjzoneClient.getChapterComments(authorization, chapterUrl)
                }
                CommunityState.Result(comments)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("CommentPageModel", "Failed to load comments", error)
                CommunityState.Error
            }
        }
    }

    fun clearSubmitError() {
        submitError.value = null
    }

    fun submit(content: String, replyToken: String?) {
        val targetUrl = chapterUrl ?: return
        if (content.isBlank()) {
            submitError.value = CommentSubmitError.EMPTY
            return
        }
        if (isSubmitting.value) return

        scope.launch(Dispatchers.IO) {
            isSubmitting.value = true
            submitError.value = null
            try {
                val submission = EsjzoneClient.submitForumComment(
                    authorization = authorization,
                    pageUrl = targetUrl,
                    content = content,
                    replyToken = replyToken
                )
                mutableState.value = CommunityState.Result(submission.comments)
                lastCreatedCommentId.value = submission.createdComment.id
                submittedVersion.intValue += 1
            } catch (error: CancellationException) {
                throw error
            } catch (error: CommentSubmissionNotVerifiedException) {
                mutableState.value = CommunityState.Result(error.comments)
                submitError.value = CommentSubmitError.NOT_VERIFIED
                AppLogger.w("CommentPageModel", "Comment write completed but was not verified")
            } catch (error: Exception) {
                submitError.value = CommentSubmitError.FAILED
                AppLogger.e("CommentPageModel", "Failed to submit comment", error)
            } finally {
                isSubmitting.value = false
            }
        }
    }
}

private enum class CommentSubmitError(val messageResource: Int) {
    EMPTY(R.string.comment_empty_error),
    FAILED(R.string.comment_submit_failed),
    NOT_VERIFIED(R.string.comment_submit_unverified)
}

private fun <T> List<T>.toCommunityState(): CommunityState<List<T>> =
    if (isEmpty()) CommunityState.Empty else CommunityState.Result(this)

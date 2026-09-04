package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.breakyuna.esjzone.MainActivity
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.CommentSubmissionNotVerifiedException
import com.breakyuna.esjzone.network.features.getPageComments
import com.breakyuna.esjzone.network.features.submitForumComment
import com.breakyuna.esjzone.novellibrary.novel.COMMENT_PAGE_SIZE
import com.breakyuna.esjzone.novellibrary.novel.Comment
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietErrorState
import com.breakyuna.esjzone.ui.component.QuietLoadingState
import com.breakyuna.esjzone.ui.component.QuietSectionHeader
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal sealed class CommunityState<out T> {
    data object Loading : CommunityState<Nothing>()
    data object Empty : CommunityState<Nothing>()
    data class Error(val failure: LoadFailureKind) : CommunityState<Nothing>()
    data class Result<T>(val data: T) : CommunityState<T>()
}

@Composable
internal fun <T> CommunityStateContent(
    state: CommunityState<T>,
    emptyText: String,
    onRetry: (() -> Unit)? = null,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is CommunityState.Loading -> QuietLoadingState(modifier = Modifier.fillMaxSize())

        is CommunityState.Error -> QuietErrorState(
            failure = state.failure,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize()
        )

        is CommunityState.Empty -> QuietEmptyState(
            title = emptyText,
            message = stringResource(R.string.community_empty_guidance),
            icon = androidx.compose.material.icons.Icons.Filled.Forum,
            modifier = Modifier.fillMaxSize()
        )

        is CommunityState.Result -> content(state.data)
    }
}

/** Hosts the same comments UI for chapter pages, the guestbook, and novel details. */
@Composable
internal fun CommentListPage(
    title: String,
    model: CommentPageModel
) {
    val navigator = LocalBaseNavigator.current

    Column(modifier = Modifier.fillMaxSize()) {
        com.breakyuna.esjzone.ui.component.AppBar(
            title = title,
            onBack = { navigator?.pop() }
        )
        CommentSectionHost(
            model = model,
            showHeader = false,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

@Composable
internal fun CommentSectionHost(
    model: CommentPageModel,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    val scrollState = rememberScrollState()
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            CommentSectionContent(
                model = model,
                showHeader = showHeader,
                modifier = Modifier.fillMaxWidth(),
                scrollState = scrollState
            )
        }
        CommentComposerHost(model = model)
    }
}

/**
 * Renders only the scrollable comments area.  Pages with a larger scroll
 * container (novel and forum posts) use this together with [CommentComposerHost]
 * so the composer remains outside that container.
 */
@Composable
internal fun CommentSectionContent(
    model: CommentPageModel,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    scrollState: androidx.compose.foundation.ScrollState? = null
) {
    val state by model.state.collectAsState()
    val lastCreatedCommentId by model.lastCreatedCommentId
    val anonymousName = stringResource(R.string.anonymous_user)

    fun selectReply(comment: Comment) {
        model.replyToken.value = comment.replyToken?.trim()?.takeIf { it.isNotBlank() }
        model.replyAuthor.value = comment.authorName?.trim()?.takeIf { it.isNotBlank() }
            ?: anonymousName
        model.clearSubmitError()
    }

    when (val snapshot = state) {
        is CommunityState.Loading -> QuietLoadingState(modifier = modifier)

        is CommunityState.Error -> QuietErrorState(
            failure = snapshot.failure,
            onRetry = {
                model.clearSubmitError()
                model.load(forceRefresh = true)
            },
            modifier = modifier
        )

        is CommunityState.Empty -> CommentSection(
            comments = emptyList(),
            lastCreatedCommentId = lastCreatedCommentId,
            showHeader = showHeader,
            modifier = modifier,
            onReply = ::selectReply,
            scrollState = scrollState,
        )

        is CommunityState.Result -> CommentSection(
            comments = snapshot.data,
            lastCreatedCommentId = lastCreatedCommentId,
            showHeader = showHeader,
            modifier = modifier,
            onReply = ::selectReply,
            scrollState = scrollState,
        )
    }

    LaunchedEffect(model) { model.load() }
}

/** The persistent composer is deliberately a sibling of the comments scroller. */
@Composable
internal fun CommentComposerHost(
    model: CommentPageModel,
    modifier: Modifier = Modifier
) {
    var savedDraft by rememberSaveable(model.pageUrl) { mutableStateOf("") }
    var savedReplyToken by rememberSaveable(model.pageUrl) { mutableStateOf<String?>(null) }
    var savedReplyAuthor by rememberSaveable(model.pageUrl) { mutableStateOf<String?>(null) }
    val anonymousLabel = stringResource(id = R.string.anonymous_user)
    LaunchedEffect(model) {
        // Restore the composer after an activity/process recreation, then keep
        // the saveable mirror current while the screen model remains the
        // single source shared by the comments list and this host.
        if (model.draft.value.isBlank() && savedDraft.isNotBlank()) {
            model.draft.value = savedDraft
        }
        if (model.replyToken.value == null && savedReplyToken != null) {
            model.replyToken.value = savedReplyToken
            model.replyAuthor.value = savedReplyAuthor
        }
        snapshotFlow {
            Triple(model.draft.value, model.replyToken.value, model.replyAuthor.value)
        }.collect { (draft, token, author) ->
            savedDraft = draft
            savedReplyToken = token
            savedReplyAuthor = author
        }
    }
    val draft by model.draft
    val replyToken by model.replyToken
    val rawReplyAuthor by model.replyAuthor
    // A reply token is authoritative context even when the source comment
    // has no author field. Keep the context row and cancel affordance visible
    // instead of silently turning an anonymous reply into a new comment.
    val replyAuthor = if (replyToken != null) {
        rawReplyAuthor?.trim()?.takeIf { it.isNotEmpty() } ?: anonymousLabel
    } else {
        null
    }
    val isSubmitting by model.isSubmitting
    val submitError by model.submitError
    CommentComposer(
        draft = draft,
        replyAuthor = replyAuthor,
        isSubmitting = isSubmitting,
        error = submitError,
        onDraftChange = {
            model.draft.value = it
            model.clearSubmitError()
        },
        onCancelReply = {
            model.replyToken.value = null
            model.replyAuthor.value = null
        },
        onRefresh = {
            model.clearSubmitError()
            model.load(forceRefresh = true)
        },
        onSubmit = { model.submit(draft, model.replyToken.value) },
        modifier = modifier
    )
}

@Composable
private fun CommentSection(
    comments: List<Comment>,
    lastCreatedCommentId: String?,
    showHeader: Boolean,
    modifier: Modifier,
    onReply: (Comment) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState? = null
) {
    val pages = remember(comments) { comments.chunked(COMMENT_PAGE_SIZE) }
    var selectedPageIndex by rememberSaveable { mutableIntStateOf(0) }
    val safePageIndex = selectedPageIndex.coerceIn(
        0,
        (pages.size - 1).coerceAtLeast(0)
    )

    LaunchedEffect(comments, lastCreatedCommentId) {
        selectedPageIndex = if (lastCreatedCommentId == null) {
            selectedPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        } else {
            val createdIndex = comments.indexOfFirst { it.id == lastCreatedCommentId }
            if (createdIndex >= 0) createdIndex / COMMENT_PAGE_SIZE else safePageIndex
        }
    }

    LaunchedEffect(safePageIndex) {
        scrollState?.animateScrollTo(0)
    }

    Column(modifier = modifier) {
        if (showHeader) {
            QuietSectionHeader(
                title = stringResource(id = R.string.comments),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (pages.isEmpty()) {
            QuietEmptyState(
                title = stringResource(id = R.string.comments_empty),
                message = stringResource(id = R.string.community_empty_guidance),
                icon = Icons.Filled.Person
            )
        } else {
            CommentPager(
                page = safePageIndex + 1,
                totalPages = pages.size,
                onFirst = { selectedPageIndex = 0 },
                onPrevious = {
                    selectedPageIndex = (safePageIndex - 1).coerceAtLeast(0)
                },
                onNext = {
                    selectedPageIndex =
                        (safePageIndex + 1).coerceAtMost(pages.lastIndex)
                },
                onLast = { selectedPageIndex = pages.lastIndex }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            pages[safePageIndex].forEach { comment ->
                CommentCard(
                    comment = comment,
                    onReply = if (!comment.replyToken.isNullOrBlank()) {
                        { onReply(comment) }
                    } else {
                        null
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
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
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
        shape = QuietEditorial.largeShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 12.dp)
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
                placeholder = {
                    Text(
                        text = stringResource(id = R.string.comment_hint),
                        style = QuietEditorial.body
                    )
                },
                minLines = 2,
                maxLines = 5,
                shape = QuietEditorial.controlShape
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
    onFirst: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLast: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            FilledTonalIconButton(onClick = onFirst, enabled = page > 1) {
                Icon(Icons.Filled.FirstPage, contentDescription = stringResource(R.string.comment_first_page))
            }
            FilledTonalIconButton(onClick = onPrevious, enabled = page > 1) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.comment_previous_page)
                )
            }
        }
        Text(
            text = stringResource(id = R.string.page_indicator, page, totalPages),
            style = MaterialTheme.typography.labelLarge
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            FilledTonalIconButton(onClick = onNext, enabled = page < totalPages) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.comment_next_page)
                )
            }
            FilledTonalIconButton(onClick = onLast, enabled = page < totalPages) {
                Icon(Icons.Filled.LastPage, contentDescription = stringResource(R.string.comment_last_page))
            }
        }
    }
}

@Composable
private fun CommentCard(comment: Comment, onReply: (() -> Unit)?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            CommentAvatar(comment)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
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
                Text(
                    text = comment.createdAt
                        ?: stringResource(id = R.string.comment_time_unknown),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                comment.quotedContentText?.let { quotedText ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = quotedText,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
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
}

@Composable
private fun CommentAvatar(comment: Comment) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val avatarUrl = comment.authorAvatarUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(EsjzoneUrls::resolve)
            ?.takeUnless { it.startsWith("data:", ignoreCase = true) }
        if (avatarUrl.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = comment.authorName,
                imageLoader = MainActivity.imageLoader,
                loading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                },
                error = {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal class CommentPageModel(
    private val authorization: Authorization,
    internal val pageUrl: String
) : StateScreenModel<CommunityState<List<Comment>>>(CommunityState.Loading) {
    private var loadJob: Job? = null
    private var loadStarted = false

    val isSubmitting = mutableStateOf(false)
    val submitError = mutableStateOf<CommentSubmitError?>(null)
    val lastCreatedCommentId = mutableStateOf<String?>(null)
    // Keep composer state in the screen model so it survives lazy item
    // disposal while the user scrolls through a novel or forum post.
    val draft = mutableStateOf("")
    val replyToken = mutableStateOf<String?>(null)
    val replyAuthor = mutableStateOf<String?>(null)

    fun load(forceRefresh: Boolean = false) {
        if (!forceRefresh && loadStarted) return
        loadStarted = true
        loadJob?.cancel()
        loadJob = screenModelScope.launch(Dispatchers.IO) {
            try {
                val comments = EsjzoneClient.getPageComments(
                    authorization,
                    pageUrl,
                    forceRefresh = forceRefresh
                )
                ensureActive()
                mutableState.value = comments.toCommunityState()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e("CommentPageModel", "Failed to load comments", error)
                mutableState.value = CommunityState.Error(error.loadFailureKind())
                loadStarted = false
            }
        }
    }

    fun clearSubmitError() {
        submitError.value = null
    }

    fun submit(content: String, replyToken: String?) {
        val submitted = content.trim()
        if (submitted.isBlank()) {
            submitError.value = CommentSubmitError.EMPTY
            return
        }
        if (isSubmitting.value) return

        isSubmitting.value = true
        submitError.value = null
        screenModelScope.launch(Dispatchers.IO) {
            try {
                val submission = EsjzoneClient.submitForumComment(
                    authorization = authorization,
                    pageUrl = pageUrl,
                    content = submitted,
                    replyToken = replyToken
                )
                ensureActive()
                mutableState.value = CommunityState.Result(submission.comments)
                lastCreatedCommentId.value = submission.createdComment.id
                draft.value = ""
                this@CommentPageModel.replyToken.value = null
                this@CommentPageModel.replyAuthor.value = null
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

internal enum class CommentSubmitError(val messageResource: Int) {
    EMPTY(R.string.comment_empty_error),
    FAILED(R.string.comment_submit_failed),
    NOT_VERIFIED(R.string.comment_submit_unverified)
}

private fun <T> List<T>.toCommunityState(): CommunityState<List<T>> =
    if (isEmpty()) CommunityState.Empty else CommunityState.Result(this)

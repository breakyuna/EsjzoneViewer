@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.page

import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.app.cacheUserProfile

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.CommentSubmissionNotVerifiedException
import com.breakyuna.esjzone.network.features.CommentSubmissionTimeoutException
import com.breakyuna.esjzone.network.features.ForumReplyBusinessException
import com.breakyuna.esjzone.network.features.getPageComments
import com.breakyuna.esjzone.network.features.getUserProfile
import com.breakyuna.esjzone.network.features.submitForumComment
import com.breakyuna.esjzone.novellibrary.novel.COMMENT_PAGE_SIZE
import com.breakyuna.esjzone.novellibrary.novel.Comment
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.designsystem.AppAvatarImage
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppElevation
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppSyncStatusDot
import com.breakyuna.esjzone.ui.designsystem.AppTouchTarget
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.appStateColors
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.ErrorState
import com.breakyuna.esjzone.ui.product.LoadingSkeleton
import com.breakyuna.esjzone.ui.product.OfflineState
import com.breakyuna.esjzone.ui.product.ProductComponentContentTypes
import com.breakyuna.esjzone.ui.product.stableProductKey
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityTopBar(
    title: String,
    onBack: () -> Unit,
    titleIndicator: (@Composable () -> Unit)? = null
) {
    Column {
        CenterAlignedTopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                ) {
                    Text(
                        title,
                        style = AppTypography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    titleIndicator?.invoke()
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.size(AppTouchTarget.minimum)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.reader_back)
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun CommunitySyncStatusIndicator(
    syncing: Boolean,
    isSyncSuccess: Boolean,
    isSyncFailed: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    @StringRes runningRes: Int,
    @StringRes successRes: Int,
    @StringRes failedRes: Int,
    @StringRes detailRes: Int,
    @StringRes idleRes: Int = R.string.community_sync_idle
) {
    val statusLabelRes = when {
        syncing -> runningRes
        isSyncFailed -> failedRes
        isSyncSuccess -> successRes
        else -> idleRes
    }
    val statusDetailRes = when {
        syncing -> runningRes
        isSyncFailed -> failedRes
        isSyncSuccess -> detailRes
        else -> idleRes
    }
    Box {
        Box(
            modifier = Modifier
                .size(AppSpacing.xl)
                .clip(CircleShape)
                .clickable(
                    onClickLabel = stringResource(statusLabelRes)
                ) { onExpandedChange(true) },
            contentAlignment = Alignment.Center
        ) {
            AppSyncStatusDot(syncing = syncing, isSuccess = isSyncSuccess)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.widthIn(min = 200.dp, max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    AppSyncStatusDot(syncing = syncing, isSuccess = isSyncSuccess)
                    Text(
                        text = stringResource(statusLabelRes),
                        style = AppTypography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stringResource(statusDetailRes),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun CommunitySyncStatusIndicator(
    state: CommunityState<*>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    @StringRes runningRes: Int,
    @StringRes successRes: Int,
    @StringRes failedRes: Int,
    @StringRes detailRes: Int,
    @StringRes idleRes: Int = R.string.community_sync_idle
) {
    val result = state as? CommunityState.Result
    val empty = state as? CommunityState.Empty
    val syncing = state is CommunityState.Loading || result?.isSyncing == true
    val failed = state is CommunityState.Error || result?.syncFailure != null
    val isSuccess = !syncing && !failed && (result?.isSyncSuccess == true || empty?.isSyncSuccess == true)
    CommunitySyncStatusIndicator(
        syncing = syncing,
        isSyncSuccess = isSuccess,
        isSyncFailed = failed,
        expanded = expanded,
        onExpandedChange = onExpandedChange,
        runningRes = runningRes,
        successRes = successRes,
        failedRes = failedRes,
        detailRes = detailRes,
        idleRes = idleRes
    )
}

@Composable
private fun CommentSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = AppTypography.titleMedium,
        modifier = modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg)
    )
}

internal sealed class CommunityState<out T> {
    data object Loading : CommunityState<Nothing>()
    data class Empty(val isSyncSuccess: Boolean = false) : CommunityState<Nothing>()
    data class Error(val failure: LoadFailureKind) : CommunityState<Nothing>()
    data class Result<T>(
        val data: T,
        val isSyncSuccess: Boolean = false,
        val isSyncing: Boolean = false,
        val syncFailure: LoadFailureKind? = null
    ) : CommunityState<T>()
}

@Composable
internal fun <T> CommunityStateContent(
    state: CommunityState<T>,
    emptyText: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    when (state) {
        is CommunityState.Loading -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LoadingSkeleton(modifier = Modifier.fillMaxWidth())
        }

        is CommunityState.Error -> if (state.failure == LoadFailureKind.NETWORK) {
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                OfflineState(
                    modifier = Modifier.fillMaxWidth(),
                    onRetry = onRetry
                )
            }
        } else {
            Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ErrorState(
                    title = stringResource(R.string.community_load_failed),
                    message = stringResource(R.string.community_empty_guidance),
                    modifier = Modifier.fillMaxWidth(),
                    onRetry = onRetry
                )
            }
        }

        is CommunityState.Empty -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                title = emptyText,
                message = stringResource(R.string.community_empty_guidance),
                modifier = Modifier.fillMaxWidth()
            )
        }

        is CommunityState.Result -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            content(state.data)
        }
    }
}

/** Hosts the same comments UI for chapter pages, the guestbook, and novel details. */
@Composable
internal fun CommentListPage(
    title: String,
    model: CommentPageModel,
    titleIndicator: (@Composable () -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    refreshing: Boolean = false
) {
    val navigator = LocalBaseNavigator.current
    val state by model.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CommunityTopBar(
            title = title,
            onBack = { navigator?.pop() },
            titleIndicator = titleIndicator
        )
        PullToRefreshBox(
            isRefreshing = refreshing || state is CommunityState.Loading,
            onRefresh = { (onRefresh ?: { model.load(forceRefresh = true) })() },
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
        CommentSectionHost(
            model = model,
            showHeader = false,
            modifier = Modifier
                .fillMaxSize()
        )
        }
    }
}

@Composable
internal fun CommentSectionHost(
    model: CommentPageModel,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .widthIn(max = 960.dp)
            .padding(horizontal = AppSpacing.lg)
    ) {
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
        is CommunityState.Loading -> LoadingSkeleton(modifier = modifier)

        is CommunityState.Error -> if (snapshot.failure == LoadFailureKind.NETWORK) {
            OfflineState(
                modifier = modifier,
                onRetry = {
                    model.clearSubmitError()
                    model.load(forceRefresh = true)
                }
            )
        } else {
            ErrorState(
                title = stringResource(R.string.community_load_failed),
                message = stringResource(R.string.community_empty_guidance),
                modifier = modifier,
                onRetry = {
                    model.clearSubmitError()
                    model.load(forceRefresh = true)
                }
            )
        }

        is CommunityState.Empty -> key("comment-list", model.pageUrl) {
            CommentSection(
                pageUrl = model.pageUrl,
                comments = emptyList(),
                lastCreatedCommentId = lastCreatedCommentId,
                showHeader = showHeader,
                modifier = modifier,
                onReply = ::selectReply,
                scrollState = scrollState,
            )
        }

        is CommunityState.Result -> key("comment-list", model.pageUrl) {
            CommentSection(
                pageUrl = model.pageUrl,
                comments = snapshot.data,
                lastCreatedCommentId = lastCreatedCommentId,
                showHeader = showHeader,
                modifier = modifier,
                onReply = ::selectReply,
                scrollState = scrollState,
            )
        }
    }

    LaunchedEffect(model) { model.load() }
}

/** The persistent composer is deliberately a sibling of the comments scroller. */
@Composable
internal fun CommentComposerHost(
    model: CommentPageModel,
    modifier: Modifier = Modifier,
    onHeightChanged: ((Int) -> Unit)? = null
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
        // Keep system insets outside the painted composer surface.
        // Combining navigationBars and IME insets using union (taking the max
        // of both) ensures the composer sits above the navigation bar when the
        // keyboard is closed, and sits flush directly above the keyboard when
        // the keyboard opens, eliminating additive double-padding (navigation
        // bar height + keyboard height) that causes an extra blank gap above
        // the keyboard.
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .onSizeChanged { onHeightChanged?.invoke(it.height) }
    )
}

@Composable
private fun CommentSection(
    pageUrl: String,
    comments: List<Comment>,
    lastCreatedCommentId: String?,
    showHeader: Boolean,
    modifier: Modifier,
    onReply: (Comment) -> Unit,
    scrollState: androidx.compose.foundation.ScrollState? = null
) {
    val pages = remember(comments) { comments.chunked(COMMENT_PAGE_SIZE) }
    // LoadingSkeleton owns animated Float state. Keep pagination in an explicit
    // keyed group and registry entry so a Loading -> Result recomposition can
    // never restore that animation state as MutableIntState.
    var selectedPageIndex by rememberSaveable("comment-page-index:$pageUrl") {
        mutableIntStateOf(0)
    }
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
        if (scrollState != null && scrollState.value != 0) {
            scrollState.animateScrollTo(0)
        }
    }

    Column(modifier = modifier) {
        if (showHeader) {
            CommentSectionHeader(
                title = stringResource(id = R.string.comments),
                modifier = Modifier.padding(top = AppSpacing.sm)
            )
        }

        if (pages.isEmpty()) {
            EmptyState(
                title = stringResource(id = R.string.comments_empty),
                message = stringResource(id = R.string.community_empty_guidance),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            val pager = @Composable {
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
            }
            pager()
            pages[safePageIndex].forEachIndexed { index, comment ->
                // A malformed or legacy page can contain blank/repeated DOM
                // ids. Include the page-local position so every rendered item
                // still has a non-blank, deterministic Compose identity.
                key(commentRenderKey(comment, index)) {
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
            pager()
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Produces a page-local key for comment rendering. The server id is preferred,
 * while parent id and index protect against blank or duplicated ids observed in
 * legacy/community markup.
 */
internal fun commentRenderKey(comment: Comment, index: Int): String {
    val parent = comment.parentPostId.trim().ifBlank { "unknown-parent" }
    val id = comment.id.trim().ifBlank { "unknown-comment" }
    return stableProductKey("$parent:$id:$index", ProductComponentContentTypes.Comment)
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
            .wrapContentHeight(),
        color = appStateColors().containerRaised,
        tonalElevation = 0.dp,
        shadowElevation = AppElevation.raised,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (replyAuthor != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.compact,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .padding(start = 10.dp, end = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(id = R.string.comment_replying_to, replyAuthor),
                            style = AppTypography.labelMedium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onCancelReply,
                            enabled = !isSubmitting,
                            modifier = Modifier.size(AppTouchTarget.minimum)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(id = R.string.comment_cancel_reply),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AppTouchTarget.minimum),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(
                            min = AppTouchTarget.minimum,
                            max = AppTouchTarget.minimum
                        ),
                    enabled = !isSubmitting,
                    singleLine = true,
                    minLines = 1,
                    maxLines = 1,
                    textStyle = AppTypography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(
                                    min = AppTouchTarget.minimum,
                                    max = AppTouchTarget.minimum
                                )
                                .clip(AppShapes.standard)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = AppSpacing.md),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (draft.isBlank()) {
                                Text(
                                    text = stringResource(id = R.string.comment_hint),
                                    style = AppTypography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                IconButton(
                    onClick = { },
                    enabled = !isSubmitting,
                    modifier = Modifier.size(AppTouchTarget.minimum)
                ) {
                    Icon(
                        imageVector = Icons.Filled.EmojiEmotions,
                        contentDescription = stringResource(id = R.string.comment_emoji),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Button(
                    onClick = onSubmit,
                    enabled = !isSubmitting && draft.isNotBlank() && (error == null || error == CommentSubmitError.EMPTY),
                    modifier = Modifier.size(AppTouchTarget.minimum),
                    shape = AppShapes.pill,
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Send,
                            contentDescription = stringResource(id = R.string.comment_send),
                            modifier = Modifier.size(23.dp)
                        )
                    }
                }
            }
            error?.let { err ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text(
                        text = err.message(context = LocalContext.current),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (err != CommentSubmitError.EMPTY) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(id = R.string.comment_check_before_retry_hint),
                                style = AppTypography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            )
                            TextButton(
                                onClick = onRefresh,
                                enabled = !isSubmitting,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(id = R.string.comment_action_refresh),
                                    style = AppTypography.labelMedium
                                )
                            }
                        }
                    }
                }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.sm),
        shape = AppShapes.standard,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = AppElevation.raised
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
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
            Surface(
                shape = AppShapes.pill,
                color = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = stringResource(id = R.string.page_indicator, page, totalPages),
                    style = AppTypography.labelLarge,
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                )
            }
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
}

@Composable
private fun CommentCard(comment: Comment, onReply: (() -> Unit)?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xs),
        shape = AppShapes.standard,
        color = appStateColors().containerRaised,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = AppElevation.raised
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                CommentAvatar(comment)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comment.authorName
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: stringResource(id = R.string.anonymous_user),
                        style = AppTypography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = comment.createdAt
                            ?.trim()
                            ?.takeIf { it.isNotBlank() }
                            ?: stringResource(id = R.string.comment_time_unknown),
                        style = AppTypography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                comment.floor
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { floor ->
                        Surface(
                            shape = AppShapes.compact,
                            color = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = floor,
                                style = AppTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
            }

            comment.quotedContentText
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { quotedText ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = AppShapes.standard
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .heightIn(min = 54.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .fillMaxHeight()
                                    .clip(AppShapes.compact)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                            )
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FormatQuote,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = quotedText,
                                    style = AppTypography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

            Text(
                text = comment.contentText,
                style = AppTypography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp)
            )

            if (onReply != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(top = AppSpacing.md),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                TextButton(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = AppSpacing.xs)
                        .heightIn(min = AppTouchTarget.minimum),
                    onClick = onReply
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.xs))
                    Text(
                        text = stringResource(id = R.string.comment_reply),
                        style = AppTypography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentAvatar(comment: Comment) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        val avatarUrl = comment.authorAvatarUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(EsjzoneUrls::resolve)
            ?.takeUnless { it.startsWith("data:", ignoreCase = true) }
        if (avatarUrl.isNullOrBlank()) {
            Text(
                text = comment.authorName
                    ?.trim()
                    ?.firstOrNull()
                    ?.uppercaseChar()
                    ?.toString()
                    ?: "?",
                style = AppTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            AppAvatarImage(
                model = avatarUrl,
                contentDescription = comment.authorName,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal class CommentPageModel(
    private val authorization: Authorization,
    internal val pageUrl: String
) : AppStateViewModel<CommunityState<List<Comment>>>(CommunityState.Loading) {
    private var loadJob: Job? = null
    private var loadStarted = false
    @Volatile private var loadGeneration = 0L

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
        val generation = ++loadGeneration
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            val currentResult = mutableState.value as? CommunityState.Result
            val cachedComments = if (!forceRefresh && currentResult == null) {
                runCatching {
                    PresentationAccess.client.getPageComments(
                        authorization,
                        pageUrl,
                        forceRefresh = false
                    )
                }.getOrNull()
            } else null

            if (cachedComments != null && cachedComments.isNotEmpty()) {
                mutableState.value = CommunityState.Result(
                    data = cachedComments,
                    isSyncSuccess = false,
                    isSyncing = true
                )
            } else if (currentResult != null) {
                mutableState.value = currentResult.copy(isSyncing = true, isSyncSuccess = false, syncFailure = null)
            } else {
                mutableState.value = CommunityState.Loading
            }

            try {
                val comments = PresentationAccess.client.getPageComments(
                    authorization,
                    pageUrl,
                    forceRefresh = true
                )
                ensureActive()
                mutableState.value = if (comments.isEmpty()) {
                    CommunityState.Empty(isSyncSuccess = true)
                } else {
                    CommunityState.Result(
                        data = comments,
                        isSyncSuccess = true,
                        isSyncing = false
                    )
                }
            } catch (error: CancellationException) {
                if (generation != loadGeneration) throw error
                val existing = mutableState.value as? CommunityState.Result
                if (existing != null) {
                    mutableState.value = existing.copy(
                        isSyncing = false,
                        isSyncSuccess = false,
                        syncFailure = LoadFailureKind.NETWORK
                    )
                } else {
                    mutableState.value = CommunityState.Error(LoadFailureKind.NETWORK)
                }
                throw error
            } catch (error: Exception) {
                if (generation != loadGeneration) return@launch
                AppLogger.e("CommentPageModel", "Failed to load comments", error)
                val existing = mutableState.value as? CommunityState.Result
                if (existing != null) {
                    mutableState.value = existing.copy(
                        isSyncing = false,
                        isSyncSuccess = false,
                        syncFailure = error.loadFailureKind()
                    )
                } else {
                    mutableState.value = CommunityState.Error(error.loadFailureKind())
                }
            } finally {
                if (generation == loadGeneration) loadStarted = false
            }
        }
    }

    private var lastSubmittedContent: String? = null
    private var lastSubmitAttemptTime: Long = 0L

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

        // Deduplication protection: If the exact same content is resubmitted within 10s after an error,
        // prevent redundant network calls to avoid accidental duplicate comments.
        if (submitted == lastSubmittedContent &&
            System.currentTimeMillis() - lastSubmitAttemptTime < 10_000L &&
            submitError.value != null && submitError.value != CommentSubmitError.EMPTY
        ) {
            return
        }

        lastSubmittedContent = submitted
        lastSubmitAttemptTime = System.currentTimeMillis()

        isSubmitting.value = true
        submitError.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val submission = PresentationAccess.client.submitForumComment(
                    authorization = authorization,
                    pageUrl = pageUrl,
                    content = submitted,
                    replyToken = replyToken
                )
                ensureActive()
                mutableState.value = CommunityState.Result(submission.comments, isSyncSuccess = true)
                lastCreatedCommentId.value = submission.createdComment.id
                draft.value = ""
                lastSubmittedContent = null
                this@CommentPageModel.replyToken.value = null
                this@CommentPageModel.replyAuthor.value = null
                viewModelScope.launch(Dispatchers.IO) { refreshProfileSnapshot() }
            } catch (error: CancellationException) {
                throw error
            } catch (error: CommentSubmissionTimeoutException) {
                mutableState.value = CommunityState.Result(error.comments, isSyncSuccess = false)
                submitError.value = CommentSubmitError.TIMEOUT
                AppLogger.w("CommentPageModel", "Comment submit timed out; attempting silent recovery", error)
                viewModelScope.launch(Dispatchers.IO) {
                    silentVerifySubmission(submitted)
                }
            } catch (error: CommentSubmissionNotVerifiedException) {
                mutableState.value = CommunityState.Result(error.comments, isSyncSuccess = false)
                submitError.value = CommentSubmitError.NOT_VERIFIED
                AppLogger.w("CommentPageModel", "Comment write completed but was not verified")
            } catch (error: ForumReplyBusinessException) {
                // ESJ returns HTTP 200 for business failures such as the daily
                // posting limit. Preserve the server message and leave the list intact.
                submitError.value = CommentSubmitError.Server(error.serverMessage)
            } catch (error: Exception) {
                submitError.value = CommentSubmitError.FAILED
                AppLogger.e("CommentPageModel", "Failed to submit comment", error)
            } finally {
                isSubmitting.value = false
            }
        }
    }

    private suspend fun silentVerifySubmission(submittedContent: String) {
        try {
            delay(3000L)
            val comments = PresentationAccess.client.getPageComments(
                authorization,
                pageUrl,
                forceRefresh = true
            )
            val normalized = submittedContent.trim().replace(Regex("\\s+"), " ")
            val found = comments.firstOrNull { comment ->
                val rendered = comment.contentText.trim().replace(Regex("\\s+"), " ")
                rendered == normalized || rendered.endsWith(normalized)
            }
            if (found != null) {
                withContext(Dispatchers.Main) {
                    mutableState.value = if (comments.isEmpty()) {
                        CommunityState.Empty(isSyncSuccess = true)
                    } else {
                        CommunityState.Result(comments, isSyncSuccess = true)
                    }
                    lastCreatedCommentId.value = found.id
                    draft.value = ""
                    lastSubmittedContent = null
                    this@CommentPageModel.replyToken.value = null
                    this@CommentPageModel.replyAuthor.value = null
                    submitError.value = null
                }
                refreshProfileSnapshot()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.d("CommentPageModel", "Silent recovery check completed without finding comment")
        }
    }

    /** A successful post changes the server-side experience and can change the user's level. */
    private suspend fun refreshProfileSnapshot() {
        try {
            val profile = PresentationAccess.client.getUserProfile(authorization, forceRefresh = true)
            val domain = authorization.domain.ifBlank { PresentationAccess.settings.domain.value }
            cacheUserProfile(authorization, domain, profile)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // The comment was already verified. Keep that success state even if the
            // non-critical profile refresh is temporarily unavailable.
            AppLogger.w("CommentPageModel", "Failed to refresh profile after comment submission", error)
        }
    }
}

internal sealed class CommentSubmitError {
    data object EMPTY : CommentSubmitError()
    data object FAILED : CommentSubmitError()
    data object NOT_VERIFIED : CommentSubmitError()
    data object TIMEOUT : CommentSubmitError()
    data class Server(val serverMessage: String) : CommentSubmitError()

    fun message(context: android.content.Context): String = when (this) {
        EMPTY -> context.getString(R.string.comment_empty_error)
        FAILED -> context.getString(R.string.comment_submit_failed)
        NOT_VERIFIED -> context.getString(R.string.comment_submit_unverified)
        TIMEOUT -> context.getString(R.string.comment_submit_timeout)
        is Server -> serverMessage.ifBlank { context.getString(R.string.comment_submit_failed) }
    }
}

private fun <T> List<T>.toCommunityState(isSyncSuccess: Boolean = false): CommunityState<List<T>> =
    if (isEmpty()) CommunityState.Empty(isSyncSuccess = isSyncSuccess) else CommunityState.Result(this, isSyncSuccess = isSyncSuccess)

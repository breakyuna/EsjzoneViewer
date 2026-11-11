@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.LinearProgressIndicator
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.app.profileCachePrefix
import com.breakyuna.esjzone.app.cacheUserProfile
import com.breakyuna.esjzone.network.features.getUserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecord
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordType
import com.breakyuna.esjzone.novellibrary.user.MemberReply
import com.breakyuna.esjzone.novellibrary.user.SystemMessage
import com.breakyuna.esjzone.novellibrary.user.copyText
import com.breakyuna.esjzone.ui.component.SelectableProse
import com.breakyuna.esjzone.ui.component.copyPlainText
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.AppNavigator
import com.breakyuna.esjzone.ui.navigation.LegacyRoute
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavPadding
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.navigation.restore
import com.breakyuna.esjzone.ui.product.EmptyState
import com.breakyuna.esjzone.ui.product.ErrorState
import com.breakyuna.esjzone.ui.product.ErrorStateCompact
import com.breakyuna.esjzone.ui.product.LoadingSkeleton
import com.breakyuna.esjzone.ui.product.OfflineState

/** `/my/sys`: read-only system notices. */
object SystemMessagesPage : AppDestination {
    private fun readResolve(): Any = SystemMessagesPage
    override val key: String = "SystemMessagesPage"

    @Composable
    override fun Content(navigator: AppNavigator) {
        MemberInboxContent(navigator, initialReplies = false)
    }
}

/**
 * `/my/reply`: the account's replies. Reply links point at chapter pages;
 * requesting one with the session would move the site's watch record, so the
 * app only offers the novel detail page or copying the link.
 */
object MemberRepliesPage : AppDestination {
    private fun readResolve(): Any = MemberRepliesPage
    override val key: String = "MemberRepliesPage"

    @Composable
    override fun Content(navigator: AppNavigator) {
        MemberInboxContent(navigator, initialReplies = true)
    }
}

@Composable
private fun MemberInboxContent(navigator: AppNavigator, initialReplies: Boolean) {
    var replies by rememberSaveable { mutableStateOf(initialReplies) }
    val authorization = LocalAuthorization.current
    val messagesModel = rememberAppViewModel { SystemMessagesPageModel(authorization) }
    val repliesModel = rememberAppViewModel { MemberRepliesPageModel(authorization) }
    val messages by messagesModel.state.collectAsState()
    val replyState by repliesModel.state.collectAsState()
    var selected by remember { mutableStateOf<MemberReply?>(null) }
    LaunchedEffect(replies) { if (replies) repliesModel.load() else messagesModel.load() }
    selected?.let { reply ->
        MemberReplyDialog(reply, onDismiss = { selected = null }, onOpenNovel = { path ->
            selected = null
            LegacyRoute.Novel(path).restore()?.let(navigator::pushIfNotCurrent)
        })
    }
    val state = if (replies) replyState else messages
    val refresh: () -> Unit = { if (replies) repliesModel.refresh() else messagesModel.refresh() }
    MemberPageScaffold(
        title = stringResource(R.string.member_messages), navigator = navigator,
        refreshing = state is MemberListState.Loading || (state as? MemberListState.Result<*>)?.refreshing == true,
        onRefresh = refresh
    ) {
        item(key = "message-tabs") {
            PrimaryTabRow(selectedTabIndex = if (replies) 1 else 0) {
                Tab(selected = !replies, onClick = { replies = false }, text = { Text(stringResource(R.string.member_system_messages)) })
                Tab(selected = replies, onClick = { replies = true }, text = { Text(stringResource(R.string.member_replies)) })
            }
        }
        if (replies) memberListContent(replyState, R.string.member_replies_empty, repliesModel::refresh) { _, reply ->
            MemberReplyRow(reply) { selected = reply }
        } else memberListContent(messages, R.string.member_system_messages_empty, messagesModel::refresh) { _, message ->
            SystemMessageRow(message)
        }
    }
}

/** `/my/record`: experience history by category, loaded 30 rows at a time. */
object ExperienceRecordsPage : AppDestination {
    private fun readResolve(): Any = ExperienceRecordsPage
    override val key: String = "ExperienceRecordsPage"

    @Composable
    override fun Content(navigator: AppNavigator) {
        val authorization = LocalAuthorization.current
        val model = rememberAppViewModel { ExperienceRecordsPageModel(authorization) }
        val experienceProgress = rememberExperienceProgress()
        val state by model.state.collectAsState()
        LaunchedEffect(Unit) { model.load() }
        MemberPageScaffold(
            title = stringResource(R.string.member_experience_records),
            navigator = navigator,
            refreshing = (state.loading && !state.loaded) || state.refreshing,
            onRefresh = model::refresh
        ) {
            item(key = "experience-progress") { ExperienceProgress(experienceProgress) }
            item(key = "record-types") {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    listOf(ExperienceRecordType.MESSAGE, ExperienceRecordType.COMMENT, ExperienceRecordType.POST).forEach { type ->
                        FilterChip(
                            selected = state.type == type,
                            onClick = { model.selectType(type) },
                            label = { Text(stringResource(type.labelRes())) }
                        )
                    }
                }
            }
            if (state.loading) item(key = "record-slow-hint") {
                // Signed in, the site can take 20–30 s before answering some categories.
                MetaText(stringResource(R.string.member_experience_records_slow_hint))
            }
            if (state.refreshFailed) item(key = "record-refresh-error") {
                ErrorStateCompact(
                    stringResource(
                        if (state.timedOut) R.string.member_experience_records_timeout_compact
                        else R.string.member_refresh_failed
                    ),
                    onRetry = model::retry
                )
            }
            when {
                !state.loaded && state.loading -> item(key = "record-loading") {
                    LoadingSkeleton(Modifier.fillMaxWidth().padding(top = AppSpacing.xl))
                }
                !state.loaded && state.failure != null -> {
                    val failure = state.failure ?: LoadFailureKind.CLIENT
                    item(key = "record-error") {
                        if (state.timedOut) ExperienceRecordTimeoutState(onRetry = model::retry)
                        else MemberFailureState(failure, onRetry = model::retry)
                    }
                }
                state.loaded && state.rows.isEmpty() -> item(key = "record-empty") {
                    EmptyState(
                        title = stringResource(R.string.member_experience_records_empty),
                        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xl)
                    )
                }
                else -> {
                    itemsIndexed(state.rows, key = { index, _ -> "record-$index" }) { _, record ->
                        ExperienceRecordRow(record)
                    }
                    item(key = "record-footer") {
                        ExperienceRecordFooter(state, onLoadMore = model::loadMore, onRetry = model::retry)
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberExperienceProgress(): Pair<Int?, Int?> {
    val authorization = LocalAuthorization.current
    val domain = authorization.domain.ifBlank { PresentationAccess.settings.domain.value }
    val prefix = remember(authorization, domain) { profileCachePrefix(authorization, domain) }
    val expFlow = remember(prefix) { PresentationAccess.database.cacheDao().observeByKey("${prefix}experience") }
    val requiredFlow = remember(prefix) { PresentationAccess.database.cacheDao().observeByKey("${prefix}experienceRequired") }
    val exp by expFlow.collectAsState(initial = null)
    val required by requiredFlow.collectAsState(initial = null)
    LaunchedEffect(authorization) {
        try {
            val fresh = withContext(Dispatchers.IO) { PresentationAccess.client.getUserProfile(authorization) }
            cacheUserProfile(authorization, domain, fresh)
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { com.breakyuna.esjzone.util.AppLogger.w("ExperienceRecords", "Profile unavailable", error) }
    }
    return exp?.value?.toIntOrNull() to required?.value?.toIntOrNull()
}

@Composable
private fun ExperienceProgress(progress: Pair<Int?, Int?>) {
    val (current, target) = progress
    Column(Modifier.fillMaxWidth().padding(vertical = AppSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        if (current != null) {
            Text(stringResource(R.string.profile_experience, current), style = AppTypography.titleLarge)
            if (target != null && target > 0) {
                Text(stringResource(R.string.member_experience_progress, current, target), style = AppTypography.bodySmall)
                LinearProgressIndicator(progress = { (current.toFloat() / target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(0.65f))
            }
        }
    }
}

private fun ExperienceRecordType.labelRes(): Int = when (this) {
    ExperienceRecordType.POST -> R.string.member_experience_type_post
    ExperienceRecordType.MESSAGE -> R.string.member_experience_type_message
    ExperienceRecordType.COMMENT -> R.string.member_experience_type_comment
}

@Composable
private fun MemberPageScaffold(
    title: String,
    navigator: AppNavigator,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, style = AppTypography.titleMedium) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            navigationIcon = { BackIconButton(onClick = { navigator.pop() }) }
        )
    }) { padding ->
        val navPadding = LocalFloatingNavPadding.current
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
        ) {
            LazyColumn(
                Modifier.fillMaxSize().accountContentWidth(),
                contentPadding = PaddingValues(
                    start = AppSpacing.lg,
                    end = AppSpacing.lg,
                    top = AppSpacing.md,
                    bottom = AppSpacing.lg + navPadding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                content = content
            )
        }
    }
}

private fun <T> LazyListScope.memberListContent(
    state: MemberListState<T>,
    emptyTitle: Int,
    onRetry: () -> Unit,
    row: @Composable (Int, T) -> Unit
) {
    when (state) {
        MemberListState.Loading -> item(key = "member-loading") {
            LoadingSkeleton(Modifier.fillMaxWidth().padding(top = AppSpacing.xl))
        }
        is MemberListState.Error -> item(key = "member-error") {
            MemberFailureState(state.failure, onRetry)
        }
        is MemberListState.Result -> {
            if (state.refreshFailure != null) item(key = "member-refresh-error") {
                ErrorStateCompact(stringResource(R.string.member_refresh_failed), onRetry = onRetry)
            }
            if (state.items.isEmpty()) item(key = "member-empty") {
                EmptyState(
                    title = stringResource(emptyTitle),
                    modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xl)
                )
            } else itemsIndexed(state.items, key = { index, _ -> "member-$index" }) { index, item ->
                row(index, item)
            }
        }
    }
}

@Composable
private fun MemberFailureState(failure: LoadFailureKind, onRetry: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xl)
    if (failure == LoadFailureKind.NETWORK) {
        OfflineState(
            title = stringResource(R.string.load_network_error),
            message = stringResource(R.string.member_load_failed_message),
            modifier = modifier,
            onRetry = onRetry
        )
    } else {
        ErrorState(
            title = stringResource(R.string.load_failed_short),
            message = stringResource(R.string.member_load_failed_message),
            modifier = modifier,
            onRetry = onRetry
        )
    }
}

/** The site was reachable but did not answer in time; distinct from being offline. */
@Composable
private fun ExperienceRecordTimeoutState(onRetry: () -> Unit) {
    ErrorState(
        title = stringResource(R.string.member_experience_records_timeout_title),
        message = stringResource(R.string.member_experience_records_timeout_message),
        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xl),
        onRetry = onRetry
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberCard(
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable () -> Unit
) {
    val inner: @Composable () -> Unit = {
        Column(
            Modifier.fillMaxWidth().padding(AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) { content() }
    }
    val modifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick
        )
    } else {
        Modifier
    }
    Surface(modifier = modifier, shape = AppShapes.standard, color = MaterialTheme.colorScheme.surface, content = inner)
}

@Composable
private fun MetaText(text: String) {
    Text(
        text,
        style = AppTypography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SystemMessageRow(message: SystemMessage) {
    MemberCard {
        // The row has no other gesture, so the title and time can share one selection.
        SelectableProse {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(message.title, style = AppTypography.bodyMedium)
                message.time?.let { MetaText(it) }
            }
        }
    }
}

@Composable
private fun MemberReplyRow(reply: MemberReply, onClick: () -> Unit) {
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.member_reply_copy)
    // The row opens a dialog on tap, so in-place selection would steal that click.
    // Long press copies the full reply; the dialog wraps the body in SelectableProse.
    MemberCard(
        onClick = onClick,
        onLongClickLabel = copyLabel,
        onLongClick = {
            val text = reply.copyText()
            if (text.isNotBlank()) copyPlainText(context, copyLabel, text)
        }
    ) {
        Text(
            reply.content.ifBlank { stringResource(R.string.member_reply_no_content) },
            style = AppTypography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
        reply.subject?.let { MetaText(stringResource(R.string.member_reply_subject, it)) }
        reply.replierName?.let { MetaText(stringResource(R.string.member_reply_replier, it)) }
        reply.date?.let { MetaText(it) }
    }
}

@Composable
private fun MemberReplyDialog(reply: MemberReply, onDismiss: () -> Unit, onOpenNovel: (String) -> Unit) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.member_reply_link_copied)
    val novelPath = reply.novelDetailPath
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(reply.subject ?: stringResource(R.string.member_replies)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                SelectableProse {
                    Text(reply.content, style = AppTypography.bodyMedium)
                }
                Text(
                    stringResource(R.string.member_reply_chapter_notice),
                    style = AppTypography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            if (novelPath != null) {
                TextButton(onClick = { onOpenNovel(novelPath) }) { Text(stringResource(R.string.member_reply_open_novel)) }
            }
        },
        dismissButton = {
            Row {
                if (reply.url.isNotBlank()) {
                    TextButton(onClick = {
                        copyReplyLink(context, reply.url)
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }) { Text(stringResource(R.string.member_reply_copy_link)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

private fun copyReplyLink(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("ESJ reply link", text))
}

@Composable
private fun ExperienceRecordRow(record: ExperienceRecord) {
    MemberCard {
        // The row has no other gesture; note, exp, time and extras share one selection.
        SelectableProse {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        record.note ?: record.time.orEmpty(),
                        style = AppTypography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    record.exp?.let {
                        Text(
                            stringResource(R.string.member_experience_value, it),
                            style = AppTypography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (record.note != null) record.time?.let { MetaText(it) }
                record.extras.forEach { MetaText(it) }
            }
        }
    }
}

@Composable
private fun ExperienceRecordFooter(state: ExperienceRecordsState, onLoadMore: () -> Unit, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            state.loading && !state.refreshing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            state.refreshing || state.refreshFailed -> Unit
            state.failure != null -> ErrorStateCompact(
                stringResource(
                    if (state.timedOut) R.string.member_experience_records_timeout_compact
                    else R.string.member_refresh_failed
                ),
                onRetry = onRetry
            )
            state.canLoadMore -> TextButton(onClick = onLoadMore) { Text(stringResource(R.string.search_load_more)) }
            else -> MetaText(stringResource(R.string.member_experience_records_end, state.rows.size))
        }
    }
}

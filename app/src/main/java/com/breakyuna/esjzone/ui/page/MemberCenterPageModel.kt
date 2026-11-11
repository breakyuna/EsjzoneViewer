package com.breakyuna.esjzone.ui.page

import androidx.lifecycle.viewModelScope
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.network.features.EXPERIENCE_RECORD_PAGE_SIZE
import com.breakyuna.esjzone.network.features.getExperienceRecords
import com.breakyuna.esjzone.network.features.getMemberReplies
import com.breakyuna.esjzone.network.features.getSystemMessages
import com.breakyuna.esjzone.network.features.isRequestTimeout
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecord
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordPage
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordType
import com.breakyuna.esjzone.novellibrary.user.MemberReply
import com.breakyuna.esjzone.novellibrary.user.SystemMessage
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Read-only member-center list state shared by `/my/sys` and `/my/reply`. */
sealed class MemberListState<out T> {
    data object Loading : MemberListState<Nothing>()
    data class Error(val failure: LoadFailureKind) : MemberListState<Nothing>()
    data class Result<T>(
        val items: List<T>,
        val refreshing: Boolean = false,
        val refreshFailure: LoadFailureKind? = null
    ) : MemberListState<T>()
}

/** Keeps visible rows on a failed refresh instead of replacing them with an error page. */
internal fun <T> MemberListState<T>.afterFailure(failure: LoadFailureKind): MemberListState<T> =
    when (this) {
        is MemberListState.Result -> copy(refreshing = false, refreshFailure = failure)
        else -> MemberListState.Error(failure)
    }

open class MemberListPageModel<T>(
    private val tag: String,
    private val loader: (forceRefresh: Boolean) -> List<T>
) : AppStateViewModel<MemberListState<T>>(MemberListState.Loading) {
    private var job: Job? = null
    private var started = false

    fun load() {
        if (started) return
        started = true
        fetch(forceRefresh = false)
    }

    fun refresh() = fetch(forceRefresh = true)

    private fun fetch(forceRefresh: Boolean) {
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            val current = mutableState.value
            mutableState.value = if (current is MemberListState.Result) {
                current.copy(refreshing = true, refreshFailure = null)
            } else MemberListState.Loading
            try {
                val items = cancellablePageRequest { loader(forceRefresh) }
                mutableState.value = MemberListState.Result(items)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLogger.e(tag, "Failed to load member-center page", error)
                mutableState.value = mutableState.value.afterFailure(error.loadFailureKind())
            }
        }
    }
}

class SystemMessagesPageModel(authorization: Authorization) : MemberListPageModel<SystemMessage>(
    "SystemMessagesPage",
    { force -> PresentationAccess.client.getSystemMessages(authorization, forceRefresh = force) }
)

class MemberRepliesPageModel(authorization: Authorization) : MemberListPageModel<MemberReply>(
    "MemberRepliesPage",
    { force -> PresentationAccess.client.getMemberReplies(authorization, forceRefresh = force) }
)

/** Accumulated rows of one experience record category. */
data class ExperienceRecordsState(
    val type: ExperienceRecordType = ExperienceRecordType.MESSAGE,
    val rows: List<ExperienceRecord> = emptyList(),
    val total: Int = 0,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val failure: LoadFailureKind? = null,
    /** True while a same-category refresh runs over the rows still on screen. */
    val refreshing: Boolean = false,
    /** The last same-category refresh failed; [rows] are the previous result. */
    val refreshFailed: Boolean = false,
    /** The last failure was the site not answering in time, not a broken connection. */
    val timedOut: Boolean = false
) {
    val canLoadMore: Boolean get() = loaded && !loading && !endReached
}

/**
 * Pure paging step: append one response page. The list ends when the server
 * reports no more rows, returns a short page, or returns nothing new.
 */
internal fun ExperienceRecordsState.appendPage(
    rows: List<ExperienceRecord>,
    total: Int,
    pageSize: Int = EXPERIENCE_RECORD_PAGE_SIZE
): ExperienceRecordsState {
    val merged = this.rows + rows
    return copy(
        rows = merged,
        total = total,
        loaded = true,
        loading = false,
        refreshing = false,
        refreshFailed = false,
        failure = null,
        timedOut = false,
        endReached = rows.isEmpty() || rows.size < pageSize || merged.size >= total
    )
}

/** A failed request keeps whatever was on screen; only a failed refresh is flagged as stale. */
internal fun ExperienceRecordsState.afterFailure(
    failure: LoadFailureKind,
    refresh: Boolean,
    timedOut: Boolean = false
): ExperienceRecordsState = copy(
    loading = false,
    refreshing = false,
    failure = failure,
    refreshFailed = refresh,
    timedOut = timedOut
)

/** Loads one page of a category; must be cancellable down to the HTTP call. */
internal typealias ExperienceRecordLoader = suspend (type: ExperienceRecordType, offset: Int) -> ExperienceRecordPage

/**
 * Paging and category switching for `/my/record`, kept apart from the ViewModel so
 * it can run on any scope. Only one request is active: a new category, refresh,
 * page or retry cancels the previous job (and with it the HTTP call), and a
 * generation check keeps a late response from replacing the newer state.
 * Failures are never retried automatically; the slow categories would pile up.
 */
internal class ExperienceRecordsController(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<ExperienceRecordsState>,
    private val loader: ExperienceRecordLoader,
    private val onFailure: (Exception) -> Unit = {}
) {
    private val lock = Any()
    private var job: Job? = null
    private var generation = 0L
    private var started = false

    fun load() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        reload(state.value.type)
    }

    fun selectType(type: ExperienceRecordType) {
        val current = state.value
        if (type == current.type && (current.loaded || current.loading)) return
        reload(type)
    }

    /**
     * Same-category refresh keeps the visible rows until the first page arrives;
     * a failure leaves them in place with a retry prompt.
     */
    fun refresh() {
        val current = state.value
        if (!current.loaded) {
            reload(current.type)
            return
        }
        fetch(ExperienceRecordsState(type = current.type), offset = 0, visible = current, refresh = true)
    }

    fun loadMore() {
        val current = state.value
        if (!current.canLoadMore) return
        fetch(current, offset = current.rows.size)
    }

    fun retry() {
        val current = state.value
        when {
            !current.loaded -> reload(current.type)
            current.refreshFailed -> refresh()
            else -> fetch(current, offset = current.rows.size)
        }
    }

    private fun reload(type: ExperienceRecordType) {
        fetch(ExperienceRecordsState(type = type), offset = 0)
    }

    /**
     * [base] is the state the response is appended to. [visible] is what stays
     * on screen meanwhile and after a failure; it differs from [base] only for
     * a same-category refresh.
     */
    private fun fetch(
        base: ExperienceRecordsState,
        offset: Int,
        visible: ExperienceRecordsState = base,
        refresh: Boolean = false
    ) {
        synchronized(lock) {
            job?.cancel()
            val requestGeneration = ++generation
            state.value = visible.copy(
                loading = true,
                refreshing = refresh,
                failure = null,
                refreshFailed = false,
                timedOut = false
            )
            job = scope.launch {
                val next = try {
                    val page = loader(base.type, offset)
                    base.appendPage(page.rows, page.total)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (isCurrent(requestGeneration)) onFailure(error)
                    visible.afterFailure(error.loadFailureKind(), refresh, error.isRequestTimeout())
                }
                synchronized(lock) {
                    if (requestGeneration == generation) state.value = next
                }
            }
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean = synchronized(lock) { requestGeneration == generation }
}

class ExperienceRecordsPageModel(
    authorization: Authorization
) : AppStateViewModel<ExperienceRecordsState>(ExperienceRecordsState()) {
    private val controller = ExperienceRecordsController(
        scope = viewModelScope,
        state = mutableState,
        loader = { type, offset ->
            cancellablePageRequest { PresentationAccess.client.getExperienceRecords(authorization, type, offset) }
        },
        onFailure = { error -> AppLogger.e("ExperienceRecordsPage", "Failed to load experience records", error) }
    )

    fun load() = controller.load()
    fun selectType(type: ExperienceRecordType) = controller.selectType(type)
    fun refresh() = controller.refresh()
    fun loadMore() = controller.loadMore()
    fun retry() = controller.retry()
}

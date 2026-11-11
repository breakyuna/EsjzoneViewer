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
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecord
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordType
import com.breakyuna.esjzone.novellibrary.user.MemberReply
import com.breakyuna.esjzone.novellibrary.user.SystemMessage
import com.breakyuna.esjzone.ui.navigation.AppStateViewModel
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

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
    val type: ExperienceRecordType = ExperienceRecordType.POST,
    val rows: List<ExperienceRecord> = emptyList(),
    val total: Int = 0,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val failure: LoadFailureKind? = null
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
        failure = null,
        endReached = rows.isEmpty() || rows.size < pageSize || merged.size >= total
    )
}

class ExperienceRecordsPageModel(
    private val authorization: Authorization
) : AppStateViewModel<ExperienceRecordsState>(ExperienceRecordsState()) {
    private var job: Job? = null
    private var generation = 0L
    private var started = false

    fun load() {
        if (started) return
        started = true
        reload(mutableState.value.type)
    }

    fun selectType(type: ExperienceRecordType) {
        if (type == mutableState.value.type && mutableState.value.loaded) return
        reload(type)
    }

    fun refresh() = reload(mutableState.value.type)

    fun loadMore() {
        val current = mutableState.value
        if (!current.canLoadMore) return
        fetch(current, offset = current.rows.size)
    }

    fun retry() {
        val current = mutableState.value
        if (current.loaded) fetch(current, offset = current.rows.size) else reload(current.type)
    }

    private fun reload(type: ExperienceRecordType) {
        fetch(ExperienceRecordsState(type = type), offset = 0)
    }

    private fun fetch(base: ExperienceRecordsState, offset: Int) {
        job?.cancel()
        val requestGeneration = ++generation
        mutableState.value = base.copy(loading = true, failure = null)
        job = viewModelScope.launch {
            try {
                val page = runInterruptible(Dispatchers.IO) {
                    PresentationAccess.client.getExperienceRecords(authorization, base.type, offset)
                }
                if (requestGeneration != generation) return@launch
                mutableState.value = base.appendPage(page.rows, page.total)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (requestGeneration != generation) return@launch
                AppLogger.e("ExperienceRecordsPage", "Failed to load experience records", error)
                mutableState.value = base.copy(loading = false, failure = error.loadFailureKind())
            }
        }
    }
}

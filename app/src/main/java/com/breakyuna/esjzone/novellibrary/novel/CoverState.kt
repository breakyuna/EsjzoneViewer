package com.breakyuna.esjzone.novellibrary.novel

import androidx.compose.runtime.Immutable
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.google.gson.annotations.SerializedName
import java.io.InterruptedIOException
import java.net.SocketTimeoutException

/** Source lookup and actual image loading are separate from the fallback artwork. */
@Immutable
sealed interface CoverState {
    data object Unknown : CoverState
    data object Resolving : CoverState
    data object Missing : CoverState
    data object Loading : CoverState
    data object Loaded : CoverState
    data class Failed(val reason: CoverFailure) : CoverState
}

enum class CoverFailure {
    @SerializedName("TIMEOUT")
    TIMEOUT,
    @SerializedName("NETWORK")
    NETWORK,
    @SerializedName("UNAVAILABLE")
    UNAVAILABLE,
    @SerializedName("OTHER")
    OTHER
}

@Immutable
data class CoverSource(val url: String = "", val state: CoverState = CoverState.Unknown) {
    companion object {
        /** Only a completed metadata lookup may interpret an empty URL as Missing. */
        fun fromUrl(rawUrl: String?, emptyState: CoverState = CoverState.Missing): CoverSource {
            val url = EsjzoneUrls.coverOrEmpty(rawUrl)
            return CoverSource(url, if (url.isBlank()) emptyState else CoverState.Loading)
        }
    }
}

/** Keep only a failure category, never the exception or a potentially private request URL. */
internal fun Throwable.coverFailure(): CoverFailure {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is SocketTimeoutException ||
            (current is InterruptedIOException && current.message?.contains("timeout", ignoreCase = true) == true)
        ) return CoverFailure.TIMEOUT
        current = current.cause
    }
    return if (loadFailureKind() == LoadFailureKind.NETWORK) CoverFailure.NETWORK else CoverFailure.OTHER
}

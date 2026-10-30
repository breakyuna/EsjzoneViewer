package com.breakyuna.esjzone.novellibrary.data

import androidx.compose.runtime.Immutable
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.CoverFailure
import com.breakyuna.esjzone.novellibrary.novel.CoverSource
import com.breakyuna.esjzone.novellibrary.novel.CoverState
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@Immutable
data class HomeData(
    val recentlyUpdateTranslated: List<CoveredNovel>,
    val recentlyUpdateOriginal: List<CoveredNovel>,
    val recentlyUpdateTranslatedR18: List<CoveredNovel>,
    val recentlyUpdateOriginalR18: List<CoveredNovel>,
    val recommendation: List<CoveredNovel>,
    /** The site-provided days for the current Monday-to-today update window. */
    val weeklyUpdates: List<WeeklyUpdateDay> = emptyList(),
    /** The first ten entries from the site's server-rendered previous-week ranking. */
    val weeklyPopular: List<WeeklyPopularNovel> = emptyList(),
    @Transient val weeklyFailure: com.breakyuna.esjzone.network.LoadFailureKind? = null
)

@Immutable
data class WeeklyPopularNovel(
    @SerializedName("rank")
    val rank: Int = 0,
    @SerializedName("weeklyViews")
    val weeklyViews: Int = 0,
    @SerializedName("descriptionPreview")
    val descriptionPreview: String = "",
    @SerializedName("type")
    val type: String = "",
    @SerializedName("coverUrl")
    override val coverUrl: String = "",
    @SerializedName("name")
    override val name: String = "",
    @SerializedName("url")
    override val url: String = "",
    @SerializedName("views")
    override val views: Int = 0,
    @SerializedName("likes")
    override val likes: Int = 0,
    @SerializedName("isAdult")
    override val isAdult: Boolean = false,
    @SerializedName("author")
    override val author: String? = null,
    @SerializedName("coverResolved")
    val coverResolved: Boolean? = null,
    @SerializedName("coverFailure")
    val coverFailure: CoverFailure? = null
) : CoveredNovel, Serializable {
    override val coverState: CoverState
        get() = CoverSource.fromUrl(coverUrl, when {
            coverFailure != null -> CoverState.Failed(coverFailure)
            coverResolved == true -> CoverState.Missing
            coverResolved == false -> CoverState.Resolving
            else -> CoverState.Unknown
        }).state
}

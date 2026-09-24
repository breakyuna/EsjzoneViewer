package com.breakyuna.esjzone.network.features

import android.content.Context
import com.breakyuna.esjzone.EsjzoneApplication
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.novellibrary.data.HomeData
import com.breakyuna.esjzone.novellibrary.data.WeeklyUpdateDay
import com.breakyuna.esjzone.novellibrary.data.WeeklyPopularNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import com.breakyuna.esjzone.novellibrary.novel.CoveredNovelImpl
import com.breakyuna.esjzone.util.AppLogger
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

/**
 * Persistent snapshot and in-memory cache for [HomeData].
 * Enables instant, zero-wait opening of the Home tab across process restarts
 * without displaying skeleton placeholders.
 */
object HomeDataCache {

    private const val FILE_NAME = "home_data_snapshot.json"
    private val gson = Gson()
    private val ioLock = Any()

    @Volatile
    private var memoryCache: HomeData? = null

    @Volatile
    private var memoryDomain: String? = null

    @Volatile
    private var storageDir: File? = null

    fun initialize(context: Context) {
        val dir = context.applicationContext.filesDir
        storageDir = dir
        synchronized(ioLock) {
            if (memoryDomain == null) {
                loadFromDisk(dir)?.let { (domain, data) ->
                    memoryDomain = domain
                    memoryCache = data
                }
            }
        }
    }

    private fun normalizeLegacyDomain(domain: String): String {
        val effective = domain.ifBlank { EsjzoneUrls.BaseWithoutProtocol }
        return effective.trim().lowercase().removePrefix("www.")
    }

    fun readSnapshot(authorization: Authorization): HomeData? {
        val normalizedDomain = EsjzoneClient.accountScope(authorization)
        val dir = storageDir ?: runCatching {
            EsjzoneApplication.instance.filesDir
        }.getOrNull() ?: return null

        return synchronized(ioLock) {
            if (memoryDomain == normalizedDomain) {
                memoryCache
            } else {
                loadFromDisk(dir)?.let { (diskDomain, data) ->
                    memoryDomain = diskDomain
                    memoryCache = data
                    if (diskDomain == normalizedDomain) data else null
                }
            }
        }
    }

    fun writeSnapshot(authorization: Authorization, data: HomeData) {
        val normalizedDomain = EsjzoneClient.accountScope(authorization)
        if (normalizedDomain != EsjzoneClient.activeAccountScopeOrNull()) return
        val dir = storageDir ?: runCatching {
            EsjzoneApplication.instance.filesDir
        }.getOrNull() ?: return

        synchronized(ioLock) {
            if (normalizedDomain != EsjzoneClient.activeAccountScopeOrNull()) return
            try {
                val snapshot = data.toSnapshot(normalizedDomain)
                val json = gson.toJson(snapshot)
                val targetFile = File(dir, FILE_NAME)
                val tempFile = File(dir, "$FILE_NAME.tmp")
                tempFile.writeText(json, StandardCharsets.UTF_8)
                try {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                } catch (_: Exception) {
                    Files.move(
                        tempFile.toPath(),
                        targetFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
                memoryDomain = normalizedDomain
                memoryCache = data
            } catch (e: Exception) {
                AppLogger.w("HomeDataCache", "Failed to write home data snapshot", e)
            }
        }
    }

    private fun loadFromDisk(dir: File): Pair<String, HomeData>? {
        val file = File(dir, FILE_NAME)
        if (!file.isFile) return null
        return try {
            val json = file.readText(StandardCharsets.UTF_8)
            val snapshot = gson.fromJson(json, HomeDataSnapshot::class.java)
            val data = snapshot?.toHomeData() ?: return null
            val domain = snapshot.domain?.takeIf(String::isNotBlank)?.let { stored ->
                if (stored.startsWith("account:shared:")) stored else normalizeLegacyDomain(stored)
            } ?: normalizeLegacyDomain("")
            domain to data
        } catch (e: Exception) {
            AppLogger.w("HomeDataCache", "Failed to read home data snapshot", e)
            runCatching { file.delete() }
            null
        }
    }

    private fun HomeData.toSnapshot(domain: String): HomeDataSnapshot = HomeDataSnapshot(
        domain = domain,
        recentlyUpdateTranslated = recentlyUpdateTranslated.map(::toImpl),
        recentlyUpdateOriginal = recentlyUpdateOriginal.map(::toImpl),
        recentlyUpdateTranslatedR18 = recentlyUpdateTranslatedR18.map(::toImpl),
        recentlyUpdateOriginalR18 = recentlyUpdateOriginalR18.map(::toImpl),
        recommendation = recommendation.map(::toImpl),
        weeklyPopular = weeklyPopular,
        weeklyUpdates = weeklyUpdates.map { day ->
            WeeklyUpdateDaySnapshot(
                dateString = day.date.toString(),
                novels = day.novels.map(::toImpl)
            )
        }
    )

    private fun toImpl(novel: CoveredNovel): CoveredNovelImpl =
        (novel as? CoveredNovelImpl) ?: CoveredNovelImpl(
            coverUrl = novel.coverUrl,
            name = novel.name,
            url = novel.url,
            views = novel.views,
            likes = novel.likes,
            isAdult = novel.isAdult,
            latestTitle = novel.latestTitle,
            latestUrl = novel.latestUrl,
            author = novel.author,
            authorUrl = novel.authorUrl,
            words = novel.words,
            articleCount = novel.articleCount,
            discussionCount = novel.discussionCount
        )
}

internal data class HomeDataSnapshot(
    @SerializedName("domain")
    val domain: String? = null,
    @SerializedName("recentlyUpdateTranslated")
    val recentlyUpdateTranslated: List<CoveredNovelImpl>? = null,
    @SerializedName("recentlyUpdateOriginal")
    val recentlyUpdateOriginal: List<CoveredNovelImpl>? = null,
    @SerializedName("recentlyUpdateTranslatedR18")
    val recentlyUpdateTranslatedR18: List<CoveredNovelImpl>? = null,
    @SerializedName("recentlyUpdateOriginalR18")
    val recentlyUpdateOriginalR18: List<CoveredNovelImpl>? = null,
    @SerializedName("recommendation")
    val recommendation: List<CoveredNovelImpl>? = null,
    @SerializedName("weeklyUpdates")
    val weeklyUpdates: List<WeeklyUpdateDaySnapshot>? = null,
    // Nullable keeps Gson snapshots written before this field was introduced readable.
    @SerializedName("weeklyPopular")
    val weeklyPopular: List<WeeklyPopularNovel>? = null
) {
    fun toHomeData(): HomeData {
        val parsedWeekly = weeklyUpdates.orEmpty().mapNotNull { daySnapshot ->
            val dateStr = daySnapshot.dateString ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@mapNotNull null
            WeeklyUpdateDay(date, daySnapshot.novels.orEmpty())
        }
        return HomeData(
            recentlyUpdateTranslated = recentlyUpdateTranslated.orEmpty(),
            recentlyUpdateOriginal = recentlyUpdateOriginal.orEmpty(),
            recentlyUpdateTranslatedR18 = recentlyUpdateTranslatedR18.orEmpty(),
            recentlyUpdateOriginalR18 = recentlyUpdateOriginalR18.orEmpty(),
            recommendation = recommendation.orEmpty(),
            weeklyUpdates = parsedWeekly,
            weeklyPopular = weeklyPopular.orEmpty()
        )
    }
}

internal data class WeeklyUpdateDaySnapshot(
    @SerializedName("dateString")
    val dateString: String? = null,
    @SerializedName("novels")
    val novels: List<CoveredNovelImpl>? = null
)

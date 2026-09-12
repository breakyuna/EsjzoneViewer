package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.PageCacheTtl
import com.breakyuna.esjzone.network.PageKind
import com.breakyuna.esjzone.novellibrary.data.WeeklyUpdateDay
import java.io.IOException
import java.time.LocalDate
import org.jsoup.Jsoup

private val updateDateRegex = Regex("""\d{4}-\d{2}-\d{2}""")

/**
 * Reads ESJ's current-week update board. The site itself publishes tabs from Monday
 * through today, so this deliberately does not manufacture future-day placeholders.
 */
fun EsjzoneClient.getWeeklyUpdates(authorization: Authorization): List<WeeklyUpdateDay> {
    val body = getPage(
        authorization = authorization,
        url = EsjzoneUrls.WeeklyUpdate,
        maxAgeMillis = PageCacheTtl.WEEKLY_UPDATE,
        pageKind = PageKind.WEEKLY_UPDATE
    )
    val document = Jsoup.parse(body, EsjzoneUrls.WeeklyUpdate)
    val tabs = document.select("a[href^='#tab']")
        .mapNotNull { tab ->
            val date = updateDateRegex.find(tab.text())?.value
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@mapNotNull null
            val target = tab.attr("href").removePrefix("#").trim()
            target.takeIf { it.isNotBlank() }?.let { date to it }
        }
        .distinctBy { it.first }

    val days = tabs.mapNotNull { (date, tabId) ->
        val pane = document.getElementById(tabId) ?: return@mapNotNull null
        val novels = pane.select(".card")
            .filter { card -> card.selectFirst("a[href*='/detail/']") != null }
            .map { card -> parseNovelCard(card, r18 = false, layout = NovelCardLayout.HOME) }
            .filter { it.name.isNotBlank() && it.url.isNotBlank() }
            .distinctBy { it.url }
        WeeklyUpdateDay(date, novels)
    }.sortedByDescending { it.date }

    if (days.isEmpty()) throw IOException("ESJ weekly update page contained no recognizable date tabs")
    return days
}

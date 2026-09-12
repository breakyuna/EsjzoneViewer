package com.breakyuna.esjzone.novellibrary.data

import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel

class HomeData(
    val recentlyUpdateTranslated: List<CoveredNovel>,
    val recentlyUpdateOriginal: List<CoveredNovel>,
    val recentlyUpdateTranslatedR18: List<CoveredNovel>,
    val recentlyUpdateOriginalR18: List<CoveredNovel>,
    val recommendation: List<CoveredNovel>,
    /** The site-provided days for the current Monday-to-today update window. */
    val weeklyUpdates: List<WeeklyUpdateDay> = emptyList()
)

package com.breakyuna.esjzone.novellibrary.data

import com.breakyuna.esjzone.novellibrary.novel.CoveredNovel
import java.time.LocalDate

/** One date tab exposed by ESJ's `/update/` page. */
data class WeeklyUpdateDay(
    val date: LocalDate,
    val novels: List<CoveredNovel>
)

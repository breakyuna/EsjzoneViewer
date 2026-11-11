package com.breakyuna.esjzone.novellibrary.user

/** One row of the read-only `/my/sys` system message list. */
data class SystemMessage(
    val title: String,
    val time: String?
)

/**
 * One row of `/my/reply`.
 *
 * [url] points at a chapter or forum post page. Opening it with the account
 * session would move the site's `/my/view` record, so the app never requests it.
 */
data class MemberReply(
    val content: String,
    val url: String,
    val subject: String?,
    val replierName: String?,
    val replierUrl: String?,
    val date: String?
) {
    /** Novel detail path derived from `/forum/<novelId>/<chapterId>.html`, when it looks like a novel. */
    val novelDetailPath: String?
        get() = REPLY_NOVEL_PATH.find(url)?.groupValues?.get(1)?.let { "/detail/$it.html" }

    private companion object {
        // Novel ids are ten-digit creation timestamps; forum boards use short ids.
        val REPLY_NOVEL_PATH = Regex("/forum/(\\d{9,})/\\d+\\.html")
    }
}

/** Experience record categories accepted by `/inc/mem_record_data.php` (`search=type:<n>`). */
enum class ExperienceRecordType(val code: Int) {
    POST(1),
    MESSAGE(2),
    COMMENT(3)
}

/**
 * One experience record row. Only `cdate` and `exp` are named by the site's
 * table header; any other non-empty field is kept as text in [extras].
 */
data class ExperienceRecord(
    val time: String?,
    val exp: String?,
    val note: String?,
    val extras: List<String> = emptyList()
)

data class ExperienceRecordPage(
    val total: Int,
    val rows: List<ExperienceRecord>
)


/** Plain text for clipboard or tests: title, then time, skipping blanks. */
fun SystemMessage.copyText(): String =
    listOfNotNull(title.takeIf { it.isNotBlank() }, time?.takeIf { it.isNotBlank() })
        .joinToString("\n")

/** Plain text of a reply row: body, subject, replier and date. The chapter URL is omitted. */
fun MemberReply.copyText(): String =
    listOfNotNull(
        content.takeIf { it.isNotBlank() },
        subject?.takeIf { it.isNotBlank() },
        replierName?.takeIf { it.isNotBlank() },
        date?.takeIf { it.isNotBlank() },
    ).joinToString("\n")

/** Plain text of an experience row: note, exp, time (once) and any extra fields. */
fun ExperienceRecord.copyText(): String = buildList {
    val primary = note?.takeIf { it.isNotBlank() } ?: time?.takeIf { it.isNotBlank() }
    primary?.let(::add)
    exp?.takeIf { it.isNotBlank() }?.let(::add)
    if (!note.isNullOrBlank()) time?.takeIf { it.isNotBlank() && it != primary }?.let(::add)
    extras.mapNotNull { it.takeIf(String::isNotBlank) }.forEach(::add)
}.joinToString("\n")

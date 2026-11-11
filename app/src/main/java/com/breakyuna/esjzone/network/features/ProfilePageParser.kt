package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import org.jsoup.nodes.Document

private val profileSelector: HtmlSelector = JsoupHtmlSelector

private const val PROFILE_NAME_SELECTORS =
    "aside h4, form.form-edit h4, .profile-username, [data-profile-username]"

private const val PROFILE_AVATAR_SELECTORS =
    "aside img, .profile-avatar, [data-profile-avatar], img[data-avatar]"

private val PROFILE_EXPERIENCE_PATTERN = Regex(
    "(?i)(?<![\\d\\w])([\\d,]+)\\s*(?:exp|經驗值|经验值)"
)
private val PROFILE_LEVEL_PATTERN = Regex(
    "(?i)([A-Za-z]{1,4}\\s*[級级]\\s*(?:Lv\\.?\\s*\\d+|Max))"
)

/**
 * The account card currently renders the value as `7264 exp`. Keep the
 * selector broad enough for the site's template variants, but search the
 * member card/sidebar before the whole page so unrelated post text cannot be
 * mistaken for account metadata.
 */
private fun profileCardText(document: Document): Sequence<String> = sequence {
    document.select("aside, .user-info, .profile-card, .profile-summary, [data-profile-card]")
        .map { it.text() }
        .filter { it.isNotBlank() }
        .forEach { yield(it) }
    document.body()?.text()?.takeIf { it.isNotBlank() }?.let { yield(it) }
}

/** Shared CSS profile marker used by both profile loading and auth probing. */
internal fun profileName(document: Document): String =
    profileSelector.first(document, PROFILE_NAME_SELECTORS)
        ?.text()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: "User"

internal fun hasProfileMarker(document: Document): Boolean =
    profileSelector.first(document, PROFILE_NAME_SELECTORS)
        ?.text()
        ?.trim()
        ?.isNullOrBlank() == false

/** Keeps the legacy relative avatar URL contract while accepting lazy markup. */
internal fun profileAvatarUrl(document: Document): String =
    profileSelector.first(document, PROFILE_AVATAR_SELECTORS)?.let { image ->
        image.attr("data-src").trim().ifBlank { image.attr("src").trim() }
    }.orEmpty()

internal fun profileExperience(document: Document): Int? =
    profileCardText(document)
        .mapNotNull { PROFILE_EXPERIENCE_PATTERN.find(it)?.groupValues?.getOrNull(1) }
        .mapNotNull { it.replace(",", "").toIntOrNull() }
        .firstOrNull()

internal fun profileLevel(document: Document): String? =
    profileSelector.first(
        document,
        ".profile-level, [data-profile-level], [data-level], .user-level"
    )?.text()
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: profileCardText(document)
        .mapNotNull { PROFILE_LEVEL_PATTERN.find(it)?.groupValues?.getOrNull(1) }
        .map { it.replace(Regex("\\s+"), " ").trim() }
        .firstOrNull()

private val PROFILE_REGISTER_DATE_PREFIX = Regex("^\\s*(?:註冊|注册)日期\\s*[：:]?\\s*")
private val PROFILE_EXPERIENCE_PROGRESS = Regex("([\\d,]+)\\s*/\\s*([\\d,]+)")

/** `<span>註冊日期 2026-08-07</span>` inside the member card; label and date share one text node. */
internal fun profileRegisterDate(document: Document): String? =
    document.select("div.user-data > span, .user-data span")
        .firstOrNull { PROFILE_REGISTER_DATE_PREFIX.containsMatchIn(it.ownText()) }
        ?.text()
        ?.replace(PROFILE_REGISTER_DATE_PREFIX, "")
        ?.trim()
        ?.takeIf(String::isNotBlank)

/**
 * Experience needed for the next level from the card tooltip, for example
 * `F級 Lv3：16122/30000`. Bootstrap moves `title` into `data-original-title`
 * once the tooltip initializes, so the server HTML may carry either attribute.
 */
internal fun profileExperienceRequired(document: Document): Int? =
    document.select("div.user-cover .info-label, .user-cover .info-label")
        .asSequence()
        .flatMap { sequenceOf(it.attr("data-original-title"), it.attr("title"), it.attr("data-bs-original-title")) }
        .filter(String::isNotBlank)
        .mapNotNull { PROFILE_EXPERIENCE_PROGRESS.find(it)?.groupValues?.getOrNull(2) }
        .mapNotNull { it.replace(",", "").toIntOrNull() }
        .firstOrNull { it > 0 }

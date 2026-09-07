package com.breakyuna.esjzone.network.features

import com.breakyuna.esjzone.network.HtmlSelector
import com.breakyuna.esjzone.network.JsoupHtmlSelector
import org.jsoup.nodes.Document

private val profileSelector: HtmlSelector = JsoupHtmlSelector

private const val PROFILE_NAME_SELECTORS =
    "aside h4, form.form-edit h4, .profile-username, [data-profile-username]"

private const val PROFILE_AVATAR_SELECTORS =
    "aside img, .profile-avatar, [data-profile-avatar], img[data-avatar]"

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
        image.attr("src").ifBlank { image.attr("data-src") }
    }.orEmpty()


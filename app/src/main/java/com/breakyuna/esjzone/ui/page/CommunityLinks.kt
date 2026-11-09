@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.breakyuna.esjzone.ui.page

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.cancellablePageRequest
import com.breakyuna.esjzone.ui.navigation.AppNavigator
import com.breakyuna.esjzone.ui.navigation.forumContentLinkPath
import com.breakyuna.esjzone.ui.navigation.forumLinkDestination
import com.breakyuna.esjzone.ui.navigation.resolveForumContentLink
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

internal data class CommunityUrlMatch(val start: Int, val end: Int, val url: String)

// Unicode path characters remain valid. Chinese sentence punctuation and quotation marks
// delimit a link; ASCII closing brackets are handled separately so balanced URL paths survive.
private val bareUrlPattern = Regex(
    """(?i)(?:(?<![A-Za-z0-9_@.])https?://|(?<![A-Za-z0-9_@./])www\.)[^\s\p{Z}<>"`“”‘’「」『』《》【】〈〉，。！？；：、（）［］｛｝…]+"""
)
private val htmlAnchorPattern = Regex("<a\\b[^>]*\\bhref\\s*=", RegexOption.IGNORE_CASE)
private val communityMarkup = Safelist.basic()
    .addTags("div", "h1", "h2", "h3", "h4", "h5", "h6")
    .removeProtocols("a", "href", "ftp", "mailto")

internal fun hasCommunityHtmlLink(html: String): Boolean = htmlAnchorPattern.containsMatchIn(html)

/** Finds explicit web addresses without interpreting ordinary text as a hostname. */
internal fun findCommunityUrls(text: String): List<CommunityUrlMatch> =
    bareUrlPattern.findAll(text).mapNotNull { match ->
        val value = match.value
        var end = value.length
        var roundBalance = value.count { it == '(' } - value.count { it == ')' }
        var squareBalance = value.count { it == '[' } - value.count { it == ']' }
        var curlyBalance = value.count { it == '{' } - value.count { it == '}' }
        while (end > 0) {
            when (value[end - 1]) {
                '.', ',', '!', '?', ';', ':', '\'' -> end--
                ')' -> if (roundBalance < 0) { end--; roundBalance++ } else break
                ']' -> if (squareBalance < 0) { end--; squareBalance++ } else break
                '}' -> if (curlyBalance < 0) { end--; curlyBalance++ } else break
                else -> break
            }
        }
        val raw = value.substring(0, end)
        val normalized = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
        val url = normalized.toHttpUrlOrNull()
        if (url == null || url.username.isNotEmpty() || url.password.isNotEmpty()) null
        else CommunityUrlMatch(match.range.first, match.range.first + end, url.toString())
    }.toList()

/** Preserve existing HTML link ranges and add links only to the remaining text. */
internal fun linkifyCommunityText(
    source: AnnotatedString,
    styles: TextLinkStyles,
    listener: LinkInteractionListener
): AnnotatedString {
    val existing = source.getLinkAnnotations(0, source.length)
    val matches = findCommunityUrls(source.text).filter { candidate ->
        existing.none { it.start < candidate.end && candidate.start < it.end }
    }
    if (matches.isEmpty()) return source
    return AnnotatedString.Builder(source).apply {
        matches.forEach { match ->
            addLink(LinkAnnotation.Url(match.url, styles, listener), match.start, match.end)
        }
    }.toAnnotatedString()
}

internal fun buildCommunityLinkedText(
    text: String,
    html: String?,
    baseUrl: String,
    styles: TextLinkStyles,
    listener: LinkInteractionListener
): AnnotatedString {
    // Locally pending comments contain literal text in both fields, not HTML.
    val cleanHtml = html?.takeIf { it != text && hasCommunityHtmlLink(it) }
        ?.let { Jsoup.clean(it, EsjzoneUrls.resolve(baseUrl), communityMarkup) }
    val source = if (cleanHtml != null && hasCommunityHtmlLink(cleanHtml)) {
        AnnotatedString.fromHtml(cleanHtml, linkStyles = styles, linkInteractionListener = listener)
    } else {
        AnnotatedString(text)
    }
    return linkifyCommunityText(source, styles, listener)
}

@Composable
internal fun rememberCommunityLinkOpener(navigator: AppNavigator): (String) -> Unit {
    val context = LocalContext.current
    val authorization = LocalAuthorization.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var pending by remember(navigator, context, authorization) { mutableStateOf<HttpUrl?>(null) }
    val urlToResolve = pending
    LaunchedEffect(navigator, context, authorization, lifecycle, urlToResolve) {
        val url = urlToResolve ?: return@LaunchedEffect
        val path = forumContentLinkPath(url) ?: return@LaunchedEffect
        val destination = try {
            cancellablePageRequest { resolveForumContentLink(authorization, path) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        if (pending != url) return@LaunchedEffect
        pending = null
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
        if (destination != null) navigator.pushIfNotCurrent(destination)
        else InAppBrowserActivity.open(context, url.toString(), keepInApp = true)
    }

    return remember(navigator, context, authorization) {
        val opener: (String) -> Unit = { href ->
            val url = href.toHttpUrlOrNull()
            if (url != null && url.username.isEmpty() && url.password.isEmpty()) {
                if (forumContentLinkPath(url) != null) {
                    pending = url
                } else {
                    pending = null
                    val destination = forumLinkDestination(url)
                    if (destination != null) navigator.pushIfNotCurrent(destination)
                    else InAppBrowserActivity.open(context, url.toString(), keepInApp = true)
                }
            }
        }
        opener
    }
}

@Composable
internal fun rememberCommunityLinkedText(
    text: String,
    html: String? = null,
    baseUrl: String = "",
    onLinkClick: (String) -> Unit
): AnnotatedString {
    val currentOnLinkClick by rememberUpdatedState(onLinkClick)
    val linkColor = MaterialTheme.colorScheme.primary
    val listener = remember {
        LinkInteractionListener { link ->
            if (link is LinkAnnotation.Url) currentOnLinkClick(link.url)
        }
    }
    return remember(text, html, baseUrl, linkColor, listener) {
        val styles = TextLinkStyles(
            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        )
        buildCommunityLinkedText(text, html, baseUrl, styles, listener)
    }
}

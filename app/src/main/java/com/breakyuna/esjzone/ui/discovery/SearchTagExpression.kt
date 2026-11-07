package com.breakyuna.esjzone.ui.discovery

internal data class SearchTagExpression(
    val query: String,
    val invalidExcluded: Boolean
) {
    val canSearch: Boolean get() = query.isNotBlank() && !invalidExcluded
}

private val searchTagSeparator = Regex("[\\p{javaWhitespace}+＋]+")

private fun hasExcludePrefix(value: String): Boolean = value.startsWith('!') || value.startsWith('！')

/** Tag name after one optional exclude mark. Null when the token is empty or still excluded. */
private fun exclusionTagBody(token: String): String? {
    val body = if (hasExcludePrefix(token)) token.drop(1) else token
    if (body.isEmpty() || hasExcludePrefix(body)) return null
    return body
}

/**
 * Show each exclusion keyword as !tag and join keywords with +.
 * A trailing separator is kept so another keyword can be typed.
 * Tokens that would invert the exclusion are left unchanged.
 */
internal fun formatExclusionInput(value: String): String {
    if (value.isEmpty()) return ""
    val trailingSeparator = value.last().let { it.isWhitespace() || it == '+' || it == '＋' }
    val tokens = value.split(searchTagSeparator).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return ""
    val formatted = tokens.joinToString("+") { token ->
        exclusionTagBody(token)?.let { "!$it" } ?: token
    }
    return if (trailingSeparator) "$formatted+" else formatted
}

/** Append exclusion tags without rewriting the existing search text. */
internal fun buildSearchTagExpression(
    currentQuery: String,
    excluded: String
): SearchTagExpression {
    val tokens = excluded.split(searchTagSeparator).filter { it.isNotEmpty() }
    val bodies = tokens.map(::exclusionTagBody)
    val exclusion = bodies.filterNotNull().joinToString("+") { "!$it" }
    val existing = currentQuery.trim()
    val query = when {
        existing.isEmpty() -> exclusion
        exclusion.isEmpty() -> existing
        else -> "$existing+$exclusion"
    }
    return SearchTagExpression(query, bodies.any { it == null })
}

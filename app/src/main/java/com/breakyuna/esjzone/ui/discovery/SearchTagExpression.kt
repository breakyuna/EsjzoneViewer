package com.breakyuna.esjzone.ui.discovery

internal data class SearchTagExpression(
    val query: String,
    val invalidExcluded: Boolean
) {
    val canSearch: Boolean get() = query.isNotBlank() && !invalidExcluded
}

private val searchTagSeparator = Regex("[\\p{javaWhitespace}+＋]+")
private val searchTagPart = Regex("([^\\p{javaWhitespace}+＋]+)|([\\p{javaWhitespace}+＋]+)")

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

/**
 * Map a cursor in the raw exclusion text onto [formatExclusionInput].
 * Prefixes and separators inserted before the cursor move it with them.
 */
internal fun mapExclusionCursor(raw: String, cursor: Int): Int {
    val target = cursor.coerceIn(0, raw.length)
    var formattedPos = 0
    var rawPos = 0
    var seenToken = false
    for (match in searchTagPart.findAll(raw)) {
        val token = match.groups[1]?.value
        if (token != null) {
            if (seenToken) formattedPos += 1
            seenToken = true
            if (target < rawPos) return formattedPos
            if (target < rawPos + token.length) {
                return formattedPos + renderedOffset(token, target - rawPos)
            }
            val body = exclusionTagBody(token)
            formattedPos += if (body != null) 1 + body.length else token.length
        } else {
            val start = rawPos
            val end = start + match.value.length
            if (target <= start) return formattedPos
            if (target < end || end == raw.length) return if (seenToken) formattedPos + 1 else 0
        }
        rawPos += match.value.length
    }
    return formattedPos
}

private fun renderedOffset(token: String, offset: Int): Int {
    val body = exclusionTagBody(token) ?: return offset
    val rawPrefix = if (hasExcludePrefix(token)) 1 else 0
    if (offset == 0) return if (rawPrefix == 0) 1 else 0
    return 1 + (offset - rawPrefix).coerceAtLeast(0).coerceAtMost(body.length)
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

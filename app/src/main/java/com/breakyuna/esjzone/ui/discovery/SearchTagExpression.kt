package com.breakyuna.esjzone.ui.discovery

internal data class SearchTagExpression(
    val query: String,
    val invalidIncluded: Boolean,
    val invalidExcluded: Boolean
) {
    val canSearch: Boolean get() = query.isNotBlank() && !invalidIncluded && !invalidExcluded
}

/** Append visual tag filters without reinterpreting the existing search text. */
internal fun buildSearchTagExpression(
    currentQuery: String,
    included: String,
    excluded: String
): SearchTagExpression {
    fun tokens(value: String): List<String> = value
        .split(Regex("[\\p{javaWhitespace}+＋]+"))
        .filter { it.isNotEmpty() }

    fun hasExcludePrefix(value: String): Boolean = value.startsWith('!') || value.startsWith('！')

    val includedTags = tokens(included)
    val excludedTags = tokens(excluded).map { if (hasExcludePrefix(it)) it.drop(1) else it }
    val invalidIncluded = includedTags.any(::hasExcludePrefix)
    val invalidExcluded = excludedTags.any { it.isEmpty() || hasExcludePrefix(it) }
    val parts = buildList<String> {
        val existing = currentQuery.trim()
        if (existing.isNotEmpty()) add(existing)
        addAll(includedTags)
        addAll(excludedTags.map { "!$it" })
    }
    return SearchTagExpression(parts.joinToString(" "), invalidIncluded, invalidExcluded)
}

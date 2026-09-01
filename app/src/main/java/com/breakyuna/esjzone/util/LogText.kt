package com.breakyuna.esjzone.util

/** Bounds persisted records without cutting a surrogate pair or a UTF-8 sequence. */
internal fun boundedUtf8LogRecord(input: String, maxBytes: Int): String {
    require(maxBytes > 0)
    if (input.toByteArray(Charsets.UTF_8).size <= maxBytes) return input
    val marker = "\n[record truncated]\n"
    val markerBytes = marker.toByteArray(Charsets.UTF_8).size
    val suffix = if (maxBytes >= markerBytes) marker else ""
    val budget = maxBytes - suffix.toByteArray(Charsets.UTF_8).size
    val headBudget = if (suffix.isEmpty()) budget else budget / 2
    var start = 0
    var used = 0
    while (start < input.length) {
        val end = start + Character.charCount(input.codePointAt(start))
        val bytes = input.substring(start, end).toByteArray(Charsets.UTF_8).size
        if (used + bytes > headBudget) break
        used += bytes
        start = end
    }
    if (suffix.isEmpty()) return input.substring(0, start)
    var end = input.length
    used = 0
    val tailBudget = budget - headBudget
    while (end > start) {
        val previous = end - Character.charCount(input.codePointBefore(end))
        val bytes = input.substring(previous, end).toByteArray(Charsets.UTF_8).size
        if (used + bytes > tailBudget) break
        used += bytes
        end = previous
    }
    return input.substring(0, start) + suffix + input.substring(end)
}

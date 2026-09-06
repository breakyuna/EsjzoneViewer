package com.breakyuna.esjzone.ui.page

/** Immutable snapshot used by the settings presentation layer. */
data class LocalCacheStats(
    val pageBytes: Long,
    val pageEntries: Int,
    val imageBytes: Long
)

internal fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    val kib = 1024.0
    val mib = kib * 1024.0
    return when {
        safeBytes >= mib -> String.format("%.1f MiB", safeBytes / mib)
        safeBytes >= kib -> String.format("%.1f KiB", safeBytes / kib)
        else -> "$safeBytes B"
    }
}

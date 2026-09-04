package com.breakyuna.esjzone.update

import java.math.BigInteger

/** Compare stable Release tags against the installed APK, never lexicographically. */
internal object ReleaseVersion {
    private val pattern = Regex(
        """^(?:beta[-_.]?)?[vV]?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$""",
        RegexOption.IGNORE_CASE
    )

    fun isNewerStableRelease(tag: String, installed: String): Boolean {
        val latest = pattern.matchEntire(tag.trim()) ?: return false
        val current = pattern.matchEntire(installed.trim()) ?: return false
        if (latest.groupValues[4].isNotEmpty() || tag.contains("beta", ignoreCase = true)) return false
        for (index in 1..3) {
            val order = BigInteger(latest.groupValues[index])
                .compareTo(BigInteger(current.groupValues[index]))
            if (order != 0) return order > 0
        }
        return current.groupValues[4].isNotEmpty() || installed.contains("beta", ignoreCase = true)
    }
}

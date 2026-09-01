package com.breakyuna.esjzone.database

/** Pure merge rules kept separate from Room/network code for regression tests. */
object BookshelfSyncRules {
    /** Local rows are retained; tombstones are the only rows excluded from import. */
    fun mergedVisibleKeys(
        localKeys: Set<String>,
        remoteKeys: Set<String>,
        tombstoneKeys: Set<String>
    ): Set<String> = (localKeys + remoteKeys) - tombstoneKeys

    fun shouldImport(remoteKey: String, localKeys: Set<String>, tombstoneKeys: Set<String>): Boolean =
        remoteKey !in localKeys && remoteKey !in tombstoneKeys

    /** A network response may mutate a row only if it belongs to the current intent. */
    fun shouldApplyResponse(currentVersion: Long, responseVersion: Long): Boolean =
        currentVersion == responseVersion

    fun deduplicateRemoteKeys(keys: Iterable<String>): List<String> =
        keys.map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

package com.breakyuna.esjzone.ui.product

/**
 * Stable list identity and content-type values for lazy layouts.
 * Feature screens own list state and choose the appropriate item model; these helpers
 * keep keys consistent without coupling the component library to a repository or database.
 */
object ProductComponentContentTypes {
    const val Novel = "novel"
    const val Comment = "comment"
    const val Download = "download"
    const val History = "history"
    const val Bookshelf = "bookshelf"
}

fun stableProductKey(id: String, contentType: String): String = "$contentType:$id"

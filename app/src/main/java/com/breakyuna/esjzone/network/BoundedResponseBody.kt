package com.breakyuna.esjzone.network

import java.io.IOException
import okhttp3.ResponseBody

/** Bound decoded HTML/JSON, including gzip and chunked responses with no Content-Length. */
internal fun ResponseBody.readTextBounded(maxBytes: Long = 8L * 1024 * 1024): String = use {
    require(maxBytes in 1 until Long.MAX_VALUE)
    if (contentLength() > maxBytes || source().request(maxBytes + 1)) {
        throw IOException("Text response exceeds the allowed size")
    }
    // OkHttp still owns BOM/charset decoding. At this point EOF was reached within the limit.
    string()
}

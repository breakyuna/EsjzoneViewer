package com.breakyuna.esjzone.util

import java.io.InputStream
import java.io.OutputStream

/** Check between chunks without closing streams owned by the archive or its caller. */
internal fun InputStream.copyWithCancellation(output: OutputStream, checkActive: () -> Unit) {
    copyWithCancellation(output, ByteArray(64 * 1024), checkActive)
}

/** A sequential multi-file transfer can retain one buffer for the entire operation. */
internal fun InputStream.copyWithCancellation(output: OutputStream, buffer: ByteArray, checkActive: () -> Unit) {
    require(buffer.isNotEmpty())
    while (true) {
        checkActive()
        val count = read(buffer)
        checkActive()
        if (count < 0) return
        output.write(buffer, 0, count)
    }
}

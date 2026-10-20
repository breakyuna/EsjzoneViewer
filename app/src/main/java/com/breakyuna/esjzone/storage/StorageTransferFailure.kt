package com.breakyuna.esjzone.storage

import java.io.IOException

enum class StorageTransferFailureReason {
    TARGET_MANIFEST_CONFLICT, TARGET_FILE_CONFLICT, SOURCE_MANIFEST_INVALID,
    SOURCE_FILE_MISSING, VERIFICATION_FAILED, ACCESS, IO, UNKNOWN
}

data class StorageTransferFailure(val reason: StorageTransferFailureReason, val item: String = "")

/** Structured diagnostics never include manifest contents or provider exception messages. */
class StorageTransferException(val failure: StorageTransferFailure, cause: Exception? = null) :
    IOException("Storage transfer failed: ${failure.reason}", cause)

internal fun Exception.transferFailure(item: String): StorageTransferFailure = when (this) {
    is StorageTransferException -> failure
    is StorageAccessException, is SecurityException -> StorageTransferFailure(StorageTransferFailureReason.ACCESS, item)
    is IOException -> StorageTransferFailure(StorageTransferFailureReason.IO, item)
    else -> StorageTransferFailure(StorageTransferFailureReason.UNKNOWN, item)
}

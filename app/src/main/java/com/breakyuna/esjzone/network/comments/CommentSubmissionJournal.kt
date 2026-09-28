package com.breakyuna.esjzone.network.comments

import android.content.Context
import androidx.annotation.Keep
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Private recovery record. Contains no credentials and is excluded from Android backup. */
@Keep
internal data class PendingCommentWrite(
    val operationId: String = UUID.randomUUID().toString(),
    val content: String,
    val replyToken: String?,
    val previousIds: Set<String>,
    val authorName: String? = null,
    val accepted: Boolean = false,
    val acceptedId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

internal fun blockingCommentWrite(
    existing: List<PendingCommentWrite>,
    incoming: PendingCommentWrite,
    allowRetry: Boolean
): PendingCommentWrite? = existing.lastOrNull {
    it.content == incoming.content && it.replyToken == incoming.replyToken &&
        (it.accepted || !allowRetry)
}

/** Keeps separate operations for different comments on the same page. */
internal class CommentSubmissionJournal(context: Context, authorization: Authorization, pageUrl: String) {
    companion object { private val lock = Any() }

    private val jar = EsjzoneClient.persistentCookieJar
    private val identity = jar?.activeAccountIdentity()
    private val epoch = jar?.sessionEpoch()
    private val scope = listOf(identity.orEmpty(), authorization.domain, pageUrl).joinToString("\n")
    private val name = MessageDigest.getInstance("SHA-256").digest(scope.toByteArray())
        .joinToString("") { "%02x".format(it) }
    private val root = File(context.noBackupFilesDir, "comment_operations")
    private val directory = File(root, name)
    private val legacyFile = File(root, "$name.json")
    private val gson = Gson()

    private fun current() = identity != null && jar?.activeAccountIdentity() == identity && jar?.sessionEpoch() == epoch

    private fun fileFor(operationId: String): File {
        require(operationId.matches(Regex("[A-Za-z0-9-]{1,64}")))
        return File(directory, "$operationId.json")
    }

    private fun readFile(file: File): PendingCommentWrite? = runCatching {
        gson.fromJson(file.readText(), PendingCommentWrite::class.java)
            ?.takeIf { it.operationId?.matches(Regex("[A-Za-z0-9-]{1,64}")) == true }
    }.getOrNull()

    private fun migrateLegacy() {
        if (!legacyFile.isFile) return
        val old = readFile(legacyFile) ?: return
        write(old)
        legacyFile.delete()
    }

    fun readAll(): List<PendingCommentWrite> = synchronized(lock) {
        if (!current()) return@synchronized emptyList()
        migrateLegacy()
        directory.listFiles { file -> file.isFile && file.extension == "json" }
            ?.mapNotNull { file -> readFile(file)?.let { operation -> operation to file.lastModified() } }
            ?.sortedWith(compareBy<Pair<PendingCommentWrite, Long>> { (operation, modified) ->
                operation.createdAtMillis.takeIf { it > 0L } ?: modified
            }.thenBy { it.first.operationId })
            ?.map { it.first }
            .orEmpty()
    }

    fun read(): PendingCommentWrite? = readAll().lastOrNull()

    fun start(operation: PendingCommentWrite, allowRetry: Boolean): Unit = synchronized(lock) {
        val existing = blockingCommentWrite(readAll(), operation, allowRetry)
        if (existing != null) {
            throw PendingCommentExistsException(existing)
        }
        write(operation)
    }

    fun write(operation: PendingCommentWrite): Unit = synchronized(lock) {
        check(current()) { "Comment account changed" }
        directory.mkdirs()
        val file = fileFor(operation.operationId)
        val temporary = File(directory, "${operation.operationId}.tmp")
        java.io.FileOutputStream(temporary).use { stream ->
            stream.write(gson.toJson(operation).toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        check(temporary.renameTo(file)) { "Cannot preserve pending comment" }
    }

    fun clear(operationId: String): Unit = synchronized(lock) {
        if (current()) fileFor(operationId).delete()
    }
}

internal class PendingCommentExistsException(val operation: PendingCommentWrite) :
    java.io.IOException("A comment operation already needs verification")

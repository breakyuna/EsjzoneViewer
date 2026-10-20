package com.breakyuna.esjzone

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Binder
import android.os.Bundle
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Test APK only: exercises opaque document IDs without depending on an installed file manager. */
class StorageTestDocumentsProvider : DocumentsProvider() {
    private val fixtures = ConcurrentHashMap<String, Pair<String, String>>()
    private val root: File get() = File(requireNotNull(context).filesDir, "storage-provider-fixture").apply { mkdirs() }
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method == "configure-fixture") {
            require(callingPackage == "com.breakyuna.esjzone")
            require(arg != null && arg.startsWith("case-") && '/' !in arg && '\\' !in arg)
            val mode = extras?.getString("mode")
            if (mode == null) fixtures.remove("root/$arg")
            else fixtures["root/$arg"] = mode to (extras?.getString("operation") ?: "all")
            return Bundle()
        }
        if (method != "grant-fixture-tree") return super.call(method, arg, extras)
        val target = requireNotNull(callingPackage)
        require(target == "com.breakyuna.esjzone")
        val identity = Binder.clearCallingIdentity()
        try {
            val tree = android.provider.DocumentsContract.buildTreeDocumentUri(
                "com.breakyuna.esjzone.storage.test.documents", "root")
            requireNotNull(context).grantUriPermission(target, tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            return Bundle()
        } finally { Binder.restoreCallingIdentity(identity) }
    }
    private fun checkAccess(documentId: String, operation: String) {
        val fixture = fixtures.entries.firstOrNull {
            documentId == it.key || documentId.startsWith("${it.key}/")
        }?.value ?: return
        if (fixture.second != "all" && fixture.second != operation) return
        when (fixture.first) {
            "security" -> throw SecurityException("Fixture permission revoked")
            "argument" -> throw IllegalArgumentException("Fixture document unavailable")
            "reject-file-metadata" -> if (operation == "metadata" && file(documentId).isFile) {
                throw SecurityException("Fixture rejects per-file metadata queries")
            }
            "reject-file-rename" -> if (operation == "rename" && file(documentId).isFile) {
                throw SecurityException("Fixture rejects per-file renames")
            }
            "reject-book-metadata" -> if (operation == "metadata" &&
                file(documentId).let { it.isDirectory && it.parentFile?.name == "downloads" }) {
                throw SecurityException("Fixture rejects per-book metadata queries")
            }
        }
    }
    private fun file(id: String): File {
        require(id == "root" || id.startsWith("root/"))
        val file = File(root, id.removePrefix("root").trimStart('/')).canonicalFile
        require(file == root.canonicalFile || file.path.startsWith(root.canonicalPath + File.separator))
        return file
    }
    private fun id(file: File) = "root" + file.relativeTo(root).invariantSeparatorsPath.let {
        if (it.isBlank()) "" else "/$it"
    }
    private val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_FLAGS)
    private fun row(cursor: MatrixCursor, file: File) {
        val values = mapOf<String, Any>(Document.COLUMN_DOCUMENT_ID to id(file),
            Document.COLUMN_DISPLAY_NAME to file.name,
            Document.COLUMN_MIME_TYPE to if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream",
            Document.COLUMN_SIZE to file.length(), Document.COLUMN_FLAGS to
                (Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME or
                    if (file.isDirectory) Document.FLAG_DIR_SUPPORTS_CREATE else Document.FLAG_SUPPORTS_WRITE))
        cursor.addRow(cursor.columnNames.map { values[it] }.toTypedArray())
    }
    override fun queryRoots(projection: Array<out String>?): Cursor {
        val names = projection ?: arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_FLAGS)
        return MatrixCursor(names).apply {
            val values = mapOf(Root.COLUMN_ROOT_ID to "root", Root.COLUMN_DOCUMENT_ID to "root",
                Root.COLUMN_TITLE to "Storage fixture", Root.COLUMN_FLAGS to Root.FLAG_SUPPORTS_CREATE)
            addRow(columnNames.map { values[it] }.toTypedArray())
        }
    }
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        checkAccess(documentId, "metadata")
        return MatrixCursor(projection ?: columns).apply { file(documentId).takeIf(File::exists)?.let { row(this, it) } }
    }
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        checkAccess(parentDocumentId, "children")
        return MatrixCursor(projection ?: columns).apply {
            file(parentDocumentId).listFiles().orEmpty().forEach { row(this, it) }
            if (fixtures[parentDocumentId]?.first == "odd-names") {
                listOf("", " ", ".", "..", "bad\\name", "a/b", "/absolute").forEachIndexed { index, name ->
                    val values = mapOf<String, Any>(Document.COLUMN_DOCUMENT_ID to "$parentDocumentId/odd-$index",
                        Document.COLUMN_DISPLAY_NAME to name, Document.COLUMN_MIME_TYPE to "application/octet-stream",
                        Document.COLUMN_SIZE to 1L, Document.COLUMN_FLAGS to 0)
                    addRow(columnNames.map { values[it] }.toTypedArray())
                }
            }
        }
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        checkAccess(documentId, "open")
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode))
    }
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        checkAccess(parentDocumentId, "create")
        require(displayName.isNotBlank() && displayName != "." && displayName != ".." && '/' !in displayName && '\\' !in displayName)
        val child = File(file(parentDocumentId), displayName)
        require(!child.exists())
        check(if (mimeType == Document.MIME_TYPE_DIR) child.mkdir() else child.createNewFile())
        return id(child)
    }
    override fun renameDocument(documentId: String, displayName: String): String {
        checkAccess(documentId, "rename")
        require(displayName.isNotBlank() && displayName != "." && displayName != ".." && '/' !in displayName && '\\' !in displayName)
        val original = file(documentId)
        val target = File(original.parentFile, displayName)
        require(!target.exists())
        check(original.renameTo(target))
        return id(target)
    }
    override fun deleteDocument(documentId: String) {
        checkAccess(documentId, "delete")
        check(file(documentId).deleteRecursively())
    }
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith("$parentDocumentId/")
}

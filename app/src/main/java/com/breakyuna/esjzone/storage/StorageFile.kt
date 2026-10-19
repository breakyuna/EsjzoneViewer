package com.breakyuna.esjzone.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.breakyuna.esjzone.util.copyWithCancellation
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Distinguishes an unavailable document provider from malformed downloaded content. */
class StorageAccessException(cause: Exception? = null) :
    IOException("Unable to access the selected storage folder", cause)

/** Files in either the legacy private directory or a user-authorized document tree. */
class StorageFile private constructor(
    private val local: File?,
    private val tree: DocumentTree?,
    private val relative: String
) {
    constructor(file: File) : this(file, null, "")
    constructor(parent: StorageFile, child: String) : this(
        parent.local?.let { File(it, checkedRelativePath(child)) }, parent.tree,
        listOf(parent.relative, checkedRelativePath(child)).filter(String::isNotEmpty).joinToString("/")
    )

    val name: String get() = local?.name ?: relative.substringAfterLast('/').ifEmpty { tree!!.name }
    val extension: String get() = name.substringAfterLast('.', "")
    val nameWithoutExtension: String get() = name.substringBeforeLast('.', name)
    val isDocument: Boolean get() = tree != null
    val absolutePath: String get() = local?.absolutePath ?: "${tree!!.root}/$relative"
    val parentFile: StorageFile? get() = local?.parentFile?.let(::StorageFile)
        ?: if (relative.isEmpty()) null else StorageFile(null, tree, relative.substringBeforeLast('/', ""))
    val uri: Uri get() = local?.let(Uri::fromFile) ?: tree!!.resolve(relative)
        ?: throw IOException("Storage document is unavailable")
    val uriString: String get() = local?.toURI()?.toString() ?: uri.toString()
    val isDirectory: Boolean get() = local?.isDirectory ?: (tree!!.metadata(relative)?.type == DIRECTORY)
    val isFile: Boolean get() = local?.isFile ?: tree!!.metadata(relative)?.let { it.type != DIRECTORY } ?: false
    val usableSpace: Long get() = local?.usableSpace ?: Long.MAX_VALUE

    fun exists(): Boolean = local?.exists() ?: (tree!!.metadata(relative) != null)
    fun length(): Long = local?.length() ?: tree!!.metadata(relative)?.size ?: 0L
    fun mkdirs(): Boolean {
        if (local != null) return local.isDirectory || local.mkdirs()
        if (relative.isEmpty()) return isDirectory
        if (exists()) return isDirectory
        val parent = parentFile ?: return false
        if (!parent.mkdirs()) return false
        tree!!.create(relative, DIRECTORY)
        return true
    }

    fun listFiles(): Array<StorageFile>? {
        if (local != null) return local.listFiles()?.map(::StorageFile)?.toTypedArray()
        if (!exists()) return null
        return tree!!.children(relative).map { StorageFile(this, it) }.toTypedArray()
    }

    data class Entry(val file: StorageFile, val directory: Boolean, val size: Long)

    /** Operation-scoped metadata: one provider query per directory, with sizes and types. */
    fun entries(): List<Entry> {
        if (local != null) {
            val children = local.listFiles() ?: throw IOException("Unable to list storage directory")
            return children.map { Entry(StorageFile(it), it.isDirectory, it.length()) }
        }
        return tree!!.entries(relative).map { (name, metadata) ->
            Entry(StorageFile(this, name), metadata.type == DIRECTORY, metadata.size)
        }
    }

    fun snapshotFiles(checkActive: () -> Unit): List<Entry> = buildList {
        fun visit(directory: StorageFile) {
            checkActive()
            directory.entries().forEach { entry ->
                checkActive()
                if (entry.directory) visit(entry.file) else add(entry)
            }
        }
        visit(this@StorageFile)
    }

    /** Only for an unpublished, exclusively owned staging directory with an existing parent. */
    fun createNewOutputStream(): OutputStream {
        if (local != null) {
            check(local.createNewFile())
            return local.outputStream()
        }
        tree!!.create(relative, "application/octet-stream")
        return documentAccess {
            tree.context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Unable to write storage document")
        }
    }

    fun inputStream(): InputStream = local?.inputStream()
        ?: documentAccess {
            tree!!.context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to read storage document")
        }

    fun outputStream(): OutputStream {
        if (local != null) return local.outputStream()
        if (!exists()) {
            check(parentFile?.mkdirs() == true)
            tree!!.create(relative, "application/octet-stream")
        }
        return documentAccess {
            tree!!.context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IOException("Unable to write storage document")
        }
    }

    fun bufferedWriter(charset: Charset = Charsets.UTF_8) = outputStream().bufferedWriter(charset)
    fun writeText(text: String) = bufferedWriter().use { it.write(text) }
    fun storedTextReader(): java.io.BufferedReader {
        val input = inputStream().buffered()
        return try {
            input.mark(2)
            val compressed = input.read() == 0x1f && input.read() == 0x8b
            input.reset()
            (if (compressed) GZIPInputStream(input) else input).bufferedReader(Charsets.UTF_8)
        } catch (error: Exception) { input.close(); throw error }
    }
    fun writeCompressedText(write: (java.io.Writer) -> Unit) {
        GZIPOutputStream(outputStream().buffered()).bufferedWriter(Charsets.UTF_8).use(write)
    }

    fun delete(): Boolean {
        if (local != null) return local.delete()
        val currentTree = checkNotNull(tree)
        val document = currentTree.resolve(relative) ?: return true
        val deleted = documentAccess { DocumentsContract.deleteDocument(currentTree.context.contentResolver, document) }
        if (deleted) currentTree.invalidate(relative)
        return deleted
    }
    fun deleteRecursively(): Boolean = local?.deleteRecursively() ?: delete()

    /** Rename within one parent; callers never publish an unfinished file as a completed one. */
    fun renameTo(destination: StorageFile): Boolean {
        if (local != null && destination.local != null) return local.renameTo(destination.local)
        require(tree === destination.tree && parentFile == destination.parentFile)
        val currentTree = checkNotNull(tree)
        if (destination.exists()) return false
        val directory = isDirectory
        val renamed = documentAccess {
            DocumentsContract.renameDocument(currentTree.context.contentResolver, uri, destination.name)
        }
            ?: return false
        currentTree.invalidate(relative, directory)
        currentTree.invalidate(destination.relative, directory)
        currentTree.documents[destination.relative] = renamed
        return true
    }

    fun replaceWith(temporary: StorageFile) {
        if (local != null && temporary.local != null) {
            try {
                Files.move(temporary.local.toPath(), local.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary.local.toPath(), local.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            return
        }
        recoverReplacement()
        val old = if (exists()) StorageFile(checkNotNull(parentFile), "$name.previous.tmp") else null
        if (old != null) check(renameTo(old))
        try {
            check(temporary.renameTo(this))
        } catch (error: Exception) {
            if (old != null) check(old.renameTo(this))
            throw error
        }
        old?.delete()
    }

    /** SAF has no atomic replace; retain the last file across a process death between renames. */
    fun recoverReplacement() {
        if (!isDocument) return
        val parent = parentFile ?: return
        val previous = StorageFile(parent, "$name.previous.tmp")
        if (!previous.exists()) return
        val recovered = if (exists()) previous.delete() else previous.renameTo(this)
        if (!recovered) throw StorageAccessException()
    }

    fun walkTopDown(): Sequence<StorageFile> = sequence {
        yield(this@StorageFile)
        if (isDirectory) {
            val children = listFiles() ?: if (exists()) throw IOException("Unable to list storage directory") else emptyArray()
            for (child in children) yieldAll(child.walkTopDown())
        }
    }
    fun relativePathFrom(parent: StorageFile): String {
        if (local != null && parent.local != null) return local.relativeTo(parent.local).invariantSeparatorsPath
        require(tree === parent.tree && relative.startsWith(parent.relative + "/"))
        return relative.removePrefix(parent.relative + "/")
    }
    internal fun isDescendantOf(parent: StorageFile): Boolean {
        if (local != null && parent.local != null) {
            return local.canonicalPath.startsWith(parent.local.canonicalPath + File.separator)
        }
        val currentTree = tree ?: return false
        val parentTree = parent.tree ?: return false
        if (currentTree.root.authority != parentTree.root.authority) return false
        return documentAccess { DocumentsContract.isChildDocument(currentTree.context.contentResolver, parent.uri, uri) }
    }
    fun copyTo(destination: File, overwrite: Boolean = false) {
        require(overwrite || !destination.exists())
        inputStream().use { input -> destination.outputStream().use { input.copyTo(it) } }
    }
    /** Existing files are never overwritten during migration. A retry accepts identical copies. */
    fun copyFrom(
        source: StorageFile,
        checkActive: () -> Unit = {},
        beforeTemporaryCreate: (StorageFile) -> Unit = {}
    ) {
        checkActive()
        if (source.isDirectory) {
            check(mkdirs())
            for (child in source.listFiles().orEmpty()) StorageFile(this, child.name).copyFrom(child, checkActive, beforeTemporaryCreate)
        } else {
            if (exists()) {
                require(sameContent(source, checkActive)) { "The destination contains different files" }
                return
            }
            check(parentFile?.mkdirs() == true)
            val pending = StorageFile(checkNotNull(parentFile), "migration-${UUID.randomUUID()}.tmp")
            beforeTemporaryCreate(pending)
            try {
                source.inputStream().use { input -> pending.outputStream().use { output ->
                    input.copyWithCancellation(output, checkActive)
                } }
                require(pending.length() == source.length())
                check(pending.renameTo(this))
            } finally { pending.delete() }
        }
    }
    fun sameContent(other: StorageFile, checkActive: () -> Unit = {}, sizesChecked: Boolean = false): Boolean {
        if (!sizesChecked && length() != other.length()) return false
        return inputStream().buffered().use { first -> other.inputStream().buffered().use secondUse@ { second ->
            val a = ByteArray(64 * 1024)
            val b = ByteArray(a.size)
            while (true) {
                checkActive()
                val count = first.read(a)
                if (count < 0) return@secondUse second.read() < 0
                var offset = 0
                while (offset < count) {
                    val read = second.read(b, offset, count - offset)
                    if (read < 0) return@secondUse false
                    offset += read
                }
                if (!(0 until count).all { a[it] == b[it] }) return@secondUse false
            }
            @Suppress("UNREACHABLE_CODE") false
        } }
    }
    override fun equals(other: Any?): Boolean = other is StorageFile && absolutePath == other.absolutePath
    override fun hashCode(): Int = absolutePath.hashCode()

    companion object {
        private const val DIRECTORY = DocumentsContract.Document.MIME_TYPE_DIR
        private inline fun <T> documentAccess(action: () -> T): T = try {
            action()
        } catch (error: SecurityException) {
            throw StorageAccessException(error)
        } catch (error: IllegalArgumentException) {
            throw StorageAccessException(error)
        } catch (error: IOException) {
            throw if (error is StorageAccessException) error else StorageAccessException(error)
        }

        internal fun checkedRelativePath(path: String): String {
            require(path.isNotBlank() && !path.startsWith('/') && '\\' !in path &&
                path.split('/').all { it.isNotBlank() && it != "." && it != ".." })
            return path
        }
        fun fromTree(context: Context, uri: Uri): StorageFile = documentAccess {
            StorageFile(null, DocumentTree(context.applicationContext, uri), "")
        }
        fun createTempFile(prefix: String, suffix: String, parent: StorageFile): StorageFile {
            if (parent.local != null) return StorageFile(File.createTempFile(prefix, suffix, parent.local))
            return StorageFile(parent, "$prefix${UUID.randomUUID()}$suffix").apply { outputStream().close() }
        }
    }

    private class DocumentTree(val context: Context, val root: Uri) {
        val documents = ConcurrentHashMap<String, Uri>()
        private val listedDirectories = ConcurrentHashMap.newKeySet<String>()
        val name: String get() = metadata("")?.displayName
            ?: DocumentsContract.getTreeDocumentId(root).substringAfterLast('/').substringAfter(':')
        init { documents[""] = DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root)) }
        data class Metadata(val type: String, val size: Long, val displayName: String)
        fun resolve(path: String): Uri? {
            documents[path]?.let { return it }
            if (path.isEmpty()) return null
            val parent = path.substringBeforeLast('/', "")
            if (parent !in listedDirectories) children(parent)
            return documents[path]
        }
        fun metadata(path: String): Metadata? {
            val uri = resolve(path) ?: run {
                if (path.isNotEmpty()) metadata("")
                return null
            }
            return documentAccess {
                val result = try {
                    context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (!cursor.moveToFirst()) null else Metadata(cursor.getString(0), cursor.getLong(1), cursor.getString(2))
                    }
                } catch (error: java.io.FileNotFoundException) {
                    if (path.isEmpty()) throw StorageAccessException(error)
                    null
                }
                if (result == null) {
                    if (path.isEmpty()) throw StorageAccessException()
                    // A missing child is normal; a disconnected/missing tree is an access error.
                    metadata("")
                    invalidate(path)
                }
                result
            }
        }
        fun children(path: String): List<String> = entries(path).map { it.first }
        fun entries(path: String): List<Pair<String, Metadata>> = documentAccess {
            val parent = resolve(path) ?: return@documentAccess emptyList()
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(root, DocumentsContract.getDocumentId(parent))
            val cursor = context.contentResolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE), null, null, null)
                ?: throw IOException("Unable to list storage directory")
            val names = cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        val name = it.getString(1)
                        // Display names are provider data, not trusted relative paths.
                        if (name.isNullOrBlank() || name == "." || name == ".." || '/' in name || '\\' in name) continue
                        val childPath = listOf(path, name).filter(String::isNotEmpty).joinToString("/")
                        documents[childPath] = DocumentsContract.buildDocumentUriUsingTree(root, it.getString(0))
                        add(name to Metadata(it.getString(2), it.getLong(3), name))
                    }
                }
            }
            val children = names.map { listOf(path, it.first).filter(String::isNotEmpty).joinToString("/") }.toSet()
            documents.keys.filter { it.isNotEmpty() && it.substringBeforeLast('/', "") == path && it !in children }
                .forEach { invalidate(it) }
            listedDirectories += path
            names
        }
        fun create(path: String, type: String) = documentAccess {
            val parent = resolve(path.substringBeforeLast('/', ""))
                ?: throw IOException("Storage directory is unavailable")
            documents[path] = DocumentsContract.createDocument(context.contentResolver, parent, type, path.substringAfterLast('/'))
                ?: throw IOException("Unable to create storage document")
        }
        fun invalidate(path: String, subtree: Boolean = true) {
            if (!subtree) {
                documents.remove(path)
                return
            }
            documents.keys.removeAll { it == path || it.startsWith("$path/") }
            listedDirectories.removeAll { it == path || it.startsWith("$path/") }
        }
    }
}

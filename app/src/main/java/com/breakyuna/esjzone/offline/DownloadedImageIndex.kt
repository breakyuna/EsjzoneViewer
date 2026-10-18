package com.breakyuna.esjzone.offline

import com.breakyuna.esjzone.storage.StorageFile
import java.io.IOException

/** Owned by NovelDownloadStore's IO lock; only committed image files enter the index. */
internal class DownloadedImageIndex(
    private val listFiles: (StorageFile) -> Array<StorageFile>? = { it.listFiles() }
) {
    private val books = HashMap<String, MutableMap<String, StorageFile>>()
    private val imageName = Regex("image-([a-f0-9]{64})\\.[^.]+")

    private fun images(novelDirectory: StorageFile): MutableMap<String, StorageFile> =
        books.getOrPut(novelDirectory.absolutePath) {
            val directory = StorageFile(novelDirectory, "images")
            val files = listFiles(directory) ?: if (!directory.exists()) emptyArray() else {
                throw IOException("Unable to list downloaded images")
            }
            buildMap {
                files.forEach { file ->
                    val digest = imageName.matchEntire(file.name)?.groupValues?.get(1)
                    if (digest != null && file.isFile && file.length() > 0L) put(digest, file)
                }
            }.toMutableMap()
        }

    fun find(novelDirectory: StorageFile, digest: String): StorageFile? {
        val images = images(novelDirectory)
        val file = images[digest] ?: return null
        if (file.isFile && file.length() > 0L) return file
        images.remove(digest)
        return null
    }

    fun committed(novelDirectory: StorageFile, file: StorageFile) {
        val digest = imageName.matchEntire(file.name)?.groupValues?.get(1) ?: return
        if (file.isFile && file.length() > 0L) images(novelDirectory)[digest] = file
    }

    fun remove(novelDirectory: StorageFile) { books.remove(novelDirectory.absolutePath) }
    fun clear() { books.clear() }
}

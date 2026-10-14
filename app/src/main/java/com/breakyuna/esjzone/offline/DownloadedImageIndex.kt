package com.breakyuna.esjzone.offline

import java.io.File
import java.io.IOException

/** Owned by NovelDownloadStore's IO lock; only committed image files enter the index. */
internal class DownloadedImageIndex(
    private val listFiles: (File) -> Array<File>? = { it.listFiles() }
) {
    private val books = HashMap<String, MutableMap<String, File>>()
    private val imageName = Regex("image-([a-f0-9]{64})\\.[^.]+")

    private fun images(novelDirectory: File): MutableMap<String, File> =
        books.getOrPut(novelDirectory.absolutePath) {
            val directory = File(novelDirectory, "images")
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

    fun find(novelDirectory: File, digest: String): File? {
        val images = images(novelDirectory)
        val file = images[digest] ?: return null
        if (file.isFile && file.length() > 0L) return file
        images.remove(digest)
        return null
    }

    fun committed(novelDirectory: File, file: File) {
        val digest = imageName.matchEntire(file.name)?.groupValues?.get(1) ?: return
        if (file.isFile && file.length() > 0L) images(novelDirectory)[digest] = file
    }

    fun remove(novelDirectory: File) { books.remove(novelDirectory.absolutePath) }
    fun clear() { books.clear() }
}

package com.breakyuna.esjzone.ui.reader

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

private data class ReaderFontFile(
    val font: ReaderFont,
    val name: String,
    val url: String,
    val bytes: Long,
    val sha256: String
)

/** Font sources and hashes are also listed with their licenses in assets/font_licenses. */
private val readerFontFiles = listOf(
    ReaderFontFile(
        ReaderFont.SOURCE_HAN_SERIF,
        "source_han_serif.otf",
        "https://raw.githubusercontent.com/adobe-fonts/source-han-serif/2.003R/OTF/SimplifiedChinese/SourceHanSerifSC-Regular.otf",
        24_543_332L,
        "78aa7a328fd974df2d688c8a9fd74a33d8334dfa84ab24d9d11efb2ffc464117"
    ),
    ReaderFontFile(
        ReaderFont.SOURCE_HAN_SANS,
        "source_han_sans.otf",
        "https://raw.githubusercontent.com/adobe-fonts/source-han-sans/2.005R/OTF/SimplifiedChinese/SourceHanSansSC-Regular.otf",
        16_529_832L,
        "f1d8611151880c6c336aabeac4640ef434fa13cbfbf1ffe82d0a71b2a5637256"
    ),
    ReaderFontFile(
        ReaderFont.LXGW_WENKAI,
        "lxgw_wenkai.ttf",
        "https://github.com/lxgw/LxgwWenKai/releases/download/v1.522/LXGWWenKai-Regular.ttf",
        25_575_676L,
        "39ad71264b588165b469e35e6afb162a378dacd1f95348160240ba9038ac3009"
    )
)

internal object ReaderFontStore {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requested = ConcurrentHashMap.newKeySet<ReaderFont>()
    private val familiesState = MutableStateFlow<Map<ReaderFont, FontFamily>>(emptyMap())
    private val families = familiesState.asStateFlow()

    private fun request(context: Context, font: ReaderFont) {
        if (!requested.add(font)) return
        val appContext = context.applicationContext
        scope.launch {
            val fontFile = readerFontFiles.first { it.font == font }
            val file = File(File(appContext.filesDir, "reader_fonts"), fontFile.name)
            if (!publishIfValid(fontFile, file)) {
                val work = OneTimeWorkRequestBuilder<ReaderFontDownloadWorker>()
                    .setInputData(workDataOf(ReaderFontDownloadWorker.KEY_FONT to font.name))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(appContext).enqueueUniqueWork(
                    "reader_font_${font.name}", ExistingWorkPolicy.KEEP, work
                )
            }
        }
    }

    @Composable
    fun family(font: ReaderFont): FontFamily {
        val context = LocalContext.current
        LaunchedEffect(font) { request(context, font) }
        val loaded by families.collectAsState()
        return loaded[font] ?: FontFamily.Default
    }

    @Composable
    fun isAvailable(font: ReaderFont): Boolean {
        val loaded by families.collectAsState()
        return font !in downloadedFonts || font in loaded
    }

    private fun publishIfValid(fontFile: ReaderFontFile, file: File): Boolean {
        if (file.length() != fontFile.bytes || sha256(file) != fontFile.sha256) return false
        val typeface = runCatching { Typeface.Builder(file).build() }.getOrNull() ?: return false
        val family = FontFamily(typeface)
        familiesState.update { it + (fontFile.font to family) }
        return true
    }

    suspend fun download(context: Context, font: ReaderFont) = withContext(Dispatchers.IO) {
        val fontFile = readerFontFiles.first { it.font == font }
        val directory = File(context.filesDir, "reader_fonts").also { it.mkdirs() }
        val destination = File(directory, fontFile.name)
        if (publishIfValid(fontFile, destination)) return@withContext

        val temporary = File(directory, "${fontFile.name}.part")
        try {
            val request = Request.Builder().url(fontFile.url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful || response.request.url.scheme != "https") {
                    throw IOException("Font download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IOException("Font download has no body")
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                body.byteStream().use { input ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            if (count > fontFile.bytes) throw IOException("Font exceeds expected size")
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (count != fontFile.bytes || digest.digest().toHex() != fontFile.sha256) {
                    throw IOException("Font checksum mismatch")
                }
            }
            if (!temporary.renameTo(destination)) throw IOException("Could not save font")
            if (!publishIfValid(fontFile, destination)) throw IOException("Could not load font")
        } finally {
            temporary.delete()
        }
    }

    private fun sha256(file: File): String = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().toHex()
    }.getOrDefault("")

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private val downloadedFonts = readerFontFiles.map { it.font }.toSet()

    private val client = OkHttpClient.Builder()
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.MINUTES)
        .build()
}

class ReaderFontDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val font = ReaderFont.entries.firstOrNull { it.name == inputData.getString(KEY_FONT) }
            ?: return Result.failure()
        return try {
            ReaderFontStore.download(applicationContext, font)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_FONT = "font"
    }
}

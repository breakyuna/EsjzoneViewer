package com.breakyuna.esjzone.app

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.breakyuna.esjzone.data.settings.SettingsDataStore
import com.breakyuna.esjzone.data.settings.ReaderSettingsDataStore
import com.breakyuna.esjzone.data.repository.DatabaseBookmarkRepository
import com.breakyuna.esjzone.data.repository.DatabaseBookshelfRepository
import com.breakyuna.esjzone.data.repository.DatabaseHistoryRepository
import com.breakyuna.esjzone.data.repository.FileDownloadRepository
import com.breakyuna.esjzone.data.repository.NetworkCommentRepository
import com.breakyuna.esjzone.data.repository.NetworkCommunityRepository
import com.breakyuna.esjzone.data.repository.NetworkNovelRepository
import com.breakyuna.esjzone.data.repository.NetworkSearchRepository
import com.breakyuna.esjzone.data.repository.NetworkSessionRepository
import com.breakyuna.esjzone.database.GeneralDatabase
import com.breakyuna.esjzone.domain.repository.BookmarkRepository
import com.breakyuna.esjzone.domain.repository.BookshelfRepository
import com.breakyuna.esjzone.domain.repository.CommentRepository
import com.breakyuna.esjzone.domain.repository.CommunityRepository
import com.breakyuna.esjzone.domain.repository.DownloadRepository
import com.breakyuna.esjzone.domain.repository.HistoryRepository
import com.breakyuna.esjzone.domain.repository.NovelRepository
import com.breakyuna.esjzone.domain.repository.ReaderRepository
import com.breakyuna.esjzone.domain.repository.SearchRepository
import com.breakyuna.esjzone.domain.repository.SessionRepository
import com.breakyuna.esjzone.domain.repository.SettingsRepository
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.features.HomeDataCache
import com.breakyuna.esjzone.offline.NovelDownloadStore
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application-scoped ownership of infrastructure and repository adapters.
 *
 * This is deliberately a small hand-written container for the transition period. It has no
 * Compose dependency and does not alter the existing database or download contracts.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: GeneralDatabase = Room.databaseBuilder(
        appContext,
        GeneralDatabase::class.java,
        "general"
    ).addMigrations(
        GeneralDatabase.MIGRATION_1_2,
        GeneralDatabase.MIGRATION_2_3,
        GeneralDatabase.MIGRATION_3_4,
        GeneralDatabase.MIGRATION_4_5,
        GeneralDatabase.MIGRATION_5_6,
        GeneralDatabase.MIGRATION_6_7,
        GeneralDatabase.MIGRATION_7_8
    ).fallbackToDestructiveMigrationOnDowngrade().build()

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(appContext, 0.20)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(appContext.cacheDir.resolve("image_cache").toOkioPath())
                .maxSizePercent(0.05)
                .build()
        }
        .build()

    /** Reader images use the same cache and the isolated wenku CookieJar. */
    val wenkuImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .memoryCache { imageLoader.memoryCache ?: error("Image memory cache unavailable") }
            .diskCache { imageLoader.diskCache ?: error("Image disk cache unavailable") }
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { EsjzoneClient.wenkuImageClient() }))
            }
            .build()
    }

    val session: SessionRepository = NetworkSessionRepository()
    val settingsDataStore = SettingsDataStore(appContext)
    val readerSettingsDataStore = ReaderSettingsDataStore(appContext)
    val settings: SettingsRepository = settingsDataStore
    val novel: NovelRepository = NetworkNovelRepository()
    val search: SearchRepository = NetworkSearchRepository()
    val bookshelf: BookshelfRepository = DatabaseBookshelfRepository(database)
    val history: HistoryRepository = DatabaseHistoryRepository(database)
    val bookmarks: BookmarkRepository = DatabaseBookmarkRepository(database)
    val downloads: DownloadRepository = FileDownloadRepository()
    val comments: CommentRepository = NetworkCommentRepository()
    val community: CommunityRepository = NetworkCommunityRepository()
    val reader: ReaderRepository = novel as ReaderRepository

    suspend fun initializeAsync() = withContext(Dispatchers.IO) {
        coroutineScope {
            val clientJob = launch { EsjzoneClient.initialize(appContext) }
            val downloadJob = launch { NovelDownloadStore.initialize(appContext) }
            val homeCacheJob = launch { HomeDataCache.initialize(appContext) }
            val legacyCacheCleanupJob = launch {
                val legacyCacheDir = appContext.filesDir.resolve("image_cache")
                if (legacyCacheDir.exists()) {
                    runCatching { legacyCacheDir.deleteRecursively() }
                }
            }
            clientJob.join()
            downloadJob.join()
            homeCacheJob.join()
            legacyCacheCleanupJob.join()
        }
    }
}

package com.breakyuna.esjzone.app

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.disk.DiskCache
import com.breakyuna.esjzone.network.CacheImageDiskCache
import com.breakyuna.esjzone.network.CacheImageEventListener
import com.breakyuna.esjzone.network.CacheStorage
import com.breakyuna.esjzone.network.CacheAnalysis
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
import com.breakyuna.esjzone.network.features.HistoryDataCache
import com.breakyuna.esjzone.offline.NovelDownloadStore
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        GeneralDatabase.MIGRATION_7_8,
        GeneralDatabase.MIGRATION_8_9,
        GeneralDatabase.MIGRATION_9_10,
        GeneralDatabase.MIGRATION_10_11
    ).fallbackToDestructiveMigrationOnDowngrade().build()

    private val imageHttpClient = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply {
            maxRequests = 8
            maxRequestsPerHost = 8
        })
        .build()

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(appContext, 0.20)
                .build()
        }
        .diskCache {
            CacheImageDiskCache(DiskCache.Builder()
                .directory(appContext.cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(Long.MAX_VALUE)
                .build())
        }
        .eventListener(CacheImageEventListener())
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient }))
        }
        .build()

    /** Reader images use the same cache and the isolated wenku CookieJar. */
    val wenkuImageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .memoryCache { imageLoader.memoryCache ?: error("Image memory cache unavailable") }
            .diskCache { imageLoader.diskCache ?: error("Image disk cache unavailable") }
            .eventListener(CacheImageEventListener())
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { EsjzoneClient.wenkuImageClient() }))
            }
            .build()
    }

    val session: SessionRepository = NetworkSessionRepository()
    val storage = com.breakyuna.esjzone.storage.UserStorage(appContext)
    val autoBackup = com.breakyuna.esjzone.backup.AutoBackup(appContext)
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

    private val cacheInitializationMutex = Mutex()
    private var cachesInitialized = false
    private val maintenanceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Shared by Activity startup and workers before either can open or trim page caches. */
    suspend fun initializeCaches() = withContext(Dispatchers.IO) {
        cacheInitializationMutex.withLock {
            if (cachesInitialized) return@withLock
            CacheStorage.initialize(imageLoader.diskCache as CacheImageDiskCache, settingsDataStore.readCacheLimitMiB())
            coroutineScope {
                launch { EsjzoneClient.initialize(appContext) }
                launch {
                    HomeDataCache.initialize(appContext)
                    HistoryDataCache.initialize(appContext)
                }
            }
            cachesInitialized = true
            // Image inventory restoration and quota eviction can touch every cached file.
            // They must not keep StartupCoordinator in Starting until maintenance finishes.
            maintenanceScope.launch {
                runCatching { CacheAnalysis.initialize(appContext) }.onFailure {
                    com.breakyuna.esjzone.util.AppLogger.w("AppContainer", "Unable to initialize cache analysis", it)
                }
                runCatching { CacheStorage.start() }.onFailure {
                    com.breakyuna.esjzone.util.AppLogger.w("AppContainer", "Unable to trim caches", it)
                }
                val legacyCacheDir = appContext.filesDir.resolve("image_cache")
                if (legacyCacheDir.exists()) {
                    runCatching { legacyCacheDir.deleteRecursively() }
                }
            }
        }
    }

    suspend fun initializeAsync() = withContext(Dispatchers.IO) {
        database.cacheDao().deleteObsoleteReaderUnderlines()
        CoverLoadingPolicy.initialize(appContext)
        coroutineScope {
            launch { initializeCaches() }
            launch { initializeDownloads() }
        }
    }

    suspend fun initializeDownloads() = withContext(Dispatchers.IO) {
        storage.initialize()
        // A background transfer must not block startup or reset its in-memory root switch.
        NovelDownloadStore.initialize(appContext, storage.downloads(), onlyIfUninitialized = true)
    }
}

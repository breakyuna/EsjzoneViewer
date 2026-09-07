package com.breakyuna.esjzone.app

import android.content.Context
import androidx.room.Room
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
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
import com.breakyuna.esjzone.offline.NovelDownloadStore
import okio.Path.Companion.toOkioPath

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
        GeneralDatabase.MIGRATION_5_6
    ).build()

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(appContext, 0.15)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(appContext.filesDir.resolve("image_cache").toOkioPath())
                .maxSizePercent(0.05)
                .build()
        }
        .build()

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

    init {
        EsjzoneClient.initialize(appContext)
        NovelDownloadStore.initialize(appContext)
    }
}

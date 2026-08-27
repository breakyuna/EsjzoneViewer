package com.breakyuna.esjzone.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.breakyuna.esjzone.database.dao.CacheDao
import com.breakyuna.esjzone.database.dao.SearchHistoryDao
import com.breakyuna.esjzone.database.entity.Cache
import com.breakyuna.esjzone.database.entity.SearchHistory

@Database(entities = [Cache::class, SearchHistory::class], version = 1, exportSchema = false)
abstract class GeneralDatabase : RoomDatabase() {

    abstract fun cacheDao(): CacheDao

    abstract fun searchHistoryDao(): SearchHistoryDao

}
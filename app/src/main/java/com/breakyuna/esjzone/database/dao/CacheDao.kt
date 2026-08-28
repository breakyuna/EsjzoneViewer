package com.breakyuna.esjzone.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.breakyuna.esjzone.database.entity.Cache

@Dao
interface CacheDao {

    @Query("SELECT * FROM cache")
    fun getAll(): List<Cache>

    @Query("SELECT * FROM cache WHERE cache_key = :key LIMIT 1")
    fun findByKey(key: String): Cache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(vararg caches: Cache)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertNotExists(vararg caches: Cache)

    @Update
    fun update(vararg caches: Cache)

    @Delete
    fun delete(vararg caches: Cache)

    @Query("DELETE FROM cache WHERE cache_key = :key")
    fun deleteByKey(key: String)

    @Query("SELECT EXISTS(SELECT * FROM cache WHERE cache_key = :key)")
    fun exists(key: String): Boolean

}

/**
 * Stores a cache value even when the row was lost because of a fresh install,
 * damaged data, or a previous database migration.
 */
fun CacheDao.put(key: String, value: String) {
    val cache = findByKey(key)
    if (cache == null) {
        insertAll(Cache(key = key, value = value))
    } else if (cache.value != value) {
        cache.value = value
        update(cache)
    }
}

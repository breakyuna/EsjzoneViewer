package com.breakyuna.esjzone.database.dao
import androidx.room.*
import com.breakyuna.esjzone.database.entity.BookshelfGroup
import com.breakyuna.esjzone.database.entity.BookshelfGroupMember
import kotlinx.coroutines.flow.Flow
@Dao
interface BookshelfGroupDao {
    @Query("SELECT * FROM bookshelf_groups WHERE scope = :scope ORDER BY name")
    fun observeGroups(scope: String): Flow<List<BookshelfGroup>>
    @Query("SELECT * FROM bookshelf_group_members WHERE scope = :scope")
    fun observeMembers(scope: String): Flow<List<BookshelfGroupMember>>
    @Query("SELECT * FROM bookshelf_groups WHERE scope = :scope")
    suspend fun groups(scope: String): List<BookshelfGroup>
    @Query("SELECT * FROM bookshelf_group_members WHERE scope = :scope")
    suspend fun members(scope: String): List<BookshelfGroupMember>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(group: BookshelfGroup)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun assign(member: BookshelfGroupMember)
    @Query("DELETE FROM bookshelf_group_members WHERE scope = :scope AND bookKey IN (:keys)")
    suspend fun ungroup(scope: String, keys: List<String>)
    @Query("DELETE FROM bookshelf_groups WHERE scope = :scope AND name = :name")
    suspend fun deleteGroup(scope: String, name: String)
    @Query("DELETE FROM bookshelf_group_members WHERE scope = :scope AND groupName = :name")
    suspend fun deleteMembers(scope: String, name: String)
    @Query("UPDATE bookshelf_group_members SET groupName = :newName WHERE scope = :scope AND groupName = :oldName")
    suspend fun renameMembers(scope: String, oldName: String, newName: String)
    @Transaction
    suspend fun remove(scope: String, name: String) {
        deleteMembers(scope, name)
        deleteGroup(scope, name)
    }
    @Transaction
    suspend fun rename(scope: String, oldName: String, newName: String) {
        require(newName.isNotBlank() && groups(scope).none { it.name == newName })
        add(BookshelfGroup(scope, newName))
        renameMembers(scope, oldName, newName)
        deleteGroup(scope, oldName)
    }
    @Transaction
    suspend fun move(scope: String, keys: List<String>, name: String?) {
        if (name == null) ungroup(scope, keys)
        else {
            require(groups(scope).any { it.name == name })
            keys.forEach { assign(BookshelfGroupMember(scope, it, name)) }
        }
    }
}

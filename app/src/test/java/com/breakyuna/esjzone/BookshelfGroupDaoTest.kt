package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.dao.BookshelfGroupDao
import com.breakyuna.esjzone.database.entity.BookshelfGroup
import com.breakyuna.esjzone.database.entity.BookshelfGroupMember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Exercises group operation results; Room persistence/transactions need instrumentation. */
class BookshelfGroupDaoTest {
    @Test fun createAndMoveKeepsUnselectedBooksAndOtherAccountsInPlace() = runBlocking {
        val dao = MemoryGroupDao()
        dao.create("local", "Reading", listOf("1", "2"))
        dao.create("other", "Reading", listOf("1"))
        dao.create("local", "Finished", listOf("1"))

        assertEquals(mapOf("1" to "Finished", "2" to "Reading"), dao.members("local").associate { it.bookKey to it.groupName })
        assertEquals("Reading", dao.members("other").single().groupName)
    }

    @Test fun duplicateCreationDoesNotMoveBooksIntoAnExistingGroup() = runBlocking {
        val dao = MemoryGroupDao()
        dao.create("local", "Reading", listOf("1"))
        dao.create("local", "Finished")
        try {
            dao.create("local", "Finished", listOf("1"))
            fail("Duplicate creation must fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals("Reading", dao.members("local").single().groupName)
    }

    @Test fun renamePreservesMembershipAndRejectsMergingGroups() = runBlocking {
        val dao = MemoryGroupDao()
        dao.create("local", "Reading", listOf("1", "2"))
        dao.rename("local", "Reading", "Finished")
        dao.rename("local", "Finished", "Finished")
        assertEquals(listOf("Finished"), dao.groups("local").map { it.name })
        assertTrue(dao.members("local").all { it.groupName == "Finished" })
        dao.create("local", "Other")
        try {
            dao.rename("local", "Finished", "Other")
            fail("Renaming cannot merge into another group")
        } catch (_: IllegalArgumentException) { }
        assertEquals(2, dao.members("local").count { it.groupName == "Finished" })
    }

    @Test fun missingRenameSourceDoesNotCreateAPhantomGroup() = runBlocking {
        val dao = MemoryGroupDao()
        try {
            dao.rename("local", "Missing", "New")
            fail("Missing source must fail")
        } catch (_: IllegalArgumentException) { }
        assertTrue(dao.groups("local").isEmpty())
    }

    @Test fun moveAndUngroupOnlyChangeSelectedBooks() = runBlocking {
        val dao = MemoryGroupDao()
        dao.create("local", "Reading", listOf("1", "2"))
        dao.create("local", "Finished", listOf("3"))
        dao.move("local", listOf("1", "3"), "Finished")
        dao.move("local", listOf("1"), null)
        assertEquals(mapOf("2" to "Reading", "3" to "Finished"), dao.members("local").associate { it.bookKey to it.groupName })
        assertEquals(2, dao.groups("local").size)
    }

    @Test fun deletionUngroupsMembersWithinTheCurrentAccount() = runBlocking {
        val dao = MemoryGroupDao()
        dao.create("local", "Reading", listOf("1"))
        dao.create("local", "Finished", listOf("2"))
        dao.create("other", "Reading", listOf("1"))
        dao.remove("local", "Reading")
        assertEquals(listOf("Finished"), dao.groups("local").map { it.name })
        assertEquals(listOf("2"), dao.members("local").map { it.bookKey })
        assertEquals("Reading", dao.members("other").single().groupName)
    }

    private class MemoryGroupDao : BookshelfGroupDao {
        private val savedGroups = mutableSetOf<BookshelfGroup>()
        private val savedMembers = mutableMapOf<Pair<String, String>, BookshelfGroupMember>()
        override fun observeGroups(scope: String): Flow<List<BookshelfGroup>> = flowOf(savedGroups.filter { it.scope == scope })
        override fun observeMembers(scope: String): Flow<List<BookshelfGroupMember>> = flowOf(savedMembers.values.filter { it.scope == scope })
        override suspend fun groups(scope: String) = savedGroups.filter { it.scope == scope }
        override suspend fun members(scope: String) = savedMembers.values.filter { it.scope == scope }
        override suspend fun add(group: BookshelfGroup) { savedGroups.add(group) }
        override suspend fun assign(member: BookshelfGroupMember) { savedMembers[member.scope to member.bookKey] = member }
        override suspend fun ungroup(scope: String, keys: List<String>) { keys.forEach { savedMembers.remove(scope to it) } }
        override suspend fun deleteGroup(scope: String, name: String) { savedGroups.remove(BookshelfGroup(scope, name)) }
        override suspend fun deleteMembers(scope: String, name: String) {
            savedMembers.entries.removeAll { it.value.scope == scope && it.value.groupName == name }
        }
        override suspend fun renameMembers(scope: String, oldName: String, newName: String) {
            savedMembers.replaceAll { _, member ->
                if (member.scope == scope && member.groupName == oldName) member.copy(groupName = newName) else member
            }
        }
    }
}

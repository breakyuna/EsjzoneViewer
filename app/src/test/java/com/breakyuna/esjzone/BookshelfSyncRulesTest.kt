package com.breakyuna.esjzone

import com.breakyuna.esjzone.database.BookshelfSyncRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookshelfSyncRulesTest {
    @Test
    fun cloudOnlyIsImportedAndLocalOnlyIsRetained() {
        assertEquals(setOf("local", "cloud"), BookshelfSyncRules.mergedVisibleKeys(
            localKeys = setOf("local"), remoteKeys = setOf("cloud"), tombstoneKeys = emptySet()
        ))
    }

    @Test
    fun commonEntryIsNotReplaced() {
        assertFalse(BookshelfSyncRules.shouldImport("same", setOf("same"), emptySet()))
    }

    @Test
    fun tombstoneBlocksCloudImport() {
        assertFalse(BookshelfSyncRules.shouldImport("removed", emptySet(), setOf("removed")))
        assertEquals(setOf("kept"), BookshelfSyncRules.mergedVisibleKeys(
            setOf("removed", "kept"), setOf("removed"), setOf("removed")
        ))
    }

    @Test
    fun processedRemovalRemainsExcludedAfterTombstoneCleanup() {
        val exclusions = BookshelfSyncRules.excludedImportKeys(
            initialTombstoneKeys = setOf("removed"),
            processedRemovalKeys = setOf("removed")
        )
        assertFalse(BookshelfSyncRules.shouldImport("removed", emptySet(), exclusions))
    }

    @Test
    fun emptyCloudNeverClearsLocal() {
        assertEquals(setOf("one", "two"), BookshelfSyncRules.mergedVisibleKeys(
            setOf("one", "two"), emptySet(), emptySet()
        ))
        assertTrue(BookshelfSyncRules.shouldImport("new", emptySet(), emptySet()))
    }

    @Test
    fun staleResponseCannotOverwriteQuickReverseIntent() {
        assertFalse(BookshelfSyncRules.shouldApplyResponse(currentVersion = 2, responseVersion = 1))
        assertTrue(BookshelfSyncRules.shouldApplyResponse(currentVersion = 2, responseVersion = 2))
    }

    @Test
    fun duplicateRemotePagesCollapseToOneStableKey() {
        assertEquals(listOf("a", "b"), BookshelfSyncRules.deduplicateRemoteKeys(
            listOf("a", "b", "a", " ", "b")
        ))
    }
}

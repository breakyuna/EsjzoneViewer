package com.breakyuna.esjzone

import com.breakyuna.esjzone.ui.page.StorageLocationModel
import com.breakyuna.esjzone.ui.screen.canContinueStorageSetup
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSetupPolicyTest {
    private val location = "content://fixture/tree/books"

    @Test
    fun noSelectionOrCancelledPickerCannotContinue() {
        assertFalse(canContinueStorageSetup("", StorageLocationModel.Status()))
    }

    @Test
    fun persistedLocationCannotContinueWhileMigrationIsStillFinishing() {
        assertFalse(canContinueStorageSetup(location, StorageLocationModel.Status(busy = true)))
    }

    @Test
    fun failedSelectionOrMigrationRequiresRetry() {
        for (result in listOf(R.string.storage_access_failed, R.string.storage_location_failed)) {
            assertFalse(canContinueStorageSetup("", StorageLocationModel.Status(result = result)))
            assertFalse(canContinueStorageSetup(location, StorageLocationModel.Status(result = result)))
        }
    }

    @Test
    fun completedMigrationAllowsContinueIncludingOriginalCleanupWarning() {
        for (result in listOf(R.string.storage_location_done, R.string.storage_location_cleanup_failed)) {
            assertTrue(canContinueStorageSetup(location, StorageLocationModel.Status(result = result)))
            assertFalse(canContinueStorageSetup("", StorageLocationModel.Status(result = result)))
        }
        // A recreated gate may have a committed tree without an in-memory operation result.
        assertTrue(canContinueStorageSetup(location, StorageLocationModel.Status()))
    }
}

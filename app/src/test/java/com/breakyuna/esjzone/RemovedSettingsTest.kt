package com.breakyuna.esjzone

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.breakyuna.esjzone.data.settings.REMOVED_SETTING_KEYS
import com.breakyuna.esjzone.data.settings.hasRemovedSettings
import com.breakyuna.esjzone.data.settings.removeRemovedSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The 「更新网站观看记录」 switch was removed; its stored value is dropped without touching other settings. */
class RemovedSettingsTest {
    private val websiteHistory = booleanPreferencesKey("update_website_history")
    private val adult = booleanPreferencesKey("adult")
    private val domain = stringPreferencesKey("domain")

    @Test
    fun theWebsiteHistoryKeyIsListedAsRemoved() {
        assertTrue(websiteHistory in REMOVED_SETTING_KEYS)
    }

    @Test
    fun anOldStoreLosesOnlyTheRemovedKey() {
        for (value in listOf(true, false)) {
            val stored = mutablePreferencesOf(websiteHistory to value, adult to false, domain to "www.esjzone.one")
            assertTrue(hasRemovedSettings(stored))
            removeRemovedSettings(stored)
            assertFalse(hasRemovedSettings(stored))
            assertFalse(websiteHistory in stored)
            assertEquals(false, stored[adult])
            assertEquals("www.esjzone.one", stored[domain])
        }
    }

    @Test
    fun aCleanStoreNeedsNoWrite() {
        val stored = mutablePreferencesOf(adult to true)
        assertFalse(hasRemovedSettings(stored))
        removeRemovedSettings(stored)
        assertEquals(mutablePreferencesOf(adult to true), stored)
    }
}

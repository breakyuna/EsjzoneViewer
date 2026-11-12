package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecord
import com.breakyuna.esjzone.novellibrary.user.ExperienceRecordType
import com.breakyuna.esjzone.ui.page.ExperienceRecordSnapshot
import com.breakyuna.esjzone.ui.page.ExperienceRecordsState
import com.breakyuna.esjzone.ui.page.PageExperienceRecordsCache
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExperienceRecordsCacheTest {
    private val message = ExperienceRecordsState(
        type = ExperienceRecordType.MESSAGE,
        rows = listOf(ExperienceRecord("2026-11-12", "+2", null, listOf("其他内容"))),
        total = 50,
        loaded = true,
        endReached = false
    )

    @Test fun snapshotPreservesRowsAndPagingAndRejectsBadData() {
        val json = ExperienceRecordSnapshot.encode(message)
        assertEquals(message, ExperienceRecordSnapshot.decode(ExperienceRecordType.MESSAGE, json))
        assertNull(ExperienceRecordSnapshot.decode(ExperienceRecordType.MESSAGE, "{bad json"))
        assertNull(ExperienceRecordSnapshot.decode(ExperienceRecordType.MESSAGE, """{"total":-1,"rows":[],"endReached":true}"""))
    }

    @Test fun snapshotsAreScopedByAccountAndCategoryAndRespectClear() = runBlocking {
        val directory = Files.createTempDirectory("experience-records-cache-").toFile()
        PageCache.initializeDirectory(directory)
        try {
            val first = PageExperienceRecordsCache(Authorization("account-one", "session-one", "www.esjzone.cc"))
            val second = PageExperienceRecordsCache(Authorization("account-two", "session-two", "www.esjzone.cc"))
            val guest = PageExperienceRecordsCache(Authorization(domain = "www.esjzone.cc"))
            first.writer(ExperienceRecordType.MESSAGE)(message)
            // Reinitialization stands in for a later process reading the persisted file.
            PageCache.initializeDirectory(directory)
            assertEquals(message, first.read(ExperienceRecordType.MESSAGE))
            assertNull(first.read(ExperienceRecordType.COMMENT))
            assertNull(second.read(ExperienceRecordType.MESSAGE))
            guest.writer(ExperienceRecordType.MESSAGE)(message)
            assertNull(guest.read(ExperienceRecordType.MESSAGE))

            val obsolete = first.writer(ExperienceRecordType.MESSAGE)
            val newer = first.writer(ExperienceRecordType.MESSAGE)
            newer(message)
            obsolete(message.copy(total = 999))
            assertEquals(message, first.read(ExperienceRecordType.MESSAGE))

            val oldWrite = first.writer(ExperienceRecordType.MESSAGE)
            PageCache.clear()
            oldWrite(message)
            assertNull(first.read(ExperienceRecordType.MESSAGE))
        } finally {
            directory.deleteRecursively()
        }
    }
}

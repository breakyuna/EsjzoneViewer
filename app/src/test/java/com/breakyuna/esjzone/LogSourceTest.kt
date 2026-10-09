package com.breakyuna.esjzone

import com.breakyuna.esjzone.util.LogEntry
import com.breakyuna.esjzone.util.LogLevel
import com.breakyuna.esjzone.util.LogSource
import com.breakyuna.esjzone.util.logSourceForTag
import com.breakyuna.esjzone.util.logSourceForUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogSourceTest {
    @Test fun separatesSiteComponentsFromApplicationLogs() {
        assertEquals(LogSource.ESJZONE, logSourceForTag("AuthorizationCookieJar"))
        assertEquals(LogSource.ESJZONE, logSourceForTag("GetHomeData"))
        assertEquals(LogSource.WENKU8, logSourceForTag("Wenku8PageModel"))
        assertEquals(LogSource.APP, logSourceForTag("AppLogger"))
    }

    @Test fun sharedLoadersFollowTheRequestedHostRatherThanTheCurrentSite() {
        assertEquals(LogSource.WENKU8, logSourceForUrl("https://www.wenku8.net/book/123.htm"))
        assertEquals(LogSource.ESJZONE, logSourceForUrl("https://www.esjzone.one/detail/123.html"))
        assertEquals(LogSource.ESJZONE, logSourceForUrl("/detail/123.html"))
        assertEquals(LogSource.APP, logSourceForUrl("https://example.org/wenku8.net"))
        val entry = LogEntry(level = LogLevel.ERROR, tag = "NovelPageModel", message = "Fixture",
            source = LogSource.WENKU8)
        assertEquals(LogSource.WENKU8, entry.source)
        assertTrue(entry.toFormattedString().contains("[Wenku8]"))
    }
}

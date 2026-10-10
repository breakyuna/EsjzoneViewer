package com.breakyuna.esjzone

import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.diagnosticHtml
import com.breakyuna.esjzone.util.diagnosticSql
import com.breakyuna.esjzone.util.diagnosticUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugDiagnosticsTest {
    @Test fun urlsPreserveRoutesButRemoveAccountQueryFragmentAndSearchValues() {
        val url = diagnosticUrl("https://fixture-user:fixture-password@www.wenku8.net/login.php?jumpurl=fixture-private&token=fixture-secret#fixture-fragment")
        assertTrue(url.contains("/login.php"))
        assertTrue(url.contains("queryNames=[jumpurl, token]"))
        assertFalse(url.contains("fixture-"))
        assertFalse(diagnosticUrl("https://www.esjzone.cc/tags/fixture-query/1.html").contains("fixture-query"))
        assertTrue(diagnosticUrl("https://www.wenku8.net/book/123.htm").contains("/book/123.htm"))
    }

    @Test fun htmlDiagnosticsDescribeFormWithoutValuesOrBodyText() {
        val shape = diagnosticHtml("""
            <html><body>fixture-private-body<form name="frmlogin">
            <input name="username" value="fixture-user"><input type="password" value="fixture-password">
            <select name="usecookie"><option value="86400">fixture-option</option></select>
            </form></body></html>
        """.trimIndent())
        assertTrue(shape.contains("wenkuLogin=1"))
        assertTrue(shape.contains("passwordFields=1"))
        assertTrue(shape.contains("loginDurations=1"))
        assertFalse(shape.contains("fixture-"))
    }

    @Test fun sqlDiagnosticsExcludeLiteralValuesAndArguments() {
        val shape = diagnosticSql("INSERT INTO Cache VALUES ('fixture-secret', ?)", 1)
        assertTrue(shape.contains("operation=INSERT"))
        assertTrue(shape.contains("argumentCount=1"))
        assertFalse(shape.contains("fixture-secret"))
    }

    @Test fun sanitizerCoversGenericSecretsAndExceptionUrls() {
        val output = AppLogger.sanitizeForDisplay("""
            token=fixture-token api_key=fixture-key username=fixture-user
            {"refresh_token":"fixture-refresh","password":"fixture-password with spaces"}
            Cookie: fixture-cookie=fixture-value
            failed at https://www.wenku8.net/login.php?jumpurl=fixture-private#fixture-fragment
        """.trimIndent())
        assertFalse(output.contains("fixture-"))
        assertTrue(output.contains("login.php"))
    }

    @Test fun repeatedSanitizationPreservesRequestedAndFinalRoutes() {
        val message = "requested=${diagnosticUrl("https://www.wenku8.net/index.php")}, " +
            "final=${diagnosticUrl("https://www.wenku8.net/login.php?do=submit")}."
        val output = AppLogger.sanitizeForDisplay(AppLogger.sanitizeForDisplay(message))
        assertTrue(output.contains("/index.php,"))
        assertTrue(output.contains("/login.php queryNames=[do]"))
        assertFalse(output.contains("submit"))
    }
}

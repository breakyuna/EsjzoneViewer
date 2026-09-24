package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.isFavoriteToggleSuccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteToggleResponseTest {
    @Test
    fun acceptsConfirmedSuccessWithHtmlContentTypeResponseBody() {
        assertTrue(isFavoriteToggleSuccess(
            """{"status":200,"msg":"","url":"","id":"","swal":"","favorite":3714}"""
        ))
    }

    @Test
    fun rejectsUnconfirmedToggleSoNextSyncCanCheckRemoteState() {
        listOf(
            """{"status":214,"msg":"request denied"}""",
            """{"status":"200","favorite":3714}""",
            """{"status":200.5,"favorite":3714}""",
            """{"favorite":3714}""",
            "<html>login</html>",
            "",
            "invalid json"
        ).forEach { body -> assertFalse(isFavoriteToggleSuccess(body)) }
    }
}

package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.profileAvatarUrl
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileAvatarParserTest {
    @Test fun lazyAvatarUsesRealImageInsteadOfPlaceholder() {
        val document = Jsoup.parse("""
            <aside><img src="/images/grey.png" data-src=" /uploads/avatar.jpg "></aside>
        """.trimIndent())
        assertEquals("/uploads/avatar.jpg", profileAvatarUrl(document))
    }

    @Test fun regularAvatarStillUsesSrcWhenLazyAttributeIsBlank() {
        val document = Jsoup.parse("""
            <aside><img src="/uploads/avatar.jpg" data-src=" "></aside>
        """.trimIndent())
        assertEquals("/uploads/avatar.jpg", profileAvatarUrl(document))
    }
}

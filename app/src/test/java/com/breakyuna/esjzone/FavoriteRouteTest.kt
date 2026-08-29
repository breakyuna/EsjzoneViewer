package com.breakyuna.esjzone

import com.breakyuna.esjzone.network.features.favoritePageUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteRouteTest {

    @Test
    fun favoritePageUrl_entersSortModeBeforeFollowingNumericPager() {
        assertEquals(
            "https://www.esjzone.cc/my/favorite/new/",
            favoritePageUrl("new", 1)
        )
        assertEquals(
            "https://www.esjzone.cc/my/favorite/udate/",
            favoritePageUrl("udate", 1)
        )
        assertEquals(
            "https://www.esjzone.cc/my/favorite/2",
            favoritePageUrl("new", 2)
        )
        assertEquals(
            "https://www.esjzone.cc/my/favorite/udate/2",
            favoritePageUrl("udate", 2)
        )
    }
}

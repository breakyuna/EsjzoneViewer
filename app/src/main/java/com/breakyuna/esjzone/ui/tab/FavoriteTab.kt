package com.breakyuna.esjzone.ui.tab

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.page.FavoritePage

object FavoriteTab : Tab {

    private fun readResolve(): Any = FavoriteTab

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 3u,
            title = stringResource(id = R.string.bookshelf),
            icon = rememberVectorPainter(image = Icons.Filled.AutoStories)
        )

    @Composable
    override fun Content() {
        FavoritePage.Content(showBack = false)
    }
}

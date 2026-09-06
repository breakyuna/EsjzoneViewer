package com.breakyuna.esjzone.ui.tab

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import com.breakyuna.esjzone.ui.navigation.AppTab
import com.breakyuna.esjzone.ui.navigation.AppTabOptions
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.page.FavoritePage

object FavoriteTab : AppTab {

    private fun readResolve(): Any = FavoriteTab

    override val options: AppTabOptions
        @Composable
        get() = AppTabOptions(
            index = 2,
            title = stringResource(id = R.string.bookshelf),
            icon = rememberVectorPainter(image = Icons.Filled.AutoStories)
        )

    @Composable
    override fun Content() {
        FavoritePage.Content(showBack = false)
    }
}

package com.breakyuna.esjzone

import androidx.compose.runtime.mutableStateOf
import com.breakyuna.esjzone.ui.theme.catppuccin.CatppuccinThemeType

object GlobalSettings {

    val DOMAINS = listOf(
        "www.esjzone.cc",
        "www.esjzone.one"
    )

    var adult = mutableStateOf(true)

    var theme = mutableStateOf(CatppuccinThemeType.LATTE_YELLOW)

    var domain = mutableStateOf(DOMAINS[0])

}
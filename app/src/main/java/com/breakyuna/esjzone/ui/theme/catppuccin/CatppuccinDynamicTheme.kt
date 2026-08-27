package com.breakyuna.esjzone.ui.theme.catppuccin

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeBlueTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeFlamingoTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeGreenTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeLavenderTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeMaroonTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeMauveTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappePeachTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappePinkTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeRedTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeRosewaterTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeSapphireTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeSkyTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeTealTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.frappe.CatppuccinFrappeYellowTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteBlueTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteFlamingoTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteGreenTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteLavenderTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteMaroonTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteMauveTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLattePeachTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLattePinkTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteRedTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteRosewaterTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteSapphireTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteSkyTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteTealTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.latte.CatppuccinLatteYellowTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoBlueTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoFlamingoTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoGreenTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoLavenderTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoMaroonTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoMauveTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoPeachTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoPinkTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoRedTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoRosewaterTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoSapphireTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoSkyTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoTealTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.macchiato.CatppuccinMacchiatoYellowTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaBlueTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaFlamingoTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaGreenTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaLavenderTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaMaroonTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaMauveTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaPeachTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaPinkTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaRedTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaRosewaterTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaSapphireTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaSkyTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaTealTheme
import com.breakyuna.esjzone.ui.theme.catppuccin.mocha.CatppuccinMochaYellowTheme

@Composable
fun CatppuccinDynamicTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val theme by remember {
        GlobalSettings.theme
    }
    val colorScheme by remember {
        derivedStateOf {
            if (!useDarkTheme) {
                theme.lightColorScheme
            } else {
                theme.darkColorScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Composable
private fun CatppuccinThemeTypeOld(
    type: CatppuccinThemeType,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {

    when (type) {
        CatppuccinThemeType.FRAPPE_BLUE -> CatppuccinFrappeBlueTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_FLAMINGO -> CatppuccinFrappeFlamingoTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_GREEN -> CatppuccinFrappeGreenTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_LAVENDER -> CatppuccinFrappeLavenderTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_MAROON -> CatppuccinFrappeMaroonTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_MAUVE -> CatppuccinFrappeMauveTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_PEACH -> CatppuccinFrappePeachTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_PINK -> CatppuccinFrappePinkTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_RED -> CatppuccinFrappeRedTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_ROSEWATER -> CatppuccinFrappeRosewaterTheme(
            useDarkTheme,
            content
        )

        CatppuccinThemeType.FRAPPE_SAPPHIRE -> CatppuccinFrappeSapphireTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_SKY -> CatppuccinFrappeSkyTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_TEAL -> CatppuccinFrappeTealTheme(useDarkTheme, content)
        CatppuccinThemeType.FRAPPE_YELLOW -> CatppuccinFrappeYellowTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_BLUE -> CatppuccinLatteBlueTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_FLAMINGO -> CatppuccinLatteFlamingoTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_GREEN -> CatppuccinLatteGreenTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_LAVENDER -> CatppuccinLatteLavenderTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_MAROON -> CatppuccinLatteMaroonTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_MAUVE -> CatppuccinLatteMauveTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_PEACH -> CatppuccinLattePeachTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_PINK -> CatppuccinLattePinkTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_RED -> CatppuccinLatteRedTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_ROSEWATER -> CatppuccinLatteRosewaterTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_SAPPHIRE -> CatppuccinLatteSapphireTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_SKY -> CatppuccinLatteSkyTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_TEAL -> CatppuccinLatteTealTheme(useDarkTheme, content)
        CatppuccinThemeType.LATTE_YELLOW -> CatppuccinLatteYellowTheme(useDarkTheme, content)
        CatppuccinThemeType.MACCHIATO_BLUE -> CatppuccinMacchiatoBlueTheme(useDarkTheme, content)
        CatppuccinThemeType.MACCHIATO_FLAMINGO -> CatppuccinMacchiatoFlamingoTheme(
            useDarkTheme,
            content
        )

        CatppuccinThemeType.MACCHIATO_GREEN -> CatppuccinMacchiatoGreenTheme(useDarkTheme, content)
        CatppuccinThemeType.MACCHIATO_LAVENDER -> CatppuccinMacchiatoLavenderTheme(
            useDarkTheme,
            content
        )

        CatppuccinThemeType.MACCHIATO_MAROON -> CatppuccinMacchiatoMaroonTheme(
            useDarkTheme,
            content
        )

        CatppuccinThemeType.MACCHIATO_MAUVE -> CatppuccinMacchiatoMauveTheme(useDarkTheme, content)
        CatppuccinThemeType.MACCHIATO_PEACH -> CatppuccinMacchiatoPeachTheme(useDarkTheme, content)
        CatppuccinThemeType.MACCHIATO_PINK -> CatppuccinMacchiatoPinkTheme(useDarkTheme, content)
        CatppuccinThemeType.MACCHIATO_RED -> CatppuccinMacchiatoRedTheme(useDarkTheme, content)
        CatppuccinThemeType.MACCHIATO_ROSEWATER -> CatppuccinMacchiatoRosewaterTheme(
            useDarkTheme,
            content
        )

        CatppuccinThemeType.MACCHIATO_SAPPHIRE -> CatppuccinMacchiatoSapphireTheme(
            useDarkTheme,
            content
        )

        CatppuccinThemeType.MACCHIATO_SKY -> CatppuccinMacchiatoSkyTheme(useDarkTheme, content)
        CatppuccinThemeType.MACCHIATO_TEAL -> CatppuccinMacchiatoTealTheme(useDarkTheme, content)
        CatppuccinThemeType.MACCHIATO_YELLOW -> CatppuccinMacchiatoYellowTheme(
            useDarkTheme,
            content
        )

        CatppuccinThemeType.MOCHA_BLUE -> CatppuccinMochaBlueTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_FLAMINGO -> CatppuccinMochaFlamingoTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_GREEN -> CatppuccinMochaGreenTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_LAVENDER -> CatppuccinMochaLavenderTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_MAROON -> CatppuccinMochaMaroonTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_MAUVE -> CatppuccinMochaMauveTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_PEACH -> CatppuccinMochaPeachTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_PINK -> CatppuccinMochaPinkTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_RED -> CatppuccinMochaRedTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_ROSEWATER -> CatppuccinMochaRosewaterTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_SAPPHIRE -> CatppuccinMochaSapphireTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_SKY -> CatppuccinMochaSkyTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_TEAL -> CatppuccinMochaTealTheme(useDarkTheme, content)
        CatppuccinThemeType.MOCHA_YELLOW -> CatppuccinMochaYellowTheme(useDarkTheme, content)
    }
}
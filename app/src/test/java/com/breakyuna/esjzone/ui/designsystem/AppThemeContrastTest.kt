package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeContrastTest {
    @Test
    fun textAndActionsRemainReadableInBothThemes() {
        listOf(PaperLightColors, PaperDarkColors).forEach { colors ->
            val pairs = listOf(
                colors.onPrimary to colors.primary,
                colors.onPrimaryContainer to colors.primaryContainer,
                colors.onSecondary to colors.secondary,
                colors.onSecondaryContainer to colors.secondaryContainer,
                colors.onTertiary to colors.tertiary,
                colors.onTertiaryContainer to colors.tertiaryContainer,
                colors.onError to colors.error,
                colors.onErrorContainer to colors.errorContainer,
                colors.onBackground to colors.background,
                colors.inverseOnSurface to colors.inverseSurface
            )
            pairs.forEach { (text, surface) -> assertReadable(text, surface) }
            listOf(colors.background, colors.surface, colors.surfaceContainerLow, colors.surfaceContainerHigh).forEach { surface ->
                assertReadable(colors.onSurface, surface)
                assertReadable(colors.onSurfaceVariant, surface)
                assertReadable(colors.primary, surface)
                assertReadable(colors.error, surface)
            }
        }
    }

    @Test
    fun statusLabelsRemainReadableOnTheirContainers() {
        listOf(LightAccentColors, DarkAccentColors).forEach { colors ->
            assertReadable(colors.success, colors.successContainer)
            assertReadable(colors.warning, colors.warningContainer)
            assertReadable(colors.info, colors.infoContainer)
            assertReadable(colors.favorite, colors.favoriteContainer)
        }
    }

    @Test
    fun chartLabelsKeepTheirIdentityAcrossRankingsAndPeriods() {
        val ranking = listOf("奇幻", "恋爱", "冒险", "日常")
        listOf(false, true).forEach { dark ->
            val original = ranking.associateWith { chartColor(it, dark) }
            val changedPeriod = listOf("其他类型") + ranking.reversed()
            val changed = changedPeriod.associateWith { chartColor(it, dark) }
            ranking.forEach { tag -> assertEquals(original.getValue(tag), changed.getValue(tag)) }
            original.values.forEach { color ->
                assertReadable(color, if (dark) PaperDarkColors.surface else PaperLightColors.surface)
            }
        }
    }

    private fun assertReadable(text: Color, surface: Color) {
        val a = text.luminance()
        val b = surface.luminance()
        val contrast = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        assertTrue("Text $text on $surface has contrast $contrast", contrast >= 4.5f)
    }
}

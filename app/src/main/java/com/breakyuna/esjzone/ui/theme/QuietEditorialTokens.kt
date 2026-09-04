package com.breakyuna.esjzone.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Presentation tokens for the Quiet Editorial Reader visual language.
 *
 * The app still uses the selected Catppuccin Material color scheme for its
 * actual color roles. These tokens provide the stable geometry, typography,
 * and low-contrast surfaces shared by the redesigned discovery surfaces.
 */
object QuietEditorial {
    val contentMaxWidth: Dp = 760.dp
    val pagePadding: Dp = 16.dp
    val sectionGap: Dp = 32.dp
    val itemGap: Dp = 12.dp
    val hairline: Dp = 0.5.dp

    val badgeShape = RoundedCornerShape(6.dp)
    val controlShape = RoundedCornerShape(8.dp)
    val coverShape = RoundedCornerShape(12.dp)
    val cardShape = RoundedCornerShape(16.dp)
    val largeShape = RoundedCornerShape(24.dp)

    val serif = FontFamily.Serif
    val sans = FontFamily.SansSerif

    val display = TextStyle(
        fontFamily = serif,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold
    )
    val sectionTitle = TextStyle(
        fontFamily = serif,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold
    )
    val cardTitle = TextStyle(
        fontFamily = serif,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    )
    val title = TextStyle(
        fontFamily = sans,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    )
    val body = TextStyle(
        fontFamily = sans,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
    val label = TextStyle(
        fontFamily = sans,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    )
    val smallLabel = TextStyle(
        fontFamily = sans,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Immutable
data class QuietEditorialColors(
    val inkRule: Color,
    val softSurface: Color,
    val cardSurface: Color,
    val mutedInk: Color
)

@Composable
fun quietEditorialColors(): QuietEditorialColors = QuietEditorialColors(
    inkRule = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    softSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
    cardSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    mutedInk = MaterialTheme.colorScheme.onSurfaceVariant
)

package com.breakyuna.esjzone.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.designsystem.AppMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun AppNavigationIcon(
    tab: AppTabId,
    selected: Boolean,
    tint: Color = LocalContentColor.current
) {
    val progress = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(AppMotion.expressive, easing = FastOutSlowInEasing),
        label = "${tab.name}IconParts"
    )
    val path = remember { Path() }
    val stroke = remember {
        Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    // Read animation state only while drawing. Reuse the path and stroke across frames;
    // the fixed viewport preserves both layout and the parent tab's touch/semantic bounds.
    Box(
        Modifier
            .size(24.dp)
            .drawBehind {
                val value = progress.value
                scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                    when (tab) {
                        AppTabId.HOME -> drawHomeIcon(value, tint, path, stroke)
                        AppTabId.HISTORY -> drawHistoryIcon(value, tint, path, stroke)
                        AppTabId.BOOKSHELF -> drawBookshelfIcon(value, tint, path, stroke)
                        AppTabId.PROFILE -> drawProfileIcon(value, tint, path, stroke)
                    }
                }
            }
    )
}

private fun DrawScope.drawHomeIcon(progress: Float, tint: Color, path: Path, stroke: Stroke) {
    path.reset()
    path.moveTo(3f, 10f)
    path.lineTo(12f, 3f)
    path.lineTo(21f, 10f)
    drawPath(path, tint, style = stroke)

    path.reset()
    path.moveTo(5f, 10f)
    path.lineTo(5f, 21f)
    path.lineTo(19f, 21f)
    path.lineTo(19f, 10f)
    drawPath(path, tint, style = stroke)

    // The frame stays still while the door swings inward around its left hinge.
    path.reset()
    path.moveTo(9f, 21f)
    path.lineTo(9f, 13f)
    path.lineTo(15f, 13f)
    path.lineTo(15f, 21f)
    drawPath(path, tint, style = stroke)
    val edge = 15f - 4.5f * progress
    val depth = 1.2f * progress
    path.reset()
    path.moveTo(9f, 13f)
    path.lineTo(edge, 13f + depth)
    path.lineTo(edge, 21f - depth)
    path.lineTo(9f, 21f)
    path.close()
    drawPath(path, tint.copy(alpha = tint.alpha * 0.2f * progress))
    drawPath(path, tint, style = stroke)
}

private fun DrawScope.drawHistoryIcon(progress: Float, tint: Color, path: Path, stroke: Stroke) {
    drawArc(
        color = tint,
        startAngle = 210f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(3f, 3f),
        size = Size(18f, 18f),
        style = stroke
    )
    path.reset()
    path.moveTo(4.2f, 3.7f)
    path.lineTo(4.2f, 7.5f)
    path.lineTo(8f, 7.5f)
    drawPath(path, tint, style = stroke)

    // Only the clock hands turn; the history arrow remains anchored.
    rotate(360f * progress, pivot = Offset(12f, 12f)) {
        path.reset()
        path.moveTo(12f, 6.8f)
        path.lineTo(12f, 12f)
        path.lineTo(15.4f, 14f)
        drawPath(path, tint, style = stroke)
    }
}

private fun DrawScope.drawBookshelfIcon(progress: Float, tint: Color, path: Path, stroke: Stroke) {
    path.reset()
    path.moveTo(12f, 6f)
    path.cubicTo(9f, 4f, 5f, 3.5f, 2.5f, 4.5f)
    path.lineTo(2.5f, 18f)
    path.cubicTo(6f, 17f, 9f, 18f, 12f, 20f)
    path.cubicTo(15f, 18f, 18f, 17f, 21.5f, 18f)
    path.lineTo(21.5f, 4.5f)
    path.cubicTo(19f, 3.5f, 15f, 4f, 12f, 6f)
    path.close()
    drawPath(path, tint, style = stroke)
    drawLine(tint, Offset(12f, 6f), Offset(12f, 20f), strokeWidth = stroke.width)

    // Project a turning leaf around the spine: right page -> upright -> left page.
    val angle = PI.toFloat() * progress
    val reach = 9.5f * cos(angle)
    val lift = 3f * sin(angle)
    path.reset()
    path.moveTo(12f, 6f)
    path.cubicTo(
        12f + reach * 0.35f, 4f - lift,
        12f + reach * 0.75f, 3.5f - lift,
        12f + reach, 4.5f - lift
    )
    path.lineTo(12f + reach, 18f - lift * 0.4f)
    path.cubicTo(
        12f + reach * 0.65f, 17f - lift * 0.4f,
        12f + reach * 0.3f, 18f,
        12f, 20f
    )
    path.close()
    val leafColor = tint.copy(alpha = tint.alpha * sin(angle).coerceIn(0f, 1f))
    drawPath(path, leafColor.copy(alpha = leafColor.alpha * 0.18f))
    drawPath(path, leafColor, style = stroke)
}

private fun DrawScope.drawProfileIcon(progress: Float, tint: Color, path: Path, stroke: Stroke) {
    val nod = sin(PI.toFloat() * progress)
    val headCenter = Offset(12f + 1.6f * nod, 7f + 0.8f * nod)
    val headSize = Size(7f, 7f - 0.8f * nod)
    val headTopLeft = headCenter - Offset(headSize.width / 2f, headSize.height / 2f)
    drawOval(tint.copy(alpha = tint.alpha * 0.18f * progress), headTopLeft, headSize)
    drawOval(tint, headTopLeft, headSize, style = stroke)

    // A small shoulder follow-through makes the head movement read as a nod.
    val shoulder = 0.8f * nod
    path.reset()
    path.moveTo(4f, 21f)
    path.lineTo(4f, 18.5f)
    path.cubicTo(4f, 15.5f + shoulder, 8f, 14f + shoulder, 12f, 14f)
    path.cubicTo(16f, 14f - shoulder, 20f, 15.5f - shoulder, 20f, 18.5f)
    path.lineTo(20f, 21f)
    path.close()
    drawPath(path, tint.copy(alpha = tint.alpha * 0.18f * progress))
    drawPath(path, tint, style = stroke)
}

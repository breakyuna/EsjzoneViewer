package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import java.util.Locale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.reader.ReaderGlowQuality
import com.breakyuna.esjzone.ui.reader.ReaderImageBackground
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderImageAppearanceControls(
    value: ReaderImageBackground, enabled: Boolean = true, onChange: (ReaderImageBackground) -> Unit
) {
    var advanced by rememberSaveable { mutableStateOf(false) }
    BackHandler(advanced) { advanced = false }
    var editColor by rememberSaveable { mutableStateOf(false) }
    BackHandler(editColor) { editColor = false }
    if (advanced) {
        ReaderGlowAdvancedControls(value, enabled, { advanced = false }, onChange)
        return
    }
    if (editColor) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            ReaderTextColorControls(value.textColor, { editColor = false }) {
                if (enabled) onChange(value.copy(textColor = it))
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reader_background_text_color), Modifier.weight(1f), style = AppTypography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                listOf(0xFFFFFFFFL to R.string.reader_glow_white, 0xFF111111L to R.string.reader_glow_black)
                    .forEachIndexed { index, (color, label) ->
                        SegmentedButton(selected = value.textColor == color, enabled = enabled,
                            onClick = { onChange(value.copy(textColor = color)) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(stringResource(label)) }
                    }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0xFF202020L, 0xFFFFFFFFL, 0xFFE8E0D0L, 0xFF433422L, 0xFF24382CL, 0xFF353149L).forEach { color ->
                    val label = stringResource(R.string.reader_background_color_value, colorHex(color))
                    Box(Modifier.size(36.dp)
                        .selectable(value.textColor == color, enabled = enabled, role = Role.RadioButton,
                            onClick = { onChange(value.copy(textColor = color)) })
                        .semantics { contentDescription = label }
                        .border(if (value.textColor == color) 2.dp else 1.dp,
                            if (value.textColor == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            CircleShape).padding(4.dp).background(Color(color), CircleShape))
                }
            }
            TextButton(onClick = { editColor = true }, enabled = enabled) {
                Text(stringResource(R.string.reader_background_custom_color))
            }
        }
        listOf(
            Triple(R.string.reader_background_transparency, value.transparency, 100f),
            Triple(R.string.reader_background_blur, value.blur, 50f)
        ).forEach { (resource, current, maximum) ->
            val sliderLabel = stringResource(resource)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(sliderLabel, Modifier.weight(1f), style = AppTypography.labelLarge)
                Text("${current.roundToInt()}%", style = AppTypography.labelLarge)
            }
            Slider(value = current, enabled = enabled, valueRange = 0f..maximum,
                onValueChange = { amount ->
                    onChange(if (resource == R.string.reader_background_blur) value.copy(blur = amount.roundToInt().toFloat())
                        else value.copy(transparency = amount.roundToInt().toFloat()))
                }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = sliderLabel })
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reader_glow_enabled), Modifier.weight(1f), style = AppTypography.labelLarge)
            Switch(checked = value.glowEnabled, enabled = enabled, onCheckedChange = { onChange(value.copy(glowEnabled = it)) })
            TextButton(onClick = { advanced = true }, enabled = enabled) {
                Text(stringResource(R.string.reader_glow_advanced))
            }
        }
        val label = stringResource(R.string.reader_glow_strength)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = AppTypography.labelLarge)
            Text("${value.glowStrength.roundToInt()}%", style = AppTypography.labelLarge)
        }
        Slider(value = value.glowStrength, onValueChange = { onChange(value.copy(glowStrength = it.roundToInt().toFloat())) },
            valueRange = 0f..100f, enabled = enabled && value.glowEnabled,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
        ReaderGlowQualityNotice()
    }
}

@Composable
internal fun ReaderGlowQualityNotice() {
    val reduced by ReaderGlowQuality.reduced.collectAsState()
    if (reduced) Text(stringResource(R.string.reader_glow_reduced), style = AppTypography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun colorHex(value: Long): String = String.format(Locale.ROOT, "#%06X", value and 0xFFFFFFL)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTextColorControls(color: Long, onDismiss: () -> Unit, onChange: (Long) -> Unit) {
    val initial = remember { FloatArray(3).also { androidx.core.graphics.ColorUtils.colorToHSL(color.toInt(), it) } }
    var hue by rememberSaveable { mutableFloatStateOf(initial[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(initial[1]) }
    var lightness by rememberSaveable { mutableFloatStateOf(initial[2]) }
    fun selected(h: Float = hue, s: Float = saturation, l: Float = lightness) =
        Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(h, s, l)))
    fun publish() = onChange(selected().toArgb().toLong() and 0xFFFFFFFFL)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.reader_background_custom_color), Modifier.weight(1f), style = AppTypography.titleMedium)
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_background_apply)) }
    }
    GradientColorSlider(stringResource(R.string.reader_color_hue), hue, 0f..360f,
        (0..6).map { Color.hsv((it * 60f).coerceAtMost(360f), 1f, 1f) }) { hue = it; publish() }
    GradientColorSlider(stringResource(R.string.reader_color_saturation), saturation, 0f..1f,
        listOf(selected(s = 0f), selected(s = 1f))) { saturation = it; publish() }
    GradientColorSlider(stringResource(R.string.reader_color_brightness), lightness, 0f..1f,
        listOf(Color.Black, selected(l = 0.5f), Color.White)) { lightness = it; publish() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradientColorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>,
    colors: List<Color>, onChange: (Float) -> Unit) {
    Text(label, style = AppTypography.labelLarge)
    Slider(value = value, onValueChange = onChange, valueRange = range,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        thumb = { Box(Modifier.size(28.dp).background(Color.White, CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)) },
        track = { Box(Modifier.fillMaxWidth().height(28.dp).clip(CircleShape)
            .background(Brush.horizontalGradient(colors))) })
}

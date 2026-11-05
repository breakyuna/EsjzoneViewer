package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.reader.ReaderGlowMode
import com.breakyuna.esjzone.ui.reader.ReaderImageBackground
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderGlowAdvancedControls(value: ReaderImageBackground, enabled: Boolean,
    onDismiss: () -> Unit, onChange: (ReaderImageBackground) -> Unit) {
    var editGlowColor by rememberSaveable { mutableStateOf(false) }
    BackHandler(editGlowColor) { editGlowColor = false }
    if (editGlowColor) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            ReaderTextColorControls(value.customGlowColor ?: 0xFFFFFFFFL, { editGlowColor = false }) {
                if (enabled) onChange(value.copy(customGlowColor = it))
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.reader_back))
            }
            Text(stringResource(R.string.reader_glow_advanced), Modifier.weight(1f), style = AppTypography.titleMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_background_apply)) }
        }
        Text(stringResource(R.string.reader_glow_mode), style = AppTypography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = listOf(ReaderGlowMode.SAME_HUE to R.string.reader_glow_same_hue,
                ReaderGlowMode.BLACK_WHITE to R.string.reader_glow_black_white,
                ReaderGlowMode.CUSTOM to R.string.reader_glow_custom)
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(selected = value.glowMode == mode, enabled = enabled,
                    onClick = { onChange(value.copy(glowMode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size)) { Text(stringResource(label)) }
            }
        }
        if (value.glowMode == ReaderGlowMode.SAME_HUE) {
            GlowParameter(R.string.reader_glow_light_l, value.lightTextGlowL, 0f..40f, enabled) {
                onChange(value.copy(lightTextGlowL = it))
            }
            GlowParameter(R.string.reader_glow_dark_l, value.darkTextGlowL, 80f..100f, enabled) {
                onChange(value.copy(darkTextGlowL = it))
            }
            GlowParameter(R.string.reader_glow_chroma, value.glowChromaRetention, 0f..100f, enabled) {
                onChange(value.copy(glowChromaRetention = it))
            }
        }
        if (value.glowMode == ReaderGlowMode.CUSTOM) {
            TextButton(onClick = { editGlowColor = true }, enabled = enabled) {
                Text(stringResource(R.string.reader_glow_custom_color))
            }
        }
        GlowParameter(R.string.reader_glow_strength, value.glowStrength, 0f..100f, enabled && value.glowEnabled) {
            onChange(value.copy(glowStrength = it))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reader_glow_outer_alpha), Modifier.weight(1f), style = AppTypography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                listOf(0.9f, 0.7f).forEachIndexed { index, alpha ->
                    SegmentedButton(selected = value.outerGlowAlpha == alpha, enabled = enabled,
                        onClick = { onChange(value.copy(outerGlowAlpha = alpha)) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(alpha.toString()) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.reader_glow_auto_clamp), Modifier.weight(1f), style = AppTypography.labelLarge)
            Switch(checked = value.autoClampLightness, enabled = enabled,
                onCheckedChange = { onChange(value.copy(autoClampLightness = it)) })
        }
    }
}

@Composable
private fun GlowParameter(labelId: Int, value: Float, range: ClosedFloatingPointRange<Float>,
    enabled: Boolean, onChange: (Float) -> Unit) {
    val label = stringResource(labelId)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = AppTypography.labelLarge)
        Text("${value.roundToInt()}%", style = AppTypography.labelLarge)
    }
    Slider(value = value, enabled = enabled, valueRange = range,
        onValueChange = { onChange(it.roundToInt().toFloat()) },
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
}

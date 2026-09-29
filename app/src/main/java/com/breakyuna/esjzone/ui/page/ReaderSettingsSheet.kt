package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.reader.*
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    visible: Boolean,
    settings: ReaderSettings,
    previewText: String,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    if (!visible) return
    var draft by remember(settings) { mutableStateOf(settings) }
    var confirmReset by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    fun commit(value: ReaderSettings) {
        draft = value
        onSettingsChange(value)
    }
    val maximumHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.heightIn(max = maximumHeight),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetMaxWidth = 640.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.reader_reset)) }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            TabRow(selectedTabIndex = tab) {
                listOf(R.string.reader_tab_appearance, R.string.reader_tab_layout, R.string.reader_tab_controls).forEachIndexed { index, label ->
                    Tab(selected = tab == index, onClick = { draft = settings; tab = index }, text = { Text(stringResource(label)) })
                }
            }
            key(tab) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tab != 2) {
                        Surface(color = draft.background.containerColor(), contentColor = draft.background.contentColor(), shape = AppShapes.prominent) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = draft.horizontalPaddingDp.dp, vertical = 16.dp)) {
                                Text(stringResource(R.string.reader_live_preview), style = MaterialTheme.typography.labelMedium)
                                val sample = previewText.take(100).ifBlank { stringResource(R.string.reader_preview_sample) }
                                val style = MaterialTheme.typography.bodyLarge.copy(fontFamily = draft.font.family(), fontSize = draft.fontSizeSp.sp, lineHeight = draft.lineHeightSp.sp, letterSpacing = draft.letterSpacingSp.sp)
                                Text(sample, style = style, maxLines = 3, modifier = Modifier.padding(top = 12.dp))
                                Spacer(Modifier.height(draft.paragraphSpacingDp.dp))
                                Text(stringResource(R.string.reader_preview_sample), style = style, maxLines = 2)
                                if (tab == 1) {
                                    Spacer(Modifier.height(draft.pageSpacingDp.dp))
                                    HorizontalDivider(color = draft.background.contentColor().copy(alpha = 0.2f))
                                    Text(stringResource(R.string.reader_chapter_spacing), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    when (tab) {
                        0 -> {
                            Text(stringResource(R.string.reader_background), style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReaderBackground.entries.forEach { background ->
                                    FilterChip(selected = draft.background == background, onClick = { commit(draft.copy(background = background)) },
                                        label = { Text(stringResource(background.label())) },
                                        leadingIcon = { Surface(color = background.containerColor(), border = androidx.compose.foundation.BorderStroke(1.dp, background.contentColor().copy(alpha = 0.3f)), shape = AppShapes.pill) { Spacer(Modifier.size(22.dp)) } })
                                }
                            }
                            Text(stringResource(R.string.reader_font), style = MaterialTheme.typography.titleSmall)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReaderFont.entries.forEach { font ->
                                    Surface(onClick = { commit(draft.copy(font = font)) }, shape = AppShapes.standard,
                                        color = if (draft.font == font) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        border = if (draft.font == font) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
                                        Column(Modifier.widthIn(min = 140.dp).padding(12.dp)) {
                                            val sampleFont = if (draft.font == font) font.family() else FontFamily.Default
                                            Text((if (draft.font == font) "✓ " else "") + stringResource(font.label()), fontFamily = sampleFont, style = MaterialTheme.typography.titleMedium)
                                            Text(stringResource(R.string.reader_font_sample), fontFamily = sampleFont, fontSize = 16.sp)
                                            if (draft.font == font && !ReaderFontStore.isAvailable(font)) Text(stringResource(R.string.reader_font_downloading), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            Text(stringResource(R.string.reader_script), style = MaterialTheme.typography.titleSmall)
                            ReaderSettingChoices(draft.script, listOf(ReaderScript.ORIGINAL to stringResource(R.string.reader_script_original), ReaderScript.SIMPLIFIED to stringResource(R.string.reader_script_simplified), ReaderScript.TRADITIONAL to stringResource(R.string.reader_script_traditional))) { commit(draft.copy(script = it)) }
                            TextButton(onClick = { showLicenses = true }) { Text(stringResource(R.string.reader_font_licenses)) }
                        }
                        1 -> {
                            ReaderSettingSlider(
                                label = stringResource(id = R.string.reader_font_size),
                                value = settings.fontSizeSp,
                                onValueChange = { draft = draft.copy(fontSizeSp = it) },
                                valueLabel = { "${it.roundToInt()}sp" },
                                valueRange = 14f..30f,
                                steps = 15,
                                onValueChangeFinished = { value ->
                                    commit(draft.copy(fontSizeSp = value))
                                }
                            )
                            ReaderSettingSlider(
                                label = stringResource(id = R.string.reader_letter_spacing),
                                value = settings.letterSpacingSp,
                                onValueChange = { draft = draft.copy(letterSpacingSp = it) },
                                valueLabel = { "${(it * 10f).roundToInt() / 10f}sp" },
                                valueRange = 0f..2f,
                                steps = 19,
                                onValueChangeFinished = { value ->
                                    commit(draft.copy(letterSpacingSp = value))
                                }
                            )
                            ReaderSettingSlider(
                                label = stringResource(id = R.string.reader_line_spacing),
                                value = settings.lineSpacingSp,
                                onValueChange = { draft = draft.copy(lineSpacingSp = it) },
                                valueLabel = { "${it.roundToInt()}sp" },
                                valueRange = 4f..24f,
                                steps = 19,
                                onValueChangeFinished = { value ->
                                    commit(draft.copy(lineSpacingSp = value))
                                }
                            )
                            ReaderSettingSlider(
                                label = stringResource(id = R.string.reader_paragraph_spacing),
                                value = settings.paragraphSpacingDp,
                                onValueChange = { draft = draft.copy(paragraphSpacingDp = it) },
                                valueLabel = { "${it.roundToInt()}dp" },
                                valueRange = 0f..32f,
                                steps = 15,
                                onValueChangeFinished = { value ->
                                    commit(draft.copy(paragraphSpacingDp = value))
                                }
                            )
                            ReaderSettingSlider(
                                label = stringResource(id = R.string.reader_chapter_spacing),
                                value = settings.pageSpacingDp,
                                onValueChange = { draft = draft.copy(pageSpacingDp = it) },
                                valueLabel = { "${it.roundToInt()}dp" },
                                valueRange = 16f..80f,
                                steps = 15,
                                onValueChangeFinished = { value ->
                                    commit(draft.copy(pageSpacingDp = value))
                                }
                            )
                            ReaderSettingSlider(
                                label = stringResource(id = R.string.reader_horizontal_padding),
                                value = settings.horizontalPaddingDp,
                                onValueChange = { draft = draft.copy(horizontalPaddingDp = it) },
                                valueLabel = { "${it.roundToInt()}dp" },
                                valueRange = 12f..48f,
                                steps = 8,
                                onValueChangeFinished = { value ->
                                    commit(draft.copy(horizontalPaddingDp = value))
                                }
                            )
                        }
                        2 -> {
                            ReaderToggle(stringResource(R.string.reader_tap_paging), stringResource(R.string.reader_tap_paging_help), draft.tapPagingEnabled) { commit(draft.copy(tapPagingEnabled = it)) }
                            ReaderToggle(stringResource(R.string.reader_swipe_paging), stringResource(R.string.reader_swipe_paging_help), draft.horizontalSwipePagingEnabled) { commit(draft.copy(horizontalSwipePagingEnabled = it)) }
                            ReaderToggle(stringResource(R.string.reader_volume_paging), stringResource(R.string.reader_volume_paging_help), draft.volumeKeyPaging) { commit(draft.copy(volumeKeyPaging = it)) }
                            Text(stringResource(R.string.reader_paging_method), style = MaterialTheme.typography.titleSmall)
                            ReaderSettingChoices(draft.pageAnimation, listOf(
                                ReaderPageAnimation.VERTICAL_SCROLL to stringResource(R.string.reader_page_animation_vertical),
                                ReaderPageAnimation.HORIZONTAL_SLIDE to stringResource(R.string.reader_page_animation_slide),
                                ReaderPageAnimation.FADE to stringResource(R.string.reader_page_animation_fade),
                                ReaderPageAnimation.COVER to stringResource(R.string.reader_page_animation_cover)
                            )) { commit(draft.copy(pageAnimation = it)) }
                        }
                    }
                }
            }
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text(stringResource(R.string.reader_reset)) }, text = { Text(stringResource(R.string.reader_reset_confirmation)) }, confirmButton = { TextButton(onClick = { commit(ReaderSettings()); confirmReset = false }) { Text(stringResource(R.string.reader_reset)) } }, dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } })
    if (showLicenses) {
        val context = LocalContext.current
        val license by produceState("") {
            value = withContext(Dispatchers.IO) {
                listOf("SOURCES.md", "source_han_serif.txt", "source_han_sans.txt", "lxgw_wenkai.txt").joinToString("\n\n") { name -> context.assets.open("font_licenses/$name").bufferedReader().use { it.readText() } }
            }
        }
        AlertDialog(onDismissRequest = { showLicenses = false }, title = { Text(stringResource(R.string.reader_font_licenses)) }, text = { Text(license, modifier = Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(stringResource(R.string.close)) } })
    }
}

@Composable
private fun ReaderToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = AppShapes.standard, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

private fun ReaderFont.label(): Int = when (this) {
    ReaderFont.SYSTEM -> R.string.reader_font_system
    ReaderFont.SERIF -> R.string.reader_font_serif
    ReaderFont.MONOSPACE -> R.string.reader_font_monospace
    ReaderFont.SANS_SERIF -> R.string.reader_font_sans_serif
    ReaderFont.CURSIVE -> R.string.reader_font_cursive
    ReaderFont.SOURCE_HAN_SERIF -> R.string.reader_font_source_serif
    ReaderFont.SOURCE_HAN_SANS -> R.string.reader_font_source_sans
    ReaderFont.LXGW_WENKAI -> R.string.reader_font_wenkai
}
private fun ReaderBackground.label(): Int = when (this) {
    ReaderBackground.SYSTEM -> R.string.reader_background_system
    ReaderBackground.PAPER -> R.string.reader_background_paper
    ReaderBackground.SEPIA -> R.string.reader_background_sepia
    ReaderBackground.DARK -> R.string.reader_background_dark
    ReaderBackground.MINT -> R.string.reader_background_mint
    ReaderBackground.LAVENDER -> R.string.reader_background_lavender
    ReaderBackground.SLATE -> R.string.reader_background_slate
    ReaderBackground.OLED -> R.string.reader_background_oled
}

@Composable
private fun <T> ReaderSettingChoices(
    selected: T,
    options: List<Pair<T, String>>,
    onSelected: (T) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(text = label) }
            )
        }
    }
}

@Composable
private fun ReaderSettingSlider(
    label: String,
    value: Float,
    valueLabel: (Float) -> String,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (Float) -> Unit
) {
    var sliderPosition by remember(value) { mutableFloatStateOf(value) }
    val interactionSource = remember { MutableInteractionSource() }
    val committedValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Cancel) {
                sliderPosition = committedValue
                currentOnChange(committedValue)
            }
        }
    }
    Column(modifier = Modifier.padding(top = AppSpacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel(sliderPosition),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = sliderPosition,
            interactionSource = interactionSource,
            onValueChange = { sliderPosition = it; onValueChange(it) },
            onValueChangeFinished = { onValueChangeFinished(sliderPosition) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

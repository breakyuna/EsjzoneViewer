package com.breakyuna.esjzone.ui.page

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.AppThemeMode
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.reader.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun ReaderSettingsSheet(
    visible: Boolean,
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tabPager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var draft by remember(settings) { mutableStateOf(settings) }
    var confirmReset by remember { mutableStateOf(false) }
    val themeMode by PresentationAccess.settings.themeMode
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    fun commit(value: ReaderSettings) {
        draft = value
        onSettingsChange(value)
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .height((LocalConfiguration.current.screenHeightDp * 0.5f).dp),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.reader_settings), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { confirmReset = true }) { Text(stringResource(R.string.reader_reset)) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                }
                TabRow(selectedTabIndex = tabPager.currentPage) {
                    listOf(R.string.reader_tab_appearance, R.string.reader_tab_controls, R.string.reader_tab_toolbar).forEachIndexed { index, label ->
                        Tab(selected = tabPager.currentPage == index,
                            onClick = { scope.launch { tabPager.animateScrollToPage(index) } },
                            text = { Text(stringResource(label)) })
                    }
                }
                HorizontalPager(state = tabPager, modifier = Modifier.weight(1f)) { tab ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                    if (tab == 0) {
                        Text(stringResource(R.string.reader_background), style = MaterialTheme.typography.titleSmall)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = {
                                    PresentationAccess.settings.setThemeMode(AppThemeMode.SYSTEM)
                                    commit(draft.copy(background = ReaderBackground.SYSTEM))
                                },
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = Color.Transparent,
                                border = BorderStroke(
                                    if (draft.background == ReaderBackground.SYSTEM && themeMode == AppThemeMode.SYSTEM) 2.dp else 1.dp,
                                    if (draft.background == ReaderBackground.SYSTEM && themeMode == AppThemeMode.SYSTEM)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.reader_background_system), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Surface(
                                onClick = {
                                    PresentationAccess.settings.setThemeMode(
                                        if (darkTheme) AppThemeMode.LIGHT else AppThemeMode.DARK
                                    )
                                    commit(draft.copy(background = ReaderBackground.SYSTEM))
                                },
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = if (darkTheme) Color(0xFF333333) else Color(0xFFEAEAEA),
                                border = BorderStroke(
                                    if (draft.background == ReaderBackground.SYSTEM && themeMode != AppThemeMode.SYSTEM) 2.dp else 1.dp,
                                    if (draft.background == ReaderBackground.SYSTEM && themeMode != AppThemeMode.SYSTEM)
                                        MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Brightness4,
                                        contentDescription = stringResource(
                                            if (darkTheme) R.string.reader_theme_switch_light else R.string.reader_theme_switch_dark
                                        ),
                                        tint = if (darkTheme) Color.White else Color.Black,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            ReaderBackground.entries.filterNot { it == ReaderBackground.SYSTEM }.forEach { background ->
                                FilterChip(
                                    selected = draft.background == background,
                                    onClick = { commit(draft.copy(background = background)) },
                                    label = { Text(stringResource(background.label())) },
                                    leadingIcon = {
                                        Surface(
                                            color = background.containerColor(),
                                            border = BorderStroke(1.dp, background.contentColor().copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(50)
                                        ) { Spacer(Modifier.size(18.dp)) }
                                    }
                                )
                            }
                        }
                        Text(stringResource(R.string.reader_font), style = MaterialTheme.typography.titleSmall)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderFont.entries.forEach { font ->
                                FilterChip(
                                    selected = draft.font == font,
                                    onClick = { commit(draft.copy(font = font)) },
                                    label = {
                                        Text(
                                            stringResource(font.label()),
                                            fontFamily = if (draft.font == font) font.family() else FontFamily.Default
                                        )
                                    }
                                )
                            }
                        }
                        if (!ReaderFontStore.isAvailable(draft.font)) {
                            Text(stringResource(R.string.reader_font_downloading), style = MaterialTheme.typography.labelSmall)
                        }
                        Text(stringResource(R.string.reader_script), style = MaterialTheme.typography.titleSmall)
                        ReaderSettingChoices(draft.script, listOf(
                            ReaderScript.ORIGINAL to stringResource(R.string.reader_script_original),
                            ReaderScript.SIMPLIFIED to stringResource(R.string.reader_script_simplified),
                            ReaderScript.TRADITIONAL to stringResource(R.string.reader_script_traditional)
                        )) { commit(draft.copy(script = it)) }
                        Text(stringResource(R.string.reader_paging_method), style = MaterialTheme.typography.titleSmall)
                        ReaderSettingChoices(draft.pageAnimation, listOf(
                            ReaderPageAnimation.VERTICAL_SCROLL to stringResource(R.string.reader_page_animation_vertical),
                            ReaderPageAnimation.HORIZONTAL_SLIDE to stringResource(R.string.reader_page_animation_slide),
                            ReaderPageAnimation.FADE to stringResource(R.string.reader_page_animation_fade),
                            ReaderPageAnimation.COVER to stringResource(R.string.reader_page_animation_cover)
                        )) { commit(draft.copy(pageAnimation = it)) }
                        ReaderLayoutSlider(draft, settings, ::commit) { draft = it }
                    } else if (tab == 1) {
                        ReaderToggle(stringResource(R.string.reader_show_system_status), draft.showSystemStatusBar) { commit(draft.copy(showSystemStatusBar = it)) }
                        ReaderToggle(stringResource(R.string.reader_show_system_navigation), draft.showSystemNavigationBar) { commit(draft.copy(showSystemNavigationBar = it)) }
                        ReaderToggle(stringResource(R.string.reader_show_chapter_name), draft.showChapterName) { commit(draft.copy(showChapterName = it)) }
                        ReaderToggle(stringResource(R.string.reader_show_time_battery), draft.showTimeBattery) { commit(draft.copy(showTimeBattery = it)) }
                        ReaderToggle(stringResource(R.string.settings_auto_resume_reading), draft.autoResumeLastReading) { commit(draft.copy(autoResumeLastReading = it)) }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        ReaderToggle(stringResource(R.string.reader_long_press_underline), draft.longPressUnderline) { commit(draft.copy(longPressUnderline = it)) }
                        ReaderToggle(stringResource(R.string.reader_tap_paging), draft.tapPagingEnabled) { commit(draft.copy(tapPagingEnabled = it)) }
                        ReaderToggle(stringResource(R.string.reader_scroll_side_gestures), draft.scrollSideGesturesEnabled) { commit(draft.copy(scrollSideGesturesEnabled = it)) }
                        ReaderToggle(stringResource(R.string.reader_paged_bookmark_gestures), draft.pagedBookmarkGesturesEnabled) { commit(draft.copy(pagedBookmarkGesturesEnabled = it)) }
                        ReaderToggle(stringResource(R.string.reader_volume_paging), draft.volumeKeyPaging) { commit(draft.copy(volumeKeyPaging = it)) }
                        ReaderToggle(stringResource(R.string.reader_eye_protection), draft.eyeProtectionEnabled) { commit(draft.copy(eyeProtectionEnabled = it)) }
                        Text(stringResource(R.string.reader_left_tap_action), style = MaterialTheme.typography.titleSmall)
                        ReaderSettingChoices(draft.leftTapForward, listOf(
                            false to stringResource(R.string.reader_left_tap_previous),
                            true to stringResource(R.string.reader_left_tap_next)
                        )) { commit(draft.copy(leftTapForward = it)) }
                    } else {
                        ReaderToolbarSettings(draft.toolbarTools) { commit(draft.copy(toolbarTools = it)) }
                    }
                    }
                }
            }
        }
    }

    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text(stringResource(R.string.reader_reset)) }, text = { Text(stringResource(R.string.reader_reset_confirmation)) }, confirmButton = { TextButton(onClick = { commit(ReaderSettings()); confirmReset = false }) { Text(stringResource(R.string.reader_reset)) } }, dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun ReaderLayoutSlider(
    draft: ReaderSettings,
    settings: ReaderSettings,
    commit: (ReaderSettings) -> Unit,
    onDraftChange: (ReaderSettings) -> Unit
) {
    val labels = listOf(
        R.string.reader_font_size, R.string.reader_letter_spacing,
        R.string.reader_line_spacing, R.string.reader_paragraph_spacing,
        R.string.reader_page_spacing, R.string.reader_horizontal_padding
    )
    val pagerState = rememberPagerState(pageCount = { labels.size })
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
            enabled = pagerState.currentPage > 0
        ) { Icon(Icons.Default.ChevronLeft, stringResource(labels[(pagerState.currentPage - 1).coerceAtLeast(0)])) }
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(labels[page]), style = MaterialTheme.typography.titleSmall)
            }
        }
        IconButton(
            onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
            enabled = pagerState.currentPage < labels.lastIndex
        ) { Icon(Icons.Default.ChevronRight, stringResource(labels[(pagerState.currentPage + 1).coerceAtMost(labels.lastIndex)])) }
    }
    val index = pagerState.currentPage
    val value = when (index) {
        0 -> settings.fontSizeSp
        1 -> settings.letterSpacingSp
        2 -> settings.lineSpacingSp
        3 -> settings.paragraphSpacingDp
        4 -> settings.pageSpacingDp
        else -> settings.horizontalPaddingDp
    }
    val valueRange = when (index) {
        0 -> 14f..30f
        1 -> 0f..2f
        2 -> 4f..24f
        3 -> 0f..32f
        4 -> 16f..80f
        else -> 12f..48f
    }
    val steps = when (index) {
        0, 3, 4 -> 15
        1, 2 -> 19
        else -> 8
    }
    key(index) {
        ReaderSettingSlider(
            value = value,
            valueLabel = {
                val amount = if (index == 1) "${(it * 10f).roundToInt() / 10f}" else "${it.roundToInt()}"
                amount + if (index >= 3) "dp" else "sp"
            },
            valueRange = valueRange,
            steps = steps,
            onValueChange = { changed ->
                onDraftChange(when (index) {
                    0 -> draft.copy(fontSizeSp = changed)
                    1 -> draft.copy(letterSpacingSp = changed)
                    2 -> draft.copy(lineSpacingSp = changed)
                    3 -> draft.copy(paragraphSpacingDp = changed)
                    4 -> draft.copy(pageSpacingDp = changed)
                    else -> draft.copy(horizontalPaddingDp = changed)
                })
            },
            onValueChangeFinished = { changed ->
                commit(when (index) {
                    0 -> draft.copy(fontSizeSp = changed)
                    1 -> draft.copy(letterSpacingSp = changed)
                    2 -> draft.copy(lineSpacingSp = changed)
                    3 -> draft.copy(paragraphSpacingDp = changed)
                    4 -> draft.copy(pageSpacingDp = changed)
                    else -> draft.copy(horizontalPaddingDp = changed)
                })
            }
        )
    }
}

@Composable
private fun ReaderToggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
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
private fun <T> ReaderSettingChoices(selected: T, options: List<Pair<T, String>>, onSelected: (T) -> Unit) {
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        options.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelected(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun ReaderSettingSlider(
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
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Slider(
            value = sliderPosition,
            interactionSource = interactionSource,
            onValueChange = { sliderPosition = it; onValueChange(it) },
            onValueChangeFinished = { onValueChangeFinished(sliderPosition) },
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth().height(40.dp)
        )
        Text(valueLabel(sliderPosition), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

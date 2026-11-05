package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.LocalFloatingNavSuppression
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import com.breakyuna.esjzone.ui.reader.MAX_READER_IMAGE_BLUR
import java.util.Locale

object ReaderBackgroundPage : AppDestination {
    override val key = "ReaderBackgroundPage"
    private fun readResolve(): Any = ReaderBackgroundPage

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val suppressNavigation = LocalFloatingNavSuppression.current
        DisposableEffect(suppressNavigation) {
            suppressNavigation(true)
            onDispose { suppressNavigation(false) }
        }
        val context = LocalContext.current.applicationContext
        val model = rememberAppViewModel { ReaderBackgroundPageModel(context) }
        val state by model.state.collectAsStateWithLifecycle()
        val settings by PresentationAccess.readerSettings.settings.collectAsStateWithLifecycle()
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let(model::chooseImage)
        }
        val snackbar = remember { SnackbarHostState() }
        val error = state.error?.let { stringResource(it) }
        LaunchedEffect(error) {
            if (error != null) {
                snackbar.showSnackbar(error)
                model.dismissError()
            }
        }
        LaunchedEffect(state.saved) { if (state.saved) navigator?.pop() }
        BackHandler(state.saving) { }
        var editColor by rememberSaveable { mutableStateOf(false) }
        var controlsVisible by rememberSaveable { mutableStateOf(true) }
        BackHandler(!controlsVisible || editColor) { if (!controlsVisible) controlsVisible = true else editColor = false }
        val window = LocalActivity.current?.window
        val view = LocalView.current
        DisposableEffect(window, view, controlsVisible) {
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
            val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
            val oldBehavior = controller?.systemBarsBehavior
            if (!controlsVisible) {
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                if (!controlsVisible) {
                    if (statusVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
                    if (navigationVisible) controller?.show(WindowInsetsCompat.Type.navigationBars())
                    oldBehavior?.let { controller?.systemBarsBehavior = it }
                }
            }
        }
        val foreground = Color(state.draft.textColor)
        val chooseImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        Scaffold(
            contentWindowInsets = if (controlsVisible) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
            topBar = {
                if (controlsVisible) TopAppBar(
                    title = { Text(stringResource(R.string.reader_background_image), style = AppTypography.titleMedium) },
                    navigationIcon = { BackIconButton { if (!state.saving) navigator?.pop() } },
                    actions = {
                        IconButton(onClick = chooseImage, enabled = !state.loading && !state.saving) {
                            Icon(Icons.Outlined.Image, stringResource(R.string.reader_background_choose_image))
                        }
                        TextButton(onClick = model::apply, enabled = state.canApply) {
                            Text(stringResource(R.string.reader_background_apply))
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbar) }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                Box(
                    Modifier.fillMaxSize()
                        .clickable(enabled = state.preview != null) { controlsVisible = !controlsVisible }
                        .background(if (state.preview != null) Color.Black
                            else if (foreground.luminance() < 0.5f) Color.White else Color(0xFF121212)),
                    contentAlignment = Alignment.Center
                ) {
                    val preview = state.preview
                    if (preview != null) {
                        Image(preview.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                            alpha = state.draft.imageAlpha)
                        Column(
                            Modifier.fillMaxSize().padding(horizontal = settings.horizontalPaddingDp.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp)
                        ) {
                            Text(stringResource(R.string.reader_background_preview_title), color = foreground,
                                fontFamily = settings.font.family(), fontSize = (settings.fontSizeSp + 4).sp, maxLines = 1)
                            Text(stringResource(R.string.reader_background_preview_text), color = foreground,
                                fontFamily = settings.font.family(), fontSize = settings.fontSizeSp.sp,
                                lineHeight = settings.lineHeightSp.sp, letterSpacing = settings.letterSpacingSp.sp)
                        }
                    } else if (!state.loading && !state.previewBusy) {
                        TextButton(onClick = chooseImage) {
                            Icon(Icons.Outlined.Image, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.reader_background_choose_image))
                        }
                    }
                    if (state.loading || state.saving || state.previewBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(24.dp),
                            color = if (state.preview != null && state.draft.imageAlpha <= 0.5f) Color.White else foreground,
                            strokeWidth = 2.dp
                        )
                    }
                }
                if (controlsVisible) Surface(modifier = Modifier.align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.accountContentWidth().padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)) {
                        if (editColor) {
                            ReaderTextColorControls(state.draft.textColor, { editColor = false }, model::setTextColor)
                        } else {
                        val transparencyLabel = stringResource(R.string.reader_background_transparency)
                        val blurLabel = stringResource(R.string.reader_background_blur)
                        val sliderEnabled = state.source != null && !state.loading && !state.saving
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(IntrinsicSize.Max)) {
                                Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                                    Text(transparencyLabel, style = AppTypography.labelLarge, maxLines = 1)
                                }
                                Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                                    Text(blurLabel, style = AppTypography.labelLarge, maxLines = 1)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                BackgroundAdjustmentSlider(
                                    label = transparencyLabel, value = state.draft.transparency,
                                    enabled = sliderEnabled, onValueChange = model::setTransparency
                                )
                                BackgroundAdjustmentSlider(
                                    label = blurLabel, value = state.draft.blur,
                                    enabled = sliderEnabled, maxValue = MAX_READER_IMAGE_BLUR.toFloat(),
                                    onValueChange = model::setBlur
                                )
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.reader_background_text_color), style = AppTypography.labelLarge,
                                modifier = Modifier.weight(1f))
                            Text(colorHex(state.draft.textColor), style = AppTypography.labelSmall, fontFamily = FontFamily.Monospace)
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
                            verticalAlignment = Alignment.CenterVertically) {
                            listOf(0xFF202020L, 0xFFFFFFFFL, 0xFFE8E0D0L, 0xFF433422L, 0xFF24382CL, 0xFF353149L).forEach { color ->
                                val label = stringResource(R.string.reader_background_color_value, colorHex(color))
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape)
                                        .selectable(state.draft.textColor == color, enabled = !state.loading && !state.saving,
                                            role = Role.RadioButton, onClick = { model.setTextColor(color) })
                                        .semantics { contentDescription = label },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.draft.textColor == color) {
                                        Box(Modifier.size(42.dp).border(2.dp, MaterialTheme.colorScheme.primary, CircleShape))
                                    }
                                    Box(Modifier.size(34.dp).background(Color(color), CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                                }
                            }
                            IconButton(onClick = { editColor = true }, enabled = !state.loading && !state.saving) {
                                Icon(Icons.Outlined.ColorLens, stringResource(R.string.reader_background_custom_color))
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackgroundAdjustmentSlider(
    label: String, value: Float, enabled: Boolean, maxValue: Float = 100f, onValueChange: (Float) -> Unit
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.16f else 0.08f)
    val thumbColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
    Slider(
        value = value, onValueChange = onValueChange, valueRange = 0f..maxValue, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = label },
        thumb = {
            Box(Modifier.size(28.dp).background(thumbColor, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        },
        track = { Box(Modifier.fillMaxWidth().height(4.dp).background(trackColor, CircleShape)) }
    )
}

internal fun parseReaderTextColor(value: String): Long? = value.trim().removePrefix("#")
    .takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLong(16)?.or(0xFF000000L)

private fun colorHex(value: Long): String = String.format(Locale.ROOT, "#%06X", value and 0xFFFFFFL)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTextColorControls(color: Long, onDismiss: () -> Unit, onChange: (Long) -> Unit) {
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

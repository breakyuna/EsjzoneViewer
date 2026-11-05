package com.breakyuna.esjzone.ui.page

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import java.util.Locale
import kotlin.math.roundToInt

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
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
        val foreground = Color(state.draft.textColor)
        val chooseImage = { picker.launch(arrayOf("image/*")) }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.reader_background_image), style = AppTypography.titleMedium) },
                    navigationIcon = { BackIconButton { if (!state.saving) navigator?.pop() } },
                    actions = {
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
                        .background(if (foreground.luminance() < 0.5f) Color.White else Color(0xFF121212)),
                    contentAlignment = Alignment.Center
                ) {
                    val preview = state.preview
                    if (preview != null) {
                        Image(preview.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
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
                            color = foreground, strokeWidth = 2.dp
                        )
                    }
                }
                Surface(modifier = Modifier.align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.accountContentWidth().padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.reader_background_blur), style = AppTypography.labelLarge,
                                modifier = Modifier.weight(1f))
                            Text("${state.draft.blur.roundToInt()}%", style = AppTypography.labelLarge)
                            TextButton(onClick = chooseImage, enabled = !state.loading && !state.saving) {
                                Text(stringResource(R.string.reader_background_choose_image))
                            }
                        }
                        Slider(value = state.draft.blur, onValueChange = model::setBlur, valueRange = 0f..100f,
                            enabled = state.source != null && !state.loading && !state.saving,
                            modifier = Modifier.semantics { contentDescription = context.getString(R.string.reader_background_blur) })
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
        if (editColor) ReaderTextColorDialog(state.draft.textColor,
            onDismiss = { editColor = false }, onApply = { model.setTextColor(it); editColor = false })
    }
}

internal fun parseReaderTextColor(value: String): Long? = value.trim().removePrefix("#")
    .takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLong(16)?.or(0xFF000000L)

private fun colorHex(value: Long): String = String.format(Locale.ROOT, "#%06X", value and 0xFFFFFFL)

@Composable
private fun ReaderTextColorDialog(color: Long, onDismiss: () -> Unit, onApply: (Long) -> Unit) {
    var input by rememberSaveable { mutableStateOf(colorHex(color)) }
    val parsed = parseReaderTextColor(input)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_background_custom_color)) },
        text = {
            OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
                label = { Text("#RRGGBB") }, isError = parsed == null,
                trailingIcon = {
                    if (parsed != null) Box(Modifier.size(24.dp).background(Color(parsed), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                })
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onApply) }, enabled = parsed != null) {
            Text(stringResource(R.string.reader_background_apply))
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

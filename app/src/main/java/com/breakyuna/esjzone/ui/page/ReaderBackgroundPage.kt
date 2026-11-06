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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
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
import com.breakyuna.esjzone.ui.reader.ReaderGlowSurface
import com.breakyuna.esjzone.ui.reader.ReaderGlowBodyText
import com.breakyuna.esjzone.ui.reader.readerBackdrop
import androidx.compose.ui.graphics.painter.BitmapPainter

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
        var fullScreen by rememberSaveable { mutableStateOf(false) }
        BackHandler(fullScreen) { fullScreen = false }
        LaunchedEffect(fullScreen) { model.setFullScreen(fullScreen) }
        val window = LocalActivity.current?.window
        val view = LocalView.current
        DisposableEffect(window, view, fullScreen) {
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val insets = androidx.core.view.ViewCompat.getRootWindowInsets(view)
            val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
            val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
            val oldBehavior = controller?.systemBarsBehavior
            if (fullScreen) {
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                if (fullScreen) {
                    if (statusVisible) controller?.show(WindowInsetsCompat.Type.statusBars())
                    if (navigationVisible) controller?.show(WindowInsetsCompat.Type.navigationBars())
                    oldBehavior?.let { controller?.systemBarsBehavior = it }
                }
            }
        }
        val foreground = remember(state.draft) { Color(com.breakyuna.esjzone.ui.reader.readerGlowColors(state.draft).text) }
        val chooseImage = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        val windowSize = LocalWindowInfo.current.containerSize
        val density = LocalDensity.current
        val screenWidth = with(density) { windowSize.width.toDp().coerceAtLeast(1.dp) }
        val aspect = if (windowSize.height > 0) windowSize.width.toFloat() / windowSize.height else 0.5f
        val previewContent: @Composable (Modifier) -> Unit = { modifier ->
            BoxWithConstraints(modifier.clipToBounds().background(Color.Black)) {
                // Scale the complete reader layout together with the photo in the miniature.
                val scale = (maxWidth / screenWidth).coerceAtLeast(0.01f)
                CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
                    val preview = state.preview
                    if (preview != null) {
                        ReaderGlowSurface(preview, state.draft, Modifier.fillMaxSize()
                            .readerBackdrop(Color.Black, remember(preview) { BitmapPainter(preview.asImageBitmap()) }, state.draft.transparency)) {
                            Column(
                                Modifier.fillMaxSize().padding(horizontal = settings.horizontalPaddingDp.dp)
                                    .padding(top = 88.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(settings.paragraphSpacingDp.dp)
                            ) {
                                Text(stringResource(R.string.reader_background_preview_title), color = foreground,
                                    fontFamily = settings.font.family(), fontSize = (settings.fontSizeSp + 4).sp, maxLines = 1)
                                ReaderGlowBodyText(stringResource(R.string.reader_background_preview_text),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = settings.font.family(), fontSize = settings.fontSizeSp.sp,
                                        lineHeight = settings.lineHeightSp.sp, letterSpacing = settings.letterSpacingSp.sp))
                            }
                        }
                    }
                }
                if (state.loading || state.saving || state.previewBusy) {
                    CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
        if (fullScreen) {
            previewContent(Modifier.fillMaxSize().clickable { fullScreen = false })
        } else {
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
                Column(Modifier.fillMaxSize().padding(padding)) {
                    val shadowColor = Color.Black.copy(
                        alpha = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) 0.08f else 0.32f
                    )
                    val topShadow = remember(shadowColor) {
                        Brush.verticalGradient(listOf(shadowColor, Color.Transparent))
                    }
                    val bottomShadow = remember(shadowColor) {
                        Brush.verticalGradient(listOf(Color.Transparent, shadowColor))
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                val width = minOf(maxWidth * 0.86f, maxHeight * aspect)
                                if (state.preview != null) {
                                    previewContent(Modifier.width(width).aspectRatio(aspect).clickable { fullScreen = true })
                                } else if (state.loading || state.previewBusy) {
                                    CircularProgressIndicator()
                                } else {
                                    IconButton(onClick = chooseImage) {
                                        Icon(Icons.Outlined.Image, stringResource(R.string.reader_background_choose_image))
                                    }
                                }
                            }
                            OutlinedButton(onClick = chooseImage, enabled = !state.loading && !state.saving,
                                modifier = Modifier.padding(top = 8.dp)) {
                                Text(stringResource(R.string.reader_background_choose_image))
                            }
                        }
                        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(8.dp).background(topShadow))
                        if (state.source != null) {
                            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp).background(bottomShadow))
                        }
                    }
                    if (state.source != null) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).accountContentWidth().padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)) {
                            ReaderImageAppearanceControls(value = state.draft,
                                enabled = !state.loading && !state.saving, onChange = model::updateAppearance)
                        }
                    }
                }
            }
        }
    }
}

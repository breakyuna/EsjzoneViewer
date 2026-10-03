package com.breakyuna.esjzone.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.breakyuna.esjzone.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Shared full-screen viewer for chapter illustrations and detail covers. */
@Composable
fun AppImageViewer(model: String, contentDescription: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val request = sharedMirrorImageRequest(model)
    val loader = readerImageLoader(model)
    val reducedMotion = rememberReaderReducedMotion()
    var saving by remember(model) { mutableStateOf(false) }
    val save: () -> Unit = {
        if (!saving) {
            saving = true
            scope.launch {
                try {
                    saveImageToGallery(context, loader, request)
                    GlobalToast.makeText(context, R.string.image_saved, GlobalToast.LENGTH_SHORT).show()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    GlobalToast.makeText(context, R.string.image_save_failed, GlobalToast.LENGTH_SHORT).show()
                } finally {
                    saving = false
                }
            }
        }
    }
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Box(Modifier.fillMaxSize()) {
                if (reducedMotion) {
                    AppReaderImage(model = model, contentDescription = contentDescription,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().combinedClickable(
                            onClick = onDismiss,
                            onLongClickLabel = globalStringResource(R.string.image_save),
                            onLongClick = save
                        ))
                } else {
                    AppReaderZoomableImage(model = model, contentDescription = contentDescription,
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
                        onClick = { onDismiss() }, onLongClick = { save() })
                }
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)
                    .statusBarsPadding().padding(AppSpacing.sm)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.76f), AppShapes.pill)) {
                    Icon(Icons.Filled.Close, contentDescription = globalStringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

package com.breakyuna.esjzone.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.LogEntry
import com.breakyuna.esjzone.util.LogLevel

/** Diagnostics surface. AppLogger remains the sole owner of redaction and persistence. */
object LogsPage : AppDestination {
    private fun readResolve(): Any = LogsPage

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val context = LocalContext.current
        val logs by AppLogger.logsFlow.collectAsState()
        val crashReport by AppLogger.crashReportFlow.collectAsState()
        var filter by remember { mutableStateOf<LogLevel?>(null) }
        var query by remember { mutableStateOf("") }
        var clearDialog by remember { mutableStateOf(false) }
        var crashDialog by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { AppLogger.refreshCrashReport() }

        val visible = remember(logs, filter, query) {
            logs.asReversed().filter { entry ->
                (filter == null || entry.level == filter) &&
                    (query.isBlank() || entry.tag.contains(query, true) || entry.message.contains(query, true) || entry.stackTrace.orEmpty().contains(query, true))
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.system_logs), style = AppTypography.titleLarge) },
                    navigationIcon = { BackIconButton { navigator?.pop() } },
                    actions = {
                        if (crashReport != null) IconButton(onClick = { crashDialog = true }) { Icon(Icons.Filled.BugReport, stringResource(R.string.logs_crash_report_btn), tint = MaterialTheme.colorScheme.error) }
                        IconButton(onClick = { copyText(context, AppLogger.exportLogsText()); Toast.makeText(context, context.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show() }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.logs_copy_all)) }
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, AppLogger.exportLogsText()); putExtra(Intent.EXTRA_SUBJECT, "Esjzone System Logs") }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.logs_share)))
                        }) { Icon(Icons.Filled.Share, stringResource(R.string.logs_share)) }
                        IconButton(onClick = { clearDialog = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.logs_clear)) }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.logs_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, null) } }
                )
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.lg), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    LogFilter(null, stringResource(R.string.logs_filter_all), logs.size, filter == null) { filter = null }
                    LogFilter(LogLevel.CRASH, stringResource(R.string.logs_filter_crash), logs.count { it.level == LogLevel.CRASH }, filter == LogLevel.CRASH) { filter = if (filter == LogLevel.CRASH) null else LogLevel.CRASH }
                    LogFilter(LogLevel.ERROR, stringResource(R.string.logs_filter_error), logs.count { it.level == LogLevel.ERROR }, filter == LogLevel.ERROR) { filter = if (filter == LogLevel.ERROR) null else LogLevel.ERROR }
                    LogFilter(LogLevel.WARN, stringResource(R.string.logs_filter_warning), logs.count { it.level == LogLevel.WARN }, filter == LogLevel.WARN) { filter = if (filter == LogLevel.WARN) null else LogLevel.WARN }
                    LogFilter(LogLevel.INFO, stringResource(R.string.logs_filter_info), logs.count { it.level == LogLevel.INFO }, filter == LogLevel.INFO) { filter = if (filter == LogLevel.INFO) null else LogLevel.INFO }
                    LogFilter(LogLevel.DEBUG, stringResource(R.string.logs_filter_debug), logs.count { it.level == LogLevel.DEBUG }, filter == LogLevel.DEBUG) { filter = if (filter == LogLevel.DEBUG) null else LogLevel.DEBUG }
                }
                HorizontalDivider(Modifier.padding(top = AppSpacing.sm))
                if (visible.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(AppSpacing.xxxl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Filled.BugReport, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(if (logs.isEmpty()) R.string.logs_empty else R.string.logs_no_matches), style = AppTypography.titleMedium, modifier = Modifier.padding(top = AppSpacing.md))
                        Text(stringResource(if (logs.isEmpty()) R.string.logs_empty_guidance else R.string.logs_search_empty_guidance), style = AppTypography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), state = rememberLazyListState(), contentPadding = PaddingValues(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        items(visible, key = { it.id }, contentType = { "log" }) { LogItem(entry = it, context = context) }
                    }
                }
            }
        }

        if (clearDialog) AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.logs_clear_title)) },
            text = { Text(stringResource(R.string.logs_clear_message)) },
            confirmButton = { TextButton(onClick = { AppLogger.clearLogs(); clearDialog = false; Toast.makeText(context, context.getString(R.string.logs_cleared_toast), Toast.LENGTH_SHORT).show() }) { Text(stringResource(R.string.logs_clear), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(stringResource(android.R.string.cancel)) } }
        )
        if (crashDialog && crashReport != null) AlertDialog(
            onDismissRequest = { crashDialog = false },
            title = { Text(stringResource(R.string.logs_last_crash_title)) },
            text = { Text(crashReport.orEmpty(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { copyText(context, crashReport.orEmpty()); Toast.makeText(context, context.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show() }) { Text(stringResource(R.string.logs_copy_report)) } },
            dismissButton = { TextButton(onClick = { crashDialog = false }) { Text(stringResource(android.R.string.ok)) } }
        )
    }
}

@Composable
private fun LogFilter(level: LogLevel?, label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text("$label ($count)") })
}

@Composable
private fun LogItem(entry: LogEntry, context: Context) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (entry.level) {
        LogLevel.CRASH -> stringResource(R.string.logs_filter_crash)
        LogLevel.ERROR -> stringResource(R.string.logs_filter_error)
        LogLevel.WARN -> stringResource(R.string.logs_filter_warning)
        LogLevel.INFO -> stringResource(R.string.logs_filter_info)
        LogLevel.DEBUG -> stringResource(R.string.logs_filter_debug)
    }
    val accent = when (entry.level) {
        LogLevel.CRASH, LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> Color(0xFFC17A00)
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(shape = AppShapes.standard, color = if (entry.level == LogLevel.CRASH) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = AppTypography.labelMedium, color = accent, fontWeight = FontWeight.Bold)
                Text(entry.tag, style = AppTypography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f).padding(start = AppSpacing.sm), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.formattedTime().substringAfter(' '), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(entry.message, style = AppTypography.bodyMedium)
            Text(stringResource(R.string.logs_thread, entry.threadName), style = AppTypography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            entry.stackTrace?.takeIf(String::isNotBlank)?.let { stack ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        Text(if (expanded) stringResource(R.string.logs_hide_stacktrace) else stringResource(R.string.logs_show_stacktrace))
                    }
                    IconButton(onClick = { copyText(context, stack); Toast.makeText(context, context.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show() }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.logs_copy_stacktrace)) }
                }
                AnimatedVisibility(expanded) {
                    Surface(shape = AppShapes.compact, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                        Text(stack, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(AppSpacing.sm))
                    }
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Esjzone Logs", text))
}

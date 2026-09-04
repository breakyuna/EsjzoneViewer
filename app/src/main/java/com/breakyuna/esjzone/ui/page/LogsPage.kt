package com.breakyuna.esjzone.ui.page

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.theme.QuietEditorial
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.util.AppLogger
import com.breakyuna.esjzone.util.LogEntry
import com.breakyuna.esjzone.util.LogLevel

object LogsPage : Screen {

    private fun readResolve(): Any = LogsPage

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val context = LocalContext.current

        val logs by AppLogger.logsFlow.collectAsState()
        val lastCrashReport = AppLogger.crashReportFlow.collectAsState().value
        var selectedFilter by remember { mutableStateOf<LogLevel?>(null) }
        var searchQuery by remember { mutableStateOf("") }
        var showClearDialog by remember { mutableStateOf(false) }
        var showCrashReportDialog by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            AppLogger.refreshCrashReport()
        }

        val filteredLogs = remember(logs, selectedFilter, searchQuery) {
            logs.filter { entry ->
                val matchesFilter = selectedFilter == null || entry.level == selectedFilter
                val matchesSearch = searchQuery.isBlank() ||
                        entry.tag.contains(searchQuery, ignoreCase = true) ||
                        entry.message.contains(searchQuery, ignoreCase = true) ||
                        (entry.stackTrace?.contains(searchQuery, ignoreCase = true) == true)
                matchesFilter && matchesSearch
            }.reversed() // Show newest first
        }

        val listState = rememberLazyListState()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            AppBar(
                title = stringResource(id = R.string.system_logs),
                onBack = { navigator?.pop() }
            ) {
                // Action row in top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (lastCrashReport != null) {
                        TextButton(
                            onClick = { showCrashReportDialog = true }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(id = R.string.logs_crash_report_btn),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val text = AppLogger.exportLogsText()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Esjzone Logs", text)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(id = R.string.logs_copy_all)
                        )
                    }

                    IconButton(
                        onClick = {
                            val text = AppLogger.exportLogsText()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, text)
                                putExtra(Intent.EXTRA_SUBJECT, "Esjzone System Logs")
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, context.getString(R.string.logs_share))
                            context.startActivity(shareIntent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = stringResource(id = R.string.logs_share)
                        )
                    }

                    IconButton(
                        onClick = { showClearDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(id = R.string.logs_clear)
                        )
                    }
                }
            }

            // Search Bar & Filter Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(text = stringResource(id = R.string.logs_search_placeholder), fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == null,
                        onClick = { selectedFilter = null },
                        label = { Text("${stringResource(id = R.string.logs_filter_all)} (${logs.size})") }
                    )
                    FilterChip(
                        selected = selectedFilter == LogLevel.CRASH,
                        onClick = { selectedFilter = if (selectedFilter == LogLevel.CRASH) null else LogLevel.CRASH },
                        label = {
                            val count = logs.count { it.level == LogLevel.CRASH }
                            Text("${stringResource(R.string.logs_filter_crash)} ($count)", color = if (count > 0) MaterialTheme.colorScheme.error else Color.Unspecified)
                        }
                    )
                    FilterChip(
                        selected = selectedFilter == LogLevel.ERROR,
                        onClick = { selectedFilter = if (selectedFilter == LogLevel.ERROR) null else LogLevel.ERROR },
                        label = {
                            val count = logs.count { it.level == LogLevel.ERROR }
                            Text("${stringResource(R.string.logs_filter_error)} ($count)")
                        }
                    )
                    FilterChip(
                        selected = selectedFilter == LogLevel.WARN,
                        onClick = { selectedFilter = if (selectedFilter == LogLevel.WARN) null else LogLevel.WARN },
                        label = {
                            val count = logs.count { it.level == LogLevel.WARN }
                            Text("${stringResource(R.string.logs_filter_warning)} ($count)")
                        }
                    )
                    FilterChip(
                        selected = selectedFilter == LogLevel.INFO,
                        onClick = { selectedFilter = if (selectedFilter == LogLevel.INFO) null else LogLevel.INFO },
                        label = {
                            val count = logs.count { it.level == LogLevel.INFO }
                            Text("${stringResource(R.string.logs_filter_info)} ($count)")
                        }
                    )
                    FilterChip(
                        selected = selectedFilter == LogLevel.DEBUG,
                        onClick = { selectedFilter = if (selectedFilter == LogLevel.DEBUG) null else LogLevel.DEBUG },
                        label = {
                            val count = logs.count { it.level == LogLevel.DEBUG }
                            Text("${stringResource(R.string.logs_filter_debug)} ($count)")
                        }
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp)

            // Log Items List
            if (filteredLogs.isEmpty()) {
                QuietEmptyState(
                    title = stringResource(
                        if (logs.isEmpty()) R.string.logs_empty else R.string.logs_no_matches
                    ),
                    message = stringResource(
                        if (logs.isEmpty()) R.string.logs_empty_guidance else R.string.logs_search_empty_guidance
                    ),
                    icon = Icons.Filled.BugReport,
                    modifier = Modifier.fillMaxSize().navigationBarsPadding()
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { logEntry ->
                        LogItemCard(entry = logEntry)
                    }
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }

        // Clear Logs Confirmation Dialog
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                shape = QuietEditorial.dialogShape,
                title = { Text(text = stringResource(id = R.string.logs_clear_title)) },
                text = { Text(text = stringResource(id = R.string.logs_clear_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            AppLogger.clearLogs()
                            showClearDialog = false
                            Toast.makeText(context, context.getString(R.string.logs_cleared_toast), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(text = stringResource(id = R.string.delete_history), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text(text = stringResource(id = android.R.string.cancel))
                    }
                }
            )
        }

        // Crash Report Dialog
        if (showCrashReportDialog && lastCrashReport != null) {
            AlertDialog(
                onDismissRequest = { showCrashReportDialog = false },
                shape = QuietEditorial.dialogShape,
                icon = {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text(text = stringResource(id = R.string.logs_last_crash_title)) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = lastCrashReport,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Esjzone Crash Report", lastCrashReport)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(text = stringResource(id = R.string.logs_copy_report))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCrashReportDialog = false }) {
                        Text(text = stringResource(id = android.R.string.ok))
                    }
                }
            )
        }
    }
}

@Composable
private fun LogItemCard(entry: LogEntry) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    val (badgeBgColor, badgeTextColor) = when (entry.level) {
        LogLevel.CRASH -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        LogLevel.WARN -> Color(0xFFF9A825) to Color.Black
        LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        color = if (entry.level == LogLevel.CRASH) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)
        },
        shape = QuietEditorial.cardShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header Row: Level Badge + Tag + Time
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(badgeBgColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = when (entry.level) {
                            LogLevel.CRASH -> stringResource(R.string.logs_filter_crash)
                            LogLevel.ERROR -> stringResource(R.string.logs_filter_error)
                            LogLevel.WARN -> stringResource(R.string.logs_filter_warning)
                            LogLevel.INFO -> stringResource(R.string.logs_filter_info)
                            LogLevel.DEBUG -> stringResource(R.string.logs_filter_debug)
                        },
                        color = badgeTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = entry.tag,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = entry.formattedTime().substringAfter(" "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Message text
            Text(
                text = entry.message,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )

            // Thread info
            Text(
                text = stringResource(R.string.logs_thread, entry.threadName),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Stack Trace if available
            if (!entry.stackTrace.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { expanded = !expanded }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (expanded) stringResource(id = R.string.logs_hide_stacktrace) else stringResource(id = R.string.logs_show_stacktrace),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Stack Trace", entry.stackTrace)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.logs_copied_toast), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.logs_copy_stacktrace),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                AnimatedVisibility(visible = expanded) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = entry.stackTrace,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

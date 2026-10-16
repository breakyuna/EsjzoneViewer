package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.CacheDiagnostics
import com.breakyuna.esjzone.network.CacheAnalysis
import com.breakyuna.esjzone.network.PageCache
import com.breakyuna.esjzone.data.settings.SettingsDefaults
import com.breakyuna.esjzone.network.CacheEvent
import com.breakyuna.esjzone.network.CacheEventReason
import com.breakyuna.esjzone.network.CacheEventType
import com.breakyuna.esjzone.network.CacheLayer
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.accountContentWidth
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.AppDestination
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.rememberAppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CacheRecordsPage : AppDestination {
    override val key = "CacheRecordsPage"
    private fun readResolve(): Any = CacheRecordsPage

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val context = LocalContext.current.applicationContext
        val model = rememberAppViewModel { CacheRecordsPageModel() }
        val operation by model.state.collectAsStateWithLifecycle()
        val trace by CacheDiagnostics.state.collectAsStateWithLifecycle()
        val analysis by CacheAnalysis.state.collectAsStateWithLifecycle()
        val limit by PageCache.limitMiB.collectAsStateWithLifecycle()
        var editLimit by rememberSaveable { mutableStateOf(false) }
        var limitInput by rememberSaveable { mutableStateOf("") }
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) model.export(context, uri)
        }
        var filter by remember { mutableStateOf<CacheEventType?>(null) }
        val visible = remember(trace.events, filter) {
            trace.events.asReversed().filter { filter == null || it.type == filter }
        }
        val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault()) }
        Scaffold(topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cache_records), style = AppTypography.titleMedium) },
                navigationIcon = { BackIconButton { navigator?.pop() } },
                actions = {
                    IconButton(onClick = { export.launch("cache-analysis-${System.currentTimeMillis()}.json") },
                        enabled = !operation.busy) {
                        Icon(Icons.Filled.SaveAlt, stringResource(R.string.cache_analysis_export))
                    }
                    IconButton(onClick = CacheDiagnostics::clear, enabled = trace.events.isNotEmpty()) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.cache_records_clear))
                    }
                }
            )
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).accountContentWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                item(key = "controls") {
                    Column {
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable(enabled = !operation.busy) { limitInput = limit.toString(); editLimit = true }
                            .padding(horizontal = AppSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.cache_limit), style = AppTypography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.cache_limit_value, limit), style = AppTypography.labelLarge,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            val sample = analysis.current?.takeIf { it.limitMiB == limit }
                            listOf(CacheLayer.PAGE, CacheLayer.CHAPTER, CacheLayer.LIST).forEach { layer ->
                                val counts = sample?.layers?.get(layer.name)
                                val rate = counts?.hitRate()?.let { String.format(Locale.getDefault(), "%.1f%%", it * 100.0) } ?: "—"
                                Text(stringResource(R.string.cache_analysis_hit_rate, stringResource(layer.label()), rate,
                                    counts?.freshHits ?: 0L, (counts?.freshHits ?: 0L) + (counts?.misses ?: 0L)),
                                    style = AppTypography.bodySmall)
                            }
                            Text(stringResource(R.string.cache_analysis_eviction_rate,
                                sample?.capacityEvictionsPerHour?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—",
                                sample?.layers?.values?.sumOf { it.capacityEvictions } ?: 0L), style = AppTypography.bodySmall)
                            Text(stringResource(R.string.cache_analysis_storage,
                                sample?.sizeBytes?.let(::formatBytes) ?: "—", formatBytes(analysis.logBytes)), style = AppTypography.bodySmall)
                            if (analysis.storageError || operation.failed) {
                                Text(stringResource(R.string.cache_analysis_failed), color = MaterialTheme.colorScheme.error,
                                    style = AppTypography.bodySmall)
                            }
                            if (operation.exported) {
                                Text(stringResource(R.string.cache_analysis_exported), style = AppTypography.bodySmall)
                            }
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .toggleable(value = trace.recording, role = Role.Switch, onValueChange = CacheDiagnostics::setRecording)
                            .padding(horizontal = AppSpacing.lg),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.cache_records_recording), style = AppTypography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            Switch(checked = trace.recording, onCheckedChange = null)
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(horizontal = AppSpacing.lg), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            FilterChip(selected = filter == null, onClick = { filter = null },
                                label = { Text(stringResource(R.string.cache_records_filter_count,
                                    stringResource(R.string.logs_filter_all), trace.events.size)) })
                            CacheEventType.entries.forEach { type ->
                                FilterChip(selected = filter == type, onClick = { filter = type },
                                    label = { Text(stringResource(R.string.cache_records_filter_count,
                                        stringResource(type.label()), trace.events.count { it.type == type })) })
                            }
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(AppSpacing.xxxl), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.cache_records_empty), style = AppTypography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(visible, key = { it.id }, contentType = { "cache-event" }) { event ->
                        Box(Modifier.padding(horizontal = AppSpacing.lg)) {
                            CacheEventRow(event, timeFormat)
                        }
                    }
                }
            }
        }
        if (editLimit) {
            val parsed = limitInput.toIntOrNull()
            val valid = parsed != null && parsed in SettingsDefaults.MIN_PAGE_CACHE_MIB..SettingsDefaults.MAX_PAGE_CACHE_MIB
            AlertDialog(onDismissRequest = { editLimit = false },
                title = { Text(stringResource(R.string.cache_limit)) },
                text = {
                    OutlinedTextField(value = limitInput, onValueChange = { limitInput = it }, singleLine = true,
                        label = { Text(stringResource(R.string.cache_limit_unit)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = limitInput.isNotEmpty() && !valid,
                        supportingText = if (limitInput.isNotEmpty() && !valid) {
                            { Text(stringResource(R.string.cache_limit_range, SettingsDefaults.MIN_PAGE_CACHE_MIB, SettingsDefaults.MAX_PAGE_CACHE_MIB)) }
                        } else null)
                },
                confirmButton = {
                    TextButton(enabled = valid && !operation.busy, onClick = {
                        model.saveLimit(requireNotNull(parsed)); editLimit = false
                    }) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = { editLimit = false }) { Text(stringResource(android.R.string.cancel)) } })
        }
    }
}

@Composable
private fun CacheEventRow(event: CacheEvent, timeFormat: DateTimeFormatter) {
    Surface(shape = AppShapes.standard, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(event.type.label()), style = AppTypography.labelLarge,
                    color = if (event.type == CacheEventType.FAILURE) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary)
                Text(stringResource(event.layer.label()), style = AppTypography.labelMedium, modifier = Modifier.weight(1f))
                Text(timeFormat.format(Instant.ofEpochMilli(event.timestamp)), style = AppTypography.bodySmall)
            }
            Text(stringResource(event.reason.label()), style = AppTypography.bodyMedium)
            if (event.entryId != null || event.bytes != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                    event.entryId?.let { Text(it, style = AppTypography.bodySmall, fontFamily = FontFamily.Monospace) }
                    event.bytes?.let { Text(formatBytes(it), style = AppTypography.bodySmall) }
                }
            }
        }
    }
}

private fun CacheEventType.label(): Int = when (this) {
    CacheEventType.HIT -> R.string.cache_event_hit
    CacheEventType.UPDATE -> R.string.cache_event_update
    CacheEventType.EVICT -> R.string.cache_event_evict
    CacheEventType.FAILURE -> R.string.cache_event_failure
}

private fun CacheLayer.label(): Int = when (this) {
    CacheLayer.PAGE -> R.string.cache_layer_page
    CacheLayer.CHAPTER -> R.string.cache_layer_chapter
    CacheLayer.LIST -> R.string.cache_layer_list
    CacheLayer.DETAIL -> R.string.cache_layer_detail
    CacheLayer.HOME -> R.string.cache_layer_home
    CacheLayer.HISTORY -> R.string.cache_layer_history
}

private fun CacheEventReason.label(): Int = when (this) {
    CacheEventReason.FRESH -> R.string.cache_reason_fresh
    CacheEventReason.STALE -> R.string.cache_reason_stale
    CacheEventReason.MEMORY -> R.string.cache_reason_memory
    CacheEventReason.DISK -> R.string.cache_reason_disk
    CacheEventReason.STORED -> R.string.cache_reason_stored
    CacheEventReason.REPLACED -> R.string.cache_reason_replaced
    CacheEventReason.CAPACITY -> R.string.cache_reason_capacity
    CacheEventReason.EXPIRED -> R.string.cache_reason_expired
    CacheEventReason.INVALIDATED -> R.string.cache_reason_invalidated
    CacheEventReason.SOURCE_UPDATED -> R.string.cache_reason_source_updated
    CacheEventReason.DOWNLOADED -> R.string.cache_reason_downloaded
    CacheEventReason.CLEARED -> R.string.cache_reason_cleared
    CacheEventReason.INVALID_DATA -> R.string.cache_reason_invalid_data
    CacheEventReason.READ_FAILED -> R.string.cache_reason_read_failed
    CacheEventReason.WRITE_FAILED -> R.string.cache_reason_write_failed
    CacheEventReason.DELETE_FAILED -> R.string.cache_reason_delete_failed
    CacheEventReason.SUPERSEDED -> R.string.cache_reason_superseded
}

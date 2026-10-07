package com.breakyuna.esjzone.ui.designsystem

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.breakyuna.esjzone.ui.component.AppGroup
import com.breakyuna.esjzone.ui.component.AppTag

/** Local, network-free review of the actual theme and shared controls. */
@Preview(name = "Paper · light", widthDp = 390, heightDp = 780)
@Preview(name = "Paper · dark", widthDp = 390, heightDp = 780, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Paper · large text", widthDp = 390, heightDp = 900, fontScale = 1.5f)
@Composable
private fun PaperThemePreview() {
    AppTheme {
        val colors = MaterialTheme.colorScheme
        val accents = appAccentColors()
        Surface(color = colors.background) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("我的书架", style = AppTypography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("全部") })
                    FilterChip(selected = false, onClick = {}, label = { Text("最近更新") })
                }
                Surface(shape = AppShapes.prominent, color = colors.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppTag("本周热门", color = colors.onTertiaryContainer, containerColor = colors.tertiaryContainer)
                        Text("星海边的长夜", style = AppTypography.titleLarge)
                        Text("第十二章 · 漫长旅途的起点", style = AppTypography.bodyMedium)
                        LinearProgressIndicator(progress = { 0.42f }, modifier = Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {}) {
                                Icon(Icons.Default.MenuBook, null)
                                Text("继续阅读")
                            }
                            Button(onClick = {}, colors = ButtonDefaults.buttonColors(
                                containerColor = accents.favoriteContainer, contentColor = accents.favorite
                            )) {
                                Icon(Icons.Default.Favorite, null)
                                Text("已收藏")
                            }
                        }
                    }
                }
                AppGroup {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("自动备份", modifier = Modifier.weight(1f), style = AppTypography.bodyLarge)
                        Switch(checked = true, onCheckedChange = {})
                    }
                }
                OutlinedTextField(value = "", onValueChange = {}, label = { Text("搜索作品") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTag("已完成", color = accents.success, containerColor = accents.successContainer)
                    AppTag("等待中", color = accents.warning, containerColor = accents.warningContainer)
                    AppTag("离线", color = accents.info, containerColor = accents.infoContainer)
                }
                Surface(shape = AppShapes.standard, color = colors.errorContainer, contentColor = colors.onErrorContainer) {
                    Text("加载失败，请重试", modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
            }
        }
    }
}

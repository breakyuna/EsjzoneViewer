package com.breakyuna.esjzone.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.breakyuna.esjzone.ui.designsystem.AppFeedback
import com.breakyuna.esjzone.ui.designsystem.AppLottieAsset
import com.breakyuna.esjzone.ui.designsystem.AppLottieState
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppShimmerPlaceholder
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.ui.designsystem.AppTypography

@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, label: String = "正在加载") {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppShimmerPlaceholder(
            modifier = Modifier.fillMaxWidth().height(136.dp),
            shape = AppShapes.standard
        )
        repeat(2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppShimmerPlaceholder(Modifier.size(width = 72.dp, height = 96.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    AppShimmerPlaceholder(Modifier.fillMaxWidth(0.82f).height(18.dp), shape = AppShapes.compact)
                    AppShimmerPlaceholder(Modifier.fillMaxWidth(0.58f).height(14.dp), shape = AppShapes.compact)
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        AppLottieState(
            asset = AppLottieAsset.EmptyState,
            contentDescription = title
        )
        Text(title, style = AppTypography.titleMedium)
        message?.let { Text(it, style = AppTypography.bodyMedium) }
        if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun ErrorState(
    title: String = "加载失败",
    message: String,
    modifier: Modifier = Modifier,
    retryLabel: String = "重试",
    onRetry: (() -> Unit)? = null
) {
    AppFeedback(title = title, message = message, actionLabel = retryLabel.takeIf { onRetry != null }, onAction = onRetry, modifier = modifier)
}

@Composable
fun OfflineState(
    title: String = "当前处于离线状态",
    message: String = "你仍然可以查看已经保存在设备上的内容。",
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null
) {
    EmptyState(title, message, modifier, actionLabel = "重新连接".takeIf { onRetry != null }, onAction = onRetry)
}

@Composable
fun ErrorStateCompact(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    androidx.compose.foundation.layout.Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(message, style = AppTypography.bodySmall, modifier = Modifier.weight(1f))
        if (onRetry != null) androidx.compose.material3.TextButton(onClick = onRetry) { Text("重试") }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun FeedbackPreview() {
    AppTheme {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            LoadingSkeleton()
            EmptyState("暂无内容", "稍后回来看看吧")
            OfflineState()
            ErrorState(message = "请稍后重试", onRetry = {})
        }
    }
}

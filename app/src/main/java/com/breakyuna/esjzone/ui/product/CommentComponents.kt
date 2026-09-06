package com.breakyuna.esjzone.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.breakyuna.esjzone.ui.designsystem.AppShapes
import com.breakyuna.esjzone.ui.designsystem.AppSpacing
import com.breakyuna.esjzone.ui.designsystem.AppTheme
import com.breakyuna.esjzone.ui.designsystem.AppTypography
import com.breakyuna.esjzone.ui.designsystem.appStateColors

@Immutable
data class CommentCardModel(
    val id: String,
    val author: String,
    val body: String,
    val timestamp: String? = null,
    val likeLabel: String? = null,
    val replyLabel: String? = null,
    val isAuthor: Boolean = false
)

@Composable
fun CommentCard(
    comment: CommentCardModel,
    modifier: Modifier = Modifier,
    onReply: (() -> Unit)? = null,
    onLike: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = appStateColors().containerRaised),
        shape = AppShapes.standard
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), modifier = Modifier.fillMaxWidth()) {
                Text(comment.author, style = AppTypography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (comment.isAuthor) {
                    Text("作者", style = AppTypography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                comment.timestamp?.let { Text(it, style = AppTypography.bodySmall, color = appStateColors().contentMuted) }
            }
            Text(comment.body, style = AppTypography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs), modifier = Modifier.fillMaxWidth()) {
                comment.likeLabel?.let { label ->
                    TextButton(onClick = { onLike?.invoke() }, enabled = onLike != null, modifier = Modifier.semantics { if (onLike != null) role = Role.Button }) {
                        Text(label)
                    }
                }
                comment.replyLabel?.let { label ->
                    TextButton(onClick = { onReply?.invoke() }, enabled = onReply != null, modifier = Modifier.semantics { if (onReply != null) role = Role.Button }) {
                        Text(label)
                    }
                }
            }
        }
    }
}

@Immutable
data class ReplyTarget(val commentId: String, val displayName: String)

@Composable
fun CommentComposer(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    replyTarget: ReplyTarget? = null,
    placeholder: String = "写下评论",
    sendLabel: String = "发送",
    enabled: Boolean = true,
    onCancelReply: (() -> Unit)? = null,
    onSend: () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        replyTarget?.let { target ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text("回复 ${target.displayName}", style = AppTypography.labelMedium, color = appStateColors().contentMuted, modifier = Modifier.weight(1f))
                if (onCancelReply != null) {
                    TextButton(onClick = onCancelReply) { Text("取消") }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                placeholder = { Text(placeholder) },
                singleLine = false,
                maxLines = 4
            )
            Button(onClick = onSend, enabled = enabled && value.isNotBlank(), modifier = Modifier.align(androidx.compose.ui.Alignment.Bottom)) {
                Text(sendLabel)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun CommentPreview() {
    AppTheme {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            CommentCard(CommentCardModel("1", "读者", "这是一条评论。", "刚刚", "赞 3", "回复"))
            CommentComposer("", {}, replyTarget = ReplyTarget("1", "读者"), onCancelReply = {}, onSend = {})
        }
    }
}

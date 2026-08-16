package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.ChatMessageDomainModel
import com.github.hatoyuze.luogu.gui.domain.model.MessageStatus
import compose.icons.FeatherIcons
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Edit3
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Square
import compose.icons.feathericons.Trash2

/**
 * 消息操作工具栏 — 显示在消息气泡下方。
 * 扁平设计：纯图标按钮，无 Material ripple，Hover 时显现。
 *
 * Hover detection is handled by the PARENT composable (ChatMessages Column)
 * via a non-consuming `pointerInput`/`awaitPointerEventScope` observer that only
 * tracks Enter/Exit (never presses), so child clickable modifiers keep receiving
 * their events on every platform (desktop, Android, iOS).
 */
@Composable
fun MessageActionBar(
    message: ChatMessageDomainModel,
    isStreaming: Boolean,
    isHovered: Boolean,
    canRegenerate: Boolean,
    canDelete: Boolean = true,
    onEdit: () -> Unit,
    onStop: () -> Unit,
    onRegenerate: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    labeled: Boolean = false,
) {
    val canModify = !isStreaming && message.status != MessageStatus.SENDING

    val targetAlpha = when {
        labeled -> 1f
        isStreaming -> 0.75f
        isHovered -> 0.75f
        canModify -> 0.15f
        else -> 0.0f
    }
    val hoverAlpha by animateFloatAsState(targetValue = targetAlpha, label = "actionBarAlpha")

    Row(
        modifier = modifier.alpha(hoverAlpha),
        horizontalArrangement = Arrangement.spacedBy(if (labeled) 6.dp else 2.dp),
    ) {
        if (isStreaming && !message.isUser) {
            ActionButton(
                icon = FeatherIcons.Square,
                contentDescription = "中止生成",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                onClick = onStop,
                labeled = labeled,
                label = "中止",
            )
        }

        if (canModify && message.isUser) {
            ActionButton(
                icon = FeatherIcons.Edit3,
                contentDescription = "修改信息",
                onClick = onEdit,
                labeled = labeled,
                label = "编辑",
            )
        }

        if (canModify && !message.isUser && canRegenerate) {
            ActionButton(
                icon = FeatherIcons.RefreshCw,
                contentDescription = "重新生成",
                onClick = onRegenerate,
                labeled = labeled,
                label = "重试",
            )
        }

        if (message.content.isNotEmpty()) {
            ActionButton(
                icon = FeatherIcons.Copy,
                contentDescription = "复制",
                onClick = onCopy,
                labeled = labeled,
                label = "复制",
            )
        }

        if (canModify && canDelete) {
            ActionButton(
                icon = FeatherIcons.Trash2,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                onClick = onDelete,
                labeled = labeled,
                label = "删除",
            )
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    onClick: () -> Unit,
    labeled: Boolean = false,
    label: String? = null,
) {
    if (labeled && label != null) {
        // 移动端常显操作条：带文案胶囊（图标 + 文字）
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(12.dp),
                    tint = tint,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = tint,
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier.size(28.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(14.dp),
                    tint = tint,
                )
            }
        }
    }
}

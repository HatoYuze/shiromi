package com.github.hatoyuze.luogu.gui.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.ChatMessageDomainModel
import com.github.hatoyuze.luogu.gui.presentation.components.ChatSidebar
import com.github.hatoyuze.luogu.gui.presentation.components.askuser.AskUserCard
import com.github.hatoyuze.luogu.gui.presentation.state.ChatEvent
import com.github.hatoyuze.luogu.gui.presentation.state.ChatUiState
import com.github.hatoyuze.luogu.gui.theme.LocalThemeIsDark
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.MoreVertical
import compose.icons.feathericons.Moon
import compose.icons.feathericons.Sun
import kotlinx.coroutines.delay

/**
 * 手机端（Compact）对话页。
 *
 * 与桌面版共享 [ChatMessages] / [ChatInput] / [AskUserCard]；差异点：
 * - 无侧栏：顶栏「⋮」打开 [ChatSidebar] 的底部弹层（SessionSheet）
 * - 消息操作条常显（无悬停），由 `alwaysShowActions` 驱动
 * - TODO 条横向滚动；输入区带 IME / 导航栏 inset 适配
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileChatScreen(
    uiState: ChatUiState,
    messages: List<ChatMessageDomainModel>,
    onEvent: (ChatEvent) -> Unit,
    onBack: () -> Unit,
) {
    var showSessionSheet by remember { mutableStateOf(false) }
    var isDark by LocalThemeIsDark.current
    val listState = rememberLazyListState()
    val pending = uiState.pendingAskUser

    Column(modifier = Modifier.fillMaxSize().navigationBarsPadding()) {
        // ── 顶栏：返回 / 会话标题 / 主题切换 / ⋮ ──
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        uiState.currentSession?.title?.takeIf { it.isNotBlank() } ?: "对话",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (uiState.currentSession?.type == com.github.hatoyuze.luogu.gui.domain.model.SessionType.COACH) "COACH" else "CHAT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
                IconButton(onClick = { isDark = !isDark }) {
                    Icon(if (isDark) FeatherIcons.Sun else FeatherIcons.Moon, contentDescription = "切换主题")
                }
                IconButton(onClick = { showSessionSheet = true }) {
                    Icon(FeatherIcons.MoreVertical, contentDescription = "会话列表")
                }
            }
        }

        // ── 消息区 ──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            ChatMessages(
                state = listState,
                messages = messages,
                isLoading = uiState.isLoading,
                editingMessageId = uiState.editingMessageId,
                activeBranchId = uiState.activeBranchId,
                branches = uiState.branches,
                onEvent = onEvent,
                alwaysShowActions = true,
            )
        }

        // ── TODO 横向滚动条 ──
        if (uiState.todos.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                        Text(
                            "TODO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                items(uiState.todos, key = { it.id }) { todo ->
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (todo.completed) "✓" else "○", style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.width(4.dp))
                            Text(todo.title.take(20), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
        }

        // ── AskUser 卡 ──
        if (pending != null && pending.answer == null) {
            AskUserCard(
                desc = pending.desc,
                options = pending.options,
                isMulti = pending.isMulti,
                allowCustom = pending.allowCustom,
                timeoutMs = pending.timeoutMs,
                startedAtMs = pending.startedAtMs,
                onAnswer = { selected, custom -> onEvent(ChatEvent.AnswerAskUser(selected, custom)) },
                modifier = Modifier.padding(horizontal = 12.dp).padding(top = 4.dp),
            )
        }

        // ── 删除确认 ──
        val pendingDelete = uiState.pendingDelete
        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { onEvent(ChatEvent.DismissDelete) },
                title = { Text("确认删除") },
                text = { Text("你真的要删除这条信息吗？此操作不可逆！") },
                confirmButton = {
                    TextButton(onClick = {
                        onEvent(ChatEvent.DeleteExchange(pendingDelete.userMessageId, pendingDelete.assistantMessageId))
                    }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { onEvent(ChatEvent.DismissDelete) }) { Text("取消") }
                },
            )
        }

        // ── Toast ──
        val toast = uiState.toast
        if (toast != null) {
            LaunchedEffect(toast) {
                delay(2000)
                onEvent(ChatEvent.ClearToast)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
            ) {
                Text(
                    text = toast,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        }

        // ── 输入区（IME 适配；发送只走按钮）──
        ChatInput(
            onSendMessage = { onEvent(ChatEvent.SendMessage(it)) },
            enabled = !uiState.isLoading && (pending == null || pending.answer == null),
            modifier = Modifier.fillMaxWidth().padding(12.dp).imePadding(),
        )
    }

    // ── SessionSheet：复用桌面 ChatSidebar ──
    if (showSessionSheet) {
        ModalBottomSheet(onDismissRequest = { showSessionSheet = false }) {
            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).navigationBarsPadding()) {
                ChatSidebar(
                    uiState = uiState,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

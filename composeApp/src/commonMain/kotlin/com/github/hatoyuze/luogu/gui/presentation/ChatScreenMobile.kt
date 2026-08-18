package com.github.hatoyuze.luogu.gui.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.ChatMessageDomainModel
import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import com.github.hatoyuze.luogu.gui.presentation.components.askuser.AskUserCard
import com.github.hatoyuze.luogu.gui.presentation.components.icons.AppIcons
import com.github.hatoyuze.luogu.gui.presentation.state.ChatEvent
import com.github.hatoyuze.luogu.gui.presentation.state.ChatUiState
import com.github.hatoyuze.luogu.gui.theme.LocalThemeIsDark
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.BookOpen
import compose.icons.feathericons.Check
import compose.icons.feathericons.MessageSquare
import compose.icons.feathericons.Moon
import compose.icons.feathericons.MoreVertical
import kotlinx.coroutines.delay

/**
 * 手机端（Compact）对话页。
 *
 * 与桌面版共享 [ChatMessages] / [ChatInput] / [AskUserCard]；差异点：
 * - 顶栏：返回 + 居中标题/副标题 + ⋮（无主题切换按钮，主题开关在 SessionSheet 内）
 * - 消息操作条带文案常显（[ChatMessages] compact 模式）
 * - TODO 条横向滚动；输入区带 IME / 导航栏 inset 适配
 * - 会话列表使用专用 [MobileSessionSheet]，不再复用桌面 [ChatSidebar]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileChatScreen(
    uiState: ChatUiState,
    messages: List<ChatMessageDomainModel>,
    onEvent: (ChatEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProblem: ((String) -> Unit)? = null,
) {
    var showSessionSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val pending = uiState.pendingAskUser

    // 根 Column 不再加 navigationBarsPadding：MobileNav 的 Scaffold 已计入
    // 底部导航栏高度 + 顶部状态栏 inset，避免输入区被双重抬高；IME 可见时
    // Scaffold 会隐藏底部导航栏，输入框经 imePadding 直接落在键盘顶边。
    Column(modifier = Modifier.fillMaxSize()) {
        // ── 顶栏：返回 / 居中标题+副标题 / ⋮ ──
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(FeatherIcons.ArrowLeft, contentDescription = "返回")
                }
                Column(
                    Modifier.weight(1f).padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        uiState.currentSession?.title?.takeIf { it.isNotBlank() } ?: "对话",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "${if (uiState.currentSession?.type == SessionType.COACH) "COACH" else "CHAT"} · ${messages.size} 条消息",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
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
                compact = true,
                onOpenProblem = onOpenProblem,
            )
        }

        // ── TODO 横向滚动条 ──
        if (uiState.todos.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
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
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (todo.completed) {
                                Icon(
                                    AppIcons.SuccessIcon,
                                    contentDescription = "已完成",
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Text("○", style = MaterialTheme.typography.labelSmall)
                            }
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
                compact = true,
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

        // ── 输入区（发送只走按钮）──
        // imePadding 是输入框唯一的 IME 偏移来源（Scaffold 只取顶部状态栏、
        // IME 可见时隐藏底部导航栏），键盘弹出时输入框恰好落在键盘顶边一次
        ChatInput(
            onSendMessage = { onEvent(ChatEvent.SendMessage(it)) },
            enabled = !uiState.isLoading && (pending == null || pending.answer == null),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).imePadding(),
            compact = true,
        )
    }

    // ── SessionSheet：移动端专用会话弹层 ──
    if (showSessionSheet) {
        MobileSessionSheet(
            uiState = uiState,
            onEvent = onEvent,
            onDismiss = { showSessionSheet = false },
        )
    }
}

/**
 * 移动端会话弹层：新对话/新教练 + 历史会话 + 深色模式开关。
 * 深色开关承接原顶栏的 🌙/☀️ 主题切换（状态仍经 [LocalThemeIsDark] 根部共享）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSessionSheet(
    uiState: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var isDark by LocalThemeIsDark.current

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            // ── 新对话 / 新教练 ──
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        onEvent(ChatEvent.CreateNewSession(SessionType.CHAT))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(FeatherIcons.MessageSquare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新对话")
                }
                FilledTonalButton(
                    onClick = {
                        onEvent(ChatEvent.CreateNewSession(SessionType.COACH))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(FeatherIcons.BookOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新教练")
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "历史会话",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))

            // ── 会话列表 ──
            if (uiState.chatSessions.isEmpty()) {
                Text(
                    "暂无会话",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                // 会话多时可滚动，避免超出弹层高度被裁切
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(uiState.chatSessions, key = { it.id }) { session ->
                        val selected = session.id == uiState.currentSession?.id
                        Surface(
                            onClick = {
                                onEvent(ChatEvent.SelectSession(session))
                                onDismiss()
                            },
                            shape = RoundedCornerShape(11.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            ),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        ),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        session.title.ifBlank { "未命名会话" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (session.type == SessionType.COACH) "COACH" else "CHAT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        FeatherIcons.Check,
                                        contentDescription = "当前会话",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ── 深色模式（主题切换迁移至此）──
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    FeatherIcons.Moon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("深色模式", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = isDark, onCheckedChange = { isDark = it })
            }
        }
    }
}

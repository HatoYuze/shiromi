package com.github.hatoyuze.luogu.gui.presentation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import com.github.hatoyuze.luogu.gui.platform.copyTextToClipboard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


import com.github.hatoyuze.luogu.gui.domain.model.ChatBranchDomainModel
import com.github.hatoyuze.luogu.gui.domain.model.ChatMessageDomainModel
import com.github.hatoyuze.luogu.gui.domain.model.MessageStatus
import com.github.hatoyuze.luogu.gui.presentation.components.AnimatedBorderBox
import com.github.hatoyuze.luogu.gui.presentation.components.ChatSidebar
import com.github.hatoyuze.luogu.gui.presentation.components.MessageActionBar
import com.github.hatoyuze.luogu.gui.presentation.components.MessageBubble
import com.github.hatoyuze.luogu.gui.presentation.components.askuser.AskUserCard



import com.github.hatoyuze.luogu.gui.presentation.components.ProblemDetailPage
import com.github.hatoyuze.luogu.gui.presentation.components.PulsatingDot
import com.github.hatoyuze.luogu.gui.presentation.components.RightSideSheet
import com.github.hatoyuze.luogu.gui.presentation.components.ThinkingIndicator
import com.github.hatoyuze.luogu.gui.presentation.components.icons.AppIcons

import com.github.hatoyuze.luogu.gui.presentation.modifier.animatedBorder

import com.github.hatoyuze.luogu.gui.presentation.state.ChatEvent
import com.github.hatoyuze.luogu.gui.presentation.state.ChatUiState
import com.github.hatoyuze.luogu.gui.presentation.state.ChatViewModel
import com.github.hatoyuze.luogu.gui.theme.LocalThemeIsDark
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.MarkdownTypography
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronLeft
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Moon
import compose.icons.feathericons.Send
import compose.icons.feathericons.Sun


@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.state.collectAsState()
    // Problem detail overlay: opened from coach finished-card recommend pills;
    // back returns to the conversation.
    var problemDetailPid by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box {
            BoxWithConstraints {
                if (maxWidth < 600.dp) {
                    MobileChatScreen(
                        uiState = uiState,
                        messages = uiState.messages,
                        onEvent = viewModel::handleEvent,
                        onBack = onBack,
                        onOpenProblem = { problemDetailPid = it },
                    )
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {

                        ChatSidebar(
                            uiState = uiState,
                            onEvent = viewModel::handleEvent,
                            modifier = Modifier.width(264.dp)
                        )

                        MainContent(
                            uiState = uiState,
                            messages = uiState.messages,
                            onEvent = viewModel::handleEvent,
                            onOpenProblem = { problemDetailPid = it },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── Problem detail overlay (full screen; back returns to the chat) ──
            problemDetailPid?.let { pid ->
                ProblemDetailPage(
                    pid = pid,
                    onBack = { problemDetailPid = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}


@Composable
private fun MainContent(
    uiState: ChatUiState,
    messages: List<ChatMessageDomainModel>,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenProblem: ((String) -> Unit)? = null,
) {
    val state = rememberLazyListState()

    LaunchedEffect(messages.size) {
        state.animateScrollToItem(messages.size)
    }
    Column(modifier = modifier.fillMaxSize()) {

        // ── 顶栏：会话标题 + 类型/消息数 + 主题切换（对齐移动端对话页头）──
        Surface(
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = uiState.currentSession?.title ?: "对话",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    val typeLabel = if (uiState.currentSession?.type == com.github.hatoyuze.luogu.gui.domain.model.SessionType.COACH)
                        "COACH" else "CHAT"
                    Text(
                        text = "$typeLabel · ${messages.size} 条消息",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.weight(1f))
                var isDark by LocalThemeIsDark.current
                IconButton(onClick = { isDark = !isDark }) {
                    Icon(
                        if (isDark) FeatherIcons.Sun else FeatherIcons.Moon,
                        contentDescription = "切换主题"
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        ) {
            // 消息列居中，最大宽度 880dp（对齐样板：居中消息列）
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                ChatMessages(
                    state = state,
                    messages = messages,
                    isLoading = uiState.isLoading,
                    editingMessageId = uiState.editingMessageId,
                    activeBranchId = uiState.activeBranchId,
                    branches = uiState.branches,
                    onEvent = onEvent,
                    onOpenProblem = onOpenProblem,
                    modifier = Modifier.fillMaxSize().widthIn(max = 880.dp),
                )
            }
        }

        // TODO compact bar
        if (uiState.todos.isNotEmpty()) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), RoundedCornerShape(8.dp),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("TODO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    uiState.todos.forEach { todo ->
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
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
        }

        // Ask user card — shown above input when AI asks a question
        val pending = uiState.pendingAskUser
        if (pending != null && pending.answer == null) {
            AskUserCard(
                desc = pending.desc,
                options = pending.options,
                isMulti = pending.isMulti,
                allowCustom = pending.allowCustom,
                timeoutMs = pending.timeoutMs,
                startedAtMs = pending.startedAtMs,
                onAnswer = { selected, custom ->
                    onEvent(ChatEvent.AnswerAskUser(selected, custom))
                },
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp),
            )
        }

        // Delete confirmation dialog
        val pendingDelete = uiState.pendingDelete
        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { onEvent(ChatEvent.DismissDelete) },
                title = { Text("确认删除") },
                text = { Text("你真的要删除这条信息吗？此操作不可逆！") },
                confirmButton = {
                    TextButton(onClick = {
                        onEvent(ChatEvent.DeleteExchange(pendingDelete.userMessageId, pendingDelete.assistantMessageId))
                    }) {
                        Text("确认删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onEvent(ChatEvent.DismissDelete) }) {
                        Text("取消")
                    }
                },
            )
        }

        // Toast overlay
        val toast = uiState.toast
        if (toast != null) {
            LaunchedEffect(toast) {
                delay(2000)
                onEvent(ChatEvent.ClearToast)
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

        ChatInput(
            onSendMessage = { onEvent(ChatEvent.SendMessage(it)) },
            enabled = !uiState.isLoading && (pending == null || pending.answer != null),
            modifier = Modifier.padding(16.dp),
        )
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun ChatMessages(
    state: LazyListState,
    messages: List<ChatMessageDomainModel>,
    isLoading: Boolean,
    editingMessageId: String?,
    activeBranchId: String,
    branches: List<ChatBranchDomainModel>,
    onEvent: (ChatEvent) -> Unit,
    alwaysShowActions: Boolean = false,
    compact: Boolean = false,
    onOpenProblem: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 新消息到达 → 动画滚动到底
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) state.animateScrollToItem(messages.size - 1)
    }

    // 流式输出：最后一条消息内容持续增长（size 不变）→ 无动画贴底跟随，
    // 解决 agent 更新信息时列表"悬停"不自动下拉的问题。
    // 仅移动端（compact）启用，桌面保持原有"仅新消息滚动"行为。
    if (compact) {
        val lastMessage = messages.lastOrNull()
        LaunchedEffect(lastMessage?.id, lastMessage?.content?.length) {
            if (messages.isNotEmpty()) state.scrollToItem(messages.size - 1)
        }
    }

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(messages, key = { it.id }, contentType = { if (it.isUser) "user" else "assistant" }) { message ->
            val hasContent = message.content.isNotEmpty()
                || !message.thinkingContent.isNullOrBlank()
                || !message.toolCalls.isNullOrEmpty()
                || !message.segments.isNullOrEmpty()
            if (hasContent) {
                // Check if this is the latest assistant message (for regenerate constraint)
                val isLatestAssistant = !message.isUser
                    && messages.lastOrNull { !it.isUser }?.id == message.id
                    && messages.indexOfFirst { it.id == message.id } > 0
                    && messages.getOrNull(messages.indexOfFirst { it.id == message.id } - 1)?.isUser == true

                // First user message should not be deletable
                val firstUserMsgIdx = messages.indexOfFirst { it.isUser }
                val isFirstUserMessage = message.isUser
                    && messages.indexOfFirst { it.id == message.id } == firstUserMsgIdx

                // Hover tracking on the message row. Uses the common pointer-input
                // API (pointerInput/awaitPointerEventScope) instead of the desktop-only
                // onPointerEvent; events are observed without consuming them, so
                // scrolling/clicking below is unaffected. No-op on touch platforms.
                var isHovered by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(message.id) {
                            awaitPointerEventScope {
                                while (true) {
                                    when (awaitPointerEvent().type) {
                                        PointerEventType.Enter -> isHovered = true
                                        PointerEventType.Exit -> isHovered = false
                                        else -> {}
                                    }
                                }
                            }
                        }
                ) {
                    MessageBubble(
                        message = message,
                        onRetry = { onEvent(ChatEvent.RetryMessage(message)) },
                        editingMessageId = editingMessageId,
                        onSendEdit = { id, content -> onEvent(ChatEvent.SendEdit(id, content)) },
                        onCancelEdit = { onEvent(ChatEvent.CancelEdit) },
                        compact = compact,
                        onOpenProblem = onOpenProblem,
                    )
                    MessageActionBar(
                        message = message,
                        isStreaming = isLoading && message.status == MessageStatus.SENDING,
                        isHovered = isHovered || alwaysShowActions,
                        canRegenerate = isLatestAssistant,
                        canDelete = !isFirstUserMessage,
                        labeled = compact,
                        onEdit = { onEvent(ChatEvent.StartEdit(message.id)) },
                        onStop = { onEvent(ChatEvent.StopGeneration) },
                        onRegenerate = {
                            // Regenerate is always on an assistant message — find the preceding user
                            val idx = messages.indexOfFirst { it.id == message.id }
                            val userMsg = messages.getOrNull(idx - 1)?.takeIf { it.isUser }
                            if (userMsg != null) onEvent(ChatEvent.RegenerateMessage(userMsg.id))
                        },
                        onDelete = {
                            val idx = messages.indexOfFirst { it.id == message.id }
                            if (message.isUser) {
                                val assistantMsg = messages.getOrNull(idx + 1)?.takeUnless { it.isUser }
                                if (assistantMsg != null) {
                                    onEvent(ChatEvent.RequestDelete(message.id, assistantMsg.id))
                                }
                            } else {
                                val userMsg = messages.getOrNull(idx - 1)?.takeIf { it.isUser }
                                if (userMsg != null) {
                                    onEvent(ChatEvent.RequestDelete(userMsg.id, message.id))
                                }
                            }
                        },
                        onCopy = {
            scope.launch { copyTextToClipboard(message.content) }
                            onEvent(ChatEvent.ShowToast("已复制到剪贴板"))
                        },
                        modifier = Modifier.align(
                            if (message.isUser) Alignment.End else Alignment.Start
                        ),
                    )
                    // Branch navigator — "方案 N" style at fork points only
                    val isForkPoint = branches.any { it.forkMessageId == message.id || it.editedMessageId == message.id }
                    if (isForkPoint && branches.size > 1) {
                        // Build ordered list by createdAt: parent first, then children
                        val allBranches = branches.sortedBy { it.createdAt }
                        val currentIdx = allBranches.indexOfFirst { it.id == activeBranchId }.coerceAtLeast(0)
                        val label = " ${currentIdx + 1} / ${allBranches.size} "
                        Row(
                            modifier = Modifier
                                .align(if (message.isUser) Alignment.End else Alignment.Start)
                                .padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    if (currentIdx > 0) {
                                        onEvent(ChatEvent.SwitchBranch(allBranches[currentIdx - 1].id))
                                    }
                                },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(FeatherIcons.ChevronLeft, "上一个方案", modifier = Modifier.size(12.dp),
                                    tint = if (currentIdx > 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                            }
                            Text(label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            IconButton(
                                onClick = {
                                    if (currentIdx < allBranches.size - 1) {
                                        onEvent(ChatEvent.SwitchBranch(allBranches[currentIdx + 1].id))
                                    }
                                },
                                modifier = Modifier.size(20.dp),
                            ) {
                                Icon(FeatherIcons.ChevronRight, "下一个方案", modifier = Modifier.size(12.dp),
                                    tint = if (currentIdx < allBranches.size - 1) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInput(
    onSendMessage: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var text by remember { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        if (isFocused)
            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    )



    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        modifier = modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier.then(
                if (isFocused) Modifier.animatedBorder(
                    borderColors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.tertiary
                    ),
                    shape = RoundedCornerShape(24.dp),
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderWidth = 2.dp
                ) else Modifier
            )
                .wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { event ->
                        if (event.key == Key.Enter) {
                            if (event.isCtrlPressed) {
                                text += "\n"
                                true
                            } else if (text.isNotBlank()) {
                                onSendMessage(text)
                                text = ""
                                true
                            } else {
                                false
                            }
                        } else {
                            false
                        }
                    },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.7f
                    ),
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.7f
                    )
                ),
                maxLines = if (compact) 3 else 10,
                placeholder = {
                    Text(
                        if (compact) "输入消息…" else "Type your message...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )

            if (compact) {
                // 移动端：圆形发送按钮；有文字时变色（enabled 仅控制可点性），
                // 加载中（disabled）用半透明主色，避免看似可点
                val hasText = text.isNotBlank()
                val canSend = enabled && hasText
                val buttonColor = when {
                    canSend -> MaterialTheme.colorScheme.primary
                    hasText -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
                val iconTint = when {
                    canSend -> MaterialTheme.colorScheme.onPrimary
                    hasText -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                }
                Surface(
                    shape = CircleShape,
                    color = buttonColor,
                    modifier = Modifier.padding(end = 6.dp).size(38.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clickable(
                                enabled = canSend,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                onSendMessage(text)
                                text = ""
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            FeatherIcons.Send,
                            contentDescription = "发送",
                            tint = iconTint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else if (text.isNotBlank()) {
                IconButton(
                    onClick = {
                        onSendMessage(text)
                        text = ""
                    },
                    enabled = enabled
                ) {
                    Icon(
                        FeatherIcons.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

}

@Composable
fun rememberMarkdownTypography(
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    typography: Typography = MaterialTheme.typography
): MarkdownTypography = remember(colorScheme, typography) {
    DefaultMarkdownTypography(
        h1 = typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            lineHeight = 28.sp,
            fontSize = 24.sp,
            background = Color.Transparent
        ),
        h2 = typography.bodyLarge.copy(
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface,
            lineHeight = 26.sp,
            fontSize = 22.sp,
            background = Color.Transparent
        ),
        h3 = typography.bodyMedium.copy(
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            lineHeight = 24.sp,
            fontSize = 20.sp,
            background = Color.Transparent
        ),
        h4 = typography.bodySmall.copy(
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            lineHeight = 22.sp,
            fontSize = 18.sp,
            background = Color.Transparent
        ),
        h5 = typography.labelLarge.copy(
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            lineHeight = 20.sp,
            fontSize = 17.sp,
            background = Color.Transparent
        ),
        h6 = typography.labelSmall.copy(
            fontWeight = FontWeight.Medium,
            color = colorScheme.onSurface,
            lineHeight = 20.sp,
            fontSize = 16.sp,
            background = Color.Transparent
        ),
        text = typography.bodySmall.copy(
            color = colorScheme.onSurface,
            lineHeight = 23.sp,
            fontSize = 15.sp,
            background = Color.Transparent
        ),
        code = typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
            fontSize = 14.sp,
            background = colorScheme.surfaceVariant.copy(alpha = 0.1f)
        ),
        inlineCode = typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = colorScheme.primary,
            lineHeight = 20.sp,
            fontSize = 14.sp,
            background = colorScheme.surfaceVariant.copy(alpha = 0.1f)
        ),
        quote = typography.bodyLarge.copy(
            color = colorScheme.onSurfaceVariant,
            fontStyle = FontStyle.Italic,
            lineHeight = 22.sp,
            fontSize = 15.sp,
            background = Color.Transparent
        ),
        paragraph = typography.bodyLarge.copy(
            color = colorScheme.onSurface,
            lineHeight = 22.sp,
            fontSize = 15.sp,
            background = Color.Transparent
        ),
        ordered = typography.bodyLarge.copy(
            color = colorScheme.onSurface,
            lineHeight = 22.sp,
            fontSize = 15.sp,
            background = Color.Transparent
        ),
        bullet = typography.bodyLarge.copy(
            color = colorScheme.onSurface,
            lineHeight = 22.sp,
            fontSize = 15.sp,
            background = Color.Transparent
        ),
        list = typography.bodyLarge.copy(
            color = colorScheme.onSurface,
            lineHeight = 22.sp,
            fontSize = 15.sp,
            background = Color.Transparent
        ),
        link = typography.bodyLarge.copy(
            color = colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            lineHeight = 22.sp,
            fontSize = 15.sp,
            background = Color.Transparent
        )
    )
}

@Composable
fun ButtonBackground(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        content()
    }
}

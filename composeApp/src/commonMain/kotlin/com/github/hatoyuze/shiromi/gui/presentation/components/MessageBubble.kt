// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.shiromi.gui.domain.model.ChatMessageDomainModel
import com.github.hatoyuze.shiromi.gui.domain.model.MessageSegment
import com.github.hatoyuze.shiromi.gui.domain.model.MessageStatus
import com.github.hatoyuze.shiromi.gui.domain.model.TextType
import com.github.hatoyuze.shiromi.gui.domain.model.ToolCallInfo
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.CachingImageTransformer
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.MathAwareParagraph
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.CuteTableAwareTable
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.traverseUnhandledNode
import com.github.hatoyuze.shiromi.gui.presentation.components.coach.CoachFinishedCard
import com.github.hatoyuze.shiromi.gui.presentation.components.coach.ProblemCardComposable
import com.github.hatoyuze.shiromi.gui.presentation.components.icons.AppIcons
import com.github.hatoyuze.shiromi.gui.presentation.markdown.FoldableFlavourDescriptor
import com.github.hatoyuze.shiromi.gui.presentation.rememberMarkdownTypography
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import compose.icons.FeatherIcons
import compose.icons.feathericons.BookOpen
import compose.icons.feathericons.CheckCircle
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.Clipboard
import compose.icons.feathericons.Code
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Filter
import compose.icons.feathericons.List
import compose.icons.feathericons.Search


// ── MessageBubble ──

@Composable
fun MessageBubble(
    message: ChatMessageDomainModel,
    onRetry: () -> Unit,
    editingMessageId: String? = null,
    onSendEdit: ((String, String) -> Unit)? = null,
    onCancelEdit: (() -> Unit)? = null,
    compact: Boolean = false,
    onOpenProblem: ((String) -> Unit)? = null,
) {
    val isEditing = editingMessageId == message.id && message.isUser
    val displayContent = message.content
    val colorScheme = MaterialTheme.colorScheme
    val typography = rememberMarkdownTypography(colorScheme = colorScheme)
    // 移动端用户气泡为实心主色，正文/链接/代码一律用白色系保证对比度。
    // 注：markdownColor 为 @Composable，无法放入 remember 缓存，构建开销可忽略。
    val mdColors = if (compact && message.isUser) {
        com.mikepenz.markdown.m3.markdownColor(
            text = Color.White,
            codeText = Color.White,
            codeBackground = Color.White.copy(alpha = 0.18f),
            inlineCodeText = Color.White,
            inlineCodeBackground = Color.White.copy(alpha = 0.22f),
            linkText = Color(0xFFDCE3FF),
        )
    } else {
        com.mikepenz.markdown.m3.markdownColor(
            text = colorScheme.onSurface,
            codeText = colorScheme.primary,
            codeBackground = colorScheme.surfaceVariant.copy(alpha = 0.3f),
            inlineCodeText = colorScheme.primary,
            inlineCodeBackground = colorScheme.surfaceVariant.copy(alpha = 0.2f),
            linkText = colorScheme.primary,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = when {
                message.isUser && compact -> MaterialTheme.colorScheme.primary
                message.isUser -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else -> MaterialTheme.colorScheme.surfaceBright
            },
            border = if (compact && message.isUser) {
                null
            } else {
                BorderStroke(
                    width = 1.dp,
                    color = if (message.isUser)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
                )
            },
            modifier = Modifier.align(if (message.isUser) Alignment.End else Alignment.Start)
                .fillMaxWidth(if (message.isUser) 0.75f else 0.92f)
        ) {
            Box(
                modifier = Modifier
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    (if (message.isUser)
                                        colorScheme.primary
                                    else
                                        colorScheme.secondary)
                                        .copy(alpha = 0.05f),
                                    Color.Transparent,
                                )
                            )
                        )
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // ── Body: segments (new) or legacy rendering (fallback) ──
                    if (!message.isUser && message.segments != null) {
                        SegmentedBody(
                            segments = message.segments,
                            messageTimestamp = message.timestamp,
                            isStreaming = message.status == MessageStatus.SENDING,
                            thinkingElapsedSec = message.thinkingElapsedSec,
                            mdColors = mdColors,
                            typography = typography,
                            onOpenProblem = onOpenProblem,
                            compact = compact,
                        )
                    } else {
                        // Legacy or user message: render content directly
                        if (!message.isUser && (!message.thinkingContent.isNullOrBlank() || !message.toolCalls.isNullOrEmpty())) {
                            ThinkingSection(
                                content = message.thinkingContent ?: "",
                                toolCalls = message.toolCalls,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                compact = compact,
                            )
                        }
                        // Inline editing mode for user messages
                        if (isEditing) {
                            var editText by remember(message.id) { mutableStateOf(message.content) }
                            val textFieldColors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            )
                            TextField(
                                value = editText,
                                onValueChange = { editText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onKeyEvent { event ->
                                        if (event.key == Key.Enter && !event.isShiftPressed && editText.isNotBlank()) {
                                            onSendEdit?.invoke(message.id, editText)
                                            true
                                        } else if (event.key == Key.Escape) {
                                            onCancelEdit?.invoke()
                                            true
                                        } else false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                colors = textFieldColors,
                                maxLines = 20,
                            )
                        } else if (displayContent.isNotEmpty()) {
                            SelectionContainer {
                                Markdown(
                                    content = displayContent,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = mdColors,
                                    flavour = FoldableFlavourDescriptor(),
                                    imageTransformer = CachingImageTransformer,
                                    components = markdownComponents(
                                        codeBlock = highlightedCodeBlock,
                                        codeFence = highlightedCodeFence,
                                        table = { CuteTableAwareTable(it) },
                                        paragraph = { MathAwareParagraph(it) },
                                        custom = { type, model ->
                                            traverseUnhandledNode(type, model)
                                        },
                                    ),
                                    typography = typography,
                                    dimens = markdownDimens(dividerThickness = 1.dp, codeBackgroundCornerSize = 12.dp, blockQuoteThickness = 4.dp),
                                    padding = markdownPadding(block = 8.dp, list = 12.dp, listItemBottom = 8.dp, indentList = 8.dp,
                                        codeBlock = PaddingValues(16.dp), blockQuote = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                                        blockQuoteText = PaddingValues(vertical = 4.dp),
                                        blockQuoteBar = PaddingValues.Absolute(left = 4.dp, top = 2.dp, right = 4.dp, bottom = 2.dp)),
                                )
                            }
                        }
                    }

                    // Only show status indicators on assistant messages
                    if (!message.isUser) {
                    when (message.status) {
                        MessageStatus.ABORTED -> {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "⊘ 已中止",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                        MessageStatus.SENDING -> {
                            Text(
                                text = "...",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        MessageStatus.ERROR -> {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    AppIcons.CloseIcon,
                                    contentDescription = "发送失败",
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Retry",
                                    modifier = Modifier.clickable { onRetry() },
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }

                        MessageStatus.SENT -> {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    AppIcons.SuccessIcon,
                                    contentDescription = "已发送",
                                    modifier = Modifier.size(11.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                if (message.totalTokens != null) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "${message.totalTokens} tokens",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                // Show finish-reason warnings that survive session reload
                                when (message.finishReason) {
                                    "length" -> {
                                        Spacer(Modifier.width(6.dp))
                                        WarningBadge("截断")
                                    }
                                    "content_filter" -> {
                                        Spacer(Modifier.width(6.dp))
                                        WarningBadge("过滤")
                                    }
                                    "insufficient_system_resource" -> {
                                        Spacer(Modifier.width(6.dp))
                                        WarningBadge("资源不足")
                                    }
                                }
                            }
                        }
                    }
                    } // end if (!message.isUser)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Segmented body — timeline for thinking/tools, markdown for content
// ═══════════════════════════════════════════════════════════

@Composable
private fun SegmentedBody(
    segments: List<MessageSegment>,
    messageTimestamp: Long,
    isStreaming: Boolean,
    thinkingElapsedSec: Int? = null,
    mdColors: com.mikepenz.markdown.model.MarkdownColors,
    typography: com.mikepenz.markdown.model.MarkdownTypography,
    onOpenProblem: ((String) -> Unit)? = null,
    compact: Boolean = false,
) {
    // Split into thinking/tool timeline items, cards (problem / coach finished), and content text
    val timelineItems = mutableListOf<MessageSegment>()
    val problemCards = mutableListOf<MessageSegment.ProblemCard>()
    val coachFinishedCards = mutableListOf<MessageSegment.CoachFinished>()
    val contentBuf = StringBuilder()

    for (seg in segments) {
        when (seg) {
            is MessageSegment.Text -> {
                when (seg.type) {
                    TextType.THINKING -> timelineItems.add(seg)
                    TextType.CONTENT -> contentBuf.append(seg.text)
                }
            }
            is MessageSegment.ToolCall -> {
                if (!ToolCallDescriber.isHidden(seg.info.name)) {
                    timelineItems.add(seg)
                }
            }
            is MessageSegment.ProblemCard -> {
                problemCards.add(seg)
            }
            is MessageSegment.CoachFinished -> {
                coachFinishedCards.add(seg)
            }
            is MessageSegment.AskUser -> { /* rendered as AskUserCard in ChatScreen */ }
        }
    }

    // Render timeline if there are thinking/tool items
    if (timelineItems.isNotEmpty()) {
        ThinkingTimeline(
            items = timelineItems,
            startTimeMs = messageTimestamp,
            isStreaming = isStreaming,
            thinkingElapsedSec = thinkingElapsedSec,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            compact = compact,
        )
    }

    // Render problem cards between timeline and content
    problemCards.forEach { card ->
        Spacer(Modifier.height(6.dp))
        ProblemCardComposable(card)
    }

    // Render coach finished summary cards (difficulty summary + recommend pills + farewell)
    coachFinishedCards.forEach { card ->
        Spacer(Modifier.height(6.dp))
        CoachFinishedCard(card = card, onOpenProblem = onOpenProblem, compact = compact)
    }

    // Render content as markdown
    val text = contentBuf.toString()
    if (text.isNotBlank()) {
        if (problemCards.isNotEmpty() || coachFinishedCards.isNotEmpty()) Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Markdown(
                content = text,
                modifier = Modifier.fillMaxWidth(),
                colors = mdColors,
                flavour = FoldableFlavourDescriptor(),
                imageTransformer = CachingImageTransformer,
                components = markdownComponents(
                    codeBlock = highlightedCodeBlock,
                    codeFence = highlightedCodeFence,
                    table = { CuteTableAwareTable(it) },
                    paragraph = { MathAwareParagraph(it) },
                    custom = { type, model ->
                        traverseUnhandledNode(type, model)
                    },
                ),
                typography = typography,
                dimens = markdownDimens(dividerThickness = 1.dp, codeBackgroundCornerSize = 12.dp, blockQuoteThickness = 4.dp),
                padding = markdownPadding(block = 8.dp, list = 12.dp, listItemBottom = 8.dp, indentList = 8.dp,
                    codeBlock = PaddingValues(16.dp), blockQuote = PaddingValues(horizontal = 24.dp, vertical = 4.dp),
                    blockQuoteText = PaddingValues(vertical = 4.dp),
                    blockQuoteBar = PaddingValues.Absolute(left = 4.dp, top = 2.dp, right = 4.dp, bottom = 2.dp)),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// ThinkingTimeline — collapsible box with timer + vertical timeline
// ═══════════════════════════════════════════════════════════

@Composable
private fun ThinkingTimeline(
    items: List<MessageSegment>,
    startTimeMs: Long,
    isStreaming: Boolean,
    thinkingElapsedSec: Int? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = colorScheme.secondary
    var expanded by remember { mutableStateOf(isStreaming) }

    val elapsedSec = thinkingElapsedSec
        ?: ((com.github.hatoyuze.shiromi.gui.platform.currentTimeMillis() - startTimeMs) / 1000).toInt()
    val headerText = if (isStreaming) "正在思考..." else "已思考（用时 ${elapsedSec.coerceAtLeast(0)} 秒）"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // ── Header ──
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(FeatherIcons.Cpu, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(headerText, style = MaterialTheme.typography.labelLarge.copy(color = accentColor), modifier = Modifier.weight(1f))
                    Icon(
                        FeatherIcons.ChevronDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = accentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp).rotate(animateFloatAsState(if (expanded) 180f else 0f, label = "chevron").value),
                    )
                }
            }

            // ── Timeline body ──
            // 移动端（compact）：流式时不用 AnimatedVisibility 尺寸动画（避免逐 token
            // 高度抖动），展开体限高 + 内部滚动 + 贴底跟随；桌面保持原展开动画。
            if (compact) {
                if (expanded) {
                    ThinkingTimelineBody(
                        items = items,
                        accentColor = accentColor,
                        isStreaming = isStreaming,
                    )
                }
            } else {
                AnimatedVisibility(visible = expanded) {
                    ThinkingTimelineBody(
                        items = items,
                        accentColor = accentColor,
                        isStreaming = isStreaming,
                    )
                }
            }
        }
    }
}

/**
 * 时间线展开体（移动端限高内滚版）。
 *
 * - 高度上限 ≈ 视口 40%（[LocalWindowInfo] 换算，夹在 160..480dp），超长内容内部滚动，
 *   滚动到边缘时经 nested scroll 交还外层 LazyColumn，解决「展开后无法下滑」；
 * - 流式时若用户停留在内滚底部则自动贴底跟随最新思考，上翻阅读则暂停跟随。
 */
/** 时间线内滚跟随容差（px）：「上翻」= 值显著减小超过该值。 */
private const val TIMELINE_FOLLOW_TOLERANCE_PX = 24f

@Composable
private fun ThinkingTimelineBody(
    items: List<MessageSegment>,
    accentColor: Color,
    isStreaming: Boolean,
) {
    val innerScroll = rememberScrollState()
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val maxBodyHeight = remember(windowInfo.containerSize.height, density) {
        with(density) { (windowInfo.containerSize.height * 0.4f).toDp() }.coerceIn(160.dp, 480.dp)
    }

    // 内滚跟随（与主列表同一模式）：观察器只更新 follow 标志（不滚动），
    // 滚动由内容增长（maxValue 变化）触发；用户上翻阅读即暂停，回到底部自动恢复。
    // 「上翻」= 值显著减小（>容差）；「回到底部」= 值回到 max-容差 内。
    val follow = remember { mutableStateOf(true) }
    var lastValue by remember { mutableStateOf(0) }
    LaunchedEffect(innerScroll, isStreaming) {
        if (!isStreaming) return@LaunchedEffect
        snapshotFlow { innerScroll.value to innerScroll.maxValue }
            .collect { (value, max) ->
                if (value < lastValue - TIMELINE_FOLLOW_TOLERANCE_PX) follow.value = false
                else if (value >= max - TIMELINE_FOLLOW_TOLERANCE_PX) follow.value = true
                lastValue = value
            }
    }
    // 内容增长 → 贴底则贴尾（滚动只在内容增长时发生，用户滚动不触发；
    // isScrollInProgress 守卫避免与用户拖动抢滚动）
    LaunchedEffect(innerScroll.maxValue, isStreaming) {
        if (isStreaming && follow.value && !innerScroll.isScrollInProgress && innerScroll.maxValue > 0) {
            innerScroll.scrollTo(innerScroll.maxValue)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 6.dp, bottom = 12.dp, end = 12.dp)
            .drawBehind {
                // Continuous vertical line — node column is 16dp wide,
                // line passes through its center at 8dp from this Box's origin.
                val lineX = 8.dp.toPx()
                drawLine(
                    color = accentColor.copy(alpha = 0.2f),
                    start = Offset(lineX, 0f),
                    end = Offset(lineX, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            .drawBehind {
                // Subtle gradient fade at the bottom of the timeline
                val fadeStart = (size.height - 24.dp.toPx()).coerceAtLeast(0f)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, accentColor.copy(alpha = 0.04f)),
                        startY = fadeStart,
                        endY = size.height,
                    )
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxBodyHeight)
                .verticalScroll(innerScroll),
        ) {
            items.forEachIndexed { idx, item ->
                TimelineRow(
                    item = item,
                    accentColor = accentColor,
                    isFirst = idx == 0,
                )
            }
        }
    }
}

// ── Single timeline row: node (dot/icon) + content ──

@Composable
private fun TimelineRow(
    item: MessageSegment,
    accentColor: Color,
    isFirst: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (isFirst) 0.dp else 4.dp),
    ) {
        // ── Node column: fixed 16dp wide, content centered on the line ──
        Box(
            modifier = Modifier.width(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (item) {
                is MessageSegment.Text -> {
                    // Ring-style dot: 8dp circle, 1.5dp border, subtle fill
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .border(1.5.dp, accentColor.copy(alpha = 0.4f), CircleShape)
                            .background(accentColor.copy(alpha = 0.08f), CircleShape),
                    )
                }
                is MessageSegment.ToolCall -> {
                    Icon(
                        imageVector = iconForTool(item.info.name),
                        contentDescription = null,
                        tint = colorScheme.tertiary.copy(alpha = 0.75f),
                        modifier = Modifier.size(14.dp),
                    )
                }
                is MessageSegment.ProblemCard -> { /* rendered outside timeline */ }
                is MessageSegment.CoachFinished -> { /* rendered outside timeline */ }
                is MessageSegment.AskUser -> { /* rendered as AskUserCard in ChatScreen */ }
            }
        }

        Spacer(Modifier.width(10.dp))

        // ── Content ──
        when (item) {
            is MessageSegment.Text -> {
                // Plain text — thinking content is not markdown
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    ),
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f).padding(vertical = 1.dp),
                )
            }
            is MessageSegment.ToolCall -> {
                ToolCallTimelineNode(
                    toolCall = item.info,
                    modifier = Modifier.weight(1f),
                )
            }
            is MessageSegment.ProblemCard -> { /* rendered outside timeline */ }
            is MessageSegment.CoachFinished -> { /* rendered outside timeline */ }
            is MessageSegment.AskUser -> { /* rendered as AskUserCard in ChatScreen */ }
        }
    }
}

// ── Tool call node in timeline ──

@Composable
private fun ToolCallTimelineNode(
    toolCall: ToolCallInfo,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    var showResult by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.clickable { showResult = !showResult }.padding(vertical = 1.dp),
    ) {
        ToolCallDescriber.Describe(toolCall)

        if (showResult) {
            Text(
                text = toolCall.result ?: "(no result)",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

// ── Tool call → FeatherIcon mapping ──

private fun iconForTool(functionName: String) = when {
    "search" in functionName -> FeatherIcons.Search
    "problem" in functionName -> FeatherIcons.FileText
    "solutions" in functionName -> FeatherIcons.BookOpen
    "training" in functionName -> FeatherIcons.List
    "record" in functionName -> FeatherIcons.Clipboard
    "practice" in functionName -> FeatherIcons.CheckCircle
    "filters" in functionName -> FeatherIcons.Filter
    else -> FeatherIcons.Code
}
/** 消息状态警示徽标：警示图标 + 文案（截断/过滤/资源不足的图标化）。 */
@Composable
private fun WarningBadge(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            AppIcons.WarningIcon,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
        )
    }
}

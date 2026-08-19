// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import com.github.hatoyuze.luogu.gui.platform.copyTextToClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.presentation.components.markdown.CachingImageTransformer
import com.github.hatoyuze.luogu.gui.presentation.components.markdown.MathAwareParagraph
import com.github.hatoyuze.luogu.gui.presentation.markdown.FoldableFlavourDescriptor
import com.github.hatoyuze.luogu.gui.domain.chat.ChatService
import com.github.hatoyuze.luogu.skill.api.DifficultyLevel
import com.github.hatoyuze.luogu.skill.api.LuoguTags
import com.github.hatoyuze.luogu.skill.api.ProblemDetail
import com.github.hatoyuze.luogu.skill.api.ProblemDetailData
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.DefaultMarkdownTypography
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.ChevronLeft
import compose.icons.feathericons.Copy
import compose.icons.feathericons.Send
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import com.github.hatoyuze.luogu.gui.presentation.utils.toFixed

// ═══════════════════════════════════════════════════════════
// ProblemDetailPage — Flat-design problem detail overlay
// ═══════════════════════════════════════════════════════════

@Composable
fun ProblemDetailPage(
    pid: String,
    prefetchedData: ProblemDetailData? = null,
    onBack: () -> Unit,
    onCoachWithProblem: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val chatService = koinInject<ChatService>()
    val scope = rememberCoroutineScope()

    var problemData by remember { mutableStateOf(prefetchedData) }
    var isLoading by remember { mutableStateOf(prefetchedData == null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pid) {
        if (prefetchedData != null) return@LaunchedEffect
        isLoading = true
        try {
            problemData = chatService.getProblemDetail(pid)
            if (problemData == null) error = "获取题目 $pid 失败"
        } catch (e: Exception) {
            error = "获取题目 $pid 失败: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Surface(
        modifier = modifier,
        // 移动端（compact）：全屏页，无圆角卡片边框；桌面保持卡片样式。
        shape = if (compact) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (compact) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shadowElevation = 0.dp,
    ) {
        when {
            isLoading -> LoadingContent()
            error != null -> ErrorContent(error!!, onBack)
            problemData != null -> ProblemDetailContent(
                detail = problemData!!.problem,
                onBack = onBack,
                onCoachWithProblem = onCoachWithProblem,
                compact = compact,
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("加载题目详情…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorContent(error: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) {
                Icon(FeatherIcons.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("返回")
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

// ── Full content ──

@Composable
private fun ProblemDetailContent(
    detail: ProblemDetail,
    onBack: () -> Unit,
    onCoachWithProblem: ((String) -> Unit)?,
    compact: Boolean,
) {
    if (compact) {
        ProblemDetailContentCompact(detail, onBack, onCoachWithProblem)
        return
    }
    // Extract fields for smart-casting
    val inputFormat = detail.inputFormat
    val outputFormat = detail.outputFormat
    val samples = detail.samples
    val hint = detail.hint

    Column(Modifier.fillMaxSize()) {
        // ═══ TopBar ═══
        ProblemDetailTopBar(
            title = "${detail.pid} ${detail.name}",
            totalSubmit = detail.totalSubmit,
            totalAccepted = detail.totalAccepted,
            timeLimit = detail.timeLimit,
            memoryLimit = detail.memoryLimit,
            onBack = onBack,
            onCoachWithProblem = onCoachWithProblem?.let { { it(detail.pid) } },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

        // ═══ Body: Markdown + Sidebar ═══
        Row(Modifier.fillMaxSize().weight(1f)) {
            // Left: Markdown content
            Column(
                Modifier
                    .weight(0.72f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            ) {
                if (detail.description.isNotBlank()) {
                    MarkdownSection("题目描述", detail.description)
                    Spacer(Modifier.height(16.dp))
                }
                if (!inputFormat.isNullOrBlank()) {
                    MarkdownSection("输入格式", inputFormat)
                    Spacer(Modifier.height(16.dp))
                }
                if (!outputFormat.isNullOrBlank()) {
                    MarkdownSection("输出格式", outputFormat)
                    Spacer(Modifier.height(16.dp))
                }
                if (!samples.isNullOrEmpty()) {
                    Text(
                        text = "输入输出样例",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    samples.forEachIndexed { idx, sample ->
                        SampleBlockWithCopy(idx + 1, sample.input, sample.output)
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (!hint.isNullOrBlank()) {
                    MarkdownSection("说明/提示", hint)
                }
            }

            // Right: Metadata sidebar
            ProblemMetaSidebar(
                detail = detail,
                modifier = Modifier.weight(0.28f).fillMaxHeight(),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Compact content — 移动端（窄屏）专用布局
// ═══════════════════════════════════════════════════════════

@Composable
private fun ProblemDetailContentCompact(
    detail: ProblemDetail,
    onBack: () -> Unit,
    onCoachWithProblem: ((String) -> Unit)?,
) {
    val inputFormat = detail.inputFormat
    val outputFormat = detail.outputFormat
    val samples = detail.samples
    val hint = detail.hint

    Column(Modifier.fillMaxSize()) {
        // ── 顶栏：返回 + 标题(2行) + 教练模式 pill ──
        ProblemDetailTopBarCompact(
            title = "${detail.pid} ${detail.name}",
            onBack = onBack,
            onCoachWithProblem = onCoachWithProblem?.let { { it(detail.pid) } },
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

        // ── 统计条：提交/通过/时间/内存 四列 ──
        ProblemStatsStrip(detail)

        // ── 正文滚动区 ──
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // 题目信息（难度/标签/pid/历史分数）折叠卡
            ProblemInfoCard(detail)
            Spacer(Modifier.height(12.dp))

            if (detail.description.isNotBlank()) {
                MarkdownSection("题目描述", detail.description)
                Spacer(Modifier.height(12.dp))
            }
            if (!inputFormat.isNullOrBlank()) {
                MarkdownSection("输入格式", inputFormat)
                Spacer(Modifier.height(12.dp))
            }
            if (!outputFormat.isNullOrBlank()) {
                MarkdownSection("输出格式", outputFormat)
                Spacer(Modifier.height(12.dp))
            }
            if (!samples.isNullOrEmpty()) {
                Text(
                    text = "输入输出样例",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                samples.forEachIndexed { idx, sample ->
                    SampleBlockWithCopy(idx + 1, sample.input, sample.output, stacked = true)
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
            if (!hint.isNullOrBlank()) {
                MarkdownSection("说明/提示", hint)
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── 底部常驻操作栏（教练模式）──
        if (onCoachWithProblem != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = { onCoachWithProblem(detail.pid) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(FeatherIcons.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("用教练模式讲解这道题")
                }
            }
        }
    }
}

/** 移动端顶栏：返回 + 标题（最多两行）+ 教练模式 pill。 */
@Composable
private fun ProblemDetailTopBarCompact(
    title: String,
    onBack: () -> Unit,
    onCoachWithProblem: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(FeatherIcons.ChevronLeft, contentDescription = "返回")
        }
        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (onCoachWithProblem != null) {
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onCoachWithProblem),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(FeatherIcons.Send, contentDescription = null, modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("教练模式", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** 移动端统计条：提交/通过/时间限制/内存限制 四列。 */
@Composable
private fun ProblemStatsStrip(detail: ProblemDetail) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            CompactStatCell("提交", formatCount2(detail.totalSubmit), Modifier.weight(1f))
            CompactStatCell("通过", formatCount2(detail.totalAccepted), Modifier.weight(1f))
            CompactStatCell("时间限制", detail.timeLimit?.let { formatTimeLimitInline(it) } ?: "—", Modifier.weight(1f))
            CompactStatCell("内存限制", detail.memoryLimit?.let { formatMemoryLimitInline(it) } ?: "—", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactStatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 移动端「题目信息」折叠卡：难度 / 题目编号 / 历史分数 / 标签 chips。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProblemInfoCard(detail: ProblemDetail) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                Text("题目信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                Icon(
                    FeatherIcons.ChevronDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                Spacer(Modifier.height(10.dp))
                MetaRowCompact("题目编号") { Text(detail.pid, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
                Spacer(Modifier.height(10.dp))
                MetaRowCompact("难度") { DifficultyBadgeInline(detail.difficulty) }
                Spacer(Modifier.height(10.dp))
                MetaRowCompact("历史分数") { Text("暂无", fontSize = 13.sp) }
                if (detail.tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    MetaRowCompact("标签") {
                        val tags = remember(detail.tags) { LuoguTags.resolveTags(detail.tags) }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            tags.forEach { tag ->
                                Surface(shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)) {
                                    Text(tag.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRowCompact(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(58.dp))
        content()
    }
}

// ═══════════════════════════════════════════════════════════
// TopBar
// ═══════════════════════════════════════════════════════════

@Composable
private fun ProblemDetailTopBar(
    title: String,
    totalSubmit: Int,
    totalAccepted: Int,
    timeLimit: Int?,
    memoryLimit: Int?,
    onBack: () -> Unit,
    onCoachWithProblem: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Icon(FeatherIcons.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("返回")
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        StatChip("提交", formatCount2(totalSubmit))
        StatChip("通过", formatCount2(totalAccepted))
        StatChip("时间限制", if (timeLimit != null) formatTimeLimitInline(timeLimit) else "—")
        StatChip("内存限制", if (memoryLimit != null) formatMemoryLimitInline(memoryLimit) else "—")
        if (onCoachWithProblem != null) {
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onCoachWithProblem) {
                Icon(FeatherIcons.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("教练模式", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.width(4.dp))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
    Spacer(Modifier.width(16.dp))
}

// ═══════════════════════════════════════════════════════════
// Metadata Sidebar
// ═══════════════════════════════════════════════════════════

@Composable
private fun ProblemMetaSidebar(
    detail: ProblemDetail,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            MetaRowLabel("题目编号") { Text(detail.pid, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface) }
            Spacer(Modifier.height(12.dp))
            MetaRowLabel("难度") { DifficultyBadgeInline(detail.difficulty) }
            Spacer(Modifier.height(12.dp))
            MetaRowLabel("历史分数") { Text("暂无", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            CollapsibleTagsSection(detail.tags)
        }
    }
}

@Composable
private fun MetaRowLabel(label: String, content: @Composable () -> Unit) {
    Column {
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        Spacer(Modifier.height(2.dp))
        content()
    }
}

@Composable
private fun CollapsibleTagsSection(tagIds: List<Int>) {
    var tagsExpanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .clickable(indication = null, interactionSource = interactionSource) { tagsExpanded = !tagsExpanded },
    ) {
        Text("标签", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Icon(FeatherIcons.ChevronDown, contentDescription = if (tagsExpanded) "收起标签" else "展开标签",
            modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
    if (tagsExpanded) {
        Spacer(Modifier.height(8.dp))
        val tags = remember(tagIds) { LuoguTags.resolveTags(tagIds) }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tags.forEach { tag ->
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)) {
                    Text(tag.name, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Markdown section (proper rendering)
// ═══════════════════════════════════════════════════════════

@Composable
private fun MarkdownSection(title: String, content: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    val typography = rememberCompactDetailTypography()
    SelectionContainer {
        Markdown(
            content = content,
            modifier = Modifier.fillMaxWidth(),
            flavour = FoldableFlavourDescriptor(),
            imageTransformer = CachingImageTransformer,
            components = markdownComponents(
                codeBlock = highlightedCodeBlock,
                codeFence = highlightedCodeFence,
                paragraph = { MathAwareParagraph(it) },
            ),
            dimens = markdownDimens(dividerThickness = 1.dp, codeBackgroundCornerSize = 8.dp),
            padding = markdownPadding(block = 4.dp, list = 8.dp, listItemBottom = 4.dp, codeBlock = PaddingValues(12.dp)),
            typography = typography,
        )
    }
}

@Composable
private fun rememberCompactDetailTypography(): com.mikepenz.markdown.model.MarkdownTypography {
    val t = MaterialTheme.typography
    val cs = MaterialTheme.colorScheme
    return remember {
        DefaultMarkdownTypography(
        h1 = t.bodyLarge.copy(fontWeight = FontWeight.Bold, color = cs.onSurface, fontSize = 18.sp, lineHeight = 24.sp, background = Color.Transparent),
        h2 = t.bodyMedium.copy(fontWeight = FontWeight.Bold, color = cs.onSurface, fontSize = 17.sp, lineHeight = 23.sp, background = Color.Transparent),
        h3 = t.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = cs.onSurface, fontSize = 16.sp, lineHeight = 22.sp, background = Color.Transparent),
        h4 = t.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = cs.onSurface, fontSize = 15.sp, lineHeight = 21.sp, background = Color.Transparent),
        h5 = t.bodySmall.copy(fontWeight = FontWeight.Medium, color = cs.onSurface, fontSize = 15.sp, lineHeight = 20.sp, background = Color.Transparent),
        h6 = t.bodySmall.copy(fontWeight = FontWeight.Medium, color = cs.onSurface, fontSize = 15.sp, lineHeight = 20.sp, background = Color.Transparent),
        text = t.bodySmall.copy(color = cs.onSurface, fontSize = 15.sp, lineHeight = 23.sp, background = Color.Transparent),
        code = t.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = cs.onSurfaceVariant, fontSize = 14.sp, lineHeight = 20.sp, background = cs.surfaceVariant.copy(alpha = 0.1f)),
        inlineCode = t.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = cs.primary, fontSize = 14.sp, lineHeight = 20.sp, background = cs.surfaceVariant.copy(alpha = 0.1f)),
        quote = t.bodyLarge.copy(color = cs.onSurfaceVariant, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 22.sp, background = Color.Transparent),
        paragraph = t.bodyLarge.copy(color = cs.onSurface, fontSize = 15.sp, lineHeight = 22.sp, background = Color.Transparent),
        ordered = t.bodyLarge.copy(color = cs.onSurface, fontSize = 15.sp, lineHeight = 22.sp, background = Color.Transparent),
        bullet = t.bodyLarge.copy(color = cs.onSurface, fontSize = 15.sp, lineHeight = 22.sp, background = Color.Transparent),
        list = t.bodyLarge.copy(color = cs.onSurface, fontSize = 15.sp, lineHeight = 22.sp, background = Color.Transparent),
        link = t.bodyLarge.copy(color = cs.primary, fontSize = 15.sp, lineHeight = 22.sp, textDecoration = TextDecoration.Underline, background = Color.Transparent),
    )
    }
}

// ═══════════════════════════════════════════════════════════
// Sample block with copy buttons
// ═══════════════════════════════════════════════════════════

@Composable
private fun SampleBlockWithCopy(
    index: Int,
    input: String,
    output: String,
    stacked: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (stacked) {
            // 移动端：输入/输出上下堆叠
            Column(modifier = Modifier.padding(10.dp)) {
                SampleBlock("输入 #$index", input)
                Spacer(Modifier.height(10.dp))
                SampleBlock("输出 #$index", output)
            }
        } else {
            // 桌面：输入/输出并排
            Row(modifier = Modifier.padding(10.dp)) {
                SampleBlock("输入 #$index", input, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                SampleBlock("输出 #$index", output, Modifier.weight(1f))
            }
        }
    }
}

/** 单个样例块（标签 + 复制 + 等宽内容，长行可横向滚动）。 */
@Composable
private fun SampleBlock(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { scope.launch { copyTextToClipboard(text) } }, modifier = Modifier.size(20.dp)) {
                Icon(FeatherIcons.Copy, contentDescription = "复制$label", modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            SelectionContainer {
                // 长行可横向滚动（样例多为窄屏，避免被裁切）
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    Text(text, modifier = Modifier.padding(8.dp).fillMaxWidth(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Helpers
// ═══════════════════════════════════════════════════════════

@Composable
private fun DifficultyBadgeInline(levelId: Int) {
    val level = DifficultyLevel.fromId(levelId)
    Surface(shape = RoundedCornerShape(4.dp), color = parseHexColorInline(level.color)) {
        Text(level.label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

/** Format count with 2 decimal places for values >= 1000 */
private fun formatCount2(n: Int): String = when {
    n >= 10000 -> (n / 1000.0).toFixed(2) + "k"
    n >= 1000 -> (n / 1000.0).toFixed(2) + "k"
    else -> n.toString()
}

private fun formatTimeLimitInline(ms: Int): String =
    if (ms >= 1000) (ms / 1000.0).toFixed(2) + "s" else "${ms}ms"

private fun formatMemoryLimitInline(kb: Int): String {
    return if (kb >= 1024) {
        val mb = kb / 1024.0
        if (mb == mb.toInt().toDouble()) "${mb.toInt()}MB" else mb.toFixed(2) + "MB"
    } else "${kb}KB"
}

private fun parseHexColorInline(hex: String): Color {
    val colorStr = hex.removePrefix("#")
    val rgb = colorStr.toLong(16)
    return Color(red = ((rgb shr 16) and 0xFF) / 255f, green = ((rgb shr 8) and 0xFF) / 255f, blue = (rgb and 0xFF) / 255f)
}

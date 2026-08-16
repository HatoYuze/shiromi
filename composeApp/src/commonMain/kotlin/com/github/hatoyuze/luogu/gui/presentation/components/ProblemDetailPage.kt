package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shadowElevation = 0.dp,
    ) {
        when {
            isLoading -> LoadingContent()
            error != null -> ErrorContent(error!!, onBack)
            problemData != null -> ProblemDetailContent(
                detail = problemData!!.problem,
                onBack = onBack,
                onCoachWithProblem = onCoachWithProblem,
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
) {
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
private fun SampleBlockWithCopy(index: Int, input: String, output: String) {
    val scope = rememberCoroutineScope()
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("输入 #$index", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    IconButton(onClick = { scope.launch { copyTextToClipboard(input) } }, modifier = Modifier.size(20.dp)) {
                        Icon(FeatherIcons.Copy, contentDescription = "复制输入", modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(input, modifier = Modifier.padding(8.dp).fillMaxWidth(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("输出 #$index", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    IconButton(onClick = { scope.launch { copyTextToClipboard(output) } }, modifier = Modifier.size(20.dp)) {
                        Icon(FeatherIcons.Copy, contentDescription = "复制输出", modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                    SelectionContainer {
                        Text(output, modifier = Modifier.padding(8.dp).fillMaxWidth(), fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
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

package com.github.hatoyuze.luogu.gui.presentation.components.coach

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.platform.copyTextToClipboard
import kotlinx.coroutines.launch
import com.github.hatoyuze.luogu.gui.domain.model.MessageSegment
import com.github.hatoyuze.luogu.gui.presentation.components.markdown.CachingImageTransformer
import com.github.hatoyuze.luogu.gui.presentation.components.markdown.MathAwareParagraph
import com.github.hatoyuze.luogu.gui.presentation.components.icons.AppIcons
import com.github.hatoyuze.luogu.gui.presentation.markdown.FoldableFlavourDescriptor
import compose.icons.FeatherIcons
import compose.icons.feathericons.Copy
import compose.icons.feathericons.*
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
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import com.github.hatoyuze.luogu.gui.presentation.utils.toFixed

// ═══════════════════════════════════════════════════════════
// ProblemCard — rich problem detail card in chat
// ═══════════════════════════════════════════════════════════

@Composable
fun ProblemCardComposable(
    card: MessageSegment.ProblemCard,
    onRefresh: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    when {
        card.error != null -> ProblemCardError(card.pid, card.error, modifier)
        card.data != null -> ProblemCardContent(card.data.problem, onRefresh, modifier)
        else -> ProblemCardLoading(card.pid, card.coachContent, modifier)
    }
}

// ── Loading state ──

@Composable
private fun ProblemCardLoading(
    pid: String,
    coachContent: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "加载题目 $pid …",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!coachContent.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = coachContent,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ── Error state ──

@Composable
private fun ProblemCardError(pid: String, error: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                AppIcons.WarningIcon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$pid: $error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

// ── Rich content card ──

@Composable
private fun ProblemCardContent(
    detail: ProblemDetail,
    onRefresh: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Assign to local vals to enable smart casts (these are custom-getter
    // or cross-module properties that Kotlin cannot smart-cast directly).
    val provider = detail.provider
    val background = detail.background
    val inputFormat = detail.inputFormat
    val outputFormat = detail.outputFormat
    val samples = detail.samples
    val hint = detail.hint
    val timeLimit = detail.timeLimit
    val memoryLimit = detail.memoryLimit

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
    ) {
        Column {
            // ── Header (always visible, clickable to toggle) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Title + subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${detail.pid} ${detail.name}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DifficultyBadge(detail.difficulty)
                        if (provider != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "· ${provider.name}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Refresh button
                if (onRefresh != null) {
                    IconButton(
                        onClick = { onRefresh(detail.pid) },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            FeatherIcons.RefreshCw,
                            contentDescription = "刷新题目缓存",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                }
                // Expand/collapse indicator
                Icon(
                    AppIcons.SortingIcon,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Stats bar (always visible) ──
            ProblemStatsBar(detail, timeLimit, memoryLimit)

            // ── Expandable content ──
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 10.dp))

                    // Tags
                    if (detail.tags.isNotEmpty()) {
                        ProblemTagsRow(detail.tags)
                        Spacer(Modifier.height(10.dp))
                    }

                    // Background
                    if (!background.isNullOrBlank()) {
                        ProblemSection("题目背景", background)
                        Spacer(Modifier.height(8.dp))
                    }

                    // Description
                    if (detail.description.isNotBlank()) {
                        ProblemSection("题目描述", detail.description)
                        Spacer(Modifier.height(8.dp))
                    }

                    // Input format
                    if (!inputFormat.isNullOrBlank()) {
                        ProblemSection("输入格式", inputFormat)
                        Spacer(Modifier.height(8.dp))
                    }

                    // Output format
                    if (!outputFormat.isNullOrBlank()) {
                        ProblemSection("输出格式", outputFormat)
                        Spacer(Modifier.height(8.dp))
                    }

                    // Samples
                    if (!samples.isNullOrEmpty()) {
                        Text(
                            text = "输入输出样例",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        samples.forEachIndexed { idx, sample ->
                            SampleBlock(idx + 1, sample.input, sample.output)
                            Spacer(Modifier.height(6.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // Hint
                    if (!hint.isNullOrBlank()) {
                        ProblemSection("说明/提示", hint)
                    }
                }
            }
        }
    }
}

// ── Compact stats bar (always visible, below title) ──

@Composable
private fun ProblemStatsBar(
    detail: ProblemDetail,
    timeLimit: Int?,
    memoryLimit: Int?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactStat("提交", formatCount(detail.totalSubmit))
        CompactStat("通过", formatCount(detail.totalAccepted))
        if (timeLimit != null) {
            CompactStat("时间限制", formatTimeLimit(timeLimit))
        }
        if (memoryLimit != null) {
            CompactStat("内存限制", formatMemoryLimit(memoryLimit))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp))
}

@Composable
private fun CompactStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Tags row ──

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProblemTagsRow(tagIds: List<Int>) {
    val tags = remember(tagIds) { LuoguTags.resolveTags(tagIds) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            ) {
                Text(
                    text = tag.name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

// ── Markdown section ──

@Composable
private fun ProblemSection(title: String, content: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    SelectionContainer {
        val typography = rememberCompactMarkdownTypography()
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
            dimens = markdownDimens(
                dividerThickness = 1.dp,
                codeBackgroundCornerSize = 8.dp,
            ),
            padding = markdownPadding(
                block = 4.dp,
                list = 8.dp,
                listItemBottom = 4.dp,
                codeBlock = PaddingValues(12.dp),
            ),
            typography = typography,
        )
    }
}

// ── Compact markdown typography (headings shifted down 3 levels) ──

@Composable
private fun rememberCompactMarkdownTypography(): com.mikepenz.markdown.model.MarkdownTypography {
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

// ── Sample block (side-by-side input | output) ──

@Composable
private fun SampleBlock(index: Int, input: String, output: String) {
    val scope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            // Input column (left)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "输入 #$index",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
        onClick = { scope.launch { copyTextToClipboard(input) } },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Copy,
                            contentDescription = "复制输入",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    SelectionContainer {
                        Text(
                            text = input,
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            // Output column (right)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "输出 #$index",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(
        onClick = { scope.launch { copyTextToClipboard(output) } },
                        modifier = Modifier.size(20.dp),
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Copy,
                            contentDescription = "复制输出",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    SelectionContainer {
                        Text(
                            text = output,
                            modifier = Modifier.padding(8.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

// ── Difficulty badge (reuses DifficultyLevel from ToolCallDescriber pattern) ──

@Composable
private fun DifficultyBadge(levelId: Int) {
    val level = DifficultyLevel.fromId(levelId)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = parseHexColor(level.color),
    ) {
        Text(
            text = level.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ── Formatting helpers ──

private fun formatCount(n: Int): String = when {
    n >= 10000 -> "${n / 1000}k"
    n >= 1000 -> (n / 1000.0).toFixed(1) + "k"
    else -> n.toString()
}

private fun formatTimeLimit(ms: Int): String {
    // Luogu provides time limit in ms; max value for typical limits
    return if (ms >= 1000) (ms / 1000.0).toFixed(2) + "s" else "${ms}ms"
}

private fun formatMemoryLimit(kb: Int): String {
    return if (kb >= 1024) {
        val mb = kb / 1024.0
        if (mb == mb.toInt().toDouble()) "${mb.toInt()}MB"
        else mb.toFixed(1) + "MB"
    } else {
        "${kb}KB"
    }
}

private fun parseHexColor(hex: String): Color {
    val colorStr = hex.removePrefix("#")
    val rgb = colorStr.toLong(16)
    return Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
    )
}

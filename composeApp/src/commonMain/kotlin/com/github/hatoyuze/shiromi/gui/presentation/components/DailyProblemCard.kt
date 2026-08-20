// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.shiromi.gui.data.remote.DailyProblemAgent
import com.github.hatoyuze.shiromi.gui.domain.model.DailyProblemResult
import com.github.hatoyuze.shiromi.protocol.api.DifficultyLevel
import com.github.hatoyuze.shiromi.protocol.api.LuoguTags
import com.github.hatoyuze.shiromi.protocol.api.ProblemDetailData
import compose.icons.FeatherIcons
import compose.icons.feathericons.BookOpen
import compose.icons.feathericons.AlertTriangle
import compose.icons.feathericons.RefreshCw

// ═══════════════════════════════════════════════════════════
// DailyProblemCard — 每日推荐（桌面与移动端统一卡片）
//
// 对齐 mobile-ui-preview.html 与 desktop-home-design.html 的「每日推荐」卡：
// 方块主色徽标 + 标题 + 刷新，题目 / 难度 / 标签 / 推荐理由，
// 右下「查看详情 ›」。tips 默认隐藏（设计稿不展示），可用 showTips 恢复。
// ═══════════════════════════════════════════════════════════

/**
 * 每日推荐卡片（AI 推荐题目）。
 *
 * @param state 来自 [DailyProblemAgent] 的当前状态
 * @param onRefresh 手动刷新
 * @param onViewDetail 打开题目详情
 * @param showTips 是否展示 LLM 附带的小贴士（默认隐藏，对齐设计稿）
 */
@Composable
fun DailyProblemCard(
    state: DailyProblemAgent.DailyProblemState,
    onRefresh: () -> Unit,
    onViewDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
    showTips: Boolean = false,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shadowElevation = 0.dp,
    ) {
        when {
            state.isLoading && state.result == null -> LoadingState()
            state.error != null && state.result == null -> ErrorState(state.error, onRefresh)
            state.result != null -> ContentState(
                result = state.result,
                problemDetail = state.problemDetail,
                isLoading = state.isLoading,
                onRefresh = onRefresh,
                onViewDetail = onViewDetail,
                showTips = showTips,
            )
            state.isLoading -> LoadingState()
            else -> EmptyState()
        }
    }
}

// ── Loading ──

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                "AI 正在推荐今日题目…",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

// ── Error ──

@Composable
private fun ErrorState(error: String, onRefresh: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = error,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "点击重试",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(
                indication = null,
                interactionSource = interactionSource,
            ) { onRefresh() },
        )
    }
}

// ── Empty / initial ──

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                FeatherIcons.BookOpen,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "AI 正在为你推荐今日题目…",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

// ── Content ──

@Composable
private fun ContentState(
    result: DailyProblemResult,
    problemDetail: ProblemDetailData?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onViewDetail: (String) -> Unit,
    showTips: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val detail = problemDetail?.problem

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
        ) {
            // ── Header：方块主色徽标 + 每日推荐 + 刷新 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                ) {
                    Icon(
                        FeatherIcons.BookOpen,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "每日推荐",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                // Manual refresh button
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        FeatherIcons.RefreshCw,
                        contentDescription = "刷新",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Title + Difficulty ──
            Text(
                text = "${result.pid} ${detail?.name ?: ""}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            // ── Tags / Difficulty ──
            if (detail != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DifficultyChip(detail.difficulty)
                    val tags = remember(detail.tags) { LuoguTags.resolveTags(detail.tags).take(3) }
                    tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        ) {
                            Text(
                                text = tag.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Reason (intro) ──
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    FeatherIcons.BookOpen,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp).padding(top = 1.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = result.reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Tips（默认隐藏；showTips 恢复）──
            if (showTips) {
                Spacer(Modifier.height(6.dp))
                result.tips.take(2).forEach { tip ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            FeatherIcons.AlertTriangle,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = tip,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Bottom action：查看详情 › ──
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "查看详情 ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = interactionSource,
                    ) { onViewDetail(result.pid) },
                )
            }
        }

        // ── Loading overlay during refresh ──
        if (isLoading) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun DifficultyChip(levelId: Int) {
    val level = DifficultyLevel.fromId(levelId)
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = parseHexColor(level.color),
    ) {
        Text(
            text = level.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
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

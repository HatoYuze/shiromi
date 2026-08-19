// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.ToolCallInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.Cpu

/**
 * Collapsible thinking/reasoning content display.
 *
 * Shows the model's internal reasoning in a styled collapsible box.
 * Initially collapsed — the thinking process is done by the time
 * this appears in the message bubble.
 */
@Composable
fun ThinkingSection(
    content: String,
    toolCalls: List<ToolCallInfo>? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = colorScheme.secondary

    val isExpanded = remember { mutableStateOf(false) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded.value) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "thinkingChevron",
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            // Header row — clickable to toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accentColor.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded.value = !isExpanded.value },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = FeatherIcons.Cpu,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )

                    Text(
                        text = "Thinking Process",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = accentColor,
                        ),
                        modifier = Modifier.weight(1f),
                    )

                    Icon(
                        imageVector = FeatherIcons.ChevronDown,
                        contentDescription = if (isExpanded.value) "Collapse" else "Expand",
                        tint = accentColor.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation),
                    )
                }
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded.value,
                enter = expandVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(200)),
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        bottomStart = 12.dp,
                        bottomEnd = 12.dp,
                    ),
                    color = accentColor.copy(alpha = 0.03f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (content.isNotBlank()) {
                            SelectionContainer {
                                Text(
                                    text = content,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Default,
                                        fontSize = 13.sp,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                        lineHeight = 20.sp,
                                    ),
                                    // 移动端（compact）：内容并入消息项、由外层 LazyColumn
                                    // 统一滚动，避免项内 verticalScroll 抢占触摸手势导致
                                    // 「展开思考链后无法下滑」；桌面保留限高内滚。
                                    modifier = if (compact) Modifier else Modifier
                                        .heightIn(max = 400.dp)
                                        .verticalScroll(rememberScrollState()),
                                )
                            }
                        }
                        if (!toolCalls.isNullOrEmpty()) {
                            ToolCallList(
                                toolCalls = toolCalls,
                                modifier = Modifier.padding(top = if (content.isNotBlank()) 8.dp else 0.dp),
                                compact = compact,
                            )
                        }
                    }
                }
            }
        }
    }
}

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronDown
import compose.icons.feathericons.Code
import com.github.hatoyuze.luogu.gui.domain.model.ToolCallInfo
import kotlinx.serialization.json.Json

/**
 * Wrapper that renders a list of [ToolCallSection] composables.
 */
@Composable
fun ToolCallList(
    toolCalls: List<ToolCallInfo>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        toolCalls.forEach { toolCall ->
            ToolCallSection(
                toolCall = toolCall,
                modifier = Modifier.fillMaxWidth(),
                compact = compact,
            )
        }
    }
}

/**
 * Collapsible tool call display for a single tool execution.
 *
 * Shows function name, arguments (pretty-printed JSON), and result.
 * Uses green tint for success, red tint for errors.
 * Initially collapsed.
 */
@Composable
fun ToolCallSection(
    toolCall: ToolCallInfo,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = if (toolCall.isError) colorScheme.error else colorScheme.tertiary

    val isExpanded = remember { mutableStateOf(false) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded.value) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "toolChevron",
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f)),
        modifier = modifier,
    ) {
        Column {
            // Header row
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
                        imageVector = FeatherIcons.Code,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )

                    Text(
                        text = "Tool: ${toolCall.name}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = accentColor,
                        ),
                        modifier = Modifier.weight(1f),
                    )

                    if (toolCall.isError) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = colorScheme.error.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "ERROR",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }

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
                    Column(
                        // 移动端（compact）：并入消息项由外层滚动，避免项内 verticalScroll
                        // 抢占触摸手势；桌面保留限高内滚。
                        modifier = if (compact) {
                            Modifier.padding(12.dp)
                        } else {
                            Modifier
                                .heightIn(max = 400.dp)
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        },
                    ) {
                        // Arguments section
                        SectionLabel("Arguments")
                        Spacer(Modifier.height(4.dp))
                        CodeBlock(
                            text = prettyPrintJson(toolCall.arguments),
                        )

                        // Result section
                        if (toolCall.result != null) {
                            Spacer(Modifier.height(8.dp))
                            SectionLabel("Result")
                            Spacer(Modifier.height(4.dp))
                            CodeBlock(
                                text = prettyPrintJson(toolCall.result),
                                isError = toolCall.isError,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontWeight = FontWeight.SemiBold,
        ),
    )
}

@Composable
private fun CodeBlock(
    text: String,
    isError: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isError)
            colorScheme.error.copy(alpha = 0.06f)
        else
            colorScheme.surfaceVariant.copy(alpha = 0.2f),
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (isError)
                        colorScheme.error.copy(alpha = 0.85f)
                    else
                        colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    lineHeight = 18.sp,
                ),
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

private val prettyJson = Json { isLenient = true; ignoreUnknownKeys = true; prettyPrint = true }
private val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }

/** Attempts to pretty-print a JSON string; falls back to the original string. */
private fun prettyPrintJson(raw: String): String {
    return try {
        val element = lenientJson.parseToJsonElement(raw.trim())
        prettyJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), element)
    } catch (_: Exception) {
        raw
    }
}

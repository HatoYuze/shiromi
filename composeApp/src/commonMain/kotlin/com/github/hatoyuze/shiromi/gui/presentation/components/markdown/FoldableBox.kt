// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components.markdown

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.hatoyuze.shiromi.gui.presentation.markdown.FoldableFlavourDescriptor
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

/**
 * Maps foldable block type strings to MaterialTheme colors.
 */
@Composable
private fun foldableTypeColor(type: String): Color {
    val colorScheme = MaterialTheme.colorScheme
    return when (type.lowercase()) {
        "info" -> colorScheme.primary
        "success" -> colorScheme.tertiary
        "warning" -> colorScheme.secondary
        "error" -> colorScheme.error
        else -> colorScheme.primary // fallback
    }
}

/**
 * Maps foldable block type strings to FeatherIcons.
 */
@Composable
private fun FoldableTypeIcon(type: String, tint: Color, modifier: Modifier = Modifier) {
    val icon = when (type.lowercase()) {
        "info" -> FeatherIcons.Info
        "success" -> FeatherIcons.CheckCircle
        "warning" -> FeatherIcons.AlertTriangle
        "error" -> FeatherIcons.XCircle
        else -> FeatherIcons.Info
    }
    Icon(
        imageVector = icon,
        contentDescription = type,
        tint = tint,
        modifier = modifier.size(20.dp)
    )
}

/**
 * Renders a collapsible foldable block with animated expand/collapse.
 *
 * Extracts header metadata (type, title, isOpen) from the FOLDABLE_HEADER child node
 * and renders inner content with standard Markdown processing.
 */
@Composable
fun FoldableBoxContent(
    node: org.intellij.markdown.ast.ASTNode,
    content: String,
    typography: com.mikepenz.markdown.model.MarkdownTypography,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    // Extract header info from FOLDABLE_HEADER child node
    val headerNode = node.children.firstOrNull()
    val headerText = if (headerNode != null) {
        content.substring(headerNode.startOffset, headerNode.endOffset)
    } else {
        ""
    }

    val headerInfo = com.github.hatoyuze.shiromi.gui.presentation.markdown.FoldableHeaderInfo.parse(headerText.trim())
    val foldableType = headerInfo?.type ?: "info"
    val title = headerInfo?.title ?: ""
    val isInitiallyOpen = headerInfo?.isOpen ?: false

    val typeColor = foldableTypeColor(foldableType)

    // State for expand/collapse
    val isExpanded = remember { mutableStateOf(isInitiallyOpen) }

    // Chevron rotation animation
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded.value) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "chevronRotation"
    )

    // Extract inner content text (everything between FOLDABLE_HEADER and end of FOLDABLE_BLOCK)
    val innerContent = remember(content, node.startOffset, node.endOffset) {
        val contentStart = headerNode?.endOffset?.let { it + 1 } ?: node.startOffset
        val contentEnd = node.endOffset
        if (contentStart < contentEnd) {
            content.substring(contentStart, contentEnd).trim()
        } else {
            ""
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = typeColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, typeColor.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Header row - clickable to toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded.value = !isExpanded.value }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Type indicator icon
                    FoldableTypeIcon(type = foldableType, tint = typeColor)

                    // Title
                    Text(
                        text = title.ifEmpty { foldableType.replaceFirstChar { it.uppercase() } },
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = typeColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Chevron indicator
                    Icon(
                        imageVector = FeatherIcons.ChevronDown,
                        contentDescription = if (isExpanded.value) "Collapse" else "Expand",
                        tint = typeColor,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronRotation)
                    )
                }
            }

            // Expandable content area
            AnimatedVisibility(
                visible = isExpanded.value,
                enter = expandVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300)),
                exit = shrinkVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    color = typeColor.copy(alpha = 0.04f)
                ) {
                    if (innerContent.isNotEmpty()) {
                        Markdown(
                            content = innerContent,
                            modifier = Modifier
                                .wrapContentWidth()
                                .padding(12.dp),
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
                            dimens = markdownDimens(
                                dividerThickness = 1.dp,
                                codeBackgroundCornerSize = 12.dp,
                                blockQuoteThickness = 2.dp,
                                tableMaxWidth = Dp.Unspecified,
                                tableCellWidth = 160.dp,
                                tableCellPadding = 16.dp,
                                tableCornerSize = 8.dp,
                            ),
                            padding = markdownPadding(
                                block = 4.dp,
                                list = 8.dp,
                                listItemBottom = 4.dp,
                                indentList = 8.dp,
                                codeBlock = PaddingValues(12.dp),
                                blockQuote = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                blockQuoteText = PaddingValues(vertical = 4.dp),
                                blockQuoteBar = PaddingValues.Absolute(
                                    left = 4.dp,
                                    top = 2.dp,
                                    right = 4.dp,
                                    bottom = 2.dp
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }
}

// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.LocalMarkdownComponents
import com.mikepenz.markdown.compose.LocalMarkdownTypography
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.material.MarkdownBasicText
import com.mikepenz.markdown.model.DefaultMarkdownAnnotator
import com.mikepenz.markdown.utils.MARKDOWN_TAG_IMAGE_URL
import com.mikepenz.markdown.utils.buildMarkdownAnnotatedString
import com.mikepenz.markdown.utils.codeSpanStyle
import com.mikepenz.markdown.utils.linkTextSpanStyle
import io.ratex.DisplayList
import io.ratex.RaTeXEngine
import io.ratex.compose.RaTeX
import io.ratex.measure
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import com.github.hatoyuze.shiromi.gui.presentation.markdown.CuteTableElementTypes
import com.github.hatoyuze.shiromi.gui.presentation.markdown.FoldableElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes

// ── Math rendering constants ──
private val INLINE_FONT_SIZE = 18.sp
private val BLOCK_FONT_SIZE = 22.sp
/** Additional horizontal spacing on each side of an inline formula placeholder. */
private const val PLACEHOLDER_H_PAD = 2f

private data class MathEntry(
    val id: String,
    val formula: String,
    val dims: FormulaDimensions,
    val displayList: DisplayList?,
)

/**
 * Builds the [InlineTextContent] map and [DefaultMarkdownAnnotator] needed to render
 * inline LaTeX formulas within a markdown paragraph.
 *
 * Each formula is parsed **once** via [RaTeXEngine.parseBlocking] (cached per formula
 * string + color via [remember]). The resulting [DisplayList] is shared between
 * dimension measurement (placeholder sizing) and rendering (RaTeX),
 * eliminating the redundant second native parse.
 *
 * The placeholder height is set to [FormulaDimensions.ascent] so that
 * [PlaceholderVerticalAlign.AboveBaseline] correctly aligns the formula baseline
 * with the text baseline.
 *
 * @param formulas  deduplicated list of LaTeX formula strings in this paragraph
 * @param fontSize  font size for rendering the formulas
 * @param color     text color for the formulas (passed to RaTeX parser)
 */
@Composable
private fun rememberInlineMathSupport(
    formulas: List<String>,
    fontSize: TextUnit,
    color: Color,
): Pair<Map<String, InlineTextContent>, DefaultMarkdownAnnotator> {
    val density = LocalDensity.current

    // ── Per-formula DisplayList cache ──
    // Each formula is parsed ONCE and cached by its string + color.
    // During streaming, previously-seen formulas hit this cache (instant O(1)).
    val displayLists: List<DisplayList?> = formulas.map { formula ->
        remember(formula, color) {
            try {
                RaTeXEngine.parseBlocking(formula, displayMode = false, color = color)
            } catch (_: Exception) {
                null
            }
        }
    }

    // ── Measure each DisplayList (or use fallback) ──
    val dims: List<FormulaDimensions> = formulas.zip(displayLists).map { (formula, dl) ->
        if (dl != null) rememberFormulaDimensions(dl, fontSize)
        else fallbackFormulaDimensions(formula, fontSize, density)
    }

    // ── Build MathEntry list ──
    val entries: List<MathEntry> = formulas.mapIndexed { idx, formula ->
        MathEntry("math_$idx", formula, dims[idx], displayLists[idx])
    }

    // ── Annotator: O(1) lookup via HashMap ──
    val annotator = remember(formulas, fontSize) {
        val formulaToId = entries.associate { it.formula to it.id }
        DefaultMarkdownAnnotator { contentStr, child ->
            if (child.type == GFMElementTypes.INLINE_MATH) {
                val formula = extractLaTeX(contentStr, child)
                val id = formulaToId[formula] ?: return@DefaultMarkdownAnnotator false
                appendInlineContent(id, formula)
                true
            } else false
        }
    }

    // ── Inline content map ──
    val inlineContent = remember(formulas, fontSize) {
        entries.associate { entry ->
            entry.id to InlineTextContent(
                Placeholder(
                    width = (entry.dims.width.value + PLACEHOLDER_H_PAD).sp,
                    height = entry.dims.totalHeight,
                    PlaceholderVerticalAlign.AboveBaseline,
                )
            ) {
                // AboveBaseline: placeholder bottom = text baseline.
                // The formula baseline sits at `ascent` from its top, so at y=0
                // it lands at textBaseline - totalHeight + ascent = textBaseline - descent.
                // Offset by +descent to bring the baseline back to textBaseline.
                val descentPx = with(LocalDensity.current) { entry.dims.descent.toPx() }
                val descentDp = with(LocalDensity.current) { descentPx.toDp() }
                RaTeX(
                    displayList = entry.displayList,
                    fontSize = fontSize,
                    modifier = Modifier.offset(y = descentDp),
                )
            }
        } + mapOf(
            MARKDOWN_TAG_IMAGE_URL to InlineTextContent(
                Placeholder(0.sp, 0.sp, PlaceholderVerticalAlign.TextTop)
            ) { }
        )
    }

    return Pair(inlineContent, annotator)
}

/**
 * Drop-in replacement for [MarkdownParagraph] that intercepts inline and block
 * LaTeX math nodes, rendering them via RaTeX.
 *
 * Three rendering paths:
 * 1. **No math** — delegates to the standard [MarkdownParagraph]
 * 2. **Has block math** — segments children into text groups and block formulas;
 *    text groups render with inline math support via `flushTextGroup`
 * 3. **Only inline math** — unified [buildMarkdownAnnotatedString] with the
 *    inline math annotator, preserving bold/italic/code formatting
 */
@Composable
fun MathAwareParagraph(model: MarkdownComponentModel) {
    val node = model.node
    val content = model.content
    val hasBlockMath = node.children.any { it.type == GFMElementTypes.BLOCK_MATH }
    val hasInlineMath = node.children.any { it.type == GFMElementTypes.INLINE_MATH }

    when {
        !hasBlockMath && !hasInlineMath -> MarkdownParagraph(content, node)

        hasBlockMath -> {
            val typography = LocalMarkdownTypography.current
            val themeColor = MaterialTheme.colorScheme.onSurface

            val inlineMathFormulas = node.children
                .filter { it.type == GFMElementTypes.INLINE_MATH }
                .map { extractLaTeX(content, it) }
            val (inlineContent, inlineMathAnnotator) = rememberInlineMathSupport(
                formulas = inlineMathFormulas,
                fontSize = INLINE_FONT_SIZE,
                color = themeColor,
            )

            var textGroup = mutableListOf<ASTNode>()
            for (child in node.children) {
                if (child.type == GFMElementTypes.BLOCK_MATH) {
                    flushTextGroup(content, textGroup, typography, inlineMathAnnotator, inlineContent)
                    textGroup.clear()

                    val formula = extractLaTeX(content, child)

                    // Parse block formula ONCE, share DisplayList with rendering
                    val blockDl: DisplayList? = remember(formula) {
                        try {
                            RaTeXEngine.parseBlocking(formula, displayMode = true, color = themeColor)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    // 块级公式：能放下则居中显示，超宽则横向滚动，避免溢出聊天框
                    val density = LocalDensity.current
                    val blockFontPx = with(density) { BLOCK_FONT_SIZE.toPx() }
                    val blockWidthPx = remember(blockDl, blockFontPx) {
                        blockDl?.measure(blockFontPx)?.widthPx ?: 0f
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val fits = with(LocalDensity.current) { blockWidthPx <= maxWidth.toPx() }
                        if (fits) {
                            Box(
                                Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                RaTeX(
                                    displayList = blockDl,
                                    fontSize = BLOCK_FONT_SIZE,
                                )
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            ) {
                                RaTeX(
                                    displayList = blockDl,
                                    fontSize = BLOCK_FONT_SIZE,
                                )
                            }
                        }
                    }
                } else {
                    textGroup.add(child)
                }
            }
            flushTextGroup(content, textGroup, typography, inlineMathAnnotator, inlineContent)
        }

        else -> {
            val themeColor = MaterialTheme.colorScheme.onSurface
            val inlineFormulas = node.children
                .filter { it.type == GFMElementTypes.INLINE_MATH }
                .map { extractLaTeX(content, it) }
            val (inlineContent, annotator) = rememberInlineMathSupport(
                formulas = inlineFormulas,
                fontSize = INLINE_FONT_SIZE,
                color = themeColor,
            )

            val annotatedString = content.buildMarkdownAnnotatedString(
                textNode = node,
                style = LocalMarkdownTypography.current.paragraph,
                annotator = annotator,
            )
            MarkdownBasicText(
                annotatedString,
                style = LocalMarkdownTypography.current.paragraph,
                color = themeColor,
                inlineContent = inlineContent,
            )
        }
    }
}

/** Renders a group of consecutive non-block-math AST children as styled markdown text. */
@Composable
private fun flushTextGroup(
    content: String,
    children: List<ASTNode>,
    typography: com.mikepenz.markdown.model.MarkdownTypography,
    annotator: DefaultMarkdownAnnotator,
    inlineContent: Map<String, InlineTextContent>,
) {
    if (children.isEmpty()) return
    val annotated = buildAnnotatedString {
        pushStyle(typography.paragraph.toSpanStyle())
        buildMarkdownAnnotatedString(
            content, children,
            linkTextStyle = typography.linkTextSpanStyle,
            codeStyle = typography.codeSpanStyle,
            annotator = annotator,
        )
        pop()
    }
    MarkdownBasicText(
        annotated,
        style = typography.paragraph,
        color = MaterialTheme.colorScheme.onSurface,
        inlineContent = inlineContent,
    )
}

/**
 * Fallback handler for markdown AST node types not covered by the standard
 * `markdownComponents` defaults. Dispatches PARAGRAPH, TEXT, EMPH, STRONG,
 * CODE_SPAN, INLINE_LINK, AUTOLINK, lists, code blocks, block quotes, images,
 * tables, and [FoldableBoxContent] (custom foldable block).
 *
 * Use as `custom = { type, model -> traverseUnhandledNode(type, model) }`
 * in the `markdownComponents` DSL.
 */
@Composable
fun ColumnScope.traverseUnhandledNode(
    type: IElementType,
    model: MarkdownComponentModel,
) {
    when (type) {
        FoldableElementTypes.FOLDABLE_BLOCK -> {
            FoldableBoxContent(node = model.node, content = model.content, typography = model.typography)
            return
        }
    }

    val components = LocalMarkdownComponents.current
    for (child in model.node.children) {
        val childModel = MarkdownComponentModel(model.content, child, model.typography)
        when (child.type) {
            MarkdownElementTypes.PARAGRAPH -> components.paragraph.invoke(this, childModel)
            MarkdownTokenTypes.TEXT -> components.text.invoke(this, childModel)
            MarkdownElementTypes.EMPH -> components.text.invoke(this, childModel)
            MarkdownElementTypes.STRONG -> components.text.invoke(this, childModel)
            MarkdownElementTypes.CODE_SPAN -> components.text.invoke(this, childModel)
            MarkdownElementTypes.INLINE_LINK -> components.text.invoke(this, childModel)
            MarkdownElementTypes.AUTOLINK -> components.text.invoke(this, childModel)
            MarkdownElementTypes.ORDERED_LIST -> components.orderedList.invoke(this, childModel)
            MarkdownElementTypes.UNORDERED_LIST -> components.unorderedList.invoke(this, childModel)
            MarkdownElementTypes.CODE_BLOCK -> components.codeBlock.invoke(this, childModel)
            MarkdownElementTypes.CODE_FENCE -> components.codeFence.invoke(this, childModel)
            MarkdownElementTypes.BLOCK_QUOTE -> components.blockQuote.invoke(this, childModel)
            MarkdownElementTypes.IMAGE -> components.image.invoke(this, childModel)
            GFMElementTypes.TABLE -> components.table.invoke(this, childModel)
            FoldableElementTypes.FOLDABLE_BLOCK -> {
                FoldableBoxContent(node = child, content = model.content, typography = model.typography)
            }
            CuteTableElementTypes.CUTE_TABLE_MARKER -> {
                // Zero-size: directive text already consumed by MarkerBlock
            }
            else -> {
                for (grandchild in child.children) {
                    val gcModel = MarkdownComponentModel(model.content, grandchild, model.typography)
                    when (grandchild.type) {
                        MarkdownElementTypes.PARAGRAPH -> components.paragraph.invoke(this, gcModel)
                        MarkdownTokenTypes.TEXT -> components.text.invoke(this, gcModel)
                        else -> {}
                    }
                }
            }
        }
    }
}

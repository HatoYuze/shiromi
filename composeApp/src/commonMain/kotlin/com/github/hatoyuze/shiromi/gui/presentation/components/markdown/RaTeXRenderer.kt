// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components.markdown

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.ratex.DisplayList
import io.ratex.measure
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode

/**
 * Extracts the LaTeX formula string from a math AST node,
 * stripping the surrounding `$` (inline) or `$$` (block) delimiters.
 */
fun extractLaTeX(content: String, node: ASTNode): String {
    val rawText = node.getTextInNode(content).toString().trim()
    return when {
        rawText.startsWith("$$") && rawText.endsWith("$$") ->
            rawText.removePrefix("$$").removeSuffix("$$").trim()
        rawText.startsWith('$') && rawText.endsWith('$') ->
            rawText.removePrefix("$").removeSuffix("$").trim()
        else -> rawText
    }
}

/** Fallback width multiplier when RaTeX measurement fails (char count × multiplier ≈ glyph width). */
private const val FALLBACK_WIDTH_RATIO = 0.5f
/** Fallback height multiplier when RaTeX measurement fails (font size × multiplier = estimated height). */
private const val FALLBACK_HEIGHT_RATIO = 1.2f

/**
 * Pre-measured dimensions of a rendered formula.
 *
 * @param width   total width of the formula bounding box
 * @param ascent  height above the baseline (= DisplayList.height × fontSize)
 * @param descent depth below the baseline (= DisplayList.depth × fontSize)
 */
data class FormulaDimensions(
    val width: TextUnit,
    val ascent: TextUnit,
    val descent: TextUnit,
) {
    /** Convenience: total bounding box height = ascent + descent. */
    val totalHeight: TextUnit get() = (ascent.value + descent.value).sp
}

/** Fallback dimensions when formula parsing fails. Approximates glyph proportions. */
fun fallbackFormulaDimensions(formula: String, fontSize: TextUnit, density: Density): FormulaDimensions {
    val fontSizePx = with(density) { fontSize.toPx() }
    val totalH = fontSizePx * FALLBACK_HEIGHT_RATIO
    return FormulaDimensions(
        width = with(density) { (fontSizePx * formula.length.toFloat() * FALLBACK_WIDTH_RATIO).toSp() },
        ascent = with(density) { (totalH * 0.7f).toSp() },
        descent = with(density) { (totalH * 0.3f).toSp() },
    )
}

/**
 * Measures the given [displayList] to produce [FormulaDimensions].
 *
 * Unlike the previous overload, this does NOT call the RaTeX parser — it only measures
 * an already-parsed [DisplayList]. This enables sharing a single [DisplayList] between
 * measurement (placeholder sizing) and rendering (composable), avoiding redundant
 * native JNA calls.
 */
@Composable
fun rememberFormulaDimensions(displayList: DisplayList, fontSize: TextUnit): FormulaDimensions {
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSize.toPx() }
    val measured = remember(displayList, fontSizePx) { displayList.measure(fontSizePx) }
    return FormulaDimensions(
        width = with(density) { measured.widthPx.toSp() },
        ascent = with(density) { measured.heightPx.toSp() },
        descent = with(density) { measured.depthPx.toSp() },
    )
}

// 注：RaTeX 0.1.14+ 的 `io.ratex.compose.RaTeX` 已原生上报 FirstBaseline/LastBaseline，
// 此前用于对齐的 BaselineAlignedRaTeX 包装器已删除，直接使用 RaTeX 即可。

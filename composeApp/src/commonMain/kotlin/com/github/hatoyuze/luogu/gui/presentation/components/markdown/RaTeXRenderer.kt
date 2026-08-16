package com.github.hatoyuze.luogu.gui.presentation.components.markdown

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.ratex.DisplayList
import io.ratex.compose.RaTeX
import io.ratex.compose.rememberRaTeXDisplayList
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

/**
 * A RaTeX wrapper that properly reports its [FirstBaseline] alignment line
 * so that formulas in a [Row] with [Modifier.alignByBaseline] share the same
 * baseline as surrounding [Text].
 *
 * @param precomputedDisplayList when provided, the internal `rememberRaTeXDisplayList`
 *   call is skipped and this [DisplayList] is used directly. This eliminates a
 *   redundant native parse when the caller already holds a parsed [DisplayList].
 */
@Composable
fun BaselineAlignedRaTeX(
    latex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    displayMode: Boolean = false,
    color: Color = LocalContentColor.current,
    precomputedDisplayList: DisplayList? = null,
) {
    val resolvedList: DisplayList?
    if (precomputedDisplayList != null) {
        resolvedList = precomputedDisplayList
    } else {
        val parseResult by rememberRaTeXDisplayList(latex, displayMode, color)
        resolvedList = parseResult?.getOrNull()
    }

    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSize.toPx() }

    val measured = remember(resolvedList, fontSizePx) {
        resolvedList?.measure(fontSizePx)
    }

    if (measured != null && resolvedList != null) {
        val baselinePx = measured.heightPx.toInt()

        Layout(
            modifier = modifier,
            content = {
                RaTeX(
                    displayList = resolvedList,
                    fontSize = fontSize,
                )
            },
        ) { measurables, constraints ->
            val placeable = measurables.first().measure(constraints)
            layout(
                width = placeable.width,
                height = placeable.height,
                alignmentLines = mapOf(FirstBaseline to baselinePx),
            ) {
                placeable.placeRelative(0, 0)
            }
        }
    }
}

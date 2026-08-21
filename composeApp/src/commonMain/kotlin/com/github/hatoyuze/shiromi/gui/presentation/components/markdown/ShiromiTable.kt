// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.shiromi.gui.presentation.markdown.CuteTableElementTypes
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import io.ratex.RaTeXEngine
import io.ratex.compose.RaTeX
import kotlin.math.max
import kotlin.math.min
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * Universal markdown table renderer (from-scratch implementation).
 *
 * Every table — ordinary GFM or `::cute-table{tuack}` styled — is drawn by this
 * component so that:
 *  - each column's cells are centered on the column's shared center axis,
 *  - the whole table is centered horizontally in its container (scrolling
 *    horizontally when wider than the viewport),
 *  - a body cell containing only `^` merges with the cell above it in the same
 *    column (rowspan), with the merged content vertically centered.
 *
 * The three-line (GB/T 7713.1) style is selected *structurally*: the TABLE node's
 * immediately preceding AST sibling is the zero-size [CuteTableElementTypes.CUTE_TABLE_MARKER]
 * node produced by the `CuteTableMarkerBlock` parser — no raw-text scanning, so it
 * is O(1) and immune to variant spelling (`tuack`/`truck`/…), leading whitespace or
 * a trailing `::`.
 */
@Composable
fun ColumnScope.ShiromiTable(model: MarkdownComponentModel) {
    // The upstream Markdown composable rebuilds the AST on every recomposition
    // (streaming tokens), so node identity changes each frame. Cache the parsed grid
    // on (table text, table position): when both are unchanged the stored cell nodes
    // remain valid for the current content and the O(rows×columns) parse is skipped.
    val tableText = remember(model.node, model.content) {
        model.content.substring(model.node.startOffset, model.node.endOffset)
    }
    val tableStart = model.node.startOffset
    val grid = remember(tableText, tableStart) { parseTableGrid(model.node, model.content) }
    if (grid.isEmpty) return
    val style = remember(tableText, tableStart) {
        if (isThreeLineTable(model.node)) TableStyle.THREE_LINE else TableStyle.DEFAULT
    }
    TableLayout(model = model, grid = grid, style = style)
}

/** Visual style of a rendered table. */
internal enum class TableStyle {
    /** GB-style three-line table: thick top/bottom rules, thin rule under the header, no vertical lines. */
    THREE_LINE,

    /** Ordinary GFM table: rounded background, bold header, thin rule under the header. */
    DEFAULT,
}

/**
 * Structural three-line detection: the table node's previous sibling in the AST is
 * the consumed `::cute-table{…}` marker line. An `EOL` node may sit between the
 * marker and the table (the marker block closes before the line break), so EOL
 * siblings are skipped while walking back.
 */
internal fun isThreeLineTable(tableNode: ASTNode): Boolean {
    val parent = tableNode.parent ?: return false
    val siblings = parent.children
    val index = siblings.indexOf(tableNode)
    if (index <= 0) return false
    var previous = index - 1
    while (previous >= 0 && siblings[previous].type == MarkdownTokenTypes.EOL) previous--
    return previous >= 0 && siblings[previous].type == CuteTableElementTypes.CUTE_TABLE_MARKER
}

/** A single table cell; [rowspan] grows when `^` cells below merge into it. */
internal data class TableCell(
    val node: ASTNode,
    val rowspan: Int,
)

/** Parsed grid: header cells plus body rows padded to [columnCount] with `null` for merged-away slots. */
internal data class TableGrid(
    val header: List<TableCell>,
    val rows: List<List<TableCell?>>,
    val columnCount: Int,
) {
    val isEmpty: Boolean get() = header.isEmpty() || columnCount == 0
}

/**
 * Parses a GFM TABLE node into a grid and resolves `^` rowspan merging:
 * a body cell whose trimmed text is exactly `^` (and which is not in the first
 * body row) is merged into the nearest live cell above it in the same column,
 * growing that cell's rowspan. `^` in the first body row or in the header stays
 * a literal cell.
 */
internal fun parseTableGrid(tableNode: ASTNode, content: String): TableGrid {
    val children = tableNode.children
    val headerNode = children.firstOrNull { it.type == GFMElementTypes.HEADER }
    val headerCells = headerNode?.children?.filter { it.type == GFMTokenTypes.CELL }.orEmpty()
    if (headerCells.isEmpty()) return TableGrid(emptyList(), emptyList(), 0)
    val columnCount = headerCells.size

    val rawRows = children.filter { it.type == GFMElementTypes.ROW }
        .map { row -> row.children.filter { it.type == GFMTokenTypes.CELL } }
    val rawText = rawRows.map { row -> row.map { cell -> content.substring(cell.startOffset, cell.endOffset).trim() } }

    val resolved: List<MutableList<TableCell?>> = rawRows.map { row ->
        row.map { TableCell(it, rowspan = 1) }.toMutableList()
    }
    // One-pass top-down merge per column (O(rows × columns)): `liveRow` tracks the
    // nearest row above that holds a real (non-'^') cell in this column. A '^' cell
    // merges into it; a literal '^' in the first body row is not a merge target and
    // keeps blocking merges until a real cell resets the chain.
    for (col in 0 until columnCount) {
        var liveRow = -1
        for (row in rawRows.indices) {
            val isCaret = col < rawText[row].size && rawText[row][col] == "^"
            if (isCaret && row > 0) {
                val target = if (liveRow >= 0) resolved[liveRow].getOrNull(col) else null
                if (target != null) {
                    resolved[liveRow][col] = target.copy(rowspan = target.rowspan + 1)
                    resolved[row][col] = null
                }
            } else if (!isCaret) {
                liveRow = row
            }
        }
    }

    return TableGrid(
        header = headerCells.map { TableCell(it, rowspan = 1) },
        rows = resolved.map { row -> (0 until columnCount).map { row.getOrNull(it) } },
        columnCount = columnCount,
    )
}

// ─────────────────────────────────────────────────────────────
// Layout constants
// ─────────────────────────────────────────────────────────────

private val CELL_PADDING_H = 8.dp
private val CELL_PADDING_V = 6.dp
private val CELL_FONT_SIZE = 16.sp
private val MIN_COLUMN_WIDTH = 24.dp
private val MIN_ROW_HEIGHT = 24.dp
private val THREE_LINE_RULE = 4.dp
private val THREE_LINE_SEPARATOR = 1.5.dp
private val DEFAULT_SEPARATOR = 1.dp

// ─────────────────────────────────────────────────────────────
// Layout
// ─────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.TableLayout(
    model: MarkdownComponentModel,
    grid: TableGrid,
    style: TableStyle,
) {
    val colors = LocalMarkdownColors.current
    val dimens = LocalMarkdownDimens.current
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val numCols = grid.columnCount
    val numRows = grid.rows.size

    val textColor = if (style == TableStyle.THREE_LINE) colors.text else colors.tableText
    val lineColor = if (style == TableStyle.THREE_LINE) colors.text else colors.dividerColor
    val headerWeight = if (style == TableStyle.THREE_LINE) FontWeight.Normal else FontWeight.Bold

    // Child order: header cells, then body cells (row-major, live cells only), then style rules.
    data class BodySlot(val row: Int, val col: Int, val cell: TableCell)
    val bodySlots = buildList {
        for (r in grid.rows.indices) {
            for (c in 0 until numCols) {
                grid.rows[r][c]?.let { add(BodySlot(r, c, it)) }
            }
        }
    }
    val headerCount = numCols
    val bodyOffset = headerCount

    val backgroundModifier = if (style == TableStyle.DEFAULT) {
        Modifier.background(colors.tableBackground, RoundedCornerShape(dimens.tableCornerSize))
    } else {
        Modifier
    }

    Layout(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .then(backgroundModifier)
            .horizontalScroll(scrollState),
        content = {
            for (c in 0 until numCols) {
                val cell = grid.header[c]
                TableCellContent(
                    content = model.content,
                    node = cell.node,
                    color = textColor,
                    isHeader = true,
                    headerWeight = headerWeight,
                )
            }
            for (slot in bodySlots) {
                TableCellContent(
                    content = model.content,
                    node = slot.cell.node,
                    color = textColor,
                    isHeader = false,
                    headerWeight = headerWeight,
                )
            }
            when (style) {
                TableStyle.THREE_LINE -> {
                    Box(Modifier.height(THREE_LINE_RULE).background(lineColor))
                    Box(Modifier.height(THREE_LINE_SEPARATOR).background(lineColor))
                    Box(Modifier.height(THREE_LINE_RULE).background(lineColor))
                }
                TableStyle.DEFAULT -> {
                    Box(Modifier.height(DEFAULT_SEPARATOR).background(lineColor))
                }
            }
        },
    ) { measurables, _ ->
        val headerMeas = measurables.subList(0, headerCount)
        val bodyMeas = measurables.subList(bodyOffset, bodyOffset + bodySlots.size)
        val ruleMeas = measurables.subList(bodyOffset + bodySlots.size, measurables.size)
        val allCellMeas = headerMeas + bodyMeas
        val columnOf = IntArray(allCellMeas.size) { i -> if (i < headerCount) i else bodySlots[i - headerCount].col }

        // Compose allows only ONE measure() per measurable per pass, so column widths and
        // wrapped row heights are derived from intrinsic measurements and every cell is
        // measured exactly once with its final box.
        // ── Column widths: natural (unwrapped) width per column (single pass over cells) ──
        val minColPx = with(density) { MIN_COLUMN_WIDTH.roundToPx() }
        val colWidths = IntArray(numCols) { minColPx }
        for (i in allCellMeas.indices) {
            val width = allCellMeas[i].maxIntrinsicWidth(Constraints.Infinity)
            val col = columnOf[i]
            if (width > colWidths[col]) colWidths[col] = width
        }
        val tableW = colWidths.sum()

        // ── Row heights: wrapped height at the column width ──
        val minRowPx = with(density) { MIN_ROW_HEIGHT.roundToPx() }
        var headerH = minRowPx
        for (i in 0 until headerCount) {
            headerH = max(headerH, allCellMeas[i].maxIntrinsicHeight(colWidths[columnOf[i]]))
        }

        val rowHeights = IntArray(numRows) { minRowPx }
        for (i in bodySlots.indices) {
            val slot = bodySlots[i]
            if (slot.cell.rowspan == 1) {
                val height = allCellMeas[bodyOffset + i].maxIntrinsicHeight(colWidths[slot.col])
                if (height > rowHeights[slot.row]) rowHeights[slot.row] = height
            }
        }

        // Span cells may need more height than their rows currently allocate; distribute the excess.
        val spanNeeded = IntArray(bodySlots.size) { i ->
            val slot = bodySlots[i]
            if (slot.cell.rowspan > 1) {
                allCellMeas[bodyOffset + i].maxIntrinsicHeight(colWidths[slot.col])
            } else {
                0
            }
        }
        var changed = true
        var iteration = 0
        while (changed && iteration < 10) {
            changed = false
            iteration++
            for (i in bodySlots.indices) {
                val slot = bodySlots[i]
                if (slot.cell.rowspan <= 1) continue
                val span = min(slot.cell.rowspan, numRows - slot.row)
                val allocated = (0 until span).sumOf { rowHeights[slot.row + it] }
                val needed = spanNeeded[i]
                if (needed > allocated) {
                    val excess = needed - allocated
                    val perRow = excess / span
                    val remainder = excess % span
                    for (d in 0 until span) {
                        rowHeights[slot.row + d] += perRow + (if (d < remainder) 1 else 0)
                    }
                    changed = true
                }
            }
        }

        // ── Geometry ──
        val topPx = with(density) {
            if (style == TableStyle.THREE_LINE) THREE_LINE_RULE.roundToPx() else 0
        }
        val separatorPx = with(density) {
            if (style == TableStyle.THREE_LINE) THREE_LINE_SEPARATOR.roundToPx() else DEFAULT_SEPARATOR.roundToPx()
        }
        val bottomPx = with(density) {
            if (style == TableStyle.THREE_LINE) THREE_LINE_RULE.roundToPx() else 0
        }
        val tableH = topPx + headerH + separatorPx + rowHeights.sum() + bottomPx

        val colX = IntArray(numCols + 1)
        for (c in 0 until numCols) colX[c + 1] = colX[c] + colWidths[c]
        val rowY = IntArray(numRows + 1)
        rowY[0] = topPx + headerH + separatorPx
        for (r in 0 until numRows) rowY[r + 1] = rowY[r] + rowHeights[r]

        // ── Final: measure every cell exactly once with its final box ──
        val precise = allCellMeas.mapIndexed { i, m ->
            val col = columnOf[i]
            val height = if (i < headerCount) {
                headerH
            } else {
                val slot = bodySlots[i - headerCount]
                if (slot.cell.rowspan > 1) {
                    val span = min(slot.cell.rowspan, numRows - slot.row)
                    (0 until span).sumOf { rowHeights[slot.row + it] }
                } else {
                    rowHeights[slot.row]
                }
            }
            m.measure(
                Constraints(
                    minWidth = colWidths[col],
                    maxWidth = colWidths[col],
                    minHeight = height,
                    maxHeight = height,
                )
            )
        }
        val rules = ruleMeas.mapIndexed { i, m ->
            val height = when (style) {
                TableStyle.THREE_LINE -> if (i == 1) separatorPx else topPx
                TableStyle.DEFAULT -> separatorPx
            }
            m.measure(
                Constraints(
                    minWidth = tableW,
                    maxWidth = tableW,
                    minHeight = height,
                    maxHeight = height,
                )
            )
        }

        layout(tableW, tableH) {
            for (c in 0 until numCols) precise[c].place(colX[c], topPx)
            for (i in bodySlots.indices) {
                val slot = bodySlots[i]
                precise[bodyOffset + i].place(colX[slot.col], rowY[slot.row])
            }
            when (style) {
                TableStyle.THREE_LINE -> {
                    rules[0].place(0, 0)
                    rules[1].place(0, topPx + headerH)
                    rules[2].place(0, tableH - bottomPx)
                }
                TableStyle.DEFAULT -> {
                    rules[0].place(0, headerH)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Cell content
// ─────────────────────────────────────────────────────────────

@Composable
private fun TableCellContent(
    content: String,
    node: ASTNode,
    color: Color,
    isHeader: Boolean,
    headerWeight: FontWeight,
) {
    val raw = node.getTextInNode(content).toString().trim()
    val segments = remember(raw) { segmentCellText(raw) }
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = CELL_PADDING_H, vertical = CELL_PADDING_V),
        contentAlignment = Alignment.Center,
    ) {
        if (segments.size == 1 && segments[0] is CellTextSegment.TextRun) {
            Text(
                text = (segments[0] as CellTextSegment.TextRun).text,
                modifier = Modifier.fillMaxWidth(),
                color = color,
                fontSize = CELL_FONT_SIZE,
                fontWeight = if (isHeader) headerWeight else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (segment in segments) {
                    when (segment) {
                        is CellTextSegment.TextRun -> Text(
                            text = segment.text,
                            color = color,
                            fontSize = CELL_FONT_SIZE,
                            fontWeight = if (isHeader) headerWeight else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        )
                        is CellTextSegment.MathRun -> {
                            // displayMode is part of the cache key: `$x$` and `$$x$$` parse differently.
                            val displayList = remember(segment.formula, segment.displayMode, color) {
                                try {
                                    RaTeXEngine.parseBlocking(segment.formula, segment.displayMode, color = color)
                                } catch (_: Throwable) {
                                    // UnsatisfiedLinkError (missing native lib) is an Error, not an
                                    // Exception — catching Throwable here is deliberate so the cell
                                    // degrades to literal text instead of crashing the renderer.
                                    null
                                }
                            }
                            if (displayList != null) {
                                RaTeX(displayList = displayList, fontSize = CELL_FONT_SIZE)
                            } else {
                                // Parse failure must not silently drop content: render the raw formula.
                                Text(
                                    text = if (segment.displayMode) {
                                        "\$\$${segment.formula}\$\$"
                                    } else {
                                        "\$${segment.formula}\$"
                                    },
                                    color = color,
                                    fontSize = CELL_FONT_SIZE,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal sealed class CellTextSegment {
    data class TextRun(val text: String) : CellTextSegment()
    data class MathRun(val formula: String, val displayMode: Boolean) : CellTextSegment()
}

/** Formulas longer than this are rendered as literal text (bounded RaTeX parse cost). */
internal const val MAX_FORMULA_LENGTH = 300

/** Hard budget on the number of segments per cell; overflow is dumped as plain text. */
internal const val MAX_CELL_SEGMENTS = 64

/**
 * Splits a cell on `$…$` / `$$…$$`; unterminated `$` stays literal text. Formula
 * length is capped at [MAX_FORMULA_LENGTH] and the segment count at [MAX_CELL_SEGMENTS]
 * so pathological input cannot trigger unbounded work on the UI thread.
 */
internal fun segmentCellText(raw: String): List<CellTextSegment> {
    val segments = mutableListOf<CellTextSegment>()
    var position = 0
    while (position < raw.length) {
        val dollar = raw.indexOf('$', position)
        if (dollar < 0) {
            segments.add(CellTextSegment.TextRun(raw.substring(position)))
            break
        }
        if (dollar > position) segments.add(CellTextSegment.TextRun(raw.substring(position, dollar)))
        if (segments.size >= MAX_CELL_SEGMENTS) {
            segments.add(CellTextSegment.TextRun(raw.substring(dollar)))
            break
        }
        if (dollar + 1 < raw.length && raw[dollar + 1] == '$') {
            val close = raw.indexOf("$$", dollar + 2)
            if (close >= 0) {
                val formula = raw.substring(dollar + 2, close).trim()
                if (formula.length <= MAX_FORMULA_LENGTH) {
                    segments.add(CellTextSegment.MathRun(formula, displayMode = true))
                } else {
                    segments.add(CellTextSegment.TextRun("\$\$$formula\$\$"))
                }
                position = close + 2
            } else {
                segments.add(CellTextSegment.TextRun(raw.substring(dollar)))
                break
            }
        } else {
            val close = raw.indexOf('$', dollar + 1)
            if (close >= 0) {
                val formula = raw.substring(dollar + 1, close).trim()
                if (formula.length <= MAX_FORMULA_LENGTH) {
                    segments.add(CellTextSegment.MathRun(formula, displayMode = false))
                } else {
                    segments.add(CellTextSegment.TextRun("\$$formula\$"))
                }
                position = close + 1
            } else {
                segments.add(CellTextSegment.TextRun(raw.substring(dollar)))
                break
            }
        }
    }
    return segments
}

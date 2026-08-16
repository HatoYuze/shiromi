package com.github.hatoyuze.luogu.gui.presentation.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.LocalMarkdownColors
import com.mikepenz.markdown.compose.LocalMarkdownDimens
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.model.MarkdownTypography
import io.ratex.RaTeXEngine
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

// ═══════════════════════════════════════════════════════
// Default table component (includes MathAwareParagraph, no table override)
// ═══════════════════════════════════════════════════════

/** Library's built-in table renderer with MathAwareParagraph for LaTeX support. */
val defaultTableComponent by lazy {
    markdownComponents(
        codeBlock = highlightedCodeBlock,
        codeFence = highlightedCodeFence,
        paragraph = { MathAwareParagraph(it) },
    ).table
}

// ═══════════════════════════════════════════════════════
// CuteTableAwareTable — table override entry point
// ═══════════════════════════════════════════════════════

/**
 * Drop-in `table` slot for [markdownComponents].
 * Detects `::cute-table{tuack}` prefix in raw content and routes to
 * [CuteTableContent] (styled) or [defaultTableComponent] (standard).
 */
@Composable
fun ColumnScope.CuteTableAwareTable(model: MarkdownComponentModel) {
    val content = model.content
    val tableStart = model.node.startOffset
    val prefix = "::cute-table{tuack}"

    val isCute = remember(tableStart, content) {
        val lastIdx = content.lastIndexOf(prefix, tableStart.coerceAtLeast(0))
        lastIdx >= 0 && content.substring(lastIdx + prefix.length, tableStart).all { it.isWhitespace() }
    }

    if (isCute) {
        CuteTableContent(tableNode = model.node, content = model.content, typography = model.typography)
    } else {
        defaultTableComponent.invoke(this, model)
    }
}

// ═══════════════════════════════════════════════════════
// CuteTableContent — three-line styled table with ^ rowspan
// ═══════════════════════════════════════════════════════

private val CELL_PADDING_H = 12.dp
private val CELL_PADDING_V = 6.dp
private val CELL_FONT_SIZE = 16.sp

@Composable
private fun CuteTableContent(
    tableNode: ASTNode,
    content: String,
    typography: MarkdownTypography,
    modifier: Modifier = Modifier,
) {
    val grid = remember(tableNode) { parseTableGrid(tableNode, content) }
    if (grid.header.isEmpty() || grid.columnCount == 0) return

    val lineColor = LocalMarkdownColors.current.text
    val minCellWidth = LocalMarkdownDimens.current.tableCellWidth
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val numCols = grid.columnCount
    val numDataRows = grid.rows.size

    val thickPx = with(density) { 4.dp.roundToPx() }
    val mediumPx = with(density) { 1.5.dp.roundToPx() }
    val minW = with(density) { minCellWidth.roundToPx() }

    // ── Flatten all cells + dividers into indexed slots ──
    data class Slot(val kind: String, val row: Int = 0, val col: Int = 0)
    val slots = mutableListOf<Slot>()
    for (c in 0 until numCols) slots.add(Slot("hdr", 0, c))
    for (r in 0 until numDataRows)
        for (c in 0 until numCols)
            if (grid.rows[r].getOrNull(c) != null)
                slots.add(Slot("cel", r, c))
    slots.add(Slot("top"))
    slots.add(Slot("hsep"))
    slots.add(Slot("bot"))

    Layout(
        modifier = modifier.horizontalScroll(scrollState),
        content = {
            for (s in slots) {
                when (s.kind) {
                    "hdr" -> Box { CuteTableCellContent(content, grid.header[s.col], lineColor, isHeader = true) }
                    "cel" -> {
                        val cell = grid.rows[s.row][s.col]!!
                        Box { CuteTableCellContent(content, cell.astNode, lineColor) }
                    }
                    "top" -> Box(Modifier.height(4.dp).background(lineColor))
                    "hsep" -> Box(Modifier.height(1.5.dp).background(lineColor))
                    "bot" -> Box(Modifier.height(4.dp).background(lineColor))
                }
            }
        }
    ) { measurables, constraints ->
        val loose = measurables.map { it.measure(Constraints()) }

        // ── Column widths ──
        val colWidths = IntArray(numCols) { col ->
            val ws = slots.mapIndexedNotNull { i, s ->
                if ((s.kind == "hdr" || s.kind == "cel") && s.col == col) loose[i].width else null
            }
            maxOf(minW, ws.maxOrNull() ?: minW)
        }
        val tableW = colWidths.sum()

        // ── Header height ──
        val headerH = (0 until numCols).maxOf { c ->
            slots.indexOfFirst { it.kind == "hdr" && it.col == c }.let { if (it >= 0) loose[it].height else 0 }
        }

        // ── Base row heights (rowspan==1 cells) ──
        val baseRH = IntArray(numDataRows) { row ->
            val hs = (0 until numCols).mapNotNull { col ->
                val cell = grid.rows[row].getOrNull(col)
                if (cell != null && cell.rowspan == 1) {
                    slots.indexOfFirst { it.kind == "cel" && it.row == row && it.col == col }
                        .let { if (it >= 0) loose[it].height else null }
                } else null
            }
            hs.maxOrNull() ?: CELL_FONT_SIZE.value.toInt()
        }

        // ── Shortage iteration ──
        var changed = true; var iter = 0
        while (changed && iter < 10) { changed = false; iter++
            for (row in 0 until numDataRows)
                for (col in 0 until numCols) {
                    val cell = grid.rows[row].getOrNull(col) ?: continue
                    if (cell.rowspan <= 1) continue
                    val span = cell.rowspan.coerceAtMost(numDataRows - row)
                    val alloc = (0 until span).sumOf { baseRH[row + it] }
                    val i = slots.indexOfFirst { it.kind == "cel" && it.row == row && it.col == col }
                    if (i < 0) continue
                    val nat = loose[i].height
                    if (nat > alloc) {
                        val per = (nat - alloc) / span; val rem = (nat - alloc) % span
                        for (d in 0 until span) baseRH[row + d] += per + (if (d < rem) 1 else 0)
                        changed = true
                    }
                }
        }

        // ── Total row heights ──
        val totalRH = IntArray(numDataRows) { r -> baseRH[r] }
        val tableH = thickPx + headerH + mediumPx + totalRH.sum() + thickPx

        // ── Precise measurement ──
        val precise = measurables.mapIndexed { i, m ->
            val s = slots[i]
            when (s.kind) {
                "hdr" -> m.measure(Constraints(maxWidth = colWidths[s.col], maxHeight = headerH))
                "cel" -> {
                    val cell = grid.rows[s.row][s.col]!!
                    val h = if (cell.rowspan > 1)
                        (0 until cell.rowspan.coerceAtMost(numDataRows - s.row)).sumOf { totalRH[s.row + it] }
                    else totalRH[s.row]
                    m.measure(Constraints(maxWidth = colWidths[s.col], maxHeight = h))
                }
                "top", "hsep", "bot" -> m.measure(Constraints(maxWidth = tableW))
                else -> m.measure(Constraints())
            }
        }

        val colX = IntArray(numCols + 1) { i -> (0 until i).sumOf { colWidths[it] } }

        // ── Place ──
        layout(tableW, tableH) {
            for (i in precise.indices) {
                val s = slots[i]; val p = precise[i]
                when (s.kind) {
                    "top" -> p.place(0, 0)
                    "hsep" -> p.place(0, thickPx + headerH)
                    "bot" -> p.place(0, tableH - thickPx)
                    "hdr" -> p.place(colX[s.col], thickPx)
                    "cel" -> {
                        var y = thickPx + headerH + mediumPx
                        for (r in 0 until s.row) y += totalRH[r]
                        p.place(colX[s.col], y)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// CuteTableCellContent — inline content with LaTeX math
// ═══════════════════════════════════════════════════════

@Composable
private fun CuteTableCellContent(
    content: String,
    cellNode: ASTNode,
    color: androidx.compose.ui.graphics.Color,
    isHeader: Boolean = false,
) {
    val raw = cellNode.getTextInNode(content).toString().trim()
    val segments = remember(raw) { segmentCellText(raw) }

    if (segments.size == 1 && segments[0] is CellTextSegment.TextRun) {
        Text(
            text = (segments[0] as CellTextSegment.TextRun).text,
            color = color, fontSize = CELL_FONT_SIZE, textAlign = TextAlign.Center,
        )
    } else {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = CELL_PADDING_H, vertical = CELL_PADDING_V),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            segments.forEach { seg ->
                when (seg) {
                    is CellTextSegment.TextRun -> Text(
                        text = seg.text, color = color,
                        fontSize = CELL_FONT_SIZE, textAlign = TextAlign.Center,
                    )
                    is CellTextSegment.MathRun -> {
                        val dl = remember(seg.formula, color) {
                            try { RaTeXEngine.parseBlocking(seg.formula, seg.displayMode, color = color) }
                            catch (_: Exception) { null }
                        }
                        BaselineAlignedRaTeX(
                            latex = seg.formula, displayMode = seg.displayMode,
                            fontSize = CELL_FONT_SIZE, color = color, precomputedDisplayList = dl,
                        )
                    }
                }
            }
        }
    }
}

private sealed class CellTextSegment {
    data class TextRun(val text: String) : CellTextSegment()
    data class MathRun(val formula: String, val displayMode: Boolean) : CellTextSegment()
}

private fun segmentCellText(raw: String): List<CellTextSegment> {
    val out = mutableListOf<CellTextSegment>()
    var p = 0
    while (p < raw.length) {
        val d = raw.indexOf('$', p)
        if (d < 0) { out.add(CellTextSegment.TextRun(raw.substring(p))); break }
        if (d > p) out.add(CellTextSegment.TextRun(raw.substring(p, d)))
        if (d + 1 < raw.length && raw[d + 1] == '$') {
            val c = raw.indexOf("$$", d + 2)
            if (c >= 0) {
                out.add(CellTextSegment.MathRun(raw.substring(d + 2, c).trim(), true))
                p = c + 2
            } else p = d + 2
        } else {
            val c = raw.indexOf('$', d + 1)
            if (c >= 0) {
                out.add(CellTextSegment.MathRun(raw.substring(d + 1, c).trim(), false))
                p = c + 1
            } else p = d + 1
        }
    }
    return out
}

// ═══════════════════════════════════════════════════════
// Table AST parsing
// ═══════════════════════════════════════════════════════

private data class CuteTableCell(val astNode: ASTNode, val rowspan: Int)

private data class CuteTableGrid(
    val header: List<ASTNode>,
    val rows: List<List<CuteTableCell?>>,
    val columnCount: Int,
)

private fun parseTableGrid(tableNode: ASTNode, content: String): CuteTableGrid {
    val children = tableNode.children.toList()
    val headerNode = children.find { it.type == GFMElementTypes.HEADER }
    val headerCells = if (headerNode != null) {
        children.filter {
            it.type == GFMTokenTypes.CELL && it.startOffset >= headerNode.startOffset && it.endOffset <= headerNode.endOffset
        }
    } else emptyList()
    val columnCount = headerCells.size
    if (columnCount == 0) return CuteTableGrid(emptyList(), emptyList(), 0)

    val rowNodes = children.filter { it.type == GFMElementTypes.ROW }
    val rawRows: List<List<ASTNode>> = rowNodes.map { row ->
        children.filter {
            it.type == GFMTokenTypes.CELL && it.startOffset >= row.startOffset && it.endOffset <= row.endOffset
        }
    }
    val rawText: List<List<String>> = rawRows.map { row ->
        row.map { cell -> content.substring(cell.startOffset, cell.endOffset).trim() }
    }
    val resolved: List<MutableList<CuteTableCell?>> = rawRows.map { row ->
        row.map { CuteTableCell(it, rowspan = 1) }.toMutableList()
    }
    for (col in 0 until columnCount) {
        for (row in 0 until rawRows.size) {
            if (col >= rawText[row].size) continue
            if (rawText[row][col].trim() == "^" && row > 0) {
                var pr = row - 1
                while (pr >= 0 && col < rawText[pr].size && rawText[pr][col].trim() == "^") pr--
                if (pr >= 0) {
                    val pc = resolved[pr].getOrNull(col)
                    if (pc != null) {
                        resolved[pr][col] = pc.copy(rowspan = pc.rowspan + 1)
                        resolved[row][col] = null
                    }
                }
            }
        }
    }
    return CuteTableGrid(
        header = headerCells,
        rows = resolved.map { r -> (0 until columnCount).map { r.getOrNull(it) } },
        columnCount = columnCount,
    )
}

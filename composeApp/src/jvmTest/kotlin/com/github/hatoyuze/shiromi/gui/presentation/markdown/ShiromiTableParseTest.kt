// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.markdown

import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.MAX_CELL_SEGMENTS
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.MAX_FORMULA_LENGTH
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.CellTextSegment
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.isThreeLineTable
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.parseTableGrid
import com.github.hatoyuze.shiromi.gui.presentation.components.markdown.segmentCellText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.parser.MarkdownParser

class ShiromiTableParseTest {

    private fun parse(markdown: String): ASTNode =
        MarkdownParser(FoldableFlavourDescriptor()).buildMarkdownTreeFromString(markdown)

    private fun tableNodeOf(markdown: String): ASTNode =
        parse(markdown).children.first { it.type == GFMElementTypes.TABLE }

    // ═══════════════════════════════════════════════════════
    // Structural three-line detection
    // ═══════════════════════════════════════════════════════

    @Test
    fun threeLineMarker_canonicalForm_recognized() {
        val node = tableNodeOf(
            """
            ::cute-table{tuack}
            | A | B |
            | --- | --- |
            | 1 | 2 |
            """.trimIndent(),
        )
        assertTrue(isThreeLineTable(node))
    }

    @Test
    fun threeLineMarker_trailingColons_recognized() {
        val node = tableNodeOf(
            """
            ::cute-table{tuack}::
            | A | B |
            | --- | --- |
            | 1 | 2 |
            """.trimIndent(),
        )
        assertTrue(isThreeLineTable(node))
    }

    @Test
    fun threeLineMarker_leadingWhitespace_recognized() {
        val node = tableNodeOf(
            """
              ::cute-table{tuack}
            | A | B |
            | --- | --- |
            | 1 | 2 |
            """.trimIndent(),
        )
        assertTrue(isThreeLineTable(node))
    }

    @Test
    fun threeLineMarker_anyVariant_recognized() {
        // The parser consumes any ::cute-table{…} directive; detection must not
        // depend on the variant spelling (tuack is canonical, truck is tolerated).
        val node = tableNodeOf(
            """
            ::cute-table{truck}
            | A | B |
            | --- | --- |
            | 1 | 2 |
            """.trimIndent(),
        )
        assertTrue(isThreeLineTable(node))
    }

    @Test
    fun plainTable_notRecognized() {
        val node = tableNodeOf(
            """
            | A | B |
            | --- | --- |
            | 1 | 2 |
            """.trimIndent(),
        )
        assertFalse(isThreeLineTable(node))
    }

    @Test
    fun threeLineMarker_blankLineBetween_stillRecognized() {
        // The marker applies across blank lines: only EOL nodes separate it from the table.
        val node = tableNodeOf(
            """
            ::cute-table{tuack}

            | A | B |
            | --- | --- |
            | 1 | 2 |
            """.trimIndent(),
        )
        assertTrue(isThreeLineTable(node))
    }

    @Test
    fun detachedMarker_paragraphBetween_swallowsTable() {
        // GFM semantics: a table line directly after paragraph text is absorbed into the
        // paragraph, so no TABLE node exists and the marker has nothing to style.
        val root = parse(
            """
            ::cute-table{tuack}
            说明文字
            | A | B |
            | --- | --- |
            | 1 | 2 |
            """.trimIndent(),
        )
        assertTrue(root.children.none { it.type == GFMElementTypes.TABLE })
    }

    // ═══════════════════════════════════════════════════════
    // Grid parsing and '^' rowspan merging
    // ═══════════════════════════════════════════════════════

    @Test
    fun grid_basic_parsesHeaderAndRows() {
        val markdown = """
            | A | B | C |
            | --- | --- | --- |
            | 1 | 2 | 3 |
            | 4 | 5 | 6 |
        """.trimIndent()
        val node = tableNodeOf(markdown)
        val grid = parseTableGrid(node, markdown)

        assertEquals(3, grid.columnCount)
        assertEquals(3, grid.header.size)
        assertEquals(2, grid.rows.size)
        assertEquals(listOf("A", "B", "C"), grid.header.map { cellText(it, markdown) })
        assertEquals("1", cellText(assertNotNull(grid.rows[0][0]), markdown))
        assertEquals("6", cellText(assertNotNull(grid.rows[1][2]), markdown))
        // Every live cell starts with rowspan 1.
        assertTrue(grid.rows.flatten().filterNotNull().all { it.rowspan == 1 })
    }

    @Test
    fun caret_singleMerge_extendsUpperCell() {
        val markdown = """
            | A | B |
            | --- | --- |
            | x | y |
            | p | ^ |
        """.trimIndent()
        val grid = parseTableGrid(tableNodeOf(markdown), markdown)

        val upper = assertNotNull(grid.rows[0][1])
        assertEquals("y", cellText(upper, markdown))
        assertEquals(2, upper.rowspan)
        assertNull(grid.rows[1][1], "merged-away slot must be null")
    }

    @Test
    fun caret_chain_mergesIntoTopCell() {
        val markdown = """
            | A | B |
            | --- | --- |
            | top | 1 |
            | ^ | 2 |
            | ^ | 3 |
        """.trimIndent()
        val grid = parseTableGrid(tableNodeOf(markdown), markdown)

        val top = assertNotNull(grid.rows[0][0])
        assertEquals("top", cellText(top, markdown))
        assertEquals(3, top.rowspan)
        assertNull(grid.rows[1][0])
        assertNull(grid.rows[2][0])
    }

    @Test
    fun caret_firstBodyRow_staysLiteral() {
        val markdown = """
            | A | B |
            | --- | --- |
            | ^ | 1 |
            | ^ | 2 |
        """.trimIndent()
        val grid = parseTableGrid(tableNodeOf(markdown), markdown)

        val first = assertNotNull(grid.rows[0][0])
        val second = assertNotNull(grid.rows[1][0])
        assertEquals("^", cellText(first, markdown))
        assertEquals("^", cellText(second, markdown))
        assertEquals(1, first.rowspan)
        assertEquals(1, second.rowspan)
    }

    @Test
    fun caret_inHeader_staysLiteral() {
        val markdown = """
            | ^ | B |
            | --- | --- |
            | 1 | 2 |
        """.trimIndent()
        val grid = parseTableGrid(tableNodeOf(markdown), markdown)

        assertEquals(2, grid.columnCount)
        assertEquals("^", cellText(grid.header[0], markdown))
        assertNotNull(grid.rows[0][0])
    }

    @Test
    fun shortRow_isPaddedWithNulls() {
        val markdown = """
            | A | B | C |
            | --- | --- | --- |
            | 1 | 2 |
        """.trimIndent()
        val grid = parseTableGrid(tableNodeOf(markdown), markdown)

        assertEquals(3, grid.rows[0].size)
        assertNotNull(grid.rows[0][0])
        assertNotNull(grid.rows[0][1])
        assertNull(grid.rows[0][2])
    }

    @Test
    fun mathCell_textIsPreserved() {
        val markdown = """
            | 公式 | 含义 |
            | --- | --- |
            | `${'$'}a^2+b^2=c^2${'$'}` | 勾股 |
        """.trimIndent()
        val grid = parseTableGrid(tableNodeOf(markdown), markdown)

        val cell = assertNotNull(grid.rows[0][0])
        assertEquals("`${'$'}a^2+b^2=c^2${'$'}`", cellText(cell, markdown))
    }

    @Test
    fun noTable_returnsNoTableNode() {
        val markdown = "没有表格的文本"
        val root = parse(markdown)
        assertTrue(root.children.none { it.type == GFMElementTypes.TABLE })
    }

    // ═══════════════════════════════════════════════════════
    // Adversarial inputs (security regression)
    // ═══════════════════════════════════════════════════════

    @Test
    fun caret_longChain_mergesInOnePass() {
        // 150 rows of '^' must resolve in a single pass — no quadratic walk-up.
        val body = buildString {
            append("| 顶 | 值 |\n")
            append("| --- | --- |\n")
            append("| x | 1 |\n")
            repeat(149) { append("| ^ | 2 |\n") }
        }
        val markdown = body.trimEnd()
        val grid = parseTableGrid(tableNodeOf(markdown), markdown)

        val top = assertNotNull(grid.rows[0][0])
        assertEquals("x", cellText(top, markdown))
        assertEquals(150, top.rowspan)
        for (r in 1 until 150) assertNull(grid.rows[r][0])
    }

    @Test
    fun caret_mixedChain_resetsAtRealCell() {
        // A real cell below a literal '^' resets the chain; merges resume below it.
        val markdown = """
            | A | B |
            | --- | --- |
            | ^ | 1 |
            | x | 2 |
            | ^ | 3 |
            | ^ | 4 |
        """.trimIndent()
        val grid = parseTableGrid(tableNodeOf(markdown), markdown)

        // Row 0 '^' stays literal; rows 2-3 merge into row 1's 'x'.
        assertEquals("^", cellText(assertNotNull(grid.rows[0][0]), markdown))
        assertEquals("x", cellText(assertNotNull(grid.rows[1][0]), markdown))
        assertEquals(3, assertNotNull(grid.rows[1][0]).rowspan)
        assertNull(grid.rows[2][0])
        assertNull(grid.rows[3][0])
    }

    @Test
    fun hugeGrid_smokeParses() {
        val header = "| " + (0 until 12).joinToString(" | ") { "列$it" } + " |"
        val separator = "| " + (0 until 12).joinToString(" | ") { "---" } + " |"
        val body = (0 until 40).joinToString("\n") { r ->
            "| " + (0 until 12).joinToString(" | ") { c -> if (c == 0) "行$r" else (r * 12 + c).toString() } + " |"
        }
        val markdown = "$header\n$separator\n$body"
        val grid = parseTableGrid(tableNodeOf(markdown), markdown)

        assertEquals(12, grid.columnCount)
        assertEquals(40, grid.rows.size)
        assertEquals("行0", cellText(assertNotNull(grid.rows[0][0]), markdown))
        assertEquals("479", cellText(assertNotNull(grid.rows[39][11]), markdown))
    }

    @Test
    fun segmenter_oversizedFormula_rendersAsText() {
        val longFormula = "x".repeat(MAX_FORMULA_LENGTH + 1)
        val segments = segmentCellText("\$$longFormula\$")
        assertEquals(1, segments.size)
        assertTrue(segments[0] is CellTextSegment.TextRun, "oversized formula must stay literal text")
        assertEquals("\$$longFormula\$", (segments[0] as CellTextSegment.TextRun).text)
    }

    @Test
    fun segmenter_dollarStorm_segmentsBounded() {
        val storm = buildString {
            repeat(200) { append("\$a^2\$ ") }
        }
        val segments = segmentCellText(storm)
        assertTrue(segments.size <= MAX_CELL_SEGMENTS + 1, "segment budget must bound '$' storms")
        // Reassemble: no content may be dropped by the budget cut (only the tail becomes one TextRun).
        val joined = segments.joinToString("") { seg ->
            when (seg) {
                is CellTextSegment.TextRun -> seg.text
                is CellTextSegment.MathRun -> "\$${seg.formula}\$"
            }
        }
        assertEquals(storm.trim(), joined.trim())
    }

    private fun cellText(cell: com.github.hatoyuze.shiromi.gui.presentation.components.markdown.TableCell, content: String): String =
        content.substring(cell.node.startOffset, cell.node.endOffset).trim()
}

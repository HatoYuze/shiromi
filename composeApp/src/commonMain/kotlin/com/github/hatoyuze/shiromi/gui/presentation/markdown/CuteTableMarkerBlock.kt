// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.constraints.eatItselfFromString
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider

/**
 * A [MarkerBlock] that consumes a single `::cute-table{variant}` directive line.
 * The directive text is emitted as a [CuteTableElementTypes.CUTE_TABLE_DIRECTIVE] token
 * inside a [CuteTableElementTypes.CUTE_TABLE_MARKER] node.
 *
 * [allowsSubBlocks] is `false` — the following GFM table remains an independent
 * top-level AST node, rendered by the `table` component which detects the directive
 * prefix in the raw content and applies cute-table styling.
 */
class CuteTableMarkerBlock(
    pos: LookaheadText.Position,
    constraints: MarkdownConstraints,
    private val productionHolder: ProductionHolder,
) : MarkerBlockImpl(constraints, productionHolder.mark()) {

    init {
        val endOffset = pos.nextLineOrEofOffset
        productionHolder.addProduction(
            listOf(SequentialParser.Node(pos.offset..endOffset, CuteTableElementTypes.CUTE_TABLE_DIRECTIVE))
        )
    }

    override fun allowsSubBlocks(): Boolean = false

    override fun isInterestingOffset(pos: LookaheadText.Position): Boolean = true

    override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int = pos.nextLineOrEofOffset

    override fun getDefaultAction(): MarkerBlock.ClosingAction = MarkerBlock.ClosingAction.DONE

    override fun getDefaultNodeType(): IElementType = CuteTableElementTypes.CUTE_TABLE_MARKER

    override fun doProcessToken(
        pos: LookaheadText.Position,
        currentConstraints: MarkdownConstraints,
    ): MarkerBlock.ProcessingResult = MarkerBlock.ProcessingResult.DEFAULT
}

class CuteTableMarkerBlockProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {
    override fun createMarkerBlocks(
        pos: LookaheadText.Position,
        productionHolder: ProductionHolder,
        stateInfo: MarkerProcessor.StateInfo,
    ): List<MarkerBlock> {
        val currentConstraints = stateInfo.currentConstraints
        if (stateInfo.nextConstraints != currentConstraints) return emptyList()
        val line = pos.currentLineFromPosition.toString()
        if (!line.trimStart().startsWith("::cute-table{")) return emptyList()
        return listOf(CuteTableMarkerBlock(pos, currentConstraints, productionHolder))
    }

    override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean {
        return pos.currentLineFromPosition.toString().trimStart().startsWith("::cute-table")
    }
}

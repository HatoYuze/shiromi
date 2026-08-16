package com.github.hatoyuze.luogu.gui.presentation.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.flavours.gfm.GFMConstraints
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.constraints.eatItselfFromString
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.sequentialparsers.SequentialParser

data class FoldableHeaderInfo(
    val type: String,
    val title: String,
    val isOpen: Boolean
) {
    companion object {
        fun parse(line: String): FoldableHeaderInfo? {
            if (!line.startsWith("::::")) return null
            val afterColons = line.substring(4).trimStart()
            if (afterColons.isEmpty()) return null
            val bracketIdx = afterColons.indexOf('[')
            if (bracketIdx <= 0) return null
            val type = afterColons.substring(0, bracketIdx).trim()
            if (type !in setOf("info", "success", "warning", "error")) return null
            val afterType = afterColons.substring(bracketIdx + 1)
            val closeBracketIdx = findMatchingCloseBracket(afterType)
            if (closeBracketIdx < 0) return null
            val title = afterType.substring(0, closeBracketIdx).trim()
            val afterTitle = afterType.substring(closeBracketIdx + 1).trimStart()
            val isOpen = afterTitle.startsWith("{") && afterTitle.contains("open")
            return FoldableHeaderInfo(type = type, title = title, isOpen = isOpen)
        }
        private fun findMatchingCloseBracket(s: String): Int {
            var depth = 0
            for (i in s.indices) { when (s[i]) { '[' -> depth++; ']' -> { if (depth == 0) return i; depth-- } } }
            return -1
        }
    }
}

class FoldableMarkerBlock(
    pos: LookaheadText.Position, constraints: MarkdownConstraints, private val productionHolder: ProductionHolder,
) : MarkerBlockImpl(constraints, productionHolder.mark()) {
    private var hasProcessedOpening = false
    private val fenceEndText = "::::"
    init {
        val endOffset = pos.nextLineOrEofOffset
        productionHolder.addProduction(listOf(SequentialParser.Node(pos.offset..endOffset, FoldableElementTypes.FOLDABLE_HEADER)))
    }
    override fun allowsSubBlocks(): Boolean = true
    override fun isInterestingOffset(pos: LookaheadText.Position): Boolean = true
    override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int = pos.nextLineOrEofOffset
    override fun getDefaultAction(): MarkerBlock.ClosingAction = MarkerBlock.ClosingAction.DONE
    override fun getDefaultNodeType(): IElementType = FoldableElementTypes.FOLDABLE_BLOCK
    override fun doProcessToken(pos: LookaheadText.Position, currentConstraints: MarkdownConstraints): MarkerBlock.ProcessingResult {
        if (pos.offsetInCurrentLine != -1) return MarkerBlock.ProcessingResult.PASS
        if (!hasProcessedOpening) { hasProcessedOpening = true
            val line = currentConstraints.eatItselfFromString(pos.currentLine).toString().trim()
            return if (line == fenceEndText) MarkerBlock.ProcessingResult.DEFAULT else MarkerBlock.ProcessingResult.PASS
        }
        val line = currentConstraints.eatItselfFromString(pos.currentLine).toString().trim()
        return if (line == fenceEndText) MarkerBlock.ProcessingResult.DEFAULT else MarkerBlock.ProcessingResult.PASS
    }
}

class FoldableMarkerBlockProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {
    override fun createMarkerBlocks(pos: LookaheadText.Position, productionHolder: ProductionHolder, stateInfo: MarkerProcessor.StateInfo): List<MarkerBlock> {
        val currentConstraints = stateInfo.currentConstraints
        if (stateInfo.nextConstraints != currentConstraints) return emptyList()
        val line = pos.currentLineFromPosition.toString()
        if (FoldableHeaderInfo.parse(line) == null) return emptyList()
        return listOf(FoldableMarkerBlock(pos, currentConstraints, productionHolder))
    }
    override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean {
        val line = pos.currentLineFromPosition.toString().trimStart()
        return line.startsWith("::::")
    }
}

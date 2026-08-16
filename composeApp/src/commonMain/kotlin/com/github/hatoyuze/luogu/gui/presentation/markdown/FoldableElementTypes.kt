package com.github.hatoyuze.luogu.gui.presentation.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType

object FoldableElementTypes {
    val FOLDABLE_BLOCK: IElementType = MarkdownElementType("FOLDABLE_BLOCK")
    val FOLDABLE_HEADER: IElementType = MarkdownElementType("FOLDABLE_HEADER", isToken = true)
}

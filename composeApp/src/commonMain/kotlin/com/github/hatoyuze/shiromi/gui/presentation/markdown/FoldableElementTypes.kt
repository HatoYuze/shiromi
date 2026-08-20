// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType

object FoldableElementTypes {
    val FOLDABLE_BLOCK: IElementType = MarkdownElementType("FOLDABLE_BLOCK")
    val FOLDABLE_HEADER: IElementType = MarkdownElementType("FOLDABLE_HEADER", isToken = true)
}

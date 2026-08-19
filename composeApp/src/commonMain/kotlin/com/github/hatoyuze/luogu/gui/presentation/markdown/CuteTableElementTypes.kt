// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType

object CuteTableElementTypes {
    /** Zero-size marker node: consumes `::cute-table{tuack}` text so it does not render as a paragraph. */
    val CUTE_TABLE_MARKER: IElementType = MarkdownElementType("CUTE_TABLE_MARKER")
    /** Token containing the raw directive text. */
    val CUTE_TABLE_DIRECTIVE: IElementType = MarkdownElementType("CUTE_TABLE_DIRECTIVE", isToken = true)
}

// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 贴底判定纯逻辑测试（[ScrollProbe.isAtBottom]）。
 *
 * 语义：贴底 = 最后一条消息可见 且 内容下方没有可滚内容（canScrollForward=false）。
 * 覆盖：空列表、贴底、最后一条可见但内容下方还有内容（高消息只露开头）、
 * 上翻到首条、长列表中部、内容未溢出、单条高消息内部滚动。
 */
class ChatAutoScrollTest {

    @Test
    fun emptyList_isAtBottom() {
        assertTrue(ScrollProbe(0, 0, contentBelowViewport = false).isAtBottom())
        assertTrue(ScrollProbe(0, 0, contentBelowViewport = true).isAtBottom())
    }

    @Test
    fun atContentEnd_follows() {
        // 最后一条可见且下方无内容 → 贴底
        assertTrue(ScrollProbe(9, 10, contentBelowViewport = false).isAtBottom())
        assertTrue(ScrollProbe(1, 2, contentBelowViewport = false).isAtBottom())
        assertTrue(ScrollProbe(0, 1, contentBelowViewport = false).isAtBottom())
    }

    @Test
    fun contentBelowButLastItemVisible_doesNotFollow() {
        // 回归：最后一条（高消息）可见但内容下方还有内容（只露出开头）→ 不贴底，
        // 防止松手时被拽到底部（旧「上滑卡」bug）。
        assertFalse(ScrollProbe(1, 2, contentBelowViewport = true).isAtBottom())
        assertFalse(ScrollProbe(0, 1, contentBelowViewport = true).isAtBottom())
    }

    @Test
    fun scrolledUpToFirstItem_doesNotFollow() {
        // 2 条消息上翻到第一条（最后一条不可见）→ 不贴底（旧「下滑卡」bug）
        assertFalse(ScrollProbe(0, 2, contentBelowViewport = false).isAtBottom())
        assertFalse(ScrollProbe(0, 2, contentBelowViewport = true).isAtBottom())
    }

    @Test
    fun longListMid_doesNotFollow() {
        assertFalse(ScrollProbe(5, 100, contentBelowViewport = true).isAtBottom())
    }
}

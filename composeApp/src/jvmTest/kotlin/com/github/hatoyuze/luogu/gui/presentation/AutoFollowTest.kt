// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 智能贴底判定纯函数测试（移动端思考链滚动修复）：
 * 仅当最后可见项接近列表末尾时才自动跟随，上翻阅读不被打扰。
 */
class AutoFollowTest {

    @Test
    fun shouldAutoFollow_emptyList_returnsTrue() {
        assertTrue(shouldAutoFollow(lastVisibleIndex = 0, totalItemsCount = 0))
    }

    @Test
    fun shouldAutoFollow_atBottom_follows() {
        assertTrue(shouldAutoFollow(lastVisibleIndex = 9, totalItemsCount = 10))
        assertTrue(shouldAutoFollow(lastVisibleIndex = 8, totalItemsCount = 10))
    }

    @Test
    fun shouldAutoFollow_oneAwayFromBottom_stillFollows() {
        // 最后可见项 = 倒数第 2（index 8/10）→ 仅 1 项在视口下方，仍算「在底部附近」
        assertTrue(shouldAutoFollow(lastVisibleIndex = 8, totalItemsCount = 10))
    }

    @Test
    fun shouldAutoFollow_scrolledUp_doesNotFollow() {
        // 视口下方还有 2 项（index 7/10）→ 已上翻，不跟随
        assertFalse(shouldAutoFollow(lastVisibleIndex = 7, totalItemsCount = 10))
        assertFalse(shouldAutoFollow(lastVisibleIndex = 0, totalItemsCount = 10))
    }

    @Test
    fun shouldAutoFollow_singleItem_follows() {
        assertTrue(shouldAutoFollow(lastVisibleIndex = 0, totalItemsCount = 1))
    }

    @Test
    fun shouldAutoFollow_negativeTotal_neverHappensButSafe() {
        // totalItemsCount 为 0 时视为「应跟随」，不抛异常
        assertTrue(shouldAutoFollow(lastVisibleIndex = -1, totalItemsCount = 0))
    }
}

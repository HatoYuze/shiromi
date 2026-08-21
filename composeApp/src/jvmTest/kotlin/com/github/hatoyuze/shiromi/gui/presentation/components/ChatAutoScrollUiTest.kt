// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.hatoyuze.shiromi.gui.domain.model.ChatMessageDomainModel
import com.github.hatoyuze.shiromi.gui.domain.model.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 贴底跟随控制器的无头 UI 回归测试（Compose Desktop ui-test，无需 X 服务）。
 *
 * 用固定高度的两条消息制造内容溢出（视口 400dp < 内容 660dp），验证控制器
 * 的核心不变量：**滚动只由内容增长触发，用户上翻后内容增长绝不跳动**。
 */
@OptIn(ExperimentalTestApi::class)
class ChatAutoScrollUiTest {

    private fun msg(id: String, content: String) = ChatMessageDomainModel(
        id = id,
        sessionId = "s",
        content = content,
        isUser = false,
        status = MessageStatus.SENT,
        timestamp = 0L,
    )

    @Test
    fun scrollAwayThenContentGrows_doesNotJump() = runDesktopComposeUiTest {
        val messages = mutableStateListOf(msg("m1", "短消息"), msg("m2", "很长".repeat(200)))
        val pinnedEvents = mutableListOf<Boolean>()
        lateinit var listState: LazyListState

        setContent {
            listState = rememberLazyListState()
            rememberChatAutoScroll(listState, messages, streamFollowEnabled = true) { pinnedEvents += it }
            LazyColumn(
                state = listState,
                modifier = Modifier.height(400.dp).fillMaxWidth(),
            ) {
                items(messages) { m ->
                    Box(Modifier.fillMaxWidth().height(if (m.id == "m2") 600.dp else 60.dp))
                }
            }
        }

        // 初始：首次布局后应贴底（控制器首次无条件落到底部）
        waitForIdle()
        assertTrue(pinnedEvents.lastOrNull() ?: false, "初始应处于贴底状态")
        assertEquals(1, listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, "初始应看到最后一条消息")

        // 用户上翻到第一条 → unpinned
        listState.scrollToItem(0)
        waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex, "应已滚动到第一条")
        assertFalse(pinnedEvents.last(), "上翻后应退出贴底")

        // 同一条消息内容增长（流式）→ 已上翻，列表不得跳动（核心回归）
        messages[1] = msg("m2", "很长".repeat(300))
        waitForIdle()
        assertEquals(0, listState.firstVisibleItemIndex, "上翻后内容增长不应把列表拽回底部")

        // 回到底部（scrollOffset=MAX 对齐内容末尾）→ 重新 pinned
        listState.scrollToItem(1, Int.MAX_VALUE)
        waitForIdle()
        assertTrue(pinnedEvents.last(), "回到底部后应重新贴底")

        // 内容再增长 → 贴底跟随到尾部
        messages[1] = msg("m2", "很长".repeat(400))
        waitForIdle()
        assertEquals(1, listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, "贴底时内容增长应跟随到最新尾部")
    }

    @Test
    fun sessionSwitch_reanchorsToBottom() = runDesktopComposeUiTest {
        val messages = mutableStateListOf(msg("m1", "短消息"), msg("m2", "很长".repeat(200)))
        val pinnedEvents = mutableListOf<Boolean>()
        lateinit var listState: LazyListState

        setContent {
            listState = rememberLazyListState()
            rememberChatAutoScroll(listState, messages, streamFollowEnabled = true) { pinnedEvents += it }
            LazyColumn(
                state = listState,
                modifier = Modifier.height(400.dp).fillMaxWidth(),
            ) {
                items(messages) { m ->
                    Box(Modifier.fillMaxWidth().height(if (m.id.endsWith("2")) 600.dp else 60.dp))
                }
            }
        }

        // 旧会话：先贴底再上翻（模拟阅读历史）
        waitForIdle()
        listState.scrollToItem(0)
        waitForIdle()
        assertFalse(pinnedEvents.last(), "旧会话上翻后应退出贴底")

        // 切换会话（首条消息 id 变化）→ 控制器重置并重新落到底部
        messages.clear()
        messages.add(msg("n1", "新会话"))
        messages.add(msg("n2", "新内容很长".repeat(300)))
        waitForIdle()
        assertTrue(pinnedEvents.last(), "切换会话后应重新贴底")
        assertEquals(1, listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, "切换会话后应看到新会话最后一条")
    }
}

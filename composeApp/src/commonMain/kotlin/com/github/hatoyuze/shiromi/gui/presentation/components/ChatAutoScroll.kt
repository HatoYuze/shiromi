// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.github.hatoyuze.shiromi.gui.domain.model.ChatMessageDomainModel
import kotlinx.coroutines.flow.collect

/** 一次滚动位置快照（纯数据，可单测）。 */
internal data class ScrollProbe(
    val lastVisibleIndex: Int,
    val totalItemsCount: Int,
    /** 内容下方是否还有可滚内容（= [LazyListState.canScrollForward]）。 */
    val contentBelowViewport: Boolean,
)

/**
 * 贴底判定：最后一条消息可见，且内容下方没有可滚内容（已到内容末尾）。
 * 空列表视为贴底（无内容可滚，跟随是无操作）。
 *
 * 用 Compose 自带的 canScrollForward（测量结果维护）判定，不做任何坐标运算：
 * 天然免疫「高消息只露开头」「padding 偏移」「viewportStartOffset 语义」等边界。
 */
internal fun ScrollProbe.isAtBottom(): Boolean =
    totalItemsCount <= 0 || (lastVisibleIndex == totalItemsCount - 1 && !contentBelowViewport)

/** 由 [LazyListState] 生成当前滚动位置快照。 */
internal fun LazyListState.toScrollProbe(): ScrollProbe {
    val info = layoutInfo
    return ScrollProbe(
        lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index ?: 0,
        totalItemsCount = info.totalItemsCount,
        contentBelowViewport = canScrollForward,
    )
}

/**
 * 聊天列表「贴底跟随」控制器（参考 GetStream MessageList 的滚动句柄模式）。
 *
 * 核心原则：**滚动只由「内容增长」触发，绝不因用户滚动/松手触发**——
 * 用户上滑/下滑时列表从不被程序抢走，松手停哪算哪。
 *
 * - 「钉底」由独立观察器跟踪（只写 pinned 标志，不滚动）：贴底 = pinned；
 *   上翻阅读 = unpinned；滚回底部后自动恢复 pinned。
 * - 新消息到达（id 变化）→ 首次无条件贴底，之后仅 pinned 时贴底；
 * - 同一条消息流式增长 → pinned 时瞬时跟随尾部（`scrollToItem(total - 1,
 *   Int.MAX_VALUE)`——scrollOffset 钳制使最后一项底部对齐视口底部，即内容末尾；
 *   注意 `scrollToItem(itemCount)` 在 Compose 1.11 会被钳到 itemCount-1 并把
 *   最后一项钉在视口顶部，无法到尾部），避免每 token 重启动画；
 * - 会话切换（首条消息 id 变化）→ 重置状态并落到新会话底部。
 *
 * @param state 外层传入的 [LazyListState]（与 LazyColumn 共用）
 * @param messages 消息列表（用首条/末条消息 id 与内容变化驱动）
 * @param streamFollowEnabled 是否启用流式增长跟随（移动端 compact=true）
 * @param onPinnedChange pinned 变化回调（供 UI 显示「回到底部」按钮）
 * @return 当前是否 pinned
 */
@Composable
internal fun rememberChatAutoScroll(
    state: LazyListState,
    messages: List<ChatMessageDomainModel>,
    streamFollowEnabled: Boolean,
    onPinnedChange: (Boolean) -> Unit = {},
): Boolean {
    val pinned = remember { mutableStateOf(true) }
    var didInitialScroll by remember { mutableStateOf(false) }
    var lastGrowthId by remember { mutableStateOf<String?>(null) }
    val firstMessageId = messages.firstOrNull()?.id
    val lastMessage = messages.lastOrNull()

    // 会话切换（首条消息变化）→ 重置贴底状态，新会话重新落到底部
    LaunchedEffect(firstMessageId) {
        didInitialScroll = false
        pinned.value = true
        lastGrowthId = null
    }

    // 观察滚动位置：只更新 pinned，绝不在此滚动
    LaunchedEffect(state, didInitialScroll) {
        if (!didInitialScroll) return@LaunchedEffect
        snapshotFlow { state.toScrollProbe() }
            .collect { probe -> pinned.value = probe.isAtBottom() }
    }

    // 新消息到达 / 首次布局 / 重新钉底 → 首次无条件贴底，之后仅 pinned 时贴底。
    // key 含 pinned.value：用户滚回底部（pinned 变 true）时补一次贴尾，
    // 避免「到达时正在滚动」的消息被永久丢弃。
    LaunchedEffect(lastMessage?.id, pinned.value) {
        val id = lastMessage?.id ?: return@LaunchedEffect
        // layoutInfo.totalItemsCount 需等首次布局后才可知，最多等 30 帧
        var count = state.layoutInfo.totalItemsCount
        var waits = 0
        while (count <= 0 && waits < 30) {
            withFrameNanos { }
            count = state.layoutInfo.totalItemsCount
            waits++
        }
        if (count <= 0 || state.isScrollInProgress) return@LaunchedEffect
        if (!didInitialScroll || pinned.value) {
            // 贴到内容末尾：scrollOffset=MAX 会把最后一项向上卷到底（钳制），
            // 使最后一项底部对齐视口底部（内容尾部可见）。
            when {
                didInitialScroll && streamFollowEnabled -> state.scrollToItem(count - 1, Int.MAX_VALUE)
                didInitialScroll -> state.animateScrollToItem(count - 1, Int.MAX_VALUE)
                else -> state.scrollToItem(count - 1, Int.MAX_VALUE)
            }
            didInitialScroll = true
        }
    }

    // 流式增长（同一条消息内容变长）→ pinned 时瞬时跟随尾部
    LaunchedEffect(lastMessage) {
        if (lastMessage == null || lastGrowthId != lastMessage.id) {
            lastGrowthId = lastMessage?.id
            return@LaunchedEffect
        }
        if (streamFollowEnabled && pinned.value && !state.isScrollInProgress) {
            val count = state.layoutInfo.totalItemsCount
            if (count > 0) state.scrollToItem(count - 1, Int.MAX_VALUE)
        }
    }

    SideEffect { onPinnedChange(pinned.value) }
    return pinned.value
}

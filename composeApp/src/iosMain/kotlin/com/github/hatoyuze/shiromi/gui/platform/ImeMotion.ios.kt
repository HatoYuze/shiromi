// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf

private val imeMotionZeroState: State<Int> = mutableIntStateOf(0)

/** iOS 软键盘 inset 由系统滚动/避让处理，输入区不主动消费 IME 偏移 → 恒 0。 */
internal actual val imeMotionPxState: State<Int> get() = imeMotionZeroState

/** iOS 不主动消费 IME 偏移 → no-op。 */
internal actual fun pushImeBottom(bottomPx: Int) = Unit

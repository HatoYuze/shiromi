// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.platform

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf

private val imeMotionZeroState: State<Int> = mutableIntStateOf(0)

/** 桌面无软件键盘 → 恒 0。 */
internal actual val imeMotionPxState: State<Int> get() = imeMotionZeroState

/** 桌面无软件键盘 → no-op。 */
internal actual fun pushImeBottom(bottomPx: Int) = Unit

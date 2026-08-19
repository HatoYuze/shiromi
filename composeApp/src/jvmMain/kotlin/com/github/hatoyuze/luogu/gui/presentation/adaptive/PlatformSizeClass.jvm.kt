// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.adaptive

/**
 * 桌面 JVM 无屏幕宽度可依赖（窗口尺寸未测量），默认 [PlatformSizeClass.Expanded]；
 * 若用户把桌面窗口调窄，`calculatePlatformSizeClass` 的 BoxWithConstraints 会在
 * 首帧后立即校正为 Compact。
 */
actual fun initialPlatformSizeClass(): PlatformSizeClass = PlatformSizeClass.Expanded

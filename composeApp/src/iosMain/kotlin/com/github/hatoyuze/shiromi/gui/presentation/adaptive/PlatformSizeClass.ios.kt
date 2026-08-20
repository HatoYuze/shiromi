// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.adaptive

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIScreen

/**
 * iOS：按主屏宽度（点，1 点 ≈ 1 dp）决定初始布局方向，首帧即渲染正确布局；
 * 旋转/分屏变化仍由 BoxWithConstraints 实时校正。
 */
@OptIn(ExperimentalForeignApi::class)
actual fun initialPlatformSizeClass(): PlatformSizeClass {
    val width = UIScreen.mainScreen.bounds.useContents { size.width }
    return if (width < 600.0) PlatformSizeClass.Compact else PlatformSizeClass.Expanded
}

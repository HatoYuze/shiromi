// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.adaptive

import com.github.hatoyuze.shiromi.gui.platform.AppContextHolder

/**
 * Android：按当前配置的屏幕宽度（dp）决定初始布局方向，首帧即渲染正确布局，
 * 避免冷启动闪现桌面布局；旋转/分屏变化仍由 BoxWithConstraints 实时校正。
 */
actual fun initialPlatformSizeClass(): PlatformSizeClass {
    val screenWidthDp = AppContextHolder.context.resources.configuration.screenWidthDp
    return if (screenWidthDp < 600) PlatformSizeClass.Compact else PlatformSizeClass.Expanded
}

package com.github.hatoyuze.luogu.gui.presentation.adaptive

import com.github.hatoyuze.luogu.gui.platform.AppContextHolder

/**
 * Android：按当前配置的屏幕宽度（dp）决定初始布局方向，首帧即渲染正确布局，
 * 避免冷启动闪现桌面布局；旋转/分屏变化仍由 BoxWithConstraints 实时校正。
 */
actual fun initialPlatformSizeClass(): PlatformSizeClass {
    val screenWidthDp = AppContextHolder.context.resources.configuration.screenWidthDp
    return if (screenWidthDp < 600) PlatformSizeClass.Compact else PlatformSizeClass.Expanded
}

package com.github.hatoyuze.luogu.gui.presentation.adaptive

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/**
 * 窗口尺寸分级（对齐 Material 3 宽度断点的简化版）。
 *
 * - [Compact]：宽 < 600dp（手机竖屏）→ 底部导航 + 移动端布局
 * - [Expanded]：宽 ≥ 600dp（桌面窗口 / 平板）→ 现有侧栏布局
 */
enum class PlatformSizeClass { Compact, Expanded }

/** 600dp 断点，与 Material 3 WindowSizeClass 的 compact/medium 分界一致。 */
private val CompactBreakpoint = 600.dp

/**
 * 平台感知的初始尺寸类。
 *
 * 在首帧布局测量前就按平台屏幕宽度决定布局方向：Android/iOS 直接读取
 * 屏幕/窗口宽度（< 600dp → [PlatformSizeClass.Compact]），桌面 JVM 恒为
 * [PlatformSizeClass.Expanded]。避免移动端启动时先渲染一帧桌面布局
 * （桌面窗口宽度未测量前的默认值），桌面行为保持不变。
 */
expect fun initialPlatformSizeClass(): PlatformSizeClass

/** 基于当前可用宽度计算 [PlatformSizeClass]（在桌面把窗口调窄也能预览手机布局）。 */
@Composable
fun calculatePlatformSizeClass(): PlatformSizeClass {
    var sizeClass by remember { mutableStateOf(initialPlatformSizeClass()) }
    BoxWithConstraints {
        sizeClass = if (maxWidth < CompactBreakpoint) PlatformSizeClass.Compact else PlatformSizeClass.Expanded
    }
    return sizeClass
}

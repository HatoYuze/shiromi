// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared design tokens for Home page components.
 *
 * 桌面与移动端首页共用同一套形状 / 描边 / 间距 / 尺寸，保证双端视觉一致
 * （对齐 mobile-ui-preview.html 与 desktop-home-design.html 的「暖米纸色 +
 * 白卡圆角 + 藏蓝主色 + 暖金胶囊」语言）。
 */
object HomeDesignTokens {
    /** 标准卡片圆角（白卡）。 */
    val CardShape = RoundedCornerShape(16.dp)

    /** 标准卡片描边 —— 无阴影，浅色细描边做分隔。 */
    val CardBorder
        @androidx.compose.runtime.Composable
        get() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

    /** 卡片水平/垂直间距（首页网格）。 */
    val RowSpacing = 16.dp

    /** 卡片内边距（对齐设计稿 padding:16px）。 */
    val CardPadding: Dp = 16.dp

    /** 信息胶囊圆角。 */
    val PillShape = RoundedCornerShape(12.dp)

    /** 小号胶囊/标签圆角。 */
    val SmallPillShape = RoundedCornerShape(8.dp)

    /** 输入条圆角。 */
    val InputShape = RoundedCornerShape(12.dp)

    /** 桌面侧栏宽度。 */
    val SidebarWidth = 264.dp

    /** 桌面信息轨宽度。 */
    val RailWidth = 300.dp

    /** 信息轨与主列并排所需的最小内容区宽度（dp）。 */
    val RailBreakpoint = 820.dp
}

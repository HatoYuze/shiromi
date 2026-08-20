// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later
//
// 本文件为项目自有代码（ImageVector 构建包装）；全部图标路径数据来自第三方
// Hippo Design 官方图标库（见同目录 AppIconGlyphs.kt 的 SPDX 声明与
// LICENSES/LicenseRef-HippoDesign.txt）。

package com.github.hatoyuze.shiromi.gui.presentation.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 应用图标集：Hippo Design 风格实心图标（路径见 [AppIconGlyphs]，授权见
 * LICENSES/LicenseRef-HippoDesign.txt）。用于替换首页/设置/引导页中的装饰性 emoji。
 * 使用方式：`Icon(AppIcons.Checklist, null, tint = ...)` 渲染。
 */
object AppIcons {
    private fun solid(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 1024f,
            viewportHeight = 1024f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
        ).build()

    val Checklist = solid("checklist", AppIconGlyphs.CHECKLIST)
    val ChartBar = solid("chartBar", AppIconGlyphs.CHART_BAR)
    val CalendarIcon = solid("calendarIcon", AppIconGlyphs.CALENDAR_ICON)
    val EyeOpen = solid("eyeOpen", AppIconGlyphs.EYE_OPEN)
    val EyeClose = solid("eyeClose", AppIconGlyphs.EYE_CLOSE)
    val WarningIcon = solid("warningIcon", AppIconGlyphs.WARNING_ICON)
    val CloseIcon = solid("closeIcon", AppIconGlyphs.CLOSE_ICON)
    val SuccessIcon = solid("successIcon", AppIconGlyphs.SUCCESS_ICON)
    val SortingIcon = solid("sortingIcon", AppIconGlyphs.SORTING_ICON)
    val DirectionRight = solid("directionRight", AppIconGlyphs.DIRECTION_RIGHT)
    val RiseFilling = solid("riseFilling", AppIconGlyphs.RISE_FILLING)
    val NavigationIcon = solid("navigationIcon", AppIconGlyphs.NAVIGATION_ICON)
    val FavoriteIcon = solid("favorite", AppIconGlyphs.FAVORITE)
}

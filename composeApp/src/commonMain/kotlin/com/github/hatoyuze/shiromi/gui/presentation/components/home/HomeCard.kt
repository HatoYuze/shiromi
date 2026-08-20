// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.hatoyuze.shiromi.gui.presentation.components.HomeDesignTokens

/**
 * 首页标准白卡：圆角 + 浅描边 + 统一内边距（桌面与移动端共用）。
 *
 * @param contentPadding 卡片内边距，默认 [HomeDesignTokens.CardPadding]（16dp）。
 */
@Composable
fun HomeCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = HomeDesignTokens.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = HomeDesignTokens.CardShape,
        color = MaterialTheme.colorScheme.surface,
        border = HomeDesignTokens.CardBorder,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/**
 * 带标题的首页卡片（移动端「学习进度」等区块沿用）：可选前置图标 + 标题 + 内容。
 * 桌面端改用 [HomeCard] + [HomeSectionHeader] 组合，此组件保留给移动端简洁用法。
 */
@Composable
fun HomeContentCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            HomeSectionHeader(title = title, icon = icon)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

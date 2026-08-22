// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import shiromi.composeapp.generated.resources.Res
import shiromi.composeapp.generated.resources.shiromi_icon

/**
 * 应用图标徽章：以圆角方形（圆角 ≈ 22%）呈现完整应用图标（纯白满铺底）。
 * 图标自带白底，浅色/深色主题下都加一圈细边框以保证与背景区分。
 */
@Composable
internal fun AppIconBadge(size: Dp, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(size * 0.22f),
        color = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.size(size),
    ) {
        Image(
            painter = painterResource(Res.drawable.shiromi_icon),
            contentDescription = "Shiromi logo",
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * 左上角品牌：图标徽章 + “Shiromi” 标题（桌面侧栏 36dp，移动端顶栏 32dp）。
 */
@Composable
internal fun AppBrandMark(badgeSize: Dp = 36.dp, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        AppIconBadge(badgeSize)
        Spacer(Modifier.width(11.dp))
        Text(
            "Shiromi",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

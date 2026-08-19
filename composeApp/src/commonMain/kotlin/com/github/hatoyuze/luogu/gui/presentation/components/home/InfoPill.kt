// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.components.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.presentation.components.HomeDesignTokens

/**
 * 信息胶囊：warm 用暖金（secondary）浅底，否则主色浅底。
 * 桌面与移动端首页共用（对齐设计稿 pill.warm / pill.pri）。
 *
 * @param warm 是否使用暖金配色（连续打卡等强调信息）。
 * @param icon 可选前置图标（替换 🔥 等装饰性 emoji）。
 * @param fontSize 字号；[small] 时用更紧凑的内边距与小号字（日历图例等）。
 */
@Composable
fun InfoPill(
    text: String,
    warm: Boolean,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    fontSize: TextUnit = if (small) 10.sp else 12.sp,
    icon: ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (warm) scheme.secondary.copy(alpha = 0.12f) else scheme.primary.copy(alpha = 0.1f)
    val fg = if (warm) scheme.secondary else scheme.primary
    Surface(shape = HomeDesignTokens.PillShape, color = bg, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                horizontal = if (small) 8.dp else 10.dp,
                vertical = if (small) 3.dp else 5.dp,
            ),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (small) 11.dp else 13.dp),
                    tint = fg,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontSize = fontSize,
                color = fg,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

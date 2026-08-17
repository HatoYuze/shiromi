package com.github.hatoyuze.luogu.gui.presentation.components.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * @param fontSize 字号；[small] 时用更紧凑的内边距与小号字（日历图例等）。
 */
@Composable
fun InfoPill(
    text: String,
    warm: Boolean,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    fontSize: TextUnit = if (small) 10.sp else 12.sp,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (warm) scheme.secondary.copy(alpha = 0.12f) else scheme.primary.copy(alpha = 0.1f)
    val fg = if (warm) scheme.secondary else scheme.primary
    Surface(shape = HomeDesignTokens.PillShape, color = bg, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontSize = fontSize,
            color = fg,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(
                horizontal = if (small) 8.dp else 10.dp,
                vertical = if (small) 3.dp else 5.dp,
            ),
        )
    }
}

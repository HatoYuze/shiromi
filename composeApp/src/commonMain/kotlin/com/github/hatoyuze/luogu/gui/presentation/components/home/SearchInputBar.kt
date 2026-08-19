// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Search
import compose.icons.feathericons.Send

/**
 * 搜索输入条（桌面与移动端共用）：圆角描边容器 + 搜索图标 + 无边框输入 +
 * 尾部圆形主色发送按钮（对齐设计稿 inputbar / 移动端今日页）。
 * 回车与按钮均可提交；错误态显示红边 + 下方提示。
 */
@Composable
fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索题目编号…",
    isError: Boolean = false,
    errorText: String? = "未找到该题目，请检查编号",
) {
    val scheme = MaterialTheme.colorScheme
    val borderColor = if (isError) scheme.error.copy(alpha = 0.7f)
    else scheme.outline.copy(alpha = 0.35f)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = scheme.surface,
        border = BorderStroke(1.5.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                FeatherIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = scheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                            onSubmit()
                            true
                        } else false
                    },
                textStyle = TextStyle(fontSize = 13.sp, color = scheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(scheme.primary),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            placeholder,
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                    }
                    inner()
                },
            )
            Spacer(Modifier.width(8.dp))
            val canSubmit = query.isNotBlank()
            Surface(
                shape = CircleShape,
                color = if (canSubmit) scheme.primary else scheme.primary.copy(alpha = 0.35f),
                modifier = Modifier.size(30.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clickable(
                            enabled = canSubmit,
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onSubmit() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        FeatherIcons.Send,
                        contentDescription = "搜索",
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }

    if (isError && errorText != null) {
        Text(
            errorText,
            fontSize = 12.sp,
            color = scheme.error,
            modifier = Modifier.padding(start = 6.dp, top = 4.dp),
        )
    }
}

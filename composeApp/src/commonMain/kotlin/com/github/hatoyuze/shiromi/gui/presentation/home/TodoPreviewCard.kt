// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.shiromi.gui.presentation.components.home.DashedDivider
import com.github.hatoyuze.shiromi.gui.presentation.components.home.HomeCard
import com.github.hatoyuze.shiromi.gui.presentation.components.home.HomeSectionHeader
import com.github.hatoyuze.shiromi.gui.presentation.components.home.TodoInput
import com.github.hatoyuze.shiromi.gui.presentation.components.home.TodoRow
import com.github.hatoyuze.shiromi.gui.presentation.components.icons.AppIcons
import com.github.hatoyuze.shiromi.gui.presentation.state.HomeViewModel

/** 信息轨最多直接展示的待办条数（「查看全部」前）。 */
private const val PREVIEW_LIMIT = 4

/**
 * 待办预览卡（对齐设计稿 rail 待办）：移动端样式行
 * （圆形复选 / 到期标签 / 虚线分隔 / ✕ 删除）+ 「查看全部 ›」展开。
 */
@Composable
internal fun TodoPreviewCard(
    state: HomeViewModel.HomeUiState,
    onAddTodo: (String, Long?) -> Unit,
    onToggleTodo: (String, Boolean) -> Unit,
    onDeleteTodo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = state.todos.size
    val done = remember(state.todos) { state.todos.count { it.completed } }
    var expanded by remember { mutableStateOf(false) }

    HomeCard(modifier = modifier) {
        HomeSectionHeader(
            title = "待办",
            icon = AppIcons.Checklist,
            trailing = {
                Text(
                    "$done/$total 完成",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
        )
        Spacer(Modifier.height(10.dp))
        TodoInput(onAdd = onAddTodo)
        Spacer(Modifier.height(6.dp))
        if (state.todos.isEmpty()) {
            Text(
                "暂无待办事项",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else {
            val visible = if (expanded) state.todos else state.todos.take(PREVIEW_LIMIT)
            visible.forEachIndexed { index, todo ->
                if (index > 0) DashedDivider()
                TodoRow(
                    todo = todo,
                    onToggle = { onToggleTodo(todo.id, todo.completed) },
                    onDelete = { onDeleteTodo(todo.id) },
                )
            }
            if (state.todos.size > PREVIEW_LIMIT) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (expanded) "收起 ▲" else "查看全部 ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { expanded = !expanded },
                )
            }
        }
    }
}

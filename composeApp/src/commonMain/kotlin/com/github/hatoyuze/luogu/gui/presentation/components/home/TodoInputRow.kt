// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.TodoItemDomainModel
import com.github.hatoyuze.luogu.gui.presentation.utils.formatDue
import compose.icons.FeatherIcons
import compose.icons.feathericons.Check
import compose.icons.feathericons.Plus
import compose.icons.feathericons.X
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

// ═══════════════════════════════════════════════════════════
// 待办输入（共享）：圆角输入条 + 圆形主色添加钮 + 到期快捷胶囊
// ═══════════════════════════════════════════════════════════

/** 待办标题输入上限（与 EventEditDialog.MAX_NAME_LENGTH 保持一致的安全边界）。 */
private const val MAX_TODO_TEXT_LENGTH = 200

/** 到期日快捷计算（本地时区当天 23:59）。 */
private fun endOfDayDue(date: LocalDate): Long =
    date.atTime(23, 59).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()

private fun todayDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun dueFriday(): Long {
    var d = todayDate()
    while (d.dayOfWeek != DayOfWeek.FRIDAY) d = LocalDate.fromEpochDays(d.toEpochDays() + 1L)
    return endOfDayDue(d)
}

/** 到期快捷选项（key 固定；epoch 在添加时按当天解析，避免跨零点后过期）。 */
private val DUE_OPTIONS = listOf(
    "无期限" to "none",
    "今天" to "today",
    "明天" to "tomorrow",
    "本周五" to "friday",
)

private fun resolveDueKey(key: String): Long? = when (key) {
    "today" -> endOfDayDue(todayDate())
    "tomorrow" -> endOfDayDue(LocalDate.fromEpochDays(todayDate().toEpochDays() + 1L))
    "friday" -> dueFriday()
    else -> null
}

/**
 * 待办输入行（桌面与移动端共用）：文本 + 到期快捷胶囊 + 圆形添加按钮。
 * 回车或点按圆形按钮提交。
 */
@Composable
fun TodoInput(
    onAdd: (String, Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var dueKey by remember { mutableStateOf("none") }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(MAX_TODO_TEXT_LENGTH) },
            placeholder = { Text("添加新待办…", fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter && text.isNotBlank()) {
                        onAdd(text.trim(), resolveDueKey(dueKey))
                        text = ""
                        dueKey = "none"
                        true
                    } else false
                },
            textStyle = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.width(8.dp))
        val hasText = text.isNotBlank()
        Surface(
            shape = CircleShape,
            color = if (hasText) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            modifier = Modifier.size(40.dp),
        ) {
            Box(
                modifier = Modifier
                    .clickable(
                        enabled = hasText,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) {
                        onAdd(text.trim(), resolveDueKey(dueKey))
                        text = ""
                        dueKey = "none"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    FeatherIcons.Plus,
                    contentDescription = "添加",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DUE_OPTIONS.forEach { (label, key) ->
            val selected = dueKey == key
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { dueKey = key },
            ) {
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// 待办行（共享）：圆形复选 + 标题 + 到期标签 + ✕ 删除
// ═══════════════════════════════════════════════════════════

/**
 * 待办行（桌面与移动端共用）：圆形复选框（完成 = 实心主色 + 白勾）、
 * 标题（完成划线）、到期标签（[formatDue]）、右侧 ✕ 删除。
 */
@Composable
fun TodoRow(
    todo: TodoItemDomainModel,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = todo.completed
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 圆形复选框（完成 = 实心主色 + 白勾；未完成 = 空心描边）
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(if (done) scheme.primary else Color.Transparent, CircleShape)
                .border(
                    width = 1.5.dp,
                    color = if (done) scheme.primary else scheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape,
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (done) {
                Icon(
                    FeatherIcons.Check,
                    contentDescription = "已完成",
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            todo.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough
            else androidx.compose.ui.text.style.TextDecoration.None,
            color = if (done) scheme.onSurfaceVariant.copy(alpha = 0.5f) else scheme.onSurface,
        )
        Spacer(Modifier.width(8.dp))
        val dueText = formatDue(todo.dueAt, todo.completed)
        if (dueText.isNotEmpty()) {
            Text(
                dueText,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.55f),
            )
            Spacer(Modifier.width(6.dp))
        }
        // 删除 ✕
        Box(
            modifier = Modifier
                .size(24.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDelete() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                FeatherIcons.X,
                contentDescription = "删除",
                tint = scheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

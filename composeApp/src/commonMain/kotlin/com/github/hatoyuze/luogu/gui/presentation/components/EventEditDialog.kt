// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Trash2
import compose.icons.feathericons.X
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

// ═══════════════════════════════════════════════════════════
// EventEditDialog — 桌面端事件编辑弹窗（设计稿 A2）
// 居中弹窗（暖色灯罩遮罩由宿主 HomeScreen 提供）：双栏布局（左字段/右色板）、
// 预设色板 + 自定义折叠、带文案按钮、键盘操作（Enter 保存 / Esc 取消）。
// 与移动端 EventEditSheet 共用 EventEditState / EventColorPicker。
// ═══════════════════════════════════════════════════════════

private const val MAX_NAME_LENGTH = 64

/** 解析 "HH:mm" 文本 → 分钟数（0..1439）；非法返回 null。 */
internal fun parseTimeMinutes(text: String): Int? {
    val parts = text.split(":").map { it.trim() }
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

@Composable
fun EventEditDialog(
    date: LocalDate,
    initialName: String,
    initialColor: Int,        // ARGB int; 0 = use default
    initialPinned: Boolean = false,
    existingEventId: String?,
    onSave: (name: String, color: Int, pinned: Boolean, allDay: Boolean, timeMinutes: Int?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    initialAllDay: Boolean = false,
    initialTimeMinutes: Int? = null,
) {
    val state = remember {
        EventEditState(initialName, initialColor, initialPinned, initialAllDay, initialTimeMinutes)
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    fun save() {
        if (state.canSave) {
            onSave(state.name.trim(), hsvToArgb(state.hsvColor), state.pinned, state.allDay, state.timeMinutesOrNull())
        }
    }

    Surface(
        modifier = Modifier
            .width(460.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.Enter -> { save(); true }
                        Key.Escape -> { onDismiss(); true }
                        else -> false
                    }
                } else false
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* consume click — block propagation to scrim */ },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // ── Title + close ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "标记事件 · ${date.month.number}月${date.day}日",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        FeatherIcons.X,
                        contentDescription = "取消",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── 双栏：左 = 字段，右 = 颜色 ──
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1.1f)) {
                    Text("事件名称", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = { state.name = it.take(MAX_NAME_LENGTH) },
                        placeholder = { Text("事件名称...", fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("全天", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.allDay, onCheckedChange = { state.allDay = it })
                    }

                    if (!state.allDay) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.timeText,
                            onValueChange = { input ->
                                state.timeText = input.filter { it.isDigit() || it == ':' }.take(5)
                            },
                            placeholder = { Text("HH:mm", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall,
                            isError = state.timeInvalid,
                            supportingText = if (state.timeInvalid) {
                                { Text("时间格式应为 HH:mm", fontSize = 11.sp) }
                            } else null,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("置顶", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.pinned, onCheckedChange = { state.pinned = it })
                    }
                }

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("颜色", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                        Text(state.hexInput.take(6).let { "#$it" }, fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    }
                    Spacer(Modifier.height(8.dp))
                    EventColorPicker(state)
                }
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            Spacer(Modifier.height(12.dp))

            // ── Actions：删除（仅编辑态）/ 取消 / 保存 ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (existingEventId != null) {
                    TextButton(onClick = onDelete) {
                        Icon(FeatherIcons.Trash2, contentDescription = null, modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                        Spacer(Modifier.width(4.dp))
                        Text("删除", color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { save() },
                    enabled = state.canSave,
                    shape = RoundedCornerShape(10.dp),
                ) { Text("保存") }
            }
            Text(
                "Enter 保存 · Esc 取消",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

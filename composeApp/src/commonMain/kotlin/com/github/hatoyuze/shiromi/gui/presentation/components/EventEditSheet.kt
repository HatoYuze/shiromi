// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Trash2
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

// ═══════════════════════════════════════════════════════════
// EventEditSheet — 移动端事件编辑底部表单（设计稿 A）
// ModalBottomSheet：大触控目标、IME/安全区适配、可滚动；与桌面 EventEditDialog
// 共用 EventEditState / EventColorPicker。
// ═══════════════════════════════════════════════════════════

private const val MAX_NAME_LENGTH = 64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditSheet(
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state = remember {
        EventEditState(initialName, initialColor, initialPinned, initialAllDay, initialTimeMinutes)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // ── Title ──
            Text(
                "标记事件 · ${date.month.number}月${date.day}日",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(Modifier.height(16.dp))

            // ── 名称 ──
            OutlinedTextField(
                value = state.name,
                onValueChange = { state.name = it.take(MAX_NAME_LENGTH) },
                placeholder = { Text("事件名称...", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            // ── 全天 + 时间 ──
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("全天", style = MaterialTheme.typography.bodyLarge)
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
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    isError = state.timeInvalid,
                    supportingText = if (state.timeInvalid) {
                        { Text("时间格式应为 HH:mm", fontSize = 11.sp) }
                    } else null,
                )
            }

            // ── 颜色（预设 + 自定义折叠）──
            Spacer(Modifier.height(16.dp))
            Text("颜色", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            EventColorPicker(state)

            // ── 置顶 ──
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("置顶", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.pinned, onCheckedChange = { state.pinned = it })
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            Spacer(Modifier.height(12.dp))

            // ── Actions：删除（仅编辑态）/ 取消 / 保存 ──
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (existingEventId != null) {
                    TextButton(onClick = onDelete) {
                        Icon(FeatherIcons.Trash2, contentDescription = null, modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                        Spacer(Modifier.width(4.dp))
                        Text("删除", color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp),
                ) { Text("取消") }
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (state.canSave) {
                            onSave(state.name.trim(), hsvToArgb(state.hsvColor), state.pinned, state.allDay, state.timeMinutesOrNull())
                        }
                    },
                    enabled = state.canSave,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp),
                ) { Text("保存") }
            }
        }
    }
}

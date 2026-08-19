// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.components.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.StudyTopic
import com.github.hatoyuze.luogu.gui.presentation.components.HomeDesignTokens
import com.github.hatoyuze.luogu.gui.presentation.components.icons.AppIcons

/** 学习专题名输入上限（防止超长文本持久化/渲染膨胀）。 */
private const val MAX_TOPIC_NAME_LENGTH = 200

/** 连续打卡行：图标 + 连续打卡 N 天（桌面与移动端共用）。 */
@Composable
fun StreakRow(streakDays: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            AppIcons.RiseFilling,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "连续打卡 ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "$streakDays",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            " 天",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 学习专题进度条（桌面与移动端共用）：专题名 + 编辑 + 进度条 + 题数统计。
 * 编辑态内联输入专题名与目标题数。
 */
@Composable
fun TopicProgressBar(
    topic: StudyTopic,
    onUpdateTopic: (String, Int) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var editName by remember(topic.name) { mutableStateOf(topic.name) }
    var editGoal by remember(topic.goalCount) { mutableStateOf(topic.goalCount.toString()) }

    Column {
        if (editing) {
            OutlinedTextField(
                value = editName,
                onValueChange = { editName = it.take(MAX_TOPIC_NAME_LENGTH) },
                placeholder = { Text("专题名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = editGoal,
                    onValueChange = { editGoal = it.filter { c -> c.isDigit() } },
                    placeholder = { Text("目标") },
                    singleLine = true,
                    modifier = Modifier.width(72.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(4.dp))
                Text("题", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    val goal = editGoal.toIntOrNull() ?: 10
                    onUpdateTopic(editName.ifBlank { "未命名专题" }, goal)
                    editing = false
                }) {
                    Text("保存", fontSize = 13.sp)
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    AppIcons.NavigationIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (topic.name.isBlank()) "设置学习专题" else topic.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                    onClick = {
                        editName = topic.name
                        editGoal = topic.goalCount.toString()
                        editing = true
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Text("编辑", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress bar
            if (topic.goalCount > 0) {
                val progress = (topic.currentCount.toFloat() / topic.goalCount).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${topic.currentCount} / ${topic.goalCount} 题",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    "点击编辑设置目标题数",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

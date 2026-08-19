// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.skill.coach

import io.github.hatoyuze.deepseek.toolcall.dsl.ToolHostBuilder

fun ToolHostBuilder.installCoachTools(provider: MemoryProvider) {

    tool("get_memory") {
        description = buildString {
            append("获取该学生的历史学习记录。返回包含：上次学习主题和题目、理解水平评分(0~100)、")
            append("历史评分记录、主要卡点(pain_points)、进步点(progress_points)。")
            append("在新对话或无上下文时必须首先调用此工具了解学生水平。若学生无历史记录则返回空记录。")
        }
        parameters {}
        execute { _, _ ->
            provider.getMemory() ?: StudentMemory(
                studentId = "",
                levelScore = 50,
                levelHistory = emptyList(),
                painPoints = emptyList(),
                progressPoints = emptyList(),
            )
        }
    }

    tool("get_selected") {
        description = buildString {
            append("获取该学生所有已完成题目的洛谷题号列表。选题阶段必须调用以避重。")
            append("返回 completed 数组和 count。若学生无已完成题目则返回空数组。")
        }
        parameters {}
        execute { _, _ ->
            val list = provider.getSelectedProblems()
            SelectedResult(completed = list, count = list.size)
        }
    }
}

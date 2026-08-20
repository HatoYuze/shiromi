// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.protocol.coach

/**
 * 学生记忆与进度的抽象接口。
 *
 * 调用方实现此接口对接持久化存储。`get_memory` / `get_selected` 工具通过此接口获取数据。
 */
interface MemoryProvider {

    /**
     * 获取学生的历史学习记录。
     *
     * 返回 `null` 表示无历史记录（首次使用），工具返回空 [StudentMemory]。
     */
    suspend fun getMemory(): StudentMemory?

    /**
     * 获取该学生所有已完成题目的题号列表。
     *
     * @return 已完成题目的洛谷题号（如 `["P1000", "P1001"]`）
     */
    suspend fun getSelectedProblems(): List<String>
}

// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.skill.api

import kotlin.math.*

// ═══════════════════════════════════════════════════════════
// 常量与参数
// ═══════════════════════════════════════════════════════════

private const val GLOBAL_PASS_RATE = 0.3       // 全局平均通过率（先验）
private const val SMOOTH_STRENGTH = 100.0      // 贝叶斯平滑强度
private const val TAG_DECAY_BETA = 1.0         // 标签权重衰减系数
private const val KNOWLEDGE_LAMBDA = 0.5       // 知识标签融合比例
private const val PASS_SENSITIVITY_ALPHA = 0.47 // 通过率难度敏感度指数
private const val EPSILON = 0.01               // 防止除零
private const val DIFFICULTY_BASE = 2.0        // 难度指数底数
private const val SCALE_FACTOR = 100.0         // 最终分数缩放系数
private const val META_TAG_FLAG = -1.0         // 元信息标签标记

// ═══════════════════════════════════════════════════════════
// 数据结构
// ═══════════════════════════════════════════════════════════

data class DifficultyScores(
    val overall: Double,       // 综合难度分
    val knowledge: Double,     // 知识难度分
    val thinking: Double,      // 思维难度分
)

// ═══════════════════════════════════════════════════════════
// 核心函数
// ═══════════════════════════════════════════════════════════

/**
 * 计算题目的三维难度分数
 *
 * @param difficulty 洛谷官方难度等级 (1~8)
 * @param passRate 通过率 (0.0~1.0)
 * @param totalSubmissions 总提交数
 * @param tags 题目已解析的标签列表（含知识等级 k）
 * @return 三维难度分数
 */
fun calcDifficultyScores(
    difficulty: Int,
    passRate: Double,
    totalSubmissions: Int,
    tags: List<ResolvedTag>,
): DifficultyScores {

    val D = difficulty.toDouble()
    val N = totalSubmissions.toDouble()

    // 1. 平滑通过率（贝叶斯平滑）
    val pAdj = (passRate * N + SMOOTH_STRENGTH * GLOBAL_PASS_RATE) / (N + SMOOTH_STRENGTH)

    // 2. 计算知识等级 K（过滤掉元信息标签 k < 0）
    val knowledgeTags = tags.filter { it.k >= 0.0 }

    val K = if (knowledgeTags.isNotEmpty()) {
        val weighted = knowledgeTags.map { tag ->
            val w = exp(-TAG_DECAY_BETA * abs(tag.k - D))
            tag.k to w
        }
        val sumW = weighted.sumOf { it.second }
        if (sumW > 0) weighted.sumOf { it.first * it.second } / sumW else D
    } else {
        D
    }

    // 3. 综合等级 L
    val L = (1 - KNOWLEDGE_LAMBDA) * D + KNOWLEDGE_LAMBDA * K

    // 4. 通过率难度因子
    val passFactor = (1.0 / (pAdj + EPSILON)).pow(PASS_SENSITIVITY_ALPHA)

    // 5. 生成三项分数
    val S = SCALE_FACTOR * DIFFICULTY_BASE.pow(L) * passFactor
    val SKnow = SCALE_FACTOR * DIFFICULTY_BASE.pow(K)
    val SThink = if (SKnow > 0) S / SKnow else S

    return DifficultyScores(overall = S, knowledge = SKnow, thinking = SThink)
}

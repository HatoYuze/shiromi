// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.protocol.api

import com.github.hatoyuze.shiromi.protocol.platform.loadTagResourceJson

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 洛谷题目难度级别。
 *
 * 范围 0（暂无评定）到 8（NOI/CTSC）。
 * 不参与 JSON 序列化，仅作为内部 ID → 标签的查找表。
 * 实际序列化到 JSON 的是 [DifficultyInfo]。
 */
enum class DifficultyLevel(val id: Int, val label: String, val color: String) {
    UNRATED(0, "暂无评定", "#bfbfbf"),
    ENTRY(1, "入门", "#fe4c61"),
    POPULARIZATION_MINUS(2, "普及−", "#f39c11"),
    POPULARIZATION(3, "普及", "#ffc116"),
    POPULARIZATION_PLUS(4, "普及+/提高-", "#52c41a"),
    IMPROVEMENT(5,"提高","#13c2c2"),
    IMPROVEMENT_PLUS(6, "提高+/省选−", "#3498db"),
    PROVINCIAL(7, "省选/NOI−", "#9d3dcf"),
    NOI_MINUS(8, "NOI/NOI+/CTSC", "#0e1d69");

    companion object {
        fun fromId(id: Int): DifficultyLevel =
            entries.find { it.id == id } ?: UNRATED
    }
}

/**
 * 洛谷题目来源类型。
 *
 * 不参与 JSON 序列化，仅用于内部字符串 → 类型的查找和构建 API query string。
 * 实际序列化到 JSON 的是 [TypeInfo]。
 */
enum class ProblemType(val value: String, val label: String) {
    LUOGU("luogu", "洛谷主题库"),
    CF("cf", "CodeForces"),
    SP("sp", "SPOJ"),
    AT("at", "AtCoder"),
    UVA("uva", "UVA"),
    BZOJ("bzoj", "BZOJ"),
    POJ("poj", "POJ"),
    HDU("hdu", "HDU"),
    LOJ("loj", "LibreOJ"),
    ;

    companion object {
        fun fromValue(value: String): ProblemType =
            entries.find { it.value == value } ?: LUOGU
    }
}

// ═══════════════════════════════════════════════════════════
// 标签映射
// ═══════════════════════════════════════════════════════════

/** 标签名（普通 data class：@JvmInline 在 KMP common 源集不可用） */
data class LuoguTag(val name: String)

/** 解析后的标签（含 ID 和名称），直接作为 tool 返回值 */
@Serializable
data class ResolvedTag(val id: Int, val name: String, val k: Double = -1.0)

/** 资源文件中单条标签条目的序列化格式 */
@Serializable
internal data class TagEntry(val id: Int, val name: String, val k: Double = -1.0)

/**
 * 洛谷算法标签映射表。
 *
 * 从扫库产出的 `luogu_tags.json` 资源文件加载。
 */
object LuoguTags {

    /** ID → 标签名 */
    val TAG_MAP: Map<Int, LuoguTag> by lazy { tagEntries.associate { it.id to LuoguTag(it.name) } }

    /** ID → 知识难度因子 k */
    val K_MAP: Map<Int, Double> by lazy { tagEntries.associate { it.id to it.k } }

    private val tagEntries: List<TagEntry> by lazy {
        val json = loadTagResourceJson()
        if (json != null) {
            try {
                resourceJson.decodeFromString<List<TagEntry>>(json)
            } catch (_: Exception) { emptyList() }
        } else emptyList()
    }

    private val resourceJson = Json { ignoreUnknownKeys = true }

    /** 将标签 ID 列表解析为带名称的标签列表。未知 ID 会被静默跳过。 */
    fun resolveTags(ids: List<Int>): List<ResolvedTag> =
        ids.mapNotNull { id -> TAG_MAP[id]?.let { ResolvedTag(id, it.name, K_MAP[id] ?: -1.0) } }

    /** 解析单个标签 ID */
    fun resolveTag(id: Int): ResolvedTag? =
        TAG_MAP[id]?.let { ResolvedTag(id, it.name, K_MAP[id] ?: -1.0) }

    /** 获取全部标签（按 ID 排序） */
    fun allTags(): List<ResolvedTag> =
        tagEntries.map { ResolvedTag(it.id, it.name, it.k) }.sortedBy { it.id }
}

// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.ToolCallInfo
import com.github.hatoyuze.luogu.skill.api.DifficultyLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Human-readable descriptions for tool calls in the thinking timeline.
 * Only renders parameters that are actually present (non-null, non-empty).
 */
object ToolCallDescriber {

    /** Coach memory tools — hidden from timeline */
    private val hiddenTools = setOf("get_memory", "get_selected", "askuser")

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun isHidden(functionName: String): Boolean = functionName in hiddenTools

    @Composable
    fun Describe(toolCall: ToolCallInfo) {
        // 按 arguments 记忆解析结果：流式 token 每帧重走 Describe 时避免重复 JSON 解析
        // （工具行数量有限，但逐 token 解析纯属浪费）。
        val args = remember(toolCall.arguments) {
            try { json.parseToJsonElement(toolCall.arguments).jsonObject } catch (_: Exception) { null }
        }
        when (toolCall.name) {
            "luogu_get_filters" -> Text("获取筛选条件", fontSize = 13.sp)
            "luogu_get_practice" -> Text("查看做题记录", fontSize = 13.sp)
            "luogu_search_problems" -> DescribeSearchProblems(args)
            "luogu_get_problem" -> DescribeGetProblem(args)
            "luogu_get_solutions" -> DescribeGetSolutions(args)
            "luogu_search_trainings" -> DescribeSearchTrainings(args)
            "luogu_get_training" -> DescribeGetTraining(args)
            "luogu_get_records" -> DescribeGetRecords(args)
            "luogu_get_record" -> DescribeGetRecord(args)
            else -> Text("调用 ${toolCall.name}", fontSize = 13.sp)
        }
    }

    /** `luogu_search_problems` 参数的纯数据视图（可测试，供 FlowRow 排版）。 */
    data class SearchProblemsParts(
        val keyword: String?,
        val tags: List<String>,
        val difficultyMin: Int?,
        val difficultyMax: Int?,
        val sortBy: String?,
    )

    // ── 安全访问器：LLM 提供的参数可能类型不符（如 keyword 为数组），
    //    一律用 as? 而非抛异常的 jsonPrimitive/jsonArray，避免组合期崩溃。──

    private fun JsonObject.optString(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.optInt(key: String): Int? = optString(key)?.toIntOrNull()

    private fun JsonObject.optStringArray(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull { item ->
            (item as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content?.takeIf { it.isNotBlank() }
        } ?: emptyList()

    /** 解析搜索参数；全部为空时返回 null。tag 支持逗号/顿号/分号/空白分隔的多个标签。 */
    fun parseSearchProblemsParts(args: JsonObject?): SearchProblemsParts? {
        if (args == null) return null
        val keyword = args.optString("keyword")
        val tags = args.optString("tag")
            ?.split(',', '，', ';', '；', '、', ' ', '\n', '\t')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        val dMin = args.optInt("difficulty_min")
        val dMax = args.optInt("difficulty_max")
        val sortBy = args.optString("sort_by")
        if (keyword == null && tags.isEmpty() && dMin == null && dMax == null && sortBy == null) return null
        return SearchProblemsParts(keyword, tags, dMin, dMax, sortBy)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun DescribeSearchProblems(args: JsonObject?) {
        val parts = parseSearchProblemsParts(args)
            ?: run { Text("搜索题目", fontSize = 13.sp); return }
        // FlowRow 换行排版：关键词/标签 chip/难度徽章/排序文案在窄屏下自然换行，
        // 不再单行溢出（移动端思考链 tag 适配）。
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("搜索题目", fontWeight = FontWeight.Medium, fontSize = 13.sp)
            parts.keyword?.let {
                Text(it, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            parts.tags.forEach { tag -> TagChip(tag) }
            val dMin = parts.difficultyMin
            val dMax = parts.difficultyMax
            when {
                dMin != null && dMax != null -> {
                    Text("难度", fontSize = 13.sp)
                    DifficultyBadge(dMin)
                    if (dMin != dMax) {
                        Text("~", fontSize = 13.sp)
                        DifficultyBadge(dMax)
                    }
                }
                dMin != null -> {
                    Text("难度≥", fontSize = 13.sp)
                    DifficultyBadge(dMin)
                }
                dMax != null -> {
                    Text("难度≤", fontSize = 13.sp)
                    DifficultyBadge(dMax)
                }
            }
            parts.sortBy?.let {
                Text("按${sortLabel(it)}排序", fontSize = 13.sp)
            }
        }
    }

    /** 标签 chip：与题目卡 `ProblemTagsRow` 同一视觉语言（secondaryContainer 圆角胶囊）。 */
    @Composable
    private fun TagChip(text: String) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        ) {
            Text(
                text = "#$text",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }

    @Composable
    private fun DescribeGetProblem(args: JsonObject?) {
        val pid = args?.optString("pid") ?: "?"
        Text("查看题目 ", fontWeight = FontWeight.Normal, fontSize = 13.sp)
        Text(pid, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }

    @Composable
    private fun DescribeGetSolutions(args: JsonObject?) {
        val pid = args?.optString("pid") ?: "?"
        val limit = args?.optInt("limit")
        val ids = args?.optStringArray("solution_ids")
        val extra = when {
            ids?.isNotEmpty() == true -> "(指定篇)"
            limit != null -> "(前${limit}篇)"
            else -> ""
        }
        Text("查看题解 $extra", fontWeight = FontWeight.Normal, fontSize = 13.sp)
        Text(pid, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }

    @Composable
    private fun DescribeSearchTrainings(args: JsonObject?) {
        val keyword = args?.optString("keyword")
        if (keyword != null) {
            Text("搜索题单：", fontSize = 13.sp)
            Text(keyword, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        } else {
            Text("搜索题单", fontSize = 13.sp)
        }
    }

    @Composable
    private fun DescribeGetTraining(args: JsonObject?) {
        val id = args?.optString("id") ?: "?"
        Text("查看题单 #$id", fontSize = 13.sp)
    }

    @Composable
    private fun DescribeGetRecords(args: JsonObject?) {
        val pid = args?.optString("pid") ?: "?"
        val limit = args?.optInt("limit")
        Text("获取提交记录 ", fontSize = 13.sp)
        Text(pid, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (limit != null) Text("，最近${limit}条", fontSize = 13.sp)
    }

    @Composable
    private fun DescribeGetRecord(args: JsonObject?) {
        val id = args?.optString("id") ?: "?"
        Text("查看提交记录 #$id", fontSize = 13.sp)
    }

    @Composable
    fun DifficultyBadge(levelId: Int) {
        val level = DifficultyLevel.fromId(levelId)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = parseHexColor(level.color),
        ) {
            Text(
                text = level.label,
                color = Color.White,
                fontSize = 11.sp,
            )
        }
    }

    private fun parseHexColor(hex: String): Color {
        val colorStr = hex.removePrefix("#")
        val rgb = colorStr.toLong(16)
        return Color(
            red = ((rgb shr 16) and 0xFF) / 255f,
            green = ((rgb shr 8) and 0xFF) / 255f,
            blue = (rgb and 0xFF) / 255f,
        )
    }

    private fun sortLabel(sort: String): String = when (sort) {
        "relevance" -> "相关度"
        "difficulty_asc" -> "难度升序"
        "difficulty_desc" -> "难度降序"
        "pass_rate" -> "通过率"
        else -> sort
    }
}

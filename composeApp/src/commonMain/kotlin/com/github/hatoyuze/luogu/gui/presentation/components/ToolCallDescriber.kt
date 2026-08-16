package com.github.hatoyuze.luogu.gui.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.hatoyuze.luogu.gui.domain.model.ToolCallInfo
import com.github.hatoyuze.luogu.skill.api.DifficultyLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        val args = try { json.parseToJsonElement(toolCall.arguments).jsonObject } catch (_: Exception) { null }
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

    @Composable
    private fun DescribeSearchProblems(args: kotlinx.serialization.json.JsonObject?) {
        val parts = mutableListOf<@Composable () -> Unit>()
        args?.get("keyword")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let {
            parts.add { Text(it, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
        }
        args?.get("tag")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let {
            parts.add { Text("标签$it", fontSize = 13.sp) }
        }
        val dMin = args?.get("difficulty_min")?.jsonPrimitive?.content?.toIntOrNull()
        val dMax = args?.get("difficulty_max")?.jsonPrimitive?.content?.toIntOrNull()
        if (dMin != null && dMax != null) {
            parts.add { DifficultyBadge(dMin) }
            if (dMin != dMax) {
                parts.add { Text("~", fontSize = 13.sp) }
                parts.add { DifficultyBadge(dMax) }
            }
        } else if (dMin != null) {
            parts.add { Text("难度≥", fontSize = 13.sp) }
            parts.add { DifficultyBadge(dMin) }
        } else if (dMax != null) {
            parts.add { Text("难度≤", fontSize = 13.sp) }
            parts.add { DifficultyBadge(dMax) }
        }
        args?.get("sort_by")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let {
            parts.add { Text("按${sortLabel(it)}排序", fontSize = 13.sp) }
        }
        Row { parts.forEachIndexed { i, p -> if (i > 0) { Spacer(Modifier.width(4.dp)) }; p() } }
    }

    @Composable
    private fun DescribeGetProblem(args: kotlinx.serialization.json.JsonObject?) {
        val pid = args?.get("pid")?.jsonPrimitive?.content ?: "?"
        Text("查看题目 ", fontWeight = FontWeight.Normal, fontSize = 13.sp)
        Text(pid, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }

    @Composable
    private fun DescribeGetSolutions(args: kotlinx.serialization.json.JsonObject?) {
        val pid = args?.get("pid")?.jsonPrimitive?.content ?: "?"
        val limit = args?.get("limit")?.jsonPrimitive?.content?.toIntOrNull()
        val ids = args?.get("solution_ids")?.jsonArray?.mapNotNull { it.jsonPrimitive.content }
        val extra = when {
            !ids.isNullOrEmpty() -> "(指定篇)"
            limit != null -> "(前${limit}篇)"
            else -> ""
        }
        Text("查看题解 $extra", fontWeight = FontWeight.Normal, fontSize = 13.sp)
        Text(pid, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }

    @Composable
    private fun DescribeSearchTrainings(args: kotlinx.serialization.json.JsonObject?) {
        val keyword = args?.get("keyword")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        if (keyword != null) {
            Text("搜索题单：", fontSize = 13.sp)
            Text(keyword, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        } else {
            Text("搜索题单", fontSize = 13.sp)
        }
    }

    @Composable
    private fun DescribeGetTraining(args: kotlinx.serialization.json.JsonObject?) {
        val id = args?.get("id")?.jsonPrimitive?.content ?: "?"
        Text("查看题单 #$id", fontSize = 13.sp)
    }

    @Composable
    private fun DescribeGetRecords(args: kotlinx.serialization.json.JsonObject?) {
        val pid = args?.get("pid")?.jsonPrimitive?.content ?: "?"
        val limit = args?.get("limit")?.jsonPrimitive?.content?.toIntOrNull()
        Text("获取提交记录 ", fontSize = 13.sp)
        Text(pid, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (limit != null) Text("，最近${limit}条", fontSize = 13.sp)
    }

    @Composable
    private fun DescribeGetRecord(args: kotlinx.serialization.json.JsonObject?) {
        val id = args?.get("id")?.jsonPrimitive?.content ?: "?"
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

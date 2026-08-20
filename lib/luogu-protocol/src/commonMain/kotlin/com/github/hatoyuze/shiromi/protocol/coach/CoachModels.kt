// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.protocol.coach

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ═══════════════════════════════════════════════════════════
// 学生记忆 (get_memory)
// ═══════════════════════════════════════════════════════════

@Serializable
data class LevelHistoryEntry(
    val topic: String,
    val score: Int,
    val date: String,
    val problem: String? = null,
)

@Serializable
data class StudentMemory(
    @SerialName("student_id") val studentId: String = "",
    @SerialName("last_topic") val lastTopic: String? = null,
    @SerialName("last_problem") val lastProblem: String? = null,
    @SerialName("level_score") val levelScore: Int = 50,
    @SerialName("level_history") val levelHistory: List<LevelHistoryEntry> = emptyList(),
    @SerialName("pain_points") val painPoints: List<String> = emptyList(),
    @SerialName("progress_points") val progressPoints: List<String> = emptyList(),
)

// ═══════════════════════════════════════════════════════════
// get_selected
// ═══════════════════════════════════════════════════════════

@Serializable
data class SelectedResult(
    val completed: List<String>,
    val count: Int = completed.size,
)

// ═══════════════════════════════════════════════════════════
// 教练应答 — init / thinking / finished
// ═══════════════════════════════════════════════════════════

@Serializable
data class CoachInitResponse(
    @SerialName("progress") val progress: String = "init",
    val selected: String,
    val content: String = "先看看这题吧",
)

@Serializable
data class CoachProcessingResponse(
    @SerialName("progress") val progress: String = "thinking",
    val content: String,
)

@Serializable
data class CoachFinishedResponse(
    @SerialName("progress") val progress: String = "finished",
    val recommend: List<String>? = null,
    /** Student-facing difficulty summary (agent-written); displayed to the student. */
    @SerialName("difficulty_summary") val difficultySummary: String = "",
    /** Learning record for the memory system only — never shown to the student. */
    val summary: String,
    val content: String,
)

// ═══════════════════════════════════════════════════════════
// 教练应答 — checkpoint
// ═══════════════════════════════════════════════════════════

@Serializable
data class CheckpointProblem(
    val id: String,
    val title: String,
    val synopsis: String,
    val difficulty: String? = null,
)

@Serializable
data class CheckpointSolution(
    val approach: String,
    val outline: List<String>,
    @SerialName("modules_completed") val modulesCompleted: List<String>,
    @SerialName("current_module") val currentModule: String,
    @SerialName("current_module_detail") val currentModuleDetail: String,
)

@Serializable
data class CheckpointStudent(
    @SerialName("level_so_far") val levelSoFar: Int,
    val strengths: List<String>,
    val weaknesses: List<String>,
    @SerialName("history_note") val historyNote: String? = null,
)

@Serializable
data class CoachCheckpointResponse(
    @SerialName("progress") val progress: String = "checkpoint",
    val problem: CheckpointProblem,
    val solution: CheckpointSolution,
    @SerialName("key_definitions") val keyDefinitions: Map<String, String>,
    val established: List<String>,
    val pending: List<String>,
    val student: CheckpointStudent,
    val next: String,
)

// ═══════════════════════════════════════════════════════════
// 联合类型 + 解析
// ═══════════════════════════════════════════════════════════

sealed interface CoachResponse {
    data class Init(val response: CoachInitResponse) : CoachResponse
    data class Processing(val response: CoachProcessingResponse) : CoachResponse
    data class Finished(val response: CoachFinishedResponse) : CoachResponse
    data class Checkpoint(val response: CoachCheckpointResponse) : CoachResponse
}

private val coachJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

fun parseCoachResponse(jsonStr: String): CoachResponse? {
    return try {
        val node = coachJson.parseToJsonElement(jsonStr).jsonObject
        when (node["progress"]?.jsonPrimitive?.content) {
            "init" -> CoachResponse.Init(coachJson.decodeFromString<CoachInitResponse>(jsonStr))
            "thinking" -> CoachResponse.Processing(coachJson.decodeFromString<CoachProcessingResponse>(jsonStr))
            "finished" -> CoachResponse.Finished(coachJson.decodeFromString<CoachFinishedResponse>(jsonStr))
            "checkpoint" -> CoachResponse.Checkpoint(coachJson.decodeFromString<CoachCheckpointResponse>(jsonStr))
            else -> null
        }
    } catch (_: Exception) { null }
}

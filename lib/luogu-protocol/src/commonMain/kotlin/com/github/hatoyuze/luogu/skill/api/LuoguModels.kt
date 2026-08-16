package com.github.hatoyuze.luogu.skill.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

// ═══════════════════════════════════════════════════════════
// 通用响应包装
// ═══════════════════════════════════════════════════════════

/**
 * 洛谷 content-only API 的通用响应结构。
 *
 * 所有以 `x-lentille-request: content-only` 发起的请求
 * 均返回此格式的 JSON。
 */
@Serializable
data class LuoguResponse<T>(
    val status: Int,
    val data: T,
) {
    val isSuccess: Boolean get() = status == 200
}

// ═══════════════════════════════════════════════════════════
// 题目列表（搜索 / 筛选）
// ═══════════════════════════════════════════════════════════

/** `/problem/list` 的 `data` 字段 */
@Serializable
data class ProblemListData(
    val problems: ProblemResult,
    val filter: FilterState? = null,
)

/** 题目结果集 */
@Serializable
data class ProblemResult(
    @SerialName("perPage") val perPage: Int,
    val count: Int,
    val result: List<ProblemSummary>,
)

/** 题目摘要（列表中的单条） */
@Serializable
data class ProblemSummary(
    val pid: String,
    val type: String,
    val name: String,
    val difficulty: Int,
    val tags: List<Int> = emptyList(),
    val submitted: Boolean = false,
    val accepted: Boolean = false,
    @SerialName("totalSubmit") val totalSubmit: Int = 0,
    @SerialName("totalAccepted") val totalAccepted: Int = 0,
    val flag: Int = 0,
    val provider: ProviderInfo? = null,
)

/** 题目提供者信息 */
@Serializable
data class ProviderInfo(
    val uid: Int,
    val name: String,
    val slogan: String = "",
    @SerialName("isAdmin") val isAdmin: Boolean = false,
    @SerialName("isBanned") val isBanned: Boolean = false,
    val color: String = "",
    @SerialName("ccfLevel") val ccfLevel: Int = 0,
)

/** 当前已应用的筛选条件 */
@Serializable
data class FilterState(
    val tag: List<List<Int>>? = null,
    val content: Boolean? = null,
    val keyword: String? = null,
    val difficulty: List<Int>? = null,
    val type: String? = null,
    val page: Int = 1,
)

// ═══════════════════════════════════════════════════════════
// 题目详情
// ═══════════════════════════════════════════════════════════

/** `/problem/<pid>` 的 `data` 字段 */
@Serializable
data class ProblemDetailData(
    val problem: ProblemDetail,
)

/** 题目描述内容（嵌套在 contenu 中） */
@Serializable
data class ProblemContent(
    val description: String = "",
    val background: String? = null,
    @SerialName("formatI") val inputFormat: String? = null,
    @SerialName("formatO") val outputFormat: String? = null,
    val hint: String? = null,
    val samples: List<Sample>? = null,
)

/** 题目完整详情 */
@Serializable
data class ProblemDetail(
    val pid: String,
    val name: String = "",
    /** 题目内容（contenu 子对象，含 description/background/samples 等） */
    val contenu: ProblemContent? = null,
    /** 题目标签 ID 列表 — 来自题面侧栏 */
    val tags: List<Int> = emptyList(),
    val difficulty: Int = 0,
    @SerialName("timeLimit") val timeLimit: Int? = null,
    @SerialName("memoryLimit") val memoryLimit: Int? = null,
    @SerialName("totalSubmit") val totalSubmit: Int = 0,
    @SerialName("totalAccepted") val totalAccepted: Int = 0,
    val origin: String? = null,
    val provider: ProviderInfo? = null,
    val submitted: Boolean = false,
    val accepted: Boolean = false,
    /** Samples are at problem level in the API, not inside contenu */
    @SerialName("samples") val rawSamples: List<Sample>? = null,
) {
    /** 便捷访问 description（来自 contenu） */
    val description: String get() = contenu?.description ?: ""
    val background: String? get() = contenu?.background
    val inputFormat: String? get() = contenu?.inputFormat
    val outputFormat: String? get() = contenu?.outputFormat
    val samples: List<Sample>? get() = rawSamples ?: contenu?.samples
    val hint: String? get() = contenu?.hint
}

/** 样例数据 — 支持两种格式：`[["input","output"]]` 和 `[{"input":"...","output":"..."}]` */
@Serializable(with = SampleSerializer::class)
data class Sample(
    val input: String,
    val output: String,
    val description: String? = null,
)

// ═══════════════════════════════════════════════════════════
// 题解
// ═══════════════════════════════════════════════════════════

/** `/problem/solution/<pid>` 的 `data` 字段 */
@Serializable
data class SolutionListData(
    val solutions: SolutionResult,
)

/** 题解结果集 */
@Serializable
data class SolutionResult(
    @SerialName("perPage") val perPage: Int = 20,
    val count: Int = 0,
    val result: List<SolutionSummary> = emptyList(),
)

/** 题解摘要 */
@Serializable
data class SolutionSummary(
    @SerialName("lid") val sid: String = "",
    val author: AuthorInfo,
    val title: String,
    /** Markdown 格式的解题思路 / 代码 */
    val content: String = "",
    val time: Long? = null,
    val score: Int? = null,
    val likes: Int? = null,
    val type: String? = null,
)

/** 题解作者信息 */
@Serializable
data class AuthorInfo(
    val uid: Int,
    val name: String,
    val avatar: String? = null,
    val color: String? = null,
    @SerialName("ccfLevel") val ccfLevel: Int? = null,
    @SerialName("isAdmin") val isAdmin: Boolean = false,
    val badge: String? = null,
)

// ═══════════════════════════════════════════════════════════
// 题单 (training)
// ═══════════════════════════════════════════════════════════

@Serializable
data class TrainingListData(
    val trainings: TrainingResult,
    val categories: List<TrainingCategory> = emptyList(),
)
@Serializable
data class TrainingResult(
    @SerialName("perPage") val perPage: Int,
    val count: Int,
    val result: List<TrainingSummary>,
)
@Serializable
data class TrainingSummary(
    val id: Int,
    val name: String,
    val type: Int = 0,
    @SerialName("problemCount") val problemCount: Int = 0,
    @SerialName("markCount") val markCount: Int = 0,
    val provider: ProviderInfo? = null,
)
@Serializable
data class TrainingCategory(
    val key: String,
    val name: String,
)
@Serializable
data class TrainingDetailData(
    val training: TrainingDetail,
)
@Serializable
data class TrainingDetail(
    val id: Int,
    val name: String,
    val description: String = "",
    val problems: List<ProblemSummary> = emptyList(),
    @SerialName("problemCount") val problemCount: Int = 0,
    @SerialName("markCount") val markCount: Int = 0,
    val provider: ProviderInfo? = null,
)

// ═══════════════════════════════════════════════════════════
// Tool 返回类型
// ═══════════════════════════════════════════════════════════

/** `luogu_get_filters` — 筛选条件 */
@Serializable
data class FilterMetadata(
    val tags: List<ResolvedTag>,
    @SerialName("difficulty_range") val difficultyRange: DifficultyRange,
    @SerialName("sort_options") val sortOptions: List<String>,
)

@Serializable
data class DifficultyRange(
    val min: Int = 1,
    val max: Int = 8,
    val levels: Map<String, String>,
)

/** `luogu_search_problems` — 搜索结果 */
@Serializable
data class SearchResult(
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val problems: List<SearchProblemItem>,
)

@Serializable
data class SearchProblemItem(
    val pid: String,
    val title: String,
    val difficulty: Int,
    @SerialName("difficulty_name") val difficultyName: String,
    val tags: List<String>,
    @SerialName("pass_rate") val passRate: Double,
    @SerialName("total_submissions") val totalSubmissions: Int,
)

/** `luogu_get_problem` — 题目详情 */
@Serializable
data class DifficultyScoresInfo(
    val overall: Double,
    val knowledge: Double,
    val thinking: Double,
)

@Serializable
data class ProblemInfo(
    val pid: String,
    val title: String,
    val difficulty: Int,
    @SerialName("difficulty_name") val difficultyName: String,
    val tags: List<String>,
    val description: String,
    @SerialName("input_format") val inputFormat: String? = null,
    @SerialName("output_format") val outputFormat: String? = null,
    val samples: List<Sample>? = null,
    val constraints: ProblemConstraints? = null,
    @SerialName("difficulty_scores") val difficultyScores: DifficultyScoresInfo? = null,
)

@Serializable
data class ProblemConstraints(
    @SerialName("time_limit") val timeLimit: String? = null,
    @SerialName("memory_limit") val memoryLimit: String? = null,
)

/** `luogu_get_solutions` — 题解列表 */
@Serializable
data class SolutionInfo(
    val pid: String,
    @SerialName("total_solutions") val totalSolutions: Int,
    val returned: Int,
    val truncated: Boolean,
    val solutions: List<SolutionItem>,
)

@Serializable
data class SolutionItem(
    val id: String,
    val author: String,
    val title: String,
    @SerialName("vote_count") val voteCount: Int = 0,
    val content: String,
)

/** `luogu_search_trainings` — 题单搜索 */
@Serializable
data class TrainingSearchResult(
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val trainings: List<TrainingItem>,
    val categories: List<TrainingCategory>,
)

@Serializable
data class TrainingItem(
    val id: Int,
    val name: String,
    @SerialName("problem_count") val problemCount: Int,
    @SerialName("mark_count") val markCount: Int,
    val provider: String? = null,
)

// ═══════════════════════════════════════════════════════════
// 做题记录 (practice)
// ═══════════════════════════════════════════════════════════

@Serializable
data class PracticeData(
    val passed: List<PassedProblem> = emptyList(),
    val submitted: List<PassedProblem> = emptyList(),
    @SerialName("user") val user: UserBrief? = null,
)
@Serializable
data class PassedProblem(
    val pid: String,
    val name: String,
    val difficulty: Int = 0,
    val type: String = "P",
)
@Serializable
data class UserBrief(
    val uid: Int,
    val name: String,
)

@Serializable
data class PracticeResult(
    val uid: Int,
    @SerialName("passed_count") val passedCount: Int,
    @SerialName("submitted_count") val submittedCount: Int,
    val passed: List<SearchProblemItem>,
    val submitted: List<SearchProblemItem>,
)

// ═══════════════════════════════════════════════════════════
// 提交记录 (record)
// ═══════════════════════════════════════════════════════════

@Serializable
data class RecordList(val records: RecordResult)
@Serializable
data class RecordResult(
    val count: Int = 0,
    @SerialName("perPage") val perPage: Int = 20,
    val result: List<RecordItem> = emptyList(),
)
@Serializable
data class RecordItem(
    val id: Long,
    val status: Int = 0,
    val score: Int = 0,
    val time: Int = 0,
    val memory: Int = 0,
    @SerialName("sourceCodeLength") val sourceCodeLength: Int = 0,
    @SerialName("submitTime") val submitTime: Long = 0,
    val language: Int = 0,
    @SerialName("enableO2") val enableO2: Boolean = false,
    val problem: ProblemSummary? = null,
)

@Serializable
data class RecordDetail(
    val compileResult: CompileResult? = null,
    val judgeResult: JudgeResult? = null,
)
@Serializable
data class CompileResult(
    val success: Boolean = false,
    val message: String? = null,
)
@Serializable
data class JudgeResult(
    val subtasks: List<SubtaskResult> = emptyList(),
    @SerialName("finishedCaseCount") val finishedCaseCount: Int = 0,
    val status: Int = 0,
    val time: Int = 0,
    val memory: Int = 0,
    val score: Int = 0,
)
@Serializable
data class SubtaskResult(
    val id: Int,
    val score: Int = 0,
    val status: Int = 0,
    val time: Int = 0,
    val memory: Int = 0,
    @SerialName("testCases") val testCases: Map<String, TestCaseResult> = emptyMap(),
)
@Serializable
data class TestCaseResult(
    val id: Int = 0,
    val status: Int = 0,
    val time: Int = 0,
    val memory: Int = 0,
    val score: Int = 0,
    val signal: Int = 0,
    @SerialName("exitCode") val exitCode: Int = 0,
    val description: String = "",
    @SerialName("subtaskID") val subtaskId: Int = 0,
)

@Serializable
data class RecordDetailData(
    val id: Long = 0,
    val status: Int = 0,
    val score: Int = 0,
    val time: Int = 0,
    val memory: Int = 0,
    @SerialName("sourceCodeLength") val sourceCodeLength: Int = 0,
    @SerialName("submitTime") val submitTime: Long = 0,
    val language: Int = 0,
    @SerialName("enableO2") val enableO2: Boolean = false,
    val problem: ProblemSummary? = null,
    @SerialName("sourceCode") val sourceCode: String = "",
    val detail: RecordDetail? = null,
)

/** `luogu_get_records` — 提交记录列表 */
@Serializable
data class RecordsResult(
    val pid: String,
    val uid: Int,
    @SerialName("total_count") val totalCount: Int,
    val records: List<RecordSummaryItem>,
)
@Serializable
data class RecordSummaryItem(
    val id: Long,
    val score: Int,
    val status: Int,
    @SerialName("time_ms") val timeMs: Int,
    @SerialName("memory_kb") val memoryKb: Int,
    @SerialName("code_length") val codeLength: Int,
    @SerialName("submit_time") val submitTime: Long,
    val language: Int,
)

/** `luogu_get_record` — 单条提交详情 */
@Serializable
data class RecordDetailInfo(
    val id: Long,
    val pid: String,
    val uid: Int,
    val score: Int,
    val status: Int,
    @SerialName("time_ms") val timeMs: Int,
    @SerialName("memory_kb") val memoryKb: Int,
    @SerialName("code_length") val codeLength: Int,
    @SerialName("submit_time") val submitTime: Long,
    val language: Int,
    @SerialName("source_code") val sourceCode: String,
    @SerialName("judge_result") val judgeResult: JudgeResult? = null,
)

/** `luogu_get_training` — 题单详情 */
@Serializable
data class TrainingInfo(
    val id: Int,
    val name: String,
    val description: String,
    @SerialName("problem_count") val problemCount: Int,
    @SerialName("mark_count") val markCount: Int,
    val problems: List<SearchProblemItem>,
    /** description 中提取的题单 ID，Agent 可直接用此调用 luogu_get_training */
    @SerialName("linked_training_ids") val linkedTrainingIds: List<Int>,
)

/** 自定义序列化器：支持 `[["input","output"]]` 和 `[{"input":"...","output":"..."}]` 两种格式 */
object SampleSerializer : KSerializer<Sample> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Sample", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Sample {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when {
            // Format: ["input_str", "output_str"]
            element is kotlinx.serialization.json.JsonArray && element.size >= 2 -> {
                Sample(
                    input = element[0].jsonPrimitive.content,
                    output = element[1].jsonPrimitive.content,
                )
            }
            // Format: {"input": "...", "output": "...", "description": "..."}
            element is JsonObject -> {
                Sample(
                    input = element["input"]?.jsonPrimitive?.content ?: "",
                    output = element["output"]?.jsonPrimitive?.content ?: "",
                )
            }
            else -> Sample("", "")
        }
    }

    override fun serialize(encoder: Encoder, value: Sample) {
        val array = buildJsonArray {
            add(kotlinx.serialization.json.JsonPrimitive(value.input))
            add(kotlinx.serialization.json.JsonPrimitive(value.output))
            if (value.description != null) add(kotlinx.serialization.json.JsonPrimitive(value.description))
        }
        (encoder as JsonEncoder).encodeJsonElement(array)
    }
}

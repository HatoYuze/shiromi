// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.remote

import com.github.hatoyuze.shiromi.gui.LuoguDatabase
import com.github.hatoyuze.shiromi.gui.config.ConfigService
import com.github.hatoyuze.shiromi.gui.data.Logger
import com.github.hatoyuze.shiromi.gui.data.log.LogCategory
import com.github.hatoyuze.shiromi.gui.data.local.DatabaseCacheStorage
import com.github.hatoyuze.shiromi.gui.domain.model.DailyProblemResult
import com.github.hatoyuze.shiromi.gui.platform.currentTimeMillis
import com.github.hatoyuze.shiromi.protocol.api.LuoguApi
import com.github.hatoyuze.shiromi.protocol.api.ProblemDetailData
import com.github.hatoyuze.shiromi.protocol.api.installLuoguTools
import io.github.hatoyuze.deepseek.protocol.api.Deepseek
import io.github.hatoyuze.deepseek.protocol.api.entity.Message
import io.github.hatoyuze.deepseek.protocol.api.entity.Role
import io.github.hatoyuze.deepseek.protocol.api.collectResponse
import io.github.hatoyuze.deepseek.protocol.api.deepseek
import io.github.hatoyuze.deepseek.protocol.api.onContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import shiromi.composeapp.generated.resources.Res
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Independent agent that manages a daily problem recommendation via Deepseek.
 *
 * Key design:
 * - Fresh [Deepseek] instance per query — context replayed from DB via [Deepseek.addMessage]
 * - [Mutex] ensures only one refresh runs at a time; concurrent requests are dropped
 * - [SupervisorJob] scope — child failures don't break the agent
 * - Reads config from [ConfigService] (global singleton)
 * - Problem detail is prefetched and stored in [state] so the UI reads it directly
 */
class DailyProblemAgent(
    private val db: LuoguDatabase,
    private val cacheStorage: DatabaseCacheStorage?,
) {
    // ══════════════════════════════════════════════════════
    // State
    // ══════════════════════════════════════════════════════

    data class DailyProblemState(
        val result: DailyProblemResult? = null,
        val problemDetail: ProblemDetailData? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(DailyProblemState())
    val state: StateFlow<DailyProblemState> = _state.asStateFlow()

    // ══════════════════════════════════════════════════════
    // Internals
    // ══════════════════════════════════════════════════════

    private val refreshMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastQueryDateDays: Long? = null

    private val tipsJson = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    private data class DailyProblemJsonResponse(
        val pid: String,
        val reason: String,
        val tips: List<String>,
    )

    // ══════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════

    /** Async, non-blocking. Call once after DI wiring. */
    fun initialize() {
        Logger.info(LogCategory.DAILY, "daily.initialize", "starting")
        scope.launch {
            if (refreshMutex.isLocked) { Logger.info(LogCategory.DAILY, "daily.initialize", "dropped: mutex locked"); return@launch }
            refreshMutex.withLock {
                try {
                    _state.update { it.copy(isLoading = true, error = null) }
                    loadContextAndQuery(isPassive = false)
                } catch (e: Exception) {
                    Logger.error(LogCategory.DAILY, "daily.initialize", "failed: ${e.message}")
                    _state.update { it.copy(isLoading = false, error = "初始化失败: ${e.message}") }
                }
            }
        }
    }

    /** User-triggered refresh. Drops if already refreshing. */
    fun refresh() {
        Logger.info(LogCategory.DAILY, "daily.refresh", "user requested")
        scope.launch {
            if (refreshMutex.isLocked) { Logger.info(LogCategory.DAILY, "daily.refresh", "dropped: mutex locked"); return@launch }
            refreshMutex.withLock {
                try {
                    _state.update { it.copy(isLoading = true, error = null) }
                    loadContextAndQuery(isPassive = true)
                } catch (e: Exception) {
                    Logger.error(LogCategory.DAILY, "daily.refresh", "failed: ${e.message}")
                    _state.update { it.copy(isLoading = false, error = "刷新失败: ${e.message}") }
                }
            }
        }
    }

    /** Check if day changed → auto-refresh. Drops if already refreshing. */
    fun checkDayChange() {
        val todayDays = today().toEpochDays()
        if (lastQueryDateDays != null && lastQueryDateDays == todayDays) return
        Logger.info(LogCategory.DAILY, "daily.dayChange", "day changed, auto-refreshing")
        scope.launch {
            if (refreshMutex.isLocked) { Logger.info(LogCategory.DAILY, "daily.dayChange", "dropped: mutex locked"); return@launch }
            refreshMutex.withLock {
                try {
                    _state.update { it.copy(isLoading = true, error = null) }
                    loadContextAndQuery(isPassive = false)
                } catch (e: Exception) {
                    Logger.error(LogCategory.DAILY, "daily.dayChange", "failed: ${e.message}")
                    _state.update { it.copy(isLoading = false, error = "自动刷新失败: ${e.message}") }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════
    // Core query pipeline
    // ══════════════════════════════════════════════════════

    private suspend fun loadContextAndQuery(isPassive: Boolean) {
        val todayDays = today().toEpochDays()

        // 1. Check cache for today (active refresh → use cache)
        val cached = db.luoguDatabaseQueries.selectDailyProblem(todayDays).executeAsOneOrNull()
        if (cached != null && !isPassive) {
            Logger.info(LogCategory.DAILY, "daily.cache", "hit for $todayDays (passive=$isPassive)")
            val result = cached.toDailyProblemResult()
            val detail = try { fetchProblemDetail(result.pid) } catch (_: Exception) { null }
            _state.update { it.copy(result = result, problemDetail = detail, isLoading = false) }
            lastQueryDateDays = todayDays
            return
        }

        Logger.info(LogCategory.DAILY, "daily.cache", "miss — querying Deepseek (passive=$isPassive)")
        // 2. Check API key availability
        val apiKey = ConfigService.apiKey
        if (apiKey.isBlank()) {
            _state.update { it.copy(isLoading = false, error = "API Key 未配置，请在设置中配置") }
            return
        }

        // 3. Create fresh Deepseek instance
        val ds = createDeepseekForQuery(apiKey)

        // 4. Replay historical context from DB (last 80 messages)
        val contextMessages = db.luoguDatabaseQueries.selectDailyProblemContext().executeAsList()
        contextMessages.takeLast(80).forEach { msg ->
            ds.addMessage(
                Message(
                    role = if (msg.role == "user") Role.User else Role.Assistance,
                    content = msg.content,
                )
            )
        }

        // 5. Build query
        val formattedDate = formatToday()
        val query = if (isPassive) {
            "今天是 $formattedDate, 用户选择了刷新每日一题，推荐重新推荐一个主题哦~"
        } else {
            "今天是 $formattedDate. 你需要更新今日的\"每日一题\""
        }

        // 6. Stream response — tool-call loop handled by Deepseek internally
        val fullResponse = StringBuilder()
        try {
            ds.chatStream(query)
                .onContent { fullResponse.append(it) }
                .collectResponse()
        } catch (e: Exception) {
            Logger.error(LogCategory.DAILY, "daily.query", "Deepseek API error: ${e.message}")
            _state.update { it.copy(isLoading = false, error = "Deepseek API 请求失败: ${e.message}") }
            return
        }

        val responseText = fullResponse.toString()

        // 7. Extract JSON from response
        val jsonStr = extractJsonBlock(responseText)
        if (jsonStr == null) {
            Logger.warn(LogCategory.DAILY, "daily.parse", "JSON extract failed: raw=${responseText.take(200)}")
            // Save raw response as context for debugging, but report error
            db.luoguDatabaseQueries.insertDailyProblemMessage("user", query, currentTimeMillis())
            db.luoguDatabaseQueries.insertDailyProblemMessage("assistant", responseText, currentTimeMillis())
            trimContext()
            _state.update { it.copy(
                isLoading = false,
                error = "AI 返回格式异常，未找到 JSON 响应，请重试"
            ) }
            return
        }

        // 8. Parse JSON
        val parsed: DailyProblemJsonResponse
        try {
            parsed = tipsJson.decodeFromString(jsonStr)
        } catch (e: Exception) {
            Logger.warn(LogCategory.DAILY, "daily.parse", "JSON parse failed: ${e.message} — raw=${responseText.take(200)}")
            db.luoguDatabaseQueries.insertDailyProblemMessage("user", query, currentTimeMillis())
            db.luoguDatabaseQueries.insertDailyProblemMessage("assistant", responseText, currentTimeMillis())
            trimContext()
            _state.update { it.copy(
                isLoading = false,
                error = "AI 返回 JSON 解析失败: ${e.message}，请重试"
            ) }
            return
        }

        val result = DailyProblemResult(
            pid = parsed.pid,
            reason = parsed.reason,
            tips = parsed.tips,
        )

        // 9. Persist user + assistant messages to context DB
        db.luoguDatabaseQueries.insertDailyProblemMessage("user", query, currentTimeMillis())
        db.luoguDatabaseQueries.insertDailyProblemMessage("assistant", responseText, currentTimeMillis())
        trimContext()

        // 10. Fetch problem detail from Luogu API (Agent prefetches — UI uses directly)
        val detail = try {
            fetchProblemDetail(result.pid)
        } catch (_: Exception) { null }

        // 11. Save cache
        val tipsEncoded = tipsJson.encodeToString(
            ListSerializer(serializer<String>()),
            result.tips
        )
        db.luoguDatabaseQueries.insertOrReplaceDailyProblem(
            date_epoch_days = todayDays,
            pid = result.pid,
            reason = result.reason,
            tips = tipsEncoded,
            created_at_ms = currentTimeMillis(),
        )

        // 12. Update state
        Logger.info(LogCategory.DAILY, "daily.refresh", "complete: pid=${result.pid}, detail=${detail != null}")
        _state.update { it.copy(
            result = result,
            problemDetail = detail,
            isLoading = false,
            error = null,
        ) }
        lastQueryDateDays = todayDays
    }

    // ══════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════

    private fun createDeepseekForQuery(apiKey: String): Deepseek {
        val prompt = loadDailyProblemPrompt()
        return deepseek(apiKey, sharedConfig = ConfigService.chatConfig) {
            model { custom(ConfigService.model) }  // avoid lazy api.models() call → zstd crash
            this.prompt = prompt
            tools { installLuoguTools(createLuoguApi()) }
        }
    }

    private fun createLuoguApi(): LuoguApi = LuoguApi(cacheStorage).apply {
        cookie = ConfigService.luoguCookie
        val uid = ConfigService.luoguUid.toIntOrNull()
        if (uid != null && uid > 0) uid0 = uid
    }

    private suspend fun fetchProblemDetail(pid: String): ProblemDetailData? {
        return try {
            val api = createLuoguApi()
            val response = api.getProblemDetail(pid)
            if (response.isSuccess) response.data else null
        } catch (_: Exception) { null }
    }

    /** Keep only the last 80 messages in context DB. */
    private suspend fun trimContext() {
        try {
            val count = db.luoguDatabaseQueries.selectDailyProblemContextCount().executeAsOne()
            if (count > 80) {
                db.luoguDatabaseQueries.deleteOldestContextMessages(count - 80)
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    private fun today(): LocalDate {
        val now = kotlin.time.Clock.System.now()
        return now.toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    private fun formatToday(): String {
        val t = today()
        return "${t.year}/${t.month.number.toString().padStart(2, '0')}/${t.day.toString().padStart(2, '0')}"
    }

    // ══════════════════════════════════════════════════════
    // JSON extraction
    // ══════════════════════════════════════════════════════

    companion object {
        /**
         * Extract the first complete JSON object from a potentially messy LLM response.
         * Handles responses wrapped in markdown code fences or with surrounding text.
         */
        fun extractJsonBlock(text: String): String? {
            // Try to find content inside markdown code fence first
            val fencePattern = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
            val fenceMatch = fencePattern.find(text)
            if (fenceMatch != null) {
                val inner = fenceMatch.groupValues[1].trim()
                val json = extractJsonObject(inner)
                if (json != null) return json
            }

            // Fall back to searching the raw text
            return extractJsonObject(text)
        }

        private fun extractJsonObject(text: String): String? {
            val startIdx = text.indexOf('{')
            if (startIdx < 0) return null

            var depth = 0
            for (i in startIdx until text.length) {
                when (text[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(startIdx, i + 1)
                    }
                }
            }
            return null
        }
    }
}

/**
 * Load the bundled daily problem system prompt from Compose resources.
 *
 * Returns a minimal placeholder if the resource cannot be loaded
 * (e.g. resource not yet configured).
 */
/**
 * Load the bundled daily problem system prompt.
 * Uses [kotlinx.coroutines.runBlocking] to bridge the suspend [Res.readBytes] call
 * — same pattern as [com.github.hatoyuze.shiromi.gui.platform.CoachPromptLoader].
 */
internal fun loadDailyProblemPrompt(): String {
    return try {
        kotlinx.coroutines.runBlocking {
            Res.readBytes("files/daily_problem_prompt.txt").decodeToString()
        }
    } catch (_: Exception) {
        // Fallback placeholder — user should replace with real prompt
        """
你是一个洛谷（Luogu）算法竞赛题目推荐助手。你的任务是根据用户需求，每天推荐一道合适的洛谷题目。

你可以使用提供的 Luogu API 工具来搜索和验证题目信息。

## 输出格式
你必须以严格的 JSON 格式回复，不要包含任何其他文字：
{"pid": "P1001", "reason": "选择理由（约60字，三句话，仅基于题面自身特点）", "tips": ["预判坑点1", "预判坑点2"]}

## 要求
- pid 必须是真实存在的洛谷题目编号
- reason 应简洁有力，说明为何推荐此题（约60字，三句话）
- tips 应基于用户画像和题目特点分析可能的易错点，而非解题思路
- 在推荐前可使用工具验证题目存在性
        """.trimIndent()
    }
}

// ── Mapper: SQLDelight DailyProblemCache -> domain DailyProblemResult ──

private val mapperTipsJson = Json { ignoreUnknownKeys = true; isLenient = true }

private fun com.github.hatoyuze.shiromi.gui.DailyProblemCache.toDailyProblemResult(): DailyProblemResult {
    val tips: List<String> = try {
        mapperTipsJson.decodeFromString(
            ListSerializer(serializer<String>()),
            tips
        )
    } catch (_: Exception) { emptyList() }
    return DailyProblemResult(pid = pid, reason = reason, tips = tips)
}

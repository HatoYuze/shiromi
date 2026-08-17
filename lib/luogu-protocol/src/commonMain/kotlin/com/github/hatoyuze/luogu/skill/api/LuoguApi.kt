package com.github.hatoyuze.luogu.skill.api

import com.github.hatoyuze.luogu.skill.cache.CacheStorage
import com.github.hatoyuze.luogu.skill.cache.LuoguCache
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

/**
 * Abstract interface for Luogu API operations.
 * Platform provides the concrete [LuoguApi] implementation.
 */
interface LuoguApiClient {
    val uid: Int
    var cookie: String
    suspend fun searchProblems(
        keyword: String? = null,
        difficultyMin: Int? = null,
        difficultyMax: Int? = null,
        tags: List<Int>? = null,
        type: ProblemType = ProblemType.LUOGU,
        page: Int = 1,
    ): LuoguResponse<ProblemListData>

    suspend fun getProblemDetail(pid: String): LuoguResponse<ProblemDetailData>
    suspend fun getSolutions(pid: String, page: Int = 1): LuoguResponse<SolutionListData>
    suspend fun searchTrainings(
        keyword: String? = null,
        type: String = "select",
        page: Int = 1,
    ): LuoguResponse<TrainingListData>
    suspend fun getTrainingDetail(id: Int): LuoguResponse<TrainingDetailData>
    suspend fun getPractice(uid: Int): PracticeData
    suspend fun getRecordList(pid: String, uid: Int, page: Int = 1): RecordResult
    suspend fun getRecordDetail(id: Long): RecordDetailData
}

/**
 * 洛谷 API 客户端实现。
 *
 * ## 使用方式
 *
 * ```kotlin
 * val api = LuoguApi().apply {
 *     cookie = "_uid=0; __client_id=xxx; C3VK=yyy"
 * }
 * ```
 *
 * Cookie 管理：只需设置一次初始 cookie（含 `_uid`、`__client_id`、可选的 `C3VK`），
 * 后续 `HttpCookies` 插件自动跟踪 `Set-Cookie` 响应头维持会话。
 */
class LuoguApi(
    private val cacheStorage: CacheStorage? = null,
) : LuoguApiClient {

    internal var http: LuoguHttpClient
    private val cache: LuoguCache? = cacheStorage?.let { LuoguCache(it) }

    init {
        http = LuoguHttpClient()
    }

    internal companion object {
        // TTL constants — literal values, no function calls
        internal const val TTL_PERMANENT = -1L
        internal const val TTL_5_MINUTES = 300_000L
        internal const val TTL_1_HOUR     = 3_600_000L
        internal const val TTL_1_DAY      = 86_400_000L
        internal const val TTL_3_DAYS     = 259_200_000L
        internal const val TTL_7_DAYS     = 604_800_000L

        /** FNV-1a 32-bit hash — dependency-free, deterministic. */
        internal fun hash(s: String): String {
            var h = 0x811c9dc5u
            for (b in s.encodeToByteArray()) {
                h = h xor b.toUInt()
                h *= 0x01000193u
            }
            return h.toString(16).padStart(8, '0')
        }

        /**
         * Build a deterministic cache key from a prefix and named parameters.
         * Parameters are sorted by key, blanks are dropped, values are lowercased.
         */
        internal fun cacheKey(prefix: String, vararg params: Pair<String, String>): String {
            val normalized = params
                .filter { (_, v) -> v.isNotBlank() }
                .sortedBy { (k, _) -> k }
                .joinToString("&") { (k, v) -> "$k=${v.trim().lowercase()}" }
            return if (normalized.isEmpty()) prefix
            else "${prefix}_${hash(normalized)}"
        }
    }

    internal constructor(http: LuoguHttpClient) : this(null) {
        this.http = http
    }
    internal constructor(http: LuoguHttpClient, cacheStorage: CacheStorage) : this(cacheStorage) {
        this.http = http
    }

    // ── 凭证 ──

    /** 浏览器 Cookie 字符串（`_uid=0; __client_id=xxx; C3VK=yyy`） */
    override var cookie: String
        get() = http.initialCookie
        set(value) { http.initialCookie = value }

    /** 释放底层 HTTP 引擎资源（一次性验证实例使用后调用）。 */
    fun close() = http.close()

    // ── UID ──

    /** 用户显式配置的 UID，优先于 cookie 解析 */
    var uid0: Int? = null

    /** 惰性解析 UID：uid0 ?: cookie._uid */
    override val uid: Int by lazy { uid0 ?: resolveUid() }

    private fun resolveUid(): Int {
        val m = Regex("_uid=(\\d+)").find(cookie)
        val id = m?.groupValues?.get(1)?.toIntOrNull()
        if (id != null && id > 0) return id
        throw IllegalStateException("未登录：cookie 中 _uid=0 或不存在，请配置 LuoguApi.uid0")
    }

    // ── 缓存 ──

    var cacheEnabled: Boolean = true

    // ── 统一缓存方法 ──

    /**
     * Unified cache-or-fetch strategy.
     *
     * @param cacheKey  deterministic cache key
     * @param ttlMs     cache TTL in millis; [TTL_PERMANENT] (-1) = never expire
     * @param block     HTTP call returning [LuoguResponse<T>]
     */
    private suspend inline fun <reified T> tryCache(
        cacheKey: String,
        ttlMs: Long,
        block: () -> LuoguResponse<T>,
    ): LuoguResponse<T> {
        if (!cacheEnabled || cache == null) return block()
        cache.load<LuoguResponse<T>>(cacheKey)?.let { return it }
        val result: LuoguResponse<T> = try { block() } catch (e: Exception) { throw e }
        if (result.isSuccess) cache.save(cacheKey, result, ttlMs)
        return result
    }

    // ── API ──

    override suspend fun searchProblems(
        keyword: String?,
        difficultyMin: Int?,
        difficultyMax: Int?,
        tags: List<Int>?,
        type: ProblemType,
        page: Int,
    ): LuoguResponse<ProblemListData> {
        val diffStr = if (difficultyMin != null || difficultyMax != null)
            "${difficultyMin ?: 1}-${difficultyMax ?: 8}" else ""
        val tagsStr = tags?.sorted()?.joinToString(",") ?: ""
        return tryCache(
            cacheKey("search",
                "type" to type.value, "page" to page.toString(),
                "kw" to (keyword ?: ""), "diff" to diffStr, "tags" to tagsStr,
            ),
            TTL_1_DAY,
        ) {
            val path = buildLuoguPath("/problem/list") {
                append("type", type.value)
                append("page", page.toString())
                if (!keyword.isNullOrBlank()) append("keyword", keyword.trim())
                if (difficultyMin != null || difficultyMax != null) {
                    val lo = difficultyMin ?: 1
                    val hi = difficultyMax ?: 8
                    append("difficulty", if (lo == hi) lo.toString() else "$lo,$hi")
                }
                if (!tags.isNullOrEmpty()) append("tag", tags.joinToString(","))
            }
            http.get(path)
        }
    }

    override suspend fun getProblemDetail(pid: String): LuoguResponse<ProblemDetailData> {
        val key = pid.trim().uppercase()
        return tryCache("problem_$key", TTL_PERMANENT) {
            http.get("/problem/$key")
        }
    }

    override suspend fun getSolutions(pid: String, page: Int): LuoguResponse<SolutionListData> {
        val key = pid.trim().uppercase()
        return tryCache("solution_${key}_p$page", TTL_3_DAYS) {
            val path = buildLuoguPath("/problem/solution/$key") {
                if (page > 1) append("page", page.toString())
            }
            http.get(path)
        }
    }

    // ── 题单 (training) ──

    override suspend fun searchTrainings(
        keyword: String?,
        type: String,
        page: Int,
    ): LuoguResponse<TrainingListData> {
        return tryCache(
            cacheKey("training_list", "type" to type, "page" to page.toString(), "kw" to (keyword ?: "")),
            TTL_7_DAYS,
        ) {
            val path = buildLuoguPath("/training/list") {
                append("type", type)
                append("page", page.toString())
                if (!keyword.isNullOrBlank()) append("keyword", keyword.trim())
            }
            http.get(path)
        }
    }

    override suspend fun getTrainingDetail(id: Int): LuoguResponse<TrainingDetailData> {
        return tryCache("training_$id", TTL_7_DAYS) {
            http.get("/training/$id")
        }
    }

    // ── 做题记录 (practice) ──

    /** Set up cookie refresh provider from the GUI layer. */
    fun initCookieRefresh(onRefresh: suspend () -> String?) {
        http.cookieRefreshProvider = object : LuoguHttpClient.CookieRefreshProvider {
            override suspend fun refreshCookie(): String? = onRefresh()
        }
    }

    /** Non-critical warm-up request to establish session cookies. Fails silently. */
    suspend fun warmUp() {
        try {
            http.getRaw("/", referer = "https://www.luogu.com.cn/")
        } catch (_: Exception) { /* non-critical */ }
    }

    /**
     * 验证当前会话是否已登录，返回服务端确认的当前用户 UID；未登录或请求失败返回 `null`。
     *
     * 探针使用登录门槛端点 `/user/notification`：未登录返回 401/302，已登录返回 JSON
     * 且 `user.uid` 为当前登录用户（旧版响应用 `currentUser.uid`，一并兼容）。
     * 供内嵌浏览器登录后验证提取的 Cookie 使用。
     *
     * 注意：2026-08 起服务端拒绝 `type=message` 查询参数（404 Invalid query parameter
     * "type"），因此不再携带该参数。
     */
    suspend fun fetchLoggedInUid(): Int? {
        return try {
            val raw = http.getRaw(
                "/user/notification?page=1&_contentOnly=1",
                referer = "https://www.luogu.com.cn/",
            )
            val root = LuoguHttpClient.json.parseToJsonElement(raw).jsonObject
            // 逐键安全回退：`jsonObject` 是强制转换，`"user": null` 时
            // `root["user"]` 为非空 JsonNull 会抛异常，必须用 `as?` 转换，
            // 才能在没有 `user` 对象时落到旧版 `currentUser` 字段。
            val user = root["user"] as? JsonObject ?: root["currentUser"] as? JsonObject ?: return null
            user["uid"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it > 0 }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 协程取消必须向上传播，不能被当作“未登录”吞掉
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun getPractice(uid: Int): PracticeData {
        val cacheKey = "practice_$uid"
        if (cacheEnabled) {
            cache?.load<PracticeData>(cacheKey)?.let { return it }
        }
        val json = http.getRaw("/user/$uid/practice")
        val practiceJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        val node = practiceJson.parseToJsonElement(json).jsonObject
        val result = practiceJson.decodeFromJsonElement(serializer<PracticeData>(), node.getValue("data"))
        if (cacheEnabled) cache?.save(cacheKey, result, TTL_1_HOUR)
        return result
    }

    // ── 提交记录 (record) ──

    @Serializable
    data class FeRecordList(val currentData: FeCurrentData)
    @Serializable
    data class FeCurrentData(val records: RecordResult)

    override suspend fun getRecordList(pid: String, uid: Int, page: Int): RecordResult {
        val key = buildLuoguPath("/record/list") {
            append("pid", pid.trim().uppercase())
            append("user", uid.toString())
            if (page > 1) append("page", page.toString())
        }
        val cacheKey = "rec_list_$key"
        if (cacheEnabled) cache?.load<RecordResult>(cacheKey)?.let { return it }
        val result = http.getFeInjection<FeRecordList>(key).currentData.records
        if (cacheEnabled) cache?.save(cacheKey, result, TTL_5_MINUTES)
        return result
    }

    @Serializable
    data class FeRecordDetail(val currentData: FeRecordDetailData)
    @Serializable
    data class FeRecordDetailData(val record: RecordDetailData)

    override suspend fun getRecordDetail(id: Long): RecordDetailData {
        val cacheKey = "rec_detail_$id"
        if (cacheEnabled) cache?.load<RecordDetailData>(cacheKey)?.let { return it }
        val r = http.getFeInjection<FeRecordDetail>("/record/$id").currentData.record
        if (cacheEnabled) cache?.save(cacheKey, r, TTL_1_DAY)
        return r
    }

    // ── 缓存刷新 —— 跳过缓存读、始终网络获取、成功时写入 ──

    /** Force-refresh problem detail from network, overwriting cache on success. */
    suspend fun refreshProblemDetail(pid: String): LuoguResponse<ProblemDetailData> {
        val key = pid.trim().uppercase()
        val r: LuoguResponse<ProblemDetailData> = http.get("/problem/$key")
        if (r.isSuccess) cache?.save("problem_$key", r, TTL_PERMANENT)
        return r
    }

    /** Force-refresh training detail from network, overwriting cache on success. */
    suspend fun refreshTrainingDetail(id: Int): LuoguResponse<TrainingDetailData> {
        val r: LuoguResponse<TrainingDetailData> = http.get("/training/$id")
        if (r.isSuccess) cache?.save("training_$id", r, TTL_7_DAYS)
        return r
    }

    // ── URL helper ──

    private fun buildLuoguPath(path: String, block: ParametersBuilder.() -> Unit): String {
        val url = URLBuilder().apply {
            this.path(path)
            this.parameters.apply(block)
        }.build()
        return if (url.encodedQuery.isNotEmpty()) "${url.encodedPath}?${url.encodedQuery}"
        else url.encodedPath
    }
}

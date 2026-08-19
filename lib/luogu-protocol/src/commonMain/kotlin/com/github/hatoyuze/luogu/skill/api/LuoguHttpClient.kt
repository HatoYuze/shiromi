// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.skill.api

import com.github.hatoyuze.luogu.skill.platform.createPlatformHttpClient
import com.github.hatoyuze.luogu.skill.platform.decompressGzip
import com.github.hatoyuze.luogu.skill.platform.percentDecode
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cookies.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.concurrent.Volatile
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * 洛谷 HTTP 客户端封装。
 *
 * 使用 [HttpCookies] 插件自动管理会话 Cookie（含 C3VK 动态轮转）。
 * CSRF 防护通过 Cookie 实现，无需额外的 header。
 *
 * **凭证模型**：用户通过 `cookie` 属性注入初始 Cookie
 * （如 `_uid=0; __client_id=xxx; C3VK=yyy`），后续请求由 [HttpCookies]
 * 自动跟踪 `Set-Cookie` 响应头维持会话。
 */
internal class LuoguHttpClient {

    companion object {
        const val BASE_URL = "https://www.luogu.com.cn"

        // ── 请求头常量 ──
        const val HEADER_LENTILLE = "x-lentille-request"
        const val LENTILLE_CONTENT_ONLY = "content-only"

        const val DEFAULT_REFERER = "https://www.luogu.com.cn/problem/list"

        /** 使用 HAR 中提取的浏览器 UA */
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"

        internal val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /** Minimum interval between requests to avoid triggering Cloudflare rate limits */
        private const val MIN_REQUEST_INTERVAL_MS = 500L
    }

    /**
     * Callback for refreshing expired/blocked cookies.
     *
     * When a 401 or 403 is detected, the client calls [refreshCookie] before retrying.
     * Implementations should re-fetch the Luogu cookie string from config or prompt the user.
     */
    interface CookieRefreshProvider {
        suspend fun refreshCookie(): String?
    }

    // ── Rate limiting ──

    private val throttleMutex = Mutex()
    @Volatile
    private var lastRequestTimeMs = 0L

    private suspend fun throttle() {
        throttleMutex.withLock {
            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val elapsed = now - lastRequestTimeMs
            if (elapsed < MIN_REQUEST_INTERVAL_MS) {
                delay(MIN_REQUEST_INTERVAL_MS - elapsed)
            }
            lastRequestTimeMs = kotlin.time.Clock.System.now().toEpochMilliseconds()
        }
    }

    // ── Cookie 存储 ──

    /** 线程安全的 cookie 存储，HttpCookies 插件使用 */
    private val cookieStorage = AcceptAllCookiesStorage()

    // ── 初始凭证 ──

    /** 用户提供的初始 cookie 字符串（仅用于首次请求前的种子填充） */
    var initialCookie: String = ""

    /** Optional provider for refreshing cookies on 401/403 */
    var cookieRefreshProvider: CookieRefreshProvider? = null

    // ── HTTP 客户端 ──

    private val client: HttpClient

    constructor() {
        client = createPlatformHttpClient(json, cookieStorage) {
            //install(Logging) { level = LogLevel.ALL }
        }
    }

    /** 测试用构造器 */
    internal constructor(client: HttpClient) {
        this.client = client
    }

    /** 关闭底层 HttpClient，释放引擎线程与连接池资源。 */
    fun close() {
        client.close()
    }

    // ── 请求构建 ──

    private fun HttpRequestBuilder.luoguHeaders(referer: String = DEFAULT_REFERER) {
        header(HEADER_LENTILLE, LENTILLE_CONTENT_ONLY)
        header("Referer", referer)
        header(HttpHeaders.UserAgent, DEFAULT_USER_AGENT)
        header(HttpHeaders.Accept, "application/json, text/plain, */*")
        header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9,en;q=0.8")
        header(HttpHeaders.AcceptEncoding, "gzip, deflate, br")
        header("Sec-Ch-Ua", "\"Google Chrome\";v=\"149\", \"Chromium\";v=\"149\"")
        header("Sec-Ch-Ua-Mobile", "?0")
        header("Sec-Ch-Ua-Platform", "\"Windows\"")
        header("Sec-Fetch-Dest", "empty")
        header("Sec-Fetch-Mode", "cors")
        header("Sec-Fetch-Site", "same-origin")
    }

    // ── 公共方法 ──

    /**
     * 发起 GET 请求。
     *
     * 首次请求前将 [initialCookie]（可为空）解析填充到 [cookieStorage]。
     * 若初始 cookie 为空，服务器会自动通过 Set-Cookie + 302 建立会话。
     */
    suspend inline fun <reified T> get(path: String, referer: String = DEFAULT_REFERER, maxRetries: Int = 2): T {
        throttle()
        ensureCookiesSeeded()
        var lastException: Exception? = null
        repeat(maxRetries + 1) { attempt ->
            try {
                val response = client.get("$BASE_URL$path") { luoguHeaders(referer) }
                checkLuoguStatus(response)
                val body = response.readDecompressed()
                checkCloudflareChallenge(body)
                return json.decodeFromString<T>(body)
            } catch (e: LuoguApiException) {
                lastException = e
                if (e.httpStatus == 429 || e.httpStatus >= 500) {
                    if (attempt < maxRetries) {
                        delay((1000L * (attempt + 1)).coerceAtMost(10000L))
                        return@repeat
                    }
                }
                if (e.httpStatus == 401 || e.httpStatus == 403) {
                    if (attempt < maxRetries && tryRefreshCookie()) {
                        return@repeat
                    }
                }
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    delay(500L * (attempt + 1))
                    return@repeat
                }
                throw e
            }
        }
        throw lastException!!
    }

    /** 发起 GET 请求，返回原始响应文本 */
    suspend fun getRaw(path: String, referer: String = DEFAULT_REFERER): String {
        throttle()
        ensureCookiesSeeded()
        val response = client.get("$BASE_URL$path") { luoguHeaders(referer) }
        checkLuoguStatus(response)
        val body = response.readDecompressed()
        checkCloudflareChallenge(body)
        return body
    }

    /** 从 HTML 中提取 _feInjection 的 JSON 数据并反序列化为 [T] */
    suspend inline fun <reified T> getFeInjection(path: String, referer: String = DEFAULT_REFERER): T {
        val html = getRaw(path, referer)
        val encoded = Regex("""_feInjection\s*=\s*JSON\.parse\(decodeURIComponent\("(.+?)"\)\)""")
            .find(html)?.groupValues?.get(1)
            ?: throw LuoguApiException("未找到 _feInjection 数据", 0)
        val decoded = percentDecode(encoded)
        return json.decodeFromString<T>(decoded)
    }

    /** 读取响应体，若为 gzip 则自动解压 */
    internal suspend fun io.ktor.client.statement.HttpResponse.readDecompressed(): String {
        val encoding = headers[HttpHeaders.ContentEncoding] ?: ""
        return if (encoding.contains("gzip", ignoreCase = true)) {
            val raw = body<ByteArray>()
            decompressGzip(raw).decodeToString()
        } else {
            bodyAsText()
        }
    }

    /** 将用户提供的 cookie 字符串解析并填充到 cookie storage（仅首次） */
    private var cookiesSeeded = false

    private suspend fun ensureCookiesSeeded() {
        if (cookiesSeeded) return
        cookiesSeeded = true

        val baseUrl = Url(BASE_URL)
        for (part in initialCookie.split(";")) {
            val trimmed = part.trim()
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx <= 0) continue
            val name = trimmed.substring(0, eqIdx).trim()
            val value = trimmed.substring(eqIdx + 1).trim()
            if (name.isNotEmpty() && value.isNotEmpty()) {
                cookieStorage.addCookie(
                    baseUrl,
                    Cookie(
                        name = name,
                        value = value,
                        domain = "www.luogu.com.cn",
                        path = "/",
                        expires = null,
                        httpOnly = false,
                        secure = true,
                    )
                )
            }
        }
    }

    // ── Cookie refresh ──

    private suspend fun tryRefreshCookie(): Boolean {
        val provider = cookieRefreshProvider ?: return false
        return try {
            val newCookie = provider.refreshCookie() ?: return false
            initialCookie = newCookie
            cookiesSeeded = false
            ensureCookiesSeeded()
            true
        } catch (_: Exception) { false }
    }

    private fun checkCloudflareChallenge(body: String) {
        if (body.contains("cf-browser-verify") || body.contains("Just a moment")
            || body.contains("cf_chl_opt") || body.contains("cf-chl")) {
            throw LuoguApiException("Cloudflare challenge detected — cookie may need refresh", httpStatus = 503)
        }
    }

    // ── 错误处理 ──

    private suspend fun checkLuoguStatus(response: HttpResponse) {
        val status = response.status
        when {
            status == HttpStatusCode.OK -> return

            status == HttpStatusCode.Forbidden -> throw LuoguApiException(
                message = "请求被拒绝 (403)，Cookie 可能已过期，请重新获取",
                httpStatus = status.value,
            )

            status == HttpStatusCode.NotFound -> throw LuoguApiException(
                message = "请求的资源不存在 (404)",
                httpStatus = status.value,
            )

            status == HttpStatusCode.TooManyRequests -> throw LuoguApiException(
                message = "请求频率过高 (429)，请稍后再试",
                httpStatus = status.value,
            )
            status == HttpStatusCode.Unauthorized -> throw LuoguApiException(
                message = "当前用户未在默认浏览器登录洛谷账号 (401)，无法获取需要账号验证的内容",
                httpStatus = status.value
            )

            status.value >= 500 -> throw LuoguApiException(
                message = "洛谷服务器错误 (${status.value})，请稍后再试",
                httpStatus = status.value,
            )

            else -> throw LuoguApiException(
                message = "请求失败 (${status.value})",
                httpStatus = status.value,
                responseBody = response.bodyAsText().take(500),
            )
        }
    }
}

class LuoguApiException(
    message: String,
    val httpStatus: Int,
    val responseBody: String? = null,
) : Exception(message)

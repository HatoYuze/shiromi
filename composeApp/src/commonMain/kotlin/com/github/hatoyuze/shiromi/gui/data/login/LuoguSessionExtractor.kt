// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.login

import com.github.hatoyuze.shiromi.gui.platform.getWebViewCookieString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.kagg886.wvbridge.WebViewController

/** 登录后从 WebView 提取到的洛谷会话。 */
data class LuoguSession(
    /** 登录用户 UID（0/负值/未知为 null）。 */
    val uid: Int?,
    /** 登录用户名（页面 _feInjection 提供，可能为 null）。 */
    val username: String?,
    /** 种子 Cookie 串（`_uid=…; __client_id=…; C3VK=…`），供 Ktor 客户端使用。 */
    val cookieString: String,
    /** 是否包含 HttpOnly 的 `__client_id`（原生读取通道的标志）。 */
    val hasClientId: Boolean,
) {
    val hasUid: Boolean get() = uid != null && uid > 0
}

/**
 * 从内嵌 WebView 提取洛谷登录会话。
 *
 * 通道（按优先级）：
 * 1. 原生 WebView cookie 读取（桌面 fork / Android CookieManager）——含 HttpOnly 的
 *    `__client_id`，这是认证必需的；
 * 2. `document.cookie`——交叉补充（`_uid` 若非 HttpOnly 也会出现在这里）；
 * 3. 页面 `window._feInjection.currentUser`——提供 uid/username（无论 cookie 是否 HttpOnly）。
 */
object LuoguSessionExtractor {
    const val LOGIN_URL = "https://www.luogu.com.cn/auth/login"
    const val BASE_URL = "https://www.luogu.com.cn"

    /** 洛谷会话种子所需 cookie 白名单。 */
    private val SESSION_COOKIES = setOf("_uid", "__client_id", "C3VK")

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun extract(controller: WebViewController<*>): LuoguSession {
        // 各通道独立容错：原生引擎未就绪/页面未加载完成时返回 null，不中断整体提取。
        val nativeCookie = try {
            getWebViewCookieString(controller, BASE_URL)
        } catch (_: Exception) {
            null
        }
        val jsCookie = try {
            controller.bridge.evaluateScript("document.cookie")
        } catch (_: Exception) {
            null
        }
        val userJson = try {
            controller.bridge.evaluateScript(
                "window._feInjection && window._feInjection.currentUser ? " +
                    "JSON.stringify(window._feInjection.currentUser) : 'null'",
            )
        } catch (_: Exception) {
            null
        }

        val merged = LinkedHashMap<String, String>()
        parseCookieString(nativeCookie).forEach { (k, v) -> if (v.isNotBlank()) merged[k] = v }
        parseCookieString(jsCookie).forEach { (k, v) -> if (v.isNotBlank() && !merged.containsKey(k)) merged[k] = v }

        val (feUid, feName) = parseUserJson(userJson)
        val uid = merged["_uid"]?.toIntOrNull()?.takeIf { it > 0 } ?: feUid

        return LuoguSession(
            uid = uid,
            username = feName,
            cookieString = buildCookieString(merged),
            hasClientId = merged.containsKey("__client_id"),
        )
    }

    /** 解析 `name=value; name=value` cookie 串为有序 map。 */
    internal fun parseCookieString(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (part in raw.split(';')) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue
            val eq = trimmed.indexOf('=')
            if (eq <= 0) continue
            val name = trimmed.substring(0, eq).trim()
            val value = trimmed.substring(eq + 1).trim()
            if (name.isNotEmpty()) result[name] = value
        }
        return result
    }

    /**
     * 会话预检：cookie 串中是否存在已登录 `_uid`（> 0）。
     * 匿名会话无 `_uid`（服务端仅在登录后下发），故这是"已登录"的可靠信号，
     * 用于外链登录等中间导航时避免误触发提取。
     */
    internal fun hasLoggedInUid(cookieString: String?): Boolean =
        parseCookieString(cookieString)["_uid"]?.toIntOrNull()?.takeIf { it > 0 } != null

    /** 按会话白名单组装种子 cookie 串，保持插入顺序。 */
    internal fun buildCookieString(cookies: Map<String, String>): String = cookies
        .filterKeys { it in SESSION_COOKIES }
        .mapNotNull { (k, v) -> if (v.isNotBlank()) "$k=$v" else null }
        .joinToString("; ")

    /** 解析 `_feInjection.currentUser` JSON 串，返回 (uid, username)。 */
    internal fun parseUserJson(userJson: String?): Pair<Int?, String?> {
        if (userJson.isNullOrBlank() || userJson == "null") return null to null
        return try {
            val obj = json.parseToJsonElement(userJson).jsonObject
            val uid = obj["uid"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()?.takeIf { it > 0 }
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            uid to name
        } catch (_: Exception) {
            null to null
        }
    }
}

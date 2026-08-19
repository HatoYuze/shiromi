// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.onboarding

/**
 * 引导页输入校验（纯函数，可单测）。
 * 仅为格式门；真实可用性由使用时的请求/验证决定。
 */
internal fun isValidApiKey(value: String): Boolean {
    val trimmed = value.trim()
    // DeepSeek Key 形如 sk-<32+ 字符>；拒绝空后缀（如 "sk-"）。
    return trimmed.startsWith("sk-") && trimmed.length >= 8
}

// 边界：`_uid` 位于串首或分号之后（容忍空白），避免 `my_uid=5` 这类子串误匹配。
private val UID_COOKIE_REGEX = Regex("(?:^|;\\s*)\\s*_uid=(\\d+)")
private val POSITIVE_UID_COOKIE_REGEX = Regex("(?:^|;\\s*)\\s*_uid=[1-9]\\d*")

/** 洛谷 Cookie 基本格式门：必须包含正数 `_uid=`（`_uid=0` 为匿名，不视为已登录配置）。 */
internal fun looksLikeLuoguCookie(value: String): Boolean =
    POSITIVE_UID_COOKIE_REGEX.containsMatchIn(value)

/** 从 cookie 串提取 UID（与 LuoguApi 的 `_uid=(\\d+)` 解析一致）；无匹配/匿名返回 null。 */
internal fun extractLuoguUid(cookie: String): Int? =
    UID_COOKIE_REGEX.find(cookie)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

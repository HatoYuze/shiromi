// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.login

import com.github.hatoyuze.luogu.skill.api.LuoguApi

/**
 * 用提取到的会话做一次真实 API 验证（登录门槛端点），返回服务端确认的 UID。
 *
 * 使用一次性 [LuoguApi]（无缓存、不污染 [com.github.hatoyuze.luogu.gui.data.remote.LuoguApiProvider]
 * 的运行中单例）；验证通过后由调用方把 Cookie/UID 写入配置。
 */
object LuoguLoginVerifier {
    suspend fun verify(session: LuoguSession): Int? {
        val api = LuoguApi(cacheStorage = null)
        try {
            api.cookie = session.cookieString
            session.uid?.takeIf { it > 0 }?.let { api.uid0 = it }
            return api.fetchLoggedInUid()
        } finally {
            api.close() // 释放一次性 HttpClient 的引擎资源
        }
    }
}

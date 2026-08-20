// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.protocol.platform

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * Android 平台 HTTP 客户端：使用 OkHttp 引擎（与 deepseek-helper 的 Android 默认一致）。
 */
actual fun createPlatformHttpClient(
    json: Json,
    cookieStorage: CookiesStorage,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) { json(json) }
    install(HttpCookies) { storage = cookieStorage }
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 15_000
    }
    followRedirects = true
    block()
}

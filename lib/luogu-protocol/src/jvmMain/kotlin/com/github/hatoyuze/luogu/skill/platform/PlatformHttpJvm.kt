// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.skill.platform

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

actual fun createPlatformHttpClient(
    json: Json,
    cookieStorage: CookiesStorage,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(CIO) {
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

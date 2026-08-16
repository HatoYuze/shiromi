package com.github.hatoyuze.luogu.skill.platform

import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.cookies.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * iOS 平台 HTTP 客户端：使用 Darwin 引擎（NSURLSession）。
 */
actual fun createPlatformHttpClient(
    json: Json,
    cookieStorage: CookiesStorage,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(Darwin) {
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

// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.skill.platform

import io.ktor.client.*
import io.ktor.client.plugins.cookies.CookiesStorage
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer

/**
 * Create a platform-specific HTTP client with common plugins (ContentNegotiation,
 * HttpCookies, HttpTimeout, followRedirects) pre-configured.
 */
expect fun createPlatformHttpClient(
    json: Json,
    cookieStorage: CookiesStorage,
    block: HttpClientConfig<*>.() -> Unit,
): HttpClient

/**
 * Decompress gzip-compressed bytes.
 *
 * Common implementation backed by okio's [GzipSource], shared by all KMP targets.
 */
fun decompressGzip(bytes: ByteArray): ByteArray {
    val buffer = Buffer().apply { write(bytes) }
    val source = GzipSource(buffer).buffer()
    try {
        return source.readByteArray()
    } finally {
        source.close()
    }
}

/** Compress bytes into gzip format (common implementation via okio [GzipSink]). */
fun compressGzip(bytes: ByteArray): ByteArray {
    val buffer = Buffer()
    val sink = GzipSink(buffer).buffer()
    try {
        sink.write(bytes)
    } finally {
        // GzipSink writes the trailer on close; closing is mandatory
        sink.close()
    }
    return buffer.readByteArray()
}

/**
 * Pure Kotlin URL percent-decoding.
 * Replaces java.net.URLDecoder.decode(s, "UTF-8").
 * Handles %XX hex sequences and '+' → space.
 */
fun percentDecode(encoded: String): String {
    val sb = StringBuilder(encoded.length)
    var i = 0
    while (i < encoded.length) {
        when (val c = encoded[i]) {
            '%' -> {
                if (i + 2 < encoded.length) {
                    val hi = encoded[i + 1].digitToIntOrNull(16) ?: -1
                    val lo = encoded[i + 2].digitToIntOrNull(16) ?: -1
                    if (hi >= 0 && lo >= 0) {
                        sb.append(((hi shl 4) or lo).toChar())
                        i += 3
                    } else {
                        sb.append(c); i++
                    }
                } else {
                    sb.append(c); i++
                }
            }
            '+' -> { sb.append(' '); i++ }
            else -> { sb.append(c); i++ }
        }
    }
    return sb.toString()
}

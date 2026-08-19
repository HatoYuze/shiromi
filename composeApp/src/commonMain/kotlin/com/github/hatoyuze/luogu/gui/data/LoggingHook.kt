// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data

import com.github.hatoyuze.luogu.gui.platform.currentTimeMillis
import io.github.hatoyuze.deepseek.protocol.net.HttpHook

/**
 * Bridges [HttpHook] events to [Logger].
 *
 * SSE responses are NOT accumulated: only the event count is tracked and the
 * response body is stored as a short summary, so a long conversation stream
 * never sits in memory or on disk.
 */
class LoggingHook : HttpHook {

    private data class PendingRequest(
        val method: String,
        val url: String,
        val headers: String,
        val body: String?,
        val timestamp: Long,
    )

    private val pending = mutableListOf<PendingRequest>()
    private var sseEventCount = 0

    override fun onRequest(method: String, url: String, headers: Map<String, String>, body: String?) {
        val hdr = headers.entries.joinToString(" | ") { "${it.key}: ${it.value}" }
        pending.add(PendingRequest(method, url, hdr, body, currentTimeMillis()))
    }

    override fun onSseEvent(data: String) {
        if (data != "[DONE]") {
            sseEventCount++
        }
    }

    override fun onResponse(method: String, url: String, status: Int, headers: Map<String, String>, body: String?) {
        val hdr = headers.entries.joinToString(" | ") { "${it.key}: ${it.value}" }
        val req = pending.removeFirstOrNull()
        val durationMs = req?.timestamp?.let { currentTimeMillis() - it }
        val responseSummary = if (sseEventCount > 0) "<SSE stream: $sseEventCount events>" else body
        Logger.http(
            method = method,
            url = url,
            status = status,
            requestHeaders = req?.headers,
            requestBody = req?.body,
            responseHeaders = hdr,
            responseSummary = responseSummary,
            sseEventCount = sseEventCount,
            durationMs = durationMs,
        )
        sseEventCount = 0
    }
}

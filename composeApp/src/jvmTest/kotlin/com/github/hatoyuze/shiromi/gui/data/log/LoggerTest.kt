// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.log

import com.github.hatoyuze.shiromi.gui.data.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoggerTest {

    private fun init(sink: TestLogSink, minLevel: LogLevel = LogLevel.INFO, captureAssistant: Boolean = true) {
        Logger.init(sink = sink, minLevel = minLevel, captureAssistantMessages = captureAssistant, maxBodyBytes = 20)
    }

    @Test
    fun minLevel_shouldDropDebugEntries() {
        val sink = TestLogSink()
        init(sink)
        Logger.info(LogCategory.APP, "test.info", "hello")
        Logger.debug(LogCategory.APP, "test.debug", "verbose")
        assertEquals(1, sink.entries.size)
        assertEquals("test.info", sink.entries[0].event)
    }

    @Test
    fun assistantMessage_shouldBeControllable() {
        val sink = TestLogSink()
        init(sink, captureAssistant = false)
        Logger.assistantMessage("s1", "m1", "content")
        assertTrue(sink.entries.isEmpty())

        init(sink, captureAssistant = true)
        Logger.assistantMessage("s1", "m1", "content", segmentsJson = "[]", finishReason = "stop", totalTokens = 42)
        assertEquals(1, sink.entries.size)
        val entry = sink.entries[0]
        assertEquals(LogCategory.ASSISTANT, entry.category)
        assertEquals("s1", entry.sessionId)
        val assistant = assertIs<LogDetail.Assistant>(Logger.parseDetail(entry)).value
        assertEquals("m1", assistant.messageId)
        assertEquals("content", assistant.content)
        assertEquals("[]", assistant.segmentsJson)
        assertEquals(42L, assistant.totalTokens)
    }

    @Test
    fun http_shouldTruncateBodyAndRecordSseSummary() {
        val sink = TestLogSink()
        init(sink)
        Logger.http(
            method = "POST",
            url = "https://api.deepseek.com/chat/completions",
            status = 200,
            requestHeaders = "authorization: Bearer sk-abcdefghijklmnopqrst",
            requestBody = "x".repeat(100),
            responseSummary = "<SSE stream: 5 events>",
            sseEventCount = 5,
        )
        assertEquals(1, sink.entries.size)
        val entry = sink.entries[0]
        assertEquals(LogCategory.HTTP, entry.category)
        val detail = (Logger.parseDetail(entry) as LogDetail.Http).value
        assertEquals(5, detail.sseEventCount)
        assertTrue(detail.requestBody!!.length <= 20 + "\n...[truncated]".length)
        assertFalse(detail.requestHeaders!!.contains("sk-abcdefghijklmnopqrst"))
    }

    @Test
    fun sanitizeSensitive_shouldMaskKeysAndCredentials() {
        val masked = sanitizeSensitive("key=sk-1234567890abcdef cookie: _uid=42; abc")
        assertFalse(masked.contains("sk-1234567890abcdef"))
        assertTrue(masked.contains("<sk-"))
        assertFalse(masked.contains("_uid=42"))
        assertTrue(masked.contains("<redacted>"))
    }

    @Test
    fun sanitizeSensitive_shouldRedactMultiSegmentAndShortCookies() {
        // Regression: the old regex stopped at the first ';' and required 6+ chars,
        // so real Luogu cookies (e.g. "uid=5; __client_id=abc") leaked into logs.
        val masked = sanitizeSensitive("cookie: uid=5; __client_id=abcdef123456; PHPSESSID=xyz")
        assertFalse(masked.contains("uid=5"))
        assertFalse(masked.contains("__client_id=abcdef123456"))
        assertFalse(masked.contains("PHPSESSID=xyz"))
        assertTrue(masked.contains("cookie: <redacted>"))
    }

    @Test
    fun sanitizeSensitive_shouldKeepEmbeddedJsonWellFormed() {
        // The value is redacted up to the closing quote so embedded JSON stays
        // parseable by Logger.parseDetail (was broken by whole-line redaction).
        val masked = sanitizeSensitive("""{"requestHeaders":"authorization: Bearer sk-abcdefghijklmnopqrst","status":200}""")
        assertFalse(masked.contains("sk-abcdefghijklmnopqrst"))
        assertTrue(masked.contains(""""requestHeaders":"authorization: <redacted>""""))
        assertTrue(masked.contains(""""status":200"""))
    }
}

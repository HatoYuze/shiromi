package com.github.hatoyuze.luogu.gui.data

import com.github.hatoyuze.luogu.gui.data.log.LogCategory
import com.github.hatoyuze.luogu.gui.data.log.LogDetail
import com.github.hatoyuze.luogu.gui.data.log.TestLogSink
import kotlin.test.Test
import kotlin.test.assertEquals

class LoggingHookTest {

    @Test
    fun sseStream_shouldOnlyCountEventsAndRecordSummary() {
        val sink = TestLogSink()
        Logger.init(sink)
        val hook = LoggingHook()

        hook.onRequest("POST", "https://api.deepseek.com/chat/completions", mapOf("Authorization" to "Bearer sk-x"), """{"messages":[]}""")
        hook.onSseEvent("""{"delta":"a"}""")
        hook.onSseEvent("""{"delta":"b"}""")
        hook.onSseEvent("[DONE]")
        hook.onResponse("POST", "https://api.deepseek.com/chat/completions", 200, emptyMap(), null)

        assertEquals(1, sink.entries.size)
        val entry = sink.entries[0]
        assertEquals(LogCategory.HTTP, entry.category)
        val detail = (Logger.parseDetail(entry) as LogDetail.Http).value
        assertEquals(2, detail.sseEventCount)
        assertEquals("<SSE stream: 2 events>", detail.responseSummary)
        assertEquals("""{"messages":[]}""", detail.requestBody)
    }

    @Test
    fun nonSseResponse_shouldUseBodyAsSummary() {
        val sink = TestLogSink()
        Logger.init(sink)
        val hook = LoggingHook()

        hook.onRequest("GET", "https://api.deepseek.com/models", emptyMap(), null)
        hook.onResponse("GET", "https://api.deepseek.com/models", 200, emptyMap(), """{"data":[]}""")

        val detail = (Logger.parseDetail(sink.entries[0]) as LogDetail.Http).value
        assertEquals(0, detail.sseEventCount)
        assertEquals("""{"data":[]}""", detail.responseSummary)
    }

    @Test
    fun multipleRequests_shouldPairFifo() {
        val sink = TestLogSink()
        Logger.init(sink)
        val hook = LoggingHook()

        hook.onRequest("GET", "url1", emptyMap(), "body1")
        hook.onRequest("GET", "url2", emptyMap(), "body2")
        hook.onResponse("GET", "url1", 200, emptyMap(), "r1")
        hook.onResponse("GET", "url2", 201, emptyMap(), "r2")

        assertEquals(2, sink.entries.size)
        val first = (Logger.parseDetail(sink.entries[0]) as LogDetail.Http).value
        val second = (Logger.parseDetail(sink.entries[1]) as LogDetail.Http).value
        assertEquals("body1", first.requestBody)
        assertEquals("body2", second.requestBody)
        assertEquals(201, second.status)
    }
}

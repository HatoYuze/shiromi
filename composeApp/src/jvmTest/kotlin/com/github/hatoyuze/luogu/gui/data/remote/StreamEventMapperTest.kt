package com.github.hatoyuze.luogu.gui.data.remote

import com.github.hatoyuze.luogu.gui.domain.chat.ChatService
import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import io.github.hatoyuze.deepseek.protocol.api.ChatChunk
import io.github.hatoyuze.deepseek.toolcall.executor.ToolCall
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StreamEventMapperTest {

    @Test
    fun chat_shouldMapChunksToEvents() = runTest {
        val events = StreamEventMapper().map(
            flowOf(
                ChatChunk.ContentDelta("hi", reasoningContent = "think"),
                ChatChunk.ToolCallRequest(ToolCall("t1", "luogu_search_problem", "{}")),
                ChatChunk.ToolResultData("t1", "luogu_search_problem", "ok", false),
                ChatChunk.Done(1, 2, 3, "stop"),
            ),
            SessionType.CHAT,
        ).toList()

        assertEquals(5, events.size)
        assertEquals(ChatService.StreamEvent.Thinking("think"), events[0])
        assertEquals(ChatService.StreamEvent.Content("hi"), events[1])
        assertEquals(ChatService.StreamEvent.ToolCall("t1", "luogu_search_problem", "{}"), events[2])
        assertEquals(ChatService.StreamEvent.ToolResult("t1", "luogu_search_problem", "ok", false), events[3])
        assertEquals(ChatService.StreamEvent.Done(3, "stop"), events[4])
    }

    @Test
    fun coach_shouldMapCoachJsonAndFlushTail() = runTest {
        val events = StreamEventMapper().map(
            flowOf(
                ChatChunk.ContentDelta("""{"progress":"init","selected":"P1001","content":"看题"}"""),
                ChatChunk.ContentDelta("tail"),
                ChatChunk.Done(0, 0, 0, "stop"),
            ),
            SessionType.COACH,
        ).toList()

        assertIs<ChatService.StreamEvent.CoachInit>(events[0])
        assertEquals("P1001", (events[0] as ChatService.StreamEvent.CoachInit).pid)
        assertEquals(ChatService.StreamEvent.Content("tail"), events[1])
        assertEquals(ChatService.StreamEvent.Done(0, "stop"), events[2])
    }

    @Test
    fun coach_processing_shouldMapToContent() = runTest {
        val events = StreamEventMapper().map(
            flowOf(ChatChunk.ContentDelta("""{"progress":"thinking","content":"hint"}""")),
            SessionType.COACH,
        ).toList()
        assertEquals(ChatService.StreamEvent.Content("hint"), events[0])
    }

    @Test
    fun errorInsideStream_shouldEmitErrorEvent() = runTest {
        val events = StreamEventMapper().map(
            flow { throw RuntimeException("boom") },
            SessionType.CHAT,
        ).toList()
        assertEquals(ChatService.StreamEvent.Error("boom"), events[0])
    }
}

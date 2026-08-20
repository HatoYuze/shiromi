// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.remote

import com.github.hatoyuze.shiromi.gui.domain.chat.ChatService
import com.github.hatoyuze.shiromi.gui.domain.model.SessionType
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
    fun coach_prettyPrintedInit_shouldMapToCoachInit() = runTest {
        val events = StreamEventMapper().map(
            flowOf(ChatChunk.ContentDelta("""
                {
                  "progress": "init",
                  "selected": "P1001",
                  "content": "看题"
                }
            """.trimIndent())),
            SessionType.COACH,
        ).toList()
        val init = assertIs<ChatService.StreamEvent.CoachInit>(events[0])
        assertEquals("P1001", init.pid)
        assertEquals("看题", init.content)
    }

    @Test
    fun coach_finished_shouldCarryRecommendAndDifficultySummary() = runTest {
        val events = StreamEventMapper().map(
            flowOf(ChatChunk.ContentDelta("""
                {
                  "progress": "finished",
                  "recommend": ["SP1043", "P4513"],
                  "difficulty_summary": "本次难点：pref/suf 合并边界。",
                  "summary": "内部记忆记录（不展示）。",
                  "content": "加油！"
                }
            """.trimIndent())),
            SessionType.COACH,
        ).toList()
        val finished = assertIs<ChatService.StreamEvent.CoachFinished>(events[0])
        assertEquals(listOf("SP1043", "P4513"), finished.recommend)
        assertEquals("本次难点：pref/suf 合并边界。", finished.difficultySummary)
        assertEquals("内部记忆记录（不展示）。", finished.summary)
        assertEquals("加油！", finished.content)
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

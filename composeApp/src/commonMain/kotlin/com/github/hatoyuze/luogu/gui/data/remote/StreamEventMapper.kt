package com.github.hatoyuze.luogu.gui.data.remote

import com.github.hatoyuze.luogu.gui.domain.chat.ChatService
import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import com.github.hatoyuze.luogu.skill.coach.CoachResponse
import com.github.hatoyuze.luogu.skill.coach.CoachSegment
import com.github.hatoyuze.luogu.skill.coach.StreamCoachParser
import io.github.hatoyuze.deepseek.protocol.api.ChatChunk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * Maps raw [ChatChunk] streams to [ChatService.StreamEvent].
 *
 * CHAT: content / thinking / tool events pass through directly.
 * COACH: content deltas are fed through [StreamCoachParser]; parsed coach JSON
 * becomes Coach* events and buffered raw text is flushed as [ChatService.StreamEvent.Content]
 * when the stream ends (no trailing text loss).
 *
 * Every exception inside the stream is converted to [ChatService.StreamEvent.Error].
 */
class StreamEventMapper {

    suspend fun map(chunks: Flow<ChatChunk>, type: SessionType): Flow<ChatService.StreamEvent> = flow {
        try {
            when (type) {
                SessionType.CHAT -> mapChat(chunks)
                SessionType.COACH -> mapCoach(chunks)
            }
        } catch (e: Exception) {
            emit(ChatService.StreamEvent.Error(e.message ?: "Unknown error"))
        }
    }

    private suspend fun FlowCollector<ChatService.StreamEvent>.mapChat(chunks: Flow<ChatChunk>) {
        var usage = 0L
        var finishReason: String? = null
        chunks.collect { chunk ->
            when (chunk) {
                is ChatChunk.ContentDelta -> {
                    chunk.reasoningContent?.let { emit(ChatService.StreamEvent.Thinking(it)) }
                    if (chunk.content.isNotEmpty()) {
                        emit(ChatService.StreamEvent.Content(chunk.content))
                    }
                }
                is ChatChunk.ToolCallRequest -> emit(
                    ChatService.StreamEvent.ToolCall(chunk.call.id, chunk.call.name, chunk.call.arguments),
                )
                is ChatChunk.ToolResultData -> emit(
                    ChatService.StreamEvent.ToolResult(chunk.toolCallId, chunk.functionName, chunk.content, chunk.isError),
                )
                is ChatChunk.Done -> {
                    usage = chunk.totalTokens
                    finishReason = chunk.finishReason
                    emit(ChatService.StreamEvent.Done(usage, finishReason))
                }
            }
        }
    }

    private suspend fun FlowCollector<ChatService.StreamEvent>.mapCoach(chunks: Flow<ChatChunk>) {
        val parser = StreamCoachParser()
        var usage = 0L
        var finishReason: String? = null
        chunks.collect { chunk ->
            when (chunk) {
                is ChatChunk.ContentDelta -> {
                    chunk.reasoningContent?.let { emit(ChatService.StreamEvent.Thinking(it)) }
                    if (chunk.content.isNotEmpty()) {
                        parser.append(chunk.content).forEach { segment ->
                            when (segment) {
                                is CoachSegment.Raw -> emit(ChatService.StreamEvent.Content(segment.text))
                                is CoachSegment.Parsed -> emit(coachEvent(segment))
                            }
                        }
                    }
                }
                is ChatChunk.ToolCallRequest -> emit(
                    ChatService.StreamEvent.ToolCall(chunk.call.id, chunk.call.name, chunk.call.arguments),
                )
                is ChatChunk.ToolResultData -> emit(
                    ChatService.StreamEvent.ToolResult(chunk.toolCallId, chunk.functionName, chunk.content, chunk.isError),
                )
                is ChatChunk.Done -> {
                    usage = chunk.totalTokens
                    finishReason = chunk.finishReason
                    parser.flush()?.let { raw ->
                        if (raw is CoachSegment.Raw) {
                            emit(ChatService.StreamEvent.Content(raw.text))
                        }
                    }
                    emit(ChatService.StreamEvent.Done(usage, finishReason))
                }
            }
        }
    }

    private fun coachEvent(segment: CoachSegment.Parsed): ChatService.StreamEvent = when (val response = segment.response) {
        is CoachResponse.Init -> ChatService.StreamEvent.CoachInit(response.response.selected, response.response.content)
        is CoachResponse.Processing -> ChatService.StreamEvent.Content(response.response.content)
        is CoachResponse.Finished -> ChatService.StreamEvent.CoachFinished(
            summary = response.response.summary,
            recommend = response.response.recommend,
            content = response.response.content,
        )
        is CoachResponse.Checkpoint -> ChatService.StreamEvent.CoachCheckpoint(segment.rawJson)
    }
}

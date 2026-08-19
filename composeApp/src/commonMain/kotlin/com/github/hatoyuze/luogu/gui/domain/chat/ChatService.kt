// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.domain.chat

import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import com.github.hatoyuze.luogu.skill.api.ProblemDetailData
import kotlinx.coroutines.flow.SharedFlow

/**
 * Contract for chat/coach streaming service.
 *
 * CHAT and COACH are mutually exclusive per session:
 * - CHAT: luogu tools only, chat prompt
 * - COACH: full tool set + coach prompt + MemoryProvider
 *
 * Each [ChatSession] owns a dedicated Deepseek instance with isolated message
 * history, so sessions never leak context into each other.
 *
 * Ask-user interaction is event-driven: the tool executor emits a
 * [UserQuestion] on [userQuestionEvents] and suspends until
 * [submitUserAnswer] completes it. This removes any UI-to-implementation cast.
 */
interface ChatService {
    sealed interface StreamEvent {
        data class Content(val text: String) : StreamEvent
        data class Thinking(val text: String) : StreamEvent
        data class ToolCall(
            val toolCallId: String,
            val functionName: String,
            val arguments: String,
        ) : StreamEvent
        data class ToolResult(
            val toolCallId: String,
            val functionName: String,
            val content: String,
            val isError: Boolean,
        ) : StreamEvent
        data class Done(val totalTokens: Long, val finishReason: String? = null) : StreamEvent
        data class Error(val message: String) : StreamEvent

        // ── Coach-specific events ──
        data class CoachInit(val pid: String, val content: String = "") : StreamEvent
        data class CoachFinished(
            val summary: String,
            val recommend: List<String>?,
            val content: String,
            /** Student-facing difficulty summary written by the agent (displayed; `summary` is not). */
            val difficultySummary: String = "",
        ) : StreamEvent
        data class CoachCheckpoint(val checkpointJson: String) : StreamEvent
    }

    /** Emits when the underlying tool executor is waiting for user input. */
    val userQuestionEvents: SharedFlow<UserQuestion>

    /** Create or retrieve a per-session [ChatSession] with isolated context. */
    suspend fun createSession(sessionId: String, type: SessionType): ChatSession

    /** Force-create a new [ChatSession], replacing any cached instance. */
    suspend fun resetSession(sessionId: String, type: SessionType): ChatSession

    /** Complete a pending [UserQuestion] with the user's answer (null = timeout/dismiss). */
    suspend fun submitUserAnswer(questionId: String, answer: String?)

    suspend fun availableModels(): List<String>

    /** Fetch problem detail from Luogu API. Returns null on failure. */
    suspend fun getProblemDetail(pid: String): ProblemDetailData?

    /** Force-refresh problem detail: skip cache, fetch from network, update cache. */
    suspend fun refreshProblemDetail(pid: String): ProblemDetailData?
}

/**
 * An ask-user question awaiting an answer, emitted by the streaming service.
 */
data class UserQuestion(
    val questionId: String,
    val desc: String,
    val timeoutMs: Int,
    val isMulti: Boolean,
    val allowCustom: Boolean,
    val options: List<String>,
    val startedAtMs: Long,
)

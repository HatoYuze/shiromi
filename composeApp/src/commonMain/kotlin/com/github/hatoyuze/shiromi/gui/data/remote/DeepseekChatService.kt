// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.data.remote

import com.github.hatoyuze.shiromi.gui.config.ConfigService
import com.github.hatoyuze.shiromi.gui.config.deepseekWithConfig
import com.github.hatoyuze.shiromi.gui.domain.chat.ChatService
import com.github.hatoyuze.shiromi.gui.domain.chat.ChatSession
import com.github.hatoyuze.shiromi.gui.domain.chat.UserQuestion
import com.github.hatoyuze.shiromi.gui.domain.model.SessionType
import com.github.hatoyuze.shiromi.gui.platform.currentTimeMillis
import com.github.hatoyuze.shiromi.gui.presentation.components.askuser.installAskUserTool
import com.github.hatoyuze.shiromi.protocol.api.LuoguApi
import com.github.hatoyuze.shiromi.protocol.api.ProblemDetailData
import com.github.hatoyuze.shiromi.protocol.api.installLuoguTools
import com.github.hatoyuze.shiromi.protocol.coach.MemoryProvider
import com.github.hatoyuze.shiromi.protocol.coach.installAllTools
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * [ChatService] implementation backed by [LuoguApiProvider] and per-session
 * [DeepseekChatSession]s.
 *
 * Ask-user interaction is event-driven: [requestUserAnswer] emits a
 * [UserQuestion] on [userQuestionEvents] and suspends until [submitUserAnswer]
 * completes the matching deferred (or the stream is cancelled / times out,
 * in which case the deferred is cleaned up by `finally`).
 */
class DeepseekChatService(
    private val settings: ConfigService,
    private val apiProvider: LuoguApiProvider,
    private val memoryProvider: MemoryProvider,
    private val maxCachedSessions: Int = DEFAULT_MAX_CACHED_SESSIONS,
) : ChatService {

    private val sessionMutex = Mutex()
    /** 手写 LRU（common 无 access-order LinkedHashMap）：命中即重插入刷新顺序，超限驱逐最旧项。 */
    private val sessions = LinkedHashMap<String, DeepseekChatSession>()
    /** Guards [pendingAnswers] + [questionCounter]: question-id allocation and answer routing. */
    private val answerMutex = Mutex()
    private val pendingAnswers = mutableMapOf<String, CompletableDeferred<String?>>()
    private var questionCounter = 0L

    private val _userQuestionEvents = MutableSharedFlow<UserQuestion>(extraBufferCapacity = 1)
    override val userQuestionEvents: SharedFlow<UserQuestion> = _userQuestionEvents.asSharedFlow()

    override suspend fun createSession(sessionId: String, type: SessionType): ChatSession {
        // Fast path: cache hit — keep the lock window tiny.
        sessionMutex.withLock {
            sessions.remove(sessionId)?.let { cached ->
                sessions[sessionId] = cached // touch: refresh LRU order
                return cached
            }
        }
        // Slow path: build outside the lock so concurrent sessions aren't blocked
        // by another session's construction (API provider fetch + tool pipeline).
        val built = buildSession(sessionId, type)
        return sessionMutex.withLock {
            sessions[sessionId] = built
            while (sessions.size > maxCachedSessions) {
                sessions.remove(sessions.keys.first())
            }
            built
        }
    }

    override suspend fun resetSession(sessionId: String, type: SessionType): ChatSession {
        val built = buildSession(sessionId, type)
        return sessionMutex.withLock {
            sessions[sessionId] = built
            built
        }
    }

    private suspend fun buildSession(sessionId: String, type: SessionType): DeepseekChatSession {
        val api = apiProvider.get()
        return DeepseekChatSession(
            sessionId = sessionId,
            type = type,
            ds = buildDeepseek(type, api),
            mapper = StreamEventMapper(),
        )
    }

    override suspend fun submitUserAnswer(questionId: String, answer: String?) {
        answerMutex.withLock {
            pendingAnswers.remove(questionId)?.complete(answer)
        }
    }

    override suspend fun availableModels(): List<String> = try {
        io.github.hatoyuze.deepseek.protocol.api.statelessDeepseek(settings.apiKey) {}
            .availableModels()
            .map { it.id }
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun getProblemDetail(pid: String): ProblemDetailData? = try {
        val response = apiProvider.get().getProblemDetail(pid)
        if (response.isSuccess) response.data else null
    } catch (_: Exception) {
        null
    }

    override suspend fun refreshProblemDetail(pid: String): ProblemDetailData? = try {
        val response = apiProvider.get().refreshProblemDetail(pid)
        if (response.isSuccess) response.data else null
    } catch (_: Exception) {
        null
    }

    private fun buildDeepseek(type: SessionType, api: LuoguApi) =
        deepseekWithConfig(type, settings, api) { luoguApi ->
            when (type) {
                SessionType.CHAT -> installLuoguTools(luoguApi)
                SessionType.COACH -> installAllTools(luoguApi, memoryProvider)
            }
            installAskUserTool(::requestUserAnswer)
        }

    private suspend fun requestUserAnswer(
        desc: String,
        timeoutMs: Int,
        isMulti: Boolean,
        options: List<String>,
        allowCustom: Boolean,
    ): String? {
        // Allocate the id + deferred atomically so concurrent sessions never
        // produce duplicate ids (the counter used to be JVM-only AtomicLong).
        val (questionId, deferred) = answerMutex.withLock {
            questionCounter++
            val id = "ask_${questionCounter}_${currentTimeMillis()}"
            val d = CompletableDeferred<String?>()
            pendingAnswers[id] = d
            id to d
        }
        _userQuestionEvents.emit(
            UserQuestion(
                questionId = questionId,
                desc = desc,
                timeoutMs = timeoutMs,
                isMulti = isMulti,
                allowCustom = allowCustom,
                options = options,
                startedAtMs = currentTimeMillis(),
            ),
        )
        return try {
            deferred.await()
        } finally {
            answerMutex.withLock { pendingAnswers.remove(questionId) }
        }
    }

    companion object {
        const val DEFAULT_MAX_CACHED_SESSIONS = 20
    }
}

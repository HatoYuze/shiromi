package com.github.hatoyuze.luogu.gui.domain.chat

import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import io.github.hatoyuze.deepseek.protocol.api.entity.Message
import kotlinx.coroutines.flow.Flow

/**
 * A stateful, per-session conversation handle.
 *
 * Each [ChatSession] owns a dedicated Deepseek instance with isolated message
 * history, ensuring sessions do not leak context into each other.
 *
 * Created by [ChatService.createSession].
 */
interface ChatSession {
    val sessionId: String
    val type: SessionType

    /** Send a user message and receive a [Flow] of [ChatService.StreamEvent]s. */
    suspend fun chat(userMessage: String): Flow<ChatService.StreamEvent>

    /**
     * Continue streaming from the current history (no user message added).
     * Used for regenerate: Deepseek history already ends with the user prompt.
     */
    suspend fun continueChat(): Flow<ChatService.StreamEvent>

    /** Cancel in-flight generation. */
    fun cancelGeneration()

    /** Truncate message history at the given index (inclusive). */
    suspend fun truncateAt(index: Int)

    /** Get number of messages currently in the internal history. */
    fun getMessageCount(): Int

    /** Find index of a user message by content. Returns -1 if not found. */
    fun findUserMessageIndex(content: String): Int

    /**
     * Replay a message into the internal history. Returns the index where the
     * message was added. Used during branch switching to rebuild context.
     */
    suspend fun addMessageToHistory(message: Message): Int
}

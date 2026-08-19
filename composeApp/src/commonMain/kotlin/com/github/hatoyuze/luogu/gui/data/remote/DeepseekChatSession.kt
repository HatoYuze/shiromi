// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.remote

import com.github.hatoyuze.luogu.gui.domain.chat.ChatService
import com.github.hatoyuze.luogu.gui.domain.chat.ChatSession
import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import io.github.hatoyuze.deepseek.protocol.api.Deepseek
import io.github.hatoyuze.deepseek.protocol.api.entity.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import com.github.hatoyuze.luogu.gui.platform.ioDispatcher

/**
 * Per-session adapter over a dedicated [Deepseek] instance plus [StreamEventMapper].
 */
class DeepseekChatSession(
    override val sessionId: String,
    override val type: SessionType,
    private val ds: Deepseek,
    private val mapper: StreamEventMapper,
) : ChatSession {

    override suspend fun chat(userMessage: String): Flow<ChatService.StreamEvent> =
        mapper.map(ds.chatStream(userMessage), type).flowOn(ioDispatcher)

    override suspend fun continueChat(): Flow<ChatService.StreamEvent> =
        mapper.map(ds.continueStream(), type).flowOn(ioDispatcher)

    override fun cancelGeneration() = ds.cancelStream()

    override suspend fun truncateAt(index: Int) = ds.truncateAt(index)

    override fun getMessageCount(): Int = ds.getMessageCount()

    override fun findUserMessageIndex(content: String): Int = ds.findUserMessageIndex(content)

    override suspend fun addMessageToHistory(message: Message): Int {
        ds.addMessage(message)
        return ds.getMessageCount() - 1
    }
}

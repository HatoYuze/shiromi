// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.presentation.state

import com.github.hatoyuze.luogu.gui.domain.model.ChatMessageDomainModel
import com.github.hatoyuze.luogu.gui.domain.model.ChatSessionDomainModel
import com.github.hatoyuze.luogu.gui.domain.model.SessionType

sealed interface ChatEvent {
    // User actions
    data class SendMessage(val content: String) : ChatEvent
    data class SelectSession(val session: ChatSessionDomainModel) : ChatEvent
    data class SelectModel(val model: String) : ChatEvent
    data class RetryMessage(val message: ChatMessageDomainModel) : ChatEvent
    data class CreateNewSession(val type: SessionType) : ChatEvent
    data object ClearError : ChatEvent
    data object DismissModelList : ChatEvent
    data object ShowModelList : ChatEvent
    data object GoHome : ChatEvent
    data class AnswerAskUser(val selectedOptions: List<String>, val customText: String) : ChatEvent

    // ── Message actions (new) ──
    data object StopGeneration : ChatEvent
    data class StartEdit(val messageId: String) : ChatEvent
    data object CancelEdit : ChatEvent
    data class SendEdit(val messageId: String, val newContent: String) : ChatEvent
    data class RequestDelete(val userMessageId: String, val assistantMessageId: String) : ChatEvent
    data class DeleteExchange(val userMessageId: String, val assistantMessageId: String) : ChatEvent
    data object DismissDelete : ChatEvent
    data class RegenerateMessage(val messageId: String) : ChatEvent
    data class SwitchBranch(val branchId: String) : ChatEvent
    data class ShowToast(val message: String) : ChatEvent
    data object ClearToast : ChatEvent

    // Internal
    data class LoadedSessions(val sessions: List<ChatSessionDomainModel>) : ChatEvent
    data class LoadedMessages(val messages: List<ChatMessageDomainModel>) : ChatEvent
    data class ModelsLoaded(val models: List<String>) : ChatEvent
    data class StreamUpdate(
        val content: String,
        val thinkingContent: String? = null,
    ) : ChatEvent
    data class StreamDone(val totalTokens: Long) : ChatEvent
    data class StreamError(val message: String) : ChatEvent
}

package com.github.hatoyuze.luogu.gui.presentation.state

import com.github.hatoyuze.luogu.gui.domain.model.ChatBranchDomainModel
import com.github.hatoyuze.luogu.gui.domain.model.ChatMessageDomainModel
import com.github.hatoyuze.luogu.gui.domain.model.ChatSessionDomainModel
import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import com.github.hatoyuze.luogu.gui.domain.model.AskUserAnswer

data class ChatUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentSession: ChatSessionDomainModel? = null,
    val chatSessions: List<ChatSessionDomainModel> = emptyList(),
    val messages: List<ChatMessageDomainModel> = emptyList(),
    val selectedModel: String = "deepseek-v4-flash",
    val availableModels: List<String> = emptyList(),
    val showModelList: Boolean = false,
    val showHomeScreen: Boolean = true,
    val sessionType: SessionType = SessionType.CHAT,
    val todos: List<com.github.hatoyuze.luogu.gui.domain.model.TodoItemDomainModel> = emptyList(),
    /** Non-null when an askuser tool call is awaiting user response. */
    val pendingAskUser: PendingAskUser? = null,
    // ── Branch / fork state ──
    /** Non-null when a message is in inline-edit mode. */
    val editingMessageId: String? = null,
    /** Currently active branch ID. */
    val activeBranchId: String = "main",
    /** All branches for the current session. */
    val branches: List<ChatBranchDomainModel> = emptyList(),
    /** Toast message for non-critical notifications (e.g. "只能重新生成最新的消息"). */
    val toast: String? = null,
    /** Non-null when a delete confirmation dialog is shown. */
    val pendingDelete: PendingDelete? = null,
)

data class PendingDelete(
    val userMessageId: String,
    val assistantMessageId: String,
)

/**
 * Snapshot of an in-flight askuser question shown above the chat input.
 */
data class PendingAskUser(
    val questionId: String? = null,
    val toolCallId: String? = null,
    val desc: String,
    val timeoutMs: Int,
    val isMulti: Boolean,
    val allowCustom: Boolean,
    val options: List<String>,
    val startedAtMs: Long,
    val answer: AskUserAnswer? = null,
)

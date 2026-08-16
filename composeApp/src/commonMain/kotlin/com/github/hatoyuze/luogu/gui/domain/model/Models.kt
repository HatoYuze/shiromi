package com.github.hatoyuze.luogu.gui.domain.model

import com.github.hatoyuze.luogu.skill.api.ProblemDetailData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ═══════════════════════════════════════════════════════════
// Message segments — ordered timeline of events within a message
// ═══════════════════════════════════════════════════════════

@Serializable
enum class TextType {
    @SerialName("t") THINKING,
    @SerialName("c") CONTENT
}

@Serializable
sealed interface MessageSegment {
    @Serializable
    @SerialName("t")  // text segment — discriminator value
    data class Text(
        @SerialName("k") val type: TextType,  // kind: THINKING or CONTENT
        @SerialName("v") val text: String     // value
    ) : MessageSegment

    @Serializable
    @SerialName("c")  // call / tool call — discriminator value
    data class ToolCall(
        @SerialName("i") val info: ToolCallInfo  // info
    ) : MessageSegment

    @Serializable
    @SerialName("p")  // problem card — discriminator value
    data class ProblemCard(
        @SerialName("d") val pid: String,
        @SerialName("l") val loading: Boolean = true,
        @SerialName("v") val data: ProblemDetailData? = null,
        @SerialName("e") val error: String? = null,
        @SerialName("cc") val coachContent: String? = null,  // coach intro text from CoachInit
    ) : MessageSegment

    @Serializable
    @SerialName("u")  // askUser — discriminator value
    data class AskUser(
        @SerialName("d") val desc: String,
        @SerialName("tm") val timeoutMs: Int,
        @SerialName("im") val isMulti: Boolean,
        @SerialName("ac") val allowCustom: Boolean,
        @SerialName("o") val options: List<String>,
        @SerialName("sm") val startedAtMs: Long,
        @SerialName("r") val answer: AskUserAnswer? = null,
    ) : MessageSegment
}

// ═══════════════════════════════════════════════════════════

enum class MessageStatus {
    SENT, SENDING, ERROR, ABORTED
}

enum class SessionType {
    CHAT, COACH;

    companion object {
        fun fromString(value: String): SessionType = when (value.lowercase()) {
            "coach" -> COACH
            else -> CHAT
        }
    }
}

data class ChatSessionDomainModel(
    val id: String,
    val title: String,
    val type: SessionType,
    val createdAt: Long,
    val lastModified: Long,
    val lastActiveBranchId: String = "main",
)

data class ChatMessageDomainModel(
    val id: String,
    val sessionId: String,
    val content: String,
    val isUser: Boolean,
    val status: MessageStatus,
    val timestamp: Long,
    val thinkingContent: String? = null,
    val toolCalls: List<ToolCallInfo>? = null,
    val totalTokens: Long? = null,
    /** Ordered timeline of segments (null = use legacy content/thinkingContent/toolCalls fields). */
    val segments: List<MessageSegment>? = null,
    /** Thinking duration in seconds (set on Done). */
    val thinkingElapsedSec: Int? = null,
    /** Finish reason from API: "stop", "length", "content_filter", "tool_calls", "insufficient_system_resource". */
    val finishReason: String? = null,
    // ── Fork / branch fields ──
    /** Branch this message belongs to. */
    val branchId: String = "main",
    /** Previous message in this branch's linear chain. */
    val parentMessageId: String? = null,
    /** Cached position in Deepseek._messages (rebuilt during branch replay). */
    val deepseekMessageIndex: Int? = null,
)

/**
 * Represents a single tool call execution by the model.
 */
@Serializable
data class ToolCallInfo(
    @SerialName("i") val id: String,
    @SerialName("n") val name: String,
    @SerialName("a") val arguments: String,
    @SerialName("r") val result: String? = null,
    @SerialName("e") val isError: Boolean = false,
)

/**
 * Branch metadata — each branch is an independent conversation line.
 * Branches replace editVersions: editing a message creates a new branch.
 */
data class ChatBranchDomainModel(
    val id: String,
    val sessionId: String,
    val parentBranchId: String? = null,
    val forkMessageId: String? = null,
    val editedMessageId: String? = null,
    val createdAt: Long,
)

/**
 * User's answer to an askuser tool call.
 */
@Serializable
data class AskUserAnswer(
    @SerialName("s") val selected: List<String>,
    @SerialName("ct") val customText: String? = null,
    @SerialName("em") val elapsedMs: Long = 0,
)

data class TodoItemDomainModel(
    val id: String,
    val title: String,
    val completed: Boolean,
    val createdAt: Long,
    val dueAt: Long? = null,
)

// ═══════════════════════════════════════════════════════════
// Calendar & Learning Progress
// ═══════════════════════════════════════════════════════════

/** User-marked important calendar event (e.g. "NOIP 2026", "期末考试"). */
data class CalendarEvent(
    val id: String,
    val name: String,
    val date: kotlinx.datetime.LocalDate,
    val createdAtMs: Long,
    val color: Int = 0,  // ARGB int; 0 = default (no custom color)
    val pinned: Boolean = false,
    val allDay: Boolean = false,  // 全天事件（不显示具体时间）
    val timeMinutes: Int? = null, // 0..1439，分钟数；null = 未指定
)

/** Current study topic with goal tracking. */
data class StudyTopic(
    val name: String = "",
    val currentCount: Int = 0,
    val goalCount: Int = 10,
)

// ═══════════════════════════════════════════════════════════
// Daily Problem
// ═══════════════════════════════════════════════════════════

/** AI-generated daily problem recommendation. */
data class DailyProblemResult(
    val pid: String,
    val reason: String,
    val tips: List<String>,
)

/** Single message in the daily problem agent's conversation context. */
data class DailyProblemMessage(
    val role: String,
    val content: String,
    val createdAtMs: Long,
)

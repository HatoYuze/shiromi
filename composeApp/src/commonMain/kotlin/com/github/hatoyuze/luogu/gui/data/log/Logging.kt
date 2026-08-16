package com.github.hatoyuze.luogu.gui.data.log

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Ordered severity levels: DEBUG < INFO < WARN < ERROR. */
@Serializable
enum class LogLevel {
    @SerialName("debug") DEBUG,
    @SerialName("info") INFO,
    @SerialName("warn") WARN,
    @SerialName("error") ERROR,
}

/** Log entry category, used for filtering in the log viewer. */
@Serializable
enum class LogCategory {
    @SerialName("app") APP,
    @SerialName("chat") CHAT,
    @SerialName("tool") TOOL,
    @SerialName("http") HTTP,
    @SerialName("config") CONFIG,
    @SerialName("daily") DAILY,
    @SerialName("assistant") ASSISTANT,
}

/**
 * One log entry. Serialized as a single JSON line by [LogSink] implementations.
 * [message] is the human-readable summary; [detailJson] carries structured
 * payloads such as [HttpLogDetail] or [AssistantMessageDetail].
 */
@Serializable
data class LogEntryData(
    val timestamp: Long,
    val level: LogLevel,
    val category: LogCategory,
    val event: String,
    val message: String,
    val detailJson: String? = null,
    val sessionId: String? = null,
    val durationMs: Long? = null,
)

/** Structured payload for HTTP request/response logging (plain text in file, sanitized). */
@Serializable
data class HttpLogDetail(
    val method: String,
    val url: String,
    val status: Int? = null,
    val requestHeaders: String? = null,
    val requestBody: String? = null,
    val responseHeaders: String? = null,
    val responseSummary: String? = null,
    val sseEventCount: Int = 0,
)

/** Structured payload for assistant message debugging (raw data for render diff). */
@Serializable
data class AssistantMessageDetail(
    val messageId: String,
    val content: String,
    val thinking: String? = null,
    val segmentsJson: String? = null,
    val toolCallsJson: String? = null,
    val finishReason: String? = null,
    val totalTokens: Long? = null,
)

/** Parsed [detailJson] view model for the log viewer. */
sealed interface LogDetail {
    data class Http(val value: HttpLogDetail) : LogDetail
    data class Assistant(val value: AssistantMessageDetail) : LogDetail
}

/**
 * Storage abstraction for the logging system.
 *
 * Desktop is backed by [FileLogSink] (JSON Lines + rotation); a future Android
 * target can implement this over `filesDir` without touching consumers.
 */
interface LogSink {
    fun write(entry: LogEntryData)
    fun readRecent(limit: Int, category: LogCategory? = null, level: LogLevel? = null): List<LogEntryData>
    fun clear()
    val location: String
}

private val skKeyPattern = Regex("sk-[A-Za-z0-9]{10,}")
/**
 * Redacts credential headers. The value is matched up to a closing quote or the
 * end of the line — cookie values are often `;`-delimited multi-segment strings,
 * and stopping at the first `;` (or requiring 6+ chars) let real cookies leak
 * into log files. An optional surrounding quote is preserved so embedded JSON
 * stays well-formed.
 */
private val sensitiveHeaderPattern =
    Regex("""(?i)(cookie|authorization|api[_-]?key)(\s*[:=]\s*)("?)([^"\r\n]*)("?)""")

/** Masks API keys and credential headers before persisting log text. */
fun sanitizeSensitive(text: String): String {
    val maskedSk = skKeyPattern.replace(text) { "<sk-${it.value.takeLast(4)}>" }
    return sensitiveHeaderPattern.replace(maskedSk) {
        "${it.groupValues[1]}${it.groupValues[2]}${it.groupValues[3]}<redacted>${it.groupValues[5]}"
    }
}

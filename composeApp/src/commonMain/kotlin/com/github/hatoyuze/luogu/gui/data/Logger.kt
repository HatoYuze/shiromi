package com.github.hatoyuze.luogu.gui.data

import com.github.hatoyuze.luogu.gui.data.log.AssistantMessageDetail
import com.github.hatoyuze.luogu.gui.data.log.HttpLogDetail
import com.github.hatoyuze.luogu.gui.data.log.LogCategory
import com.github.hatoyuze.luogu.gui.data.log.LogDetail
import com.github.hatoyuze.luogu.gui.data.log.LogEntryData
import com.github.hatoyuze.luogu.gui.data.log.LogLevel
import com.github.hatoyuze.luogu.gui.data.log.LogSink
import com.github.hatoyuze.luogu.gui.data.log.sanitizeSensitive
import com.github.hatoyuze.luogu.gui.platform.currentTimeMillis
import kotlinx.serialization.json.Json

/**
 * Application logging facade.
 *
 * Writes structured, size-bounded log entries through a [LogSink] (JSON Lines
 * files on desktop). Sensitive values (API keys, credential headers) are
 * masked before persisting. [init] must be called once during startup.
 */
object Logger {
    private var sink: LogSink? = null
    private var minLevel: LogLevel = LogLevel.INFO
    var captureAssistantMessages: Boolean = true
    private var maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES
    private val json = Json { encodeDefaults = false }

    fun init(
        sink: LogSink,
        minLevel: LogLevel = LogLevel.INFO,
        captureAssistantMessages: Boolean = true,
        maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES,
    ) {
        this.sink = sink
        this.minLevel = minLevel
        this.captureAssistantMessages = captureAssistantMessages
        this.maxBodyBytes = maxBodyBytes
    }

    fun log(
        category: LogCategory,
        event: String,
        message: String,
        level: LogLevel = LogLevel.INFO,
        detailJson: String? = null,
        sessionId: String? = null,
        durationMs: Long? = null,
    ) {
        if (level.ordinal < minLevel.ordinal) return
        sink?.write(
            LogEntryData(
                timestamp = currentTimeMillis(),
                level = level,
                category = category,
                event = event,
                message = sanitizeSensitive(message),
                detailJson = detailJson?.let { sanitizeSensitive(it) },
                sessionId = sessionId,
                durationMs = durationMs,
            ),
        )
    }

    fun info(
        category: LogCategory,
        event: String,
        message: String,
        detailJson: String? = null,
        sessionId: String? = null,
        durationMs: Long? = null,
    ) = log(category, event, message, LogLevel.INFO, detailJson, sessionId, durationMs)

    fun warn(
        category: LogCategory,
        event: String,
        message: String,
        detailJson: String? = null,
        sessionId: String? = null,
        durationMs: Long? = null,
    ) = log(category, event, message, LogLevel.WARN, detailJson, sessionId, durationMs)

    fun error(
        category: LogCategory,
        event: String,
        message: String,
        detailJson: String? = null,
        sessionId: String? = null,
        durationMs: Long? = null,
    ) = log(category, event, message, LogLevel.ERROR, detailJson, sessionId, durationMs)

    fun debug(
        category: LogCategory,
        event: String,
        message: String,
        detailJson: String? = null,
        sessionId: String? = null,
        durationMs: Long? = null,
    ) = log(category, event, message, LogLevel.DEBUG, detailJson, sessionId, durationMs)

    /** Structured HTTP logging; bodies are truncated and sanitized here. */
    fun http(
        method: String,
        url: String,
        status: Int? = null,
        requestHeaders: String? = null,
        requestBody: String? = null,
        responseHeaders: String? = null,
        responseSummary: String? = null,
        sseEventCount: Int = 0,
        durationMs: Long? = null,
    ) {
        val detail = HttpLogDetail(
            method = method,
            url = url,
            status = status,
            requestHeaders = truncateAndSanitize(requestHeaders),
            requestBody = truncateAndSanitize(requestBody),
            responseHeaders = truncateAndSanitize(responseHeaders),
            responseSummary = truncateAndSanitize(responseSummary),
            sseEventCount = sseEventCount,
        )
        val summary = "$method $url" + (status?.let { " → $it" } ?: "")
        log(
            category = LogCategory.HTTP,
            event = "http.request",
            message = summary,
            detailJson = json.encodeToString(HttpLogDetail.serializer(), detail),
            durationMs = durationMs,
        )
    }

    /**
     * Records the raw assistant message for debugging render differences.
     * Enabled by default; disable via `-Dluogu-gui.logs.captureAssistantMessages=false`.
     */
    fun assistantMessage(
        sessionId: String,
        messageId: String,
        content: String,
        thinking: String? = null,
        segmentsJson: String? = null,
        toolCallsJson: String? = null,
        finishReason: String? = null,
        totalTokens: Long? = null,
        durationMs: Long? = null,
    ) {
        if (!captureAssistantMessages) return
        val detail = AssistantMessageDetail(
            messageId = messageId,
            content = content,
            thinking = thinking,
            segmentsJson = segmentsJson,
            toolCallsJson = toolCallsJson,
            finishReason = finishReason,
            totalTokens = totalTokens,
        )
        log(
            category = LogCategory.ASSISTANT,
            event = "assistant.message",
            message = "assistant ${messageId.take(8)}… ${content.length} chars" +
                (finishReason?.let { ", reason=$it" } ?: ""),
            detailJson = json.encodeToString(AssistantMessageDetail.serializer(), detail),
            sessionId = sessionId,
            durationMs = durationMs,
        )
    }

    fun recent(limit: Int, category: LogCategory? = null, level: LogLevel? = null): List<LogEntryData> =
        sink?.readRecent(limit, category, level) ?: emptyList()

    fun parseDetail(entry: LogEntryData): LogDetail? = when (entry.category) {
        LogCategory.HTTP -> runCatching {
            LogDetail.Http(json.decodeFromString(HttpLogDetail.serializer(), entry.detailJson ?: return null))
        }.getOrNull()
        LogCategory.ASSISTANT -> runCatching {
            LogDetail.Assistant(json.decodeFromString(AssistantMessageDetail.serializer(), entry.detailJson ?: return null))
        }.getOrNull()
        else -> null
    }

    fun clearAll() {
        sink?.clear()
    }

    val logLocation: String get() = sink?.location ?: ""

    private fun truncateAndSanitize(text: String?): String? {
        if (text == null) return null
        val truncated = if (text.length > maxBodyBytes) text.take(maxBodyBytes) + "\n...[truncated]" else text
        return sanitizeSensitive(truncated)
    }

    const val DEFAULT_MAX_BODY_BYTES = 8192
}

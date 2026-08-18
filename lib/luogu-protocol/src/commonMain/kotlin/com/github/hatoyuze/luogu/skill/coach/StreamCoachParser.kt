package com.github.hatoyuze.luogu.skill.coach

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Result of streaming coach-content parsing.
 *
 * - [Raw]: text that is not part of a parseable coach JSON object.
 * - [Parsed]: a complete, successfully parsed [CoachResponse].
 */
sealed interface CoachSegment {
    data class Raw(val text: String) : CoachSegment
    data class Parsed(val response: CoachResponse, val rawJson: String) : CoachSegment
}

/**
 * Stateful, pure parser that accumulates streamed content deltas and extracts
 * complete coach JSON objects (objects whose top level has a `progress` field).
 * Thread-confined to a single flow collection; create one instance per stream.
 *
 * Extraction is deliberately tolerant of how the model actually formats the
 * response, since models frequently deviate from "compact, raw JSON":
 *
 * - leading whitespace / newlines before `{`
 * - pretty-printed (multi-line) JSON — the object key order does not matter
 * - Markdown code fences (```json ... ```) around the object
 * - prose before/after the object (prose is emitted as [CoachSegment.Raw],
 *   never silently dropped)
 *
 * The brace scanner is string-aware: braces inside JSON string values
 * (e.g. LaTeX or Chinese `{ }`) do not confuse depth tracking.
 */
class StreamCoachParser {

    private val buffer = StringBuilder()

    /**
     * Feed one content delta and return every segment produced by it.
     *
     * A [CoachSegment.Parsed] is emitted for each complete JSON object found in
     * the accumulated buffer; text that cannot yet form a complete JSON object
     * stays buffered. Call [flush] when the stream ends.
     */
    fun append(delta: String): List<CoachSegment> {
        if (delta.isEmpty()) return emptyList()
        buffer.append(delta)
        val segments = mutableListOf<CoachSegment>()
        while (true) {
            val extracted = extractNext() ?: break
            segments.add(extracted)
        }
        return segments
    }

    /** Emit any buffered text that never formed a JSON object. Returns null when empty. */
    fun flush(): CoachSegment? {
        if (buffer.isEmpty()) return null
        val text = buffer.toString()
        buffer.setLength(0)
        return CoachSegment.Raw(text)
    }

    /**
     * Pull one segment off the front of the buffer.
     *
     * - Real prose preceding the first `{` is consumed and returned as [CoachSegment.Raw].
     * - A Markdown fence prefix is consumed silently.
     * - A complete, parseable coach JSON object is consumed (including a trailing fence)
     *   and returned as [CoachSegment.Parsed].
     * - A structurally complete object that does not parse to a known [CoachResponse]
     *   (unknown `progress` value, or a `{...}` fragment from prose) is consumed and
     *   returned as [CoachSegment.Raw] so it can never block later valid objects.
     * - Incomplete input (no closing brace yet) keeps the buffer untouched (`null`).
     */
    private fun extractNext(): CoachSegment? {
        while (true) {
            val text = buffer.toString()
            val braceIdx = text.indexOf('{')
            if (braceIdx < 0) return null

            // ── Leading content before the first '{' ──
            val pre = text.substring(0, braceIdx)
            val preTrimmed = pre.trim()
            if (preTrimmed.isNotEmpty()) {
                if (isFenceMarker(preTrimmed)) {
                    // Markdown fence wrapper — consume it, keep scanning for JSON.
                    consumeTo(braceIdx)
                    continue
                }
                // Real prose — surface it, do not silently drop.
                consumeTo(braceIdx)
                return CoachSegment.Raw(pre)
            }

            // ── String-aware scan for the matching closing brace ──
            val endIdx = indexOfMatchingBrace(text, braceIdx)
            if (endIdx == null) return null // still incomplete — keep buffering

            val jsonStr = text.substring(braceIdx, endIdx + 1)
            val response = parseCoachResponse(jsonStr)
            if (response == null) {
                // Complete but not a known coach response (unknown progress, or a `{...}`
                // fragment in prose). Surface it as raw text and move on, otherwise it
                // would block every valid object that follows it.
                consumeTo(endIdx + 1)
                return CoachSegment.Raw(jsonStr)
            }

            // ── Consume the object, plus any trailing fence wrapper ──
            var consumeEnd = endIdx + 1
            val tail = text.substring(consumeEnd)
            val tailTrimmed = tail.trimStart()
            if (tailTrimmed.startsWith("```") || tailTrimmed.startsWith("~~~")) {
                val fenceStartInTail = tail.indexOf(tailTrimmed.first())
                val newlineAfterFence = tail.indexOf('\n', fenceStartInTail)
                consumeEnd += if (newlineAfterFence >= 0) newlineAfterFence + 1 else tail.length
            }
            consumeTo(consumeEnd)
            return CoachSegment.Parsed(response, jsonStr)
        }
    }

    /**
     * Position of the `}` that closes the object starting at [startIdx],
     * respecting JSON strings and escape sequences; null when not closed yet.
     */
    private fun indexOfMatchingBrace(text: String, startIdx: Int): Int? {
        var depth = 0
        var inString = false
        var i = startIdx
        while (i < text.length) {
            val c = text[i]
            when {
                inString -> if (c == '\\') i++ else if (c == '"') inString = false
                c == '"' -> inString = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    /** True when the trimmed prefix is a Markdown code-fence marker (``` or ~~~). */
    private fun isFenceMarker(s: String): Boolean =
        s.startsWith("```") || s.startsWith("~~~")

    /** Drop [0, to) from the buffer (StringBuilder.delete is unavailable in common source sets). */
    private fun consumeTo(to: Int) {
        val remaining = buffer.substring(to)
        buffer.setLength(0)
        buffer.append(remaining)
    }
}

/**
 * Convenience wrapper: streams [String] deltas through [StreamCoachParser] and
 * emits [CoachSegment]s, flushing any remaining raw text on completion.
 */
fun Flow<String>.parseCoachStream(): Flow<CoachSegment> = flow {
    val parser = StreamCoachParser()
    collect { delta ->
        parser.append(delta).forEach { emit(it) }
    }
    parser.flush()?.let { emit(it) }
}

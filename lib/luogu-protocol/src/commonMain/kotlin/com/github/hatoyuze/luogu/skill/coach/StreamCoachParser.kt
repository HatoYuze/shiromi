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
        buffer.clear()
        return CoachSegment.Raw(text)
    }

    private fun extractNext(): CoachSegment? {
        val text = buffer.toString()
        val startIdx = text.indexOf("""{"progress"""")
        if (startIdx < 0) return null

        var depth = 0
        var endIdx = -1
        for (i in startIdx until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        endIdx = i
                        break
                    }
                }
            }
        }
        if (endIdx < 0) return null

        val jsonStr = text.substring(startIdx, endIdx + 1)
        val response = parseCoachResponse(jsonStr) ?: return null
        // StringBuilder.delete(Int, Int) 在 common 源集不可用；用重建方式消费已解析前缀，行为跨平台一致
        buffer.setLength(0)
        buffer.append(text.substring(startIdx + jsonStr.length))
        return CoachSegment.Parsed(response, jsonStr)
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

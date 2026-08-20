package com.github.hatoyuze.shiromi.protocol.coach

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StreamCoachParserTest {

    @Test
    fun append_singleCompleteJson_shouldEmitParsed() {
        val parser = StreamCoachParser()
        val segments = parser.append("""{"progress":"init","selected":"P1001","content":"看题"}""")
        assertEquals(1, segments.size)
        val parsed = assertIs<CoachSegment.Parsed>(segments[0])
        assertIs<CoachResponse.Init>(parsed.response)
        assertEquals("P1001", parsed.response.response.selected)
    }

    @Test
    fun append_splitAcrossChunks_shouldAccumulateUntilComplete() {
        val parser = StreamCoachParser()
        assertTrue(parser.append("""{"prog""").isEmpty())
        val segments = parser.append("""ress":"finished","summary":"好","recommend":["P1002"],"content":"完成"}""")
        assertEquals(1, segments.size)
        assertIs<CoachResponse.Finished>((segments[0] as CoachSegment.Parsed).response)
    }

    @Test
    fun append_multipleJsonObjectsInOneChunk_shouldEmitEach() {
        val parser = StreamCoachParser()
        val segments = parser.append(
            """{"progress":"init","selected":"P1","content":""}""" +
                """{"progress":"thinking","content":"hint"}""",
        )
        assertEquals(2, segments.size)
        assertIs<CoachResponse.Processing>((segments[1] as CoachSegment.Parsed).response)
    }

    @Test
    fun append_unknownProgress_shouldEmitRawImmediately() {
        val parser = StreamCoachParser()
        val segments = parser.append("""{"progress":"unknown","x":1}""")
        assertEquals(1, segments.size)
        assertEquals("""{"progress":"unknown","x":1}""", (segments[0] as CoachSegment.Raw).text)
        assertEquals(null, parser.flush())
    }

    @Test
    fun append_unknownProgressThenValidJson_shouldStillParseValid() {
        val parser = StreamCoachParser()
        val segments = parser.append(
            """{"progress":"unknown","x":1}""" +
                """{"progress":"finished","summary":"s","content":"c"}""",
        )
        assertEquals(2, segments.size)
        assertEquals("""{"progress":"unknown","x":1}""", (segments[0] as CoachSegment.Raw).text)
        assertIs<CoachResponse.Finished>((segments[1] as CoachSegment.Parsed).response)
    }

    @Test
    fun append_proseWithBracesThenValidJson_shouldStillParseValid() {
        val parser = StreamCoachParser()
        val segments = parser.append("用 { } 表示集合\n{\"progress\":\"thinking\",\"content\":\"hint\"}")
        assertTrue(segments.isNotEmpty())
        val last = assertIs<CoachSegment.Parsed>(segments.last())
        assertIs<CoachResponse.Processing>(last.response)
    }

    @Test
    fun flush_shouldReturnRemainingRawText() {
        val parser = StreamCoachParser()
        parser.append("plain text ")
        val raw = assertIs<CoachSegment.Raw>(parser.flush())
        assertEquals("plain text ", raw.text)
        assertEquals(null, parser.flush())
    }

    @Test
    fun parseCoachStream_shouldEmitParsedAndFlushTail() = runTest {
        val segments = flowOf(
            """{"progress":"init","selected":"P1","content":""}""",
            "tail",
        ).parseCoachStream().toList()
        assertEquals(2, segments.size)
        assertIs<CoachSegment.Parsed>(segments[0])
        assertEquals("tail", (segments[1] as CoachSegment.Raw).text)
    }

    @Test
    fun append_prettyPrintedJson_shouldEmitParsed() {
        val parser = StreamCoachParser()
        val segments = parser.append(
            """
            {
              "progress": "thinking",
              "content": "hint"
            }
            """.trimIndent(),
        )
        assertEquals(1, segments.size)
        val parsed = assertIs<CoachSegment.Parsed>(segments[0])
        assertIs<CoachResponse.Processing>(parsed.response)
        assertEquals("hint", parsed.response.response.content)
    }

    @Test
    fun append_leadingWhitespaceAndNewlines_shouldEmitParsed() {
        val parser = StreamCoachParser()
        val segments = parser.append("\n  \n{ \"progress\" : \"init\", \"selected\" : \"P1001\", \"content\" : \"看题\" }")
        assertEquals(1, segments.size)
        assertIs<CoachSegment.Parsed>(segments[0])
        assertIs<CoachResponse.Init>((segments[0] as CoachSegment.Parsed).response)
    }

    @Test
    fun append_fencedJson_shouldEmitParsed() {
        val parser = StreamCoachParser()
        val segments = parser.append(
            """```json
{"progress":"finished","summary":"好","recommend":["P1002"],"content":"完成"}
```""",
        )
        assertEquals(1, segments.size)
        assertIs<CoachSegment.Parsed>(segments[0])
        assertIs<CoachResponse.Finished>((segments[0] as CoachSegment.Parsed).response)
    }

    @Test
    fun append_bracesInsideStringValues_shouldNotBreakExtraction() {
        val parser = StreamCoachParser()
        val segments = parser.append("""{"progress":"thinking","content":"用 { } 表示集合 {a,b}，请思考"}""")
        assertEquals(1, segments.size)
        val parsed = assertIs<CoachSegment.Parsed>(segments[0])
        val processing = assertIs<CoachResponse.Processing>(parsed.response)
        assertEquals("用 { } 表示集合 {a,b}，请思考", processing.response.content)
    }

    @Test
    fun append_progressKeyNotFirst_shouldEmitParsed() {
        val parser = StreamCoachParser()
        val segments = parser.append("""{"content":"x","selected":"P1","progress":"init"}""")
        assertEquals(1, segments.size)
        assertIs<CoachResponse.Init>((segments[0] as CoachSegment.Parsed).response)
    }

    @Test
    fun append_prettyJsonSplitAcrossChunks_shouldAccumulate() {
        val parser = StreamCoachParser()
        assertTrue(parser.append("{\n  \"prog").isEmpty())
        val segments = parser.append("""ress": "thinking",
  "content": "hint"
}""")
        assertEquals(1, segments.size)
        assertIs<CoachSegment.Parsed>(segments[0])
        assertIs<CoachResponse.Processing>((segments[0] as CoachSegment.Parsed).response)
    }

    @Test
    fun append_proseBeforeJson_shouldEmitRawThenParsed() {
        val parser = StreamCoachParser()
        val segments = parser.append("好的，请看提示\n{\"progress\":\"thinking\",\"content\":\"hint\"}")
        assertEquals(2, segments.size)
        assertEquals("好的，请看提示\n", (segments[0] as CoachSegment.Raw).text)
        assertIs<CoachSegment.Parsed>(segments[1])
        assertIs<CoachResponse.Processing>((segments[1] as CoachSegment.Parsed).response)
    }

    @Test
    fun append_multiplePrettyPrintedObjectsInOneChunk_shouldEmitEach() {
        val parser = StreamCoachParser()
        val segments = parser.append(
            """
            {
              "progress": "init",
              "selected": "P1",
              "content": ""
            }
            {
              "progress": "thinking",
              "content": "hint"
            }
            """.trimIndent(),
        )
        assertEquals(2, segments.size)
        assertIs<CoachResponse.Init>((segments[0] as CoachSegment.Parsed).response)
        assertIs<CoachResponse.Processing>((segments[1] as CoachSegment.Parsed).response)
    }

    @Test
    fun append_prettyPrintedCheckpoint_shouldParse() {
        val parser = StreamCoachParser()
        val checkpoint =
            """
            {
              "progress": "checkpoint",
              "problem": { "id": "P1", "title": "T", "synopsis": "S" },
              "solution": {
                "approach": "A",
                "outline": ["o1"],
                "modules_completed": ["m"],
                "current_module": "c",
                "current_module_detail": "d"
              },
              "key_definitions": { "k": "v" },
              "established": ["e"],
              "pending": ["p"],
              "student": { "level_so_far": 50, "strengths": [], "weaknesses": [] },
              "next": "n"
            }
            """.trimIndent()
        val segments = parser.append(checkpoint)
        assertEquals(1, segments.size)
        val response = assertIs<CoachResponse.Checkpoint>((segments[0] as CoachSegment.Parsed).response)
        assertEquals("P1", response.response.problem.id)
        assertEquals("n", response.response.next)
    }
}
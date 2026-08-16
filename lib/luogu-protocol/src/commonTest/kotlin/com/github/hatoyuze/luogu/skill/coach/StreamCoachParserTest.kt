package com.github.hatoyuze.luogu.skill.coach

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
    fun append_unknownProgress_shouldKeepBuffering() {
        val parser = StreamCoachParser()
        assertTrue(parser.append("""{"progress":"unknown","x":1}""").isEmpty())
        val raw = assertIs<CoachSegment.Raw>(parser.flush())
        assertEquals("""{"progress":"unknown","x":1}""", raw.text)
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
}

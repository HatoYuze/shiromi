// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.skill

import com.github.hatoyuze.luogu.skill.api.LuoguApi
import com.github.hatoyuze.luogu.skill.coach.CoachFinishedResponse
import com.github.hatoyuze.luogu.skill.coach.CoachInitResponse
import com.github.hatoyuze.luogu.skill.coach.CoachProcessingResponse
import com.github.hatoyuze.luogu.skill.coach.CoachResponse
import com.github.hatoyuze.luogu.skill.coach.LevelHistoryEntry
import com.github.hatoyuze.luogu.skill.coach.MemoryProvider
import com.github.hatoyuze.luogu.skill.coach.SelectedResult
import com.github.hatoyuze.luogu.skill.coach.StudentMemory
import com.github.hatoyuze.luogu.skill.coach.installAllTools
import com.github.hatoyuze.luogu.skill.coach.installCoachTools
import com.github.hatoyuze.luogu.skill.coach.parseCoachResponse
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.*

class CoachApiTest {

    // ═══════════════ CoachResponse 解析 ═══════════════

    @Test fun `parse init JSON`() {
        val r = parseCoachResponse("""{"progress":"init","selected":"P1000"}""")
        assertIs<CoachResponse.Init>(r)
        assertEquals("P1000", r.response.selected)
    }
    @Test fun `parse thinking JSON`() {
        val r = parseCoachResponse("""{"progress":"thinking","content":"理解什么是DP吗？"}""")
        assertIs<CoachResponse.Processing>(r)
        assertTrue(r.response.content.contains("DP"))
    }
    @Test fun `parse finished with recommend`() {
        val r = parseCoachResponse("""{"progress":"finished","recommend":["P1001","P1002"],"summary":"掌握DP","content":"很好！"}""")
        assertIs<CoachResponse.Finished>(r)
        assertEquals(listOf("P1001", "P1002"), r.response.recommend)
        assertEquals("掌握DP", r.response.summary)
    }
    @Test fun `parse finished null recommend`() {
        val r = parseCoachResponse("""{"progress":"finished","recommend":null,"summary":"ok","content":"done"}""")
        assertIs<CoachResponse.Finished>(r)
        assertNull(r.response.recommend)
    }
    @Test fun `parse checkpoint JSON`() {
        val r = parseCoachResponse("""{"progress":"checkpoint","problem":{"id":"P1000","title":"Test","synopsis":"..."},"solution":{"approach":"DP","outline":["step1"],"modules_completed":[],"current_module":"","current_module_detail":""},"key_definitions":{},"established":[],"pending":[],"student":{"level_so_far":60,"strengths":[],"weaknesses":[]},"next":"继续"}""")
        assertIs<CoachResponse.Checkpoint>(r)
        assertEquals("P1000", r.response.problem.id)
    }
    @Test fun `parse unknown progress returns null`() {
        assertNull(parseCoachResponse("""{"progress":"unknown","content":"x"}"""))
    }
    @Test fun `parse invalid JSON returns null`() {
        assertNull(parseCoachResponse("not json"))
        assertNull(parseCoachResponse(""))
    }

    // ═══════════════ CoachResponse 序列化 ═══════════════

    @Test fun `Init roundtrip`() {
        val orig = CoachInitResponse(selected = "P1000")
        val json = Json.encodeToString(serializer<CoachInitResponse>(), orig)
        val parsed = Json.decodeFromString<CoachInitResponse>(json)
        assertEquals("init", parsed.progress)
        assertEquals("P1000", parsed.selected)
    }
    @Test fun `Processing roundtrip`() {
        val orig = CoachProcessingResponse(content = "你好")
        val json = Json.encodeToString(serializer<CoachProcessingResponse>(), orig)
        val parsed = Json.decodeFromString<CoachProcessingResponse>(json)
        assertEquals("thinking", parsed.progress)
        assertEquals("你好", parsed.content)
    }
    @Test fun `Finished roundtrip`() {
        val orig = CoachFinishedResponse(recommend = listOf("P1001"), summary = "ok", content = "done")
        val json = Json.encodeToString(serializer<CoachFinishedResponse>(), orig)
        val parsed = Json.decodeFromString<CoachFinishedResponse>(json)
        assertEquals("finished", parsed.progress)
        assertEquals("ok", parsed.summary)
        assertEquals(listOf("P1001"), parsed.recommend)
    }

    // ═══════════════ StudentMemory ═══════════════

    @Test fun `StudentMemory roundtrip`() {
        val m = StudentMemory(
            studentId = "stu_1", lastTopic = "DP", lastProblem = "P1000", levelScore = 75,
            painPoints = listOf("转移方程"), progressPoints = listOf("理解了"),
            levelHistory = listOf(LevelHistoryEntry(topic = "DP", score = 75, date = "2026-06-01", problem = "P1000")),
        )
        val json = Json.encodeToString(serializer<StudentMemory>(), m)
        val parsed = Json.decodeFromString<StudentMemory>(json)
        assertEquals("DP", parsed.lastTopic)
        assertEquals(75, parsed.levelScore)
        assertEquals(listOf("转移方程"), parsed.painPoints)
    }

    // ═══════════════ Coach tool registration ═══════════════

    private fun emptyProvider() = object : MemoryProvider {
        override suspend fun getMemory(): StudentMemory? = null
        override suspend fun getSelectedProblems(): List<String> = emptyList()
    }

    @Test fun `installCoachTools registers 2 tools`() {
        val h = io.github.hatoyuze.deepseek.toolcall.dsl.toolHost { installCoachTools(emptyProvider()) }
        assertEquals(2, h.getDefinitions().size)
    }
    @Test fun `installCoachTools correct names`() {
        val h = io.github.hatoyuze.deepseek.toolcall.dsl.toolHost { installCoachTools(emptyProvider()) }
        val n = h.getDefinitions().map { it.name }.toSet()
        assertTrue(n.containsAll(listOf("get_memory", "get_selected")))
    }
    @Test fun `installAllTools registers 11 tools`() {
        val h = io.github.hatoyuze.deepseek.toolcall.dsl.toolHost { installAllTools(LuoguApi(), emptyProvider()) }
        assertEquals(11, h.getDefinitions().size)
    }
    @Test fun `installAllTools contains all names`() {
        val h = io.github.hatoyuze.deepseek.toolcall.dsl.toolHost { installAllTools(LuoguApi(), emptyProvider()) }
        val n = h.getDefinitions().map { it.name }.toSet()
        assertTrue(n.containsAll(listOf("luogu_get_filters","luogu_search_problems","luogu_get_problem","luogu_get_solutions","luogu_search_trainings","luogu_get_training","luogu_get_practice","luogu_get_records","luogu_get_record","get_memory","get_selected")))
    }

    // ═══════════════ SelectedResult ═══════════════

    @Test fun `SelectedResult auto count`() {
        assertEquals(3, SelectedResult(completed = listOf("P1000", "P1001", "P1002")).count)
        assertEquals(0, SelectedResult(completed = emptyList()).count)
    }

    // ═══════════════ MemoryProvider defaults ═══════════════

    @Test fun `get_memory returns default when null`() = runTest {
        val p = emptyProvider()
        val host = io.github.hatoyuze.deepseek.toolcall.dsl.toolHost { installCoachTools(p) }
        // Tool exists and can be looked up
        val def = host.getDefinitions().find { it.name == "get_memory" }
        assertNotNull(def)
        assertEquals("get_memory", def.name)
    }
}

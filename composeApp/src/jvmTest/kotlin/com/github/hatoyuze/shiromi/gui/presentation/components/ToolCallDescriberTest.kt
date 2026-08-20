// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * ToolCallDescriber 纯逻辑测试：搜索参数解析（关键词/多 tag/难度区间/排序），
 * 供思考链 FlowRow 排版（设计稿 C）。
 */
class ToolCallDescriberTest {

    private fun parts(json: String) =
        ToolCallDescriber.parseSearchProblemsParts(Json.parseToJsonElement(json).jsonObject)

    @Test
    fun parseSearchProblems_fullArgs() {
        val p = parts(
            """{"keyword":"二分","tag":"贪心","difficulty_min":6,"difficulty_max":9,"sort_by":"pass_rate"}"""
        )
        assertTrue(p != null)
        assertEquals("二分", p!!.keyword)
        assertEquals(listOf("贪心"), p.tags)
        assertEquals(6, p.difficultyMin)
        assertEquals(9, p.difficultyMax)
        assertEquals("pass_rate", p.sortBy)
    }

    @Test
    fun parseSearchProblems_multipleTagsSplits() {
        val p = parts("""{"tag":"贪心,动态规划；区间dp、二分 排序"}""")
        assertEquals(listOf("贪心", "动态规划", "区间dp", "二分", "排序"), p!!.tags)
    }

    @Test
    fun parseSearchProblems_singleDifficulty() {
        val p = parts("""{"difficulty_min":8}""")
        assertEquals(8, p!!.difficultyMin)
        assertNull(p.difficultyMax)
    }

    @Test
    fun parseSearchProblems_blankArgsReturnsNull() {
        assertNull(parts("""{"keyword":"","tag":"  "}"""))
        assertNull(parts("""{}"""))
    }

    @Test
    fun parseSearchProblems_missingOptionalFields() {
        val p = parts("""{"keyword":"P1000"}""")
        assertTrue(p != null)
        assertEquals("P1000", p!!.keyword)
        assertTrue(p.tags.isEmpty())
        assertNull(p.difficultyMin)
        assertNull(p.difficultyMax)
        assertNull(p.sortBy)
    }

    @Test
    fun parseSearchProblems_invalidNumbersIgnored() {
        val p = parts("""{"difficulty_min":"abc","difficulty_max":"9"}""")
        assertNull(p!!.difficultyMin)
        assertEquals(9, p.difficultyMax)
    }

    @Test
    fun parseSearchProblems_wrongTypedArgs_doNotThrow() {
        // LLM 参数类型不匹配（keyword 为数组、difficulty_min 为 null、sort_by 为对象）
        // → 安全降级不崩溃；数字标签按文本解析为 tag「123」。
        val p = parts("""{"keyword":[1,2],"tag":123,"difficulty_min":null,"sort_by":{"x":1}}""")
        assertTrue(p != null)
        assertNull(p!!.keyword)
        assertEquals(listOf("123"), p.tags)
        assertNull(p.difficultyMin)
        assertNull(p.sortBy)
    }
}

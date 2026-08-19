// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.domain.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AskUserArgsTest {

    @Test
    fun parse_fullArgs_shouldExtractAllFields() {
        val args = parseAskUserArgs(
            """{"desc":"你确定吗","timeout":30000,"isMulti":true,"allowCustom":true,"options":["A","B"]}""",
        )
        assertEquals("你确定吗", args?.desc)
        assertEquals(30_000, args?.timeoutMs)
        assertEquals(true, args?.isMulti)
        assertEquals(true, args?.allowCustom)
        assertEquals(listOf("A", "B"), args?.options)
    }

    @Test
    fun parse_missingFields_shouldUseDefaults() {
        val args = parseAskUserArgs("""{"desc":"q"}""")
        assertEquals(60_000, args?.timeoutMs)
        assertEquals(false, args?.isMulti)
        assertEquals(false, args?.allowCustom)
        assertEquals(emptyList<String>(), args?.options)
    }

    @Test
    fun parse_malformedJson_shouldReturnNull() {
        assertNull(parseAskUserArgs("not json"))
        assertNull(parseAskUserArgs("""{"desc":}"""))
    }
}

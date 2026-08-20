// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProblemIdTest {
    @Test
    fun normalizeProblemId_keepsUppercaseLuoguPid() {
        assertEquals("P1234", normalizeProblemId("P1234"))
        assertEquals("CF1234A", normalizeProblemId("CF1234A"))
        assertEquals("U1234", normalizeProblemId("U1234"))
        assertEquals("B2001", normalizeProblemId("B2001"))
    }

    @Test
    fun normalizeProblemId_uppercasesAndTrimsInput() {
        assertEquals("P1000", normalizeProblemId("p1000"))
        assertEquals("P1234", normalizeProblemId("  P1234  "))
        assertEquals("CF1234A", normalizeProblemId("cf1234a"))
    }

    @Test
    fun normalizeProblemId_keepsPlainDigits() {
        assertEquals("1000", normalizeProblemId("1000"))
        assertEquals("123456", normalizeProblemId(" 123456 "))
    }

    @Test
    fun normalizeProblemId_rejectsGarbage() {
        assertNull(normalizeProblemId(""))
        assertNull(normalizeProblemId("   "))
        assertNull(normalizeProblemId("P"))
        assertNull(normalizeProblemId("Problem"))
        assertNull(normalizeProblemId("abc"))
        assertNull(normalizeProblemId("P1234-1"))
        assertNull(normalizeProblemId("P1234ABCDEF"))
    }

    @Test
    fun normalizeProblemId_acceptsLetterPrefixPlusDigits() {
        // 前缀字母 + 数字即视为合法（如 PXYZ123），最终由题目接口决定是否存在
        assertEquals("PXYZ123", normalizeProblemId("Pxyz123"))
        assertEquals("ABC123", normalizeProblemId("abc123"))
    }
}

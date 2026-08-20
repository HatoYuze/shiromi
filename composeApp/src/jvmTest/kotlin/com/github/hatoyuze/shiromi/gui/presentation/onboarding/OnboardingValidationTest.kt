// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.presentation.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OnboardingValidationTest {

    @Test fun `isValidApiKey accepts sk- prefixed key with sufficient length`() {
        assertTrue(isValidApiKey("sk-1234567890abcdef"))
        assertTrue(isValidApiKey("  sk-abcdefgh  "))
        assertTrue(isValidApiKey("sk-12345")) // 恰好 8 字符边界
    }

    @Test fun `isValidApiKey rejects blank, short and non sk- values`() {
        assertFalse(isValidApiKey(""))
        assertFalse(isValidApiKey("   "))
        assertFalse(isValidApiKey("abc"))
        assertFalse(isValidApiKey("sk-")) // 空后缀
        assertFalse(isValidApiKey("sk-123")) // 过短
        assertFalse(isValidApiKey("SK-123456")) // 大小写敏感，与 DeepSeek 前缀约定一致
    }

    @Test fun `looksLikeLuoguCookie accepts positive uid cookies`() {
        assertTrue(looksLikeLuoguCookie("_uid=123; __client_id=abc; C3VK=xyz"))
        assertTrue(looksLikeLuoguCookie(" __client_id=abc; _uid=42 "))
    }

    @Test fun `looksLikeLuoguCookie rejects missing, zero and fake uid`() {
        assertFalse(looksLikeLuoguCookie(""))
        assertFalse(looksLikeLuoguCookie("__client_id=abc"))
        assertFalse(looksLikeLuoguCookie("uid=123"))
        assertFalse(looksLikeLuoguCookie("_uid=0; __client_id=abc")) // 匿名
        assertFalse(looksLikeLuoguCookie("my_uid=5"))
    }

    @Test fun `extractLuoguUid parses positive uid`() {
        assertEquals(1825403, extractLuoguUid("_uid=1825403; __client_id=abc"))
        assertEquals(42, extractLuoguUid("__client_id=abc; _uid=42"))
    }

    @Test fun `extractLuoguUid returns null for zero or missing uid`() {
        assertNull(extractLuoguUid("_uid=0"))
        assertNull(extractLuoguUid("__client_id=abc"))
        assertNull(extractLuoguUid(""))
    }
}

// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LuoguSessionExtractorTest {

    @Test fun `parseCookieString handles standard string`() {
        val map = LuoguSessionExtractor.parseCookieString("_uid=123; __client_id=abc; C3VK=xyz")
        assertEquals("123", map["_uid"])
        assertEquals("abc", map["__client_id"])
        assertEquals("xyz", map["C3VK"])
    }

    @Test fun `parseCookieString handles blank and malformed parts`() {
        assertTrue(LuoguSessionExtractor.parseCookieString("").isEmpty())
        assertTrue(LuoguSessionExtractor.parseCookieString(null).isEmpty())
        assertTrue(LuoguSessionExtractor.parseCookieString("noequals").isEmpty())
        assertTrue(LuoguSessionExtractor.parseCookieString("=val").isEmpty())
        assertEquals("v", LuoguSessionExtractor.parseCookieString("a= b = c ; d=v")["d"])
    }

    @Test fun `parseCookieString trims name and value`() {
        val map = LuoguSessionExtractor.parseCookieString(" _uid = 123 ")
        assertEquals("123", map["_uid"])
    }

    @Test fun `buildCookieString filters to session allowlist and keeps order`() {
        val map = linkedMapOf(
            "_uid" to "123",
            "__client_id" to "abc",
            "C3VK" to "xyz",
            "PHPSESSID" to "skip",
            "analytics" to "x",
        )
        assertEquals(
            "_uid=123; __client_id=abc; C3VK=xyz",
            LuoguSessionExtractor.buildCookieString(map),
        )
    }

    @Test fun `buildCookieString drops blank values`() {
        val map = linkedMapOf("_uid" to "", "__client_id" to "abc")
        assertEquals("__client_id=abc", LuoguSessionExtractor.buildCookieString(map))
    }

    @Test fun `buildCookieString returns empty for empty input`() {
        assertEquals("", LuoguSessionExtractor.buildCookieString(emptyMap()))
    }

    @Test fun `parseUserJson extracts uid and name`() {
        val (uid, name) = LuoguSessionExtractor.parseUserJson("""{"uid":1825403,"name":"YUKIKY","rank":1}""")
        assertEquals(1825403, uid)
        assertEquals("YUKIKY", name)
    }

    @Test fun `parseUserJson handles null, zero uid and garbage`() {
        assertTrue(LuoguSessionExtractor.parseUserJson(null).first == null)
        assertTrue(LuoguSessionExtractor.parseUserJson("null").first == null)
        assertTrue(LuoguSessionExtractor.parseUserJson("not json").first == null)
        assertTrue(LuoguSessionExtractor.parseUserJson("""{"uid":0}""").first == null)
        assertTrue(LuoguSessionExtractor.parseUserJson("""{"uid":-5}""").first == null)
    }

    @Test fun `hasLoggedInUid true when uid cookie present and positive`() {
        assertTrue(LuoguSessionExtractor.hasLoggedInUid("_uid=123; __client_id=abc; C3VK=xyz"))
        assertTrue(LuoguSessionExtractor.hasLoggedInUid(" __client_id=abc; _uid=42 "))
        assertTrue(LuoguSessionExtractor.hasLoggedInUid("_uid=01")) // 前导零可解析为 1
        assertTrue(LuoguSessionExtractor.hasLoggedInUid("_uid = 123 ")) // 空白容忍
        assertTrue(LuoguSessionExtractor.hasLoggedInUid("_uid=+5")) // 正号可解析
    }

    @Test fun `hasLoggedInUid false for anonymous, missing or malformed`() {
        assertFalse(LuoguSessionExtractor.hasLoggedInUid(null))
        assertFalse(LuoguSessionExtractor.hasLoggedInUid(""))
        assertFalse(LuoguSessionExtractor.hasLoggedInUid("_uid=0; __client_id=abc"))
        assertFalse(LuoguSessionExtractor.hasLoggedInUid("__client_id=abc"))
        assertFalse(LuoguSessionExtractor.hasLoggedInUid("_uid=abc"))
        assertFalse(LuoguSessionExtractor.hasLoggedInUid("_uid=123abc"))
        assertFalse(LuoguSessionExtractor.hasLoggedInUid("_uid=-5"))
        assertFalse(LuoguSessionExtractor.hasLoggedInUid("my_uid=5"))
    }
}

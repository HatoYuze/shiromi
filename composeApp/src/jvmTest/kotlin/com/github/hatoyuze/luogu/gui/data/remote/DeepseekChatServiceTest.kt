// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.luogu.gui.data.remote

import com.github.hatoyuze.luogu.gui.config.ConfigService
import com.github.hatoyuze.luogu.gui.config.GuiConfig
import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import com.github.hatoyuze.luogu.skill.coach.MemoryProvider
import com.github.hatoyuze.luogu.skill.coach.StudentMemory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class DeepseekChatServiceTest {

    private class FakeMemoryProvider : MemoryProvider {
        override suspend fun getMemory(): StudentMemory? = null
        override suspend fun getSelectedProblems(): List<String> = emptyList()
    }

    @Test
    fun createSession_shouldReuseExistingSession() = runTest {
        ConfigService.apply(GuiConfig(apiKey = "sk-x"))
        val service = DeepseekChatService(ConfigService, LuoguApiProvider(ConfigService, null), FakeMemoryProvider())

        val first = service.createSession("s1", SessionType.CHAT)
        val second = service.createSession("s1", SessionType.CHAT)
        assertSame(first, second)
    }

    @Test
    fun resetSession_shouldReplaceExistingSession() = runTest {
        ConfigService.apply(GuiConfig(apiKey = "sk-x"))
        val service = DeepseekChatService(ConfigService, LuoguApiProvider(ConfigService, null), FakeMemoryProvider())

        val first = service.createSession("s1", SessionType.COACH)
        val reset = service.resetSession("s1", SessionType.COACH)
        assertNotSame(first, reset)
        assertEquals("s1", reset.sessionId)
        assertEquals(SessionType.COACH, reset.type)
    }

    @Test
    fun sessions_shouldBeIsolatedPerId() = runTest {
        ConfigService.apply(GuiConfig(apiKey = "sk-x"))
        val service = DeepseekChatService(ConfigService, LuoguApiProvider(ConfigService, null), FakeMemoryProvider())

        val a = service.createSession("a", SessionType.CHAT)
        val b = service.createSession("b", SessionType.CHAT)
        assertNotSame(a, b)
    }

    @Test
    fun sessions_shouldEvictLeastRecentlyUsedAtCapacity() = runTest {
        ConfigService.apply(GuiConfig(apiKey = "sk-x"))
        val service = DeepseekChatService(
            ConfigService,
            LuoguApiProvider(ConfigService, null),
            FakeMemoryProvider(),
            maxCachedSessions = 2,
        )

        val a = service.createSession("a", SessionType.CHAT)
        service.createSession("b", SessionType.CHAT)
        // Third creation evicts the least recently used session ("a").
        service.createSession("c", SessionType.CHAT)

        val aAgain = service.createSession("a", SessionType.CHAT)
        assertNotSame(a, aAgain)
        assertEquals("a", aAgain.sessionId)
    }
}

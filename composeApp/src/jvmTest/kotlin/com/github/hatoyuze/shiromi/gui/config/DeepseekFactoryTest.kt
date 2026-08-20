// SPDX-FileCopyrightText: 2026 Yukiky (hatoyuze) <yukikyovo@qq.com>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.github.hatoyuze.shiromi.gui.config

import com.github.hatoyuze.shiromi.gui.domain.model.SessionType
import com.github.hatoyuze.shiromi.protocol.api.LuoguApi
import com.github.hatoyuze.shiromi.protocol.api.installLuoguTools
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class DeepseekFactoryTest {

    @Test
    fun deepseekWithConfig_shouldShareChatConfigAndSnapshotPrompt() {
        ConfigService.apply(GuiConfig(apiKey = "sk-x", model = "deepseek-v4-pro", chatPrompt = "system chat"))
        val api = LuoguApi()
        val ds = deepseekWithConfig(SessionType.CHAT, ConfigService, api) { luoguApi ->
            installLuoguTools(luoguApi)
        }
        assertSame(ConfigService.chatConfig, ds.config)
        assertEquals("sk-x", ds.apiKey)
        assertEquals("system chat", ds.messages.first().content)
    }

    @Test
    fun deepseekWithConfig_coach_shouldUseCoachPrompt() {
        ConfigService.apply(GuiConfig(apiKey = "sk-x", coachPrompt = "system coach"))
        val ds = deepseekWithConfig(SessionType.COACH, ConfigService, LuoguApi()) { }
        assertEquals("system coach", ds.messages.first().content)
    }
}

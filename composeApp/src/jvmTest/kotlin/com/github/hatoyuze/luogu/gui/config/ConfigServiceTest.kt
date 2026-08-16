@file:OptIn(io.github.hatoyuze.deepseek.protocol.api.ExperimentalDeepseekApi::class)

package com.github.hatoyuze.luogu.gui.config

import io.github.hatoyuze.deepseek.protocol.api.entity.ReasoningEffort
import io.github.hatoyuze.deepseek.protocol.api.entity.ThinkingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigServiceTest {

    @Test
    fun applyAndToGuiConfig_shouldRoundTrip() {
        val config = GuiConfig(
            apiKey = "sk-test",
            model = "deepseek-v4-pro",
            maxTokens = -1,
            temperature = 0.5,
            topP = 0.9,
            thinkingMode = ThinkingMode.WithEffort(ReasoningEffort.MAX),
            maxToolIterations = 9,
            luoguCookie = "cookie",
            luoguUid = "42",
            chatPrompt = "chat",
            coachPrompt = "coach",
        )
        ConfigService.apply(config)
        assertEquals(config, ConfigService.toGuiConfig())
        assertNull(ConfigService.chatConfig.maxTokens)
        assertEquals(ThinkingMode.WithEffort(ReasoningEffort.MAX), ConfigService.chatConfig.thinkingMode)
    }

    @Test
    fun apply_negativeMaxTokens_shouldMapToUnlimited() {
        ConfigService.apply(GuiConfig(maxTokens = -1))
        assertNull(ConfigService.chatConfig.maxTokens)
        assertEquals(-1, ConfigService.toGuiConfig().maxTokens)
    }

    @Test
    fun reasoningEffortName_shouldMapKnownModes() {
        assertEquals(null, GuiConfig.reasoningEffortName(ThinkingMode.Enabled))
        assertEquals(null, GuiConfig.reasoningEffortName(ThinkingMode.Disabled))
        assertEquals("high", GuiConfig.reasoningEffortName(ThinkingMode.WithEffort(ReasoningEffort.HIGH)))
        assertEquals("max", GuiConfig.reasoningEffortName(ThinkingMode.WithEffort(ReasoningEffort.MAX)))
        assertEquals(ThinkingMode.Disabled, GuiConfig.parseReasoningEffort("disabled"))
        assertEquals(ThinkingMode.WithEffort(ReasoningEffort.MAX), GuiConfig.parseReasoningEffort("max"))
        assertEquals(ThinkingMode.Enabled, GuiConfig.parseReasoningEffort(null))
    }
}

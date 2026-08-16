package com.github.hatoyuze.luogu.gui.config

import com.github.hatoyuze.luogu.gui.domain.model.SessionType
import com.github.hatoyuze.luogu.skill.api.LuoguApi
import io.github.hatoyuze.deepseek.protocol.api.Deepseek
import io.github.hatoyuze.deepseek.protocol.api.deepseek
import io.github.hatoyuze.deepseek.toolcall.dsl.ToolHostBuilder

/**
 * Builds a [Deepseek] from [ConfigService].
 *
 * apiKey / model / prompt are snapshotted at creation time; generation
 * parameters are shared live through [ConfigService.chatConfig], which is
 * injected as the [Deepseek.config] instance (no copy/mapping).
 */
fun deepseekWithConfig(
    type: SessionType,
    settings: ConfigService,
    api: LuoguApi,
    toolsBlock: ToolHostBuilder.(LuoguApi) -> Unit,
): Deepseek = deepseek(settings.apiKey, sharedConfig = settings.chatConfig) {
    model { custom(settings.model) }
    prompt = when (type) {
        SessionType.CHAT -> settings.chatPrompt
        SessionType.COACH -> settings.coachPrompt
    }
    tools { toolsBlock(api) }
}
